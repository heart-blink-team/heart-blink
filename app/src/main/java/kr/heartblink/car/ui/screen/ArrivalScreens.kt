package kr.heartblink.car.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kr.heartblink.car.R
import kr.heartblink.car.data.ConditionGroup as DataGroup
import kr.heartblink.car.ui.component.PrimaryButton
import kr.heartblink.car.data.Message
import kr.heartblink.car.data.ConditionValue
import kr.heartblink.car.app.TimeBand
import kr.heartblink.car.app.Session
import kr.heartblink.car.app.MessageOpener
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import kr.heartblink.car.ui.component.FigmaSvg
import kr.heartblink.car.ui.component.HeartCar
import kr.heartblink.car.ui.component.Inset
import kr.heartblink.car.ui.component.Layered
import kr.heartblink.car.ui.component.Tab
import kr.heartblink.car.ui.component.tap
import kr.heartblink.car.ui.theme.Heart
import kr.heartblink.car.ui.theme.HeartType

/** Figma screens 09 ~ 14: 메시지 도착 장면. */
val arrivalScreens: Map<String, @Composable (Nav) -> Unit> = linkedMapOf<String, @Composable (Nav) -> Unit>(
    "09" to { nav -> WiperEnvelopeScreen(nav) },
    "10" to { nav -> WiperSceneScreen(nav) },
    "11" to { nav -> OfficeEnvelopeScreen(nav) },
    "12" to { nav -> OfficeMapScreen(nav) },
    "13" to { nav -> FirstStartSceneScreen(nav) },
    "14" to { nav -> NightSceneScreen(nav) },
    // a real message that opened in this car (MainActivity opens these when the car is parked)
    "arrive" to { nav -> LiveEnvelopeScreen(nav) },
    "arrive-scene" to { nav -> LiveSceneScreen(nav) },
)

// ---------------------------------------------------------------------------------------------
// Screens

/** 09 메시지 도착 1: 봉투 (와이퍼). Rain drifts over the window; the letter rises, then the scene (10) follows. */
@Composable
fun WiperEnvelopeScreen(nav: Nav, autoAdvance: Boolean = true) {
    EnvelopeScreen(
        nav = nav,
        next = "10",
        autoAdvance = autoAdvance,
        chip = "와이퍼를 켜는 순간 열렸어요",
        chipIcon = Glyph("s09_icon_wiper", Inset.pct(28.13f, 12.5f, 16.87f, 12.5f), Inset.pct(6.82f, 5f)),
        letter = listOf("비 오는 날엔", "천천히 와"),
        letterStyle = hand(76f, 86f),
        backdrop = { RainOverlay() },
    )
}

/** 10 메시지 도착 2: 비 오는 앞유리 (와이퍼). */
@Composable
fun WiperSceneScreen(nav: Nav) {
    SceneScreen(
        nav = nav,
        title = "비 오는 길에서 열린 메시지",
        condition = "와이퍼를 켤 때",
        conditionIcon = Glyph("s10_icon_wiper", Inset.pct(28.13f, 12.5f, 16.87f, 12.5f), Inset.pct(7.39f, 5.42f)),
        sentAt = "오늘 오후 6:12, 주행 중에 들음",
        message = listOf("비 오는 날엔", "천천히 와"),
        voiceSeconds = 4,
        scene = WiperScene,
    )
}

/** 11 메시지 도착 1: 봉투 (회사 도착). The letter rises, then the map scene (12) follows. */
@Composable
fun OfficeEnvelopeScreen(nav: Nav, autoAdvance: Boolean = true) {
    EnvelopeScreen(
        nav = nav,
        next = "12",
        autoAdvance = autoAdvance,
        chip = "회사 주차장에 주차하는 순간 열렸어요",
        chipIcon = Glyph("s11_icon_flag", Inset.pct(12.5f, 25f, 9.38f, 25f), Inset.pct(5.2f, 8.13f, 5.2f, 8.12f)),
        letter = listOf("도착했구나!", "오늘 발표 잘할 거야"),
        letterStyle = hand(58f, 68f),
    )
}

