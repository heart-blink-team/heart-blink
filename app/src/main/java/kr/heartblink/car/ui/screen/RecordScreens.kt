package kr.heartblink.car.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kr.heartblink.car.ui.component.DistanceBadge
import kr.heartblink.car.ui.component.FigmaSvg
import kr.heartblink.car.ui.component.Inset
import kr.heartblink.car.ui.component.Layered
import kr.heartblink.car.ui.component.SecondaryButton
import kr.heartblink.car.ui.component.Shadow
import kr.heartblink.car.ui.component.Tab
import kr.heartblink.car.ui.component.innerShadow
import kr.heartblink.car.ui.component.outerShadow
import kr.heartblink.car.ui.component.tap
import kr.heartblink.car.ui.theme.DesignWidth
import kr.heartblink.car.ui.theme.Gaegu
import kr.heartblink.car.ui.theme.Heart
import kr.heartblink.car.ui.theme.HeartType
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed

/** Figma screens 15 ~ 19: 기록. */
val recordScreens: Map<String, @Composable (Nav) -> Unit> = linkedMapOf(
    "15" to { nav -> RecordSentScreen(nav) },
    "16" to { nav -> RecordReceivedScreen(nav) },
    "17" to { nav -> RecordReplaySheetScreen(nav) },
    "18" to { nav -> RecordQuestionsScreen(nav) },
    "19" to { nav -> RecordLoadingScreen(nav) },
)

// ---------------------------------------------------------------------------------------------
// Sample data (what the Figma screens show)
// ---------------------------------------------------------------------------------------------

/** The icon of the condition a message opens on, placed inside a 36 px box like Figma does. */
private enum class ConditionIcon(val svg: String, val inset: Inset, val bleed: Inset) {
    Wiper("s15_icon_wiper", Inset.pct(28.13f, 12.5f, 16.88f, 12.5f), Inset.pct(6.82f, 5f)),
    Place("s15_icon_place", Inset.pct(9.38f, 18.75f), Inset.pct(4.62f, 6f)),
    Parked("rec_icon_flag", Inset.pct(12.5f, 25f, 9.38f, 25f), Inset.pct(4.8f, 7.5f)),
    FirstStart("rec_icon_power", Inset.pct(18.75f, 15.63f, 12.5f, 15.63f), Inset.pct(5.45f)),
    Headlight("rec_icon_headlight", Inset.pct(21.88f, 12.5f, 21.88f, 15.63f), Inset.pct(6.67f, 5.22f)),
}

private data class RecordMessage(
    val icon: ConditionIcon,
    val quote: String,
    val detail: String,
    val voice: Boolean,
    val status: String,
    /** true: pink filled pill (heard / replay), false: outlined pill (not opened yet). */
    val heard: Boolean,
    /** Chip text in the replay sheet (received messages only). */
    val opensOn: String = "",
)

private val sentMessages = listOf(
    RecordMessage(ConditionIcon.Wiper, "\"비 오는 날엔 천천히 와\"", "서연님이 와이퍼를 켜면, 음성 0:06", true, "아직 안 열림", false),
    RecordMessage(ConditionIcon.Place, "\"1년 전 오늘, 여기서 처음 만났지\"", "처음 데이트한 카페 앞을 지나면, 글자", false, "아직 안 열림", false),
    RecordMessage(ConditionIcon.Parked, "\"오늘도 고생 많았어. 집 잘 들어가~\"", "주차했을 때, 11월 5일 오후 7:40에 열림", true, "서연님이 들었어요", true),
    RecordMessage(ConditionIcon.FirstStart, "\"오늘 발표 잘할 거야. 끝나면 맛있는 거 먹자!\"", "월요일 첫 시동, 11월 2일 오전 7:30에 열림", true, "서연님이 들었어요", true),
)

