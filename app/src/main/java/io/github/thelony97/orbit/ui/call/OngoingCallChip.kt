package io.github.thelony97.orbit.ui.call

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thelony97.orbit.call.CallActivity
import io.github.thelony97.orbit.call.CallSession
import io.github.thelony97.orbit.ui.components.glow
import io.github.thelony97.orbit.ui.theme.Space
import kotlinx.coroutines.delay

/**
 * Laufender Anruf als Chip (PLAN 3.12): while Orbit is the phone app and a call runs, a small
 * pill at the top of Orbit leads back to the call screen. Nothing shows without a call.
 */
@Composable
fun OngoingCallChip(modifier: Modifier = Modifier) {
    val calls by CallSession.calls.collectAsStateWithLifecycle()
    val version by CallSession.version.collectAsStateWithLifecycle()
    val cards by CallSession.cards.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val call = remember(calls, version) { CallSession.primary() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(call) {
        while (call != null) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    AnimatedVisibility(
        visible = call != null,
        modifier = modifier,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f),
    ) {
        val c = call ?: return@AnimatedVisibility
        val number = CallSession.numberOf(c)
        val name = cards[number]?.name?.takeIf { it.isNotBlank() } ?: number.ifBlank { "Anruf" }
        val connectedAt = runCatching { c.details.connectTimeMillis }.getOrDefault(0L)
        val sec = if (connectedAt > 0) ((now - connectedAt) / 1000).coerceAtLeast(0) else -1
        val time = if (sec >= 0) "%d:%02d".format(sec / 60, sec % 60) else "…"
        Row(
            Modifier
                .glow(Space.Teal, 12.dp, RoundedCornerShape(50), 0.45f)
                .clip(RoundedCornerShape(50))
                .background(Space.Teal)
                .clickable { runCatching { ctx.startActivity(CallActivity.intent(ctx)) } }
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Call, null, tint = Space.Void, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "$name · $time",
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                color = Space.Void,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
