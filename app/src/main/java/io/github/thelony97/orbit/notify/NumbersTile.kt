package io.github.thelony97.orbit.notify

import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.MainActivity
import io.github.thelony97.orbit.core.designsystem.Haptic
import io.github.thelony97.orbit.core.designsystem.Motion
import io.github.thelony97.orbit.core.designsystem.rememberHaptics
import io.github.thelony97.orbit.core.domain.MyNumbers
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.ui.components.DictationView
import io.github.thelony97.orbit.ui.components.GlowButton
import io.github.thelony97.orbit.ui.components.glass
import io.github.thelony97.orbit.ui.theme.OrbitTheme
import io.github.thelony97.orbit.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Nummern-Schublade (PLAN 9 A9): a quick-settings tile that slides Jakob's numbers up over any
 * app – tap copies, hold dictates.
 */
class NumbersTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.let { it.state = Tile.STATE_ACTIVE; it.updateTile() }
    }

    override fun onClick() {
        super.onClick()
        val go = {
            val intent = Intent(this, NumbersActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivityAndCollapse(PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        // The numbers are private: only on an unlocked phone.
        if (isLocked) unlockAndRun { go() } else go()
    }
}

class NumbersActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        OrbitRepository.init(applicationContext)
        setContent { OrbitTheme { NumbersDrawer(onClose = { finish() }) } }
    }

    override fun finish() {
        super.finish()
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
    }
}

@Composable
private fun NumbersDrawer(onClose: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val items by produceState<List<MyNumbers.Item>?>(null) { value = MyNumbers.of(OrbitRepository.snapshot()) }
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    var copied by remember { mutableStateOf<MyNumbers.Item?>(null) }
    var dictate by remember { mutableStateOf<MyNumbers.Item?>(null) }

    fun close() {
        shown.targetState = false
        scope.launch {
            delay(Motion.MEDIUM_MS.toLong())
            onClose()
        }
    }
    LaunchedEffect(copied) {
        if (copied != null) {
            delay(450)
            close()
        }
    }

    dictate?.let { d ->
        DictationView(d.label, d.value, onClose = { dictate = null })
        return
    }
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(shown, enter = fadeIn(tween(Motion.MEDIUM_MS)), exit = fadeOut(tween(Motion.FAST_MS))) {
            Box(Modifier.fillMaxSize().background(Space.Void.copy(alpha = 0.55f)))
        }
        AnimatedVisibility(
            shown,
            enter = slideInVertically(tween(Motion.MEDIUM_MS, easing = Motion.Emphasized)) { it } + fadeIn(tween(Motion.FAST_MS)),
            exit = slideOutVertically(tween(Motion.FAST_MS, easing = Motion.Exit)) { it } + fadeOut(tween(Motion.FAST_MS)),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .navigationBarsPadding()
                    .glass(RoundedCornerShape(28.dp), tint = Space.PanelHigh)
                    // Taps inside the drawer never close it.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    .padding(vertical = 12.dp),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(50)).background(Space.LineStrong))
                Spacer(Modifier.height(10.dp))
                val list = items
                when {
                    list == null -> Spacer(Modifier.height(120.dp))
                    list.isEmpty() -> Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Keine eigenen Nummern", style = MaterialTheme.typography.titleMedium, color = Space.Text)
                        Spacer(Modifier.height(12.dp))
                        GlowButton("Bei Ich anlegen", {
                            val me = OrbitRepository.data.value?.contacts?.firstOrNull { it.isMe }
                            ctx.startActivity(
                                Intent(ctx, MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    .apply { if (me != null) putExtra(MainActivity.EXTRA_CONTACT, me.id) },
                            )
                            onClose()
                        })
                    }
                    else -> Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                        var lastOwner: String? = "\u0000"
                        list.forEach { item ->
                            if (item.owner != lastOwner) {
                                lastOwner = item.owner
                                Text(
                                    (item.owner ?: "Ich").uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Space.TextDim,
                                    modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 2.dp),
                                )
                            }
                            NumberRow(
                                item = item,
                                copied = copied == item,
                                onCopy = {
                                    val cm = ctx.getSystemService(ClipboardManager::class.java)
                                    val clip = ClipData.newPlainText(item.label, item.value)
                                    clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
                                    cm?.setPrimaryClip(clip)
                                    haptics.perform(Haptic.Confirm)
                                    copied = item
                                },
                                onDictate = {
                                    haptics.perform(Haptic.Threshold)
                                    dictate = item
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberRow(item: MyNumbers.Item, copied: Boolean, onCopy: () -> Unit, onDictate: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onCopy, onLongClick = onDictate)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.label, style = MaterialTheme.typography.labelMedium, color = Space.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.value, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), color = Space.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(
            if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
            null,
            tint = if (copied) Space.Teal else Space.TextDim,
            modifier = Modifier.size(20.dp),
        )
    }
}
