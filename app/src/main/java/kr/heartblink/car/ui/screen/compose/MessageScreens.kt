package kr.heartblink.car.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kr.heartblink.car.app.Session
import kr.heartblink.car.app.TimeBand
import kr.heartblink.car.app.CoupleWords
import kr.heartblink.car.data.WordFix
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.layout.BoxWithConstraints
import kotlinx.datetime.Instant
import androidx.compose.ui.text.style.TextDecoration
import kotlinx.datetime.LocalTime
import androidx.compose.foundation.shape.CircleShape
import kr.heartblink.car.data.ConditionValue
import kr.heartblink.car.ui.component.FigmaSvg
import kr.heartblink.car.ui.component.Inset
import kr.heartblink.car.ui.component.Layered
import kr.heartblink.car.ui.component.PrimaryButton
import kr.heartblink.car.ui.component.SecondaryButton
import kr.heartblink.car.ui.component.Tab
import kr.heartblink.car.ui.component.TextAction
import kr.heartblink.car.ui.component.UnderlineTabs
import kr.heartblink.car.ui.component.tap
import kr.heartblink.car.ui.theme.Heart
import kr.heartblink.car.ui.theme.HeartType
import kr.heartblink.car.data.ConditionGroup as DataGroup

/**
 * One option inside a condition group (an icon in a 36 px box plus its label).
 * [value] is what messages.condition_value stores for it.
 */
internal class ConditionOption(
    val label: String,
    val value: String,
    val icon: String,
    val inset: Inset = Inset.Zero,
    val bleed: Inset = Inset.Zero,
)

/**
 * The condition groups, one underline tab each (06 차량 기능 and 07 시간대 are in Figma).
 * [tab] is the tab's word, [head] the line over the group's options.
 */
internal enum class ConditionGroup(val screen: String, val tab: String, val head: String, val data: DataGroup) {
    Vehicle("06", "차량 기능", "상대가 켜는 순간", DataGroup.Vehicle),
    Time("07", "시간대", "열리는 시간대", DataGroup.Time),
    FirstStart("06-2", "첫 시동", "상대가 그날 처음 시동을 걸 때", DataGroup.FirstStart),
    Arrival("06-3", "도착", "상대가 도착하는 순간", DataGroup.Arrival),
    Place("06-4", "장소", "열리는 장소", DataGroup.Place);

    val options: List<ConditionOption>
        get() = when (this) {
            Vehicle -> listOf(
                ConditionOption("와이퍼", ConditionValue.WIPER, "s06_opt_wiper", Inset.pct(28.13f, 12.5f, 16.88f, 12.5f), Inset.pct(6.82f, 5f)),
                ConditionOption("전조등", ConditionValue.HEADLIGHT, "s06_opt_headlight", Inset.pct(21.88f, 12.5f, 21.88f, 15.63f), Inset.pct(6.67f, 5.22f)),
                ConditionOption("열선 시트", ConditionValue.SEAT_HEATER, "s06_opt_seat", Inset.pct(12.5f, 21.88f, 12.5f, 28.13f), Inset.pct(5f, 7.5f)),
                ConditionOption("에어컨", ConditionValue.AIRCON, "s06_opt_aircon", Inset.pct(9.38f, 14.69f), Inset.pct(4.62f, 5.31f)),
                ConditionOption("창문 열림", ConditionValue.WINDOW, "s06_opt_window", Inset.pct(21.88f, 15.63f, 18.75f, 15.63f), Inset.pct(6.32f, 5.45f)),
            )
            Time -> listOf(
                ConditionOption("새벽", ConditionValue.DAWN, "s07_opt_dawn"),
                ConditionOption("아침", ConditionValue.MORNING, "s07_opt_morning"),
                ConditionOption("점심", ConditionValue.NOON, "s07_opt_noon"),
                ConditionOption("오후", ConditionValue.AFTERNOON, "s07_opt_afternoon"),
                ConditionOption("저녁", ConditionValue.EVENING, "s07_opt_evening"),
                ConditionOption("밤", ConditionValue.NIGHT, "s07_opt_night"),
                ConditionOption(CustomTime, TimeBand.CUSTOM, "s06_icon_time_off", Inset.pct(12.5f), Inset.pct(5f)),
            )
            FirstStart -> listOf(
                ConditionOption("첫 시동", ConditionValue.FIRST_START, "s06_icon_ignition", Inset.pct(18.75f, 15.63f, 12.5f, 15.63f), Inset.pct(5.45f)),
            )
            Arrival -> listOf(
                ConditionOption("목적지 도착", ConditionValue.DESTINATION, "s06_icon_arrival", Inset.pct(12.5f, 25f, 9.38f, 25f), Inset.pct(4.8f, 7.5f)),
                ConditionOption("주차", ConditionValue.PARKED, "s06_icon_arrival", Inset.pct(12.5f, 25f, 9.38f, 25f), Inset.pct(4.8f, 7.5f)),
            )
            Place -> listOf(ConditionOption("장소", ConditionValue.PLACE, "s06_icon_place", Inset.pct(9.38f, 18.75f), Inset.pct(4.62f, 6f)))
        }

