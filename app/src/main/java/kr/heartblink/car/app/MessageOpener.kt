package kr.heartblink.car.app

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kr.heartblink.car.data.ConditionGroup
import kr.heartblink.car.data.ConditionValue
import kr.heartblink.car.data.HeartRepository
import kr.heartblink.car.data.Message
import kr.heartblink.car.data.PendingMessage

/**
 * Opens my partner's messages in this car when their condition happens (the receiving car decides).
 *
 * The rules are in 하트깜빡이_주행중_흐름_설계.md, chapter 3: one event (a car function switched on,
 * the first start of the day, parking, or the time band being reached while the car is in use) opens
 * every waiting message for that condition, once its earliest opening time has passed. An opened message
 * waits in [waiting] until it has been listened to.
 */
object MessageOpener {
    private const val TAG = "MessageOpener"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var repo: HeartRepository? = null

    private var pending: List<PendingMessage> = emptyList()
    private val opening = mutableSetOf<String>()
    private var startedAt = 0L
    private var drivingSince: Long? = null

    private val _waiting = MutableStateFlow<List<Message>>(emptyList())
    /** Messages that opened in this car and have not been listened to, the earliest first. */
    val waiting: StateFlow<List<Message>> = _waiting

    fun start(context: Context) {
        if (job != null) return
        val r = Session.repo(context)
        if (!r.enabled) return
        repo = r
        startedAt = SystemClock.elapsedRealtime()
        val prefs = context.applicationContext.getSharedPreferences("opener", Context.MODE_PRIVATE)
        job = scope.launch {
            launch { r.watchPendingMessages().collect { pending = it; checkTime() } }
            launch { refreshWaiting() }
            watchCar(prefs)
            // the time band is a state: look again every minute while the car is in use
            launch {
                while (isActive) {
                    delay(60_000)
                    checkTime()
                }
            }
        }
    }

    /** Reloads the opened but unheard messages, for example after one was listened to. */
    suspend fun refreshWaiting() {
        val r = repo ?: return
        // a failed read keeps the list as it was
        val all = r.receivedMessagesOrNull() ?: return
        _waiting.value = all.filter { it.openedAt != null && it.heardAt == null }.sortedBy { it.openedAt }
        Log.i(TAG, "waiting ${_waiting.value.size}")
    }

    // ------------------------------------------------------------ car events

    private fun CoroutineScope.watchCar(prefs: SharedPreferences) {
        launch {
            var before: Boolean? = null
            CarSignals.driving.filterNotNull().collect { driving ->
                val was = before
                before = driving
                val now = SystemClock.elapsedRealtime()
                if (driving && was != true) {
                    drivingSince = now
                    // the first start of the day; the day turns at 4 a.m.
                    val day = carDay()
                    if (was == false && prefs.getString("first_start_day", null) != day) {
                        prefs.edit().putString("first_start_day", day).apply()
                        open("첫 시동") { it.group == ConditionGroup.FirstStart }
                    }
                } else if (!driving && was == true) {
                    val since = drivingSince
                    drivingSince = null
                    if (since != null && now - since >= 60_000) {
                        open("주차") { it.group == ConditionGroup.Arrival && it.conditionValue == ConditionValue.PARKED && parkedAt(it) }
                    }
                }
                checkTime()
            }
        }
        launch { held(CarSignals.wiper, { 20_000L }, "와이퍼") { vehicle(it, ConditionValue.WIPER) } }
        // in daylight the lights also come on in a tunnel: keep them on for 2 minutes first
        launch { held(CarSignals.headlight, { if (hour() in 6..17) 120_000L else 0L }, "전조등") { vehicle(it, ConditionValue.HEADLIGHT) } }
        // switched on by the car itself right after the start: still on a minute later?
        launch {
            held(CarSignals.aircon, { (60_000L - (SystemClock.elapsedRealtime() - startedAt)).coerceAtLeast(0L) }, "에어컨") {
                vehicle(it, ConditionValue.AIRCON)
            }
        }
        launch {
            var before: Int? = null
            CarSignals.seatHeater.filterNotNull().collect { level ->
                val was = before
                before = level
                if (was == 0 && level >= 1) open("열선 시트") { vehicle(it, ConditionValue.SEAT_HEATER) }
            }
        }
        launch {
            var wait: Job? = null
            var before: Float? = null
            CarSignals.windowOpen.filterNotNull().collect { open ->
                val was = before
                before = open
                if (was != null && was < 0.5f && open >= 0.5f) {
                    wait?.cancel()
                    // a toll gate or a parking ticket: closed again within 10 seconds
                    wait = launch {
                        delay(10_000)
                        if ((CarSignals.windowOpen.value ?: 0f) >= 0.5f) open("창문") { vehicle(it, ConditionValue.WINDOW) }
                    }
                }
            }
        }
    }

