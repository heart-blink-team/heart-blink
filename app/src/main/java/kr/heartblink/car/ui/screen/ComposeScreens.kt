package kr.heartblink.car.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.heartblink.car.app.CoupleWords
import kr.heartblink.car.app.Session
import kr.heartblink.car.data.Answer
import kr.heartblink.car.data.WordFix
import kr.heartblink.car.ui.component.FigmaSvg
import kr.heartblink.car.ui.component.HeartCar
import kr.heartblink.car.ui.component.PrimaryButton
import kr.heartblink.car.ui.component.Shadow
import kr.heartblink.car.ui.component.Tab
import kr.heartblink.car.ui.component.innerShadow
import kr.heartblink.car.ui.component.outerShadow
import kr.heartblink.car.ui.component.tap
import kr.heartblink.car.ui.theme.Heart
import kr.heartblink.car.ui.theme.HeartType

/** Figma screens 05, 06, 06-1, 07, 08: 오늘의 질문, 메시지 남기기. */
val composeScreens: Map<String, @Composable (Nav) -> Unit> = linkedMapOf<String, @Composable (Nav) -> Unit>(
    "05" to { nav -> QuestionScreen(nav) },
    "06" to { nav -> MessageScreen(nav, ConditionGroup.Vehicle) },
    "06-1" to { nav -> MessageEditScreen(nav) },
    "07" to { nav -> MessageScreen(nav, ConditionGroup.Time) },
    "06-2" to { nav -> MessageScreen(nav, ConditionGroup.FirstStart) },
    "06-3" to { nav -> MessageScreen(nav, ConditionGroup.Arrival) },
    "06-4" to { nav -> MessageScreen(nav, ConditionGroup.Place) },
    "06-5" to { nav -> PlaceMapScreen(nav) },
    "08" to { nav -> MessageSentScreen(nav) },
)

private val MyTape = Tape(27.13f, -9f, 7f)
private val PartnerTape = Tape(321.6f, -25.9f, -6f)
private val MyCardAt = Modifier.offset(190.dp, 302.dp)
private val PartnerCardAt = Modifier.offset(992.dp, 302.dp)

/** The answer being written on 05, kept while the app runs so leaving the tab does not lose it. */
private object AnswerDraft {
    var text by mutableStateOf("")
    var fixes by mutableStateOf<List<WordFix.Change>>(emptyList())
    val voice = VoiceDraft { heard ->
        val f = CoupleWords.fix(heard)
        text = f.text
        fixes = f.changes
    }

    fun revert(change: WordFix.Change) {
        val f = WordFix.Fixed(text, fixes).revert(change)
        text = f.text
        fixes = f.changes
    }

    fun reset() {
        text = ""
        fixes = emptyList()
        voice.clear()
    }
}

/**
 * 05 오늘의 질문. Questions go in order, not by date: the next one opens only after both answered.
 * When both have answered, the partner's card turns over on the Y axis in 0.6 s to show the answer.
 */
@Composable
fun QuestionScreen(nav: Nav) {
    if (Session.live) LiveQuestion(nav) else DemoQuestion(nav)
}

/** The Figma state: both answered, 서연's card turning over. */
@Composable
private fun DemoQuestion(nav: Nav) {
    QuestionLayout(nav, "후니", "여니", "우리의 일기장에 저장했어요. 다음 질문이 열렸어요") {
        QuestionCard(128, "이번 주말에\n나랑 꼭\n하고 싶은 것\n하나는?", "둘 다 답해서\n서로의 답이 열렸어요", fixedBreaks = true)
        AnswerCard("나의 답", "캠핑\n가고 싶어!", "0:04", "from. 후니", MyTape, MyCardAt)
        Flipping { AnswerCard("서연의 답", "캠핑 가서\n같이 별\n보고 싶어!", "0:05", "from. 여니", PartnerTape, PartnerCardAt.then(it)) }
    }
}

