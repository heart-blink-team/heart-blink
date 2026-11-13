package kr.heartblink.car.data

import android.content.Context
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File
import java.util.UUID

/**
 * Everything the screens need from the server.
 *
 * Conventions:
 * - Writes return Result<T>. A failure is always a [HeartError] whose `text` can be shown as is.
 *   Offline (no Supabase config) every write is a no-op that fails with [HeartError.Offline].
 * - Reads return null or an empty list when offline, when there is nothing, or when the request failed
 *   (the failure is logged).
 * - Flows emit right away and again whenever realtime reports a change in the watched rows
 *   (plus a slow poll as a safety net). Offline they emit a single null.
 *
 * Get it with HeartRepository.get(context).
 */
class HeartRepository private constructor(private val appContext: Context) {

    /** False when local.properties has no Supabase URL or key; the screens should stay in demo mode. */
    val enabled: Boolean get() = Backend.enabled

    // ------------------------------------------------------------ profile

    /** My profile, or null before I saved one. */
    suspend fun me(): Profile? = read(null) { c, me -> profile(c, me) }

    /** Registers or updates my name and plate (spaces in the plate are removed by the server). */
    suspend fun saveProfile(name: String, plate: String): Result<Profile> = call { c, _ ->
        c.postgrest.rpc("save_profile", buildJsonObject {
            put("p_name", name)
            put("p_plate", plate)
        }).decodeAs<Profile>()
    }

    /** The other person's profile while a request is pending or we are connected. */
    suspend fun partnerProfile(): Profile? = read(null) { c, me ->
        currentCouple(c, me)?.let { profile(c, it.partnerOf(me)) }
    }

    // ------------------------------------------------------------ connection

    /** Asks the owner of [partnerPlate] to connect. */
    suspend fun requestConnection(partnerPlate: String): Result<Couple> = call { c, _ ->
        c.postgrest.rpc("request_connection", buildJsonObject {
            put("p_partner_plate", partnerPlate)
        }).decodeAs<Couple>()
    }

    /** A request someone sent to my plate that I have not answered yet. */
    suspend fun pendingRequestForMe(): PendingRequest? = read(null) { c, me ->
        val couple = c.from("couples").select {
            filter {
                eq("partner", me)
                eq("status", Couple.STATUS_PENDING)
            }
            order("created_at", Order.DESCENDING)
            limit(1)
        }.decodeSingleOrNull<Couple>()
        couple?.let { PendingRequest(it, profile(c, it.requester)) }
    }

    /** Accepts (giving the requester's plate a nickname) or declines the pending request. */
    suspend fun respondConnection(accept: Boolean, nicknameForRequester: String?): Result<Couple> = call { c, _ ->
        c.postgrest.rpc("respond_connection", buildJsonObject {
            put("p_accept", accept)
            put("p_nickname_for_requester", nicknameForRequester ?: "")
        }).decodeAs<Couple>()
    }

    /** Sets the nickname I give my partner's plate and/or the day we first met. Null keeps the old value. */
    suspend fun updateCouple(partnerNickname: String?, firstMet: LocalDate?): Result<Couple> = call { c, _ ->
        c.postgrest.rpc("update_couple", buildJsonObject {
            put("p_partner_nickname", partnerNickname)
            put("p_first_met", firstMet?.toString())
        }).decodeAs<Couple>()
    }

    /** Disconnects. With [deleteRecords] the couple and all its messages and answers are deleted. */
    suspend fun endCouple(deleteRecords: Boolean): Result<Unit> = call { c, _ ->
        c.postgrest.rpc("end_couple", buildJsonObject { put("p_delete_records", deleteRecords) })
        Unit
    }

    /** My pending or active couple right now. */
    suspend fun couple(): Couple? = read(null) { c, me -> currentCouple(c, me) }

    /** My pending or active couple, kept fresh with realtime. Null when there is none. */
    fun myCouple(): Flow<Couple?> = live(listOf(Watch("couples")), null) { c, me -> currentCouple(c, me) }

    // ------------------------------------------------------------ questions

    /** The question we are on, with my answer and (once both answered) my partner's. */
    suspend fun currentQuestion(): QuestionState? = read(null) { c, me ->
        currentCouple(c, me)?.takeIf { it.isActive }?.let { question(c, me, it.id, it.questionNo) }
    }

    /** Question [no] of our couple with our answers (my partner's only once both answered). */
    suspend fun questionAt(no: Int): QuestionState? = read(null) { c, me ->
        currentCouple(c, me)?.takeIf { it.isActive }?.let { question(c, me, it.id, no) }
    }

