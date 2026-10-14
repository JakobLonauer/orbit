package io.github.thelony97.orbit.ui.map

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.Gravity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.GeoPoint
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.domain.Audience
import io.github.thelony97.orbit.domain.Index
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.geo.Geo
import io.github.thelony97.orbit.security.AppLock
import io.github.thelony97.orbit.ui.Nav
import io.github.thelony97.orbit.ui.Route
import io.github.thelony97.orbit.ui.Session
import io.github.thelony97.orbit.ui.components.Avatar
import io.github.thelony97.orbit.ui.components.GhostButton
import io.github.thelony97.orbit.ui.components.GlowButton
import io.github.thelony97.orbit.ui.components.KindMark
import io.github.thelony97.orbit.ui.components.OrbButton
import io.github.thelony97.orbit.ui.components.SpaceChip
import io.github.thelony97.orbit.ui.components.contactColor
import io.github.thelony97.orbit.ui.components.glass
import io.github.thelony97.orbit.ui.home.BottomBarSpace
import io.github.thelony97.orbit.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import kotlin.math.min

/** Free vector tiles (OpenStreetMap data), no API key needed. */
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/dark"
private const val SRC = "orbit-contacts"
private const val L_CLUSTER = "orbit-clusters"
private const val L_COUNT = "orbit-cluster-count"
private const val L_CONTACTS = "orbit-contacts"
private val EMPTY = """{"type":"FeatureCollection","features":[]}"""

private data class Pin(val contact: Contact, val point: GeoPoint)