/** 12 메시지 도착 2: 지도 (회사 도착). The route draws itself from 출발, the heart pin drops, then the voice plays. */
@Composable
fun OfficeMapScreen(nav: Nav) {
    val route = remember { Animatable(0f) }
    val pin = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(FadeMillis.toLong())
        route.animateTo(1f, tween(1200, easing = FastOutSlowInEasing))
        pin.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
    }
    SceneScreen(
        nav = nav,
        title = "회사에 도착했어요",
        condition = "회사 주차장에 주차했을 때",
        conditionIcon = Glyph("s12_icon_flag", Inset.pct(12.5f, 25f, 9.38f, 25f), Inset.pct(5.2f, 8.13f, 5.2f, 8.12f)),
        sentAt = "오늘 아침 7:05에 남김",
        message = listOf("도착했구나!", "오늘 발표", "잘할 거야"),
        voiceSeconds = 5,
        voiceDelayMillis = FadeMillis + 1600,
    ) {
        MapArrival(route = route.value, pin = pin.value)
        MapLabel("회사", Modifier.offset(661.9.dp, 252.7.dp))
        MapLabel("출발", Modifier.offset(71.dp, 608.dp))
    }
}

/** 13 메시지 도착 2: 아침 첫 시동. */
@Composable
fun FirstStartSceneScreen(nav: Nav) {
    SceneScreen(
        nav = nav,
        title = "오늘 첫 시동을 걸었어요",
        condition = "그날 첫 시동",
        conditionIcon = Glyph("s13_icon_start", Inset.pct(18.75f, 15.63f, 12.5f, 15.63f), Inset.pct(5.91f)),
        sentAt = "어젯밤 11:20에 남김",
        message = listOf("오늘 발표", "잘해, 응원해!"),
        voiceSeconds = 4,
        scene = FirstStartScene,
    )
}

/** 14 메시지 도착 2: 밤길 (전조등). */
@Composable
fun NightSceneScreen(nav: Nav) {
    SceneScreen(
        nav = nav,
        title = "밤길에서 열린 메시지",
        condition = "전조등을 켤 때",
        conditionIcon = Glyph("s14_icon_headlight", Inset.pct(21.88f, 12.5f, 21.88f, 15.63f), Inset.pct(7.22f, 5.65f)),
        sentAt = "오늘 밤 9:05, 주행 중에 들음",
        message = listOf("밤길", "조심해서 와"),
        voiceSeconds = 3,
        scene = HeadlightScene,
    )
}

// ---------------------------------------------------------------------------------------------
// Part 1: the envelope

private const val FadeMillis = 500

/** Ink outline of the letter and the map labels. */
private val LetterInk = Color(0xFF2B2126)
private val Pill = RoundedCornerShape(999.dp)

/** An icon drawn by a Figma vector group inside a square box. */
private class Glyph(val name: String, val inset: Inset, val bleed: Inset)

@Composable
private fun GlyphIcon(glyph: Glyph, size: Dp) {
    Layered(Modifier.size(size)) { svg(glyph.name, glyph.inset, glyph.bleed) }
}

/** Heart/Handwrite with a fixed line height and -4% tracking. */
private fun hand(size: Float, lineHeight: Float, bold: Boolean = true): TextStyle =
    HeartType.gaegu(size, bold = bold, spacing = -0.04f).copy(lineHeight = lineHeight.sp)

/**
 * The envelope step shared by every condition: both cars, the condition line, the letter rising out of the
 * envelope and the hint that the voice comes next. Flap 0.3 s, letter 0.6 s, then 1.5 s later the scene.
 * Tapping anywhere goes to the scene at once.
 */
