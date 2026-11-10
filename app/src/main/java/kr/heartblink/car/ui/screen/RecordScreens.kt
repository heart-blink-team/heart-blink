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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kr.heartblink.car.app.Session
import kr.heartblink.car.data.ConditionValue
import kr.heartblink.car.data.Message
import kr.heartblink.car.data.QuestionState
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
import kr.heartblink.car.data.ConditionGroup as DataGroup

/** Figma screens 15 ~ 19: 기록. */
val recordScreens: Map<String, @Composable (Nav) -> Unit> = linkedMapOf(
    "15" to { nav -> RecordSentScreen(nav) },
    "16" to { nav -> RecordReceivedScreen(nav) },
    "17" to { nav -> RecordReplaySheetScreen(nav) },
    "18" to { nav -> RecordQuestionsScreen(nav) },
    "19" to { nav -> RecordLoadingScreen(nav) },
)

// ---------------------------------------------------------------------------------------------
// Row data
// ---------------------------------------------------------------------------------------------

/** The icon of the condition a message opens on, placed inside a 36 px box like Figma does. */
private class ConditionIcon(val svg: String, val inset: Inset, val bleed: Inset) {
    companion object {
        val Wiper = ConditionIcon("s15_icon_wiper", Inset.pct(28.13f, 12.5f, 16.88f, 12.5f), Inset.pct(6.82f, 5f))
        val Place = ConditionIcon("s15_icon_place", Inset.pct(9.38f, 18.75f), Inset.pct(4.62f, 6f))
        val Parked = ConditionIcon("rec_icon_flag", Inset.pct(12.5f, 25f, 9.38f, 25f), Inset.pct(4.8f, 7.5f))
        val FirstStart = ConditionIcon("rec_icon_power", Inset.pct(18.75f, 15.63f, 12.5f, 15.63f), Inset.pct(5.45f))
        val Headlight = ConditionIcon("rec_icon_headlight", Inset.pct(21.88f, 12.5f, 21.88f, 15.63f), Inset.pct(6.67f, 5.22f))

        fun of(m: Message): ConditionIcon = when (m.group) {
            DataGroup.Place -> Place
            DataGroup.FirstStart -> FirstStart
            DataGroup.Arrival, null -> Parked
            DataGroup.Vehicle, DataGroup.Time -> when (m.conditionValue) {
                ConditionValue.WIPER -> Wiper
                ConditionValue.HEADLIGHT -> Headlight
                else -> (ConditionGroup.optionFor(m.conditionValue)
                    ?: ConditionGroup.customTimeOption.takeIf { m.group == DataGroup.Time })
                    ?.let { ConditionIcon(it.icon, it.inset, it.bleed) } ?: Parked
            }
        }
    }
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
    /** The row on the server; null for the Figma samples. */
    val message: Message? = null,
)

private val sentSamples = listOf(
    RecordMessage(ConditionIcon.Wiper, "\"비 오는 날엔 천천히 와\"", "서연님이 와이퍼를 켜면 열려요, 음성 0:06", true, "아직 안 열렸어요", false),
    RecordMessage(ConditionIcon.Place, "\"1년 전 오늘, 여기서 처음 만났지\"", "서연님이 처음 데이트한 카페 근처에 가면 열려요", false, "아직 안 열렸어요", false),
    RecordMessage(ConditionIcon.Parked, "\"오늘도 고생 많았어. 집 잘 들어가~\"", "주차했을 때, 11월 5일 오후 7:40에 열림", true, "서연님이 들었어요", true),
    RecordMessage(ConditionIcon.FirstStart, "\"오늘 발표 잘할 거야. 끝나면 맛있는 거 먹자!\"", "그날 첫 시동을 걸 때, 11월 2일 오전 7:30에 열림", true, "서연님이 들었어요", true),
)

private val receivedSamples = listOf(
    RecordMessage(ConditionIcon.Headlight, "\"밤길 조심해서 와\"", "전조등을 켤 때, 11월 5일 오후 6:58에 들음", true, "다시 듣기", true, "전조등을 켤 때 열림"),
    RecordMessage(ConditionIcon.FirstStart, "\"오늘 발표 잘할 거야. 끝나면 맛있는 거 먹자!\"", "그날 첫 시동을 걸 때, 11월 2일 오전 7:30에 들음", true, "다시 듣기", true, "그날 첫 시동을 걸 때 열림"),
    RecordMessage(ConditionIcon.Parked, "\"나도 이제 출발! 이따 봐\"", "회사 주차장에 도착할 때, 11월 2일 오전 8:42에 들음", true, "다시 듣기", true, "회사 주차장에 도착할 때 열림"),
)

