package io.github.thelony97.orbit.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.ui.theme.Space
import kotlin.random.Random

// ---------------------------------------------------------------- modifiers

/** Soft coloured light around a shape. */
fun Modifier.glow(color: Color, radius: Dp = 14.dp, shape: Shape = CircleShape, alpha: Float = 0.55f): Modifier =
    dropShadow(shape, Shadow(radius = radius, color = color, alpha = alpha))

/** Frosted panel: translucent fill with a hairline border. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(20.dp), tint: Color = Space.Panel, border: Color = Space.Line): Modifier =
    clip(shape)
        .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.92f), tint.copy(alpha = 0.78f))))
        .border(1.dp, border, shape)

// ---------------------------------------------------------------- background

/** Night sky with a few stars and two faint nebulae – drawn once, no animation. */
@Composable
fun SpaceBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val stars = remember {
        val r = Random(42)
        List(110) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) }
    }
    Box(modifier.fillMaxSize().background(Space.Void)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.radialGradient(
                    listOf(Space.VioletDeep.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(size.width * 0.15f, size.height * 0.05f),
                    radius = size.maxDimension * 0.55f,
                ),
            )
            drawRect(
                Brush.radialGradient(
                    listOf(Space.Orange.copy(alpha = 0.08f), Color.Transparent),
                    center = Offset(size.width * 0.95f, size.height * 0.75f),
                    radius = size.maxDimension * 0.5f,
                ),
            )
            stars.forEach { (x, y, s) ->
                drawCircle(
                    Color.White.copy(alpha = 0.10f + s * 0.35f),
                    radius = 0.6f + s * 1.3f,
                    center = Offset(x * size.width, y * size.height),
                )
            }
        }
        content()
    }
}

// ---------------------------------------------------------------- building blocks

/** Glass card for sections. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    padding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .glass(RoundedCornerShape(20.dp))
            .padding(padding),
        content = content,
    )
}

/** Small uppercase section title with an optional action on the right. */
@Composable
fun SectionTitle(
    title: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    color: Color = Space.Violet,
    action: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = Space.TextDim,
            modifier = Modifier.weight(1f),
        )
        action?.invoke(this)
    }
}

/** Compact pill chip (28 dp) used for filters and choices. */
@Composable
fun SpaceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dot: Color? = null,
    leading: ImageVector? = null,
    accent: Color = Space.Violet,
    trailing: ImageVector? = null,
) {
    val bg by animateColorAsState(if (selected) accent.copy(alpha = 0.20f) else Space.Panel, label = "chipBg")
    val border by animateColorAsState(if (selected) accent.copy(alpha = 0.75f) else Space.Line, label = "chipBorder")
    Row(
        modifier = modifier
            .height(30.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot != null) Box(Modifier.size(7.dp).background(dot, CircleShape))
        if (leading != null) Icon(leading, null, tint = if (selected) accent else Space.TextDim, modifier = Modifier.size(14.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Space.Text else Space.TextDim,
            maxLines = 1,
        )
        if (trailing != null) Icon(trailing, null, tint = if (selected) accent else Space.TextDim, modifier = Modifier.size(14.dp))
    }
}

/** Round glass button with a small counter badge (used for the filter button). */
@Composable
fun BadgeOrb(icon: ImageVector, contentDescription: String, count: Int, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 42.dp) {
    Box(modifier) {
        OrbButton(icon, contentDescription, onClick, size = size, accent = if (count > 0) Space.Violet else null)
        if (count > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(17.dp)
                    .glow(Space.Orange, 6.dp)
                    .clip(CircleShape)
                    .background(Space.Orange),
                contentAlignment = Alignment.Center,
            ) {
                Text("$count", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Space.Void)
            }
        }
    }
}