@Composable
private fun EnvelopeScreen(
    nav: Nav,
    next: String,
    autoAdvance: Boolean,
    chip: String,
    chipIcon: Glyph,
    letter: List<String>,
    letterStyle: TextStyle,
    partner: String = "서연",
    partnerNickname: String = "여니",
    me: String = "후니",
    backdrop: @Composable () -> Unit = {},
) {
    val c = Heart.colors
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(300)
        rise.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
        if (autoAdvance) {
            delay(1500)
            nav.go(next)
        }
    }
    HeartWindow(Tab.Home, nav) {
        Box(Modifier.fillMaxSize().tap { nav.go(next) }) {
            backdrop()
            HeartCar(me, Modifier.offset((-270).dp, 380.dp).size(720.dp, 558.dp))
            HeartCar(partnerNickname, Modifier.offset(1232.dp, 380.dp).size(720.dp, 558.dp))

            // condition line, centered at y = 150
            Row(
                Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .offset(y = 150.dp)
                    .padding(start = 22.dp, end = 26.dp, top = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphIcon(chipIcon, 34.dp)
                Text(chip, style = HeartType.BodyStrong, color = c.textPrimary, maxLines = 1)
            }

            FigmaSvg("scene_envelope_back", Modifier.offset(451.dp, 316.dp).size(780.dp, 560.dp))
            Letter(
                partnerNickname, letter, letterStyle,
                Modifier
                    .offset(521.dp, (236f + (1f - rise.value) * 170f).dp)
                    .alpha((rise.value * 2.5f).coerceIn(0f, 1f)),
            )
            FigmaSvg("scene_envelope_front", Modifier.offset(451.dp, 584.dp).size(780.dp, 350.dp))

            NextHint("${partner}님 목소리로 들려줄게요", Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).offset(y = 946.dp))
        }
    }
}

/** The letter card standing in the envelope, 640 x 520: the message and from, each with a ruled line under it. */
@Composable
private fun Letter(
    nickname: String,
    lines: List<String>,
    style: TextStyle,
    modifier: Modifier,
) {
    val c = Heart.colors
    val shape = RoundedCornerShape(18.dp)
    Box(modifier.size(640.dp, 520.dp).clip(shape).background(c.bgCard).border(4.dp, LetterInk, shape)) {
        Column(Modifier.fillMaxSize().padding(start = 52.dp, end = 52.dp, top = 40.dp)) {
            Text(
                lines.joinToString("\n"), style = style.copy(textAlign = TextAlign.Center), color = c.textPrimary,
                modifier = Modifier.fillMaxWidth().ruled(ruleLift(style), c.line),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "from. $nickname", style = hand(32f, 40f, bold = false).copy(textAlign = TextAlign.End), color = c.textSecondary,
                modifier = Modifier.fillMaxWidth().ruled(4f, c.line),
            )
        }
    }
}

/** A 2 px ruled line [lift] above the bottom of a text block, reaching 4 px past it on both sides. */
private fun Modifier.ruled(lift: Float, color: Color): Modifier = drawWithContent {
    drawContent()
    val side = 4.dp.toPx()
    drawRect(color, Offset(-side, size.height - lift.dp.toPx()), Size(size.width + side * 2, 2.dp.toPx()))
}

/** Where the ruled line sits under the handwritten message, by its size (Figma 09: 76 px, 11: 58 px). */
private fun ruleLift(style: TextStyle): Float = when {
    style.fontSize.value >= 76f -> 36f
    style.fontSize.value >= 58f -> 18f
    else -> 12f
}

