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
import androidx.compose.runtime.collectAsState
import kr.heartblink.car.app.DriveTalk
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.todayIn
import kr.heartblink.car.app.Korea
import kr.heartblink.car.app.Session
import kr.heartblink.car.app.Stage
import kr.heartblink.car.data.HeartError
import kr.heartblink.car.ui.component.FigmaSvg
import kr.heartblink.car.ui.component.HeartCar
import kr.heartblink.car.ui.component.HeartField
import kr.heartblink.car.ui.component.PrimaryButton
import kr.heartblink.car.ui.component.SecondaryButton
import kr.heartblink.car.ui.component.Shadow
import kr.heartblink.car.ui.component.Tab
import kr.heartblink.car.ui.component.innerShadow
import kr.heartblink.car.ui.component.outerShadow
import kr.heartblink.car.ui.component.tap
import kr.heartblink.car.ui.theme.Heart
import kr.heartblink.car.ui.theme.HeartType
import kotlin.math.roundToInt

/** Figma screens 01, 01-1, 02, 03, 04-1, 20: 첫 로그인, 연결, 주행 중 잠금, 회원관리. */
val startScreens: Map<String, @Composable (Nav) -> Unit> = linkedMapOf<String, @Composable (Nav) -> Unit>(
    "01" to { nav -> FirstLoginScreen(nav) },
    "01-1" to { nav -> ConnectSendScreen(nav) },
    "02" to { nav -> ConnectRequestScreen(nav) },
    "03" to { nav -> ConnectDoneScreen(nav) },
    "04-1" to { nav ->
        val status by DriveTalk.status.collectAsState()
        DrivingLockScreen(nav, Session.view.myNickname, Session.view.partnerNickname, status)
    },
    "20" to { nav -> AccountScreen(nav, Session.view) },
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

/** The white 1000 x 860 card of screens 01-1, 02 and 03. */
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

/** A server call started from a button: [busy] while it runs, [error] holds the sentence to show when it fails. */
internal class Action {
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
}

/** An [Action] and the function that runs a call through it, then [onDone] when it succeeded. */
@Composable
internal fun rememberAction(): Pair<Action, (suspend () -> Result<*>, () -> Unit) -> Unit> {
    val scope = rememberCoroutineScope()
    val a = remember { Action() }
    val run: (suspend () -> Result<*>, () -> Unit) -> Unit = { call, onDone ->
        if (!a.busy) {
            a.busy = true
            a.error = null
            scope.launch {
                call().onSuccess { onDone() }.onFailure { a.error = HeartError.from(it).text }
                a.busy = false
            }
        }
    }
    return a to run
}

/** The failure sentence under a form, in the deep pink. */
@Composable
internal fun ErrorLine(text: String?) {
    if (text != null) {
        Spacer(Modifier.height(10.dp))
        Text(text, style = HeartType.Label, color = Heart.colors.pinkDeep)
    }
}

/** 260 x 84 pink box next to a field: a status, or a button when [raised]. */
@Composable
private fun SideBox(text: String, raised: Boolean, onClick: (() -> Unit)? = null) {
    val c = Heart.colors
    val small = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .size(260.dp, 84.dp)
            .then(if (raised) Modifier.outerShadow(small, Shadow(0f, 10f, 22f, Color(0x24292124))) else Modifier)
            .clip(small)
            .background(if (raised) c.pinkAccent else c.pinkLight)
            .then(
                if (raised) Modifier.innerShadow(small, Shadow(5f, 5f, 10f, Color(0x59FFFFFF)), Shadow(-5f, -7f, 14f, Color(0x21292124)))
                else Modifier.innerShadow(small, *FieldInner),
            )
            .tap(onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = HeartType.BodyStrong, color = if (raised) c.textOnAccent else c.pinkDeep, maxLines = 1) }
}

/** 01 첫 로그인 (내 정보 등록): name and car number only; the plate stays empty until the partner names it. */
@Composable
fun FirstLoginScreen(nav: Nav) {
    val c = Heart.colors
    val editing = Session.live && Session.stage == Stage.Ready
    val v = Session.view
    var name by remember { mutableStateOf(if (Session.live) Session.me?.name.orEmpty() else v.meName) }
    var plate by remember { mutableStateOf(v.myPlate) }
    val (action, run) = rememberAction()
    val shape = RoundedCornerShape(40.dp)
    HeartWindow(Tab.Home, nav) {
        HeartCar(if (editing) v.myNickname else "", Modifier.offset((-270).dp, 380.dp).size(720.dp, 558.dp))
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
            ErrorLine(action.error)
            Spacer(Modifier.weight(1f))
            PrimaryButton(if (action.busy) "저장하는 중" else "다음", Modifier.fillMaxWidth(), onClick = {
                if (!Session.live) nav.go("01-1")
                else run({ Session.saveProfile(name, plate) }) { if (editing) nav.go("20") }
            })
        }
    }
}

/**
 * 01-1 연결 요청 보내기: the partner's car number, then the box beside it moves from 요청 보내기 to
 * 수락 기다리는 중 to 요청 수락됨. Once accepted, 다음 opens 03 to name the plate and set the first day.
 */
