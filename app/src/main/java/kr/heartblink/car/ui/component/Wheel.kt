package kr.heartblink.car.ui.component

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kr.heartblink.car.ui.theme.Heart
import kr.heartblink.car.ui.theme.HeartType
import kotlin.math.abs

/** Rows a wheel shows: the picked one in the middle band, two above and two below. */
private const val WheelRows = 5

/**
 * Heart/Wheel: a drum-roll picker. Scroll or flick it up and down, or tap a row; the row in the middle
 * band is the picked one, and [onPick] gets its index once the wheel stops.
 */
@Composable
fun Wheel(
    items: List<String>,
    picked: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    rowHeight: Dp = 64.dp,
) {
    val c = Heart.colors
    val pick by rememberUpdatedState(onPick)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = picked.coerceIn(0, items.lastIndex))
    val fling = rememberSnapFlingBehavior(lazyListState = state, snapPosition = SnapPosition.Center)
    val scope = rememberCoroutineScope()
    val middle by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - mid) }?.index ?: state.firstVisibleItemIndex
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.filter { !it }.collect { pick(middle) }
    }
    // a value set from outside (another wheel, a reset) moves this wheel too
    LaunchedEffect(picked) {
        if (!state.isScrollInProgress && middle != picked) state.scrollToItem(picked.coerceIn(0, items.lastIndex))
    }
    Box(modifier.height(rowHeight * WheelRows)) {
        Box(Modifier.align(Alignment.Center).fillMaxWidth().height(rowHeight).clip(RoundedCornerShape(18.dp)).background(c.pinkLight))
        LazyColumn(
            Modifier.fillMaxSize(),
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = rowHeight * (WheelRows / 2)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(items.size) { i ->
                val far = abs(i - middle)
                Box(
                    Modifier.fillMaxWidth().height(rowHeight).tap { scope.launch { state.animateScrollToItem(i) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        items[i],
                        style = if (far == 0) HeartType.Headline else HeartType.Body,
                        color = when (far) { 0 -> c.pinkDeep; 1 -> c.textSecondary; else -> c.textTertiary },
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/**
 * Heart/Bottom Sheet: rises from the bottom over a dimmed screen. A tap on the dim area closes it
 * like 취소; taps on the sheet stay in the sheet.
 */
@Composable
fun BottomSheet(title: String, onClose: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = Heart.colors
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val rise by animateFloatAsState(if (shown) 1f else 0f, tween(260, easing = LinearOutSlowInEasing), label = "sheet")
    Box(modifier.fillMaxSize().background(Color(0xFF292124).copy(alpha = 0.32f * rise)).tap(onClose)) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 40.dp)
                .graphicsLayer {
                    translationY = (1f - rise) * 240.dp.toPx()
                    alpha = rise
                }
                .clip(RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp))
                .background(c.bgCard)
                .tap {}
                .padding(start = 48.dp, end = 48.dp, top = 20.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(72.dp, 6.dp).clip(RoundedCornerShape(3.dp)).background(c.line))
            Text(title, style = HeartType.HeartTitle, color = c.textPrimary, maxLines = 1)
            content()
        }
    }
}
