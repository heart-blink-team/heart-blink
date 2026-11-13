package kr.heartblink.car.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kr.heartblink.car.app.NaviWatch
import kr.heartblink.car.app.Session
import kr.heartblink.car.data.Place
import kr.heartblink.car.ui.component.PrimaryButton
import kr.heartblink.car.ui.component.SecondaryButton
import kr.heartblink.car.ui.component.Tab
import kr.heartblink.car.ui.component.tap
import kr.heartblink.car.ui.theme.Heart
import kr.heartblink.car.ui.theme.HeartType
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.VectorSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.MultiLineString
import org.maplibre.geojson.Point
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt

/** The place a message of the 장소 group opens near, and how near (06-4, 06-5). */
internal object PlaceDraft {
    var place by mutableStateOf<Place?>(null)
    var radiusM by mutableIntStateOf(Place.DEFAULT_RADIUS_M)

    /** True when [place] was picked on the map (06-5) rather than from the navigation's list. */
    var fromMap by mutableStateOf(false)

    /** Opens the map (06-5); set by the 06 screen. */
    var onMap: () -> Unit = {}

    val chosen: Place? get() = place?.copy(radiusM = radiusM)

    /**
     * Where a 주차 message opens (06-3): a place the sender knows, such as the partner's home or office,
     * picked with a pin. The partner's car opens it when its gear goes to P within [ParkRadiusM].
     */
    var parkAt by mutableStateOf<Place?>(null)

    /** True while 06-5 picks [parkAt] instead of [place]. */
    val pickingPark: Boolean get() = MessageDraft.group == ConditionGroup.Arrival
}

/** How near the car must stop for a 주차 message: a car park can be large, the pin sits on the building. */
internal const val ParkRadiusM = 300

/** "집" for a named pin, "고른 곳" for a bare one: the place in "서연님이 ○○에 주차하면 열려요". */
internal fun parkName(p: Place?): String = p?.name?.takeIf { it.isNotBlank() && it != MapPinName } ?: "고른 곳"

private val Radii = listOf(300 to "300m", 500 to "500m", 1000 to "1km")

/** The detail row of 06-4: the car navigation's bookmarks and recent places, the map, and the circle size. */
@Composable
internal fun PlaceDetail() {
    val places by NaviWatch.places.collectAsState()
    LaunchedEffect(Unit) { NaviWatch.requestPlaces() }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            places.take(6).forEach { p ->
                val on = !PlaceDraft.fromMap && PlaceDraft.place?.let { it.lat == p.lat && it.lng == p.lng } == true
                Chip(p.name, on) {
                    PlaceDraft.place = p
                    PlaceDraft.fromMap = false
                }
            }
            val picked = PlaceDraft.place?.name?.takeIf { PlaceDraft.fromMap }
            Chip(picked?.let { "지도: $it" } ?: "지도에서 고르기", picked != null) { PlaceDraft.onMap() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("이만큼 가까이 오면", style = HeartType.Label, color = Heart.colors.textSecondary, maxLines = 1)
            Radii.forEach { (m, label) -> Chip(label, PlaceDraft.radiusM == m) { PlaceDraft.radiusM = m } }
        }
    }
}

/**
 * A flat choice chip. Picked, it is strong like a picked option: 12% pink fill, 3 px pink line and deep pink
 * bold words. The white under the fill keeps it readable over the map.
 */
@Composable
private fun Chip(text: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Heart.colors
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier
            .clip(shape)
            .background(c.bgCard)
            .background(if (on) PickedOptionFill else Color.Transparent)
            .border(if (on) 3.dp else 2.dp, if (on) c.pinkAccent else c.line, shape)
            .tap(onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            text, style = HeartType.Label.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Medium),
            color = if (on) c.pinkDeep else c.textPrimary, maxLines = 1,
        )
    }
}

/** Name of a pin with nothing named near it. */
internal const val MapPinName = "지도에서 고른 곳"

/** A pin on the map and the name of what is near it. */
private data class Pin(val at: LatLng, val name: String)

/**
 * 06-5: tap the map or a name on it to put the pin; 이곳으로 keeps it and goes back to 06-4, or to 06-3 when
 * it picks where a 주차 message opens (a fixed circle there, so no size to choose).
 */