/** "Voice comes next" line under the envelope; its three dots breathe while waiting. */
@Composable
private fun NextHint(text: String, modifier: Modifier) {
    val c = Heart.colors
    val t = rememberInfiniteTransition(label = "hint")
    val dots by t.animateFloat(0.45f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "dots")
    Row(
        modifier.padding(horizontal = 26.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FigmaSvg("scene_dots", Modifier.size(46.dp, 12.dp).alpha(dots))
        Text(text, style = HeartType.BodyStrong, color = c.textSecondary, maxLines = 1)
    }
}

/** The rain streaks over the whole window, falling slowly in a loop (two copies stacked). */
@Composable
private fun RainOverlay() {
    val t = rememberInfiniteTransition(label = "rain")
    val y by t.animateFloat(0f, 1210f, infiniteRepeatable(tween(8000, easing = LinearEasing)), label = "fall")
    FigmaSvg("s09_rain", Modifier.offset(y = y.dp).size(1682.dp, 1210.dp))
    FigmaSvg("s09_rain", Modifier.offset(y = (y - 1210f).dp).size(1682.dp, 1210.dp))
}

// ---------------------------------------------------------------------------------------------
// Part 2: the scene and the message card

/**
 * The scene step: the condition's drawing on the left, the message card with the partner's voice on the
 * right. Fades in over 0.5 s from the envelope; the voice starts after [voiceDelayMillis].
 */
@Composable
private fun SceneScreen(
    nav: Nav,
    title: String,
    condition: String,
    conditionIcon: Glyph,
    sentAt: String,
    message: List<String>,
    voiceSeconds: Int,
    voiceDelayMillis: Int = FadeMillis,
    partnerNickname: String = "여니",
    card: (@Composable () -> Unit)? = null,
    scene: @Composable BoxScope.() -> Unit,
) {
    val fade = remember { Animatable(0f) }
    LaunchedEffect(Unit) { fade.animateTo(1f, tween(FadeMillis)) }
    HeartWindow(Tab.Home, nav) {
        Row(
            Modifier.padding(start = 40.dp, top = 140.dp).alpha(fade.value),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            SceneCard(title, scene)
            if (card != null) card()
            else MessageCard(partnerNickname, condition, conditionIcon, sentAt, message, voiceSeconds, voiceDelayMillis)
        }
    }
}

/** The drawing on the page itself (no card): the P mark above its top right corner, [title] read out for it. */
@Composable
private fun SceneCard(title: String, scene: @Composable BoxScope.() -> Unit) {
    Column(
        Modifier.padding(start = 26.dp, end = 26.dp, top = 24.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(Modifier.width(950.dp), horizontalArrangement = Arrangement.End) { ParkedMark() }
        Box(
            Modifier.size(950.dp, 722.dp).clip(RoundedCornerShape(24.dp)).semantics { contentDescription = title },
            content = scene,
        )
    }
}

/** 46 px P in a ring: the car is parked. */
@Composable
private fun ParkedMark() {
    val c = Heart.colors
    Box(
        Modifier.size(46.dp).border(3.dp, c.textSecondary, CircleShape).clearAndSetSemantics { contentDescription = "주차 중" },
        contentAlignment = Alignment.Center,
    ) { Text("P", style = HeartType.noto(24f, 700), color = c.textSecondary, maxLines = 1) }
}

/** The demo card: the recorded voice plays after [voiceDelayMillis]; 다시 듣기 starts it again from 0. */
@Composable
private fun MessageCard(
    nickname: String,
    condition: String,
    conditionIcon: Glyph,
    sentAt: String,
    message: List<String>,
    voiceSeconds: Int,
    voiceDelayMillis: Int,
) {
    var plays by remember { mutableIntStateOf(0) }
    val elapsed = remember { Animatable(0f) }
    LaunchedEffect(plays) {
        elapsed.snapTo(0f)
        if (plays == 0) delay(voiceDelayMillis.toLong())
        elapsed.animateTo(voiceSeconds.toFloat(), tween(voiceSeconds * 1000, easing = LinearEasing))
    }
    MessageCardLayout(sentAt, condition, conditionIcon, message.joinToString("\n"), hand(76f, 86f), nickname) {
        VoiceRow(playing = true, time = "${clock(elapsed.value.toInt())} / ${clock(voiceSeconds)}")
        Spacer(Modifier.height(18.dp))
        ReplayButton(Modifier.fillMaxWidth(), onClick = { plays++ })
    }
}

/**
 * 568 x 840 card shared by the demo and the live scene: when it was left, the condition, the handwritten
 * message and from, then [bottom] (the voice and the buttons) at the foot. The heart stamp sits in the corner.
 */
@Composable
private fun MessageCardLayout(
    sentAt: String,
    condition: String,
    conditionIcon: Glyph,
    message: String,
    messageStyle: TextStyle,
    nickname: String,
    bottom: @Composable ColumnScope.() -> Unit,
) {
    val c = Heart.colors
    val shape = RoundedCornerShape(36.dp)
    Box(Modifier.size(568.dp, 840.dp).clip(shape).background(c.bgCard).border(2.dp, c.line, shape)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 44.dp, vertical = 40.dp)) {
            Text(sentAt, style = HeartType.Label, color = c.textSecondary, maxLines = 1)
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.padding(end = 20.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphIcon(conditionIcon, 28.dp)
                Text(condition, style = HeartType.BodyStrong, color = c.pinkDeep, maxLines = 1)
            }
            Spacer(Modifier.height(36.dp))
            Text(message, style = messageStyle, color = c.textPrimary, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Text(
                "from. $nickname", style = hand(32f, 40f, bold = false).copy(textAlign = TextAlign.End), color = c.textSecondary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.weight(1f))
            bottom()
        }

        // heart stamp in the top right corner, tilted 8 degrees
        FigmaSvg("scene_heart_stamp", Modifier.offset(442.54.dp, 25.53.dp).size(86.dp, 100.dp).rotate(-8f))
    }
}

/** Play or pause, the waveform and the time, straight on the card. */
@Composable
private fun VoiceRow(playing: Boolean, time: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(end = 22.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FigmaSvg(if (playing) "scene_pause" else "s06_play", Modifier.size(52.dp))
        FigmaSvg("scene_waveform", Modifier.size(168.dp, 44.dp))
        Text(time, style = HeartType.Label, color = Heart.colors.textSecondary, maxLines = 1)
    }
}

/** 다시 듣기: white with a 2 px pink line inside and a deep pink label. */
@Composable
private fun ReplayButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Heart.colors
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier.height(88.dp).clip(shape).background(c.bgCard).border(2.dp, c.pinkAccent, shape).tap(onClick),
        contentAlignment = Alignment.Center,
    ) { Text("다시 듣기", style = HeartType.Headline, color = c.pinkDeep, maxLines = 1, modifier = Modifier.padding(horizontal = 40.dp)) }
}