    fun option(label: String?): ConditionOption = options.firstOrNull { it.label == label } ?: options.first()

    companion object {
        /** The tabs from left to right. */
        val tabs: List<ConditionGroup> = listOf(Place, Vehicle, FirstStart, Arrival, Time)

        /** The option a stored condition value stands for, in either group. */
        fun optionFor(value: String): ConditionOption? = entries.firstNotNullOfOrNull { g -> g.options.firstOrNull { it.value == value } }

        /** "저녁", or "오후 6시~7시 30분" for a range picked with 직접 정하기. */
        fun timeLabel(value: String): String? =
            if (value.startsWith(TimeBand.CUSTOM)) TimeBand.of(value)?.spoken else optionFor(value)?.label

        /** The clock icon of 직접 정하기, for a stored custom range. */
        val customTimeOption: ConditionOption get() = Time.options.last()
    }
}

/**
 * The options 06 offers. A message opens in the partner's car, so the car functions it reported it cannot
 * sense are left out (all of them stay when it reported none, or nothing would be left).
 */
internal fun ConditionGroup.shownOptions(): List<ConditionOption> {
    if (this != ConditionGroup.Vehicle || !Session.live) return options
    val missing = Session.partner?.unsupported.orEmpty()
    return options.filter { it.value !in missing }.ifEmpty { options }
}

/** Label of the 07 option that picks a start and an end time. */
internal const val CustomTime = "직접"

/**
 * The message being written, shared by 06, 06-1, 07 and 08 while the app runs.
 * Offline it holds the Figma sentences (one per group); with the server one sentence and one voice.
 */
internal object MessageDraft {
    var group by mutableStateOf(ConditionGroup.Vehicle)
    val option = mutableStateMapOf(
        ConditionGroup.Vehicle to "와이퍼",
        ConditionGroup.Time to "저녁",
        ConditionGroup.FirstStart to "첫 시동",
        ConditionGroup.Arrival to "주차",
        ConditionGroup.Place to "장소",
    )
    private val demoText = mutableStateMapOf(
        ConditionGroup.Vehicle to "비 오는 날엔 천천히 와",
        ConditionGroup.Time to "오늘도 고생했어, 조심히 와",
    )
    private var liveText by mutableStateOf("")

    /** Couple words put back into what the recognizer heard; underlined, and a tap undoes one. */
    var fixes by mutableStateOf<List<WordFix.Change>>(emptyList())
        private set

    val voice = VoiceDraft(::hear)

    /** A sentence given to a debug build with `--es heard`, taken as if it had been recorded. */
    var pendingHeard: String? = null

    /** What the recognizer wrote down, with the couple's words put back. */
    fun hear(heard: String) {
        val f = CoupleWords.fix(heard)
        liveText = f.text
        fixes = f.changes
    }

    fun revert(change: WordFix.Change) {
        val f = WordFix.Fixed(liveText, fixes).revert(change)
        liveText = f.text
        fixes = f.changes
    }

    /** The range of 직접 정하기 (07), in 10-minute steps. */
    var customStart by mutableStateOf(LocalTime(18, 0))
    var customEnd by mutableStateOf(LocalTime(19, 30))
    val customBand: TimeBand get() = TimeBand(customStart, customEnd)

    /** Not before this moment (열리기 시작하는 시각, or the day of 직접 정하기); null is from now. */
    var opensFrom by mutableStateOf<Instant?>(null)

    /** The wheel sheet open over 06 or 07. */
    var sheet by mutableStateOf<WhenSheet?>(null)

    /** What messages.condition_value stores for the option picked in [group]. */
    fun value(group: ConditionGroup): String {
        val opt = group.option(option[group])
        return if (group == ConditionGroup.Time && opt.label == CustomTime) TimeBand.customValue(customStart, customEnd) else opt.value
    }

    /** Set once the message reached the server; the next visit to 06 starts a new one. */
    var sent by mutableStateOf(false)

    fun text(group: ConditionGroup): String = if (Session.live) liveText else demoText[group].orEmpty()

    fun setText(group: ConditionGroup, text: String) {
        if (Session.live) liveText = text else demoText[group] = text
        fixes = emptyList()
    }

    fun reset() {
        liveText = ""
        fixes = emptyList()
        voice.clear()
        opensFrom = null
        sheet = null
        sent = false
    }
}

/** Fill of a picked option: the accent pink at 12%. */
internal val PickedOptionFill = Color(0x1FE9557F)
private val KeyboardBg = Color(0xFFF3EFEA)

/** Window space under the panel: the toolbar and a 78 px margin; 08 keeps more for the toast. */
private val PanelBottom = 190.dp
private val SentPanelBottom = 278.dp

// ---------------------------------------------------------------- screens