@Composable
internal fun PlaceMapScreen(nav: Nav) {
    val c = Heart.colors
    val park = PlaceDraft.pickingPark
    val back = if (park) "06-3" else "06-4"
    val radius = if (park) ParkRadiusM else PlaceDraft.radiusM
    var pin by remember {
        val start = if (park) PlaceDraft.parkAt else PlaceDraft.place?.takeIf { PlaceDraft.fromMap }
        mutableStateOf(start?.let { Pin(LatLng(it.lat, it.lng), it.name) })
    }
    var recenter by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        NaviWatch.requestHere()
        NaviWatch.requestPlaces()
    }
    HeartWindow(Tab.Message, nav) {
        // the same edges and title as 06, the map as the one card on the page
        Column(Modifier.padding(start = 88.dp, end = 88.dp, top = 200.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            val title = if (park) "${Session.view.partnerName.ifBlank { "연인" }}님이 주차하면 메시지가 열릴 곳을 골라요"
                else "지도나 이름을 눌러 메시지가 열릴 곳을 골라요"
            Text(title, style = HeartType.HeartTitle, color = c.textPrimary, maxLines = 1)
            Box(Modifier.fillMaxWidth().height(640.dp).clip(RoundedCornerShape(28.dp)).border(2.dp, c.line, RoundedCornerShape(28.dp))) {
                PinMap(pin, radius, recenter) { pin = it }
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val label = pin?.name?.let { if (it == MapPinName) "핀을 꽂았어요" else "$it 근처" }
                        ?: "가게나 동네 이름을 보고 눌러 보세요"
                    Chip(label, pin != null) {}
                    Box(Modifier.weight(1f))
                    Chip("내 차 위치로", false) {
                        NaviWatch.requestHere()
                        recenter++
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (park) {
                    Text("이곳 ${ParkRadiusM}m 안에서 P로 바꾸면 열려요", style = HeartType.Body, color = c.textSecondary, maxLines = 1)
                } else {
                    Text("이만큼 가까이 오면", style = HeartType.Body, color = c.textSecondary, maxLines = 1)
                    Radii.forEach { (m, label) -> Chip(label, PlaceDraft.radiusM == m) { PlaceDraft.radiusM = m } }
                }
                Box(Modifier.weight(1f))
                SecondaryButton("취소", Modifier.width(200.dp), onClick = { nav.go(back) })
                PrimaryButton("이곳으로", Modifier.width(260.dp), onClick = {
                    pin?.let {
                        if (park) {
                            PlaceDraft.parkAt = Place(it.name, it.at.latitude, it.at.longitude, ParkRadiusM)
                        } else {
                            PlaceDraft.place = Place(it.name, it.at.latitude, it.at.longitude, PlaceDraft.radiusM)
                            PlaceDraft.fromMap = true
                        }
                    }
                    nav.go(back)
                })
            }
        }
    }
}

private const val StyleUrl = "https://tiles.openfreemap.org/styles/liberty"

/** What a name on the map stands for: the order is also the order names are placed in. */
private enum class Kind { Car, Saved, Area, Shop, Road }

/** A named spot from the map data or the navigation. Smaller [rank] is better known. */
private data class Spot(val name: String, val at: LatLng, val kind: Kind, val rank: Int = 0)

/** Neighbourhood names (동, 마을) that help find your way; cities and countries are too wide to pick. */
private val AreaClasses = setOf("suburb", "quarter", "neighbourhood", "village", "hamlet", "isolated_dwelling")

/**
 * Circle radius in screen pixels for [metres] at [lat], for every zoom: the map's 512-pixel tiles halve
 * the metres per pixel with each zoom step, so an exponential curve of base 2 is exact.
 */
private fun circleRadius(metres: Int, lat: Double): Expression {
    val atZero = metres * 512.0 / (40_075_016.686 * cos(Math.toRadians(lat)))
    return Expression.interpolate(
        Expression.exponential(2f), Expression.zoom(),
        Expression.stop(0, atZero), Expression.stop(22, atZero * (1 shl 22)),
    )
}

/** Shops, stations and neighbourhood names in the map tiles now on screen, in Korean when the tile has it. */
private fun spotsOnMap(s: Style): List<Spot> {
    val src = s.getSourceAs<VectorSource>("openmaptiles") ?: return emptyList()
    fun Feature.koName() = (getStringProperty("name:ko") ?: getStringProperty("name"))?.trim()?.takeIf { it.isNotEmpty() }
    fun Feature.point() = (geometry() as? Point)?.let { LatLng(it.latitude(), it.longitude()) }
    // parks and peaks are landmarks too
    val shops = src.querySourceFeatures(arrayOf("poi", "park", "mountain_peak"), null).mapNotNull { f ->
        val name = f.koName() ?: return@mapNotNull null
        val at = f.point() ?: return@mapNotNull null
        Spot(name, at, Kind.Shop, f.getNumberProperty("rank")?.toInt() ?: 99)
    }
    val areas = src.querySourceFeatures(arrayOf("place"), null).mapNotNull { f ->
        if (f.getStringProperty("class") !in AreaClasses) return@mapNotNull null
        val name = f.koName() ?: return@mapNotNull null
        val at = f.point() ?: return@mapNotNull null
        Spot(name, at, Kind.Area, f.getNumberProperty("rank")?.toInt() ?: 99)
    }
    // a road's name sits at the middle of each of its pieces; the screen keeps one per name
    val roads = src.querySourceFeatures(arrayOf("transportation_name"), null).mapNotNull { f ->
        val name = f.koName() ?: return@mapNotNull null
        val line = when (val g = f.geometry()) {
            is LineString -> g.coordinates()
            is MultiLineString -> g.coordinates().maxByOrNull { it.size }
            else -> null
        } ?: return@mapNotNull null
        val mid = line.getOrNull(line.size / 2) ?: return@mapNotNull null
        Spot(name, LatLng(mid.latitude(), mid.longitude()), Kind.Road)
    }
    // tiles overlap at their edges, so the same spot can come twice
    return (areas + shops + roads).distinctBy { "${it.name}:${"%.4f".format(it.at.latitude)}" }
}

/**
 * A MapLibre map with the car, the navigation's saved places, one pink pin and the circle it opens in.
 * Names are drawn by the app over the map (the map engine's own labels stay off): they read the same on
 * every screen, and a name can be pressed to pick that spot. A tap elsewhere names the pin after the
 * nearest shop, or else the neighbourhood.
 */
@Composable
private fun PinMap(pin: Pin?, radiusM: Int, recenter: Int, onPin: (Pin) -> Unit) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val view = remember {
        MapLibre.getInstance(context)
        MapView(context).also { it.onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var mapSpots by remember { mutableStateOf<List<Spot>>(emptyList()) }
    var moved by remember { mutableIntStateOf(0) }
    val here by NaviWatch.here.collectAsState()
    val places by NaviWatch.places.collectAsState()
    val pinNow by rememberUpdatedState(onPin)
    val known by rememberUpdatedState(
        buildList {
            here?.let { add(Spot("내 차", LatLng(it.lat, it.lng), Kind.Car)) }
            places.take(10).forEach { add(Spot(it.name, LatLng(it.lat, it.lng), Kind.Saved)) }
        },
    )
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> view.onStart()
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_STOP -> view.onStop()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        view.onStart()
        view.onResume()
        view.getMapAsync { m ->
            m.uiSettings.isAttributionEnabled = false
            m.uiSettings.isLogoEnabled = false
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            val start = pin?.at
                ?: NaviWatch.here.value?.let { LatLng(it.lat, it.lng) }
                ?: NaviWatch.places.value.firstOrNull()?.let { LatLng(it.lat, it.lng) }
                ?: LatLng(37.5665, 126.9780)
            m.cameraPosition = CameraPosition.Builder().target(start).zoom(15.5).build()
            m.setStyle(Style.Builder().fromUri(StyleUrl)) { s ->
                s.layers.filterIsInstance<SymbolLayer>().forEach { it.setProperties(PropertyFactory.visibility(Property.NONE)) }
                addOwnLayers(s)
                style = s
                map = m
                mapSpots = spotsOnMap(s)
            }
            m.addOnCameraMoveListener { moved++ }
            m.addOnCameraIdleListener {
                moved++
                m.style?.let { mapSpots = spotsOnMap(it) }
            }
            // tiles keep arriving after the camera stops
            view.addOnDidFinishRenderingMapListener { _ -> m.style?.let { mapSpots = spotsOnMap(it) } }
            m.addOnMapClickListener { at ->
                val tapped = m.projection.toScreenLocation(at)
                fun near(list: List<Spot>, radiusDp: Float) = list
                    .map { it to m.projection.toScreenLocation(it.at) }
                    .map { (s, p) -> s to hypot(p.x - tapped.x, p.y - tapped.y) }
                    .filter { it.second <= radiusDp * density }
                    .minByOrNull { it.second }?.first
                val saved = near(known.filter { it.kind == Kind.Saved }, 40f)
                val chosen = if (saved != null) {
                    Pin(saved.at, saved.name)
                } else {
                    val named = near(mapSpots.filter { it.kind == Kind.Shop }, 90f)
                        ?: near(mapSpots.filter { it.kind == Kind.Road }, 60f)
                        ?: near(mapSpots.filter { it.kind == Kind.Area }, 400f)
                    Pin(at, named?.name ?: MapPinName)
                }
                pinNow(chosen)
                true
            }
        }
        onDispose {
            lifecycle.removeObserver(observer)
            view.onPause()
            view.onStop()
            view.onDestroy()
        }
    }
    // the pin and its circle
    LaunchedEffect(pin, radiusM, style) {
        val s = style ?: return@LaunchedEffect
        val p = pin?.at
        // an empty collection, never null: MapLibre's renderer cannot take a missing shape
        val shape = p?.let { FeatureCollection.fromFeature(Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude))) }
            ?: FeatureCollection.fromFeatures(emptyList())
        (s.getSource("pin") as? GeoJsonSource)?.setGeoJson(shape)
        if (p != null) s.getLayer("area")?.setProperties(PropertyFactory.circleRadius(circleRadius(radiusM, p.latitude)))
    }
    // dots for the car and the navigation's saved places
    LaunchedEffect(here, places, style) {
        val s = style ?: return@LaunchedEffect
        val marks = buildList {
            places.take(10).forEach { add(mark(it.lat, it.lng, "place")) }
            here?.let { add(mark(it.lat, it.lng, "car")) }
        }
        (s.getSource("marks") as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(marks))
    }
    // the car's place came after the map opened, or 내 차 위치로 was pressed
    var centred by remember { mutableStateOf(pin != null) }
    LaunchedEffect(here, recenter, map) {
        val m = map ?: return@LaunchedEffect
        val h = here ?: return@LaunchedEffect
        if (centred && recenter == 0) return@LaunchedEffect
        centred = true
        m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(h.lat, h.lng), 15.5))
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView({ view }, Modifier.fillMaxSize())
        val m = map
        if (m != null) {
            // read so the names follow the map while it moves
            @Suppress("UNUSED_EXPRESSION") moved
            val candidates = (known + mapSpots.filter { it.kind == Kind.Area }.sortedBy { it.rank }.take(8) +
                mapSpots.filter { it.kind == Kind.Shop }.sortedBy { it.rank }.take(60) +
                mapSpots.filter { it.kind == Kind.Road })
            val onScreen = candidates.map { it to m.projection.toScreenLocation(it.at) }
                .filter { (_, p) -> p.x in 0f..view.width.toFloat() && p.y in 0f..view.height.toFloat() }
                .distinctBy { (s, p) -> if (s.kind == Kind.Road) s.name else "${s.name}:${p.x}:${p.y}" }
            MapNames(onScreen.map { it.first }, onScreen.map { it.second.x to it.second.y }) { spot ->
                if (spot.kind != Kind.Car) pinNow(Pin(spot.at, spot.name))
            }
        }
    }
}

