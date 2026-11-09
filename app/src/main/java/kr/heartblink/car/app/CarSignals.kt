package kr.heartblink.car.app

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.HvacAcStateListener
import ai.pleos.playground.vehicle.listener.HvacSeatTemperatureListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the app reads from the car through the Pleos Vehicle SDK. Read only.
 *
 * Driving means the gear is out of P (D, R or N). On the emulator every signal comes from the Virtual
 * Console; the SDK answers only after its service has bound, so listeners are registered a moment after
 * start. Each value stays null until the car has answered (or when the car does not have it).
 */
object CarSignals {
    private const val TAG = "CarSignals"
    private val main = Handler(Looper.getMainLooper())
    private var vehicle: Vehicle? = null

    private val _driving = MutableStateFlow<Boolean?>(null)
    /** True while the gear is out of P. */
    val driving: StateFlow<Boolean?> = _driving

    private val _wiper = MutableStateFlow<Boolean?>(null)
    /** Front wipers moving. */
    val wiper: StateFlow<Boolean?> = _wiper

    private val _headlight = MutableStateFlow<Boolean?>(null)
    /** Low beam on (daytime running lights do not count). */
    val headlight: StateFlow<Boolean?> = _headlight

    private val _seatHeater = MutableStateFlow<Int?>(null)
    /** Driver seat heater step, 0 when off. */
    val seatHeater: StateFlow<Int?> = _seatHeater

    private val _aircon = MutableStateFlow<Boolean?>(null)
    /** Air conditioning on for the driver. */
    val aircon: StateFlow<Boolean?> = _aircon

    private val _windowOpen = MutableStateFlow<Float?>(null)
    /** How far the driver window is open, 0 closed to 1 fully open. */
    val windowOpen: StateFlow<Float?> = _windowOpen

    private val driverSeat = SeatArea(SeatValue.ROW_1_LEFT)
    private val driverWindow = WindowArea(WindowValue.ROW_1_LEFT)
    private val windshield = WindowArea(WindowValue.FRONT_WINDSHIELD)

    fun start(context: Context) {
        if (vehicle != null) return
        vehicle = runCatching { Vehicle(context.applicationContext).also { it.initialize() } }
            .onFailure { Log.w(TAG, "vehicle SDK unavailable: ${it.message}") }
            .getOrNull()
        main.postDelayed({ register() }, 1500)
    }

    fun stop() {
        main.removeCallbacksAndMessages(null)
        runCatching { vehicle?.release() }
        vehicle = null
    }

    private fun register() {
        val v = vehicle ?: return
        tryRegister("gear") {
            @Suppress("DEPRECATION")
            val gear = v.getGear()
            gear.registerGearSelection(object : GearSelectionListener {
                override fun onSelected(gear: VehicleGear?) = onGear(gear)
                override fun onFailed(e: Exception) = Log.w(TAG, "gear listener failed: ${e.message}").let { }
            })
            gear.getCurrentGearSelectionState({ onGear(it) }, { e -> Log.w(TAG, "gear read failed: ${e.message}") })
        }
        tryRegister("wiper") {
            v.getWiper().registerWiperState(windshield, object : WiperStateListener {
                override fun onWiperStateUpdated(state: WiperState?) = set(_wiper, state == WiperState.ON, "wiper")
                override fun onFailed(e: Exception) = Log.w(TAG, "wiper listener failed: ${e.message}").let { }
            })
        }
        tryRegister("headlight") {
            v.getLight().registerLowBeamLightState(object : LightStateListener {
                override fun onLightStateUpdated(state: LightState?) = set(_headlight, state == LightState.ON, "headlight")
                override fun onFailed(e: Exception) = Log.w(TAG, "headlight listener failed: ${e.message}").let { }
            })
        }
        tryRegister("seat heater") {
            v.getSeat().registerHvacSeatTemperature(driverSeat, object : HvacSeatTemperatureListener {
                override fun onHvacSeatTemperatureUpdated(level: Int?) = set(_seatHeater, (level ?: 0).coerceAtLeast(0), "seat heater")
                override fun onFailed(e: Exception) = Log.w(TAG, "seat heater listener failed: ${e.message}").let { }
            })
        }
        tryRegister("aircon") {
            val hvac = v.getHVAC()
            // the air conditioning answers only for the area its capability check names (one row, both seats)
            hvac.checkHvacAcCapabilities({ areas ->
                val area = areas.firstOrNull { driverSeat.values.first() in it.values } ?: areas.firstOrNull() ?: driverSeat
                Log.i(TAG, "aircon areas ${areas.map { it.values.toList() }}")
                set(_aircon, false, "aircon")
                hvac.registerHvacAcState(area, object : HvacAcStateListener {
                    override fun onHvacAcStateUpdated(on: Boolean?) = set(_aircon, on == true, "aircon")
                    override fun onFailed(e: Exception) = Log.w(TAG, "aircon listener failed: ${e.message}").let { }
                })
            }, { e -> Log.w(TAG, "aircon areas failed: ${e.message}") })
        }
        tryRegister("window") {
            val window = v.getWindow()
            window.checkWindowPositionCapabilities({ bounds ->
                val b = bounds[driverWindow] ?: bounds.values.firstOrNull()
                val closed = b?.minValue ?: 0
                val open = b?.maxValue ?: 100
                window.registerWindowPosition(driverWindow, object : WindowPositionListener {
                    override fun onWindowPositionUpdated(position: Int?) {
                        val span = (open - closed).takeIf { it != 0 } ?: return
                        set(_windowOpen, (((position ?: closed) - closed).toFloat() / span).coerceIn(0f, 1f), "window")
                    }
                    override fun onFailed(e: Exception) = Log.w(TAG, "window listener failed: ${e.message}").let { }
                })
            }, { e -> Log.w(TAG, "window bounds failed: ${e.message}") })
        }
    }

    private inline fun tryRegister(name: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "$name not available: ${e.message}")
        }
    }

    private fun <T> set(flow: MutableStateFlow<T?>, value: T, name: String) {
        if (flow.value != value) Log.i(TAG, "$name $value")
        flow.value = value
    }

    private fun onGear(gear: VehicleGear?) {
        val driving = when (gear) {
            null, VehicleGear.GEAR_UNKNOWN -> return
            VehicleGear.GEAR_PARK -> false
            else -> true
        }
        Log.i(TAG, "gear $gear, driving $driving")
        _driving.value = driving
    }
}
