package kr.heartblink.car.app

import android.content.Context
import android.util.Log
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.todayIn
import kr.heartblink.car.data.Backend
import kr.heartblink.car.data.ConditionGroup
import kr.heartblink.car.data.Couple
import kr.heartblink.car.data.HeartRepository
import kr.heartblink.car.data.Message
import kr.heartblink.car.data.Place
import kr.heartblink.car.data.Profile
import kr.heartblink.car.data.QuestionState
import kr.heartblink.car.data.Recording

/** Where the car is in getting connected. The app opens the screen of each stage when the stage changes. */
enum class Stage {
    /** Signing in and loading the profile. */
    Loading,
    /** No profile yet: 01 내 정보 등록. */
    NeedProfile,
    /** No partner yet, or my request is waiting: 01-1 연결 요청 보내기. */
    SendRequest,
    /** Someone asked to connect with my plate: 02 연결 요청 받기. */
    Incoming,
    /** My request was accepted; I still have to name the partner's plate and set the first day: 03. */
    Setup,
    /** Connected: the main screens. */
    Ready,
}

/** Everything the screens show about the two of us, already formatted. */
data class CoupleView(
    val meName: String,
    val myPlate: String,
    /** Nickname my partner gave my plate. Empty until they do. */
    val myNickname: String,
    val partnerName: String,
    val partnerPlate: String,
    /** Nickname I gave my partner's plate. Empty until I do. */
    val partnerNickname: String,
    val firstMet: LocalDate?,
    val partnerDriving: Boolean,
) {
    /** D+ count: days since the first day (the first day itself is D+0). */
    val days: Int
        get() = firstMet?.daysUntil(Clock.System.todayIn(Korea))?.coerceAtLeast(0) ?: 0

    companion object {
        /** The values of the Figma screens, used when the server is not configured. */
        val Demo = CoupleView(
            meName = "지훈", myPlate = "12가 3456", myNickname = "후니",
            partnerName = "서연", partnerPlate = "56다 7890", partnerNickname = "여니",
            firstMet = LocalDate(2025, 9, 20), partnerDriving = true,
        )
    }
}

val Korea: TimeZone = TimeZone.of("Asia/Seoul")

/** "12가3456" as written on a plate: a space before the last four digits. */
fun displayPlate(plate: String): String {
    val p = plate.replace(" ", "")
    return if (p.length > 4) p.dropLast(4) + " " + p.takeLast(4) else p
}

/**
 * The signed-in car and its couple, shared by every screen as Compose state.
 *
 * Offline (no Supabase config) [live] is false, [stage] stays [Stage.Ready] and [view] is the Figma demo,
 * so `--es screen NN` still opens each screen as designed.
 */
object Session {
    private const val TAG = "Session"
    private var repo: HeartRepository? = null
    private var job: Job? = null

    val live: Boolean get() = Backend.enabled

    var myId: String? by mutableStateOf(null)
        private set
    var me: Profile? by mutableStateOf(null)
        private set
    var couple: Couple? by mutableStateOf(null)
        private set
    var partner: Profile? by mutableStateOf(null)
        private set
    var partnerDriving: Boolean by mutableStateOf(false)
        private set
    private var loaded by mutableStateOf(false)

    /** The question we are on now (05), kept fresh with realtime. */
    var question: QuestionState? by mutableStateOf(null)
        private set

    /**
     * The question both of us just finished, so 05 can turn the partner's card over before moving on.
     * 05 clears it when it closes.
     */
    var revealed: QuestionState? by mutableStateOf(null)

    /** The repository once [start] ran, for screens that load lists themselves. */
    val repository: HeartRepository? get() = repo

    val stage: Stage by derivedStateOf {
        val id = myId
        val c = couple
        when {
            !live -> Stage.Ready
            !loaded -> Stage.Loading
            me == null -> Stage.NeedProfile
            c == null -> Stage.SendRequest
            c.isPending && c.requester == id -> Stage.SendRequest
            c.isPending -> Stage.Incoming
            c.requester == id && (c.firstMet == null || c.partnerNickname.isNullOrEmpty()) -> Stage.Setup
            else -> Stage.Ready
        }
    }

