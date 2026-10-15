package io.github.thelony97.orbit.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thelony97.orbit.core.designsystem.Haptic
import io.github.thelony97.orbit.core.designsystem.Motion
import io.github.thelony97.orbit.core.designsystem.Spacing
import io.github.thelony97.orbit.core.designsystem.rememberHaptics
import io.github.thelony97.orbit.core.domain.Dictation
import io.github.thelony97.orbit.ui.theme.Space

/**
 * Diktier-Ansicht (PLAN 9 B7/A9): a number to read out on the phone – huge blocks in its own
 * grouping, the screen at full brightness and kept on. A teal marker (= läuft) sits on the block
 * being read; each tap moves it on (Takt) and shows that block's letters with the Austrian
 * spelling alphabet. After the last block a tap closes; back always does. [blocksModifier] lets
 * the caller morph its row into the blocks (shared bounds).
 */
@Composable
fun DictationView(label: String, value: String, onClose: () -> Unit, blocksModifier: Modifier = Modifier) {
    val view = LocalView.current
    val haptics = rememberHaptics()
    // Full brightness and screen on while it shows; back to normal afterwards.
    DisposableEffect(Unit) {
        val window = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window ?: view.context.findActivity()?.window
        val before = window?.attributes?.screenBrightness
        window?.let { w ->
            w.attributes = w.attributes.apply { screenBrightness = 1f }
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.let { w ->
                w.attributes = w.attributes.apply { screenBrightness = before ?: android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
                w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
    BackHandler(onBack = onClose)
    val blocks = remember(value) { Dictation.pieces(value) }
    var at by remember(value) { mutableIntStateOf(0) }
    val blockStyle = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum, zero", fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
    Box(
        Modifier
            .fillMaxSize()
            .background(Space.Void)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (at < blocks.lastIndex) {
                    at++
                    haptics.perform(Haptic.Segment)
                } else {
                    haptics.perform(Haptic.Tick)
                    onClose()
                }
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(Spacing.XL),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.XL)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelLarge, color = Space.TextDim)
            FlowRow(
                blocksModifier,
                horizontalArrangement = Arrangement.spacedBy(Spacing.L, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(Spacing.M),
            ) {
                blocks.forEachIndexed { i, b ->
                    val color by animateColorAsState(if (i < at) Space.TextFaint else Space.Text, tween(Motion.FAST_MS), label = "block")
                    val marker by animateFloatAsState(if (i == at) 1f else 0f, tween(Motion.MEDIUM_MS, easing = Motion.Emphasized), label = "marker")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            b.text,
                            style = blockStyle,
                            color = color,
                            modifier = Modifier.drawBehind {
                                if (marker > 0f) {
                                    // The marker grows from the middle under the block being read.
                                    val w = size.width * marker
                                    val h = 4.dp.toPx()
                                    drawRoundRect(
                                        Space.Teal.copy(alpha = marker),
                                        topLeft = Offset((size.width - w) / 2f, size.height + 2.dp.toPx()),
                                        size = Size(w, h),
                                        cornerRadius = CornerRadius(h / 2f),
                                    )
                                }
                            },
                        )
                        b.after?.let { sign ->
                            Spacer(Modifier.width(Spacing.S))
                            Text(if (sign == '-') "–" else sign.toString(), style = blockStyle, color = Space.TextFaint)
                        }
                    }
                }
            }
            // The letters of the block being read, spelled; nothing when it has none (W5).
            val letters = remember(value, at) { blocks.getOrNull(at)?.let { Dictation.spelled(it.text).distinct() }.orEmpty() }
            Box(Modifier.height(64.dp), contentAlignment = Alignment.TopCenter) {
                AnimatedContent(
                    letters,
                    transitionSpec = { fadeIn(tween(Motion.MEDIUM_MS)) togetherWith fadeOut(tween(Motion.FAST_MS)) },
                    label = "letters",
                ) { list ->
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.M, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(Spacing.XS),
                    ) {
                        list.forEach { (ch, word) ->
                            Row(
                                Modifier
                                    .background(Space.Panel, RoundedCornerShape(50))
                                    .padding(horizontal = Spacing.M, vertical = Spacing.XS),
                            ) {
                                Text("$ch ", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = Space.Text)
                                Text("wie $word", style = MaterialTheme.typography.titleMedium, color = Space.TextDim)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The activity behind a (wrapped) context, if any. */
tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