private fun Message.placeLabel(): String = placeName?.takeIf { it.isNotBlank() } ?: "고른 장소"

private fun Message.timeLabel(): String = ConditionGroup.timeLabel(conditionValue) ?: "고른 시간대"

/**
 * "와이퍼를 켤 때", "저녁에", "그날 첫 시동을 걸 때" ...: when a message opens, as the receiver reads it.
 * Before a comma ("저녁, 11월 5일 오후 6:30에 열림") a time band goes without 에.
 */
private fun opensWhen(m: Message, timeParticle: Boolean = true): String = when (m.group) {
    DataGroup.Vehicle -> when (m.conditionValue) {
        ConditionValue.WIPER -> "와이퍼를 켤 때"
        ConditionValue.HEADLIGHT -> "전조등을 켤 때"
        ConditionValue.SEAT_HEATER -> "열선 시트를 켤 때"
        ConditionValue.AIRCON -> "에어컨을 켤 때"
        ConditionValue.WINDOW -> "창문을 열 때"
        else -> "차량 기능을 켤 때"
    }
    DataGroup.Time -> if (timeParticle) "${m.timeLabel()}에" else m.timeLabel()
    DataGroup.FirstStart -> "그날 첫 시동을 걸 때"
    DataGroup.Arrival -> if (m.conditionValue == ConditionValue.PARKED) "주차했을 때" else "목적지에 도착할 때"
    DataGroup.Place -> "${m.placeLabel()} 근처에 갈 때"
    null -> "조건이 맞을 때"
}

/** The condition as the sender reads it on 15, before "열려요": "서연님이 와이퍼를 켜면", "저녁에". */
private fun opensIf(m: Message, partnerName: String): String {
    val who = "${partnerName}님이 "
    return when (m.group) {
        DataGroup.Vehicle -> who + opensWhen(m).replace("켤 때", "켜면").replace("열 때", "열면")
        DataGroup.Time -> opensWhen(m)
        DataGroup.FirstStart -> who + "그날 첫 시동을 걸면"
        DataGroup.Arrival -> who + if (m.conditionValue == ConditionValue.PARKED) "주차하면" else "목적지에 도착하면"
        DataGroup.Place -> who + "${m.placeLabel()} 근처에 가면"
        null -> "조건이 맞으면"
    }
}

private fun Message.quote(): String = if (body.isNotBlank()) "\"$body\"" else "음성 메시지"

private fun Message.toSentRow(partnerName: String): RecordMessage {
    val voice = audioPath != null
    val opened = openedAt
    val detail = if (opened == null) {
        // "12월 24일 (목)부터 저녁에 열려요" while a later start has not come yet
        val from = opensFrom?.takeIf { it > Clock.System.now() }?.let { "${koreanStart(it)} " }.orEmpty()
        "$from${opensIf(this, partnerName)} 열려요" + if (voice) ", 음성 ${durationText(audioMs ?: 0)}" else ""
    } else {
        "${opensWhen(this, timeParticle = false)}, ${koreanMoment(opened)}에 열림"
    }
    val status = when {
        heardAt != null -> "${partnerName}님이 들었어요"
        opened != null -> "열렸어요"
        else -> "아직 안 열렸어요"
    }
    return RecordMessage(ConditionIcon.of(this), quote(), detail, voice, status, opened != null, message = this)
}

private fun Message.toReceivedRow(): RecordMessage {
    val voice = audioPath != null
    val heard = heardAt
    val opened = openedAt
    val detail = when {
        heard != null -> "${opensWhen(this, timeParticle = false)}, ${koreanMoment(heard)}에 ${if (voice) "들음" else "읽음"}"
        opened != null -> "${opensWhen(this, timeParticle = false)}, ${koreanMoment(opened)}에 열림"
        else -> "${opensWhen(this)} 열려요"
    }
    return RecordMessage(
        ConditionIcon.of(this), quote(), detail, voice, if (voice) "다시 듣기" else "다시 보기", true,
        "${opensWhen(this)} 열림", message = this,
    )
}

