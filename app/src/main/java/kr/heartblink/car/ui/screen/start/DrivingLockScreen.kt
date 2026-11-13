package kr.heartblink.car.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import kr.heartblink.car.ui.component.FigmaSvg
import kr.heartblink.car.ui.component.HeartCar
import kr.heartblink.car.ui.component.HeartLogo
import kr.heartblink.car.ui.component.HeartToolbar
import kr.heartblink.car.ui.component.Inset
import kr.heartblink.car.ui.component.Layered
import kr.heartblink.car.ui.component.RoundIconButton
import kr.heartblink.car.ui.component.Tab
import kr.heartblink.car.ui.theme.Heart
import kr.heartblink.car.ui.theme.HeartType

/** Opacity of the controls that are blocked while driving. */
private const val Blocked = 0.35f

/**
 * 04-1 주행 중 잠금 화면. While driving every screen is locked: the shop and account buttons and the
 * toolbar are dimmed and take no taps. The driver's own heart lamp blinks; messages are announced by voice.
 */
@Composable
fun DrivingLockScreen(nav: Nav, me: String = "후니", partner: String = "여니", status: String? = null) {
    val c = Heart.colors
    val lit = blinkTwice()
    HeartWindow(Tab.Home, nav, showHeader = false, showToolbar = false) {
        HeartCar(me, Modifier.offset((-270).dp, 380.dp).size(720.dp, 558.dp), blinking = lit > 0.5f, nicknameSize = 64f)
        HeartCar(partner, Modifier.offset(1232.dp, 380.dp).size(720.dp, 558.dp))
        FigmaSvg("heart_signal", Modifier.offset(595.dp, 330.dp).size(492.dp))

        // Lock notice; the status line says what waits for parking
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = 170.dp)
                .padding(horizontal = 40.dp, vertical = 26.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                FigmaSvg("s041_lock", Modifier.size(34.dp))
                Text("주행 중에는 소리로만 알려 드려요", style = HeartType.Headline, color = c.textPrimary, maxLines = 1)
            }
            if (status != null) Text(status, style = HeartType.BodyStrong, color = c.pinkDeep, maxLines = 1)
        }

        Row(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = 860.dp)
                .padding(start = 20.dp, end = 22.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FigmaSvg("s041_dot", Modifier.size(14.dp))
            Text("$me 주행 중", style = HeartType.BodyStrong, color = c.textPrimary, maxLines = 1)
        }

        // Header with the shop and account buttons blocked
        Box(Modifier.padding(start = 40.dp, end = 40.dp, top = 32.dp).fillMaxWidth().height(92.dp)) {
            RoundIconButton({ FigmaSvg("icon_shop", Modifier.fillMaxSize()) }, Modifier.align(Alignment.CenterStart).alpha(Blocked))
            HeartLogo(Modifier.align(Alignment.Center).size(302.4.dp, 92.4.dp))
            RoundIconButton(
                { Layered(Modifier.fillMaxSize()) { svg("icon_account", Inset.pct(17.19f, 17.19f, 14.06f, 17.19f), Inset.pct(5.45f, 5.72f, 5.46f, 5.72f)) } },
                Modifier.align(Alignment.CenterEnd).alpha(Blocked),
            )
        }
        HeartToolbar(Tab.Home, Modifier.align(Alignment.BottomCenter).alpha(Blocked), newQuestion = true)
    }
}