@Composable
fun ConnectSendScreen(nav: Nav) {
    val c = Heart.colors
    val couple = Session.couple
    val myId = Session.myId
    val partnerName = Session.view.partnerName
    // offline the screen walks through the three states on taps, like the prototype
    var demoStep by remember { mutableStateOf(0) }
    val step = when {
        !Session.live -> demoStep
        couple == null -> 0
        couple.isPending && couple.requester == myId -> 1
        else -> 2
    }
    var wasWaiting by remember { mutableStateOf(false) }
    LaunchedEffect(step) { if (step == 1) wasWaiting = true }
    var plate by remember { mutableStateOf(if (Session.live) Session.view.partnerPlate else "56다 7890") }
    val (action, run) = rememberAction()
    HeartWindow(Tab.Home, nav) {
        HeartCar("", Modifier.offset(1232.dp, 380.dp).size(720.dp, 558.dp))
        ConnectCard(Modifier.offset(100.dp, 182.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                FigmaSvg("s02_heart", Modifier.size(44.dp))
                Text("연인과 연결하기", style = HeartType.HeartTitle, color = c.textPrimary)
            }
            Spacer(Modifier.height(10.dp))
            Text("연인의 차량 번호를 넣으면 그 차로 연결 요청을 보내요", style = HeartType.Body, color = c.textSecondary)
            Spacer(Modifier.height(28.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Bottom) {
                if (step == 0) {
                    HeartField("상대 차량 번호", Modifier.weight(1f)) { StartFieldInput(plate) { plate = it } }
                } else {
                    HeartField("상대 차량 번호", plate.ifEmpty { Session.view.partnerPlate }, Modifier.weight(1f))
                }
                when (step) {
                    0 -> SideBox(if (action.busy) "보내는 중" else "요청 보내기", raised = true) {
                        if (!Session.live) demoStep = 1 else run({ Session.requestConnection(plate) }) {}
                    }
                    1 -> SideBox("수락 기다리는 중", raised = false, onClick = if (Session.live) null else ({ demoStep = 2 }))
                    else -> SideBox("요청 수락됨", raised = false)
                }
            }
            Spacer(Modifier.height(14.dp))
            // "서연님", or just "상대" before the name is known ("상대님" sounds odd)
            val who = if (partnerName.isEmpty()) "상대" else "${partnerName}님"
            Text(
                when (step) {
                    0 -> if (wasWaiting) "요청이 수락되지 않았어요. 차량 번호를 확인하고 다시 보낼 수 있어요"
                    else "상대도 하트깜빡이에 이름과 차량 번호를 등록해 두어야 해요"
                    1 -> "$who 차 화면에 연결 요청이 떴어요. 수락하면 바로 연결돼요"
                    else -> "${who}${subjectParticle(who)} 요청을 수락했어요. 이제 애칭과 처음 만난 날을 정해요"
                },
                style = HeartType.Label, color = c.textTertiary,
            )
            ErrorLine(action.error)
            Spacer(Modifier.weight(1f))
            if (step == 2) PrimaryButton("다음", Modifier.fillMaxWidth(), onClick = { nav.go("03") })
        }
    }
}

/** 02 연결 요청 받기 (서연 화면): the nickname typed here shows on 지훈's plate right away. */
@Composable
fun ConnectRequestScreen(nav: Nav) {
    val c = Heart.colors
    val v = Session.view
    val requester = if (Session.live) v.partnerName else "지훈"
    val requesterPlate = if (Session.live) v.partnerPlate else "12가 3456"
    var nickname by remember { mutableStateOf(if (Session.live) "" else "후니") }
    val (action, run) = rememberAction()
    HeartWindow(Tab.Home, nav) {
        HeartCar(nickname, Modifier.offset(1232.dp, 380.dp).size(720.dp, 558.dp))
        ConnectCard(Modifier.offset(100.dp, 182.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                FigmaSvg("s02_heart", Modifier.size(44.dp))
                Text("연결 요청이 왔어요", style = HeartType.HeartTitle, color = c.textPrimary)
            }
            Spacer(Modifier.height(10.dp))
            Text("${requester}님이 하트깜빡이로 연결하자고 해요", style = HeartType.Body, color = c.textSecondary)
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
                StartAvatar(requester.take(1), 80.dp, c.bgCard)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(requester, style = HeartType.Headline, color = c.textPrimary, maxLines = 1)
                    Text("차량 번호 $requesterPlate", style = HeartType.Label, color = c.textSecondary, maxLines = 1)
                }
                Text(sinceText(Session.couple?.createdAt), style = HeartType.Label, color = c.textTertiary, maxLines = 1)
            }
            Spacer(Modifier.height(28.dp))
            HeartField("${requester}님 차 번호판에 띄울 애칭", Modifier.fillMaxWidth()) {
                StartFieldInput(nickname) { if (it.length <= NicknameMax) nickname = it }
            }
            Spacer(Modifier.height(10.dp))
            Text("수락하면 ${requester}님 화면에 연결됐다고 알려 줘요. 애칭은 나중에 바꿀 수 있어요", style = HeartType.Label, color = c.textTertiary)
            ErrorLine(action.error)
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SecondaryButton("거절", Modifier.width(240.dp), onClick = {
                    if (!Session.live) nav.back() else run({ Session.respond(false, null) }) {}
                })
                PrimaryButton(if (action.busy) "연결하는 중" else "수락하기", Modifier.weight(1f), onClick = {
                    when {
                        !Session.live -> nav.go("03")
                        nickname.isBlank() -> action.error = "${requester}님 차에 띄울 애칭을 지어 주세요"
                        else -> run({ Session.respond(true, nickname) }) {}
                    }
                })
            }
        }
    }
}