@Composable
fun MapScreen(data: OrbitData, idx: Index, nav: Nav) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val audience = Session.audience
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var selected by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var fitted by remember { mutableStateOf(false) }
    var fitRequest by remember { mutableIntStateOf(0) }

    // A contact opened from its detail page is always shown, even if filters would hide it.
    val focusId = remember { Session.mapFocus }
    val pins = remember(data, audience) {
        Logic.active(data)
            .filter { it.id == focusId || (!it.archived && (it.isMe || !it.isOrg || audience != Audience.PEOPLE)) }
            .mapNotNull { c -> Geo.point(c)?.let { Pin(c, it) } }
    }
    val notFound = remember(data, audience) {
        Logic.others(data).count { audience.accepts(it) && it.address.isNotBlank() && it.geo?.let { g -> g.query == it.address.trim() && !g.found } == true }
    }
    val withoutAddress = remember(data, audience) { Logic.others(data).count { audience.accepts(it) && !it.archived && it.address.isBlank() } }
    val mePoint = Logic.me(data)?.let { Geo.point(it) }

    // Look up coordinates for new or changed addresses.
    LaunchedEffect(Unit) {
        Geo.refreshAll(ctx) { done, total -> progress = if (done < total) done to total else null }
    }

    val mapView = remember {
        MapLibre.getInstance(ctx)
        MapView(ctx).apply { onCreate(null) }
    }
    DisposableEffect(lifecycle, mapView) {
        var started = false
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (!started) { mapView.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> if (!resumed) { mapView.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> if (resumed) { mapView.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> if (started) { mapView.onStop(); started = false }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }

    val bottomInset = with(density) { (BottomBarSpace + 8.dp).roundToPx() }
    val haptics = io.github.thelony97.orbit.core.designsystem.rememberHaptics()
    val popScope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.uiSettings.apply {
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
                isCompassEnabled = false
                isLogoEnabled = false
                isAttributionEnabled = true
                attributionGravity = Gravity.BOTTOM or Gravity.END
                setAttributionMargins(0, 0, with(density) { 12.dp.roundToPx() }, bottomInset)
                setAttributionTintColor(Space.TextDim.toArgb())
            }
            m.cameraPosition = CameraPosition.Builder().target(LatLng(48.2, 13.5)).zoom(4.6).build()
            m.setStyle(Style.Builder().fromUri(STYLE_URL)) { st ->
                addLayers(st)
                style = st
            }
            m.addOnMapClickListener { latLng ->
                val pt = m.projection.toScreenLocation(latLng)
                val hits = m.queryRenderedFeatures(pt, L_CONTACTS)
                if (hits.isNotEmpty()) {
                    selected = hits.first().getStringProperty("id")
                    return@addOnMapClickListener true
                }
                val clusters = m.queryRenderedFeatures(pt, L_CLUSTER)
                if (clusters.isNotEmpty()) {
                    // „Ein Cluster platzt beim Antippen in seine Mitglieder auf“ (PLAN 1.3): straight
                    // to the zoom where it splits, centred on the cluster.
                    val cluster = clusters.first()
                    val source = m.style?.getSource(SRC) as? GeoJsonSource
                    val split = runCatching { source?.getClusterExpansionZoom(cluster) }.getOrNull()?.toDouble()
                    val centre = (cluster.geometry() as? org.maplibre.geojson.Point)?.let { LatLng(it.latitude(), it.longitude()) } ?: latLng
                    m.animateCamera(CameraUpdateFactory.newLatLngZoom(centre, (split ?: (m.cameraPosition.zoom + 2.0)) + 0.3), 350)
                    haptics.perform(io.github.thelony97.orbit.core.designsystem.Haptic.Tick)
                    return@addOnMapClickListener true
                }
                selected = null
                false
            }
            map = m
        }
    }

    // Push markers into the map whenever the data changes.
    LaunchedEffect(style, pins) {
        val st = style ?: return@LaunchedEffect
        val missing = pins.filter { st.getImage(imageName(it.contact)) == null }
        val images = withContext(Dispatchers.IO) {
            missing.associate { p -> imageName(p.contact) to markerBitmap(ctx, p.contact, contactColor(p.contact, idx).toArgb()) }
        }
        images.forEach { (name, bmp) -> if (st.getImage(name) == null) st.addImage(name, bmp) }
        val json = buildJsonObject {
            put("type", "FeatureCollection")
            putJsonArray("features") {
                pins.forEach { p ->
                    add(buildJsonObject {
                        put("type", "Feature")
                        putJsonObject("geometry") {
                            put("type", "Point")
                            putJsonArray("coordinates") { add(p.point.lng); add(p.point.lat) }
                        }
                        putJsonObject("properties") {
                            put("id", p.contact.id)
                            put("name", Logic.shortName(p.contact))
                            put("img", imageName(p.contact))
                            put("me", p.contact.isMe)
                        }
                    })
                }
            }
        }
        (st.getSource(SRC) as? GeoJsonSource)?.setGeoJson(json.toString())
        if (!fitted && pins.isNotEmpty()) {
            fitted = true
            fitRequest++
            // „Marker ploppen federnd“ (PLAN 1.3): once, when the map first fills.
            val layer = st.getLayer(L_CONTACTS) as? SymbolLayer
            val dots = st.getLayer(L_CLUSTER) as? CircleLayer
            fun size(v: Float) = runCatching {
                layer?.setProperties(PropertyFactory.iconSize(v))
                dots?.setProperties(PropertyFactory.circleRadius(19f * v))
            }
            // Its own scope: more pins arriving while it pops must not cut it short.
            popScope.launch {
                try {
                    androidx.compose.animation.core.Animatable(0.3f).animateTo(
                        1f, androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 520f),
                    ) { size(value) }
                } finally {
                    // Never leave the markers half-grown.
                    size(1f)
                }
            }
        }
    }

    // Camera: focus a contact (from the detail screen) or show everyone.
    LaunchedEffect(map, fitRequest) {
        val m = map ?: return@LaunchedEffect
        if (fitRequest == 0) return@LaunchedEffect
        val focus = Session.mapFocus?.let { id -> pins.firstOrNull { it.contact.id == id } }
        Session.mapFocus = null
        val padding = with(density) { 64.dp.roundToPx() }
        when {
            focus != null -> {
                selected = focus.contact.id
                flyTo(m, focus.point.lat, focus.point.lng, 13.0, mapView.width / density.density.toDouble(), nearMs = 600)
            }
            pins.size == 1 -> m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(pins[0].point.lat, pins[0].point.lng), 12.0), 600)
            pins.size > 1 -> {
                val latSpan = pins.maxOf { it.point.lat } - pins.minOf { it.point.lat }
                val lngSpan = pins.maxOf { it.point.lng } - pins.minOf { it.point.lng }
                if (latSpan < 1e-4 && lngSpan < 1e-4) {
                    m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(pins[0].point.lat, pins[0].point.lng), 14.0), 600)
                } else {
                    val bounds = LatLngBounds.Builder().includes(pins.map { LatLng(it.point.lat, it.point.lng) }).build()
                    m.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding, padding * 2, padding, bottomInset + padding), 600)
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        // Header
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .glass(RoundedCornerShape(20.dp), tint = Space.PanelHigh, border = Space.LineStrong)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Map, null, tint = Space.Violet, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Karte", style = MaterialTheme.typography.titleMediumEmphasized, color = Space.Text)
                    val parts = buildList {
                        add("${pins.count { !it.contact.isMe }} auf der Karte")
                        if (notFound > 0) add("$notFound Adressen nicht gefunden")
                        if (withoutAddress > 0) add("$withoutAddress ohne Adresse")
                    }
                    Text(parts.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = Space.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                SpaceChip(
                    "Orgs",
                    audience != Audience.PEOPLE,
                    { Session.audience = if (audience == Audience.PEOPLE) Audience.ALL else Audience.PEOPLE },
                    leading = Icons.Rounded.Business,
                    accent = Space.Blue,
                )
                Spacer(Modifier.width(6.dp))
                OrbButton(Icons.Rounded.ZoomOutMap, "Alle zeigen", { Session.mapFocus = null; fitRequest++ }, size = 34.dp)
            }
            progress?.let { (done, total) ->
                Text("Adressen werden gesucht … $done/$total", style = MaterialTheme.typography.labelMedium, color = Space.Violet, modifier = Modifier.padding(top = 6.dp))
                LinearWavyProgressIndicator(
                    progress = { if (total == 0) 0f else done.toFloat() / total },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    color = Space.Violet,
                    trackColor = Space.PanelHighest,
                )
            }
        }

        val sel = selected?.let { id -> pins.firstOrNull { it.contact.id == id } }
        AnimatedVisibility(
            visible = sel != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = BottomBarSpace),
        ) {
            val p = sel ?: return@AnimatedVisibility
            PinCard(
                pin = p,
                pins = pins,
                me = mePoint,
                idx = idx,
                onOpen = { nav.open(Route.Detail(p.contact.id)) },
                onSelect = { id ->
                    selected = id
                    pins.firstOrNull { it.contact.id == id }?.let { q ->
                        map?.let { m -> flyTo(m, q.point.lat, q.point.lng, maxOf(m.cameraPosition.zoom, 12.0), mapView.width / density.density.toDouble(), nearMs = 400) }
                    }
                },
                onGoogle = { open(ctx, Geo.googleMapsSearch(p.contact.address)) },
                onRoute = { open(ctx, Geo.googleMapsRoute(p.contact.address)) },
            )
        }
    }
}

