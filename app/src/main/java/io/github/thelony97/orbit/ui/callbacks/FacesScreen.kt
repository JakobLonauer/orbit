package io.github.thelony97.orbit.ui.callbacks

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.core.designsystem.Haptic
import io.github.thelony97.orbit.core.designsystem.Motion
import io.github.thelony97.orbit.core.designsystem.Spacing
import io.github.thelony97.orbit.core.designsystem.rememberHaptics
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.ui.Nav
import io.github.thelony97.orbit.ui.Route
import io.github.thelony97.orbit.ui.components.OrbButton
import io.github.thelony97.orbit.ui.components.SpaceBackground
import io.github.thelony97.orbit.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val FRESH_DAYS = 60L

/** People added in the last two months who have a face – for the Lernkarten. */
fun newFaces(data: OrbitData, now: Long = System.currentTimeMillis()): List<Contact> = data.contacts
    .filter { it.deletedAt == null && !it.archived && !it.isMe && !it.isOrg && it.photo != null && now - it.createdAt < FRESH_DAYS * 86_400_000L }
    .sortedByDescending { it.createdAt }

/**
 * Neue Gesichter (PLAN 9 E10): Lernkarten of the people added lately – the face first, a tap
 * turns the card to the name and where you met; swipe for the next.
 */
@Composable
fun FacesScreen(data: OrbitData, nav: Nav, modifier: Modifier = Modifier) {
    val faces = remember { newFaces(data).shuffled() }
    val pager = rememberPagerState { faces.size }
    SpaceBackground(modifier) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.Gutter, vertical = Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                OrbButton(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück", { nav.back() })
                Spacer(Modifier.width(Spacing.M))
                Text("Neue Gesichter", style = MaterialTheme.typography.headlineSmallEmphasized, color = Space.Text, modifier = Modifier.weight(1f))
                if (faces.isNotEmpty()) Text("${pager.currentPage + 1} / ${faces.size}", style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), color = Space.TextDim)
            }
            HorizontalPager(pager, Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 32.dp), pageSpacing = 16.dp) { i ->
                FaceCard(faces[i], onOpen = { nav.open(Route.Detail(faces[i].id)) })
            }
        }
    }
}

@Composable
private fun FaceCard(c: Contact, onOpen: () -> Unit) {
    val haptics = rememberHaptics()
    var turned by remember { mutableStateOf(false) }
    val turn by animateFloatAsState(if (turned) 180f else 0f, tween(Motion.MEDIUM_MS, easing = Motion.Emphasized), label = "turn")
    val bmp by produceState<android.graphics.Bitmap?>(null, c.photo) {
        value = withContext(Dispatchers.IO) { c.photo?.let { android.graphics.BitmapFactory.decodeFile(OrbitRepository.photoFile(it).path) } }
    }
    Box(
        Modifier.fillMaxWidth().aspectRatio(0.75f).padding(vertical = 24.dp)
            .graphicsLayer { rotationY = turn; cameraDistance = 12f * density }
            .clip(RoundedCornerShape(28.dp))
            .clickable { haptics.perform(Haptic.Tick); if (turned) onOpen() else turned = true },
        contentAlignment = Alignment.Center,
    ) {
        if (turn <= 90f) {
            bmp?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        } else {
            Column(
                Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }.padding(24.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(Logic.displayName(c, false), style = MaterialTheme.typography.headlineMediumEmphasized, color = Space.Text, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                if (c.howWeMet.isNotBlank()) Text(c.howWeMet, style = MaterialTheme.typography.bodyLarge, color = Space.Violet, textAlign = TextAlign.Center)
                if (c.notes.isNotBlank()) Text(c.notes, style = MaterialTheme.typography.bodyMedium, color = Space.TextDim, textAlign = TextAlign.Center, maxLines = 4)
            }
        }
    }
}