private val receivedMessages = listOf(
    RecordMessage(ConditionIcon.Headlight, "\"밤길 조심해서 와\"", "전조등을 켤 때, 11월 5일 오후 6:58에 들음", true, "다시 듣기", true, "전조등을 켤 때 열림"),
    RecordMessage(ConditionIcon.FirstStart, "\"오늘 발표 잘할 거야. 끝나면 맛있는 거 먹자!\"", "월요일 첫 시동, 11월 2일 오전 7:30에 들음", true, "다시 듣기", true, "월요일 첫 시동에 열림"),
    RecordMessage(ConditionIcon.Parked, "\"나도 이제 출발! 이따 봐\"", "회사 주차장 도착할 때, 11월 2일 오전 8:42에 들음", true, "다시 듣기", true, "회사 주차장 도착할 때 열림"),
)

private sealed interface Answer {
    /** A voice answer; [text] is the transcript shown for reference. */
    data class Voice(val text: String) : Answer

    /** The partner already answered, hidden until I answer. */
    data object Locked : Answer

    /** I have not answered yet. */
    data object Waiting : Answer
}

private data class QuestionCard(
    val head: String,
    val status: String,
    val waitingForMe: Boolean,
    val question: String,
    val note: String? = null,
    val me: Answer,
    val partner: Answer,
)

private val questionCards = listOf(
    QuestionCard("129번째 질문  지금 질문", "내 답을 기다려요", true, "요즘 나한테 제일 고마웠던 순간은?", null, Answer.Waiting, Answer.Locked),
    QuestionCard("128번째 질문  11월 6일에 열림", "둘 다 답했어요", false, "이번 주말에 나랑 꼭 하고 싶은 것 하나는?", null, Answer.Voice("캠핑 가고 싶어!"), Answer.Voice("캠핑 가서 같이 별 보고 싶어!")),
    QuestionCard("127번째 질문  11월 5일에 열림", "둘 다 답했어요", false, "오늘 출근길에 제일 먼저 떠오른 생각은?", null, Answer.Voice("어제 같이 본 영화 장면"), Answer.Voice("지훈이 잘 일어났나")),
    QuestionCard(
        "126번째 질문  11월 3일부터 4일까지", "하루 이어진 질문", false, "처음 만난 날, 제일 기억나는 장면은?",
        "3일에 서연님이 차를 타지 않아 4일까지 이어졌어요", Answer.Voice("카페에서 웃던 얼굴"), Answer.Voice("우산 씌워 준 거"),
    ),
)

/** Which received message the replay sheet (17) shows; set when a row on 16 is tapped. */
private var replayIndex by mutableIntStateOf(0)

// ---------------------------------------------------------------------------------------------
// Screens
// ---------------------------------------------------------------------------------------------

/** 15 기록: 내가 남긴 메시지. */
@Composable
fun RecordSentScreen(nav: Nav) {
    RecordWindow(RecordTab.Sent, nav) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            itemsIndexed(sentMessages) { i, m -> MessageRow(m, Modifier.fadeUp(i)) }
        }
    }
}

/** 16 기록: 받은 메시지. Tapping a row opens the replay sheet (17). */
@Composable
fun RecordReceivedScreen(nav: Nav, animate: Boolean = true) {
    RecordWindow(RecordTab.Received, nav) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            itemsIndexed(receivedMessages) { i, m ->
                MessageRow(
                    m,
                    Modifier.fadeUp(i, animate),
                    onClick = {
                        replayIndex = i
                        nav.go("17")
                    },
                )
            }
        }
    }
}

/** 17 기록: 받은 메시지 다시 듣기. The sheet slides up over 16 in 0.4 s; 패스 or the dim closes it. */
@Composable
fun RecordReplaySheetScreen(nav: Nav) {
    val message = receivedMessages[replayIndex.coerceIn(receivedMessages.indices)]
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) { shown.animateTo(1f, tween(400, easing = FastOutSlowInEasing)) }
    val close = { nav.go("16") }

    Box(Modifier.fillMaxSize()) {
        RecordReceivedScreen(nav, animate = false)
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = shown.value }
                .background(Color(0x522E2328))
                .tap(close),
        )
        ReplaySheet(
            message,
            Modifier
                .offset(191.dp, 721.dp)
                .width(1300.dp)
                .graphicsLayer { translationY = (1f - shown.value) * 500.dp.toPx() },
            onPass = close,
        )
    }
}

