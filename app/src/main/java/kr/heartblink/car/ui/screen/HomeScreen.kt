package kr.heartblink.car.ui.screen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import kr.heartblink.car.app.CoupleView
import kr.heartblink.car.app.Session
import kr.heartblink.car.ui.component.FigmaSvg
import kr.heartblink.car.ui.component.HeartCar
import kr.heartblink.car.ui.component.Tab
import kr.heartblink.car.ui.theme.Heart
import kr.heartblink.car.ui.theme.HeartType

/**
 * 04 홈 (기본 테마 깜빡깜빡). While the partner drives, the heart tail lamp of their car and the big heart
 * in the middle blink twice together, then rest.
 */
@Composable
fun HomeScreen(nav: Nav, v: CoupleView = CoupleView.Demo) {
    val lit = if (v.partnerDriving) blinkTwice() else 0f
    val me = v.meName
    val partner = v.partnerName
    // the dot on 오늘의 질문 shows while the current question waits for my answer
    val newQuestion = if (Session.live) Session.question?.let { it.mine == null } ?: false else true
    HeartWindow(Tab.Home, nav, newQuestion = newQuestion) {
        HeartCar(v.myNickname, Modifier.offset((-270).dp, 380.dp).size(720.dp, 558.dp))
        HeartCar(v.partnerNickname, Modifier.offset(1232.dp, 380.dp).size(720.dp, 558.dp), blinking = lit > 0.5f)
        FigmaSvg("heart_glow", Modifier.offset(561.dp, 292.dp).size(560.dp).alpha(lit))
        FigmaSvg("heart_signal", Modifier.offset(595.dp, 330.dp).size(492.dp))
        Column(
            Modifier.fillMaxWidth().offset(y = 168.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text("$me${andParticle(me)} $partner${subjectParticle(partner)} 함께한 지", style = HeartType.Label, color = Heart.colors.textSecondary)
            Text("D+${v.days}", style = HeartType.HeartDisplay, color = Heart.colors.textPrimary)
        }
    }
}

private fun hasFinal(word: String): Boolean {
    val last = word.trimEnd().lastOrNull() ?: return false
    return last in '가'..'힣' && (last - '가') % 28 != 0
}

/** 과 after a final consonant, 와 otherwise (지훈과, 민지와). */
internal fun andParticle(word: String) = if (hasFinal(word)) "과" else "와"

/** 이 after a final consonant, 가 otherwise (서연이, 민지가). */
internal fun subjectParticle(word: String) = if (hasFinal(word)) "이" else "가"

/** 1 while lit: on, off, on, then a pause, once every 1.6 s. */
@Composable
fun blinkTwice(): Float {
    val t = rememberInfiniteTransition(label = "blink")
    val v by t.animateFloat(
        0f, 0f,
        infiniteRepeatable(
            keyframes {
                durationMillis = 1600
                0f at 0 using LinearEasing
                1f at 1 using LinearEasing
                1f at 220
                0f at 221
                0f at 380
                1f at 381
                1f at 600
                0f at 601
                0f at 1600
            },
            RepeatMode.Restart,
        ),
        label = "lit",
    )
    return v
}
