package kr.heartblink.car.data

import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.Executors
import ai.pleos.playground.stt.SpeechToText as PleosSpeech

/**
 * Writes down a voice while it is recorded, with the Pleos speech recognizer running on the car itself.
 *
 * The recognizer does not open the microphone here: [VoiceRecorder] does, and hands every piece of
 * sound to [feed] (16 kHz mono PCM). The recognizer ends a sentence by itself after a pause and keeps
 * going, so the sentences are joined. Call [start], [stop] and [cancel] from the main thread.
 */
class SpeechToText(context: Context) {
    private val app = context.applicationContext
    private var onText: (String) -> Unit = {}
    private val sentences = mutableListOf<String>()
    private var partial = ""
    private var stopping: CompletableDeferred<Unit>? = null

    /** Between [start] and [stop] or [cancel]. */
    @Volatile private var active = false

    /** Starts writing down; [onText] gets the words so far each time they change. */
    fun start(onText: (String) -> Unit) {
        cancel()
        this.onText = onText
        sentences.clear()
        partial = ""
        active = true
        Engine.attach(app, this)
        // a short silence before the first word, so the recognizer does not clip it
        repeat(3) { feed(Silence) }
    }

    /** A piece of the recording, 16 kHz mono 16-bit PCM. Can be called from any thread. */
    fun feed(pcm: ByteArray) {
        if (active) Engine.send(this, pcm)
    }

    /** Ends the sound and returns everything written down (empty when nothing was understood). */
    suspend fun stop(): String {
        if (!active) return words()
        // and half a second after the last one, so the end of the sentence is found
        repeat(5) { feed(Silence) }
        Engine.complete()
        if (!Engine.failed) {
            val done = CompletableDeferred<Unit>()
            stopping = done
            // the last sentence is written down a moment after the sound ends
            withTimeoutOrNull(if (partial.isBlank()) QUIET_WAIT_MS else STOP_WAIT_MS) { done.await() }
        }
        finish()
        return words()
    }

    /** Stops without waiting for the last sentence. */
    fun cancel() {
        if (!active) return
        Engine.complete()
        finish()
    }

    private fun finish() {
        active = false
        stopping = null
        Engine.detach(this)
    }

    private fun words(): String = (sentences + partial).filter { it.isNotBlank() }.joinToString(" ").trim()

    internal fun onWords(text: String, isFinal: Boolean) {
        if (!active) return
        if (isFinal) {
            Log.i(TAG, "heard \"$text\"")
            if (text.isNotBlank()) sentences += text.trim()
            partial = ""
            stopping?.complete(Unit)
        } else {
            partial = text
        }
        onText(words())
    }

    internal fun onFailed() {
        stopping?.complete(Unit)
    }

    /**
     * The one recognizer of the app; it takes a moment to set up, so it is kept once it is ready.
     * Sound goes to it through one thread, in the order it was recorded.
     */
    private object Engine {
        private val main = Handler(Looper.getMainLooper())
        private val line = Executors.newSingleThreadExecutor { Thread(it, "speech-feed") }
        private var speech: PleosSpeech? = null
        @Volatile private var ready = false
        @Volatile var failed = false
            private set
        @Volatile private var session: SpeechToText? = null

        /** Sound recorded before the recognizer was ready (used on the line thread only). */
        private val waiting = ArrayDeque<ByteArray>()

        private val listener = object : ResultListener {
            override fun onUpdated(text: String, isFinal: Boolean) {
                main.post { session?.onWords(text, isFinal) }
            }

            override fun onError(error: ErrorType) {
                Log.i(TAG, "recognizer error $error")
                main.post { session?.onFailed() }
            }

            override fun onEndedRecognition() {}
            override fun onUpdatedEpdData(start: Long, end: Long) {}
            override fun onStartedRecognition() {}
            override fun onReady() {}
            override fun onUpdatedRms(rms: Double) {}
        }

        fun prepare(context: Context) {
            if (speech != null || failed) return
            val s = runCatching { PleosSpeech(context.applicationContext, Mode.ON_DEVICE) }
                .onFailure { Log.w(TAG, "no speech recognizer", it) }
                .getOrNull()
            if (s == null) {
                failed = true
                return
            }
            speech = s
            runCatching {
                s.initialize(object : OnInitializeListener {
                    override fun onSuccess() {
                        main.post {
                            runCatching {
                                s.addListener(listener)
                                s.setLocale(Locale.KOREA)
                            }.onFailure { Log.w(TAG, "listener failed", it) }
                            Log.i(TAG, "recognizer ready")
                            line.execute {
                                ready = true
                                // what was recorded meanwhile, a little faster than real time
                                while (waiting.isNotEmpty()) {
                                    push(waiting.removeFirst())
                                    Thread.sleep(20)
                                }
                            }
                        }
                    }

                    override fun onFailure(error: ErrorType) {
                        Log.w(TAG, "recognizer setup failed: $error")
                        giveUp()
                    }
                })
            }.onFailure {
                Log.w(TAG, "recognizer setup failed", it)
                giveUp()
            }
        }

        private fun giveUp() {
            failed = true
            ready = false
            speech = null
            main.post { session?.onFailed() }
        }

        fun attach(context: Context, s: SpeechToText) {
            prepare(context)
            session = s
            line.execute { waiting.clear() }
            // the car's speech service can restart and drop the listener; hook it up again each time
            if (ready) speech?.let { sp ->
                runCatching { sp.removeListener(listener) }
                runCatching { sp.addListener(listener) }.onFailure { Log.w(TAG, "listener failed", it) }
            }
        }

        fun detach(s: SpeechToText) {
            if (session === s) session = null
        }

        fun send(s: SpeechToText, pcm: ByteArray) {
            line.execute {
                if (session !== s || failed) return@execute
                if (ready) {
                    push(pcm)
                } else if (waiting.size < MAX_WAITING) {
                    waiting.addLast(pcm)
                }
            }
        }

        fun complete() {
            line.execute {
                waiting.clear()
                if (ready) runCatching { speech?.completeAudioSend() }.onFailure { Log.w(TAG, "complete failed", it) }
            }
        }

        private fun push(pcm: ByteArray) {
            runCatching { speech?.sendAudio(pcm) }.onFailure { Log.w(TAG, "send failed", it) }
        }
    }

    companion object {
        private const val TAG = "SpeechToText"
        private const val STOP_WAIT_MS = 4_000L
        private const val QUIET_WAIT_MS = 1_500L

        /** 30 seconds of 100 ms pieces. */
        private const val MAX_WAITING = 300

        /** 100 ms of silence at 16 kHz, 16-bit mono. */
        private val Silence = ByteArray(3200)

        /** Sets the recognizer up ahead of the first recording, so the first words are not missed. */
        fun prepare(context: Context) = Engine.prepare(context)

        /** True unless this car turned out to have no speech recognizer. */
        fun available(context: Context): Boolean {
            Engine.prepare(context)
            return !Engine.failed
        }
    }
}
