package kr.heartblink.car.ui.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kr.heartblink.car.app.Session
import kr.heartblink.car.data.Recording
import kr.heartblink.car.data.SpeechToText
import kr.heartblink.car.data.VoicePlayer
import kr.heartblink.car.data.VoiceRecorder
import kr.heartblink.car.ui.component.FigmaSvg
import kr.heartblink.car.ui.component.Inset
import kr.heartblink.car.ui.component.Layered
import kr.heartblink.car.ui.component.tap
import kr.heartblink.car.ui.theme.Heart
import kr.heartblink.car.ui.theme.HeartType
import java.io.File

/** Longest recording; the bucket takes files up to 2 MB and 64 kbps AAC stays far below that. */
private const val MaxRecordMs = 60_000L

/**
 * A voice being recorded for an answer (05) or a message (06, 07). Lives as long as its draft.
 * While recording, the car's speech recognizer writes the words down and [onWords] puts them in the
 * draft's text box, so the sentence can be checked (and fixed) before it is sent.
 */
internal class VoiceDraft(private val onWords: (String) -> Unit) {
    var recording by mutableStateOf<Recording?>(null)
        private set

    /** elapsedRealtime when the current recording started; null when not recording. */
    var since by mutableStateOf<Long?>(null)
        private set

    /** True from the start of a recording until its words are written down. */
    var transcribing by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)

    private var recorder: VoiceRecorder? = null
    private var speech: SpeechToText? = null
    private var words: Job? = null
    private val scope = MainScope()

    val isRecording: Boolean get() = since != null

    fun start(context: Context) {
        clear()
        // a new recording replaces the sentence, but only once something was understood
        val s = if (SpeechToText.available(context)) {
            SpeechToText(context).also { it.start { words -> if (words.isNotBlank()) onWords(words) } }
        } else null
        val r = VoiceRecorder(context.applicationContext, onAudio = s?.let { it::feed })
        try {
            r.start()
            recorder = r
            since = SystemClock.elapsedRealtime()
        } catch (e: Exception) {
            Log.w("VoiceDraft", "microphone failed", e)
            s?.cancel()
            error = "마이크를 열지 못했어요. 잠시 뒤에 다시 눌러 주세요"
            return
        }
        speech = s
        transcribing = s != null
    }

    fun stop() {
        val r = recorder ?: return
        recorder = null
        since = null
        recording = r.stop()
        val s = speech
        speech = null
        if (recording == null) {
            error = "녹음이 너무 짧아요. 한 번 더 녹음해 주세요"
            s?.cancel()
            transcribing = false
        } else if (s != null) {
            words = scope.launch {
                val text = s.stop()
                if (text.isNotBlank()) onWords(text)
                transcribing = false
            }
        }
    }

    /** Stops a recording still running and waits until its words are in the text box. */
    suspend fun awaitWords() {
        if (isRecording) stop()
        words?.join()
    }

    /** Drops the recording and its file. */
    fun clear() {
        recorder?.cancel()
        recorder = null
        speech?.cancel()
        speech = null
        words?.cancel()
        words = null
        transcribing = false
        since = null
        recording?.file?.delete()
        recording = null
        error = null
    }
}

/** Plays one voice at a time on a screen: a local file, or a file of the voice bucket. */
internal class VoicePlayback(private val scope: CoroutineScope) {
    private val player = VoicePlayer()

    /** Key of what is playing now (a path), or null. */
    var current by mutableStateOf<String?>(null)
        private set

    val progress: Flow<Float> get() = player.progress()

    fun isPlaying(key: String?) = key != null && current == key

    fun toggleFile(file: File) {
        if (isPlaying(file.path)) stop() else play(file.path, file)
    }

    /** Plays (or stops) [audioPath] of the voice bucket, downloading it first when needed. */
    fun toggleRemote(audioPath: String, onStart: () -> Unit = {}) {
        if (isPlaying(audioPath)) {
            stop()
            return
        }
        player.release()
        current = audioPath
        scope.launch {
            val file = Session.repository?.voiceFile(audioPath)
            if (file == null || current != audioPath) {
                if (current == audioPath) current = null
                return@launch
            }
            play(audioPath, file)
            onStart()
        }
    }

    private fun play(key: String, file: File) {
        try {
            player.play(file) { if (current == key) current = null }
            current = key
        } catch (e: Exception) {
            Log.w("VoicePlayback", "play failed", e)
            current = null
        }
    }