    /** Opens when [flow] turns from false to true and is still true after [hold] milliseconds. */
    private suspend fun held(flow: StateFlow<Boolean?>, hold: () -> Long, name: String, match: (Message) -> Boolean) = coroutineScope {
        var wait: Job? = null
        var before: Boolean? = null
        flow.filterNotNull().collect { on ->
            val was = before
            before = on
            wait?.cancel()
            if (on && was == false) {
                wait = launch {
                    delay(hold())
                    if (flow.value == true) open(name, match)
                }
            }
        }
    }

    private fun vehicle(m: Message, value: String) = m.group == ConditionGroup.Vehicle && m.conditionValue == value

    // ------------------------------------------------------------ navigation

    /**
     * The navigation says the car arrived at its destination. A 주차 message whose place is right here opens
     * too: with route guidance the arrival comes before the gear goes to P.
     */
    fun onDestination() = open("목적지 도착") {
        it.group == ConditionGroup.Arrival &&
            (it.conditionValue == ConditionValue.DESTINATION || (it.conditionValue == ConditionValue.PARKED && it.place != null && parkedAt(it)))
    }

    /**
     * Whether the car stopped at the place the sender picked on the map for a 주차 message (the partner's
     * home or office, say): within its circle, judged here in the car. A message from before places were
     * picked has none and opens wherever the car parks.
     */
    private fun parkedAt(m: Message): Boolean {
        val p = m.place ?: return true
        val (lat, lng) = CarSignals.position.value ?: NaviWatch.here.value?.let { it.lat to it.lng } ?: return false
        val d = distanceM(lat, lng, p.lat, p.lng)
        Log.i(TAG, "parked ${d.toInt()} m from ${p.name}")
        return d <= p.radiusM
    }

    fun hasPlaceMessages(): Boolean = pending.any { it.message.group == ConditionGroup.Place }

    /** Where the car is now; a place message opens the first time the car comes inside its circle. */
    fun onLocation(lat: Double, lng: Double) = open("장소") { m ->
        val p = m.place ?: return@open false
        m.group == ConditionGroup.Place && distanceM(lat, lng, p.lat, p.lng) <= p.radiusM
    }

    private fun distanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2).let { it * it } +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2).let { it * it }
        return 2 * r * Math.asin(Math.sqrt(a))
    }

    // ------------------------------------------------------------ time bands

    /** A time message opens once the car is in use inside its band (this service only runs then). */
    private fun checkTime() {
        if (CarSignals.driving.value == null) return
        val now = nowLocal().time
        open("시간대") { it.group == ConditionGroup.Time && TimeBand.of(it.conditionValue)?.contains(now) == true }
    }

    // ------------------------------------------------------------ opening

    private fun open(reason: String, match: (Message) -> Boolean) {
        val r = repo ?: return
        val now = Clock.System.now()
        val ready = pending.map { it.message }.filter { it.id !in opening && it.canOpenAt(now) && match(it) }
        if (ready.isEmpty()) return
        opening += ready.map { it.id }
        Log.i(TAG, "$reason: opening ${ready.size} message(s)")
        scope.launch {
            ready.forEach { m ->
                r.markMessage(m.id, opened = true, heard = false)
                    .onFailure { Log.w(TAG, "open failed: ${it.message}"); opening -= m.id }
            }
            refreshWaiting()
        }
    }

    private fun nowLocal() = Clock.System.now().toLocalDateTime(Korea)
    private fun hour(): Int = nowLocal().hour

    /** Today's date for "the first start of the day", which turns at 4 a.m. */
    private fun carDay(): String {
        val t = nowLocal()
        return (if (t.hour < 4) t.date.minus(DatePeriod(days = 1)) else t.date).toString()
    }
}