/**
 * Kamerafahrt im Bogen (PLAN 1.3): far jumps go out until both places are on screen, across and
 * back in; near ones are one move. A sequence without touch answer, so longer than 400 ms (W9).
 */
private fun flyTo(m: MapLibreMap, lat: Double, lng: Double, zoom: Double, widthDp: Double, nearMs: Int) {
    val cam = m.cameraPosition
    val from = cam.target
    // MapLibre zoom levels are in dp (512 dp world at zoom 0).
    val hop = if (widthDp <= 0.0) null else from?.let { io.github.thelony97.orbit.core.domain.FlyPath.top(it.latitude, it.longitude, cam.zoom, lat, lng, zoom, widthDp) }
    val land = CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), zoom)
    if (hop == null) {
        m.animateCamera(land, nearMs)
        return
    }
    m.animateCamera(
        CameraUpdateFactory.newLatLngZoom(LatLng(hop.lat, hop.lng), hop.zoom), 550,
        object : MapLibreMap.CancelableCallback {
            override fun onFinish() = m.animateCamera(land, 650)
            override fun onCancel() {}
        },
    )
}

/** Image id changes with the photo and the kind, so updated avatars are picked up. */
private fun imageName(c: Contact) = "av-${c.id}-${c.photo ?: ""}-${c.kind}"

private fun open(ctx: Context, uri: android.net.Uri) {
    AppLock.allowExternal()
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
private fun PinCard(
    pin: Pin,
    pins: List<Pin>,
    me: GeoPoint?,
    idx: Index,
    onOpen: () -> Unit,
    onSelect: (String) -> Unit,
    onGoogle: () -> Unit,
    onRoute: () -> Unit,
) {
    val c = pin.contact
    val nearby = remember(pin, pins) {
        pins.filter { it.contact.id != c.id && !it.contact.isMe }
            .map { it to Geo.distanceKm(pin.point, it.point) }
            .sortedBy { it.second }
            .take(3)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .glass(RoundedCornerShape(20.dp), tint = Space.PanelHigh, border = Space.LineStrong)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(c, contactColor(c, idx), 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Logic.displayName(c, false), style = MaterialTheme.typography.titleMediumEmphasized, color = Space.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (c.isOrg) {
                        Spacer(Modifier.width(6.dp))
                        KindMark(c)
                    }
                }
                Text(c.address, style = MaterialTheme.typography.labelMedium, color = Space.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (me != null && !c.isMe) {
                    Text("${Geo.formatDistance(Geo.distanceKm(me, pin.point))} von dir", style = MaterialTheme.typography.labelMedium, color = Space.Orange)
                }
            }
        }
        if (nearby.isNotEmpty()) {
            Text("In der Nähe", style = MaterialTheme.typography.labelSmall, color = Space.TextFaint, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                nearby.forEach { (p, km) ->
                    SpaceChip("${Logic.shortName(p.contact)} · ${Geo.formatDistance(km)}", false, { onSelect(p.contact.id) })
                }
            }
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlowButton("Öffnen", onOpen, icon = Icons.AutoMirrored.Rounded.OpenInNew)
            GhostButton("Google Maps", onGoogle, icon = Icons.Rounded.Map)
            if (!c.isMe) GhostButton("Route", onRoute, icon = Icons.Rounded.Directions)
        }
    }
}

