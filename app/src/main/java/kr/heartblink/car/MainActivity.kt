package kr.heartblink.car

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Handler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.svg.SvgDecoder
import androidx.compose.runtime.mutableIntStateOf
import kr.heartblink.car.app.CarSignals
import kr.heartblink.car.app.DriveTalk
import kr.heartblink.car.app.HeartService
import kr.heartblink.car.app.MessageOpener
import kr.heartblink.car.app.NaviWatch
import kr.heartblink.car.app.SignalProbe
import kr.heartblink.car.app.Session
import kr.heartblink.car.ui.screen.Arrival
import kr.heartblink.car.ui.screen.MessageDraft
import kr.heartblink.car.app.Stage
import kr.heartblink.car.ui.component.HeartLogo
import kr.heartblink.car.ui.component.Tab
import kr.heartblink.car.ui.screen.Nav
import kr.heartblink.car.ui.screen.Screens
import kr.heartblink.car.ui.theme.Heart
import kr.heartblink.car.ui.theme.HeartTheme

/**
 * One window, many screens. `adb shell am start -n kr.heartblink.car/.MainActivity --es screen 05` opens
 * a screen by its Figma number, which is how the screens are checked against the design.
 *
 * With the server configured, the connection stage decides the screen: 01 until the profile is saved,
 * 01-1 until a request is sent and accepted, 02 when a request came in, 03 to finish the setup, then 04.
 * In gear D every screen gives way to 04-1 and comes back in P.
 */
class MainActivity : ComponentActivity() {
    private var screen by mutableStateOf("04")
    private var beforeDrive: String? = null
    private var resumes by mutableIntStateOf(0)

    /** Opened messages already brought up since the app came to the front. */
    private val shown = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.getStringExtra("screen")?.let { screen = it }
        if (intent?.getBooleanExtra("talk_fast", false) == true) DriveTalk.fast = true
        if (BuildConfig.DEBUG) {
            intent?.getStringExtra("heard")?.let { MessageDraft.pendingHeard = it }
            // test the first start of the day without waiting for 4 a.m.
            if (intent?.getBooleanExtra("new_day", false) == true) {
                getSharedPreferences("opener", MODE_PRIVATE).edit().remove("first_start_day").apply()
            }
            // test 목적지 도착 on the emulator, whose navigation never moves: report the arrival a few seconds
            // after the start, once the waiting messages have loaded
            intent?.getStringExtra("arrive")?.let { name ->
                Handler(mainLooper).postDelayed({ NaviWatch.arrived(name) }, 10_000)
            }
            // log every car signal the plan relies on (tag SignalProbe), to check them on the emulator
            if (intent?.getBooleanExtra("probe", false) == true) SignalProbe.start(this)
        }
        HeartService.start(this)
        Session.start(this)
        CarSignals.start(this)
        MessageOpener.start(this)
        askCarPermissions()
        setContent { App() }
    }

    /** The vehicle speed and the car's position are runtime permissions: ask once, then start the speed. */
    private val carPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        CarSignals.startSpeed(this)
    }

    private fun askCarPermissions() {
        val missing = listOf(CarSignals.CAR_SPEED, CarSignals.FUSED_LOCATION)
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) carPermissions.launch(missing.toTypedArray())
    }

    override fun onResume() {
        super.onResume()
        // opening the app again shows the messages still waiting from the envelope
        shown.clear()
        resumes++
        DriveTalk.appVisible = true
        Session.onResume()
    }

    override fun onPause() {
        DriveTalk.appVisible = false
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("screen")?.let { screen = it }
    }

    @Composable
    private fun App() {
        setSingletonImageLoaderFactory { ctx -> ImageLoader.Builder(ctx).components { add(SvgDecoder.Factory()) }.build() }
        val stage = Session.stage
        LaunchedEffect(stage) {
            screen = when (stage) {
                Stage.Loading -> screen
                Stage.NeedProfile -> "01"
                Stage.SendRequest -> "01-1"
                Stage.Incoming -> "02"
                // stay on 01-1 so the box shows 요청 수락됨; its 다음 button opens 03
                Stage.Setup -> if (screen == "01-1") screen else "03"
                Stage.Ready -> if (Session.live && screen in Onboarding) "04" else screen
            }
        }
        val driving by CarSignals.driving.collectAsState()
        LaunchedEffect(driving) {
            if (driving == true && screen != "04-1") {
                beforeDrive = screen
                screen = "04-1"
            } else if (driving == false && screen == "04-1") {
                screen = beforeDrive ?: "04"
                beforeDrive = null
            }
        }
        // a message that opened in this car: the envelope first, once the car is parked
        val waiting by MessageOpener.waiting.collectAsState()
        LaunchedEffect(waiting, resumes, driving, stage) {
            if (stage != Stage.Ready || driving == true || screen in Arriving) return@LaunchedEffect
            val next = waiting.firstOrNull { it.id !in shown } ?: return@LaunchedEffect
            shown += waiting.map { it.id }
            Arrival.current = next
            screen = "arrive"
        }
        HeartTheme {
            if (stage == Stage.Loading) {
                Loading()
                return@HeartTheme
            }
            val ready = stage == Stage.Ready
            val nav = Nav(
                tab = { t ->
                    if (ready) screen = when (t) {
                        Tab.Home -> "04"
                        Tab.Question -> "05"
                        Tab.Message -> "06"
                        Tab.Record -> "15"
                    }
                },
                go = { if (ready || it in Onboarding) screen = it },
                back = { if (ready) screen = "04" },
            )
            Screens.Show(screen, nav)
        }
    }

    @Composable
    private fun Loading() {
        Box(Modifier.fillMaxSize().background(Heart.colors.bgBase), contentAlignment = Alignment.Center) {
            HeartLogo(Modifier.size(302.4.dp, 92.4.dp))
        }
    }

    private companion object {
        val Onboarding = setOf("01", "01-1", "02", "03")
        val Arriving = setOf("arrive", "arrive-scene")
    }
}
