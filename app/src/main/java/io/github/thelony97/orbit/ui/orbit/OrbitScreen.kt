package io.github.thelony97.orbit.ui.orbit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.domain.DueState
import io.github.thelony97.orbit.domain.Index
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.domain.today
import io.github.thelony97.orbit.ui.Nav
import io.github.thelony97.orbit.ui.Route
import io.github.thelony97.orbit.ui.components.Avatar
import io.github.thelony97.orbit.ui.components.EmptyHint
import io.github.thelony97.orbit.ui.components.contactColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val MAX_RATIO = 1.5f

private data class Body(
    val contact: Contact,
    val color: Color,
    /** Days since last contact divided by the rhythm; 1.0 = due. */
    val ratio: Float,
    val hasRhythm: Boolean,
    val due: Boolean,
    val baseAngle: Float,
    val speed: Float,
)

@Composable
fun OrbitScreen(data: OrbitData, idx: Index, nav: Nav) {
    var groupFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val todayDate = today()
    val bodies = remember(data, groupFilter) {
        val people = Logic.active(data)
            .filterNot { it.archived }
            .filter { groupFilter == null || groupFilter in it.groupIds }
            .sortedBy { it.id.hashCode() }
        people.mapIndexed { i, c ->
            val info = Logic.due(c, idx, todayDate)
            val rhythm = info.rhythm ?: 30
            val since = (info.daysSinceReset ?: 0L).toFloat()
            val ratio = (since / rhythm).coerceIn(0f, MAX_RATIO)
            Body(
                contact = c,
                color = contactColor(c, idx),
                ratio = ratio,
                hasRhythm = info.rhythm != null && !c.paused,
                due = info.state == DueState.DUE,
                baseAngle = i * 360f / people.size.coerceAtLeast(1) + (c.id.hashCode() % 17),
                speed = 1.4f - ratio / MAX_RATIO,
            )
        }
    }

    var seconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> seconds = (now - start) / 1_000_000_000f }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Orbit") },
            subtitle = { Text("Innen frisch · außen lange her") },
        )
        if (data.groups.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = groupFilter == null, onClick = { groupFilter = null }, label = { Text("Alle") })
                data.groups.forEach { g ->
                    FilterChip(
                        selected = groupFilter == g.id,
                        onClick = { groupFilter = if (groupFilter == g.id) null else g.id },
                        label = { Text(g.name) },
                        leadingIcon = { Box(Modifier.size(10.dp).background(Color(g.color), CircleShape)) },
                    )
                }
            }
        }

        if (bodies.isEmpty()) {
            EmptyHint("Noch niemand im Orbit", "Sobald du Kontakte anlegst, kreisen sie hier um dich.")
            return@Column
        }

        val ringColor = MaterialTheme.colorScheme.outlineVariant
        val dueColor = MaterialTheme.colorScheme.error
        val primary = MaterialTheme.colorScheme.primary
        val density = LocalDensity.current

        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val avatar = if (bodies.size > 40) 34.dp else 44.dp
            val avatarPx = with(density) { avatar.toPx() }
            val labelPx = with(density) { 16.dp.toPx() }
            val w = constraints.maxWidth.toFloat()
            val h = constraints.maxHeight.toFloat()
            val cx = w / 2f
            val cy = h / 2f
            val maxR = min(w, h) / 2f - avatarPx / 2f - labelPx
            val minR = maxR * 0.26f
            fun radius(ratio: Float) = minR + (maxR - minR) * (ratio / MAX_RATIO)

            Canvas(Modifier.fillMaxSize()) {
                val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 12f))
                drawCircle(ringColor, radius(0.5f), Offset(cx, cy), style = Stroke(1.dp.toPx(), pathEffect = dash))
                drawCircle(dueColor.copy(alpha = 0.55f), radius(1f), Offset(cx, cy), style = Stroke(1.5.dp.toPx(), pathEffect = dash))
                drawCircle(ringColor, radius(MAX_RATIO), Offset(cx, cy), style = Stroke(1.dp.toPx(), pathEffect = dash))
                drawCircle(primary.copy(alpha = 0.06f), radius(1f), Offset(cx, cy))
            }

            // Ring label for the "due" line.
            Text(
                "fällig",
                style = MaterialTheme.typography.labelSmall,
                color = dueColor,
                modifier = Modifier.offset { IntOffset((cx + 6).roundToInt(), (cy - radius(1f) - 18).roundToInt()) },
            )

            // You, in the middle.
            val centerSize = 64.dp
            val centerPx = with(density) { centerSize.toPx() }
            Box(
                modifier = Modifier
                    .offset { IntOffset((cx - centerPx / 2).roundToInt(), (cy - centerPx / 2).roundToInt()) }
                    .size(centerSize)
                    .graphicsLayer { rotationZ = seconds * 6f }
                    .background(MaterialTheme.colorScheme.primaryContainer, MaterialShapes.Sunny.toShape()),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Du",
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.graphicsLayer { rotationZ = -seconds * 6f },
                )
            }

            val labelWidth = avatar + 28.dp
            val labelWidthPx = with(density) { labelWidth.toPx() }
            bodies.forEach { b ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .offset {
                            val r = radius(b.ratio)
                            val a = (b.baseAngle + seconds * 2.2f * b.speed) * (PI.toFloat() / 180f)
                            IntOffset(
                                (cx + r * cos(a) - labelWidthPx / 2).roundToInt(),
                                (cy + r * sin(a) - avatarPx / 2).roundToInt(),
                            )
                        }
                        .width(labelWidth)
                        .alpha(if (b.hasRhythm) 1f else 0.6f)
                        .clickable { nav.open(Route.Detail(b.contact.id)) },
                ) {
                    Avatar(b.contact, b.color, avatar, ring = if (b.due) dueColor else null)
                    Text(
                        b.contact.firstName.ifBlank { Logic.displayName(b.contact, false) },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        val dueCount = bodies.count { it.due }
        Text(
            if (dueCount == 0) "Alle innerhalb ihres Rhythmus ✨" else "$dueCount außerhalb der roten Linie – Zeit, sich zu melden",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}