private fun addLayers(st: Style) {
    st.addSource(
        GeoJsonSource(
            SRC,
            EMPTY,
            GeoJsonOptions().withCluster(true).withClusterRadius(42).withClusterMaxZoom(14),
        ),
    )
    val cluster = CircleLayer(L_CLUSTER, SRC)
    cluster.setProperties(
        PropertyFactory.circleColor(Space.VioletDeep.toArgb()),
        PropertyFactory.circleOpacity(0.85f),
        PropertyFactory.circleRadius(19f),
        PropertyFactory.circleStrokeColor(Space.Violet.toArgb()),
        PropertyFactory.circleStrokeWidth(2f),
    )
    cluster.setFilter(Expression.has("point_count"))
    st.addLayer(cluster)

    val count = SymbolLayer(L_COUNT, SRC)
    count.setProperties(
        PropertyFactory.textField(Expression.toString(Expression.get("point_count"))),
        PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
        PropertyFactory.textSize(13f),
        PropertyFactory.textColor(Space.Text.toArgb()),
        PropertyFactory.textIgnorePlacement(true),
        PropertyFactory.textAllowOverlap(true),
    )
    count.setFilter(Expression.has("point_count"))
    st.addLayer(count)

    val contacts = SymbolLayer(L_CONTACTS, SRC)
    contacts.setProperties(
        PropertyFactory.iconImage(Expression.get("img")),
        PropertyFactory.iconAllowOverlap(true),
        PropertyFactory.iconIgnorePlacement(true),
        PropertyFactory.textField(Expression.get("name")),
        PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
        PropertyFactory.textSize(11f),
        PropertyFactory.textColor(Space.Text.toArgb()),
        PropertyFactory.textHaloColor(Space.Void.toArgb()),
        PropertyFactory.textHaloWidth(1.5f),
        PropertyFactory.textOffset(arrayOf(0f, 2.1f)),
        PropertyFactory.textAnchor("top"),
        PropertyFactory.textOptional(true),
    )
    contacts.setFilter(Expression.not(Expression.has("point_count")))
    st.addLayer(contacts)
}

/** Avatar as bitmap for the map: photo or initials, round for people, square for organisations. */
private fun markerBitmap(ctx: Context, c: Contact, color: Int): Bitmap {
    val d = ctx.resources.displayMetrics.density
    val sizeDp = if (c.isMe) 46 else 38
    val px = (sizeDp * d).toInt()
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val ring = 2.5f * d
    val outer = RectF(0f, 0f, px.toFloat(), px.toFloat())
    val inner = RectF(ring, ring, px - ring, px - ring)
    val corner = if (c.isOrg) px * 0.28f else px / 2f
    val innerCorner = if (c.isOrg) inner.width() * 0.26f else inner.width() / 2f

    paint.color = color
    canvas.drawRoundRect(outer, corner, corner, paint)

    val photo = c.photo?.let { name ->
        runCatching {
            val file = OrbitRepository.photoFile(name)
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        }.getOrNull()
    }
    if (photo != null) {
        val shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val scale = inner.width() / min(photo.width, photo.height)
        val m = Matrix()
        m.setScale(scale, scale)
        m.postTranslate(inner.left - (photo.width * scale - inner.width()) / 2f, inner.top - (photo.height * scale - inner.height()) / 2f)
        shader.setLocalMatrix(m)
        paint.shader = shader
        canvas.drawRoundRect(inner, innerCorner, innerCorner, paint)
        paint.shader = null
    } else {
        paint.color = Space.Deep.toArgb()
        canvas.drawRoundRect(inner, innerCorner, innerCorner, paint)
        paint.shader = LinearGradient(
            inner.left, inner.top, inner.right, inner.bottom,
            (color and 0x00FFFFFF) or 0x99000000.toInt(),
            (color and 0x00FFFFFF) or 0x40000000,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(inner, innerCorner, innerCorner, paint)
        paint.shader = null
        paint.color = Space.Text.toArgb()
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = px * 0.34f
        val text = if (c.isMe) "★" else Logic.initials(c)
        val y = px / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, px / 2f, y, paint)
    }
    return bmp
}
