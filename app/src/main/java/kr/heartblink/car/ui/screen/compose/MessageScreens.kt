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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kr.heartblink.car.ui.component.FigmaSvg
import kr.heartblink.car.ui.component.Inset
import kr.heartblink.car.ui.component.Layered
import kr.heartblink.car.ui.component.PrimaryButton
import kr.heartblink.car.ui.component.SecondaryButton
import kr.heartblink.car.ui.component.Shadow
import kr.heartblink.car.ui.component.Tab
import kr.heartblink.car.ui.component.innerShadow
import kr.heartblink.car.ui.component.outerShadow
import kr.heartblink.car.ui.component.tap
import kr.heartblink.car.ui.theme.Heart
import kr.heartblink.car.ui.theme.HeartType

/** One option inside a condition group (an icon in a 36 px box plus its label). */
internal class ConditionOption(val label: String, val icon: String, val inset: Inset = Inset.Zero, val bleed: Inset = Inset.Zero)

/** The two condition groups that have a detail picker in Figma (06 차량 기능, 07 시간대). */
internal enum class ConditionGroup(val screen: String, val head: String) {
    Vehicle("06", "상대가 이 기능을 켜는 순간"),
    Time("07", "이 시간대에 열려요");

    val options: List<ConditionOption>
        get() = when (this) {
            Vehicle -> listOf(
                ConditionOption("와이퍼", "s06_opt_wiper", Inset.pct(28.13f, 12.5f, 16.88f, 12.5f), Inset.pct(6.82f, 5f)),
                ConditionOption("전조등", "s06_opt_headlight", Inset.pct(21.88f, 12.5f, 21.88f, 15.63f), Inset.pct(6.67f, 5.22f)),
                ConditionOption("열선 시트", "s06_opt_seat", Inset.pct(12.5f, 21.88f, 12.5f, 28.13f), Inset.pct(5f, 7.5f)),
                ConditionOption("에어컨", "s06_opt_aircon", Inset.pct(9.38f, 14.69f), Inset.pct(4.62f, 5.31f)),
                ConditionOption("창문 열림", "s06_opt_window", Inset.pct(21.88f, 15.63f, 18.75f, 15.63f), Inset.pct(6.32f, 5.45f)),
            )
            Time -> listOf(
                ConditionOption("새벽", "s07_opt_dawn"),
                ConditionOption("아침", "s07_opt_morning"),
                ConditionOption("점심", "s07_opt_noon"),
                ConditionOption("오후", "s07_opt_afternoon"),
                ConditionOption("저녁", "s07_opt_evening"),
                ConditionOption("밤", "s07_opt_night"),
            )
        }
}

/** The message being written, shared by 06, 06-1, 07 and 08 while the app runs. */
internal object MessageDraft {
    var group by mutableStateOf(ConditionGroup.Vehicle)
    val option = mutableStateMapOf(ConditionGroup.Vehicle to "와이퍼", ConditionGroup.Time to "저녁")
    val transcript = mutableStateMapOf(
        ConditionGroup.Vehicle to "비 오는 날엔 천천히 와",
        ConditionGroup.Time to "오늘도 고생했어, 조심히 와",
    )
}

private val CardInner = arrayOf(Shadow(6f, 6f, 12f, Color(0xD9FFFFFF)), Shadow(-6f, -8f, 16f, Color(0x0D292124)))
private val AccentInner = arrayOf(Shadow(5f, 5f, 10f, Color(0x59FFFFFF)), Shadow(-5f, -7f, 14f, Color(0x21292124)))
private val TileDrop = Shadow(0f, 10f, 22f, Color(0x14292124))
private val TileDropOn = Shadow(0f, 10f, 22f, Color(0x24292124))

/** Stroke color of the unselected option icons in Figma. */
private val OptionGray = Color(0xFF7D6B72)
private val KeyboardBg = Color(0xFFF3EFEA)

// ---------------------------------------------------------------- screens

/** 06 메시지 남기기 (차량 기능) and 07 메시지 남기기 (시간대). */
@Composable
internal fun MessageScreen(nav: Nav, group: ConditionGroup) {
    SideEffect { MessageDraft.group = group }
    HeartWindow(Tab.Message, nav) {
        MessagePanel(
            group = group,
            transcript = MessageDraft.transcript[group].orEmpty(),
            animate = true,
            onGroup = { if (it != group) nav.go(it.screen) },
            onOption = { MessageDraft.option[group] = it },
            onEdit = { nav.go("06-1") },
            onCancel = { nav.back() },
            onSend = { nav.go("08") },
        )
    }
}