    /** [currentQuestion] kept fresh: changes when either of us answers or the question moves on. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun watchCurrentQuestion(): Flow<QuestionState?> =
        myCouple().map { it?.takeIf { c -> c.isActive }?.id }.distinctUntilChanged().flatMapLatest { coupleId ->
            if (coupleId == null) flowOf(null)
            else live(listOf(Watch("answers", "couple_id", coupleId), Watch("couples", "id", coupleId)), null) { c, me ->
                currentCouple(c, me)?.takeIf { it.isActive }?.let { question(c, me, it.id, it.questionNo) }
            }
        }

    /**
     * Answers the current question with text and/or a voice recording.
     * Returns the state of the question just answered, so the partner's answer shows at once
     * when I was the second to answer.
     */
    suspend fun answerQuestion(text: String, audio: Recording? = null): Result<QuestionState> = call { c, me ->
        val couple = currentCouple(c, me)?.takeIf { it.isActive } ?: throw HeartError.NotConnected
        val answeredNo = couple.questionNo
        val audioPath = audio?.let { upload(c, couple.id, it.file) }
        c.postgrest.rpc("answer_question", buildJsonObject {
            put("p_body", text)
            put("p_audio_path", audioPath)
            put("p_audio_ms", audio?.durationMs)
        })
        question(c, me, couple.id, answeredNo) ?: throw HeartError.Unknown("question $answeredNo missing")
    }

    /**
     * Opens today's question when both of us answered the last one on an earlier day (a day starts at 4 a.m.).
     * Nothing changes when one of us has not answered yet or today's question is already open.
     */
    suspend fun openTodayQuestion(): Result<Unit> = call { c, _ ->
        c.postgrest.rpc("open_today_question")
        Unit
    }

    /** Tells the partner's 06 which message conditions this car cannot sense. */
    suspend fun saveCarSupport(unsupported: Collection<String>): Result<Unit> = call { c, _ ->
        c.postgrest.rpc("save_car_support", buildJsonObject {
            putJsonArray("p_unsupported") { unsupported.sorted().forEach { add(it) } }
        })
        Unit
    }

    /** Questions both of us answered, newest first (for the record screen). */
    suspend fun answeredQuestions(): List<QuestionState> = read(emptyList()) { c, me ->
        val couple = currentCouple(c, me)?.takeIf { it.isActive } ?: return@read emptyList()
        val answers = c.from("answers").select {
            filter { eq("couple_id", couple.id) }
        }.decodeList<Answer>()
        val pairs = answers.groupBy { it.questionNo }.filterValues { list -> list.size == 2 }
        if (pairs.isEmpty()) return@read emptyList()
        val questions = c.from("questions").select {
            filter { isIn("no", pairs.keys.toList()) }
        }.decodeList<Question>().associateBy { it.no }
        pairs.mapNotNull { (no, list) ->
            val q = questions[no] ?: return@mapNotNull null
            QuestionState(q, list.firstOrNull { it.userId == me }, list.firstOrNull { it.userId != me })
        }.sortedByDescending { it.question.no }
    }

    // ------------------------------------------------------------ messages

    /**
     * Leaves a message that opens in my partner's car when [group]/[value] happens
     * (see [ConditionValue]), not before [opensFrom] (null means now).
     * [place] is required when [group] is [ConditionGroup.Place].
     */
    suspend fun sendMessage(
        group: ConditionGroup,
        value: String,
        place: Place? = null,
        opensFrom: Instant? = null,
        text: String,
        audio: Recording? = null,
    ): Result<Message> = call { c, me ->
        require(group != ConditionGroup.Place || place != null) { "a place message needs a place" }
        val couple = currentCouple(c, me)?.takeIf { it.isActive } ?: throw HeartError.NotConnected
        val audioPath = audio?.let { upload(c, couple.id, it.file) }
        val row = NewMessage(
            coupleId = couple.id,
            sender = me,
            receiver = couple.partnerOf(me),
            conditionGroup = group.key,
            conditionValue = value,
            placeName = place?.name,
            placeLat = place?.lat,
            placeLng = place?.lng,
            placeRadiusM = place?.radiusM,
            opensFrom = opensFrom,
            body = text,
            audioPath = audioPath,
            audioMs = audio?.durationMs,
        )
        c.from("messages").insert(row) { select() }.decodeSingle<Message>()
    }

    /** Messages I sent, newest first. */
    suspend fun sentMessages(): List<Message> = read(emptyList()) { c, me ->
        c.from("messages").select {
            filter { eq("sender", me) }
            order("created_at", Order.DESCENDING)
        }.decodeList<Message>()
    }

    /** Messages I received (opened or not), newest first. */
    /** [receivedMessages], or null when the read failed, so a passing network error does not empty a list. */
    suspend fun receivedMessagesOrNull(): List<Message>? = call { c, me ->
        c.from("messages").select {
            filter { eq("receiver", me) }
            order("created_at", Order.DESCENDING)
        }.decodeList<Message>()
    }.onFailure { Log.w("HeartRepository", "received messages failed: ${it.message}") }.getOrNull()