/** 06 메시지 남기기 (차량 기능) and 07 메시지 남기기 (시간대). */
@Composable
internal fun MessageScreen(nav: Nav, group: ConditionGroup) {
    SideEffect {
        MessageDraft.group = group
        PlaceDraft.onMap = { nav.go("06-5") }
    }
    LaunchedEffect(Unit) {
        if (MessageDraft.sent) MessageDraft.reset()
        MessageDraft.sheet = null
        CoupleWords.refresh()
        MessageDraft.pendingHeard?.let {
            MessageDraft.pendingHeard = null
            MessageDraft.hear(it)
        }
    }
    val (action, run) = rememberAction()
    val playback = rememberVoicePlayback()
    val draft = MessageDraft.voice
    Box(Modifier.fillMaxSize()) {
        HeartWindow(Tab.Message, nav) {
            MessagePanel(
                group = group,
                transcript = MessageDraft.text(group),
                animate = true,
                onGroup = { if (it != group) nav.go(it.screen) },
                onOption = { MessageDraft.option[group] = it },
                onEdit = { nav.go("06-1") },
                onCancel = {
                    draft.stop()
                    nav.back()
                },
                onSend = {
                    if (!Session.live) {
                        nav.go("08")
                    } else {
                        if (draft.isRecording) draft.stop()
                        playback.stop()
                        val value = MessageDraft.value(group)
                        val parking = group == ConditionGroup.Arrival && value == ConditionValue.PARKED
                        val place = when {
                            group == ConditionGroup.Place -> PlaceDraft.chosen
                            parking -> PlaceDraft.parkAt
                            else -> null
                        }
                        if (MessageDraft.text(group).isBlank() && draft.recording == null) {
                            action.error = "녹음하거나 글을 써 주세요"
                        } else if (group == ConditionGroup.Place && place == null) {
                            action.error = "메시지가 열릴 장소를 골라 주세요"
                        } else if (parking && place == null) {
                            action.error = "주차할 곳을 지도에서 골라 주세요"
                        } else {
                            run({
                                draft.awaitWords()
                                Session.sendMessage(group.data, value, MessageDraft.text(group).trim(), draft.recording, place, MessageDraft.opensFrom)
                            }) {
                                MessageDraft.sent = true
                                nav.go("08")
                            }
                        }
                    }
                },
                voice = { if (Session.live) VoicePill(draft, playback, compact = false) else RecordedVoice() },
                error = action.error ?: draft.error,
                busy = action.busy,
            )
        }
        WhenSheetHost()
    }
}

