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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kr.heartblink.car.ui.component.FigmaSvg
import kr.heartblink.car.ui.component.HeartCar
import kr.heartblink.car.ui.component.Inset
import kr.heartblink.car.ui.component.Layered
import kr.heartblink.car.ui.component.SecondaryButton
import kr.heartblink.car.ui.component.Shadow
import kr.heartblink.car.ui.component.Tab
import kr.heartblink.car.ui.component.outerShadow
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
        chip = "비 오는 길, 와이퍼를 켜는 순간 열렸어요",
        chipIcon = Glyph("s09_icon_wiper", Inset.pct(28.13f, 12.5f, 16.87f, 12.5f), Inset.pct(6.82f, 5f)),
        letter = listOf("비 오는 날엔", "천천히 와"),
        letterStyle = hand(76f, 86f),
        letterRules = listOf(172f, 258f),
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
    ) {
        // Heart/Scene Wiper: one flattened drawing larger than the 950 x 722 window
        Layered(Modifier.fillMaxSize()) {
            svg("s10_scene_wiper", Inset.pct(-15.92f, -14f, -9.85f, -11f), Inset.pct(0f, 0.27f, 0.35f, 0.27f))
        }
    }
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
        letterRules = listOf(154f, 222f),
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
    ) {
        // Heart/Scene First Start
        Layered(Modifier.fillMaxSize()) {
            svg("s13_scene_first_start", Inset.pct(-9.21f, -6f, -3.48f, -6f), Inset.pct(0f, 0.27f, 0.35f, 0.27f))
        }
    }
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
    ) {
        // Heart/Scene Night
        Layered(Modifier.fillMaxSize()) {
            svg("s14_scene_night", Inset.pct(0f, 0f, -2.63f, -1.3f), Inset.pct(0f, 0.22f, 0.29f, 0.22f))
        }
    }
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
 * The envelope step shared by every condition: both cars, the condition chip, the letter rising out of the
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
    letterRules: List<Float>,
    partner: String = "서연",
    partnerNickname: String = "여니",
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
            HeartCar("후니", Modifier.offset((-270).dp, 380.dp).size(720.dp, 558.dp))
            HeartCar(partnerNickname, Modifier.offset(1232.dp, 380.dp).size(720.dp, 558.dp))

            // condition chip, centered at y = 150
            Row(
                Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .offset(y = 150.dp)
                    .clip(Pill)
                    .background(c.bgCard)
                    .border(2.dp, c.line, Pill)
                    .padding(start = 22.dp, end = 26.dp, top = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphIcon(chipIcon, 34.dp)
                Text(chip, style = HeartType.BodyStrong, color = c.textPrimary, maxLines = 1)
            }

            FigmaSvg("scene_envelope_back", Modifier.offset(451.dp, 316.dp).size(780.dp, 560.dp))
            Letter(
                partner, partnerNickname, letter, letterStyle, letterRules,
                Modifier
                    .offset(521.dp, (236f + (1f - rise.value) * 170f).dp)
                    .alpha((rise.value * 2.5f).coerceIn(0f, 1f)),
            )
            FigmaSvg("scene_envelope_front", Modifier.offset(451.dp, 584.dp).size(780.dp, 350.dp))

            NextHint("${partner}님 목소리로 이어서 들려줄게요", Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).offset(y = 946.dp))
        }
    }
}

/** The letter card standing in the envelope, 640 x 520 with two ruled lines under the message. */
@Composable
private fun Letter(
    partner: String,
    nickname: String,
    lines: List<String>,
    style: TextStyle,
    rules: List<Float>,
    modifier: Modifier,
) {
    val c = Heart.colors
    val shape = RoundedCornerShape(18.dp)
    Box(modifier.size(640.dp, 520.dp).clip(shape).background(c.bgCard).border(4.dp, LetterInk, shape)) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("${partner}님이 남긴 메시지가 도착했어요", style = HeartType.Label, color = c.textSecondary, maxLines = 1)
            Spacer(Modifier.height(22.dp))
            Text(
                lines.joinToString("\n"), style = style.copy(textAlign = TextAlign.Center), color = c.textPrimary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "from. $nickname", style = hand(32f, 40f, bold = false).copy(textAlign = TextAlign.End), color = c.textSecondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        rules.forEach { y -> Box(Modifier.offset(44.dp, y.dp).size(544.dp, 2.dp).background(c.line)) }
    }
}