/** "방금 전", "5분 전", "3시간 전", "2일 전" for a request time. */
private fun sinceText(t: kotlinx.datetime.Instant?): String {
    if (t == null) return "방금 전"
    val minutes = (Clock.System.now() - t).inWholeMinutes
    return when {
        minutes < 1 -> "방금 전"
        minutes < 60 -> "${minutes}분 전"
        minutes < 60 * 24 -> "${minutes / 60}시간 전"
        else -> "${minutes / (60 * 24)}일 전"
    }
}

/** "2025년 9월 20일". */
internal fun koreanDate(d: LocalDate): String = "${d.year}년 ${d.monthNumber}월 ${d.dayOfMonth}일"

/** Reads "2025년 9월 20일", "2025.09.20", "2025-9-20" or "20250920"; null when it is not a past or present day. */
internal fun parseDay(text: String): LocalDate? {
    val n = Regex("\\d+").findAll(text).map { it.value }.toList()
    val parts = when {
        n.size == 3 -> n
        n.size == 1 && n[0].length == 8 -> listOf(n[0].take(4), n[0].substring(4, 6), n[0].takeLast(2))
        else -> return null
    }
    val day = runCatching { LocalDate(parts[0].toInt(), parts[1].toInt(), parts[2].toInt()) }.getOrNull() ?: return null
    return day.takeIf { it <= Clock.System.todayIn(Korea) }
}

/** 03 커플 연결 완료 (애칭, 처음 만난 날): D+ counts up from 0 in 0.9 s when the screen opens. */
@Composable
fun ConnectDoneScreen(nav: Nav) {
    val c = Heart.colors
    val v = Session.view
    val editing = Session.live && Session.stage == Stage.Ready
    val partner = v.partnerName
    var nickname by remember { mutableStateOf(v.partnerNickname) }
    var dayText by remember { mutableStateOf(v.firstMet?.let(::koreanDate).orEmpty()) }
    val firstMet = parseDay(dayText)
    val days = firstMet?.daysUntil(Clock.System.todayIn(Korea)) ?: 0
    val count = remember { Animatable(0f) }
    LaunchedEffect(days) { count.animateTo(days.toFloat(), tween(900, easing = LinearOutSlowInEasing)) }
    val (action, run) = rememberAction()
    val small = RoundedCornerShape(20.dp)
    HeartWindow(Tab.Home, nav) {
        HeartCar(nickname, Modifier.offset(1232.dp, 380.dp).size(720.dp, 558.dp))
        ConnectCard(Modifier.offset(100.dp, 182.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                FigmaSvg("s03_check", Modifier.size(40.dp))
                Text("${partner}님과 연결되었습니다", style = HeartType.HeartTitle, color = c.textPrimary)
            }
            Spacer(Modifier.height(10.dp))
            Text("${partner}님 차에서 연결 요청을 수락했어요", style = HeartType.Body, color = c.textSecondary)
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Bottom) {
                HeartField("상대 차량 번호", v.partnerPlate, Modifier.weight(1f))
                SideBox("요청 수락됨", raised = false)
            }
            Spacer(Modifier.height(18.dp))
            HeartField("${partner}님 차 번호판에 띄울 애칭", Modifier.fillMaxWidth()) {
                StartFieldInput(nickname) { if (it.length <= NicknameMax) nickname = it }
            }
            Spacer(Modifier.height(10.dp))
            Text("내 화면의 ${partner}님 차에 보여요. ${partner}님도 내 차 애칭을 지어 줘요", style = HeartType.Label, color = c.textTertiary)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Bottom) {
                HeartField("처음 만난 날", Modifier.weight(1f)) { StartFieldInput(dayText) { dayText = it } }
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
            ErrorLine(action.error)
            Spacer(Modifier.weight(1f))
            PrimaryButton(if (action.busy) "저장하는 중" else if (editing) "저장하기" else "인증하고 시작하기", Modifier.fillMaxWidth(), onClick = {
                when {
                    !Session.live -> nav.go("04")
                    nickname.isBlank() -> action.error = "${partner}님 차에 띄울 애칭을 지어 주세요"
                    firstMet == null -> action.error = "처음 만난 날을 2025.09.20처럼 넣어 주세요"
                    else -> run({ Session.updateCouple(nickname, firstMet) }) { if (editing) nav.go("20") }
                }
            })
        }
    }
}