    suspend fun receivedMessages(): List<Message> = read(emptyList()) { c, me ->
        c.from("messages").select {
            filter { eq("receiver", me) }
            order("created_at", Order.DESCENDING)
        }.decodeList<Message>()
    }

    /**
     * Messages for me that have not opened yet, earliest first. Their voice files are downloaded
     * to the cache here, so they can play later without a connection.
     */
    suspend fun pendingMessagesForMe(): List<PendingMessage> = read(emptyList()) { c, me -> pending(c, me) }

    /** [pendingMessagesForMe] kept fresh: a new message from my partner shows up without a restart. */
    fun watchPendingMessages(): Flow<List<PendingMessage>> = flow {
        val me = try { Backend.userId() } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
        if (me == null) emit(emptyList())
        else emitAll(live(listOf(Watch("messages", "receiver", me)), emptyList()) { c, id -> pending(c, id) })
    }

    /** Reports that a message opened in my car and/or was listened to. */
    suspend fun markMessage(id: String, opened: Boolean, heard: Boolean): Result<Unit> = call { c, _ ->
        c.postgrest.rpc("mark_message", buildJsonObject {
            put("p_id", id)
            put("p_opened", opened)
            put("p_heard", heard)
        })
        Unit
    }

    /** Local copy of any voice file in the bucket (answers, sent messages). Null when it cannot be fetched. */
    suspend fun voiceFile(audioPath: String): File? = read(null) { c, _ -> download(c, audioPath) }

    // ------------------------------------------------------------ driving

    /** Tells my partner whether I am driving. Only this flag leaves the car, never a location. */
    suspend fun setDriving(driving: Boolean): Result<Unit> = call { c, _ ->
        c.postgrest.rpc("set_driving", buildJsonObject { put("p_driving", driving) })
        Unit
    }

    /** Whether my partner is driving now, kept fresh with realtime. Null when not connected. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun partnerDriving(): Flow<Boolean?> =
        myCouple().map { couple ->
            val me = try { Backend.userId() } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
            if (couple != null && couple.isActive && me != null) couple.partnerOf(me) else null
        }.distinctUntilChanged().flatMapLatest { partnerId ->
            if (partnerId == null) flowOf(null)
            else live(listOf(Watch("drive_status", "user_id", partnerId)), null) { c, _ ->
                c.from("drive_status").select {
                    filter { eq("user_id", partnerId) }
                }.decodeSingleOrNull<DriveStatus>()?.driving ?: false
            }
        }

    /** Adds [km] to today's distance (Korean date). Call it as the car moves. */
    suspend fun addDistance(km: Double): Result<Unit> = call { c, _ ->
        c.postgrest.rpc("add_distance", buildJsonObject { put("p_km", km) })
        Unit
    }

    /** Kilometres both of us have driven since we connected. */
    suspend fun coupleDistance(): Double? = read(null) { c, _ ->
        c.postgrest.rpc("couple_distance").data.trim().trim('"').toDoubleOrNull()
    }

    // ------------------------------------------------------------ internals

    private suspend fun profile(c: SupabaseClient, id: String): Profile? =
        c.from("profiles").select { filter { eq("id", id) } }.decodeSingleOrNull<Profile>()

    /** Same rule as couple_of() in SQL: newest pending or active couple I am in. */
    private suspend fun currentCouple(c: SupabaseClient, me: String): Couple? =
        c.from("couples").select {
            filter {
                or {
                    eq("requester", me)
                    eq("partner", me)
                }
                isIn("status", listOf(Couple.STATUS_PENDING, Couple.STATUS_ACTIVE))
            }
            order("created_at", Order.DESCENDING)
            limit(1)
        }.decodeSingleOrNull<Couple>()

    private suspend fun question(c: SupabaseClient, me: String, coupleId: String, no: Int): QuestionState? {
        val q = c.from("questions").select { filter { eq("no", no) } }.decodeSingleOrNull<Question>() ?: return null
        // the answers policy only lets my partner's row through once both answered
        val answers = c.from("answers").select {
            filter {
                eq("couple_id", coupleId)
                eq("question_no", no)
            }
        }.decodeList<Answer>()
        return QuestionState(q, answers.firstOrNull { it.userId == me }, answers.firstOrNull { it.userId != me })
    }

    private suspend fun pending(c: SupabaseClient, me: String): List<PendingMessage> {
        val list = c.from("messages").select {
            filter {
                eq("receiver", me)
                exact("opened_at", null)
            }
            order("opens_from", Order.ASCENDING)
        }.decodeList<Message>()
        return list.map { m ->
            val local = m.audioPath?.let {
                try { download(c, it) } catch (e: CancellationException) { throw e } catch (e: Exception) {
                    Log.w(TAG, "voice download failed for ${m.id}", e)
                    null
                }
            }
            PendingMessage(m, local)
        }
    }