    fun stop() {
        player.release()
        current = null
    }
}

@Composable
internal fun rememberVoicePlayback(): VoicePlayback {
    val scope = rememberCoroutineScope()
    val p = remember { VoicePlayback(scope) }
    DisposableEffect(Unit) { onDispose { p.stop() } }
    return p
}

/** Starts recording into [draft], asking for the microphone permission the first time. */
@Composable
internal fun rememberStartRecording(draft: VoiceDraft): () -> Unit {
    val context = LocalContext.current
    LaunchedEffect(Unit) { SpeechToText.prepare(context) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) draft.start(context) else draft.error = "마이크 권한을 허용해야 녹음할 수 있어요"
    }
    return {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) draft.start(context) else ask.launch(Manifest.permission.RECORD_AUDIO)
    }
}

/**
 * The voice pill of 06, live: 녹음하기, then 녹음 중 with a timer and 멈추기, then play, length and 다시 녹음.
 * [compact] fits the 412 px column of a 05 answer card (shorter waveform, round re-record button).
 */
@Composable
internal fun VoicePill(draft: VoiceDraft, playback: VoicePlayback, compact: Boolean, modifier: Modifier = Modifier) {
    val c = Heart.colors
    val pill = RoundedCornerShape(999.dp)
    val start = rememberStartRecording(draft)
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val since = draft.since
    LaunchedEffect(since) {
        while (since != null && draft.since == since) {
            now = SystemClock.elapsedRealtime()
            if (now - since >= MaxRecordMs) draft.stop()
            delay(200)
        }
    }
    val rec = draft.recording
    val waveWidth = if (compact) 90.dp else 192.dp
    val onPill: (() -> Unit)? = when {
        since != null -> ({ draft.stop() })
        rec == null -> start
        else -> null
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(pill)
            .background(c.bgCard)
            .border(2.dp, if (since != null) c.pinkAccent else c.line, pill)
            .tap(onPill)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            since != null -> {
                Box(Modifier.size(52.dp).clip(CircleShape).background(c.pinkAccent), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(Color.White))
                }
                val pulse = rememberInfiniteTransition(label = "rec")
                val a by pulse.animateFloat(0.35f, 1f, infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse), label = "rec")
                FigmaSvg("s06_waveform", Modifier.size(waveWidth, 48.dp).alpha(a))
                Text(durationText((now - since).toInt().coerceAtLeast(0)), style = HeartType.BodyStrong, color = c.pinkDeep, maxLines = 1)
                Spacer(Modifier.weight(1f))
                SmallPill("멈추기", null, icon = !compact)
            }
            rec == null -> {
                MicCircle(52.dp)
                Text("눌러서 녹음하기", style = HeartType.BodyStrong, color = c.textSecondary, maxLines = 1)
            }
            else -> {
                val playing = playback.isPlaying(rec.file.path)
                Box(Modifier.size(52.dp).tap { playback.toggleFile(rec.file) }) {
                    FigmaSvg(if (playing) "s17_pause" else "s06_play", Modifier.size(52.dp))
                }
                FigmaSvg("s06_waveform", Modifier.size(waveWidth, 48.dp))
                Text(durationText(rec.durationMs), style = HeartType.BodyStrong, color = c.textSecondary, maxLines = 1)
                Spacer(Modifier.weight(1f))
                val again = {
                    playback.stop()
                    start()
                }
                if (compact) Box(Modifier.tap(again)) { MicCircle(48.dp) } else SmallPill("다시 녹음", again)
            }
        }
    }
}

@Composable
private fun MicCircle(size: Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Heart.colors.pinkLight), contentAlignment = Alignment.Center) {
        Layered(Modifier.size(24.dp)) { svg("s06_mic", Inset.pct(10.94f, 21.88f), Inset.pct(5.2f, 7.22f)) }
    }
}

@Composable
private fun SmallPill(text: String, onClick: (() -> Unit)?, icon: Boolean = true) {
    val c = Heart.colors
    val pill = RoundedCornerShape(999.dp)
    Row(
        Modifier.clip(pill).background(c.pinkLight).tap(onClick).padding(start = if (icon) 16.dp else 18.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon) Layered(Modifier.size(24.dp)) { svg("s06_mic", Inset.pct(10.94f, 21.88f), Inset.pct(5.2f, 7.22f)) }
        Text(text, style = HeartType.Label, color = c.textPrimary, maxLines = 1)
    }
}
