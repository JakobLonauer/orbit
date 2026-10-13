package io.github.thelony97.orbit.ui.network

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.domain.Audience
import io.github.thelony97.orbit.domain.Index
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.domain.NetNode
import io.github.thelony97.orbit.domain.NetworkLayout
import io.github.thelony97.orbit.ui.Nav
import io.github.thelony97.orbit.ui.Route
import io.github.thelony97.orbit.ui.Session
import io.github.thelony97.orbit.ui.components.Avatar
import io.github.thelony97.orbit.ui.components.EmptyHint
import io.github.thelony97.orbit.ui.components.GhostButton
import io.github.thelony97.orbit.ui.components.GlowButton
import io.github.thelony97.orbit.ui.components.KindMark
import io.github.thelony97.orbit.ui.components.OrbButton
import io.github.thelony97.orbit.ui.components.SpaceChip
import io.github.thelony97.orbit.ui.components.SpaceSearchField
import io.github.thelony97.orbit.ui.components.contactColor
import io.github.thelony97.orbit.ui.components.glass
import io.github.thelony97.orbit.ui.home.BottomBarSpace
import io.github.thelony97.orbit.ui.theme.Space
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** One colour per ring – level 1 violet, 2 teal, 3 orange, 4 blue. */
private val levelColors = listOf(Space.Orange, Space.Violet, Space.Teal, Space.Orange, Space.Blue)

private fun nodeSize(level: Int) = when (level) {
    0 -> 54.dp
    1 -> 40.dp
    2 -> 34.dp
    3 -> 30.dp
    else -> 26.dp
}

