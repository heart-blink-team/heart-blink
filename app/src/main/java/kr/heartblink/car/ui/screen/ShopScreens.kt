package kr.heartblink.car.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kr.heartblink.car.ui.component.FigmaSvg
import kr.heartblink.car.ui.component.PrimaryButton
import kr.heartblink.car.ui.component.Tab
import kr.heartblink.car.ui.component.UnderlineTabs
import kr.heartblink.car.ui.component.tap
import kr.heartblink.car.ui.theme.Heart
import kr.heartblink.car.ui.theme.HeartType

/** Figma screens 21 ~ 24: 하트 상점. */
val shopScreens: Map<String, @Composable (Nav) -> Unit> = linkedMapOf(
    "21" to { nav -> ShopThemeScreen(nav) },
    "22" to { nav -> ShopColorScreen(nav) },
    "23" to { nav -> ShopArrivalScreen(nav) },
    "24" to { nav -> ShopDiaryScreen(nav) },
)

/** 21 하트 상점: home heart effect themes, four cards per row. */
@Composable
fun ShopThemeScreen(nav: Nav) {
    val items = listOf(
        ShopItem("깜빡깜빡", null) { ThemeThumb("s21_blink") },
        ShopItem("숨 쉬는 하트", "1,500원") { ThemeThumb("s21_breathe") },
        ShopItem("하트 배달", "1,500원") { ThemeThumb("s21_delivery") },
        ShopItem("하트 꾹", "1,500원") { ThemeThumb("s21_press") },
        ShopItem("하트 완성", "1,500원") { ThemeThumb("s21_complete") },
        ShopItem("빛 산책", "1,500원") { ThemeThumb("s21_walk") },
        ShopItem("하트 꽃가루", "1,900원") { ThemeThumb("s21_pollen") },
        ShopItem("하트 차오름", "1,900원") { LiquidHeartThumb() },
    )
    ShopWindow(nav, ShopTab.Theme, items, columns = 4) { item, selected, onTap -> SmallCard(item, selected, onTap) }
}

/** 22 하트 상점: color themes of the app. */
@Composable
fun ShopColorScreen(nav: Nav) {
    val items = listOf(
        ShopItem("연베이지", null) { WideThumb("s22_beige") },
        ShopItem("벚꽃 분홍", "1,900원") { WideThumb("s22_cherry") },
        ShopItem("민트 우유", "1,900원") { WideThumb("s22_mint") },
        ShopItem("하늘 편지", "1,900원") { WideThumb("s22_sky") },
        ShopItem("레몬 쿠키", "1,900원") { WideThumb("s22_lemon") },
        ShopItem("밤하늘", "1,900원") { WideThumb("s22_night") },
    )
    ShopWindow(nav, ShopTab.Color, items, columns = 3) { item, selected, onTap -> WideCard(item, selected, onTap) }
}

/** 23 하트 상점: how a new message arrives. */
@Composable
fun ShopArrivalScreen(nav: Nav) {
    val items = listOf(
        ShopItem("봉투", null) { WideThumb("s23_envelope") },
        ShopItem("종이비행기", "1,500원") { WideThumb("s23_plane") },
        ShopItem("선물 상자", "1,500원") { WideThumb("s23_gift") },
        ShopItem("풍선", "1,500원") { WideThumb("s23_balloon") },
        ShopItem("우체통", "1,500원") { WideThumb("s23_mailbox") },
        ShopItem("유리병 편지", "1,500원") { WideThumb("s23_bottle") },
    )
    ShopWindow(nav, ShopTab.Arrival, items, columns = 3) { item, selected, onTap -> WideCard(item, selected, onTap) }
}

/** 24 하트 상점: diary page designs. */
@Composable
fun ShopDiaryScreen(nav: Nav) {
    val items = listOf(
        ShopItem("줄노트", null) { WideThumb("s24_lined") },
        ShopItem("모눈 노트", "1,500원") { WideThumb("s24_grid") },
        ShopItem("폴라로이드", "1,500원") { PolaroidThumb() },
        ShopItem("스티커 다이어리", "1,500원") { WideThumb("s24_sticker") },
        ShopItem("편지지", "1,500원") { WideThumb("s24_letter") },
        ShopItem("원고지", "1,500원") { WideThumb("s24_manuscript") },
    )
    ShopWindow(nav, ShopTab.Diary, items, columns = 3) { item, selected, onTap -> WideCard(item, selected, onTap) }
}

