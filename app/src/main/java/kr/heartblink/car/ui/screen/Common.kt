package kr.heartblink.car.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import kr.heartblink.car.ui.component.HeartHeader
import kr.heartblink.car.ui.component.HeartToolbar
import kr.heartblink.car.ui.component.Tab
import kr.heartblink.car.ui.theme.Heart

/** What a screen can ask the app to do. Screens are named by their Figma number ("05", "06-1"). */
class Nav(
    val tab: (Tab) -> Unit = {},
    val go: (String) -> Unit = {},
    val back: () -> Unit = {},
)

/**
 * The common window of a main screen: header on top (40 px side margin, 32 px from the top) and the
 * toolbar on the bottom edge. [content] is laid out over the whole 1682 x 1210 window, so a screen can
 * place things at their Figma coordinates with `Modifier.offset`.
 */
@Composable
fun HeartWindow(
    tab: Tab?,
    nav: Nav,
    newQuestion: Boolean = false,
    showHeader: Boolean = true,
    showToolbar: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Heart.colors.bgBase).clipToBounds()) {
        content()
        if (showHeader) HeartHeader(Modifier.padding(start = 40.dp, end = 40.dp, top = 32.dp), onShop = { nav.go("21") }, onAccount = { nav.go("20") })
        if (showToolbar) HeartToolbar(tab, Modifier.align(Alignment.BottomCenter), newQuestion, nav.tab)
    }
}
