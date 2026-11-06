package kr.heartblink.car.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kr.heartblink.car.ui.component.FigmaSvg
import kr.heartblink.car.ui.component.HeartCar
import kr.heartblink.car.ui.component.HeartField
import kr.heartblink.car.ui.component.PrimaryButton
import kr.heartblink.car.ui.component.SecondaryButton
import kr.heartblink.car.ui.component.Shadow
import kr.heartblink.car.ui.component.Tab
import kr.heartblink.car.ui.component.innerShadow
import kr.heartblink.car.ui.component.outerShadow
import kr.heartblink.car.ui.theme.Heart
import kr.heartblink.car.ui.theme.HeartType
import kotlin.math.roundToInt

/** Figma screens 01, 02, 03, 04-1, 20: 첫 로그인, 연결, 주행 중 잠금, 회원관리. */
val startScreens: Map<String, @Composable (Nav) -> Unit> = linkedMapOf<String, @Composable (Nav) -> Unit>(
    "01" to { nav -> FirstLoginScreen(nav) },
    "02" to { nav -> ConnectRequestScreen(nav) },
    "03" to { nav -> ConnectDoneScreen(nav) },
    "04-1" to { nav -> DrivingLockScreen(nav) },
    "20" to { nav -> AccountScreen(nav) },
)

/** Longest nickname that still fits the plate of [HeartCar]. */
private const val NicknameMax = 5

/** Inner light of the small pink boxes next to a field (same as the field box). */
private val FieldInner = arrayOf(Shadow(-2f, -2f, 6f, Color(0xE6FFFFFF)), Shadow(3f, 4f, 8f, Color(0x0D292124)))

