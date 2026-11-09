package kr.heartblink.car.app

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kr.heartblink.car.data.Message
import kr.heartblink.car.data.QuestionState
import kr.heartblink.car.data.Recording
import kr.heartblink.car.data.SpeechToText
import kr.heartblink.car.data.VoicePlayer
import kr.heartblink.car.data.VoiceRecorder
import java.util.Locale
import kotlin.coroutines.resume
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode

/**
 * Talks to the driver while the screen is locked (하트깜빡이_주행중_흐름_설계.md, chapter 2):
 * a message that opened, my partner's answer, today's question and "driving together", one at a time,
 * only at a safe moment, with a spoken yes or no. Silence or a word it does not know means "later".
 * After parking it says once that something is waiting in the app.
 */
object DriveTalk {
    private const val TAG = "DriveTalk"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private lateinit var app: Context

    /** Shorter waits for trying the flow on the emulator (`--ez talk_fast true`). */
    var fast = false

    private val gapMs get() = if (fast) 15_000L else 3 * 60_000L
    private val maxWaitMs get() = if (fast) 60_000L else 5 * 60_000L
    private val questionAfterMs get() = if (fast) 20_000L else 3 * 60_000L
    private const val ANSWER_MS = 6_000L

    /** True while the app is on the screen; a message that opens then is shown, not told. */
    @Volatile var appVisible = false

    private val _status = MutableStateFlow<String?>(null)
    /** One line for 04-1: what waits for parking, or what was done on this drive. */
    val status: StateFlow<String?> = _status

    private sealed class Talk(val rank: Int) {
        class Arrived(val message: Message) : Talk(1)
        class PartnerAnswer(val q: QuestionState) : Talk(2)
        data object Question : Talk(3)
        data object Together : Talk(4)
    }

    private val queue = mutableListOf<Talk>()
    private val doneThisDrive = mutableSetOf<String>()
    private var lastTalkAt = 0L
    private var answeredThisDrive = false
    private var drive: Job? = null

    fun start(context: Context) {
        if (job != null) return
        app = context.applicationContext
        Speaker.prepare(app)
        job = scope.launch {
            launch {
                var before: Boolean? = null
                CarSignals.driving.filterNotNull().collect { driving ->
                    val was = before
                    before = driving
                    if (driving && was != true) onDriveStart()
                    if (!driving && was == true) onParked()
                }
            }
            // a message that opened: told while driving, announced when parked and the app is not on screen
            launch {
                var seen = emptySet<String>()
                MessageOpener.waiting.collect { list ->
                    val fresh = list.filter { it.id !in seen }
                    seen = seen + list.map { it.id }
                    if (fresh.isEmpty()) return@collect
                    if (CarSignals.driving.value == true) {
                        fresh.forEach { enqueue(Talk.Arrived(it), "m:${it.id}") }
                    } else if (!appVisible) {
                        say("${partner()}님 메시지가 열렸어요. 하트깜빡이를 열어 보세요")
                    }
                    updateStatus()
                }
            }
            // my partner answered after me: their answer is open now
            launch {
                var lastNo: Int? = null
                snapshotFlow { Session.revealed }.collect { q ->
                    if (q != null && q.question.no != lastNo && CarSignals.driving.value == true) {
                        lastNo = q.question.no
                        enqueue(Talk.PartnerAnswer(q), "a:${q.question.no}")
                    }
                }
            }
            launch {
                snapshotFlow { Session.partnerDriving }.collect { both ->
                    if (both && CarSignals.driving.value == true) enqueue(Talk.Together, "together")
                }
            }
        }
    }

    private fun onDriveStart() {
        queue.clear()
        doneThisDrive.clear()
        answeredThisDrive = false
        lastTalkAt = SystemClock.elapsedRealtime()
        drive?.cancel()
        drive = scope.launch {
            launch {
                delay(questionAfterMs)
                if (Session.question?.mine == null) enqueue(Talk.Question, "question")
            }
            if (Session.partnerDriving) enqueue(Talk.Together, "together")
            run()
        }
        updateStatus()
    }

    private fun onParked() {
        drive?.cancel()
        drive = null
        queue.clear()
        Listener.cancel()
        Speaker.stop()
        updateStatus()
        // what waited for parking: said once, the app shows it from the envelope
        if (MessageOpener.waiting.value.isNotEmpty() && !appVisible) {
            scope.launch {
                delay(3_000)
                say("${partner()}님 메시지가 기다려요. 하트깜빡이를 열어 보세요")
            }
        }
    }

    private fun enqueue(t: Talk, key: String) {
        if (key in doneThisDrive || queue.any { keyOf(it) == key }) return
        queue += t
        queue.sortBy { it.rank }
        Log.i(TAG, "queued $key")
    }

