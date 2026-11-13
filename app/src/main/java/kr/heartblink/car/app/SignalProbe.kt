package kr.heartblink.car.app

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.CruiseControl
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.CruiseControlStateListener
import ai.pleos.playground.vehicle.listener.CruiseControlTargetSpeedListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlin.concurrent.thread
import kotlin.math.abs

/**
 * Debug builds only (`--ez probe true`): logs every car signal the plan relies on, under the tag
 * SignalProbe, so each one can be checked on the emulator with the Virtual Console:
 * vehicle speed (PERF_VEHICLE_SPEED), low beam state and switch, steering angle, cruise control,
 * brake pedal, turn signal, the fused location callback rate, the navigation's driving and turn info,
 * and whether the microphone records.
 */
object SignalProbe {
    private const val TAG = "SignalProbe"
    private val main = Handler(Looper.getMainLooper())
    private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        speed(app)
        main.postDelayed({ vehicle(app) }, 1500)
        location(app)
        navigation(app)
        main.postDelayed({ record(app) }, 4000)
    }

    // ------------------------------------------------------------ speed (Android car property)

    private fun speed(app: Context) {
        val perm = "android.car.permission.CAR_SPEED"
        Log.i(TAG, "speed permission $perm granted=${app.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED}")
        runCatching {
            val car = Car.createCar(app)
            val props = car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            val ok = props.registerCallback(object : CarPropertyManager.CarPropertyEventCallback {
                override fun onChangeEvent(value: CarPropertyValue<*>) {
                    val mps = value.value as? Float ?: return
                    Log.i(TAG, "speed ${"%.1f".format(mps * 3.6f)} km/h (status ${value.status})")
                }
                override fun onErrorEvent(propertyId: Int, zone: Int) = Log.w(TAG, "speed error event").let { }
            }, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_ONCHANGE)
            Log.i(TAG, "speed callback registered=$ok")
        }.onFailure { Log.w(TAG, "speed not available: ${it.javaClass.simpleName} ${it.message}") }
    }

    // ------------------------------------------------------------ vehicle SDK

    private fun vehicle(app: Context) {
        val v = runCatching { Vehicle(app).also { it.initialize() } }.getOrElse {
            Log.w(TAG, "vehicle SDK unavailable: ${it.message}"); return
        }
        main.postDelayed({
            val light = v.getLight()
            tryIt("low beam state") {
                light.registerLowBeamLightState(object : LightStateListener {
                    override fun onLightStateUpdated(state: LightState?) = Log.i(TAG, "low beam state $state").let { }
                    override fun onFailed(e: Exception) = Log.w(TAG, "low beam state failed: ${e.message}").let { }
                })
            }
            // the switch has no listener: read it every 2 seconds and log changes
            var lastSwitch: Any? = Unit
            main.post(object : Runnable {
                override fun run() {
                    light.getLowBeamLightSwitch({ s -> if (s != lastSwitch) { lastSwitch = s; Log.i(TAG, "low beam switch $s") } },
                        { e -> if (lastSwitch != "error") { lastSwitch = "error"; Log.w(TAG, "low beam switch failed: ${e.message}") } })
                    main.postDelayed(this, 2000)
                }
            })
            tryIt("steering") {
                v.getSteeringWheel().registerSteeringWheelAngle(object : SteeringWheelAngleListener {
                    override fun onSteeringWheelAngleUpdated(angle: Float?) = Log.i(TAG, "steering angle $angle").let { }
                    override fun onFailed(e: Exception) = Log.w(TAG, "steering failed: ${e.message}").let { }
                })
            }
            tryIt("cruise") {
                val dm = v.getDrivingMode()
                dm.registerCruiseControlState(object : CruiseControlStateListener {
                    override fun onCruiseControlStateUpdated(state: CruiseControl?) = Log.i(TAG, "cruise state $state").let { }
                    override fun onFailed(e: Exception) = Log.w(TAG, "cruise state failed: ${e.message}").let { }
                })
                dm.registerCruiseControlTargetSpeed(object : CruiseControlTargetSpeedListener {
                    override fun onCruiseControlTargetSpeedUpdated(speed: Float?) = Log.i(TAG, "cruise target $speed").let { }
                    override fun onFailed(e: Exception) = Log.w(TAG, "cruise target failed: ${e.message}").let { }
                })
            }
            tryIt("brake") {
                v.getBrake().registerBrakePedalStatus(object : BrakePedalStatusListener {
                    override fun onBrakePedalStatusUpdated(pressed: Boolean?) = Log.i(TAG, "brake pedal $pressed").let { }
                    override fun onFailed(e: Exception) = Log.w(TAG, "brake failed: ${e.message}").let { }
                })
            }
            tryIt("turn signal") {
                v.getTurnSignal().registerTurnSignalState(object : TurnSignalStateListener {
                    override fun onTurnSignalStateUpdated(state: TurnSignalValue?) = Log.i(TAG, "turn signal $state").let { }
                    override fun onFailed(e: Exception) = Log.w(TAG, "turn signal failed: ${e.message}").let { }
                })
            }
        }, 1500)
    }

    // ------------------------------------------------------------ fused location

    private fun location(app: Context) {
        runCatching {
            val fused = FusedLocation(app)
            fused.initialize()
            Log.i(TAG, "fused location supported=${fused.isSupported()}")
            var last = 0L
            main.postDelayed({ tryIt("fused location") {
                fused.registerFusedLocationCallback(object : FusedLocationListener {
                    override fun onChanged(d: LocationData?) {
                        if (d == null) return
                        val now = SystemClock.elapsedRealtime()
                        val gap = if (last == 0L) -1 else now - last
                        last = now
                        Log.i(TAG, "location ${"%.5f".format(d.latitude)},${"%.5f".format(d.longitude)} heading ${d.heading} confidence ${d.confidence} gap ${gap}ms")
                    }
                    override fun onError(e: Exception) = Log.w(TAG, "location error: ${e.message}").let { }
                })
            } }, 1500)
        }.onFailure { Log.w(TAG, "fused location unavailable: ${it.message}") }
    }

    // ------------------------------------------------------------ navigation

    private fun navigation(app: Context) {
        val n = runCatching { NaviHelper(app) }.getOrElse { Log.w(TAG, "no navigation: ${it.message}"); return }
        n.addListener(object : NaviHelperEventListener {
            override fun onDrivingInfo(info: DrivingInfo) = Log.i(TAG, "driving info $info").let { }
            override fun onTBTInfo(list: List<TBTInfo>) = Log.i(TAG, "tbt ${list.take(3)}").let { }
            override fun onRouteStateInfo(info: RouteStateInfo) = Log.i(TAG, "route state $info").let { }
            override fun onCurrentRoadSpeedLimit(limit: Int) = Log.i(TAG, "speed limit $limit").let { }
        })
        n.initialize(
            onConnected = {
                Log.i(TAG, "navigation connected")
                main.post(object : Runnable {
                    override fun run() {
                        runCatching { n.getTBTInfo() }
                        runCatching { n.getRouteStateInfo() }
                        main.postDelayed(this, 5000)
                    }
                })
            },
            onFailed = { Log.w(TAG, "navigation failed: ${it.message}") },
        )
    }

    // ------------------------------------------------------------ microphone

    private fun record(app: Context) {
        if (app.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "record: RECORD_AUDIO not granted"); return
        }
        thread(name = "probe-record") {
            val rate = 16_000
            val size = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4096)
            val rec = runCatching {
                @Suppress("MissingPermission")
                AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size)
            }.getOrElse { Log.w(TAG, "record: cannot open (${it.message})"); return@thread }
            if (rec.state != AudioRecord.STATE_INITIALIZED) { Log.w(TAG, "record: not initialized"); rec.release(); return@thread }
            val buf = ShortArray(size / 2)
            var total = 0
            var peak = 0
            rec.startRecording()
            val end = SystemClock.elapsedRealtime() + 3000
            while (SystemClock.elapsedRealtime() < end) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0) { total += n; for (i in 0 until n) peak = maxOf(peak, abs(buf[i].toInt())) }
            }
            rec.stop(); rec.release()
            Log.i(TAG, "record: ${total} samples in 3 s, peak $peak of 32767")
        }
    }

    private inline fun tryIt(name: String, block: () -> Unit) {
        try { block() } catch (e: Exception) { Log.w(TAG, "$name not available: ${e.javaClass.simpleName} ${e.message}") }
    }
}