/** Typing area inside a [HeartField] box, styled like the field value. */
@Composable
internal fun StartFieldInput(value: String, onValueChange: (String) -> Unit) {
    val c = Heart.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = HeartType.BodyStrong.copy(color = c.textPrimary),
        cursorBrush = SolidColor(c.pinkAccent),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Round avatar with the first letter of a name in Gaegu. */
@Composable
internal fun StartAvatar(letter: String, size: Dp, fill: Color) {
    Box(Modifier.size(size).clip(CircleShape).background(fill), contentAlignment = Alignment.Center) {
        Text(letter, style = HeartType.gaegu(44f), color = Heart.colors.textPrimary, maxLines = 1)
    }
}

/** The white 1000 x 860 card of screens 02 and 03. */
@Composable
private fun ConnectCard(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = Heart.colors
    val shape = RoundedCornerShape(40.dp)
    Column(
        modifier
            .size(1000.dp, 860.dp)
            .outerShadow(shape, Shadow(0f, 18f, 40f, Color(0x12292124)))
            .clip(shape)
            .background(c.bgCard)
            .innerShadow(shape, Shadow(6f, 6f, 12f, Color(0xD9FFFFFF)), Shadow(-6f, -8f, 16f, Color(0x0D292124)))
            .border(2.dp, c.line, shape)
            .padding(horizontal = 56.dp, vertical = 40.dp),
        content = content,
    )
}

/** 01 첫 로그인 (내 정보 등록): name and car number only; the plate stays empty until the partner names it. */
@Composable
fun FirstLoginScreen(nav: Nav) {
    val c = Heart.colors
    var name by remember { mutableStateOf("지훈") }
    var plate by remember { mutableStateOf("12가 3456") }
    val shape = RoundedCornerShape(40.dp)
    HeartWindow(Tab.Home, nav) {
        HeartCar("", Modifier.offset((-270).dp, 380.dp).size(720.dp, 558.dp))
        Column(
            Modifier
                .offset(640.dp, 192.dp)
                .size(1002.dp, 860.dp)
                .outerShadow(shape, Shadow(0f, 18f, 44f, Color(0x0D292124)))
                .clip(shape)
                .background(c.pinkSoft)
                .innerShadow(shape, Shadow(0f, -6f, 16f, Color(0x08292124)), Shadow(0f, 6f, 14f, Color(0xB3FFFFFF)))
                .padding(start = 64.dp, end = 64.dp, top = 56.dp, bottom = 48.dp),
        ) {
            Text("내 정보 등록", style = HeartType.HeartTitle, color = c.textPrimary)
            Spacer(Modifier.height(10.dp))
            Text("연결한 연인이 내 차 번호판에 애칭을 지어 줘요", style = HeartType.Body, color = c.textSecondary)
            Spacer(Modifier.height(40.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                HeartField("이름", Modifier.fillMaxWidth()) { StartFieldInput(name) { name = it } }
                HeartField("차량 번호", Modifier.fillMaxWidth()) { StartFieldInput(plate) { plate = it } }
            }
            Spacer(Modifier.weight(1f))
            PrimaryButton("다음", Modifier.fillMaxWidth(), onClick = { nav.go("03") })
        }
    }
}

/** 02 연결 요청 받기 (서연 화면): the nickname typed here shows on 지훈's plate right away. */
@Composable
fun ConnectRequestScreen(nav: Nav) {
    val c = Heart.colors
    var nickname by remember { mutableStateOf("후니") }
    HeartWindow(Tab.Home, nav) {
        HeartCar(nickname, Modifier.offset(1232.dp, 380.dp).size(720.dp, 558.dp))
        ConnectCard(Modifier.offset(100.dp, 182.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                FigmaSvg("s02_heart", Modifier.size(44.dp))
                Text("연결 요청이 왔어요", style = HeartType.HeartTitle, color = c.textPrimary)
            }
            Spacer(Modifier.height(10.dp))
            Text("지훈님이 하트깜빡이로 연결하자고 해요", style = HeartType.Body, color = c.textSecondary)
            Spacer(Modifier.height(28.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(c.pinkLight)
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StartAvatar("지", 80.dp, c.bgCard)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("지훈", style = HeartType.Headline, color = c.textPrimary, maxLines = 1)
                    Text("차량 번호 12가 3456", style = HeartType.Label, color = c.textSecondary, maxLines = 1)
                }
                Text("방금 전", style = HeartType.Label, color = c.textTertiary, maxLines = 1)
            }
            Spacer(Modifier.height(28.dp))
            HeartField("지훈님 차 번호판에 띄울 애칭", Modifier.fillMaxWidth()) {
                StartFieldInput(nickname) { if (it.length <= NicknameMax) nickname = it }
            }
            Spacer(Modifier.height(10.dp))
            Text("수락하면 지훈님 화면에 연결됐다고 알려 줘요. 애칭은 나중에 바꿀 수 있어요", style = HeartType.Label, color = c.textTertiary)
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SecondaryButton("거절", Modifier.width(240.dp), onClick = { nav.back() })
                PrimaryButton("수락하기", Modifier.weight(1f), onClick = { nav.go("03") })
            }
        }
    }
}

/** 03 커플 연결 완료 (애칭, 처음 만난 날): D+ counts up from 0 in 0.9 s when the screen opens. */
@Composable
fun ConnectDoneScreen(nav: Nav, days: Int = 413) {
    val c = Heart.colors
    var nickname by remember { mutableStateOf("여니") }
    val count = remember { Animatable(0f) }
    LaunchedEffect(days) { count.animateTo(days.toFloat(), tween(900, easing = LinearOutSlowInEasing)) }
    val small = RoundedCornerShape(20.dp)
    HeartWindow(Tab.Home, nav) {
        HeartCar(nickname, Modifier.offset(1232.dp, 380.dp).size(720.dp, 558.dp))
        ConnectCard(Modifier.offset(100.dp, 182.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                FigmaSvg("s03_check", Modifier.size(40.dp))
                Text("서연님과 연결되었습니다", style = HeartType.HeartTitle, color = c.textPrimary)
            }
            Spacer(Modifier.height(10.dp))
            Text("서연님 차에서 연결 요청을 수락했어요", style = HeartType.Body, color = c.textSecondary)
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Bottom) {
                HeartField("상대 차량 번호", "56다 7890", Modifier.weight(1f))
                Box(
                    Modifier
                        .size(260.dp, 84.dp)
                        .clip(small)
                        .background(c.pinkLight)
                        .innerShadow(small, *FieldInner)
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("요청 수락됨", style = HeartType.BodyStrong, color = c.pinkDeep, maxLines = 1) }
            }
            Spacer(Modifier.height(18.dp))
            HeartField("서연님 차 번호판에 띄울 애칭", Modifier.fillMaxWidth()) {
                StartFieldInput(nickname) { if (it.length <= NicknameMax) nickname = it }
            }
            Spacer(Modifier.height(10.dp))
            Text("내 화면의 서연님 차에 보여요. 서연님도 내 차 애칭을 지어 줘요", style = HeartType.Label, color = c.textTertiary)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Bottom) {
                HeartField("처음 만난 날", "2025년 9월 20일", Modifier.weight(1f))
                Box(
                    Modifier
                        .size(260.dp, 84.dp)
                        .outerShadow(small, Shadow(0f, 10f, 22f, Color(0x24292124)))
                        .clip(small)
                        .background(c.pinkLight)
                        .innerShadow(small, Shadow(5f, 5f, 10f, Color(0x59FFFFFF)), Shadow(-5f, -7f, 14f, Color(0x21292124))),
                    contentAlignment = Alignment.Center,
                ) { Text("D+${count.value.roundToInt()}", style = HeartType.HeartTitle, color = c.pinkDeep, maxLines = 1) }
            }
            Spacer(Modifier.height(14.dp))
            Text("두 사람 화면에 같은 날짜로 보여요", style = HeartType.Label, color = c.textTertiary)
            Spacer(Modifier.weight(1f))
            PrimaryButton("인증하고 시작하기", Modifier.fillMaxWidth(), onClick = { nav.go("04") })
        }
    }
}