/**
 * Names over the map, centred a little above their spot. A name that would cover one placed before it
 * is left out, so the car and saved places always show and shops fill the gaps.
 */
@Composable
private fun MapNames(spots: List<Spot>, at: List<Pair<Float, Float>>, onPick: (Spot) -> Unit) {
    val c = Heart.colors
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            spots.forEach { s ->
                val shape = RoundedCornerShape(999.dp)
                when (s.kind) {
                    Kind.Area -> Text(
                        s.name, style = HeartType.Small.copy(fontWeight = FontWeight.Bold),
                        color = c.textSecondary, maxLines = 1,
                    )
                    Kind.Road -> Text(
                        s.name, style = HeartType.Small.copy(fontSize = HeartType.Small.fontSize * 0.85f),
                        color = Color(0xFF8A6A3C), maxLines = 1,
                        modifier = Modifier.background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 4.dp),
                    )
                    else -> {
                        val (bg, line, ink) = when (s.kind) {
                            Kind.Car -> Triple(Color(0xFF3B82F6), Color.White, Color.White)
                            Kind.Saved -> Triple(c.pinkLight, c.pinkAccent, c.pinkDeep)
                            else -> Triple(Color.White.copy(alpha = 0.92f), c.line, c.textPrimary)
                        }
                        Box(
                            Modifier.clip(shape).background(bg).border(1.5.dp, line, shape)
                                .tap { onPick(s) }.padding(horizontal = 10.dp, vertical = 3.dp),
                        ) { Text(s.name, style = HeartType.Small, color = ink, maxLines = 1) }
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val placed = mutableListOf<IntArray>()
        val gap = (4 * density).roundToInt()
        val lift = (16 * density).roundToInt()
        val items = measurables.mapIndexedNotNull { i, ms ->
            val p = ms.measure(Constraints())
            val (x, y) = at[i]
            val left = (x - p.width / 2f).roundToInt()
            val top = if (spots[i].kind == Kind.Area) (y - p.height / 2f).roundToInt() else (y - p.height - lift / 2f).roundToInt()
            val box = intArrayOf(left - gap, top - gap, left + p.width + gap, top + p.height + gap)
            val hit = placed.any { o -> box[0] < o[2] && o[0] < box[2] && box[1] < o[3] && o[1] < box[3] }
            if (hit) null else {
                placed += box
                Triple(p, left, top)
            }
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            items.forEach { (p, x, y) -> p.place(x, y) }
        }
    }
}

private fun mark(lat: Double, lng: Double, kind: String): Feature =
    Feature.fromGeometry(Point.fromLngLat(lng, lat)).also { it.addStringProperty("kind", kind) }

private fun addOwnLayers(s: Style) {
    s.addSource(GeoJsonSource("marks", FeatureCollection.fromFeatures(emptyList())))
    s.addSource(GeoJsonSource("pin", FeatureCollection.fromFeatures(emptyList())))
    s.addLayer(
        CircleLayer("area", "pin").withProperties(
            PropertyFactory.circleColor("#F7B6C8"), PropertyFactory.circleOpacity(0.35f),
            PropertyFactory.circleStrokeColor("#E8557F"), PropertyFactory.circleStrokeWidth(2f),
        ),
    )
    s.addLayer(
        CircleLayer("marks-place", "marks").withProperties(
            PropertyFactory.circleColor("#FFFFFF"), PropertyFactory.circleRadius(8f),
            PropertyFactory.circleStrokeColor("#E8557F"), PropertyFactory.circleStrokeWidth(3f),
        ).withFilter(Expression.eq(Expression.get("kind"), "place")),
    )
    s.addLayer(
        CircleLayer("marks-car", "marks").withProperties(
            PropertyFactory.circleColor("#3B82F6"), PropertyFactory.circleRadius(10f),
            PropertyFactory.circleStrokeColor("#FFFFFF"), PropertyFactory.circleStrokeWidth(4f),
        ).withFilter(Expression.eq(Expression.get("kind"), "car")),
    )
    s.addLayer(
        CircleLayer("dot", "pin").withProperties(
            PropertyFactory.circleColor("#E8557F"), PropertyFactory.circleRadius(10f),
            PropertyFactory.circleStrokeColor("#FFFFFF"), PropertyFactory.circleStrokeWidth(3f),
        ),
    )
}
