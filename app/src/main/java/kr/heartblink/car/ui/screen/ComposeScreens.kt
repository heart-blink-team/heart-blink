package kr.heartblink.car.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.heartblink.car.ui.component.FigmaSvg
import kr.heartblink.car.ui.component.HeartCar
import kr.heartblink.car.ui.component.Shadow
import kr.heartblink.car.ui.component.Tab
import kr.heartblink.car.ui.component.innerShadow
import kr.heartblink.car.ui.component.outerShadow
import kr.heartblink.car.ui.theme.Heart
import kr.heartblink.car.ui.theme.HeartType

/** Figma screens 05, 06, 06-1, 07, 08: 오늘의 질문, 메시지 남기기. */
val composeScreens: Map<String, @Composable (Nav) -> Unit> = linkedMapOf<String, @Composable (Nav) -> Unit>(
    "05" to { nav -> QuestionScreen(nav) },
    "06" to { nav -> MessageScreen(nav, ConditionGroup.Vehicle) },
    "06-1" to { nav -> MessageEditScreen(nav) },
    "07" to { nav -> MessageScreen(nav, ConditionGroup.Time) },
    "08" to { nav -> MessageSentScreen(nav) },
)

/**
 * 05 오늘의 질문. Questions go in order, not by date: the next one opens only after both answered.
 * When both have answered, the partner's card turns over on the Y axis in 0.6 s to show the answer.
 */
@Composable
fun QuestionScreen(nav: Nav) {
    val c = Heart.colors
    val flip = remember { Animatable(-90f) }
    LaunchedEffect(Unit) { flip.animateTo(0f, tween(600, easing = FastOutSlowInEasing)) }
    HeartWindow(Tab.Question, nav) {
        HeartCar("후니", Modifier.offset((-270).dp, 400.dp).size(720.dp, 558.dp))
        HeartCar("여니", Modifier.offset(1232.dp, 400.dp).size(720.dp, 558.dp))
        QuestionCard(Modifier.offset(560.dp, 222.dp))
        AnswerCard(
            "나의 답", "캠핑\n가고 싶어!", "0:04", "from. 후니",
            Tape(27.13f, -9f, 7f),
            Modifier.offset(190.dp, 302.dp),
        )
        AnswerCard(
            "서연의 답", "캠핑 가서\n같이 별\n보고 싶어!", "0:05", "from. 여니",
            Tape(321.6f, -25.9f, -6f),
            Modifier.offset(992.dp, 302.dp).graphicsLayer {
                rotationY = flip.value
                cameraDistance = 16f * density
            },
        )
        Text(
            "우리의 일기장에 저장했어요. 다음 질문이 열렸어요",
            style = HeartType.Label, color = c.textSecondary, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().offset(y = 1008.dp),
        )
        FigmaSvg("s05_sparkle", Modifier.offset(600.dp, 250.dp).size(40.dp))
        FigmaSvg("s05_sparkle_small", Modifier.offset(648.dp, 236.dp).size(24.dp))
        FigmaSvg("s05_doodle_heart", Modifier.offset(1030.dp, 244.dp).size(44.dp))
    }
}

@Composable
private fun QuestionCard(modifier: Modifier) {
    val c = Heart.colors
    val shape = RoundedCornerShape(40.dp)
    val pill = RoundedCornerShape(999.dp)
    Column(
        modifier
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
        Spacer(Modifier.height(150.dp))
        Text("128번째 질문", style = HeartType.Label, color = c.textSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Text(
            "이번 주말에\n나랑 꼭\n하고 싶은 것\n하나는?",
            style = HeartType.gaegu(58f, spacing = -0.04f).copy(lineHeight = 68.sp),
            color = c.textPrimary, textAlign = TextAlign.Center,
            modifier = Modifier.wrapContentWidth(unbounded = true),
        )
        Spacer(Modifier.height(36.dp))
        Text(
            "둘 다 답해서\n서로의 답이 열렸어요",
            style = HeartType.Label.copy(lineHeight = 34.sp), color = c.pinkDeep, textAlign = TextAlign.Center,
            modifier = Modifier.wrapContentWidth(unbounded = true),
        )
    }
}

/** Masking tape over a card corner: [x], [y] is its top-left in the card, turned [rotation] degrees. */
private class Tape(val x: Float, val y: Float, val rotation: Float)

@Composable
private fun AnswerCard(title: String, answer: String, duration: String, from: String, tape: Tape, modifier: Modifier) {
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
            Spacer(Modifier.height(90.dp))
            Text(answer, style = HeartType.gaegu(76f).copy(lineHeight = 86.sp), color = c.textPrimary)
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .background(c.pinkLight)
                    .padding(start = 14.dp, end = 24.dp, top = 14.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FigmaSvg("s05_play", Modifier.size(56.dp))
                FigmaSvg("s05_waveform", Modifier.size(180.dp, 44.dp))
                Text(duration, style = HeartType.Label, color = c.pinkDeep, maxLines = 1)
            }
            Spacer(Modifier.height(24.dp))
            Text(from, style = HeartType.gaegu(32f, bold = false, spacing = -0.04f).copy(lineHeight = 40.sp), color = c.textTertiary, maxLines = 1)
        }
        // notebook lines under the two lines of the answer and one more
        listOf(280, 366, 452).forEach { y ->
            Box(Modifier.offset(44.dp, y.dp).size(412.dp, 2.dp).background(c.line))
        }
        MaskingTape(tape.rotation, Modifier.offset(tape.x.dp, tape.y.dp))
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