/** "Voice comes next" pill under the envelope; its three dots breathe while waiting. */
@Composable
private fun NextHint(text: String, modifier: Modifier) {
    val c = Heart.colors
    val t = rememberInfiniteTransition(label = "hint")
    val dots by t.animateFloat(0.45f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "dots")
    Row(
        modifier
            .clip(Pill)
            .background(Color.White)
            .border(2.dp, c.line, Pill)
            .padding(horizontal = 26.dp, vertical = 14.dp),
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
    partner: String = "서연",
    partnerNickname: String = "여니",
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
            MessageCard(partner, partnerNickname, condition, conditionIcon, sentAt, message, voiceSeconds, voiceDelayMillis)
        }
    }
}

private val CardShadow = Shadow(0f, 14f, 36f, Color(0x1429211F))

@Composable
private fun SceneCard(title: String, scene: @Composable BoxScope.() -> Unit) {
    val c = Heart.colors
    val shape = RoundedCornerShape(36.dp)
    Column(
        Modifier
            .outerShadow(shape, CardShadow)
            .clip(shape)
            .background(c.bgCard)
            .border(2.dp, c.line, shape)
            .padding(start = 26.dp, end = 26.dp, top = 24.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(Modifier.width(950.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = HeartType.Headline, color = c.textPrimary, maxLines = 1)
            Box(Modifier.clip(Pill).background(c.pinkLight).padding(start = 16.dp, end = 18.dp, top = 6.dp, bottom = 6.dp)) {
                Text("주차 중 (P)", style = HeartType.Label, color = c.textSecondary, maxLines = 1)
            }
        }
        Box(Modifier.size(950.dp, 722.dp).clip(RoundedCornerShape(24.dp)), content = scene)
    }
}

/** 568 x 840 card: who left it, the condition, the handwritten message on ruled lines, the voice and 다시 듣기. */
@Composable
private fun MessageCard(
    partner: String,
    nickname: String,
    condition: String,
    conditionIcon: Glyph,
    sentAt: String,
    message: List<String>,
    voiceSeconds: Int,
    voiceDelayMillis: Int,
) {
    val c = Heart.colors
    val shape = RoundedCornerShape(36.dp)

    // Playback of the recorded voice; 다시 듣기 starts it again from 0.
    var plays by remember { mutableIntStateOf(0) }
    val elapsed = remember { Animatable(0f) }
    LaunchedEffect(plays) {
        elapsed.snapTo(0f)
        if (plays == 0) delay(voiceDelayMillis.toLong())
        elapsed.animateTo(voiceSeconds.toFloat(), tween(voiceSeconds * 1000, easing = LinearEasing))
    }

    Box(
        Modifier
            .size(568.dp, 840.dp)
            .outerShadow(shape, CardShadow)
            .clip(shape)
            .background(c.bgCard)
            .border(2.dp, c.line, shape),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 44.dp, vertical = 40.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(c.pinkLight), contentAlignment = Alignment.Center) {
                    Text(partner.take(1), style = HeartType.gaegu(34f), color = c.textPrimary)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${partner}님이 남겨 둔 메시지", style = HeartType.BodyStrong, color = c.textPrimary, maxLines = 1)
                    Text(sentAt, style = HeartType.Label, color = c.textTertiary, maxLines = 1)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.clip(Pill).background(c.pinkLight).padding(start = 16.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphIcon(conditionIcon, 28.dp)
                Text(condition, style = HeartType.BodyStrong, color = c.pinkDeep, maxLines = 1)
            }
            Spacer(Modifier.height(36.dp))
            Text(message.joinToString("\n"), style = hand(76f, 86f), color = c.textPrimary, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Text(
                "from. $nickname", style = hand(32f, 40f, bold = false).copy(textAlign = TextAlign.End), color = c.textSecondary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(Pill)
                    .background(c.pinkLight)
                    .padding(start = 12.dp, end = 22.dp, top = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FigmaSvg("scene_pause", Modifier.size(52.dp))
                FigmaSvg("scene_waveform", Modifier.size(168.dp, 44.dp))
                Text("${clock(elapsed.value.toInt())} / ${clock(voiceSeconds)}", style = HeartType.Label, color = c.textSecondary, maxLines = 1)
            }
            Spacer(Modifier.height(18.dp))
            SecondaryButton("다시 듣기", Modifier.fillMaxWidth(), onClick = { plays++ })
        }

        // heart stamp in the top right corner, tilted 8 degrees
        FigmaSvg("scene_heart_stamp", Modifier.offset(442.54.dp, 25.53.dp).size(86.dp, 100.dp).rotate(-8f))
        listOf(302f, 388f, 474f, 560f).forEach { y -> Box(Modifier.offset(42.dp, y.dp).size(480.dp, 2.dp).background(c.line)) }
    }
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