/** 06-1 받아쓴 글자 고치기 (키보드): typing only while parked; 완료 returns with the edited sentence. */
@Composable
internal fun MessageEditScreen(nav: Nav) {
    val group = MessageDraft.group
    var text by remember { mutableStateOf(MessageDraft.transcript[group].orEmpty()) }
    var shift by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        HeartWindow(Tab.Message, nav) {
            MessagePanel(group, text, animate = false, editing = true)
        }
        HangulKeyboard(
            shift = shift,
            onKey = { text = typeJamo(text, it); shift = false },
            onShift = { shift = !shift },
            onDelete = { text = text.dropLast(1) },
            onText = { text += it },
            onDone = {
                MessageDraft.transcript[group] = text
                nav.go(group.screen)
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** 08 메시지 남긴 직후: hearts burst from the send button, then the toast stays. */
@Composable
internal fun MessageSentScreen(nav: Nav) {
    val group = MessageDraft.group
    val text = MessageDraft.transcript[group].orEmpty()
    val option = MessageDraft.option[group].orEmpty()
    val quote = "\"$text\"${objectParticle(text)} 남겼어요."
    val toast = when (group) {
        ConditionGroup.Vehicle -> "$quote 서연님이 $option${objectParticle(option)} 켜면 열려요"
        ConditionGroup.Time -> "$quote ${option}에 열려요"
    }
    HeartWindow(Tab.Message, nav) {
        MessagePanel(group, text, animate = false, onEdit = { nav.go("06-1") }, onCancel = { nav.back() })
        HeartBurst()
        Toast(toast, Modifier.align(Alignment.TopCenter).offset(y = 999.dp))
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
) {
    val c = Heart.colors
    val shape = RoundedCornerShape(40.dp)
    Row(
        Modifier
            .fillMaxSize()
            .padding(start = 40.dp, end = 40.dp, top = 160.dp, bottom = 150.dp)
            .outerShadow(shape, Shadow(0f, 18f, 44f, Color(0x0D292124)))
            .clip(shape)
            .background(c.pinkSoft)
            .innerShadow(shape, Shadow(0f, -6f, 16f, Color(0x08292124)), Shadow(0f, 6f, 14f, Color(0xB3FFFFFF)))
            .padding(horizontal = 48.dp, vertical = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(40.dp),
    ) {
        Conditions(group, animate, onGroup, onOption, Modifier.weight(1f).fillMaxHeight())
        Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(c.pinkMid))
        MessageColumn(transcript, editing, onEdit, onCancel, onSend, Modifier.weight(1f).fillMaxHeight())
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
    val vehicleOn = group == ConditionGroup.Vehicle
    val timeOn = group == ConditionGroup.Time
    Column(modifier) {
        Text("메시지가 열리는 조건", style = HeartType.HeartTitle, color = c.textPrimary, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Text("하나를 골라요. 그 순간이 오면 메시지가 열려요", style = HeartType.Label, color = c.textSecondary, maxLines = 1)
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PlaceTile(Modifier.width(212.dp).height(352.dp).then(stagger(0, animate)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CategoryTile(
                        "차량 기능",
                        if (vehicleOn) MessageDraft.option[ConditionGroup.Vehicle].orEmpty() else "와이퍼, 전조등 등",
                        TileIcon("s07_icon_car_off", "s06_icon_car_on", Inset.pct(31.25f, 12.5f, 12.5f, 12.5f), Inset.pct(6.67f, 5f)),
                        vehicleOn, animate, onGroup?.let { { it(ConditionGroup.Vehicle) } },
                        Modifier.weight(1f).then(stagger(1, animate)),
                    )
                    CategoryTile(
                        "첫 시동", "그날 처음 켤 때",
                        TileIcon("s06_icon_ignition", null, Inset.pct(18.75f, 15.63f, 12.5f, 15.63f), Inset.pct(5.45f)),
                        false, animate, null, Modifier.weight(1f).then(stagger(2, animate)),
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CategoryTile(
                        "도착", "목적지, 주차",
                        TileIcon("s06_icon_arrival", null, Inset.pct(12.5f, 25f, 9.38f, 25f), Inset.pct(4.8f, 7.5f)),
                        false, animate, null, Modifier.weight(1f).then(stagger(3, animate)),
                    )
                    CategoryTile(
                        "시간대",
                        if (timeOn) MessageDraft.option[ConditionGroup.Time].orEmpty() else "새벽부터 밤까지",
                        TileIcon("s06_icon_time_off", "s07_icon_time_on", Inset.pct(12.5f), Inset.pct(5f)),
                        timeOn, animate, onGroup?.let { { it(ConditionGroup.Time) } },
                        Modifier.weight(1f).then(stagger(4, animate)),
                    )
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        DetailCard(group, animate, onOption, Modifier.then(stagger(5, animate)))
        Spacer(Modifier.height(28.dp))
        StartTime()
    }
}

/** Icon of a category tile; [on] is the pink variant shown when the tile is picked. */
private class TileIcon(val off: String, val on: String?, val inset: Inset, val bleed: Inset)

@Composable
private fun PlaceTile(modifier: Modifier) {
    val c = Heart.colors
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier
            .outerShadow(shape, TileDrop)
            .clip(shape)
            .background(c.bgCard)
            .innerShadow(shape, *CardInner)
            .border(2.dp, c.line, shape)
            .padding(start = 24.dp, end = 18.dp, top = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Layered(Modifier.size(40.dp)) { svg("s06_icon_place", Inset.pct(9.38f, 18.75f), Inset.pct(4.62f, 6f)) }
        Text("장소", style = HeartType.Headline, color = c.textPrimary, maxLines = 1)
        Text("지도에서 선택", style = HeartType.Label, color = c.textSecondary, maxLines = 1)
        Spacer(Modifier.height(10.dp))
        // Map preview: the place where the map SDK goes.
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(c.pinkLight)
                .dashedBorder(2.dp, c.pinkMid, 18.dp),
            contentAlignment = Alignment.Center,
        ) { FigmaSvg("s06_pin", Modifier.size(30.dp, 38.dp)) }
    }
}

@Composable
private fun CategoryTile(
    title: String,
    sub: String,
    icon: TileIcon,
    selected: Boolean,
    animate: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier,
) {
    val c = Heart.colors
    val f = selectFraction(selected, animate)
    val shape = RoundedCornerShape(26.dp)
    val bw = if (selected) 3.dp else 2.dp
    Column(
        modifier
            .height(170.dp)
            .outerShadow(shape, if (selected) TileDropOn else TileDrop)
            .clip(shape)
            .background(c.bgCard)
            .innerShadow(shape, *(if (selected) AccentInner else CardInner))
            .border(bw, lerp(c.line, c.pinkAccent, f), shape)
            .tap(onClick)
            .padding(start = 22.dp + bw, end = 16.dp + bw, top = 20.dp + bw, bottom = 18.dp + bw),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Layered(Modifier.size(40.dp)) {
            svg(icon.off, icon.inset, icon.bleed, Modifier.alpha(if (icon.on != null) 1f - f else 1f))
            if (icon.on != null) svg(icon.on, icon.inset, icon.bleed, Modifier.alpha(f))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = HeartType.Headline, color = c.textPrimary, maxLines = 1)
            Text(sub, style = HeartType.Label, color = lerp(c.textSecondary, c.pinkDeep, f), maxLines = 1)
        }
    }
}

@Composable
private fun DetailCard(group: ConditionGroup, animate: Boolean, onOption: ((String) -> Unit)?, modifier: Modifier) {
    val c = Heart.colors
    val shape = RoundedCornerShape(28.dp)
    val current = MessageDraft.option[group]
    Column(
        modifier
            .fillMaxWidth()
            .outerShadow(shape, TileDrop)
            .clip(shape)
            .background(c.bgCard)
            .innerShadow(shape, *CardInner)
            .padding(horizontal = 24.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(group.head, style = HeartType.BodyStrong, color = c.textPrimary, maxLines = 1)
            Text("하나만 선택", style = HeartType.Label, color = c.textTertiary, maxLines = 1)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            group.options.forEach { opt ->
                OptionTile(opt, opt.label == current, animate, onOption?.let { { it(opt.label) } }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun OptionTile(opt: ConditionOption, selected: Boolean, animate: Boolean, onClick: (() -> Unit)?, modifier: Modifier) {
    val c = Heart.colors
    val f = selectFraction(selected, animate)
    val shape = RoundedCornerShape(20.dp)
    val bw = if (selected) 3.dp else 2.dp
    Column(
        modifier
            .clip(shape)
            .background(c.bgCard)
            .innerShadow(shape, *CardInner)
            .border(bw, lerp(c.line, c.pinkAccent, f), shape)
            .tap(onClick)
            .padding(top = 18.dp + bw, bottom = 16.dp + bw),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        val tint = lerp(OptionGray, c.pinkAccent, f)
        Layered(Modifier.size(36.dp)) { svg(opt.icon, opt.inset, opt.bleed, Modifier.tint(tint)) }
        Text(opt.label, style = HeartType.Label, color = lerp(c.textSecondary, c.pinkDeep, f), maxLines = 1, softWrap = false)
    }
}

@Composable
private fun StartTime() {
    val c = Heart.colors
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .outerShadow(shape, TileDrop)
            .clip(shape)
            .background(c.bgCard)
            .innerShadow(shape, *CardInner)
            .padding(horizontal = 26.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("열리기 시작하는 시각", style = HeartType.Body, color = c.textSecondary, maxLines = 1)
        Text("11월 7일 (토) 오전 7:00부터", style = HeartType.BodyStrong, color = c.textPrimary, maxLines = 1)
    }
}

@Composable
private fun MessageColumn(
    transcript: String,
    editing: Boolean,
    onEdit: (() -> Unit)?,
    onCancel: (() -> Unit)?,
    onSend: (() -> Unit)?,
    modifier: Modifier,
) {
    val c = Heart.colors
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("남길 메시지", style = HeartType.HeartTitle, color = c.textPrimary, maxLines = 1, softWrap = false, modifier = Modifier.width(188.dp))
            FigmaSvg("s06_doodle_heart", Modifier.size(36.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text("받는 사람  서연", style = HeartType.Body, color = c.textSecondary, maxLines = 1)
        Spacer(Modifier.height(26.dp))
        RecordedVoice()
        Spacer(Modifier.height(22.dp))
        Text("받아쓴 글자 (보내기 전에 확인해요)", style = HeartType.Label, color = c.textSecondary, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        TranscriptBox(transcript, editing, onEdit)
        Spacer(Modifier.height(10.dp))
        Text(
            if (editing) "고치는 중이에요. 다 고치면 키보드의 완료를 눌러요" else "글자를 누르면 고칠 수 있어요. 그대로 두면 이 문장으로 보내져요",
            style = HeartType.Label, color = c.textTertiary, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.weight(1f))
        SecondaryButton("녹음 없이 글자로만 쓰기", Modifier.fillMaxWidth(), onEdit)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            SecondaryButton("취소", Modifier.width(200.dp), onCancel)
            PrimaryButton("메시지 남기기", Modifier.weight(1f), onSend)
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
            Modifier.clip(pill).background(c.pinkLight).padding(start = 16.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Layered(Modifier.size(24.dp)) { svg("s06_mic", Inset.pct(10.94f, 21.88f), Inset.pct(5.2f, 7.22f)) }
            Text("다시 녹음", style = HeartType.Label, color = c.textPrimary, maxLines = 1)
        }
    }
}

@Composable
private fun TranscriptBox(text: String, editing: Boolean, onEdit: (() -> Unit)?) {
    val c = Heart.colors
    val shape = RoundedCornerShape(22.dp)
    val bw = if (editing) 3.dp else 2.dp
    val style = HeartType.gaegu(40f, spacing = -0.04f).copy(lineHeight = 50.sp)
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
            Text(text, style = style, color = c.textPrimary, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f, fill = false))
            val blink = rememberInfiniteTransition(label = "cursor")
            val t by blink.animateFloat(0f, 1f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "cursor")
            Box(Modifier.size(4.dp, 44.dp).alpha(if (t < 0.5f) 1f else 0f).clip(RoundedCornerShape(2.dp)).background(c.pinkAccent))
            Spacer(Modifier.weight(1f))
        } else {
            Text(text, style = style, color = c.textPrimary, modifier = Modifier.weight(1f))
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

// Center of the 메시지 남기기 button.
private const val BurstX = 1348.25f
private const val BurstY = 976f

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
            .graphicsLayer {
                alpha = t.value
                translationY = (1f - t.value) * 16.dp.toPx()
            }
            .outerShadow(pill, Shadow(0f, 12f, 32f, Color(0x402E2328)))
            .clip(pill)
            .background(c.textPrimary)
            .padding(start = 28.dp, end = 34.dp, top = 18.dp, bottom = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FigmaSvg("s08_check", Modifier.size(36.dp))
        Text(text, style = HeartType.BodyStrong, color = c.textOnAccent, maxLines = 1)
    }
}

// ---------------------------------------------------------------- 06-1 keyboard

@Composable
private fun HangulKeyboard(
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
                .outerShadow(keyShape, Shadow(0f, 3f, 0f, Color(0x1F29211F)))
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
            .outerShadow(shape, Shadow(0f, -10f, 30f, Color(0x2429211F)))
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

private fun typeJamo(text: String, key: Char): String {
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

/** 을 or 를 for the object marker after [word]. */
private fun objectParticle(word: String): String {
    val last = word.trimEnd().lastOrNull() ?: return "를"
    return if (last in '가'..'힣' && (last - '가') % 28 != 0) "을" else "를"
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

/** Recolors a one-color stroke icon (selected options turn pink). */
private fun Modifier.tint(color: Color) = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(color, blendMode = BlendMode.SrcIn)
    }

/** CSS `border-dashed` on a rounded box. */
private fun Modifier.dashedBorder(width: Dp, color: Color, radius: Dp) = drawBehind {
    val w = width.toPx()
    drawRoundRect(
        color,
        topLeft = Offset(w / 2, w / 2),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius.toPx() - w / 2),
        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
    )
}