    /** Uploads a recording to voice/<couple_id>/<uuid>.m4a and returns the path inside the bucket. */
    private suspend fun upload(c: SupabaseClient, coupleId: String, file: File): String {
        val path = "$coupleId/${UUID.randomUUID()}.m4a"
        val bytes = withContext(Dispatchers.IO) { file.readBytes() }
        c.storage.from(VOICE_BUCKET).upload(path, bytes) {
            upsert = false
            contentType = ContentType("audio", "mp4")
        }
        return path
    }

    /** Downloads a voice file once into cacheDir/voice-in and reuses it afterwards. */
    private suspend fun download(c: SupabaseClient, audioPath: String): File {
        val dir = File(appContext.cacheDir, "voice-in")
        val file = File(dir, audioPath.replace('/', '_'))
        if (file.exists() && file.length() > 0) return file
        val bytes = c.storage.from(VOICE_BUCKET).downloadAuthenticated(audioPath)
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            val tmp = File(dir, file.name + ".part")
            tmp.writeBytes(bytes)
            tmp.renameTo(file)
        }
        return file
    }

    /** Runs a write. Offline: fails with [HeartError.Offline]. Any other failure is mapped to a [HeartError]. */
    private suspend fun <T> call(block: suspend (SupabaseClient, String) -> T): Result<T> {
        val c = Backend.client ?: return Result.failure(HeartError.Offline)
        return try {
            val me = Backend.userId() ?: return Result.failure(HeartError.Offline)
            Result.success(block(c, me))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // supabase-kt puts the request headers (with the user's token) into the message, so log only its first line
            Log.w(TAG, "request failed: ${e::class.simpleName}: ${e.message?.lineSequence()?.firstOrNull()}")
            Result.failure(HeartError.from(e))
        }
    }

    /** Runs a read. Offline or on failure it returns [fallback]. */
    private suspend fun <T> read(fallback: T, block: suspend (SupabaseClient, String) -> T): T =
        call(block).getOrElse { fallback }

    /** Rows to watch over realtime: a table, optionally narrowed to column = value. */
    private data class Watch(val table: String, val column: String? = null, val value: String? = null)

    /**
     * Emits [load] once, then again after every realtime change in [watches], after each realtime
     * (re)join, and every [POLL_MS] as a safety net. A failed reload keeps the last value.
     */
    private fun <T> live(watches: List<Watch>, initial: T, load: suspend (SupabaseClient, String) -> T): Flow<T> {
        val c = Backend.client ?: return flowOf(initial)
        return flow {
            // sign in first so realtime joins with the user's token and the table policies apply
            val signedIn = call { _, _ -> Unit }.isSuccess
            val triggers = if (signedIn) {
                merge(flowOf(Unit), poll(), *watches.map { changes(c, it) }.toTypedArray())
            } else {
                merge(flowOf(Unit), poll())
            }
            var emitted = false
            triggers.conflate().collect {
                val r = call(load)
                if (r.isSuccess) {
                    emit(r.getOrThrow())
                    emitted = true
                } else if (!emitted) {
                    emit(initial)
                    emitted = true
                }
            }
        }.distinctUntilChanged()
    }

    private fun poll(): Flow<Unit> = flow {
        while (true) {
            delay(POLL_MS)
            emit(Unit)
        }
    }

    /** One realtime channel for one [Watch]; emits Unit on every change and on each successful join. */
    private fun changes(c: SupabaseClient, w: Watch): Flow<Unit> = flow {
        val channel = c.channel("${w.table}-${UUID.randomUUID()}")
        try {
            val events = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = w.table
                if (w.column != null && w.value != null) filter(w.column, FilterOperator.EQ, w.value)
            }
            channel.subscribe()
            val joined = channel.status.filter { it == RealtimeChannel.Status.SUBSCRIBED }.map { }
            emitAll(merge(events.map { }, joined))
        } finally {
            withContext(NonCancellable) {
                try { c.realtime.removeChannel(channel) } catch (e: Exception) { Log.w(TAG, "removeChannel", e) }
            }
        }
    }.catch { e ->
        // realtime trouble must not break the screen; the poll keeps the data coming
        if (e is CancellationException) throw e
        Log.w(TAG, "realtime ${w.table} stopped", e)
    }

    companion object {
        private const val TAG = "HeartRepository"
        private const val VOICE_BUCKET = "voice"
        private const val POLL_MS = 60_000L

        @Volatile private var instance: HeartRepository? = null

        fun get(context: Context): HeartRepository =
            instance ?: synchronized(this) {
                instance ?: HeartRepository(context.applicationContext).also { instance = it }
            }
    }
}