private sealed interface Answer {
    /** An answer; [text] is what was typed (or a note for a voice-only answer). */
    data class Voice(val text: String, val audioPath: String? = null, val playable: Boolean = true) : Answer

    /** The partner's answer, hidden until both answered. */
    data class Locked(val text: String = "둘 다 답하면 열려요") : Answer

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

private val questionSamples = listOf(
    QuestionCard("129번째 질문  지금 질문", "내 답을 기다려요", true, "요즘 나한테 제일 고마웠던 순간은?", null, Answer.Waiting, Answer.Locked()),
    QuestionCard("128번째 질문  11월 6일에 열림", "둘 다 답했어요", false, "이번 주말에 나랑 꼭 하고 싶은 것 하나는?", null, Answer.Voice("캠핑 가고 싶어!"), Answer.Voice("캠핑 가서 같이 별 보고 싶어!")),
    QuestionCard("127번째 질문  11월 5일에 열림", "둘 다 답했어요", false, "오늘 출근길에 제일 먼저 떠오른 생각은?", null, Answer.Voice("어제 같이 본 영화 장면"), Answer.Voice("지훈이 잘 일어났나")),
    QuestionCard(
        "126번째 질문  11월 3일부터 4일까지", "하루 이어진 질문", false, "처음 만난 날, 제일 기억나는 장면은?",
        "3일에 서연님이 차를 타지 않아 4일까지 이어졌어요", Answer.Voice("카페에서 웃던 얼굴"), Answer.Voice("우산 씌워 준 거"),
    ),
)

private fun kr.heartblink.car.data.Answer.toAnswer(): Answer =
    Answer.Voice(body.ifBlank { "목소리로 답했어요" }, audioPath, playable = audioPath != null)

/** The question we are on now, as the first card of 18. */
private fun QuestionState.toCurrentCard(partnerName: String): QuestionCard = QuestionCard(
    "${question.no}번째 질문  지금 질문",
    if (mine == null) "내 답을 기다려요" else "${partnerName}님 답을 기다려요",
    mine == null, question.body, null,
    mine?.toAnswer() ?: Answer.Waiting,
    Answer.Locked(if (mine == null) "둘 다 답하면 열려요" else "${partnerName}님이 답하면 열려요"),
)

/** A question both answered; the head shows the day the second answer came in. */
private fun QuestionState.toDoneCard(): QuestionCard {
    val day = listOfNotNull(mine?.createdAt, partner?.createdAt).maxOrNull()?.let(::koreanDay)
    return QuestionCard(
        "${question.no}번째 질문" + (day?.let { "  $it" } ?: ""), "둘 다 답했어요", false, question.body, null,
        mine?.toAnswer() ?: Answer.Waiting, partner?.toAnswer() ?: Answer.Locked(),
    )
}

/** What the 기록 screens show from the server; kept between tabs so switching does not flash. */
private object Records {
    var sent by mutableStateOf<List<Message>?>(null)
    var received by mutableStateOf<List<Message>?>(null)
    var answered by mutableStateOf<List<QuestionState>?>(null)
    var km by mutableStateOf<Double?>(null)