/** 06-1 받아쓴 글자 고치기 (키보드): typing only while parked; 완료 returns with the edited sentence. */
@Composable
internal fun MessageEditScreen(nav: Nav) {
    val group = MessageDraft.group
    val drafted = MessageDraft.text(group)
    var text by remember { mutableStateOf(drafted) }
    // a new recording made here replaces the sentence being edited, as it does on 06
    LaunchedEffect(drafted) { text = drafted }
    var shift by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        HeartWindow(Tab.Message, nav) {
            MessagePanel(group, text, animate = false, editing = true, voice = { if (Session.live) VoicePill(MessageDraft.voice, rememberVoicePlayback(), compact = false) else RecordedVoice() })
        }
        HangulKeyboard(
            shift = shift,
            onKey = { text = typeJamo(text, it); shift = false },
            onShift = { shift = !shift },
            onDelete = { text = text.dropLast(1) },
            onText = { text += it },
            onDone = {
                MessageDraft.setText(group, text)
                nav.go(group.screen)
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** 08 메시지 남긴 직후: hearts burst from the dimmed 남겼어요 button, then the toast stays above the toolbar. */
@Composable
internal fun MessageSentScreen(nav: Nav) {
    val group = MessageDraft.group
    val text = MessageDraft.text(group)
    val option = MessageDraft.option[group].orEmpty()
    val partner = Session.view.partnerName
    // the start time stays on the screen above, so the toast only says when it opens
    val quote = "남겼어요."
    val toast = when (group) {
        ConditionGroup.Vehicle -> {
            val verb = if (option == "창문 열림") "창문을 열면" else "$option${objectParticle(option)} 켜면"
            "$quote ${partner}님이 $verb 열려요"
        }
        ConditionGroup.Time ->
            if (option == CustomTime) "$quote ${MessageDraft.customBand.spoken} 사이에 열려요" else "$quote ${option}에 열려요"
        ConditionGroup.FirstStart -> "$quote ${partner}님이 그날 처음 시동을 걸면 열려요"
        ConditionGroup.Arrival ->
            if (option == "목적지 도착") "$quote ${partner}님이 목적지에 도착하면 열려요"
            else "$quote ${partner}님이 ${parkName(PlaceDraft.parkAt)}에 주차하면 열려요"
        ConditionGroup.Place -> "$quote ${partner}님이 ${PlaceDraft.place?.name ?: "고른 장소"} 근처에 가면 열려요"
    }
    val playback = rememberVoicePlayback()
    HeartWindow(Tab.Message, nav) {
        MessagePanel(
            group, text, animate = false,
            onEdit = if (Session.live) null else ({ nav.go("06-1") }),
            onCancel = { nav.back() },
            voice = { if (Session.live) VoicePill(MessageDraft.voice, playback, compact = false) else RecordedVoice() },
            sent = true,
            bottom = SentPanelBottom,
        )
        HeartBurst()
        // 24 px above the 112 px toolbar
        Toast(toast, Modifier.align(Alignment.BottomCenter).padding(bottom = 136.dp))
    }
}

// ---------------------------------------------------------------- panel

@Composable
private fun MessagePanel(
    group: ConditionGroup,
    transcript: String,
    animate: Boolean,
    editing: Boolean = false,
    onGroup: ((ConditionGroup) -> Unit)? = null,
    onOption: ((String) -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    onSend: (() -> Unit)? = null,
    voice: @Composable () -> Unit = { RecordedVoice() },
    error: String? = null,
    busy: Boolean = false,
    sent: Boolean = false,
    bottom: Dp = PanelBottom,
) {
    val c = Heart.colors
    // two columns straight on the page, split by one hairline: no panel card behind them
    Row(
        Modifier
            .fillMaxSize()
            .padding(start = 88.dp, end = 88.dp, top = 200.dp, bottom = bottom),
        horizontalArrangement = Arrangement.spacedBy(40.dp),
    ) {
        Conditions(group, animate, onGroup, onOption, Modifier.weight(1f).fillMaxHeight())
        Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(c.pinkMid))
        MessageColumn(transcript, editing, onEdit, onCancel, onSend, voice, error, busy, sent, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun Conditions(
    group: ConditionGroup,
    animate: Boolean,
    onGroup: ((ConditionGroup) -> Unit)?,
    onOption: ((String) -> Unit)?,
    modifier: Modifier,
) {
    val c = Heart.colors
    val tabs = ConditionGroup.tabs
    Column(modifier) {
        Text("메시지가 열리는 조건", style = HeartType.HeartTitle, color = c.textPrimary, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        // the groups as underline tabs standing on a hairline
        Column(Modifier.fillMaxWidth()) {
            UnderlineTabs(
                tabs.map { it.tab }, tabs.indexOf(group),
                onSelect = onGroup?.let { go -> { i: Int -> go(tabs[i]) } },
            )
            Box(Modifier.fillMaxWidth().height(2.dp).background(c.line))
        }
        Spacer(Modifier.height(18.dp))
        ConditionDetail(group, animate, onOption)
        Spacer(Modifier.height(28.dp))
        // the custom range takes the place of the start time line
        val editable = onOption != null
        if (group == ConditionGroup.Time && MessageDraft.option[group] == CustomTime) {
            CustomTimeRange(editable, stagger(2, animate))
        } else {
            StartTime(editable, stagger(2, animate))
        }
    }
}

/**
 * One line that gets smaller, down to [minScale], when it is wider than its place instead of being cut off:
 * a picked time range ("오후 6시~다음 날 오후 5시 30분") can be longer than its row. Only the width is measured;
 * the line keeps its height so nothing under it moves.
 */
@Composable
private fun FitLine(text: String, style: TextStyle, color: Color, minScale: Float = 0.55f) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints {
        val room = constraints.maxWidth
        val scale = remember(text, style, room) {
            var s = 1f
            fun width(k: Float) = measurer.measure(text, style.copy(fontSize = style.fontSize * k), maxLines = 1, softWrap = false).size.width
            if (constraints.hasBoundedWidth) while (s > minScale && width(s) > room) s -= 0.05f
            s.coerceAtLeast(minScale)
        }
        Text(text, style = style.copy(fontSize = style.fontSize * scale), color = color, maxLines = 1, softWrap = false)
    }
}

/** The picked group: its line, then its options (the places for 장소, one line for 첫 시동). No card behind. */
@Composable
private fun ConditionDetail(group: ConditionGroup, animate: Boolean, onOption: ((String) -> Unit)?) {
    val c = Heart.colors
    val shown = group.shownOptions()
    // a function the partner's car turned out not to have cannot stay picked
    LaunchedEffect(shown) {
        if (shown.none { it.label == MessageDraft.option[group] }) MessageDraft.option[group] = shown.first().label
    }
    val current = MessageDraft.option[group]
    Column(Modifier.fillMaxWidth().padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(group.head, style = HeartType.BodyStrong, color = c.textPrimary, maxLines = 1, modifier = stagger(0, animate))
        Box(Modifier.fillMaxWidth().then(stagger(1, animate))) {
            when (group) {
                ConditionGroup.Place -> PlaceDetail()
                // nothing to pick: one line on when the day starts
                ConditionGroup.FirstStart -> Text(
                    "하루는 새벽 4시에 시작해요. 밤 12시가 지나도 새벽 4시 전이면 같은 날이에요",
                    style = HeartType.Body, color = c.textSecondary,
                )
                else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    shown.forEach { opt ->
                        // 07 writes each band's hours under its name; 주차 writes the place picked on the map
                        val parking = opt.value == ConditionValue.PARKED
                        val sub = when {
                            group == ConditionGroup.Time && opt.label == CustomTime -> "고르기"
                            group == ConditionGroup.Time -> TimeBand.of(opt.value)?.let { "${it.start.hour}~${it.end.hour}시" }
                            parking -> PlaceDraft.parkAt?.let(::parkName) ?: "지도에서 고르기"
                            else -> null
                        }
                        // 주차 opens the map at once when no place is picked yet, and again when tapped once more
                        val click = onOption?.let {
                            {
                                val again = opt.label == current
                                it(opt.label)
                                if (parking && (again || PlaceDraft.parkAt == null)) PlaceDraft.onMap()
                            }
                        }
                        OptionTile(opt, opt.label == current, animate, click, Modifier.weight(1f), sub)
                    }
                }
            }
        }
    }
}

/** One option, an icon over its name. Picked: 12% pink fill, a 3 px pink line, deep pink bold name and icon. */
@Composable
private fun OptionTile(opt: ConditionOption, selected: Boolean, animate: Boolean, onClick: (() -> Unit)?, modifier: Modifier, sub: String? = null) {
    val c = Heart.colors
    val f = selectFraction(selected, animate)
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .height(120.dp)
            .clip(shape)
            .background(PickedOptionFill.copy(alpha = PickedOptionFill.alpha * f))
            .border(3.dp, c.pinkAccent.copy(alpha = f), shape)
            .tap(onClick)
            .padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        // a tile with hours under its name keeps the same height as one without
        verticalArrangement = Arrangement.spacedBy(if (sub != null) 2.dp else 10.dp, Alignment.CenterVertically),
    ) {
        Layered(Modifier.size(if (sub != null) 30.dp else 36.dp)) { svg(opt.icon, opt.inset, opt.bleed, Modifier.activeIcon(f)) }
        Text(
            opt.label, style = HeartType.Label.copy(fontWeight = if (f > 0.5f) FontWeight.Bold else FontWeight.Medium),
            color = lerp(c.textSecondary, c.pinkDeep, f), maxLines = 1, softWrap = false,
        )
        if (sub != null) {
            Text(
                sub, style = HeartType.Small, color = lerp(c.textTertiary, c.pinkDeep, f * 0.6f),
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 열리기 시작하는 시각: from now, or from a day (and time) picked on the wheels. */
@Composable
private fun StartTime(editable: Boolean, modifier: Modifier) {
    val c = Heart.colors
    val from = MessageDraft.opensFrom?.let(::koreanStart) ?: if (Session.live) "지금부터" else "11월 7일 (토) 오전 7:00부터"
    WhenLine(editable, { MessageDraft.sheet = WhenSheet.OpensFrom }, modifier) {
        Text("$from 열려요", style = HeartType.BodyStrong, color = c.textPrimary, maxLines = 1)
    }
}

/** 직접 정하기 (07): from which day, and the range, on the start time's line. */
@Composable
private fun CustomTimeRange(editable: Boolean, modifier: Modifier) {
    val c = Heart.colors
    val from = MessageDraft.opensFrom?.let(::koreanStart) ?: "오늘부터"
    WhenLine(editable, { MessageDraft.sheet = WhenSheet.TimeRange }, modifier) {
        Box(Modifier.weight(1f)) { FitLine("$from ${MessageDraft.customBand.spoken} 사이에 열려요", HeartType.BodyStrong, c.textPrimary) }
    }
}

/** The clock and one line, no box around them; a tap opens the wheels. */
@Composable
private fun WhenLine(editable: Boolean, onClick: () -> Unit, modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .tap(if (editable) onClick else null)
            .padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Layered(Modifier.size(36.dp)) { svg("s06_icon_time_off", Inset.pct(12.5f), Inset.pct(5f)) }
        content()
    }
}

@Composable
private fun MessageColumn(
    transcript: String,
    editing: Boolean,
    onEdit: (() -> Unit)?,
    onCancel: (() -> Unit)?,
    onSend: (() -> Unit)?,
    voice: @Composable () -> Unit,
    error: String?,
    busy: Boolean,
    sent: Boolean,
    modifier: Modifier,
) {
    val c = Heart.colors
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("남길 메시지", style = HeartType.HeartTitle, color = c.textPrimary, maxLines = 1, softWrap = false, modifier = Modifier.width(188.dp))
            FigmaSvg("s06_doodle_heart", Modifier.size(36.dp))
        }
        Spacer(Modifier.height(26.dp))
        voice()
        Spacer(Modifier.height(22.dp))
        val fixes = if (editing) emptyList() else MessageDraft.fixes
        // while couple words are put back in, the hint on them stands in for the field's name
        Text(if (fixes.isNotEmpty()) FixHint else "받아쓴 글자", style = HeartType.Label, color = c.textSecondary, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        TranscriptBox(
            transcript, editing, onEdit,
            placeholder = if (MessageDraft.voice.transcribing) Listening else "녹음하면 여기에 적혀요",
            fixes = fixes, onRevert = MessageDraft::revert,
        )
        Spacer(Modifier.height(12.dp))
        TextAction("글자로만 쓰기", Modifier.align(Alignment.CenterHorizontally), style = HeartType.Headline, color = Heart.colors.textSecondary, onClick = onEdit)
        ErrorLine(error)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            SecondaryButton("취소", Modifier.width(200.dp), onCancel)
            PrimaryButton(
                when {
                    sent -> "남겼어요"
                    busy -> "남기는 중"
                    else -> "메시지 남기기"
                },
                Modifier.weight(1f), enabled = !sent, onClick = onSend.takeUnless { busy },
            )
        }
    }
}

@Composable
private fun RecordedVoice() {
    val c = Heart.colors
    val pill = RoundedCornerShape(999.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(pill)
            .background(c.bgCard)
            .border(2.dp, c.line, pill)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FigmaSvg("s06_play", Modifier.size(52.dp))
        FigmaSvg("s06_waveform", Modifier.size(192.dp, 48.dp))
        Text("0:06", style = HeartType.BodyStrong, color = c.textSecondary, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.padding(start = 16.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Layered(Modifier.size(24.dp)) { svg("s06_mic", Inset.pct(10.94f, 21.88f), Inset.pct(5.2f, 7.22f)) }
            Text("다시 녹음", style = HeartType.Label, color = c.textPrimary, maxLines = 1)
        }
    }
}

/** The empty text box while the recognizer is still writing down the recording. */
internal const val Listening = "말하는 대로 적고 있어요"

/** Shown over a sentence with couple words put back in. */
internal const val FixHint = "밑줄 친 말은 우리 말로 고쳤어요. 누르면 들은 대로 돌아가요"

private const val CursorId = "cursor"

@Composable
internal fun TranscriptBox(
    text: String,
    editing: Boolean,
    onEdit: (() -> Unit)?,
    placeholder: String = "여기를 누르면 직접 쓸 수 있어요",
    fixes: List<WordFix.Change> = emptyList(),
    onRevert: ((WordFix.Change) -> Unit)? = null,
) {
    val c = Heart.colors
    val shape = RoundedCornerShape(22.dp)
    val bw = if (editing) 3.dp else 2.dp
    val style = HeartType.gaegu(40f, spacing = -0.04f).copy(lineHeight = 50.sp)
    if (text.isEmpty() && !editing) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(c.bgCard)
                .border(bw, c.pinkMid, shape)
                .tap(onEdit)
                .padding(start = 26.dp + bw, end = 20.dp + bw, top = 18.dp + bw, bottom = 18.dp + bw),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(placeholder, style = style, color = c.textTertiary, modifier = Modifier.weight(1f))
            Layered(Modifier.size(30.dp)) { svg("s06_edit", Inset.pct(17.19f, 18.75f, 18.75f, 18.75f), Inset.pct(5.85f, 6f)) }
        }
        return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.bgCard)
            .border(bw, if (editing) c.pinkAccent else c.pinkMid, shape)
            .tap(onEdit)
            .padding(start = 26.dp + bw, end = 20.dp + bw, top = 18.dp + bw, bottom = 18.dp + bw),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (editing) {
            val blink = rememberInfiniteTransition(label = "cursor")
            val t by blink.animateFloat(0f, 1f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "cursor")
            // the cursor rides right after the last letter, so a long sentence wraps and stays readable
            val cursor = with(LocalDensity.current) { Placeholder(14.dp.toSp(), 44.dp.toSp(), PlaceholderVerticalAlign.TextCenter) }
            Text(
                buildAnnotatedString {
                    append(text)
                    appendInlineContent(CursorId, "|")
                },
                style = style,
                color = c.textPrimary,
                modifier = Modifier.weight(1f),
                inlineContent = mapOf(
                    CursorId to InlineTextContent(cursor) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                            Box(Modifier.size(4.dp, 44.dp).alpha(if (t < 0.5f) 1f else 0f).clip(RoundedCornerShape(2.dp)).background(c.pinkAccent))
                        }
                    },
                ),
            )
        } else {
            // couple words put back by WordFix are underlined; a tap on one puts back what was heard
            val shown = buildAnnotatedString {
                append(text)
                fixes.filter { it.end <= text.length }.forEach { ch ->
                    addStyle(SpanStyle(color = c.pinkDeep, textDecoration = TextDecoration.Underline), ch.start, ch.end)
                    if (onRevert != null) addLink(LinkAnnotation.Clickable("fix:${ch.start}") { onRevert(ch) }, ch.start, ch.end)
                }
            }
            Text(shown, style = style, color = c.textPrimary, modifier = Modifier.weight(1f))
        }
        Layered(Modifier.size(30.dp)) { svg("s06_edit", Inset.pct(17.19f, 18.75f, 18.75f, 18.75f), Inset.pct(5.85f, 6f)) }
    }
}

// ---------------------------------------------------------------- 08 effects

private class Particle(val name: String, val cx: Float, val cy: Float, val size: Float, val rotation: Float)

// Centers of the 8 hearts in window pixels, from the Figma group "Heart Particles".
private val Particles = listOf(
    Particle("s08_heart1", 1075.66f, 870.15f, 26f, 10.5f),
    Particle("s08_heart2", 1126.86f, 917.22f, 20f, 7.5f),
    Particle("s08_heart3", 1217.44f, 942.76f, 30f, 3f),
    Particle("s08_heart4", 1318.63f, 949.61f, 22f, -2f),
    Particle("s08_heart5", 1419.75f, 942.33f, 28f, -6.5f),
    Particle("s08_heart6", 1520.07f, 925.81f, 22f, -10.5f),
    Particle("s08_heart7", 1601.14f, 890.40f, 30f, -12.5f),
    Particle("s08_heart8", 1635.85f, 849.93f, 20f, 12.5f),
)

// Center of the send button on 08 (844 to 932, the panel is shorter there).
private const val BurstX = 1348.25f
private const val BurstY = 888f

/** 16 Particle Burst: 8 hearts spread out of the send button for 0.7 s and fade away. */
@Composable
private fun HeartBurst() {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(700, easing = LinearOutSlowInEasing)) }
    val v = t.value
    if (v < 1f) {
        Particles.forEach { p ->
            val x = BurstX + (p.cx - BurstX) * v
            val y = BurstY + (p.cy - BurstY) * v
            FigmaSvg(
                p.name,
                Modifier
                    .offset((x - p.size / 2).dp, (y - p.size / 2).dp)
                    .size(p.size.dp)
                    .graphicsLayer {
                        rotationZ = p.rotation
                        val s = 0.4f + 0.6f * v
                        scaleX = s
                        scaleY = s
                        alpha = if (v < 0.5f) 1f else (1f - v) * 2f
                    },
            )
        }
    }
}

