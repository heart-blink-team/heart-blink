package kr.heartblink.car.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kr.heartblink.car.app.Korea
import kr.heartblink.car.app.TimeBand
import kr.heartblink.car.ui.component.BottomSheet
import kr.heartblink.car.ui.component.PrimaryButton
import kr.heartblink.car.ui.component.SecondaryButton
import kr.heartblink.car.ui.component.Wheel
import kr.heartblink.car.ui.theme.Heart
import kr.heartblink.car.ui.theme.HeartType

/** The wheel sheets of 06 and 07: from when a message may open, and the range of 직접 정하기. */
internal enum class WhenSheet { OpensFrom, TimeRange }

private val Halves = listOf("오전", "오후")
private val Hours = (0 until 12).map { if (it == 0) "12시" else "${it}시" }
private val Minutes = (0 until 60 step 10).map { "${it.toString().padStart(2, '0')}분" }

/** A message can be set to start today or on one of the next 364 days. */
private const val DayCount = 365

private fun today(): LocalDate = Clock.System.now().toLocalDateTime(Korea).date

/** "오늘", "내일", "12월 24일 (목)". */
private fun dayLabel(d: LocalDate, today: LocalDate): String = when (today.daysUntil(d)) {
    0 -> "오늘"
    1 -> "내일"
    else -> "${d.monthNumber}월 ${d.dayOfMonth}일 (${koreanWeekday(d)})"
}

/** Whichever sheet is open over 06 or 07; the back key closes it like 취소. */
@Composable
internal fun WhenSheetHost() {
    val sheet = MessageDraft.sheet ?: return
    val close = { MessageDraft.sheet = null }
    BackHandler(onBack = close)
    when (sheet) {
        WhenSheet.OpensFrom -> OpensFromSheet(close)
        WhenSheet.TimeRange -> TimeRangeSheet(close)
    }
}

@Composable
private fun DayWheel(today: LocalDate, picked: Int, onPick: (Int) -> Unit) {
    val labels = remember(today) { List(DayCount) { dayLabel(today.plus(it, DateTimeUnit.DAY), today) } }
    Wheel(labels, picked, onPick, Modifier.width(250.dp))
}

/** 오전/오후, 시 and 분 in 10-minute steps for one clock time. */
@Composable
private fun ClockWheels(time: LocalTime, onChange: (LocalTime) -> Unit) {
    val half = time.hour / 12
    val hour = time.hour % 12
    val tens = time.minute / 10
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Wheel(Halves, half, { onChange(LocalTime(it * 12 + hour, tens * 10)) }, Modifier.width(110.dp))
        Wheel(Hours, hour, { onChange(LocalTime(half * 12 + it, tens * 10)) }, Modifier.width(110.dp))
        Wheel(Minutes, tens, { onChange(LocalTime(half * 12 + hour, it * 10)) }, Modifier.width(120.dp))
    }
}

@Composable
private fun Titled(title: String, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = HeartType.Label, color = Heart.colors.textSecondary, maxLines = 1)
        content()
    }
}

/** 열리기 시작하는 시각 (06, 07): a day and a time. A moment already past means from now. */
@Composable
private fun OpensFromSheet(close: () -> Unit) {
    val c = Heart.colors
    val today = remember { today() }
    val start = MessageDraft.opensFrom?.toLocalDateTime(Korea)
    var day by remember { mutableIntStateOf(start?.let { today.daysUntil(it.date).coerceIn(0, DayCount - 1) } ?: 0) }
    var time by remember { mutableStateOf(start?.let { LocalTime(it.hour, it.minute / 10 * 10) } ?: LocalTime(0, 0)) }
    val picked = today.plus(day, DateTimeUnit.DAY).atTime(time).toInstant(Korea)
    val fromNow = picked <= Clock.System.now()
    BottomSheet("언제부터 활성화할까요?", close) {
        Row(
            Modifier.align(Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(48.dp),
        ) {
            Titled("날짜") { DayWheel(today, day) { day = it } }
            Titled("시각") { ClockWheels(time) { time = it } }
        }
        Text(
            if (fromNow) "지금부터 활성화돼요" else "${koreanStart(picked)} 활성화돼요",
            style = HeartType.BodyStrong, color = c.pinkDeep, maxLines = 1,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SecondaryButton("지금부터", Modifier.width(240.dp)) {
                MessageDraft.opensFrom = null
                close()
            }
            Spacer(Modifier.weight(1f))
            SecondaryButton("취소", Modifier.width(200.dp), close)
            PrimaryButton("확인", Modifier.width(260.dp)) {
                MessageDraft.opensFrom = picked.takeUnless { fromNow }
                close()
            }
        }
    }
}

/** 직접 정하기 (07): from which day, and the start and the end of the range. */
@Composable
private fun TimeRangeSheet(close: () -> Unit) {
    val c = Heart.colors
    val today = remember { today() }
    val startDay = MessageDraft.opensFrom?.toLocalDateTime(Korea)?.date
    var day by remember { mutableIntStateOf(startDay?.let { today.daysUntil(it).coerceIn(0, DayCount - 1) } ?: 0) }
    var start by remember { mutableStateOf(MessageDraft.customStart) }
    var end by remember { mutableStateOf(MessageDraft.customEnd) }
    val same = start == end
    val from = "${dayLabel(today.plus(day, DateTimeUnit.DAY), today)}부터"
    BottomSheet("시간대 정하기", close) {
        Row(
            Modifier.align(Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(36.dp),
        ) {
            Titled("언제부터") { DayWheel(today, day) { day = it } }
            Titled("시작") { ClockWheels(start) { start = it } }
            Text("~", style = HeartType.HeartTitle, color = c.textTertiary, modifier = Modifier.padding(bottom = 134.dp))
            Titled("끝") { ClockWheels(end) { end = it } }
        }
        Text(
            if (same) "시작과 끝이 같아요. 끝을 바꿔 주세요" else "$from ${TimeBand(start, end).spoken} 사이에 열려요",
            style = HeartType.BodyStrong, color = c.pinkDeep, maxLines = 1,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.weight(1f))
            SecondaryButton("취소", Modifier.width(200.dp), close)
            PrimaryButton(
                "확인", Modifier.width(260.dp), enabled = !same,
                onClick = if (same) null else ({
                    MessageDraft.customStart = start
                    MessageDraft.customEnd = end
                    MessageDraft.opensFrom = if (day == 0) null else today.plus(day, DateTimeUnit.DAY).atStartOfDayIn(Korea)
                    close()
                }),
            )
        }
    }
}