/** A shop tab and the screen it opens; the last two have no screen yet. */
private enum class ShopTab(val label: String, val count: Int, val screen: String?) {
    Theme("하트 테마", 8, "21"),
    Color("색상", 6, "22"),
    Arrival("도착 효과", 6, "23"),
    Diary("기록 종이", 6, "24"),
    Questions("질문 테마팩", 3, null),
    Album("기념일 앨범", 2, null),
}

/** One product card. A null [price] marks the free default that is applied now. */
private class ShopItem(val name: String, val price: String?, val thumb: @Composable BoxScope.() -> Unit)

private val PillShape = RoundedCornerShape(999.dp)
private val PreviewShape = RoundedCornerShape(18.dp)

/** Fill behind the picked thumbnail (Figma #FCE6ED), under the 3 px pink border. */
private val PickedFill = Color(0xFFFCE6ED)

// Motion 01 Stagger Fade Up: rows rise one by one, 0.08 s apart, 0.5 s each.
private val StaggerEase = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

@Composable
private fun rememberStagger(count: Int): List<Animatable<Float, AnimationVector1D>> {
    val rows = remember(count) { List(count) { Animatable(0f) } }
    LaunchedEffect(rows) {
        rows.forEachIndexed { i, a ->
            launch {
                delay(80L * i)
                a.animateTo(1f, tween(500, easing = StaggerEase))
            }
        }
    }
    return rows
}

private fun Modifier.rise(a: Animatable<Float, AnimationVector1D>) = graphicsLayer {
    alpha = a.value
    translationY = (1f - a.value) * 32.dp.toPx()
}

/**
 * Places a child of fixed size [w] x [h] at ([x], [y]) inside its Box without taking space,
 * like Figma's absolute children. A null [x] centers it horizontally.
 */
private fun Modifier.pin(w: Dp, h: Dp, y: Dp, x: Dp? = null) = layout { m, c ->
    val p = m.measure(Constraints.fixed(w.roundToPx(), h.roundToPx()))
    val px = x?.roundToPx() ?: ((c.maxWidth - p.width) / 2)
    layout(c.maxWidth, 0) { p.place(px, y.roundToPx()) }
}

/** The shop window: header, then title and tabs, couple subscription and the product grid on the page, toolbar. */
@Composable
private fun ShopWindow(
    nav: Nav,
    active: ShopTab,
    items: List<ShopItem>,
    columns: Int,
    card: @Composable (item: ShopItem, selected: Boolean, onTap: () -> Unit) -> Unit,
) {
    val c = Heart.colors
    val gridRows = items.chunked(columns)
    val rows = rememberStagger(2 + gridRows.size)
    var selected by remember { mutableIntStateOf(0) }
    val tabs = ShopTab.entries
    HeartWindow(Tab.Home, nav) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 84.dp, end = 84.dp, top = 196.dp, bottom = 186.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().rise(rows[0]),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("하트 상점", style = HeartType.HeartTitle, color = c.textPrimary, maxLines = 1)
                    FigmaSvg("shop_doodle_heart", Modifier.size(36.dp))
                }
                UnderlineTabs(
                    tabs.map { it.label },
                    active.ordinal,
                    counts = tabs.map { it.count.toString() },
                    gap = 32.dp,
                ) { i -> tabs[i].screen?.let { if (tabs[i] != active) nav.go(it) } }
            }
            CoupleSubscription(Modifier.rise(rows[1]))
            Column(
                Modifier.weight(1f).fillMaxWidth().clipToBounds(),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                gridRows.forEachIndexed { r, rowItems ->
                    Row(Modifier.rise(rows[2 + r]), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        rowItems.forEachIndexed { i, item ->
                            val index = r * columns + i
                            card(item, index == selected) { selected = index }
                        }
                    }
                }
            }
        }
    }
}

/** 커플 구독 banner. Subscribing is only shown here; there is no payment in the prototype. */
@Composable
private fun CoupleSubscription(modifier: Modifier = Modifier) {
    val c = Heart.colors
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.bgCard)
            .border(2.dp, c.pinkMid, shape)
            .padding(start = 34.dp, end = 30.dp, top = 20.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FigmaSvg("shop_couple_hearts", Modifier.size(64.dp))
        Column(Modifier.weight(1f).clipToBounds(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("커플 구독", style = HeartType.Headline, color = c.textPrimary, maxLines = 1, softWrap = false)
            Text(
                "질문 테마팩, 메시지, 기록 무제한. 둘이 함께 써요",
                style = HeartType.Label, color = c.textSecondary, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
            )
        }
        Text("월 3,900원", style = HeartType.jua(40f), color = c.textPrimary, maxLines = 1)
        PrimaryButton("구독하기", Modifier.size(220.dp, 76.dp), onClick = {})
    }
}