/** 18 기록: 오늘의 질문 모아보기. */
@Composable
fun RecordQuestionsScreen(nav: Nav) {
    RecordWindow(RecordTab.Questions, nav) {
        LazyVerticalGrid(
            GridCells.Fixed(2),
            Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            gridItemsIndexed(questionCards) { i, q -> QuestionCardView(q, Modifier.fadeUp(i)) }
        }
    }
}

/** 19 기록: 불러오는 중. Row placeholders with a slow light sweep. */
@Composable
fun RecordLoadingScreen(nav: Nav) {
    val sweep = rememberSweep()
    RecordWindow(RecordTab.Sent, nav) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(4) { SkeletonRow(sweep) }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Window, panel and inner tabs
// ---------------------------------------------------------------------------------------------

private enum class RecordTab(val label: String, val count: String, val screen: String) {
    Sent("내가 남긴 메시지", "4", "15"),
    Received("받은 메시지", "3", "16"),
    Questions("오늘의 질문", "129", "18"),
}

private val PanelShape = RoundedCornerShape(40.dp)

/**
 * The common 기록 window: header and toolbar from [HeartWindow], and the beige panel between them
 * (40 px side margin, 160 px from the top, 150 px above the bottom) with the title row on top.
 */
@Composable
private fun RecordWindow(selected: RecordTab, nav: Nav, list: @Composable ColumnScope.() -> Unit) {
    val c = Heart.colors
    HeartWindow(Tab.Record, nav) {
        Column(
            Modifier
                .padding(start = 40.dp, end = 40.dp, top = 160.dp, bottom = 150.dp)
                .fillMaxSize()
                .outerShadow(PanelShape, Shadow(0f, 18f, 44f, Color(0x0D292124)))
                .clip(PanelShape)
                .background(c.pinkSoft)
                .innerShadow(PanelShape, Shadow(0f, -6f, 16f, Color(0x08292124)), Shadow(0f, 6f, 14f, Color(0xB3FFFFFF)))
                .padding(horizontal = 44.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("기록", style = HeartType.HeartTitle, color = c.textPrimary, maxLines = 1)
                    FigmaSvg("rec_doodle_heart", Modifier.size(36.dp))
                }
                DistanceBadge("860")
                RecordTabs(selected) { if (it != selected) nav.go(it.screen) }
            }
            list()
        }
    }
}

private val Pill = RoundedCornerShape(999.dp)