private fun clock(seconds: Int) = "%d:%02d".format(seconds / 60, seconds % 60)

// ---------------------------------------------------------------------------------------------
// Heart/Map Arrival (950 x 722)

private fun s(top: Float, right: Float, bottom: Float, left: Float) = Inset.pct(top, right, bottom, left)

/** The hand-drawn arrival map. [route] reveals the dashed route from 출발 (0..1), [pin] drops the heart pin (0..1, springy). */
@Composable
private fun MapArrival(route: Float, pin: Float) {
    Layered(Modifier.fillMaxSize()) {
        svg("s12_map_ground", Inset.Zero)
        svg("s12_map_blocks", Inset.pct(2.37f, 1.8f))
        svg("s12_map_roads", Inset.pct(-2.63f, -2f), Inset.pct(5.38f, 4.13f))
        // small buildings
        svg("s12_house1", s(9.08f, 82.1f, 76.84f, 5.6f), Inset.pct(2.1f, 1.83f))
        svg("s12_house2", s(13.95f, 49.1f, 71.32f, 39.1f), Inset.pct(2.01f, 1.91f))
        svg("s12_house3", s(54.74f, 80.6f, 31.71f, 6.9f), Inset.pct(2.18f, 1.8f))
        svg("s12_house4", s(54.47f, 11.2f, 31.84f, 74.7f), Inset.pct(2.16f, 1.6f))
        svg("s12_park", s(53.68f, 46.71f, 30.12f, 37.44f))
        // trees
        svg("s12_tree1", s(26.18f, 77f, 66.71f, 18.4f), Inset.pct(3.7f, 4.35f))
        svg("s12_tree2", s(28.82f, 90.3f, 64.47f, 5.7f), Inset.pct(3.92f, 5f))
        svg("s12_tree3", s(6.45f, 45.8f, 86.97f, 50.4f), Inset.pct(4f, 5.26f))
        svg("s12_tree4", s(56.18f, 58.2f, 36.84f, 37.4f), Inset.pct(3.77f, 4.55f))
        svg("s12_tree5", s(55.53f, 48.3f, 37.11f, 46.7f), Inset.pct(3.57f, 4f))
        svg("s12_tree6", s(57.76f, 4.6f, 35.39f, 91.2f), Inset.pct(3.85f, 4.76f))
        svg("s12_tree3", s(89.47f, 78.4f, 3.95f, 17.8f), Inset.pct(4f, 5.26f))
        svg("s12_tree4", s(88.95f, 27.5f, 4.08f, 68.1f), Inset.pct(3.77f, 4.55f))
        svg("s12_company", s(9.71f, 18.3f, 66.24f, 66.6f), Inset.pct(1.37f, 1.66f))
        svg("s12_parking", s(14.47f, 3.7f, 55.66f, 83.3f), Inset.pct(0.88f, 1.54f, 8.81f, 1.54f))
        // the route runs left to right from 출발, so a growing clip draws it like a line
        svg(
            "s12_route", s(31.58f, 10.9f, 18.29f, 10.9f), Inset.pct(1.05f, 0.51f, 0.52f, 0.26f),
            Modifier.drawWithContent { clipRect(right = size.width * route) { this@drawWithContent.drawContent() } },
        )
        svg(
            "s12_pin", s(18.68f, 6.8f, 63.29f, 85f), Inset.pct(1.46f, 2.74f, 0f, 2.74f),
            Modifier.graphicsLayer {
                translationY = (1f - pin) * -70.dp.toPx()
                alpha = if (pin > 0f) 1f else 0f
            },
        )
    }
}

