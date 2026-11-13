package kr.heartblink.car.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kr.heartblink.car.ui.theme.Gaegu
import kr.heartblink.car.ui.theme.Heart
import kr.heartblink.car.ui.theme.HeartType
import kr.heartblink.car.ui.theme.Ink

/** Heart/Logo: the 하트깜빡이 wordmark with its lamp lit. */
@Composable
fun HeartLogo(modifier: Modifier = Modifier.size(302.4.dp, 92.4.dp)) {
    Layered(modifier) {
        svg("logo_glyph1", Inset.pct(34.85f, 73.16f, 21.51f, 15.11f), Inset.pct(4.75f, 5.4f))
        svg("logo_glyph2", Inset.pct(36.6f, 58.89f, 25.44f, 29.91f), Inset.pct(5.46f, 5.65f))
        svg("logo_glyph3", Inset.pct(34.85f, 43.82f, 20.64f, 44.04f), Inset.pct(11.27f, 12.64f))
        svg("logo_glyph4", Inset.pct(34.85f, 29.16f, 20.2f, 58.58f), Inset.pct(11.17f, 12.5f))
        svg("logo_glyph5", Inset.pct(34.85f, 16.22f, 21.51f, 73.58f), Inset.pct(4.75f, 6.21f))
        svg("logo_lamp", Inset.pct(18.18f, 27.82f, 59.09f, 65.24f))
        svg("logo_rays", Inset.pct(10.45f, 27.64f, 75.45f, 44.86f), Inset.pct(5.48f, 0.86f))
    }
}

/** Header corner button (shop, account): the duotone icon alone, a 92 px touch area, no circle behind it. */
@Composable
fun RoundIconButton(icon: @Composable BoxScope.() -> Unit, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Box(
        modifier
            .size(92.dp)
            .clip(CircleShape)
            .tap(onClick),
        contentAlignment = Alignment.Center,
    ) { Box(Modifier.size(52.dp), content = icon) }
}

/** Heart/Header: shop on the left, logo in the middle, account on the right. */
@Composable
fun HeartHeader(modifier: Modifier = Modifier, onShop: (() -> Unit)? = null, onAccount: (() -> Unit)? = null) {
    Box(modifier.fillMaxWidth().height(92.dp)) {
        RoundIconButton({ FigmaSvg("icon_shop", Modifier.fillMaxSize()) }, Modifier.align(Alignment.CenterStart), onShop)
        HeartLogo(Modifier.align(Alignment.Center).size(302.4.dp, 92.4.dp))
        RoundIconButton(
            { Layered(Modifier.fillMaxSize()) { svg("icon_account", Inset.pct(17.19f, 17.19f, 14.06f, 17.19f), Inset.pct(5.45f, 5.72f, 5.46f, 5.72f)) } },
            Modifier.align(Alignment.CenterEnd), onAccount,
        )
    }
}

enum class Tab(val label: String, val icon: String, val iconInset: Inset, val iconBleed: Inset) {
    Home("홈", "tab_home", Inset.pct(12.5f, 15.63f, 15.63f, 15.63f), Inset.pct(5.65f, 5.91f)),
    Question("오늘의 질문", "tab_question", Inset.pct(15.63f), Inset.pct(5.91f)),
    Message("메시지 남기기", "tab_message", Inset.pct(10.94f, 21.88f), Inset.pct(5.2f, 7.22f)),
    Record("기록", "tab_record", Inset.pct(20f, 15.63f, 20f, 12.19f), Inset.pct(6.77f, 5.63f)),
}

/**
 * Heart2/Toolbar: the four tabs on a white bar fixed to the bottom edge, 1682 x 112 with a hairline on top.
 * The active tab is deep pink and bold with a short pink bar above it; nothing floats or casts a shadow.
 */
@Composable
fun HeartToolbar(active: Tab?, modifier: Modifier = Modifier, newQuestion: Boolean = false, onTab: (Tab) -> Unit = {}) {
    val c = Heart.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(112.dp)
            .background(c.bgCard)
            .drawBehind { drawRect(c.line, size = androidx.compose.ui.geometry.Size(size.width, 2.dp.toPx())) }
            .padding(horizontal = 80.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { tab ->
            val on = tab == active
            Box(Modifier.weight(1f).fillMaxHeight().tap { onTab(tab) }) {
                if (on) Box(Modifier.align(Alignment.TopCenter).padding(top = 2.dp).size(72.dp, 6.dp).clip(RoundedCornerShape(3.dp)).background(c.pinkAccent))
                Row(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Layered(Modifier.size(40.dp)) { svg(tab.icon + if (on) "_on" else "_off", tab.iconInset, tab.iconBleed) }
                    Text(
                        tab.label,
                        style = if (on) HeartType.Headline else HeartType.BodyStrong,
                        color = if (on) c.pinkDeep else c.textSecondary,
                        maxLines = 1,
                    )
                }
                if (tab == Tab.Question && newQuestion) NewBadge(Modifier.align(Alignment.TopCenter).offset(x = (-63).dp, y = 2.dp))
            }
        }
    }
}

/** The small "새 질문" pill over the question tab. */
@Composable
fun NewBadge(modifier: Modifier = Modifier, text: String = "새 질문") {
    val c = Heart.colors
    val pill = RoundedCornerShape(999.dp)
    Box(
        modifier
            .clip(pill)
            .background(c.pinkAccent)
            .border(3.dp, c.bgCard, pill)
            .padding(horizontal = 10.dp, vertical = 2.dp),
    ) { Text(text, style = HeartType.Small, color = c.textOnAccent, maxLines = 1) }
}

/**
 * Heart/Car: the hand-drawn car seen from behind, 720 x 558 at full size. The nickname is the plate
 * lettering; [blinking] lights the heart tail lamp while the partner is driving.
 */
@Composable
fun HeartCar(
    nickname: String,
    modifier: Modifier = Modifier.size(720.dp, 558.dp),
    blinking: Boolean = false,
    nicknameSize: Float = 57.6f,
) {
    BoxWithConstraints(modifier) {
        val k = maxWidth.value / 720f
        Layered(Modifier.fillMaxSize()) {
            svg("car_body", Inset.pct(15.65f, 7.63f, 8.39f, 7.63f), Inset.pct(0.62f, 0.43f))
            svg("car_plate", Inset.pct(51.77f, 32.5f, 35.32f, 32.5f), Inset.pct(2.86f, 0.82f))
            svg("car_tail", Inset.pct(44.05f, 12.54f, 44.84f, 12.54f), Inset.pct(3.7f, 0.43f))
            svg("car_antenna", Inset.pct(9.13f, 48.56f, 84.35f, 48.56f), Inset.pct(0f, 0f, 5.17f, 0f))
            svg("car_blink", Inset.pct(42.02f, 37.45f, 44.54f, 12.69f), modifier = Modifier.shownIf(blinking))
        }
        // plate lettering, 234 x 63 centered on (360, 324.9) of the drawing
        Box(
            Modifier
                .offset(x = (243 * k).dp, y = (293.4f * k).dp)
                .size((234 * k).dp, (63 * k).dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                nickname,
                style = TextStyle(
                    fontFamily = Gaegu, fontWeight = FontWeight.Bold, fontSize = (nicknameSize * k).sp,
                    letterSpacing = (-0.12).em, textAlign = TextAlign.Center,
                ),
                color = Ink,
                maxLines = 1,
            )
        }
    }
}