/** The white segmented control on the right of the title row. */
@Composable
private fun RecordTabs(selected: RecordTab, onSelect: (RecordTab) -> Unit) {
    val c = Heart.colors
    Row(
        Modifier
            .clip(Pill)
            .background(c.bgCard)
            .innerShadow(Pill, Shadow(-2f, -2f, 6f, Color(0xE6FFFFFF)), Shadow(3f, 4f, 8f, Color(0x0D292124)))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RecordTab.entries.forEach { tab ->
            val on = tab == selected
            Row(
                Modifier
                    .clip(Pill)
                    .background(c.bgCard)
                    .tap { onSelect(tab) }
                    .padding(horizontal = 30.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    tab.label,
                    style = if (on) HeartType.Headline else HeartType.BodyStrong,
                    color = if (on) c.pinkDeep else c.textSecondary,
                    maxLines = 1,
                )
                Text(tab.count, style = HeartType.Label, color = if (on) c.pinkDeep else c.textTertiary, maxLines = 1)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Message rows (15, 16)
// ---------------------------------------------------------------------------------------------

private val CardShape = RoundedCornerShape(28.dp)

/** White card with the 2 px line border; Figma counts the border inside the padding. */
@Composable
private fun Modifier.recordCard(): Modifier = this
    .clip(CardShape)
    .background(Heart.colors.bgCard)
    .border(2.dp, Heart.colors.line, CardShape)

private val HandM = HeartType.gaegu(40f, spacing = -0.04f)
private val HandS = HeartType.gaegu(32f, bold = false, spacing = -0.04f)
private val HandL = TextStyle(
    fontFamily = Gaegu, fontWeight = FontWeight.Bold, fontSize = 58.sp, lineHeight = 68.sp, letterSpacing = (-0.04f).em,
    textAlign = TextAlign.Center, lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

@Composable
private fun MessageRow(m: RecordMessage, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = Heart.colors
    Row(
        modifier
            .fillMaxWidth()
            .recordCard()
            .tap(onClick)
            .padding(horizontal = 30.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(c.pinkLight), contentAlignment = Alignment.Center) {
            Layered(Modifier.size(36.dp)) { svg(m.icon.svg, m.icon.inset, m.icon.bleed) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(m.quote, style = HandM, color = c.textPrimary)
            Text(m.detail, style = HeartType.Label, color = c.textSecondary, maxLines = 1)
        }
        if (m.voice) FigmaSvg("rec_play", Modifier.size(52.dp))
        StatusPill(m.status, m.heard)
    }
}

@Composable
private fun StatusPill(text: String, filled: Boolean) {
    val c = Heart.colors
    Box(
        Modifier
            .clip(Pill)
            .background(if (filled) c.pinkLight else c.bgCard)
            .then(if (filled) Modifier.padding(horizontal = 20.dp, vertical = 10.dp) else Modifier.border(2.dp, c.pinkMid, Pill).padding(horizontal = 22.dp, vertical = 12.dp)),
    ) {
        Text(text, style = HeartType.BodyStrong, color = if (filled) c.pinkDeep else c.textSecondary, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------------------------
// Replay sheet (17)
// ---------------------------------------------------------------------------------------------

private val SheetShape = RoundedCornerShape(topStart = 48.dp, topEnd = 48.dp)

@Composable
private fun ReplaySheet(m: RecordMessage, modifier: Modifier, onPass: () -> Unit) {
    val c = Heart.colors
    Column(
        modifier
            .outerShadow(SheetShape, Shadow(0f, -12f, 40f, Color(0x14292124)))
            .clip(SheetShape)
            .background(c.bgCard)
            .innerShadow(SheetShape, Shadow(0f, 6f, 12f, Color(0xE6FFFFFF)))
            .tap {} // keeps taps on the sheet from reaching the dim behind it
            .padding(start = 64.dp, end = 64.dp, top = 20.dp, bottom = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(88.dp, 8.dp).clip(RoundedCornerShape(4.dp)).background(c.pinkMid))
        Spacer(Modifier.height(30.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(Pill).background(c.pinkLight).padding(horizontal = 18.dp, vertical = 8.dp)) {
                Text(m.opensOn, style = HeartType.Label, color = c.pinkDeep, maxLines = 1)
            }
            Text("서연님이 남긴 메시지", style = HeartType.BodyStrong, color = c.textSecondary, maxLines = 1)
        }
        Spacer(Modifier.height(20.dp))
        Text(m.quote, style = HandL, color = c.textPrimary)
        Spacer(Modifier.height(28.dp))
        Row(
            Modifier
                .clip(Pill)
                .background(c.pinkLight)
                .padding(start = 14.dp, end = 26.dp, top = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FigmaSvg("s17_pause", Modifier.size(56.dp))
            FigmaSvg("s17_waveform", Modifier.size(264.dp, 44.dp))
            Text("0:02 / 0:04", style = HeartType.Label, color = c.pinkDeep, maxLines = 1)
        }
        Spacer(Modifier.height(36.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SecondaryButton("패스", Modifier.width(220.dp), onClick = onPass)
            SecondaryButton("다시 듣기", Modifier.weight(1f), onClick = {})
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Question diary cards (18)
// ---------------------------------------------------------------------------------------------

@Composable
private fun QuestionCardView(q: QuestionCard, modifier: Modifier = Modifier) {
    val c = Heart.colors
    Column(
        modifier
            .fillMaxWidth()
            .height(337.dp)
            .recordCard()
            .padding(horizontal = 32.dp, vertical = 26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(q.head, style = HeartType.Label, color = c.textSecondary, maxLines = 1)
            Box(Modifier.clip(Pill).background(c.pinkLight).padding(horizontal = 14.dp, vertical = 4.dp)) {
                Text(q.status, style = HeartType.Label, color = if (q.waitingForMe) c.pinkDeep else c.textSecondary, maxLines = 1)
            }
        }
        Text(q.question, style = HandM, color = c.textPrimary)
        Spacer(Modifier.weight(1f))
        if (q.note != null) Text(q.note, style = HeartType.Label, color = c.textTertiary, maxLines = 1)
        Box(Modifier.fillMaxWidth().height(2.dp).background(c.line))
        AnswerRow("지훈", q.me)
        AnswerRow("서연", q.partner)
    }
}

@Composable
private fun AnswerRow(name: String, answer: Answer) {
    val c = Heart.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = HeartType.BodyStrong, color = c.textSecondary, maxLines = 1, modifier = Modifier.width(76.dp))
        when (answer) {
            is Answer.Voice -> {
                Text(answer.text, style = HandS, color = c.textPrimary, maxLines = 1, modifier = Modifier.weight(1f))
                FigmaSvg("s18_play_small", Modifier.size(40.dp))
            }
            Answer.Waiting -> Text("다음 여유로운 순간에 다시 물어볼게요", style = HeartType.Label, color = c.textTertiary, maxLines = 1, modifier = Modifier.weight(1f))
            Answer.Locked -> {
                FigmaSvg("s18_lock", Modifier.size(28.dp))
                Text("내가 답하면 열려요", style = HeartType.Label, color = c.textSecondary, maxLines = 1)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Skeleton (19)
// ---------------------------------------------------------------------------------------------

@Composable
private fun SkeletonRow(sweep: () -> Float) {
    val c = Heart.colors
    Row(
        Modifier
            .fillMaxWidth()
            .recordCard()
            .padding(horizontal = 30.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FigmaSvg("s19_skeleton_icon", Modifier.size(72.dp).shimmer(sweep, CircleShape))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SkeletonBlock(520f, 26f, sweep)
            SkeletonBlock(340f, 18f, sweep)
        }
        SkeletonBlock(150f, 44f, sweep)
    }
}

@Composable
private fun SkeletonBlock(w: Float, h: Float, sweep: () -> Float) {
    val shape = RoundedCornerShape((h / 2).dp)
    Box(Modifier.size(w.dp, h.dp).clip(shape).background(Heart.colors.pinkLight).shimmer(sweep, shape))
}

/** 0..1 position of the light band: one slow pass every 1.8 s, then a short rest. */
@Composable
private fun rememberSweep(): () -> Float {
    val t = rememberInfiniteTransition(label = "skeleton")
    val v = t.animateFloat(
        0f, 1f,
        infiniteRepeatable(
            keyframes {
                durationMillis = 1800
                0f at 0 using LinearEasing
                1f at 1400
                1f at 1800
            },
            RepeatMode.Restart,
        ),
        label = "sweep",
    )
    return { v.value }
}

/** A soft white band that moves across the whole window, so every placeholder catches it in turn. */
@Composable
private fun Modifier.shimmer(sweep: () -> Float, shape: Shape): Modifier {
    val x = remember { mutableFloatStateOf(0f) }
    return this
        .onGloballyPositioned { x.floatValue = it.positionInRoot().x }
        .drawWithContent {
            drawContent()
            val band = 420.dp.toPx()
            val center = -band + sweep() * (DesignWidth.dp.toPx() + 2 * band) - x.floatValue
            val brush = Brush.horizontalGradient(
                listOf(Color(0x00FFFFFF), Color(0x99FFFFFF), Color(0x00FFFFFF)),
                startX = center - band / 2, endX = center + band / 2,
            )
            drawOutline(shape.createOutline(size, layoutDirection, this), brush)
        }
}

// ---------------------------------------------------------------------------------------------
// Motion
// ---------------------------------------------------------------------------------------------

private val StaggerEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/** Stagger Fade Up: each row rises from below 0.08 s after the one above, 0.5 s long. */
@Composable
private fun Modifier.fadeUp(index: Int, enabled: Boolean = true): Modifier {
    if (!enabled) return this
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(80L * index)
        p.animateTo(1f, tween(500, easing = StaggerEasing))
    }
    return graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * 32.dp.toPx()
    }
}