/** White pill with an ink outline naming a place on the map. */
@Composable
private fun MapLabel(text: String, modifier: Modifier) {
    Box(
        modifier
            .clip(Pill)
            .background(Heart.colors.bgCard)
            .border(3.dp, LetterInk, Pill)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) { Text(text, style = HeartType.BodyStrong, color = Heart.colors.textPrimary, maxLines = 1) }
}

// ---------------------------------------------------------------------------------------------
// Live: a message that opened in this car

/** The opened message the arrival screens show now. */
internal object Arrival {
    var current by mutableStateOf<Message?>(null)
}

/** How a condition looks on the envelope line, the scene title (read out for the drawing), the card and the drawing. */
private class Look(
    val chip: String,
    val icon: Glyph,
    val title: String,
    val condition: String,
    val rain: Boolean = false,
    val scene: @Composable BoxScope.() -> Unit,
)

private val WiperGlyph = Glyph("s09_icon_wiper", Inset.pct(28.13f, 12.5f, 16.87f, 12.5f), Inset.pct(6.82f, 5f))
private val FlagGlyph = Glyph("s11_icon_flag", Inset.pct(12.5f, 25f, 9.38f, 25f), Inset.pct(5.2f, 8.13f, 5.2f, 8.12f))
private val StartGlyph = Glyph("s13_icon_start", Inset.pct(18.75f, 15.63f, 12.5f, 15.63f), Inset.pct(5.91f))
private val HeadlightGlyph = Glyph("s14_icon_headlight", Inset.pct(21.88f, 12.5f, 21.88f, 15.63f), Inset.pct(7.22f, 5.65f))
private val PlaceGlyph = Glyph("s15_icon_place", Inset.pct(9.38f, 18.75f), Inset.pct(4.62f, 6f))

private fun optionGlyph(value: String, fallback: Glyph): Glyph =
    ConditionGroup.optionFor(value)?.let { Glyph(it.icon, it.inset, it.bleed) } ?: fallback

