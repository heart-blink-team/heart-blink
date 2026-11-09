package kr.heartblink.car.ui.screen

import androidx.compose.runtime.Composable
import kr.heartblink.car.app.Session

/** Every screen by its number on the Figma page "하트깜빡이". */
object Screens {
    val all: Map<String, @Composable (Nav) -> Unit> by lazy {
        linkedMapOf<String, @Composable (Nav) -> Unit>("04" to { nav -> HomeScreen(nav, Session.view) }) +
            startScreens + composeScreens + arrivalScreens + recordScreens + shopScreens
    }

    @Composable
    fun Show(id: String, nav: Nav) {
        (all[id] ?: all.getValue("04"))(nav)
    }
}
