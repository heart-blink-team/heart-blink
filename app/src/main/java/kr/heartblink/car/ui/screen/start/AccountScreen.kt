package kr.heartblink.car.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kr.heartblink.car.app.CoupleView
import kr.heartblink.car.ui.component.Tab
import kr.heartblink.car.ui.component.tap
import kr.heartblink.car.ui.theme.Gaegu
import kr.heartblink.car.ui.theme.Heart
import kr.heartblink.car.ui.theme.HeartType

/** Plate nickname written large in Gaegu (52 px, line height 52, tracking -10%). */
private val NicknameHand = TextStyle(fontFamily = Gaegu, fontWeight = FontWeight.Bold, fontSize = 52.sp, lineHeight = 52.sp, letterSpacing = (-0.1).em)

/** Motion 01 Stagger Fade Up: each block rises 0.08 s after the previous one, 0.5 s, cubic-bezier(.16,1,.3,1). */
@Composable
private fun fadeUp(index: Int): Modifier {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(80L * index)
        a.animateTo(1f, tween(500, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)))
    }
    return Modifier.graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * 24.dp.toPx()
    }
}

/** A block of rows on the page itself; [topLine] draws a 2 px hairline on its top edge. */
@Composable
private fun AccountGroup(modifier: Modifier, vertical: Int, topLine: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val line = Heart.colors.line
    Column(
        modifier
            .fillMaxWidth()
            .then(if (topLine) Modifier.drawBehind { drawRect(line, size = Size(size.width, 2.dp.toPx())) } else Modifier)
            .padding(top = (vertical + if (topLine) 2 else 0).dp, bottom = vertical.dp),
        content = content,
    )
}

/** The word on the right end of a settings row: deep pink, or the secondary text color when [quiet]. */
@Composable
private fun RowAction(label: String, quiet: Boolean = false, onClick: (() -> Unit)? = null) {
    val c = Heart.colors
    Text(
        label,
        style = HeartType.BodyStrong,
        color = if (quiet) c.textSecondary else c.pinkDeep,
        maxLines = 1,
        modifier = Modifier.tap(onClick).padding(horizontal = 24.dp, vertical = 12.dp),
    )
}

@Composable
private fun SettingRow(
    vertical: Int,
    action: String,
    quiet: Boolean = false,
    onAction: (() -> Unit)? = null,
    text: @Composable ColumnScope.() -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 36.dp, end = 28.dp, top = vertical.dp, bottom = vertical.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp), content = text)
        RowAction(action, quiet, onAction)
    }
}

@Composable
private fun RowDivider() {
    Box(Modifier.fillMaxWidth().height(2.dp).background(Heart.colors.line))
}

@Composable
private fun OneLine(text: String, style: TextStyle, color: Color) {
    Text(text, style = style, color = color, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
}

/** 20 회원관리: my profile, the partner, couple settings and account actions. Editing works only while parked. */
@Composable
fun AccountScreen(nav: Nav, v: CoupleView = CoupleView.Demo) {
    val me = v.meName
    val myPlate = v.myPlate
    val myNickname = v.myNickname
    val partner = v.partnerName
    val partnerPlate = v.partnerPlate
    val partnerNickname = v.partnerNickname
    val days = v.days
    val c = Heart.colors
    HeartWindow(Tab.Home, nav) {
        Row(
            Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = 160.dp, bottom = 150.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Column(Modifier.width(620.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                AccountGroup(fadeUp(0), vertical = 32) {
                    Text("회원관리", style = HeartType.HeartTitle, color = c.textPrimary)
                    Spacer(Modifier.height(24.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        StartAvatar(me.take(1), 84.dp, Color.Transparent, border = 3.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OneLine(me, HeartType.Headline, c.textPrimary)
                            OneLine(myPlate, HeartType.Label, c.textSecondary)
                        }
                        Text(
                            "내 정보 수정",
                            style = HeartType.Label,
                            color = c.pinkDeep,
                            maxLines = 1,
                            modifier = Modifier.tap { nav.go("01") }.padding(horizontal = 20.dp, vertical = 10.dp),
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OneLine("${partner}님이 지어 준 내 애칭", HeartType.Label, c.textSecondary)
                        OneLine(myNickname, NicknameHand, c.textPrimary)
                    }
                }
                AccountGroup(fadeUp(2), vertical = 32) {
                    Text("연결된 연인", style = HeartType.BodyStrong, color = c.textSecondary)
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        StartAvatar(partner.take(1), 84.dp, Color.Transparent, border = 3.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OneLine(partner, HeartType.Headline, c.textPrimary)
                            OneLine(partnerPlate, HeartType.Label, c.textSecondary)
                        }
                        Text("D+$days", style = HeartType.jua(44f), color = c.textPrimary, maxLines = 1)
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                AccountGroup(fadeUp(1), vertical = 12) {
                    SettingRow(22, "편집", onAction = { nav.go("03") }) {
                        OneLine("처음 만난 날", HeartType.Label, c.textSecondary)
                        OneLine(v.firstMet?.let(::koreanDate) ?: "아직 정하지 않았어요", HeartType.Headline, c.textPrimary)
                    }
                    RowDivider()
                    SettingRow(22, "변경", onAction = { nav.go("03") }) {
                        OneLine("${partner}님 차에 띄울 애칭", HeartType.Label, c.textSecondary)
                        OneLine(partnerNickname, NicknameHand, c.textPrimary)
                    }
                    RowDivider()
                    SettingRow(22, "바꾸기") {
                        OneLine("하트 테마", HeartType.Label, c.textSecondary)
                        OneLine("깜빡깜빡 (기본)", HeartType.Headline, c.textPrimary)
                    }
                }
                AccountGroup(fadeUp(3), vertical = 12, topLine = true) {
                    SettingRow(20, "끊기") {
                        OneLine("연결 끊기", HeartType.BodyStrong, c.textPrimary)
                        OneLine("함께 쌓은 기록은 보관하거나 지울 수 있어요", HeartType.Label, c.textSecondary)
                    }
                    RowDivider()
                    SettingRow(20, "탈퇴하기", quiet = true) {
                        OneLine("회원 탈퇴", HeartType.BodyStrong, c.textPrimary)
                        OneLine("내 정보와 우리가 남긴 기록을 모두 지워요", HeartType.Label, c.textSecondary)
                    }
                }
            }
        }
    }
}