/** A windshield drawing (1070 x 817, colored pencil) filling the 950 x 722 scene window. */
private fun sceneImage(@DrawableRes id: Int): @Composable BoxScope.() -> Unit = {
    Image(painterResource(id), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
}

private val WiperScene = sceneImage(R.drawable.scene_wiper)
private val FirstStartScene = sceneImage(R.drawable.scene_first_start)
private val HeadlightScene = sceneImage(R.drawable.scene_headlight)
private val SeatHeaterScene = sceneImage(R.drawable.scene_seat_heater)
private val AirconScene = sceneImage(R.drawable.scene_aircon)
private val WindowScene = sceneImage(R.drawable.scene_window)
private val PlaceScene = sceneImage(R.drawable.scene_place)

/** The time-of-day drawing for a band, by the hour it starts (a custom range too). */
private fun timeScene(band: TimeBand?): @Composable BoxScope.() -> Unit = sceneImage(
    when (band?.start?.hour) {
        null -> R.drawable.scene_morning
        in 4..6 -> R.drawable.scene_dawn
        in 7..10 -> R.drawable.scene_morning
        in 11..13 -> R.drawable.scene_noon
        in 14..16 -> R.drawable.scene_afternoon
        in 17..19 -> R.drawable.scene_evening
        else -> R.drawable.scene_night_city
    },
)

private fun mapScene(to: String): @Composable BoxScope.() -> Unit = {
    val route = remember { Animatable(0f) }
    val pin = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(FadeMillis.toLong())
        route.animateTo(1f, tween(1200, easing = FastOutSlowInEasing))
        pin.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
    }
    MapArrival(route = route.value, pin = pin.value)
    MapLabel(to, Modifier.offset(661.9.dp, 252.7.dp))
    MapLabel("출발", Modifier.offset(71.dp, 608.dp))
}

private fun look(m: Message): Look = when (m.group) {
    DataGroup.Vehicle -> when (m.conditionValue) {
        ConditionValue.WIPER -> Look("비 오는 길, 와이퍼를 켜는 순간 열렸어요", WiperGlyph, "비 오는 길에서 열린 메시지", "와이퍼를 켤 때", rain = true, scene = WiperScene)
        ConditionValue.HEADLIGHT -> Look("전조등을 켜는 순간 열렸어요", HeadlightGlyph, "밤길에서 열린 메시지", "전조등을 켤 때", scene = HeadlightScene)
        ConditionValue.SEAT_HEATER -> Look("열선 시트를 켜는 순간 열렸어요", optionGlyph(m.conditionValue, StartGlyph), "열선 시트를 켰어요", "열선 시트를 켤 때", scene = SeatHeaterScene)
        ConditionValue.AIRCON -> Look("에어컨을 켜는 순간 열렸어요", optionGlyph(m.conditionValue, StartGlyph), "에어컨을 켰어요", "에어컨을 켤 때", scene = AirconScene)
        else -> Look("창문을 여는 순간 열렸어요", optionGlyph(m.conditionValue, StartGlyph), "창문을 열었어요", "창문을 열 때", scene = WindowScene)
    }
    DataGroup.FirstStart -> Look("오늘 첫 시동을 거는 순간 열렸어요", StartGlyph, "오늘 첫 시동을 걸었어요", "그날 첫 시동을 걸 때", scene = FirstStartScene)
    DataGroup.Arrival ->
        if (m.conditionValue == ConditionValue.PARKED) {
            val at = m.parkedAt()
            Look("${at}주차하는 순간 열렸어요", FlagGlyph, "${at}주차했어요", "${at}주차했을 때", scene = mapScene(m.place?.let { parkName(it) } ?: "주차"))
        }
        else Look(
            "목적지에 도착하는 순간 열렸어요", FlagGlyph, "목적지에 도착했어요", "목적지에 도착할 때",
            scene = mapScene(kr.heartblink.car.app.NaviWatch.arrivedAt?.takeIf { it.isNotBlank() } ?: "도착"),
        )
    DataGroup.Place -> {
        val name = m.placeName?.takeIf { it.isNotBlank() } ?: "고른 장소"
        Look("$name 근처에 오는 순간 열렸어요", PlaceGlyph, "$name 근처에 왔어요", "$name 근처에 갈 때", scene = PlaceScene)
    }
    DataGroup.Time, null -> {
        val label = ConditionGroup.timeLabel(m.conditionValue) ?: "고른 시간"
        Look("${label}에 열렸어요", optionGlyph(m.conditionValue, StartGlyph), "${label}에 열린 메시지", "${label}에", scene = timeScene(TimeBand.of(m.conditionValue)))
    }
}