    suspend fun load() {
        val r = Session.repository ?: return
        coroutineScope {
            launch { sent = r.sentMessages() }
            launch { received = r.receivedMessages().filter { it.isOpened } }
            launch { answered = r.answeredQuestions() }
            launch { km = r.coupleDistance() }
        }
    }
}

@Composable
private fun sentRows(): List<RecordMessage>? =
    if (!Session.live) sentSamples else Records.sent?.map { it.toSentRow(Session.view.partnerName) }

@Composable
private fun receivedRows(): List<RecordMessage>? =
    if (!Session.live) receivedSamples else Records.received?.map { it.toReceivedRow() }

@Composable
private fun questionCards(): List<QuestionCard>? {
    if (!Session.live) return questionSamples
    val answered = Records.answered ?: return null
    val current = Session.question?.takeIf { !it.bothAnswered }?.toCurrentCard(Session.view.partnerName)
    return listOfNotNull(current) + answered.map { it.toDoneCard() }
}

/** Which received message the replay sheet (17) shows; set when a row on 16 is tapped. */
private var replayIndex by mutableIntStateOf(0)

// ---------------------------------------------------------------------------------------------
// Screens
// ---------------------------------------------------------------------------------------------

/** 15 기록: 내가 남긴 메시지. */
@Composable
fun RecordSentScreen(nav: Nav) {
    val rows = sentRows()
    val playback = rememberVoicePlayback()
    RecordWindow(RecordTab.Sent, nav) {
        RowList(rows, "아직 남긴 메시지가 없어요") { i, m ->
            val path = m.message?.audioPath
            MessageRow(m, Modifier.fadeUp(i), playing = playback.isPlaying(path), onPlay = path?.let { p -> { playback.toggleRemote(p) } })
        }
    }
}

/** 16 기록: 받은 메시지. Tapping a row opens the replay sheet (17). */
@Composable
fun RecordReceivedScreen(nav: Nav, animate: Boolean = true) {
    val rows = receivedRows()
    RecordWindow(RecordTab.Received, nav, load = animate) {
        RowList(rows, "아직 열린 메시지가 없어요") { i, m ->
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

/** The rows of 15 and 16, the skeleton while they load, or a line when there are none. */
@Composable
private fun ColumnScope.RowList(rows: List<RecordMessage>?, empty: String, row: @Composable (Int, RecordMessage) -> Unit) {
    when {
        rows == null -> Skeleton()
        rows.isEmpty() -> EmptyLine(empty)
        else -> LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            itemsIndexed(rows) { i, m -> row(i, m) }
        }
    }
}

@Composable
private fun Skeleton() {
    val sweep = rememberSweep()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) { repeat(4) { SkeletonRow(sweep) } }
}

@Composable
private fun EmptyLine(text: String) {
    Box(Modifier.fillMaxWidth().padding(top = 120.dp), contentAlignment = Alignment.Center) {
        Text(text, style = HeartType.Body, color = Heart.colors.textSecondary)
    }
}

/** 17 기록: 받은 메시지 다시 듣기. The sheet slides up over 16 in 0.4 s; 패스 or the dim closes it. */
@Composable
fun RecordReplaySheetScreen(nav: Nav) {
    val rows = receivedRows()
    val message = rows?.getOrNull(replayIndex)
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
        if (message != null) {
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
}

/** 18 기록: 오늘의 질문 모아보기. */
@Composable
fun RecordQuestionsScreen(nav: Nav) {
    val cards = questionCards()
    val playback = rememberVoicePlayback()
    RecordWindow(RecordTab.Questions, nav) {
        when {
            cards == null -> Skeleton()
            cards.isEmpty() -> EmptyLine("아직 함께 답한 질문이 없어요")
            else -> LazyVerticalGrid(
                GridCells.Fixed(2),
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                gridItemsIndexed(cards) { i, q -> QuestionCardView(q, playback, Modifier.fadeUp(i)) }
            }
        }
    }
}

/** 19 기록: 불러오는 중. Row placeholders with a slow light sweep. */
@Composable
fun RecordLoadingScreen(nav: Nav) {
    RecordWindow(RecordTab.Sent, nav, load = false) { Skeleton() }
}

// ---------------------------------------------------------------------------------------------
// Window, panel and inner tabs
// ---------------------------------------------------------------------------------------------

private enum class RecordTab(val label: String, val sample: String, val screen: String) {
    Sent("내가 남긴 메시지", "4", "15"),
    Received("받은 메시지", "3", "16"),
    Questions("오늘의 질문", "129", "18"),
}

/** The count next to each inner tab: the Figma numbers offline, the loaded lists live. */
@Composable
private fun RecordTab.count(): String {
    if (!Session.live) return sample
    return when (this) {
        RecordTab.Sent -> Records.sent?.size?.toString()
        RecordTab.Received -> Records.received?.size?.toString()
        RecordTab.Questions -> Records.answered?.let { list -> (list.size + if (Session.question?.bothAnswered == false) 1 else 0).toString() }
    }.orEmpty()
}

private val PanelShape = RoundedCornerShape(40.dp)

/**
 * The common 기록 window: header and toolbar from [HeartWindow], and the beige panel between them
 * (40 px side margin, 160 px from the top, 150 px above the bottom) with the title row on top.
 * With [load], the lists are fetched again from the server each time a 기록 screen opens.
 */
@Composable
private fun RecordWindow(selected: RecordTab, nav: Nav, load: Boolean = true, list: @Composable ColumnScope.() -> Unit) {
    val c = Heart.colors
    LaunchedEffect(Unit) { if (load && Session.live) Records.load() }
    val km = if (Session.live) "%.0f".format(Records.km ?: 0.0) else "860"
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
                DistanceBadge(km)
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
                Text(tab.count(), style = HeartType.Label, color = if (on) c.pinkDeep else c.textTertiary, maxLines = 1)
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
private fun MessageRow(
    m: RecordMessage,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    playing: Boolean = false,
    onPlay: (() -> Unit)? = null,
) {
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
        if (m.voice) {
            Box(Modifier.size(52.dp).tap(onPlay)) { FigmaSvg(if (playing) "s17_pause" else "rec_play", Modifier.size(52.dp)) }
        }
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
    val msg = m.message
    val path = msg?.audioPath
    val playback = rememberVoicePlayback()
    val scope = rememberCoroutineScope()
    val markHeard: () -> Unit = {
        if (msg != null && msg.heardAt == null) scope.launch { Session.repository?.markMessage(msg.id, opened = true, heard = true) }
    }
    // the sheet plays the voice as it opens, like the Figma frame (pause button showing)
    LaunchedEffect(path) { if (path != null) playback.toggleRemote(path, markHeard) }
    val playing = if (msg == null) true else playback.isPlaying(path)
    val progress by playback.progress.collectAsState(0f)
    val totalMs = msg?.audioMs ?: 4000
    val time = if (msg == null) "0:02 / 0:04" else "${durationText(if (playing) (progress * totalMs).toInt() else 0)} / ${durationText(totalMs)}"

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
            Text("${Session.view.partnerName}님이 남긴 메시지", style = HeartType.BodyStrong, color = c.textSecondary, maxLines = 1)
        }
        Spacer(Modifier.height(20.dp))
        Text(m.quote, style = HandL, color = c.textPrimary)
        Spacer(Modifier.height(28.dp))
        if (m.voice) {
            Row(
                Modifier
                    .clip(Pill)
                    .background(c.pinkLight)
                    .tap(path?.let { p -> { playback.toggleRemote(p, markHeard) } })
                    .padding(start = 14.dp, end = 26.dp, top = 14.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FigmaSvg(if (playing) "s17_pause" else "s05_play", Modifier.size(56.dp))
                FigmaSvg("s17_waveform", Modifier.size(264.dp, 44.dp))
                Text(time, style = HeartType.Label, color = c.pinkDeep, maxLines = 1)
            }
            Spacer(Modifier.height(36.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SecondaryButton("패스", Modifier.width(220.dp), onClick = onPass)
            if (m.voice) {
                SecondaryButton(
                    "다시 듣기", Modifier.weight(1f),
                    onClick = {
                        if (path != null) {
                            playback.stop()
                            playback.toggleRemote(path, markHeard)
                        }
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Question diary cards (18)
// ---------------------------------------------------------------------------------------------

@Composable
private fun QuestionCardView(q: QuestionCard, playback: VoicePlayback, modifier: Modifier = Modifier) {
    val c = Heart.colors
    val v = Session.view
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
        AnswerRow(v.meName, q.me, playback)
        AnswerRow(v.partnerName, q.partner, playback)
    }
}

@Composable
private fun AnswerRow(name: String, answer: Answer, playback: VoicePlayback) {
    val c = Heart.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = HeartType.BodyStrong, color = c.textSecondary, maxLines = 1, modifier = Modifier.width(76.dp))
        when (answer) {
            is Answer.Voice -> {
                Text(answer.text, style = HandS, color = c.textPrimary, maxLines = 1, modifier = Modifier.weight(1f))
                if (answer.playable) {
                    val path = answer.audioPath
                    Box(Modifier.size(40.dp).tap(path?.let { p -> { playback.toggleRemote(p) } })) {
                        FigmaSvg(if (playback.isPlaying(path)) "s17_pause" else "s18_play_small", Modifier.size(40.dp))
                    }
                }
            }
            Answer.Waiting -> Text("다음 여유로운 순간에 다시 물어볼게요", style = HeartType.Label, color = c.textTertiary, maxLines = 1, modifier = Modifier.weight(1f))
            is Answer.Locked -> {
                FigmaSvg("s18_lock", Modifier.size(28.dp))
                Text(answer.text, style = HeartType.Label, color = c.textSecondary, maxLines = 1)
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