@Composable
private fun LiveQuestion(nav: Nav) {
    val v = Session.view
    val revealed = Session.revealed
    val shown = revealed ?: Session.question
    DisposableEffect(Unit) { onDispose { Session.revealed = null } }
    LaunchedEffect(Unit) { CoupleWords.refresh() }
    val playback = rememberVoicePlayback()
    val (action, run) = rememberAction()
    var editing by remember { mutableStateOf(false) }
    var shift by remember { mutableStateOf(false) }
    val footer = if (revealed != null) "우리의 일기장에 저장했어요. 다음 질문이 열렸어요" else null

    Box(Modifier.fillMaxSize()) {
        QuestionLayout(nav, v.myNickname, v.partnerNickname, footer) {
            if (shown == null) {
                QuestionCard(null, "질문을 불러오고 있어요", "")
                return@QuestionLayout
            }
            val partner = v.partnerName
            val status = when {
                shown.bothAnswered -> "둘 다 답해서\n서로의 답이 열렸어요"
                shown.mine != null -> "${partner}님이 답하면\n서로의 답이 열려요"
                else -> "둘 다 답하면\n서로의 답이 열려요"
            }
            QuestionCard(shown.question.no, shown.question.body, status)

            val mine = shown.mine
            if (mine != null) {
                AnswerCard(
                    "나의 답", answerText(mine), mine.audioMs?.let(::durationText), "from. ${v.myNickname}", MyTape, MyCardAt,
                    playing = playback.isPlaying(mine.audioPath),
                    onPlay = mine.audioPath?.let { p -> { playback.toggleRemote(p) } },
                )
            } else {
                AnswerInputCard(
                    editing = editing,
                    playback = playback,
                    error = action.error ?: AnswerDraft.voice.error,
                    busy = action.busy,
                    onEdit = {
                        editing = true
                        AnswerDraft.fixes = emptyList()
                    },
                    onSubmit = {
                        val draft = AnswerDraft.voice
                        if (draft.isRecording) draft.stop()
                        playback.stop()
                        editing = false
                        if (AnswerDraft.text.isBlank() && draft.recording == null) {
                            action.error = "녹음하거나 글을 써 주세요"
                        } else {
                            run({
                                draft.awaitWords()
                                Session.answer(AnswerDraft.text.trim(), draft.recording)
                            }) { AnswerDraft.reset() }
                        }
                    },
                )
            }

            val theirs = shown.partner
            if (theirs != null) {
                key(shown.question.no) {
                    Flipping {
                        AnswerCard(
                            "${partner}의 답", answerText(theirs), theirs.audioMs?.let(::durationText), "from. ${v.partnerNickname}",
                            PartnerTape, PartnerCardAt.then(it),
                            playing = playback.isPlaying(theirs.audioPath),
                            onPlay = theirs.audioPath?.let { p -> { playback.toggleRemote(p) } },
                        )
                    }
                }
            } else {
                HiddenCard("${partner}의 답", if (mine == null) "둘 다 답하면 열려요" else "${partner}님이 답하면 열려요")
            }
        }
        if (editing) {
            HangulKeyboard(
                shift = shift,
                onKey = { AnswerDraft.text = typeJamo(AnswerDraft.text, it); shift = false },
                onShift = { shift = !shift },
                onDelete = { AnswerDraft.text = AnswerDraft.text.dropLast(1) },
                onText = { AnswerDraft.text += it },
                onDone = { editing = false },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

private fun answerText(a: Answer): String = a.body.ifBlank { "목소리로 답했어요" }

/** The two plates, then the cards, the footer line and the doodles on top. */
@Composable
private fun QuestionLayout(nav: Nav, myNickname: String, partnerNickname: String, footer: String?, cards: @Composable BoxScope.() -> Unit) {
    val c = Heart.colors
    HeartWindow(Tab.Question, nav) {
        HeartCar(myNickname, Modifier.offset((-270).dp, 400.dp).size(720.dp, 558.dp))
        HeartCar(partnerNickname, Modifier.offset(1232.dp, 400.dp).size(720.dp, 558.dp))
        cards()
        if (footer != null) {
            Text(
                footer,
                style = HeartType.Label, color = c.textSecondary, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().offset(y = 1008.dp),
            )
        }
        FigmaSvg("s05_sparkle", Modifier.offset(600.dp, 250.dp).size(40.dp))
        FigmaSvg("s05_sparkle_small", Modifier.offset(648.dp, 236.dp).size(24.dp))
        FigmaSvg("s05_doodle_heart", Modifier.offset(1030.dp, 244.dp).size(44.dp))
    }
}

/** Turns its content over on the Y axis in 0.6 s when it first shows. */
@Composable
private fun Flipping(content: @Composable (Modifier) -> Unit) {
    val flip = remember { Animatable(-90f) }
    LaunchedEffect(Unit) { flip.animateTo(0f, tween(600, easing = FastOutSlowInEasing)) }
    content(
        Modifier.graphicsLayer {
            rotationY = flip.value
            cameraDistance = 16f * density
        },
    )
}

/** Korean lines break between words, so a question from the server wraps like the Figma one. */
private val WordBreak = LineBreak.Heading.copy(wordBreak = LineBreak.WordBreak.Phrase)

@Composable
private fun QuestionCard(no: Int?, body: String, status: String, fixedBreaks: Boolean = false) {
    val c = Heart.colors
    val shape = RoundedCornerShape(40.dp)
    val pill = RoundedCornerShape(999.dp)
    Column(
        Modifier
            .offset(560.dp, 222.dp)
            .size(562.dp, 760.dp)
            .outerShadow(shape, Shadow(0f, 18f, 44f, Color(0x0D292124)))
            .clip(shape)
            .background(c.pinkSoft)
            .innerShadow(shape, Shadow(0f, -6f, 16f, Color(0x08292124)), Shadow(0f, 6f, 14f, Color(0xB3FFFFFF)))
            .padding(start = 130.dp, end = 130.dp, top = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.clip(pill).background(c.bgCard).padding(horizontal = 22.dp, vertical = 8.dp)) {
            Text("오늘의 질문", style = HeartType.BodyStrong, color = c.pinkDeep, maxLines = 1)
        }
        Spacer(Modifier.height(if (fixedBreaks) 150.dp else 120.dp))
        if (no != null) Text("${no}번째 질문", style = HeartType.Label, color = c.textSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Text(
            body,
            style = HeartType.gaegu(58f, spacing = -0.04f).copy(lineHeight = 68.sp, lineBreak = WordBreak),
            color = c.textPrimary, textAlign = TextAlign.Center,
            modifier = if (fixedBreaks) Modifier.wrapContentWidth(unbounded = true) else Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(36.dp))
        Text(
            status,
            style = HeartType.Label.copy(lineHeight = 34.sp), color = c.pinkDeep, textAlign = TextAlign.Center,
            modifier = Modifier.wrapContentWidth(unbounded = true),
        )
    }
}

/** Masking tape over a card corner: [x], [y] is its top-left in the card, turned [rotation] degrees. */
private class Tape(val x: Float, val y: Float, val rotation: Float)

/** The white notebook card of an answer: title, pink rule, then [content]; the tape sits on top. */
@Composable
private fun AnswerFrame(title: String, tape: Tape, modifier: Modifier, lines: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val c = Heart.colors
    val shape = RoundedCornerShape(40.dp)
    Box(
        modifier
            .size(500.dp, 760.dp)
            .innerShadow(shape, Shadow(6f, 6f, 12f, Color(0xD9FFFFFF)), Shadow(-6f, -8f, 16f, Color(0x0D292124))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .outerShadow(shape, Shadow(0f, 18f, 40f, Color(0x12292124)))
                .clip(shape)
                .background(c.bgCard)
                .border(2.dp, c.line, shape)
                .padding(start = 46.dp, end = 46.dp, top = 46.dp, bottom = 38.dp),
        ) {
            Text(title, style = HeartType.Headline, color = c.textPrimary, maxLines = 1)
            Spacer(Modifier.height(20.dp))
            Box(Modifier.size(412.dp, 3.dp).clip(RoundedCornerShape(2.dp)).background(c.pinkMid))
            content()
        }
        // notebook lines under the two lines of the answer and one more
        if (lines) {
            listOf(280, 366, 452).forEach { y ->
                Box(Modifier.offset(44.dp, y.dp).size(412.dp, 2.dp).background(c.line))
            }
        }
        MaskingTape(tape.rotation, Modifier.offset(tape.x.dp, tape.y.dp))
    }
}

@Composable
private fun AnswerCard(
    title: String,
    answer: String,
    duration: String?,
    from: String,
    tape: Tape,
    modifier: Modifier,
    playing: Boolean = false,
    onPlay: (() -> Unit)? = null,
) {
    val c = Heart.colors
    // long answers from the server get a smaller hand so they still fit the card
    val big = answer.length <= 16
    AnswerFrame(title, tape, modifier, lines = big) {
        Spacer(Modifier.height(90.dp))
        Text(
            answer,
            style = (if (big) HeartType.gaegu(76f).copy(lineHeight = 86.sp) else HeartType.gaegu(52f).copy(lineHeight = 62.sp)).copy(lineBreak = WordBreak),
            color = c.textPrimary, maxLines = if (big) 4 else 6, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        if (duration != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .background(c.pinkLight)
                    .tap(onPlay)
                    .padding(start = 14.dp, end = 24.dp, top = 14.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FigmaSvg(if (playing) "s17_pause" else "s05_play", Modifier.size(56.dp))
                FigmaSvg("s05_waveform", Modifier.size(180.dp, 44.dp))
                Text(duration, style = HeartType.Label, color = c.pinkDeep, maxLines = 1)
            }
            Spacer(Modifier.height(24.dp))
        }
        Text(from, style = HeartType.gaegu(32f, bold = false, spacing = -0.04f).copy(lineHeight = 40.sp), color = c.textTertiary, maxLines = 1)
    }
}

/** My card before I answer: the 06 voice pill and typed sentence, then 답 남기기. */
@Composable
private fun AnswerInputCard(
    editing: Boolean,
    playback: VoicePlayback,
    error: String?,
    busy: Boolean,
    onEdit: () -> Unit,
    onSubmit: () -> Unit,
) {
    val c = Heart.colors
    AnswerFrame("나의 답", MyTape, MyCardAt, lines = false) {
        Spacer(Modifier.height(30.dp))
        val fixed = !editing && AnswerDraft.fixes.isNotEmpty()
        Text(if (fixed) FixHint else "말로 남기거나 글로 써요", style = HeartType.Label, color = c.textSecondary, maxLines = 2)
        Spacer(Modifier.height(14.dp))
        TranscriptBox(
            AnswerDraft.text, editing, onEdit,
            placeholder = if (AnswerDraft.voice.transcribing) Listening else "눌러서 글로 쓰기",
            fixes = if (editing) emptyList() else AnswerDraft.fixes, onRevert = AnswerDraft::revert,
        )
        Spacer(Modifier.height(20.dp))
        VoicePill(AnswerDraft.voice, playback, compact = true)
        ErrorLine(error)
        Spacer(Modifier.weight(1f))
        PrimaryButton(if (busy) "남기는 중" else "답 남기기", Modifier.fillMaxWidth(), onSubmit.takeUnless { busy })
    }
}

/** The partner's card lying face down until both answered. */
@Composable
private fun HiddenCard(title: String, line: String) {
    val c = Heart.colors
    AnswerFrame(title, PartnerTape, PartnerCardAt, lines = false) {
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            FigmaSvg("s18_lock", Modifier.size(56.dp))
            Text(line, style = HeartType.Body, color = c.textSecondary, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.weight(1.3f))
    }
}

/** Beige tape, 150 x 38, with white stripes every 16 px turned 30 degrees. */
@Composable
private fun MaskingTape(rotation: Float, modifier: Modifier) {
    Canvas(
        modifier
            .size(150.dp, 38.dp)
            .graphicsLayer {
                rotationZ = rotation
                clip = true
            },
    ) {
        drawRect(Color(0xD9EBD9C2))
        val w = 6.dp.toPx()
        val h = 60.dp.toPx()
        val cy = 17.48f.dp.toPx()
        for (i in 0..14) {
            val cx = (-52.4f + 16f * i).dp.toPx()
            rotate(30f, pivot = Offset(cx, cy)) {
                drawRect(Color(0x59FFFFFF), topLeft = Offset(cx - w / 2, cy - h / 2), size = Size(w, h))
            }
        }
    }
}
