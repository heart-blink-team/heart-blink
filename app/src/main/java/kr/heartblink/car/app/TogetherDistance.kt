package kr.heartblink.car.app

import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kr.heartblink.car.data.HeartRepository

/**
 * 함께 달린 거리: while both of us drive at the same time, this car adds what it drives to today's
 * distance on the server. The partner's car does the same, and 기록 shows the two added up since we connected.
 *
 * The distance is the odometer's change. A car that gives no odometer counts speed over time instead.
 * Only kilometres leave the car, never where it is.
 */
object TogetherDistance {
    private const val TAG = "TogetherDistance"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    @Volatile private var together = false
    private var lastOdometer: Float? = null
    private var lastTick = 0L
    private var pendingKm = 0.0

    fun start(context: Context) {
        if (job != null) return
        val r = Session.repo(context)
        if (!r.enabled) return
        job = scope.launch {
            // together: my gear out of P while my partner's car says it is driving
            launch {
                combine(CarSignals.driving, Session.partnerDrivingFlow) { me, partner -> me == true && partner }
                    .distinctUntilChanged()
                    .collect { now ->
                        together = now
                        lastOdometer = if (now) CarSignals.odometerKm.value else null
                        lastTick = SystemClock.elapsedRealtime()
                        Log.i(TAG, if (now) "driving together from $lastOdometer km" else "not together")
                        if (!now) flush(r)
                    }
            }
            launch {
                CarSignals.odometerKm.filterNotNull().collect { km ->
                    if (!together) return@collect
                    val from = lastOdometer
                    lastOdometer = km
                    val d = if (from == null) 0f else km - from
                    // a jump of 50 km or more in one reading is a reset, not driving
                    if (d > 0f && d < 50f) add(r, d.toDouble())
                }
            }
            // no odometer: speed over the last second
            launch {
                while (isActive) {
                    delay(1_000)
                    val now = SystemClock.elapsedRealtime()
                    val seconds = (now - lastTick) / 1000.0
                    lastTick = now
                    if (!together || CarSignals.odometerKm.value != null) continue
                    val kmh = CarSignals.speedKmh.value ?: continue
                    add(r, kmh * seconds / 3600.0)
                }
            }
        }
    }

    /** Sends every half kilometre, and what is left when we stop driving together. */
    private suspend fun add(r: HeartRepository, km: Double) {
        pendingKm += km
        if (pendingKm >= 0.5) flush(r)
    }

    private suspend fun flush(r: HeartRepository) {
        val km = pendingKm
        if (km < 0.05) return
        pendingKm = 0.0
        r.addDistance(km)
            .onSuccess { Log.i(TAG, "added ${"%.1f".format(km)} km") }
            .onFailure {
                Log.w(TAG, "add_distance failed: ${it.message}")
                pendingKm += km
            }
    }
}