@Composable
private fun Toast(text: String, modifier: Modifier) {
    val c = Heart.colors
    val pill = RoundedCornerShape(999.dp)
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(300, easing = LinearOutSlowInEasing)) }
    Row(
        modifier
            .widthIn(max = 1500.dp)
            .graphicsLayer {
                alpha = t.value
                translationY = (1f - t.value) * 16.dp.toPx()
            }
            .clip(pill)
            .background(c.textPrimary)
            .padding(start = 28.dp, end = 34.dp, top = 18.dp, bottom = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FigmaSvg("s08_check", Modifier.size(36.dp))
        // a long sentence with a start day takes a second line instead of running off the window
        Text(text, style = HeartType.BodyStrong, color = c.textOnAccent, maxLines = 2)
    }
}

// ---------------------------------------------------------------- 06-1 keyboard

@Composable
internal fun HangulKeyboard(
    shift: Boolean,
    onKey: (Char) -> Unit,
    onShift: () -> Unit,
    onDelete: () -> Unit,
    onText: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier,
) {
    val c = Heart.colors
    val shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
    val jamo = HeartType.noto(34f, 500, 1.45f)
    val label = HeartType.noto(26f, 700, 1.45f)
    @Composable
    fun Key(text: String, width: Dp, big: Boolean, accent: Boolean = false, onClick: (() -> Unit)?) {
        val keyShape = RoundedCornerShape(14.dp)
        Box(
            Modifier
                .size(width, 76.dp)
                .clip(keyShape)
                .background(if (accent) c.pinkAccent else Color.White)
                .tap(onClick),
            contentAlignment = Alignment.Center,
        ) { Text(text, style = if (big) jamo else label, color = if (accent) c.textOnAccent else c.textPrimary, maxLines = 1) }
    }

    @Composable
    fun JamoRow(keys: String, lead: (@Composable () -> Unit)? = null, tail: (@Composable () -> Unit)? = null) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            lead?.invoke()
            keys.forEach { ch ->
                val k = if (shift) Shifted[ch] ?: ch else ch
                Key(k.toString(), 110.dp, big = true) { onKey(k) }
            }
            tail?.invoke()
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(KeyboardBg)
            .padding(start = 60.dp, end = 60.dp, top = 24.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        JamoRow("ㅂㅈㄷㄱㅅㅛㅕㅑㅐㅔ")
        JamoRow("ㅁㄴㅇㄹㅎㅗㅓㅏㅣ")
        JamoRow(
            "ㅋㅌㅊㅍㅠㅜㅡ",
            lead = { Key("⇧", 150.dp, big = false, onClick = onShift) },
            tail = { Key("⌫", 150.dp, big = false, onClick = onDelete) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Key("123", 150.dp, big = false, onClick = null)
            Key("스페이스", 620.dp, big = false) { onText(" ") }
            Key(".", 110.dp, big = true) { onText(".") }
            Key("완료", 220.dp, big = false, accent = true, onClick = onDone)
        }
    }
}

private val Shifted = mapOf('ㅂ' to 'ㅃ', 'ㅈ' to 'ㅉ', 'ㄷ' to 'ㄸ', 'ㄱ' to 'ㄲ', 'ㅅ' to 'ㅆ', 'ㅐ' to 'ㅒ', 'ㅔ' to 'ㅖ')

// Hangul composition for the 2-set keyboard: jamo keys join the last syllable when they can.
private const val CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
private const val JUNG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
private const val JONG = " ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"
private val VowelPairs = mapOf(
    "ㅗㅏ" to 'ㅘ', "ㅗㅐ" to 'ㅙ', "ㅗㅣ" to 'ㅚ', "ㅜㅓ" to 'ㅝ', "ㅜㅔ" to 'ㅞ', "ㅜㅣ" to 'ㅟ', "ㅡㅣ" to 'ㅢ',
)
private val FinalPairs = mapOf(
    "ㄱㅅ" to 'ㄳ', "ㄴㅈ" to 'ㄵ', "ㄴㅎ" to 'ㄶ', "ㄹㄱ" to 'ㄺ', "ㄹㅁ" to 'ㄻ', "ㄹㅂ" to 'ㄼ',
    "ㄹㅅ" to 'ㄽ', "ㄹㅌ" to 'ㄾ', "ㄹㅍ" to 'ㄿ', "ㄹㅎ" to 'ㅀ', "ㅂㅅ" to 'ㅄ',
)
private val FinalSplit = FinalPairs.entries.associate { (pair, joined) -> joined to (pair[0] to pair[1]) }

private fun syllable(cho: Int, jung: Int, jong: Int) = (0xAC00 + (cho * 21 + jung) * 28 + jong).toChar()

internal fun typeJamo(text: String, key: Char): String {
    val last = text.lastOrNull() ?: return key.toString()
    val head = text.dropLast(1)
    val vowel = JUNG.indexOf(key)
    if (last in '가'..'힣') {
        val s = last - '가'
        val cho = s / 588
        val jung = s % 588 / 28
        val jong = s % 28
        if (vowel < 0) {
            val next = if (jong == 0) JONG.indexOf(key).takeIf { it > 0 }
            else FinalPairs["${JONG[jong]}$key"]?.let { JONG.indexOf(it) }
            return if (next != null) head + syllable(cho, jung, next) else text + key
        }
        if (jong == 0) {
            val pair = VowelPairs["${JUNG[jung]}$key"] ?: return text + key
            return head + syllable(cho, JUNG.indexOf(pair), 0)
        }
        // A vowel after a final consonant takes that consonant as its initial.
        val final = JONG[jong]
        val split = FinalSplit[final]
        val keep = split?.let { JONG.indexOf(it.first) } ?: 0
        val moved = CHO.indexOf(split?.second ?: final)
        return if (moved >= 0) head + syllable(cho, jung, keep) + syllable(moved, vowel, 0) else text + key
    }
    val lead = CHO.indexOf(last)
    if (vowel >= 0 && lead >= 0) return head + syllable(lead, vowel, 0)
    return text + key
}

/** 을 or 를 for the object marker after [word]; quotes, ! ? ~ and emoji at the end do not count. */
private fun objectParticle(word: String): String {
    val last = word.lastOrNull { it.isLetterOrDigit() } ?: return "를"
    val final = when (last) {
        in '가'..'힣' -> (last - '가') % 28 != 0
        in '0'..'9' -> last in "013678" // 영, 일, 삼, 육, 칠, 팔
        else -> false
    }
    return if (final) "을" else "를"
}

// ---------------------------------------------------------------- helpers

/** 01 Stagger Fade Up: item [index] rises in 0.06 s after the one before it. */
@Composable
private fun stagger(index: Int, enabled: Boolean): Modifier {
    if (!enabled) return Modifier
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 60L)
        a.animateTo(1f, tween(320, easing = LinearOutSlowInEasing))
    }
    return Modifier.graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * 24.dp.toPx()
    }
}

