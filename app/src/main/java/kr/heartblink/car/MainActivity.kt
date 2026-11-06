package kr.heartblink.car

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.svg.SvgDecoder
import kr.heartblink.car.ui.component.Tab
import kr.heartblink.car.ui.screen.Nav
import kr.heartblink.car.ui.screen.Screens
import kr.heartblink.car.ui.theme.HeartTheme

/**
 * One window, many screens. `adb shell am start -n kr.heartblink.car/.MainActivity --es screen 05` opens
 * a screen by its Figma number, which is how the screens are checked against the design.
 */
class MainActivity : ComponentActivity() {
    private var screen by mutableStateOf("04")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.getStringExtra("screen")?.let { screen = it }
        setContent { App() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("screen")?.let { screen = it }
    }

    @Composable
    private fun App() {
        setSingletonImageLoaderFactory { ctx -> ImageLoader.Builder(ctx).components { add(SvgDecoder.Factory()) }.build() }
        HeartTheme {
            val nav = Nav(
                tab = { t ->
                    screen = when (t) {
                        Tab.Home -> "04"
                        Tab.Question -> "05"
                        Tab.Message -> "06"
                        Tab.Record -> "15"
                    }
                },
                go = { screen = it },
                back = { screen = "04" },
            )
            Screens.Show(screen, nav)
        }
    }
}
