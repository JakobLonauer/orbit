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
import kotlin.math.roundToInt
import io.github.thelony97.orbit.core.designsystem.Spacing
import io.github.thelony97.orbit.core.designsystem.Corners
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Business
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.AnimatedContent
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

/** Shared key of one number: its row in the drawer and the blocks of the dictation. */
private fun keyOf(item: MyNumbers.Item) = "n:" + item.owner + ":" + item.label + ":" + item.value

/** The row's value grows into the dictation's blocks and back (PLAN W3). */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun Modifier.sharedValue(key: String, shared: SharedTransitionScope, visibility: AnimatedVisibilityScope): Modifier = with(shared) {
    this@sharedValue.sharedBounds(
        rememberSharedContentState(key),
        animatedVisibilityScope = visibility,
        boundsTransform = { _, _ -> tween(Motion.EMPHASIZED_MS, easing = Motion.Emphasized) },
        resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
    )
}

/**
 * [preview]: fixed numbers for screenshot tests instead of the stored ones. Tap copies (marked
 * sensitive) and the drawer leaves with a tick; holding a number lets its value grow into the
 * Diktier-Ansicht, back brings it home; the handle pulls the drawer down.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun NumbersDrawer(onClose: () -> Unit, preview: List<MyNumbers.Item>? = null) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val items by produceState(preview) { if (preview == null) value = MyNumbers.of(OrbitRepository.snapshot()) }
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
            // The tick is seen, then the drawer goes.
            delay(Motion.EMPHASIZED_MS.toLong())
            close()
        }
    }

    SharedTransitionLayout {
        AnimatedContent(
            dictate,
            transitionSpec = { fadeIn(tween(Motion.MEDIUM_MS)) togetherWith fadeOut(tween(Motion.FAST_MS)) },
            label = "numbers",
        ) { d ->
            if (d != null) {
                DictationView(
                    listOfNotNull(d.owner, d.label).joinToString(" · "),
                    d.value,
                    onClose = { dictate = null },
                    blocksModifier = Modifier.sharedValue(keyOf(d), this@SharedTransitionLayout, this@AnimatedContent),
                )
            } else {
                Drawer(
                    items = items,
                    shown = shown,
                    copied = copied,
                    onClose = ::close,
                    onCopy = { item ->
                        val cm = ctx.getSystemService(ClipboardManager::class.java)
                        val clip = ClipData.newPlainText(item.label, item.value)
                        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
                        cm?.setPrimaryClip(clip)
                        haptics.perform(Haptic.Confirm)
                        copied = item
                    },
                    onDictate = { item ->
                        haptics.perform(Haptic.Threshold)
                        dictate = item
                    },
                    onAddMine = {
                        val me = OrbitRepository.data.value?.contacts?.firstOrNull { it.isMe }
                        ctx.startActivity(
                            Intent(ctx, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                .apply { if (me != null) putExtra(MainActivity.EXTRA_CONTACT, me.id) },
                        )
                        onClose()
                    },
                    valueModifier = { item -> Modifier.sharedValue(keyOf(item), this@SharedTransitionLayout, this@AnimatedContent) },
                )
            }
        }
    }
}

@Composable
private fun Drawer(
    items: List<MyNumbers.Item>?,
    shown: MutableTransitionState<Boolean>,
    copied: MyNumbers.Item?,
    onClose: () -> Unit,
    onCopy: (MyNumbers.Item) -> Unit,
    onDictate: (MyNumbers.Item) -> Unit,
    onAddMine: () -> Unit,
    valueModifier: @Composable (MyNumbers.Item) -> Modifier,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val scope = rememberCoroutineScope()
    val pull = remember { Animatable(0f) }
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
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
                    .offset { IntOffset(0, pull.value.roundToInt()) }
                    .glass(Sheet, tint = Space.PanelHigh)
                    // Taps inside the drawer never close it.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    .navigationBarsPadding()
                    .padding(bottom = Spacing.S),
            ) {
                // The handle: pull down to close, it springs back when let go early.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(Spacing.XL)
                        .draggable(
                            rememberDraggableState { delta -> scope.launch { pull.snapTo((pull.value + delta).coerceAtLeast(0f)) } },
                            Orientation.Vertical,
                            onDragStopped = { velocity ->
                                if (pull.value > with(density) { 96.dp.toPx() } || velocity > 1_800f) onClose()
                                else pull.animateTo(0f, Motion.snappy())
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(50)).background(Space.LineStrong))
                }
                when {
                    items == null -> Spacer(Modifier.height(120.dp))
                    items.isEmpty() -> Column(Modifier.fillMaxWidth().padding(Spacing.L), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Keine eigenen Nummern", style = MaterialTheme.typography.titleMedium, color = Space.Text)
                        Spacer(Modifier.height(Spacing.M))
                        GlowButton("Bei Ich anlegen", onAddMine)
                    }
                    else -> Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                        items.groupBy { it.owner }.forEach { (owner, group) ->
                            GroupHead(owner)
                            group.forEach { item ->
                                NumberRow(
                                    item = item,
                                    copied = copied == item,
                                    onCopy = { onCopy(item) },
                                    onDictate = { onDictate(item) },
                                    valueModifier = valueModifier(item),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** „Ich“ or the service the numbers belong to, with its planet. */
@Composable
private fun GroupHead(owner: String?) {
    val color = if (owner == null) Space.Orange else Space.Blue
    Row(
        Modifier.padding(start = Spacing.L, end = Spacing.L, top = Spacing.S, bottom = Spacing.XS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(20.dp).background(color.copy(alpha = 0.22f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(if (owner == null) Icons.Rounded.Person else Icons.Rounded.Business, null, tint = color, modifier = Modifier.size(12.dp))
        }
        Spacer(Modifier.width(Spacing.S))
        Text((owner ?: "Ich").uppercase(), style = MaterialTheme.typography.labelMedium, color = Space.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun NumberRow(item: MyNumbers.Item, copied: Boolean, onCopy: () -> Unit, onDictate: () -> Unit, valueModifier: Modifier) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.S)
            .clip(Corners.Row)
            .combinedClickable(onClick = onCopy, onLongClick = onDictate)
            .padding(horizontal = Spacing.S, vertical = Spacing.S),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.label, style = MaterialTheme.typography.labelMedium, color = Space.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                item.value,
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum, zero"),
                color = Space.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = valueModifier,
            )
        }
        // Copy turns into a tick in place (no toast).
        AnimatedContent(
            copied,
            transitionSpec = {
                (fadeIn(tween(Motion.FAST_MS)) + scaleIn(tween(Motion.FAST_MS), initialScale = 0.6f)) togetherWith fadeOut(tween(Motion.FAST_MS))
            },
            label = "copied",
        ) { done ->
            Icon(
                if (done) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                null,
                tint = if (done) Space.Teal else Space.TextDim,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Sheets are rounded at the top only and sit on the screen's edge. */
private val Sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