    private fun keyOf(t: Talk) = when (t) {
        is Talk.Arrived -> "m:${t.message.id}"
        is Talk.PartnerAnswer -> "a:${t.q.question.no}"
        Talk.Question -> "question"
        Talk.Together -> "together"
    }

    /** One talk at a time, each at a safe moment at least [gapMs] after the last one. */
    private suspend fun run() {
        while (scope.isActive) {
            val t = queue.firstOrNull()
            if (t == null) {
                delay(1_000)
                continue
            }
            val since = SystemClock.elapsedRealtime() - lastTalkAt
            if (since < gapMs) {
                delay(gapMs - since)
                continue
            }
            val waited = SystemClock.elapsedRealtime()
            while (!safeNow() && SystemClock.elapsedRealtime() - waited < maxWaitMs) delay(1_000)
            queue.remove(t)
            doneThisDrive += keyOf(t)
            if (!safeNow()) {
                Log.i(TAG, "no safe moment for ${keyOf(t)}, after parking")
                continue
            }
            Log.i(TAG, "talking ${keyOf(t)}")
            runCatching { talk(t) }.onFailure { Log.w(TAG, "talk failed", it) }
            lastTalkAt = SystemClock.elapsedRealtime()
            updateStatus()
        }
    }

    /** The navigation is not speaking. (Speed and turn distance come once the car gives them, design 2.2.) */
    private fun safeNow(): Boolean {
        val am = app.getSystemService(AudioManager::class.java)
        val guiding = am.activePlaybackConfigurations.any {
            it.audioAttributes.usage == AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
        }
        val turnSoon = NaviWatch.nextTurnM?.let { it < 300.0 } == true
        return !guiding && !turnSoon
    }

    private suspend fun talk(t: Talk) {
        val name = partner()
        when (t) {
            is Talk.Arrived -> {
                if (!ask("${name}님 메시지가 왔어요. 지금 들을까요, 주차하고 볼까요?")) {
                    say("주차하면 보여 드릴게요")
                    return
                }
                playMessage(t.message)
            }
            is Talk.PartnerAnswer -> {
                val answer = t.q.partner ?: return
                if (!ask("${name}님도 오늘의 질문에 답했어요. 지금 들을까요?")) {
                    say("주차하면 오늘의 질문에서 볼 수 있어요")
                    return
                }
                val path = answer.audioPath
                if (path != null) playRemote(path) else say(answer.body)
            }
            Talk.Question -> askQuestion()
            Talk.Together -> say("지금 ${name}님도 운전 중이에요")
        }
    }

    private suspend fun playMessage(m: Message) {
        val path = m.audioPath
        if (path != null) playRemote(path) else say(m.body)
        Session.repository?.markMessage(m.id, opened = true, heard = true)
        MessageOpener.refreshWaiting()
    }

    private suspend fun playRemote(path: String) {
        val file = Session.repository?.voiceFile(path) ?: return
        val player = VoicePlayer()
        try {
            suspendCancellableCoroutine { cont ->
                cont.invokeOnCancellation { player.release() }
                runCatching { player.play(file) { if (cont.isActive) cont.resume(Unit) } }
                    .onFailure { if (cont.isActive) cont.resume(Unit) }
            }
        } finally {
            player.release()
        }
    }

    /** Today's question by voice: the answer is played back and sent unless the driver says 다시. */
    private suspend fun askQuestion() {
        val q = Session.question ?: return
        if (q.mine != null) return
        if (!ask("오늘의 질문 들을까요?")) return
        say("${q.question.no}번째 질문이에요. ${q.question.body} 삐 소리 뒤에 말해 주세요")
        repeat(2) { attempt ->
            val heard = Listener.answer() ?: run {
                say("잘 못 들었어요. 주차하고 답해 주세요")
                return
            }
            // the couple's names and nicknames, put back as they are, before the answer is read back
            val text = CoupleWords.fix(heard.first).text.ifBlank { "목소리로 답했어요" }
            say("이렇게 남길게요. $text")
            val again = Listener.words(5_000).let { w -> listOf("다시", "아니").any { it in w } }
            if (again && attempt == 0) {
                heard.second?.file?.delete()
                say("다시 말해 주세요")
                return@repeat
            }
            Session.answer(text, heard.second)
                .onSuccess {
                    answeredThisDrive = true
                    say(if (it.bothAnswered) "남겼어요. ${partner()}님 답도 열렸어요" else "남겼어요")
                    if (it.bothAnswered) enqueue(Talk.PartnerAnswer(it), "a:${it.question.no}")
                }
                .onFailure { say("지금은 남기지 못했어요. 주차하고 다시 해 주세요") }
            return
        }
    }