    val view: CoupleView by derivedStateOf {
        if (!live) return@derivedStateOf CoupleView.Demo
        val id = myId
        val c = couple
        val m = me
        val p = partner
        CoupleView(
            meName = m?.name.orEmpty(),
            myPlate = m?.plate?.let(::displayPlate).orEmpty(),
            myNickname = if (c != null && id != null) c.myNickname(id).orEmpty() else "",
            partnerName = p?.name.orEmpty(),
            partnerPlate = p?.plate?.let(::displayPlate).orEmpty(),
            partnerNickname = if (c != null && id != null) c.nicknameIGave(id).orEmpty() else "",
            firstMet = c?.firstMet,
            partnerDriving = partnerDriving,
        )
    }

    fun repo(context: Context): HeartRepository = repo ?: HeartRepository.get(context).also { repo = it }

    /** Lives as long as the process, so a recreated activity finds everything already loaded. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Signs in, loads the profile and couple, then keeps the couple and the partner's driving fresh,
     * and tells the partner whenever my gear leaves or returns to P.
     */
    fun start(context: Context) {
        val r = repo(context)
        if (!r.enabled || job != null) return
        job = scope.launch {
            launch {
                // drive_status points at the profile, so nothing is sent before the profile exists
                combine(CarSignals.driving.filterNotNull(), snapshotFlow { me != null }) { driving, registered ->
                    driving.takeIf { registered }
                }.filterNotNull().distinctUntilChanged().collect { driving ->
                    r.setDriving(driving).onFailure { Log.w(TAG, "set_driving failed: ${it.message}") }
                }
            }
            myId = try {
                Backend.userId()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "sign in failed", e)
                null
            }
            me = r.me()
            setCouple(r.couple())
            loaded = true
            launch { r.myCouple().collect { setCouple(it) } }
            launch { r.partnerDriving().collect { partnerDriving = it == true } }
            launch { r.watchCurrentQuestion().collect { onQuestion(it) } }
        }
    }

    /** When the question moves on after I had answered, my partner just answered: keep that one to show. */
    private suspend fun onQuestion(q: QuestionState?) {
        val before = question
        question = q
        if (before != null && q != null && q.question.no > before.question.no && before.mine != null) {
            repo?.questionAt(before.question.no)?.takeIf { it.bothAnswered }?.let { revealed = it }
        }
    }

    private suspend fun setCouple(c: Couple?) {
        val before = couple
        couple = c
        val r = repo ?: return
        if (c == null) {
            partner = null
        } else if (partner == null || before?.id != c.id || before.status != c.status) {
            partner = r.partnerProfile()
        }
    }

    // ------------------------------------------------------------ writes used by the start screens

    suspend fun saveProfile(name: String, plate: String): Result<Profile> =
        requireRepo().saveProfile(name.trim(), plate.replace(" ", "")).onSuccess { me = it }

    suspend fun requestConnection(partnerPlate: String): Result<Couple> =
        requireRepo().requestConnection(partnerPlate.replace(" ", "")).onSuccess { setCouple(it) }

    suspend fun respond(accept: Boolean, nickname: String?): Result<Couple> =
        requireRepo().respondConnection(accept, nickname?.trim()).onSuccess { setCouple(it.takeIf { c -> !c.isEnded }) }

    suspend fun updateCouple(partnerNickname: String?, firstMet: LocalDate?): Result<Couple> =
        requireRepo().updateCouple(partnerNickname?.trim(), firstMet).onSuccess { setCouple(it) }

    // ------------------------------------------------------------ question and message writes

    /** Answers the current question. When I was the second to answer, the finished question is kept in [revealed]. */
    suspend fun answer(text: String, audio: Recording?): Result<QuestionState> =
        requireRepo().answerQuestion(text.trim(), audio).onSuccess { st ->
            if (st.bothAnswered) revealed = st else question = st
        }

    /** Leaves a message that opens in my partner's car when [group]/[value] happens, not before [opensFrom]. */
    suspend fun sendMessage(
        group: ConditionGroup,
        value: String,
        text: String,
        audio: Recording?,
        place: Place? = null,
        opensFrom: Instant? = null,
    ): Result<Message> =
        requireRepo().sendMessage(group, value, place = place, opensFrom = opensFrom, text = text.trim(), audio = audio)

    /** Reloads the couple at once (the realtime update can take a moment). */
    suspend fun refresh() {
        val r = repo ?: return
        setCouple(r.couple())
    }

    private fun requireRepo(): HeartRepository = checkNotNull(repo) { "Session.start was not called" }
}