/** The price as plain text, or a bold deep pink "적용 중" on the free default that is applied now. */
@Composable
private fun PriceText(price: String?) {
    val c = Heart.colors
    val applied = price == null
    Text(
        price ?: "적용 중",
        style = HeartType.noto(28f, if (applied) 700 else 500, 1.45f),
        color = if (applied) c.pinkDeep else c.textPrimary,
        maxLines = 1,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

/** The thumbnail box; the picked one turns pink with a 3 px pink border drawn over the picture. */
@Composable
private fun Preview(height: Dp, picked: Boolean, content: @Composable BoxScope.() -> Unit) {
    val c = Heart.colors
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(PreviewShape)
            .background(if (picked) PickedFill else c.pinkLight)
            .then(if (picked) Modifier.border(3.dp, c.pinkAccent, PreviewShape) else Modifier),
        content = content,
    )
}

/** A product on the page: only the thumbnail is filled, the name and price sit on the page under it. */
private fun Modifier.cardFrame(width: Dp, onTap: () -> Unit) = size(width, 279.dp).tap(onTap)

/** 21 card, 365 x 279: preview, name, price stacked in the middle. */
@Composable
private fun SmallCard(item: ShopItem, selected: Boolean, onTap: () -> Unit) {
    val c = Heart.colors
    Column(
        Modifier.cardFrame(365.dp, onTap).padding(bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Preview(130.dp, selected, item.thumb)
        Spacer(Modifier.weight(1f))
        Text(item.name, style = HeartType.Headline, color = c.textPrimary, maxLines = 1)
        PriceText(item.price)
    }
}

/** 22 ~ 24 card, 492 x 279: preview on top, name and price on one row at the bottom. */
@Composable
private fun WideCard(item: ShopItem, selected: Boolean, onTap: () -> Unit) {
    val c = Heart.colors
    Column(
        Modifier.cardFrame(492.dp, onTap).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Preview(150.dp, selected, item.thumb)
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(item.name, style = HeartType.Headline, color = c.textPrimary, maxLines = 1)
            PriceText(item.price)
        }
    }
}

/** 240 x 150 effect thumbnail, centered and lifted 10 px in the 130 px preview. */
@Composable
private fun ThemeThumb(name: String) {
    FigmaSvg(name, Modifier.pin(240.dp, 150.dp, y = (-10).dp))
}

/** 258 x 146.2 thumbnail centered in the 150 px preview. */
@Composable
private fun WideThumb(name: String) {
    FigmaSvg(name, Modifier.pin(258.dp, 146.2.dp, y = 1.9.dp))
}

/** 하트 차오름 preview: a heart outline filled with pink liquid and bubbles, with the "새 테마" tag. */
@Composable
private fun LiquidHeartThumb() {
    val c = Heart.colors
    Box(Modifier.pin(118.dp, 118.dp, y = 6.dp)) {
        FigmaSvg("s21_liquid_back", Modifier.pin(85.4355.dp, 51.75.dp, y = 38.59.dp, x = 16.27.dp))
        FigmaSvg("s21_liquid_front", Modifier.pin(84.3896.dp, 46.8965.dp, y = 43.44.dp, x = 16.98.dp))
        FigmaSvg("s21_bubbles", Modifier.fillMaxSize())
        FigmaSvg("s21_heart_outline", Modifier.fillMaxSize())
    }
    Box(
        Modifier
            .offset(12.dp, 12.dp)
            .clip(PillShape)
            .background(c.pinkAccent)
            .padding(horizontal = 12.dp, vertical = 2.dp),
    ) { Text("새 테마", style = HeartType.Label, color = c.textOnAccent, maxLines = 1) }
}

/** 폴라로이드 preview: two tilted photos (-6 and 8 degrees). */
@Composable
private fun PolaroidThumb() {
    Box(Modifier.pin(258.dp, 146.2.dp, y = 1.9.dp).clipToBounds()) {
        Box(Modifier.pin(107.67.dp, 122.97.dp, y = 14.2.dp, x = 40.76.dp), contentAlignment = Alignment.Center) {
            FigmaSvg("s24_polaroid1", Modifier.size(98.9.dp, 116.1.dp).rotate(-6f))
        }
        Box(Modifier.pin(111.2.dp, 125.82.dp, y = 16.2.dp, x = 112.72.dp), contentAlignment = Alignment.Center) {
            FigmaSvg("s24_polaroid2", Modifier.size(98.9.dp, 116.1.dp).rotate(8f))
        }
    }
}