    /** Asks and listens for a short answer; true only for a clear yes. */
    private suspend fun ask(question: String): Boolean {
        say(question)
        val w = Listener.words(ANSWER_MS)
        Log.i(TAG, "answer \"$w\"")
        if (listOf("나중", "주차", "패스", "아니", "괜찮").any { it in w }) return false
        // whole words only: "그래서" or "지금은" said to someone else is not a yes
        val words = w.split(' ').map { it.trim('.', ',', '?', '!') }
        return words.any { it in YesWords || it.startsWith("들려") || it.startsWith("들을") }
    }

    private val YesWords = setOf("응", "어", "네", "예", "그래", "좋아", "지금", "틀어", "틀어줘")

    private suspend fun say(text: String) {
        if (text.isBlank()) return
        Log.i(TAG, "say \"$text\"")
        Speaker.speak(text)
    }

    private fun partner(): String = Session.view.partnerName

    private fun updateStatus() {
        val waiting = MessageOpener.waiting.value.size
        _status.value = when {
            CarSignals.driving.value != true -> null
            waiting > 0 -> "메시지 ${waiting}개가 주차를 기다려요"
            answeredThisDrive -> "오늘의 질문에 답했어요"
            else -> null
        }
    }

    // ------------------------------------------------------------ the car's voice

    /** Pleos TextToSpeech; [speak] returns when the sentence has been read (or could not be). */
    private object Speaker {
        private var tts: TextToSpeech? = null
        private var ready = false
        private var done: CompletableDeferred<Unit>? = null

        fun prepare(context: Context) {
            if (tts != null) return
            val t = runCatching { TextToSpeech(context, TtsMode.ON_DEVICE) }
                .onFailure { Log.w(TAG, "no TTS: ${it.message}") }
                .getOrNull() ?: return
            tts = t
            runCatching {
                t.initialize(object : OnInitializeListener {
                    override fun onSuccess() {
                        runCatching {
                            t.addEventListener(object : EventListener {
                                override fun onDone() = finish()
                                override fun onError(error: TtsError) {
                                    Log.w(TAG, "TTS error $error")
                                    finish()
                                }
                                override fun onStop() = finish()
                                override fun onStart() {}
                                override fun onReady() {}
                                override fun onUpdatedRms(rms: Double) {}
                            })
                            t.setLocale(Locale.KOREA)
                        }
                        ready = true
                        Log.i(TAG, "TTS ready")
                    }

                    override fun onFailure(error: TtsError) {
                        Log.w(TAG, "TTS setup failed: $error")
                    }
                })
            }
        }

        private fun finish() {
            done?.complete(Unit)
        }

        suspend fun speak(text: String) {
            val t = tts
            if (t == null || !ready) return
            val d = CompletableDeferred<Unit>()
            done = d
            if (runCatching { t.speak(text, true) }.isFailure) return
            // about a quarter second a letter; never wait forever for a lost callback
            withTimeoutOrNull(4_000L + text.length * 250L) { d.await() }
        }

        fun stop() {
            runCatching { tts?.stop() }
        }
    }

    // ------------------------------------------------------------ listening

    /** Short spoken answers through the app's own microphone and the car's recognizer. */
    private object Listener {
        private var recorder: VoiceRecorder? = null
        private var speech: SpeechToText? = null

        private fun beep() {
            runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80).startTone(ToneGenerator.TONE_PROP_BEEP, 200) }
        }

        /** What was said in [ms] after a beep, without keeping the recording. */
        suspend fun words(ms: Long): String {
            beep()
            delay(250)
            val s = SpeechToText(app)
            s.start { }
            val r = VoiceRecorder(app, s::feed)
            if (runCatching { r.start() }.isFailure) {
                s.cancel()
                return ""
            }
            recorder = r
            speech = s
            delay(ms)
            r.cancel()
            recorder = null
            val text = s.stop()
            speech = null
            return text
        }

        /** An answer to today's question: stops 2.5 s after the words stop, at most 30 s. */
        suspend fun answer(): Pair<String, Recording?>? {
            beep()
            delay(250)
            var lastChange = SystemClock.elapsedRealtime()
            var any = false
            val s = SpeechToText(app)
            s.start {
                if (it.isNotBlank()) {
                    any = true
                    lastChange = SystemClock.elapsedRealtime()
                }
            }
            val r = VoiceRecorder(app, s::feed)
            if (runCatching { r.start() }.isFailure) {
                s.cancel()
                return null
            }
            recorder = r
            speech = s
            val started = SystemClock.elapsedRealtime()
            while (true) {
                delay(250)
                val now = SystemClock.elapsedRealtime()
                if (now - started > 30_000) break
                if (any && now - lastChange > 2_500) break
                if (!any && now - started > 10_000) break
            }
            val rec = r.stop()
            recorder = null
            val text = s.stop()
            speech = null
            if (!any || rec == null) {
                rec?.file?.delete()
                return null
            }
            return text to rec
        }

        fun cancel() {
            recorder?.cancel()
            recorder = null
            speech?.cancel()
            speech = null
        }
    }
}
