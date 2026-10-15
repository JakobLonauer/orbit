package io.github.thelony97.orbit.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thelony97.orbit.core.domain.Dictation
import io.github.thelony97.orbit.ui.theme.Space

/**
 * Diktier-Ansicht (PLAN 9 B7/A9): a number to read out on the phone – huge blocks, the letters
 * spelled with the Austrian alphabet, the screen at full brightness and kept on. Any tap closes.
 */
@Composable
fun DictationView(label: String, value: String, onClose: () -> Unit) {
    val view = LocalView.current
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
    val blocks = remember(value) { Dictation.blocks(value) }
    val spelled = remember(value) { Dictation.spelled(value).distinct() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Space.Void)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelLarge, color = Space.TextDim)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                blocks.forEach { b ->
                    Text(
                        b,
                        style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp),
                        color = Space.Text,
                    )
                }
            }
            if (spelled.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    spelled.forEach { (ch, word) ->
                        Text("$ch wie $word", style = MaterialTheme.typography.titleMedium, color = Space.Teal)
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
