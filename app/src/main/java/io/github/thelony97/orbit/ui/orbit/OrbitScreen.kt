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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
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
import io.github.thelony97.orbit.ui.components.GlowPill
import io.github.thelony97.orbit.ui.components.SpaceChip
import io.github.thelony97.orbit.ui.components.contactColor
import io.github.thelony97.orbit.ui.components.glow
import io.github.thelony97.orbit.ui.home.BottomBarSpace
import io.github.thelony97.orbit.ui.theme.Space
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

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Orbit", style = MaterialTheme.typography.headlineSmallEmphasized, color = Space.Text)
                Text("innen frisch · außen lange her", style = MaterialTheme.typography.labelMedium, color = Space.TextFaint)
            }
            val dueCount = bodies.count { it.due }
            if (dueCount > 0) GlowPill("$dueCount fällig", Space.Coral)
        }
        if (data.groups.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SpaceChip("Alle", groupFilter == null, { groupFilter = null })
                data.groups.forEach { g ->
                    SpaceChip(
                        g.name, groupFilter == g.id,
                        { groupFilter = if (groupFilter == g.id) null else g.id },
                        dot = Color(g.color), accent = Color(g.color),
                    )
                }
            }
        }

        if (bodies.isEmpty()) {
            EmptyHint("Noch niemand im Orbit", "Sobald du Kontakte anlegst, kreisen sie hier um dich.")
            return@Column
        }

        val density = LocalDensity.current
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = BottomBarSpace - 20.dp),
        ) {
            val avatar = when {
                bodies.size > 50 -> 26.dp
                bodies.size > 25 -> 30.dp
                else -> 36.dp
            }
            val avatarPx = with(density) { avatar.toPx() }
            val labelPx = with(density) { 14.dp.toPx() }
            val w = constraints.maxWidth.toFloat()
            val h = constraints.maxHeight.toFloat()
            val cx = w / 2f
            val cy = h / 2f
            val maxR = min(w, h) / 2f - avatarPx / 2f - labelPx
            val minR = maxR * 0.28f
            fun radius(ratio: Float) = minR + (maxR - minR) * (ratio / MAX_RATIO)

            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(cx, cy)
                // Halo of the "habitable zone" inside the due line
                drawCircle(
                    Brush.radialGradient(
                        listOf(Space.Violet.copy(alpha = 0.10f), Space.Violet.copy(alpha = 0.02f), Color.Transparent),
                        center = center,
                        radius = radius(1f),
                    ),
                    radius = radius(1f),
                    center = center,
                )
                val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 10f))
                drawCircle(Space.Line, radius(0.5f), center, style = Stroke(1.dp.toPx(), pathEffect = dash))
                drawCircle(Space.Coral.copy(alpha = 0.6f), radius(1f), center, style = Stroke(1.2.dp.toPx(), pathEffect = dash))
                drawCircle(Space.Line, radius(MAX_RATIO), center, style = Stroke(1.dp.toPx(), pathEffect = dash))
            }

            Text(
                "fällig",
                style = MaterialTheme.typography.labelSmall,
                color = Space.Coral,
                modifier = Modifier.offset { IntOffset((cx + 6).roundToInt(), (cy - radius(1f) - 16).roundToInt()) },
            )

            // The sun: you.
            val sun = 54.dp
            val sunPx = with(density) { sun.toPx() }
            Box(
                modifier = Modifier
                    .offset { IntOffset((cx - sunPx / 2).roundToInt(), (cy - sunPx / 2).roundToInt()) }
                    .size(sun)
                    .graphicsLayer {
                        val pulse = 1f + 0.04f * sin(seconds * 1.6f)
                        scaleX = pulse
                        scaleY = pulse
                    }
                    .glow(Space.Orange, 28.dp, CircleShape, 0.75f)
                    .background(
                        Brush.radialGradient(listOf(Color(0xFFFFE2B8), Space.Orange, Color(0xFFE07A2E))),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text("Du", style = MaterialTheme.typography.titleSmallEmphasized, color = Color(0xFF3A1E00))
            }

            val labelWidth = avatar + 26.dp
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
                        .alpha(if (b.hasRhythm) 1f else 0.55f)
                        .clickable { nav.open(Route.Detail(b.contact.id)) },
                ) {
                    Avatar(b.contact, b.color, avatar, glowColor = if (b.due) Space.Coral else null)
                    Text(
                        b.contact.firstName.ifBlank { Logic.displayName(b.contact, false) },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (b.due) Space.Coral else Space.TextDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
