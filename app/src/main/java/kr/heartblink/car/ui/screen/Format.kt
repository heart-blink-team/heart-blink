package kr.heartblink.car.ui.screen

import kotlinx.datetime.Instant
import kotlinx.datetime.toLocalDateTime
import kr.heartblink.car.app.Korea

private val Weekdays = listOf("월", "화", "수", "목", "금", "토", "일")

/** "0:06" for a recording length in ms. */
internal fun durationText(ms: Int): String {
    val s = (ms + 500) / 1000
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

/** "오후 7:40" in Korean time. */
private fun clock(t: Instant): String {
    val d = t.toLocalDateTime(Korea)
    val half = if (d.hour < 12) "오전" else "오후"
    val h = (d.hour % 12).let { if (it == 0) 12 else it }
    return "$half $h:${d.minute.toString().padStart(2, '0')}"
}

/** "11월 5일 오후 7:40". */
internal fun koreanMoment(t: Instant): String {
    val d = t.toLocalDateTime(Korea)
    return "${d.monthNumber}월 ${d.dayOfMonth}일 ${clock(t)}"
}

/** "11월 7일 (토) 오전 7:00". */
internal fun koreanMomentWithDay(t: Instant): String {
    val d = t.toLocalDateTime(Korea)
    return "${d.monthNumber}월 ${d.dayOfMonth}일 (${Weekdays[d.dayOfWeek.ordinal]}) ${clock(t)}"
}

/** "11월 6일". */
internal fun koreanDay(t: Instant): String {
    val d = t.toLocalDateTime(Korea)
    return "${d.monthNumber}월 ${d.dayOfMonth}일"
}