/** The handwritten message: a short one in big letters, a long one smaller, wrapped between words. */
private fun letterStyle(body: String): TextStyle = when {
    body.length <= 7 -> hand(76f, 86f)
    body.length <= 16 -> hand(58f, 70f)
    else -> hand(46f, 58f)
}.copy(lineBreak = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase))

private fun Message.letterText(): String = body.ifBlank { "목소리로 남긴 메시지예요" }

@Composable
private fun LiveEnvelopeScreen(nav: Nav) {
    val m = Arrival.current
    if (m == null) {
        LaunchedEffect(Unit) { nav.go("04") }
        return
    }
    val v = Session.view
    val l = look(m)
    EnvelopeScreen(
        nav = nav,
        next = "arrive-scene",
        autoAdvance = true,
        chip = l.chip,
        chipIcon = l.icon,
        letter = listOf(m.letterText()),
        letterStyle = letterStyle(m.letterText()),
        partner = v.partnerName,
        partnerNickname = v.partnerNickname,
        me = v.myNickname,
        backdrop = { if (l.rain) RainOverlay() },
    )
}

@Composable
private fun LiveSceneScreen(nav: Nav) {
    val m = Arrival.current
    if (m == null) {
        LaunchedEffect(Unit) { nav.go("04") }
        return
    }
    val v = Session.view
    val l = look(m)
    val sentAt = m.createdAt?.let { "${koreanMoment(it)}에 남김" }.orEmpty()
    SceneScreen(
        nav = nav,
        title = l.title,
        condition = l.condition,
        conditionIcon = l.icon,
        sentAt = sentAt,
        message = listOf(m.letterText()),
        voiceSeconds = 0,
        partnerNickname = v.partnerNickname,
        card = { LiveMessageCard(m, v.partnerNickname, l, sentAt, nav) },
        scene = l.scene,
    )
}

/** The message card with the partner's real voice; 다음 메시지 goes on to the next opened one. */
@Composable
private fun LiveMessageCard(m: Message, nickname: String, l: Look, sentAt: String, nav: Nav) {
    val scope = rememberCoroutineScope()
    val playback = rememberVoicePlayback()
    val progress by playback.progress.collectAsState(initial = 0f)
    val path = m.audioPath
    val totalMs = m.audioMs ?: 0
    val heard: () -> Unit = {
        scope.launch {
            Session.repository?.markMessage(m.id, opened = true, heard = true)
            MessageOpener.refreshWaiting()
        }
    }
    LaunchedEffect(m.id) {
        if (path != null) {
            delay(FadeMillis.toLong())
            playback.toggleRemote(path, heard)
        } else {
            heard()
        }
    }
    val playing = path != null && playback.isPlaying(path)
    val waiting by MessageOpener.waiting.collectAsState()
    val rest = waiting.filter { it.id != m.id }
    val goOn = {
        playback.stop()
        Arrival.current = rest.firstOrNull()
        nav.go(if (rest.isEmpty()) "04" else "arrive")
    }

    MessageCardLayout(sentAt, l.condition, l.icon, m.letterText(), letterStyle(m.letterText()), nickname) {
        if (path != null) {
            val elapsed = if (playing) (progress * totalMs).toInt() else 0
            VoiceRow(playing, "${durationText(elapsed)} / ${durationText(totalMs)}", Modifier.tap { playback.toggleRemote(path, heard) })
            Spacer(Modifier.height(18.dp))
        }
        // 다시 듣기 hugs its label; the pink button takes the rest of the row
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (path != null) {
                ReplayButton(onClick = {
                    playback.stop()
                    playback.toggleRemote(path, heard)
                })
            }
            PrimaryButton(if (rest.isEmpty()) "홈으로" else "다음 메시지", Modifier.weight(1f), onClick = goOn)
        }
    }
}
