package kr.heartblink.car.app

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.CruiseControl
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.CruiseControlStateListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import kr.heartblink.car.data.ConditionValue
import ai.pleos.playground.vehicle.listener.HvacAcStateListener
import ai.pleos.playground.vehicle.listener.HvacSeatTemperatureListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
    const val CAR_SPEED = "android.car.permission.CAR_SPEED"
    const val FUSED_LOCATION = "pleos.car.permission.FUSED_LOCATION"
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

    private val _speedKmh = MutableStateFlow<Float?>(null)
    /**
     * Vehicle speed in km/h. The car's own PERF_VEHICLE_SPEED (the same value as the cluster) once the
     * speed permission is allowed; until then, worked out from how far the car moved between positions.
     */
    val speedKmh: StateFlow<Float?> = _speedKmh
    @Volatile private var speedFromCar = false
    private var fused: FusedLocation? = null
    private var lastFix: LocationData? = null
    private val recent = ArrayDeque<Pair<Long, Float>>()

    /** Where the car is, from the Pleos fused location about once a second; null without the permission. */
    val position = MutableStateFlow<Pair<Double, Double>?>(null)

    // what DriveTalk reads to find a safe moment (null until the car has answered)
    @Volatile var steeringDeg: Float? = null; private set
    @Volatile var turnSignalOn: Boolean? = null; private set
    @Volatile var brakePressed: Boolean? = null; private set
    @Volatile var cruiseOn: Boolean? = null; private set

    private val _unsupported = MutableStateFlow<Set<String>>(emptySet())
    /** Car functions this car does not have (ConditionValue names): 06 hides them as conditions. */
    val unsupported: StateFlow<Set<String>> = _unsupported

    private fun notSupported(value: String, why: String?) {
        Log.w(TAG, "$value not supported: $why")
        _unsupported.value = _unsupported.value + value
    }

    private val driverSeat = SeatArea(SeatValue.ROW_1_LEFT)
    private val driverWindow = WindowArea(WindowValue.ROW_1_LEFT)
    private val windshield = WindowArea(WindowValue.FRONT_WINDSHIELD)

    fun start(context: Context) {
        if (vehicle != null) return
        vehicle = runCatching { Vehicle(context.applicationContext).also { it.initialize() } }
            .onFailure { Log.w(TAG, "vehicle SDK unavailable: ${it.message}") }
            .getOrNull()
        main.postDelayed({ register() }, 1500)
        startSpeed(context)
    }

    /** Starts (or upgrades) the speed source; call again once the speed permission was allowed. */
    fun startSpeed(context: Context) {
        val app = context.applicationContext
        if (!speedFromCar && app.checkSelfPermission(CAR_SPEED) == PackageManager.PERMISSION_GRANTED) {
            runCatching {
                val props = Car.createCar(app).getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
                speedFromCar = props.registerCallback(object : CarPropertyManager.CarPropertyEventCallback {
                    override fun onChangeEvent(value: CarPropertyValue<*>) {
                        val mps = value.value as? Float ?: return
                        if (value.status == CarPropertyValue.STATUS_AVAILABLE) main.post { setSpeed(mps * 3.6f) }
                    }
                    override fun onErrorEvent(propertyId: Int, zone: Int) = Unit
                }, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_ONCHANGE)
                Log.i(TAG, "speed from the car: $speedFromCar")
            }.onFailure { Log.w(TAG, "car speed unavailable: ${it.message}") }
        }
        if (fused == null && app.checkSelfPermission(FUSED_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // the car's position about once a second: place messages, arrival without guidance, and the speed
            // fallback (distance between two positions over the time between them)
            fused = runCatching { FusedLocation(app).also { it.initialize() } }.getOrNull()
            main.postDelayed({
                runCatching {
                    fused?.registerFusedLocationCallback(object : FusedLocationListener {
                        override fun onChanged(d: LocationData?) {
                            if (d == null || (d.latitude == 0.0 && d.longitude == 0.0)) return
                            main.post {
                                position.value = d.latitude to d.longitude
                                MessageOpener.onLocation(d.latitude, d.longitude)
                            }
                            val prev = lastFix
                            lastFix = d
                            if (speedFromCar) return
                            val dt = prev?.let { (d.timestampMillis - it.timestampMillis) / 1000.0 } ?: return
                            if (dt <= 0.2) return
                            val kmh = metres(prev.latitude, prev.longitude, d.latitude, d.longitude) / dt * 3.6
                            // a position that jumped (a new fix far away) is not a speed
                            if (kmh <= 250.0) main.post { setSpeed(kmh.toFloat()) }
                        }
                        override fun onError(e: Exception) = Log.w(TAG, "position failed: ${e.message}").let { }
                    })
                    Log.i(TAG, "positions from fused location${if (speedFromCar) "" else ", speed from positions"}")
                }.onFailure { Log.w(TAG, "positions unavailable: ${it.message}") }
            }, 1500)
        }
    }

    /**
     * No sudden speeding up or slowing down: the speed moved less than 12 km/h over the last 3 seconds.
     * True when the speed is unknown, so a car without it still talks.
     */
    fun speedSteady(): Boolean {
        val cut = SystemClock.elapsedRealtime() - 3_000
        val window = recent.filter { it.first >= cut }.map { it.second }
        if (window.size < 2) return true
        return window.max() - window.min() < 12f
    }

    private fun setSpeed(kmh: Float) {
        val now = SystemClock.elapsedRealtime()
        recent.addLast(now to kmh)
        while (recent.isNotEmpty() && recent.first().first < now - 10_000) recent.removeFirst()
        val was = _speedKmh.value
        if (was == null || kotlin.math.abs(was - kmh) >= 5f) Log.d(TAG, "speed ${kmh.toInt()} km/h (${if (speedFromCar) "car" else "positions"})")
        if (was == null || kotlin.math.abs(was - kmh) >= 0.5f) _speedKmh.value = kmh
    }

    internal fun metres(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2).let { it * it } +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2).let { it * it }
        return 2 * r * Math.asin(Math.sqrt(a))
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
        tryRegister("wiper", ConditionValue.WIPER) {
            v.getWiper().registerWiperState(windshield, object : WiperStateListener {
                override fun onWiperStateUpdated(state: WiperState?) = set(_wiper, state == WiperState.ON, "wiper")
                override fun onFailed(e: Exception) = notSupported(ConditionValue.WIPER, e.message)
            })
        }
        tryRegister("headlight", ConditionValue.HEADLIGHT) {
            v.getLight().registerLowBeamLightState(object : LightStateListener {
                override fun onLightStateUpdated(state: LightState?) = set(_headlight, state == LightState.ON, "headlight")
                override fun onFailed(e: Exception) = notSupported(ConditionValue.HEADLIGHT, e.message)
            })
        }
        tryRegister("seat heater", ConditionValue.SEAT_HEATER) {
            v.getSeat().registerHvacSeatTemperature(driverSeat, object : HvacSeatTemperatureListener {
                override fun onHvacSeatTemperatureUpdated(level: Int?) = set(_seatHeater, (level ?: 0).coerceAtLeast(0), "seat heater")
                override fun onFailed(e: Exception) = notSupported(ConditionValue.SEAT_HEATER, e.message)
            })
        }
        tryRegister("aircon", ConditionValue.AIRCON) {
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
            }, { e -> notSupported(ConditionValue.AIRCON, e.message) })
        }
        tryRegister("window", ConditionValue.WINDOW) {
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
            }, { e -> notSupported(ConditionValue.WINDOW, e.message) })
        }
        // when to talk while driving (design 2.2): steering, turn signal, brake, cruise control
        tryRegister("steering") {
            v.getSteeringWheel().registerSteeringWheelAngle(object : SteeringWheelAngleListener {
                override fun onSteeringWheelAngleUpdated(angle: Float?) { steeringDeg = angle }
                override fun onFailed(e: Exception) = Log.w(TAG, "steering failed: ${e.message}").let { }
            })
        }
        tryRegister("turn signal") {
            v.getTurnSignal().registerTurnSignalState(object : TurnSignalStateListener {
                override fun onTurnSignalStateUpdated(state: TurnSignalValue?) { turnSignalOn = state != null && state != TurnSignalValue.OFF }
                override fun onFailed(e: Exception) = Log.w(TAG, "turn signal failed: ${e.message}").let { }
            })
        }
        tryRegister("brake") {
            v.getBrake().registerBrakePedalStatus(object : BrakePedalStatusListener {
                override fun onBrakePedalStatusUpdated(pressed: Boolean?) { brakePressed = pressed }
                override fun onFailed(e: Exception) = Log.w(TAG, "brake failed: ${e.message}").let { }
            })
        }
        tryRegister("cruise") {
            v.getDrivingMode().registerCruiseControlState(object : CruiseControlStateListener {
                override fun onCruiseControlStateUpdated(state: CruiseControl?) {
                    cruiseOn = state == CruiseControl.ENABLED || state == CruiseControl.ACTIVATED
                }
                override fun onFailed(e: Exception) = Log.w(TAG, "cruise failed: ${e.message}").let { }
            })
        }
    }

    /**
     * The driver is busy with a control: steering 30 degrees or more, a turn signal on, or braking while still
     * moving. Null when none (a signal the car does not give counts as calm).
     */
    fun busyControl(): String? = when {
        (steeringDeg?.let { kotlin.math.abs(it) } ?: 0f) >= 30f -> "steering"
        turnSignalOn == true -> "turn signal"
        brakePressed == true && (speedKmh.value ?: 0f) > 3f -> "braking"
        else -> null
    }

    /** Runs one registration; when it throws, the message condition it feeds ([condition]) is hidden on 06. */
    private inline fun tryRegister(name: String, condition: String? = null, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "$name not available: ${e.message}")
            if (condition != null) notSupported(condition, e.message)
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
