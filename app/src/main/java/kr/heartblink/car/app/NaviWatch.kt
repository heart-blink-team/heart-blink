package kr.heartblink.car.app

import ai.pleos.playground.navi.data.BookmarkInfo
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.RecentDestinationInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kr.heartblink.car.data.Place

/**
 * What the app reads from the car's own navigation: arrival at the destination, where the car is (for
 * place messages, never sent to the server), how far the next turn is (design 2.2) and the driver's
 * bookmarks and recent destinations (for picking a place on 06).
 */
object NaviWatch {
    private const val TAG = "NaviWatch"
    private val main = Handler(Looper.getMainLooper())
    private var navi: NaviHelper? = null
    private var ready = false

    /** Metres to the next turn while the navigation guides, null otherwise. */
    @Volatile var nextTurnM: Double? = null
        private set

    /** Name of the destination the car arrived at last. */
    @Volatile var arrivedAt: String? = null
        private set

    private val _places = MutableStateFlow<List<Place>>(emptyList())
    /** Bookmarks first (집, 회사, 즐겨찾기), then recent destinations, without duplicates. */
    val places: StateFlow<List<Place>> = _places
    private val _here = MutableStateFlow<Place?>(null)
    /** Where the car was last seen by the navigation (the pin map starts there). */
    val here: StateFlow<Place?> = _here

    /** Asks the navigation where the car is now. */
    fun requestHere() = ask { it.getCurrentLocationInfo() }

    /** The navigation says the car arrived at [name]: the arrival screen shows it and 목적지 도착 messages open. */
    fun arrived(name: String?) {
        arrivedAt = name
        Log.i(TAG, "arrived at $name")
        MessageOpener.onDestination()
    }

    private var bookmarks = emptyList<Place>()
    private var recents = emptyList<Place>()

    fun start(context: Context) {
        if (navi != null) return
        val n = runCatching { NaviHelper(context.applicationContext) }
            .onFailure { Log.w(TAG, "no navigation: ${it.message}") }
            .getOrNull() ?: return
        navi = n
        n.addListener(object : NaviHelperEventListener {
            override fun onDestinationArrived(info: DestinationArrivedInfo) {
                main.post { arrived(info.name) }
            }

            override fun onCurrentLocationInfo(info: CurrentLocationInfo) {
                main.post {
                    if (info.latitude != 0.0 || info.longitude != 0.0) _here.value = Place("내 차", info.latitude, info.longitude)
                    MessageOpener.onLocation(info.latitude, info.longitude)
                }
            }

            override fun onTBTInfo(list: List<TBTInfo>) {
                nextTurnM = list.firstOrNull()?.distance
            }

            override fun onRouteEnded() {
                nextTurnM = null
            }

            override fun onBookmarkInfo(list: List<BookmarkInfo>) {
                bookmarks = list.filter { it.latitude != 0.0 || it.longitude != 0.0 }
                    .map { Place(it.name.ifBlank { it.address.orEmpty() }, it.latitude, it.longitude) }
                main.post { publish() }
            }

            override fun onRecentDestinationInfo(list: List<RecentDestinationInfo>) {
                recents = list.filter { it.latitude != 0.0 || it.longitude != 0.0 }
                    .map { Place(it.name.ifBlank { it.address.orEmpty() }, it.latitude, it.longitude) }
                main.post { publish() }
            }

            override fun onBookMarkChanged() {
                main.post { ask { it.getBookmarkInfo() } }
            }
        })
        n.initialize(
            onConnected = {
                main.post {
                    ready = true
                    Log.i(TAG, "navigation connected")
                    requestPlaces()
                    poll()
                }
            },
            onFailed = { t -> Log.w(TAG, "navigation failed: ${t.message}") },
        )
    }

    /** Asks for the bookmarks and recent destinations again (06 장소). */
    fun requestPlaces() {
        ask { it.getBookmarkInfo() }
        ask { it.getRecentDestinationInfo() }
    }

    private fun publish() {
        _places.value = (bookmarks + recents).distinctBy { "${it.name}:${"%.4f".format(it.lat)}:${"%.4f".format(it.lng)}" }
        if (kr.heartblink.car.BuildConfig.DEBUG) Log.d(TAG, "places ${_places.value.map { "${it.name} ${it.lat},${it.lng}" }}")
    }

    /** Where the car is every 30 s (place messages), the next turn every 3 s while driving. */
    private fun poll() {
        main.postDelayed(object : Runnable {
            override fun run() {
                // the fused location (CarSignals) checks places every second; the navigation is the backup
                if (CarSignals.position.value == null && MessageOpener.hasPlaceMessages()) ask { it.getCurrentLocationInfo() }
                main.postDelayed(this, 30_000)
            }
        }, 2_000)
        main.postDelayed(object : Runnable {
            override fun run() {
                if (CarSignals.driving.value == true) ask { it.getTBTInfo() } else nextTurnM = null
                main.postDelayed(this, 3_000)
            }
        }, 3_000)
    }

    private inline fun ask(block: (NaviHelper) -> Unit) {
        val n = navi ?: return
        if (!ready) return
        runCatching { block(n) }.onFailure { Log.w(TAG, "navigation call failed: ${it.message}") }
    }
}