/** Glowing status pill. */
@Composable
fun GlowPill(text: String, color: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Row(
        modifier = modifier
            .glow(color, radius = 10.dp, shape = RoundedCornerShape(50), alpha = 0.45f)
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(50))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).glow(color, 4.dp).background(color, CircleShape))
        Text(text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

/** Single-line glass text field, 42 dp high. */
@Composable
fun SpaceSearchField(
    state: TextFieldState,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    onSubmit: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    BasicTextField(
        state = state,
        modifier = modifier.height(42.dp),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Space.Text),
        cursorBrush = SolidColor(Space.Violet),
        lineLimits = TextFieldLineLimits.SingleLine,
        onKeyboardAction = if (onSubmit != null) KeyboardActionHandler { onSubmit() } else null,
        decorator = { inner ->
            Row(
                Modifier
                    .fillMaxSize()
                    .glass(RoundedCornerShape(50), tint = Space.Panel)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leading != null) {
                    Icon(leading, null, tint = Space.TextDim, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Box(Modifier.weight(1f)) {
                    if (state.text.isEmpty()) {
                        Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = Space.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    inner()
                }
                if (trailing != null) {
                    trailing()
                } else if (state.text.isNotEmpty()) {
                    Icon(
                        Icons.Rounded.Close, "Leeren",
                        tint = Space.TextDim,
                        modifier = Modifier.size(18.dp).clip(CircleShape).clickable { state.clearText() },
                    )
                }
            }
        },
    )
}

/** Form field: small label above a glass input. */
@Composable
fun SpaceField(
    state: TextFieldState,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    placeholder: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    Column(modifier) {
        if (label.isNotEmpty()) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Space.TextDim, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        }
        BasicTextField(
            state = state,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Space.Text),
            cursorBrush = SolidColor(Space.Violet),
            keyboardOptions = keyboardOptions,
            lineLimits = if (singleLine) TextFieldLineLimits.SingleLine else TextFieldLineLimits.MultiLine(minHeightInLines = 2, maxHeightInLines = 8),
            decorator = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .glass(RoundedCornerShape(14.dp), tint = Space.Panel)
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (state.text.isEmpty() && placeholder.isNotEmpty()) {
                        Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = Space.TextFaint)
                    }
                    inner()
                }
            },
        )
    }
}

/** Round icon button on glass that tightens its corners while pressed. */
@Composable
fun OrbButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    enabled: Boolean = true,
    tint: Color = Space.Text,
    accent: Color? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(if (pressed) size / 4 else size / 2, MaterialTheme.motionScheme.fastSpatialSpec(), label = "orb")
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .size(size)
            .then(if (accent != null && enabled) Modifier.glow(accent, 12.dp, shape, 0.5f) else Modifier)
            .clip(shape)
            .background(if (accent != null && enabled) accent.copy(alpha = 0.22f) else Space.PanelHigh.copy(alpha = if (enabled) 1f else 0.5f))
            .border(1.dp, if (accent != null && enabled) accent.copy(alpha = 0.6f) else Space.Line, shape)
            .combinedClickable(
                enabled = enabled,
                interactionSource = interaction,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, contentDescription,
            tint = when {
                !enabled -> Space.TextFaint
                accent != null -> accent
                else -> tint
            },
            modifier = Modifier.size(size * 0.45f),
        )
    }
}

/** Primary pill button with a violet glow. */
@Composable
fun GlowButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Space.Violet, icon: ImageVector? = null, enabled: Boolean = true) {
    Row(
        modifier = modifier
            .height(38.dp)
            .then(if (enabled) Modifier.glow(color, 14.dp, RoundedCornerShape(50), 0.45f) else Modifier)
            .clip(RoundedCornerShape(50))
            .background(
                if (enabled) Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.78f)))
                else SolidColor(Space.PanelHigh),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = if (enabled) Space.Void else Space.TextFaint, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = if (enabled) Space.Void else Space.TextFaint)
    }
}

/** Secondary pill button: outline on glass. */
@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color = Space.Text) {
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(50))
            .background(Space.PanelHigh.copy(alpha = 0.7f))
            .border(1.dp, Space.LineStrong, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

/** Round check used in checklists. */
@Composable
fun CheckDot(checked: Boolean, onToggle: () -> Unit, color: Color = Space.Teal) {
    Box(
        Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (checked) color.copy(alpha = 0.25f) else Color.Transparent)
            .border(1.5.dp, if (checked) color else Space.TextFaint, CircleShape)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Icons.Rounded.Check, null, tint = color, modifier = Modifier.size(14.dp))
    }
}

/** Thin progress bar with a glowing fill. */
@Composable
fun GlowBar(progress: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)).background(Space.PanelHighest)) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0.02f, 1f))
                .height(5.dp)
                .glow(color, 6.dp, RoundedCornerShape(50), 0.8f)
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(Space.Violet, color))),
        )
    }
}

/** Big number with a tiny label, used in stat strips. */
@Composable
fun RowScope.Stat(value: String, label: String, color: Color = Space.Text, onClick: (() -> Unit)? = null) {
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 4.dp, horizontal = 2.dp),
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Space.TextFaint)
        Text(value, style = MaterialTheme.typography.titleLargeEmphasized, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