/** 0..1 pink fill of a picked tile, eased in over 0.2 s. */
@Composable
private fun selectFraction(selected: Boolean, animate: Boolean): Float {
    val target = if (selected) 1f else 0f
    val a = remember { Animatable(if (animate) 0f else target) }
    LaunchedEffect(target) { a.animateTo(target, tween(200, easing = LinearOutSlowInEasing)) }
    return a.value
}

/** Paints an icon in one [color], keeping its shape and alpha (the white mic on the pink record button). */
internal fun Modifier.tint(color: Color) = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(color, blendMode = BlendMode.SrcIn)
    }

/**
 * Turns a duotone icon active by [f] (0..1): the #3A2F2A line goes deep pink #C93E68 and the
 * #E9557F fill goes from 18% to 32%, so the picked option reads at a glance.
 */
private fun Modifier.activeIcon(f: Float): Modifier = if (f <= 0f) this else drawWithContent {
    val m = FloatArray(20) { i -> IdentityMatrix[i] + (ActiveMatrix[i] - IdentityMatrix[i]) * f }
    val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(ColorMatrix(m)) }
    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(Offset.Zero, size), paint)
        drawContent()
        canvas.restore()
    }
}

private val IdentityMatrix = floatArrayOf(
    1f, 0f, 0f, 0f, 0f,
    0f, 1f, 0f, 0f, 0f,
    0f, 0f, 1f, 0f, 0f,
    0f, 0f, 0f, 1f, 0f,
)

// Each channel is a line through two colors: the line (58, 47, 42) goes to (201, 62, 104) and the
// fill (233, 85, 127) keeps its color. Alpha grows with red, so the fill's 46 becomes 82 (32%)
// while the line stays opaque; transparent pixels have no red and stay transparent.
private val ActiveMatrix = floatArrayOf(
    0.1829f, 0f, 0f, 0f, 190.39f,
    0f, 0.6053f, 0f, 0f, 33.55f,
    0f, 0f, 0.2706f, 0f, 92.64f,
    0.1532f, 0f, 0f, 1f, 0f,
)
