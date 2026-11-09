package kr.heartblink.car.data

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Process
import android.util.Log
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.util.UUID

/** A finished recording: an .m4a file (AAC in MPEG-4) and its length. */
data class Recording(val file: File, val durationMs: Int)

/**
 * Records a voice message from the car microphone: 16 kHz mono PCM, saved as AAC in MPEG-4.
 *
 * Only this recorder opens the microphone. A second capture at the same time (the speech recognizer)
 * gets silence on the car, so the raw sound is handed to [onAudio] as it comes in, in 100 ms pieces,
 * on the recording thread.
 * RECORD_AUDIO is a runtime permission; ask for it before calling [start].
 */
class VoiceRecorder(private val context: Context, private val onAudio: ((ByteArray) -> Unit)? = null) {
    private var worker: Worker? = null
    private var file: File? = null

    val isRecording: Boolean get() = worker != null

    /** Starts recording into a new file in the cache. Throws if the microphone cannot be opened. */
    @SuppressLint("MissingPermission")
    fun start(): File {
        cancel()
        val dir = File(context.cacheDir, "voice-out").apply { mkdirs() }
        val out = File(dir, "${UUID.randomUUID()}.m4a")
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        // a 1.6 s buffer rides out a busy moment of the car's CPU without losing sound
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(min, CHUNK * 16),
        )
        try {
            check(record.state == AudioRecord.STATE_INITIALIZED) { "microphone not available" }
            record.startRecording()
            check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "microphone did not start" }
        } catch (e: Exception) {
            record.release()
            throw e
        }
        worker = Worker(record, out).also { it.start() }
        file = out
        return out
    }

    /** Stops and returns the recording, or null when nothing usable was recorded (too short or failed). */
    fun stop(): Recording? {
        val w = worker ?: return null
        val out = file
        worker = null
        file = null
        w.finish()
        val durationMs = (w.samples * 1000 / RATE).toInt()
        if (!w.saved || durationMs < MIN_MS || out == null || !out.exists() || out.length() == 0L) {
            Log.w(TAG, "recording not usable: ${durationMs}ms saved=${w.saved}")
            out?.delete()
            return null
        }
        return Recording(out, durationMs)
    }

    /** Throws the recording away. */
    fun cancel() {
        val out = file
        worker?.finish()
        worker = null
        file = null
        out?.delete()
    }

    /** Reads the microphone, hands each piece to [onAudio] and encodes it into [out]. */
    private inner class Worker(private val record: AudioRecord, private val out: File) : Thread("voice-recorder") {
        @Volatile private var running = true

        /** Samples read so far (16 kHz). */
        @Volatile var samples = 0L
            private set

        /** True once the file was written completely. */
        @Volatile var saved = false
            private set

        /** Stops reading and waits until the file is closed. */
        fun finish() {
            running = false
            join(3_000)
        }

        override fun run() {
            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var track = -1
            var written = false
            try {
                val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, RATE, 1).apply {
                    setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, CHUNK)
                }
                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                codec.start()
                // the microphone is read on its own urgent thread, so a slow encoder never makes it drop sound
                val pieces = LinkedBlockingQueue<ByteArray>()
                val reader = Thread({
                    Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                    var short = 0
                    while (running) {
                        val buf = ByteArray(CHUNK)
                        val n = record.read(buf, 0, CHUNK)
                        if (n < 0) {
                            Log.w(TAG, "microphone read failed: $n")
                            break
                        }
                        if (n == 0) continue
                        if (n < CHUNK) short++
                        val piece = if (n == CHUNK) buf else buf.copyOf(n)
                        onAudio?.let { give -> runCatching { give(piece) } }
                        pieces.put(piece)
                    }
                    if (short > 0) Log.i(TAG, "short reads: $short")
                    pieces.put(EndOfSound)
                }, "voice-mic")
                reader.start()
                val info = MediaCodec.BufferInfo()
                var ended = false
                var done = false
                var encoded = 0L
                while (!done) {
                    if (!ended) {
                        val piece = pieces.poll(20, TimeUnit.MILLISECONDS)
                        if (piece === EndOfSound) {
                            feed(codec, piece, 0, encoded * 1_000_000 / RATE, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            ended = true
                        } else if (piece != null) {
                            feed(codec, piece, piece.size, encoded * 1_000_000 / RATE, 0)
                            encoded += piece.size / 2
                            samples = encoded
                        }
                    }
                    while (true) {
                        val i = codec.dequeueOutputBuffer(info, if (ended) 10_000 else 0)
                        if (i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            track = muxer.addTrack(codec.outputFormat)
                            muxer.start()
                        } else if (i >= 0) {
                            val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                            if (!isConfig && info.size > 0 && track >= 0) {
                                muxer.writeSampleData(track, codec.getOutputBuffer(i)!!, info)
                                written = true
                            }
                            codec.releaseOutputBuffer(i, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                done = true
                                break
                            }
                        } else if (i == MediaCodec.INFO_TRY_AGAIN_LATER) {
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "recording failed", e)
            } finally {
                runCatching { record.stop() }
                record.release()
                runCatching { codec.stop() }
                codec.release()
                val closed = track >= 0 && written && runCatching { muxer.stop() }.isSuccess
                muxer.release()
                saved = closed
            }
        }

        private fun feed(codec: MediaCodec, buf: ByteArray, size: Int, timeUs: Long, flags: Int) {
            var i: Int
            do { i = codec.dequeueInputBuffer(10_000) } while (i < 0)
            val input = codec.getInputBuffer(i)!!
            input.clear()
            input.put(buf, 0, size)
            codec.queueInputBuffer(i, 0, size, timeUs, flags)
        }
    }

    companion object {
        private const val TAG = "VoiceRecorder"

        /** Sample rate of the recording; the car's speech recognizer takes the same 16 kHz sound. */
        const val RATE = 16_000
        private const val CHUNK = RATE / 10 * 2
        private const val BIT_RATE = 48_000
        private const val MIN_MS = 500

        /** Marks the end of the microphone pieces for the encoder. */
        private val EndOfSound = ByteArray(0)
    }
}

/** Plays one local voice file at a time. Call [release] when the screen goes away. */
class VoicePlayer {
    private var player: MediaPlayer? = null
    private val _playing = MutableStateFlow(false)

    /** True while a file is playing. */
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    /** Plays [file] from the start. [onDone] runs when it reaches the end. */
    fun play(file: File, onDone: () -> Unit = {}) {
        release()
        val p = MediaPlayer()
        try {
            p.setDataSource(file.absolutePath)
            p.setOnCompletionListener {
                _playing.value = false
                onDone()
            }
            p.prepare()
            p.start()
        } catch (e: Exception) {
            p.release()
            throw e
        }
        player = p
        _playing.value = true
    }

    fun pause() {
        player?.takeIf { it.isPlaying }?.pause()
        _playing.value = false
    }

    fun resume() {
        player?.let {
            it.start()
            _playing.value = true
        }
    }

    /** Length of the loaded file in ms, or 0. */
    val durationMs: Int get() = player?.duration ?: 0

    /**
     * Progress of the loaded file from 0 to 1, sampled every [intervalMs] while collected.
     * Collect it in the screen (for example with collectAsState) to draw a bar or waveform cursor.
     */
    fun progress(intervalMs: Long = 100): Flow<Float> = flow {
        while (true) {
            val p = player
            val value = if (p == null) 0f else {
                val d = p.duration
                if (d > 0) (p.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
            }
            emit(value)
            delay(intervalMs)
        }
    }

    fun release() {
        player?.release()
        player = null
        _playing.value = false
    }
}