@Composable
fun NetworkScreen(data: OrbitData, idx: Index, nav: Nav) {
    val me = Logic.me(data)
    val root = Session.networkRoot?.let { idx.contacts[it] }?.takeIf { it.deletedAt == null && !it.archived } ?: me
    val depth = Session.networkDepth
    val audience = Session.audience
    var selected by remember(root?.id) { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var fitScale by remember { mutableFloatStateOf(1f) }

    val network = remember(data, root?.id, depth, audience) {
        // People are always part of the net; the chip only adds organisations.
        root?.let { r -> NetworkLayout.build(data, r, depth, include = { !it.archived && (it.isMe || !it.isOrg || audience != Audience.PEOPLE) }, lastFirst = data.settings.sortByLastName) }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Netz", style = MaterialTheme.typography.headlineSmallEmphasized, color = Space.Text)
                Text(
                    network?.let { n -> "${n.nodes.size - 1} verbunden · Ringe = Abstand zu ${Logic.shortName(n.root)}" } ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    color = Space.TextFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OrbButton(Icons.Rounded.ZoomOutMap, "Ansicht zurücksetzen", { scale = fitScale; pan = Offset.Zero }, size = 38.dp)
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpaceChip(
                "Mitte: ${root?.let { Logic.shortName(it) } ?: "–"}",
                true,
                { picker = true },
                leading = Icons.Rounded.CenterFocusStrong,
                accent = Space.Orange,
            )
            Text("Tiefe", style = MaterialTheme.typography.labelMedium, color = Space.TextFaint, modifier = Modifier.padding(start = 6.dp))
            (1..4).forEach { d ->
                SpaceChip("$d", depth == d, { Session.networkDepth = d }, accent = levelColors[d])
            }
            SpaceChip(
                "Organisationen",
                audience != Audience.PEOPLE,
                { Session.audience = if (audience == Audience.PEOPLE) Audience.ALL else Audience.PEOPLE },
                leading = Icons.Rounded.Business,
                accent = Space.Blue,
            )
        }

        if (network == null || network.nodes.size <= 1) {
            Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyHint(
                    "Noch keine Beziehungen",
                    "Verknüpfe Menschen im Kontakt unter „Beziehungen“ – z. B. Partner, Eltern, Kolleg:innen oder dich selbst. Dann entsteht hier dein Netz.",
                )
                if (root != null) GlowButton("${Logic.shortName(root)} öffnen", { nav.open(Route.Detail(root.id)) }, icon = Icons.AutoMirrored.Rounded.OpenInNew)
            }
            return@Column
        }

        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clipToBounds(),
        ) {
            val w = constraints.maxWidth.toFloat()
            val h = constraints.maxHeight.toFloat() - with(density) { (BottomBarSpace - 24.dp).toPx() }
            val center = Offset(w / 2f, h / 2f)
            val levels = max(1, network.maxLevel)
            val minGap = with(density) { 84.dp.toPx() }
            val fitRadius = max(min(w, h) / 2f - with(density) { 34.dp.toPx() }, minGap)
            val gap = max(minGap, fitRadius / levels)
            val fit = min(1f, fitRadius / (gap * levels)).coerceAtLeast(0.3f)
            LaunchedEffect(root?.id, depth, levels, fit) { fitScale = fit; scale = fit; pan = Offset.Zero }

            fun position(n: NetNode): Offset =
                if (n.level == 0) center
                else Offset(
                    center.x + (gap * n.level * cos(n.angle)).toFloat(),
                    center.y + (gap * n.level * sin(n.angle)).toFloat(),
                )
            val positions = network.nodes.associate { it.contact.id to position(it) }
            val byId = network.nodes.associateBy { it.contact.id }

            fun toWorld(screen: Offset): Offset = (screen - pan - center) / scale + center

            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(network, gap, center) {
                        detectTapGestures { tap ->
                            val p = toWorld(tap)
                            val hit = network.nodes.minByOrNull { n ->
                                val q = positions.getValue(n.contact.id)
                                hypot(q.x - p.x, q.y - p.y)
                            }
                            val dist = hit?.let { n -> positions.getValue(n.contact.id).let { q -> hypot(q.x - p.x, q.y - p.y) } }
                            val radius = hit?.let { with(density) { nodeSize(it.level).toPx() } / 2f + with(density) { 10.dp.toPx() } / scale }
                            if (hit != null && dist != null && radius != null && dist <= radius) {
                                if (selected == hit.contact.id) nav.open(Route.Detail(hit.contact.id)) else selected = hit.contact.id
                            } else {
                                selected = null
                            }
                        }
                    }
                    .pointerInput(center) {
                        detectTransformGestures { centroid, panChange, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(0.3f, 3.5f)
                            val world = (centroid - pan - center) / scale + center
                            pan = centroid - center - (world - center) * newScale + panChange
                            scale = newScale
                        }
                    },
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(center.x / size.width, center.y / size.height)
                            scaleX = scale
                            scaleY = scale
                            translationX = pan.x
                            translationY = pan.y
                        },
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 10f))
                        for (l in 1..levels) {
                            drawCircle(levelColors[l].copy(alpha = 0.22f), gap * l, center, style = Stroke(1.dp.toPx(), pathEffect = dash))
                        }
                        // Cross links first (faint, dashed), then the tree.
                        network.edges.filter { !it.tree }.forEach { e ->
                            val a = positions[e.a] ?: return@forEach
                            val b = positions[e.b] ?: return@forEach
                            val hot = selected != null && (e.a == selected || e.b == selected)
                            drawLine(
                                if (hot) Space.Text.copy(alpha = 0.7f) else Space.TextFaint.copy(alpha = 0.45f),
                                a, b,
                                strokeWidth = (if (hot) 1.6.dp else 1.dp).toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 7f)),
                            )
                        }
                        network.edges.filter { it.tree }.forEach { e ->
                            val a = positions[e.a] ?: return@forEach
                            val b = positions[e.b] ?: return@forEach
                            val level = max(byId[e.a]?.level ?: 0, byId[e.b]?.level ?: 0)
                            val hot = selected != null && (e.a == selected || e.b == selected)
                            drawLine(
                                levelColors[level.coerceIn(1, 4)].copy(alpha = if (hot) 0.95f else 0.5f),
                                a, b,
                                strokeWidth = (if (hot) 2.4.dp else 1.4.dp).toPx(),
                            )
                        }
                        // Role labels on the lines of the selected node.
                        val sel = selected
                        if (sel != null) {
                            network.edges.filter { it.a == sel || it.b == sel }.forEach { e ->
                                val other = if (e.a == sel) e.b else e.a
                                val a = positions[sel] ?: return@forEach
                                val b = positions[other] ?: return@forEach
                                val label = NetworkLayout.roleOf(other, e.relation)
                                val layout = measurer.measure(label, TextStyle(color = Space.Text, fontSize = (11f / scale).coerceIn(8f, 18f).sp))
                                val mid = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
                                val topLeft = Offset(mid.x - layout.size.width / 2f, mid.y - layout.size.height / 2f)
                                drawRoundRect(
                                    Space.Deep.copy(alpha = 0.9f),
                                    topLeft = topLeft - Offset(6f, 3f),
                                    size = androidx.compose.ui.geometry.Size(layout.size.width + 12f, layout.size.height + 6f),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(20f, 20f),
                                )
                                drawText(layout, topLeft = topLeft)
                            }
                        }
                    }

                    network.nodes.forEach { n ->
                        val size = nodeSize(n.level)
                        val labelWidth = size + 34.dp
                        val p = positions.getValue(n.contact.id)
                        val sizePx = with(density) { size.toPx() }
                        val labelWidthPx = with(density) { labelWidth.toPx() }
                        val isSel = selected == n.contact.id
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .offset { IntOffset((p.x - labelWidthPx / 2).toInt(), (p.y - sizePx / 2).toInt()) }
                                .width(labelWidth),
                        ) {
                            Avatar(
                                n.contact,
                                if (n.level == 0) Space.Orange else levelColors[n.level.coerceIn(1, 4)],
                                size,
                                glowColor = when {
                                    isSel -> Space.Text
                                    n.level == 0 -> Space.Orange
                                    else -> null
                                },
                            )
                            Text(
                                Logic.shortName(n.contact),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSel) Space.Text else Space.TextDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            // Selection card
            val selNode = selected?.let { byId[it] }
            BottomPopup(
                visible = selNode != null,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = BottomBarSpace - 6.dp),
            ) {
                val n = selNode ?: return@BottomPopup
                NodeCard(
                    node = n,
                    path = pathTo(n, byId),
                    color = contactColor(n.contact, idx),
                    onOpen = { nav.open(Route.Detail(n.contact.id)) },
                    onCenter = if (n.level > 0) { { Session.networkRoot = n.contact.id; selected = null } } else null,
                )
            }
        }
    }

    if (picker) RootPicker(data, idx, onDismiss = { picker = false })
}