/**
 * The time bands of 07 and a custom range ("custom:1800-1930"). The end is not included; a band that
 * passes midnight (밤, 20:00 to 4:00) wraps around.
 */
data class TimeBand(val start: LocalTime, val end: LocalTime) {
    fun contains(t: LocalTime): Boolean = if (start <= end) t >= start && t < end else t >= start || t < end

    /** "18:00~19:30" */
    val label: String get() = "${fmt(start)}~${fmt(end)}"

    /**
     * "오후 6시~7시 30분", as people say it; the end drops 오전/오후 when it is the same as the start's.
     * A range that passes midnight and ends in the same half of the day says so: "오전 11시~다음 날 오전 10시".
     */
    val spoken: String get() {
        val (p1, t1) = korean(start)
        val (p2, t2) = korean(end)
        return when {
            p1 != p2 -> "$p1 $t1~$p2 $t2"
            start > end -> "$p1 $t1~다음 날 $p2 $t2"
            else -> "$p1 $t1~$t2"
        }
    }

    companion object {
        private fun fmt(t: LocalTime) = "%d:%02d".format(t.hour, t.minute)

        /** 13:30 -> ("오후", "1시 30분"); midnight is 밤 12시 and noon 낮 12시. */
        fun korean(t: LocalTime): Pair<String, String> {
            val part = when (t.hour) { 0 -> "밤"; in 1..11 -> "오전"; 12 -> "낮"; else -> "오후" }
            val h = when (t.hour) { 0 -> 12; in 13..23 -> t.hour - 12; else -> t.hour }
            return part to (if (t.minute == 0) "${h}시" else "${h}시 ${t.minute}분")
        }

        private val bands = mapOf(
            ConditionValue.DAWN to TimeBand(LocalTime(4, 0), LocalTime(7, 0)),
            ConditionValue.MORNING to TimeBand(LocalTime(7, 0), LocalTime(11, 0)),
            ConditionValue.NOON to TimeBand(LocalTime(11, 0), LocalTime(14, 0)),
            ConditionValue.AFTERNOON to TimeBand(LocalTime(14, 0), LocalTime(17, 0)),
            ConditionValue.EVENING to TimeBand(LocalTime(17, 0), LocalTime(20, 0)),
            ConditionValue.NIGHT to TimeBand(LocalTime(20, 0), LocalTime(4, 0)),
        )

        const val CUSTOM = "custom:"

        fun of(value: String): TimeBand? = bands[value] ?: custom(value)

        /** "custom:1800-1930" for a range picked on 07. */
        fun customValue(start: LocalTime, end: LocalTime) =
            "%s%02d%02d-%02d%02d".format(CUSTOM, start.hour, start.minute, end.hour, end.minute)

        private fun custom(value: String): TimeBand? {
            if (!value.startsWith(CUSTOM)) return null
            val m = Regex("(\\d{2})(\\d{2})-(\\d{2})(\\d{2})").matchEntire(value.removePrefix(CUSTOM)) ?: return null
            val (h1, m1, h2, m2) = m.destructured
            return runCatching { TimeBand(LocalTime(h1.toInt(), m1.toInt()), LocalTime(h2.toInt(), m2.toInt())) }.getOrNull()
        }
    }
}