/** Slides a card in from the bottom (top-level, so it does not pick up an outer ColumnScope). */
@Composable
private fun BottomPopup(visible: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) { content() }
}

/** "Ich › Anna › Tom" and how the last step relates. */
private fun pathTo(n: NetNode, byId: Map<String, NetNode>): String {
    val names = ArrayList<String>()
    var cur: NetNode? = n
    while (cur != null) {
        names += Logic.shortName(cur.contact)
        cur = cur.parentId?.let { byId[it] }
    }
    return names.asReversed().joinToString(" › ")
}

@Composable
private fun NodeCard(node: NetNode, path: String, color: Color, onOpen: () -> Unit, onCenter: (() -> Unit)?) {
    val c = node.contact
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .glass(RoundedCornerShape(20.dp), tint = Space.PanelHigh, border = Space.LineStrong)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(c, color, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Logic.displayName(c, false), style = MaterialTheme.typography.titleMediumEmphasized, color = Space.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (c.isOrg) {
                        Spacer(Modifier.width(6.dp))
                        KindMark(c)
                    }
                }
                val via = node.via
                val role = if (via != null) "${NetworkLayout.roleOf(c.id, via)} · Ebene ${node.level}" else "Mitte"
                Text(role, style = MaterialTheme.typography.labelMedium, color = levelColors[node.level.coerceIn(0, 4)])
            }
        }
        if (node.level > 1) {
            Text(path, style = MaterialTheme.typography.labelMedium, color = Space.TextFaint, modifier = Modifier.padding(top = 6.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlowButton("Öffnen", onOpen, icon = Icons.AutoMirrored.Rounded.OpenInNew)
            if (onCenter != null) GhostButton("Als Mitte", onCenter, icon = Icons.Rounded.Hub)
        }
    }
}

@Composable
private fun RootPicker(data: OrbitData, idx: Index, onDismiss: () -> Unit) {
    val search = rememberTextFieldState()
    val q = search.text.toString()
    val linked = remember(data) { data.relations.flatMap { listOf(it.fromId, it.toId) }.groupingBy { it }.eachCount() }
    val list = remember(data, q) {
        Logic.active(data)
            .filter { !it.archived && Logic.matches(it, q, idx) }
            .sortedWith(compareByDescending<Contact> { it.isMe }.thenByDescending { linked[it.id] ?: 0 }.thenBy { Logic.sortKey(it, false) })
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Space.PanelHigh,
        title = { Text("Wer steht in der Mitte?", style = MaterialTheme.typography.titleLargeEmphasized) },
        text = {
            Column {
                SpaceSearchField(search, "Suchen", Modifier.fillMaxWidth(), leading = Icons.Rounded.Search)
                LazyColumn(Modifier.heightIn(max = 340.dp).padding(top = 8.dp)) {
                    items(list, key = { it.id }) { c ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    Session.networkRoot = if (c.isMe) null else c.id
                                    onDismiss()
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(c, contactColor(c, idx), 32.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(Logic.displayName(c, false), style = MaterialTheme.typography.bodyMedium, color = Space.Text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val n = linked[c.id] ?: 0
                            if (n > 0) Text("$n", style = MaterialTheme.typography.labelMedium, color = Space.TextFaint)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}
