package io.github.thelony97.orbit.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Domain
import androidx.compose.material.icons.rounded.EmojiPeople
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LocalBar
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.Defaults
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.domain.Index
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.ui.theme.Grotesk
import io.github.thelony97.orbit.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PhotoCache {
    private val cache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun get(name: String): ImageBitmap? = cache.get(name)

    suspend fun load(name: String): ImageBitmap? = cache.get(name) ?: withContext(Dispatchers.IO) {
        val file = OrbitRepository.photoFile(name)
        if (!file.exists()) return@withContext null
        BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()?.also { cache.put(name, it) }
    }

    fun evict(name: String) {
        cache.remove(name)
    }
}

@Composable
fun rememberPhoto(name: String?): ImageBitmap? {
    val bitmap by produceState(initialValue = name?.let { PhotoCache.get(it) }, name) {
        value = name?.let { PhotoCache.load(it) }
    }
    return bitmap
}

private val fallbackPalette = listOf(
    Color(0xFFA78BFF), Color(0xFF5EE6CF), Color(0xFFFFB45E), Color(0xFF6FB7FF),
    Color(0xFFFF8FB1), Color(0xFF9BE37A), Color(0xFFFFD166),
)

/** Stable colour per contact: "Ich" is the sun, organisations are blue, people take their first group colour. */
fun contactColor(c: Contact, idx: Index): Color {
    if (c.isMe) return Space.Orange
    if (c.isOrg) return c.groupIds.firstNotNullOfOrNull { idx.groups[it] }?.let { Color(it.color) } ?: Space.Blue
    c.groupIds.firstNotNullOfOrNull { idx.groups[it] }?.let { return Color(it.color) }
    return fallbackPalette[(c.id.hashCode() and 0x7FFFFFFF) % fallbackPalette.size]
}

fun orgIcon(key: String?): ImageVector = when (Defaults.orgCategory(key).icon) {
    "work" -> Icons.Rounded.Work
    "health" -> Icons.Rounded.LocalHospital
    "authority" -> Icons.Rounded.AccountBalance
    "service" -> Icons.Rounded.Handyman
    "club" -> Icons.Rounded.EmojiPeople
    "other" -> Icons.Rounded.Domain
    else -> Icons.Rounded.Business
}

/** Default avatar shape: round for people, a rounded square for organisations. */
fun avatarShape(c: Contact, size: Dp): Shape = if (c.isOrg) RoundedCornerShape(size * 0.28f) else CircleShape

/** Small building mark after an organisation's name – visible, but quiet. */
@Composable
fun KindMark(c: Contact, modifier: Modifier = Modifier, size: Dp = 13.dp) {
    if (c.isOrg) {
        Icon(Icons.Rounded.Business, "Organisation", tint = Space.Blue.copy(alpha = 0.85f), modifier = modifier.size(size))
    }
}

/**
 * Photo or initials. The ring takes the group colour; [glowColor] adds light around it
 * (used for "fällig").
 */
@Composable
fun Avatar(
    contact: Contact,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    ring: Boolean = true,
    glowColor: Color? = null,
) {
    val photo = rememberPhoto(contact.photo)
    val ringWidth = if (size > 56.dp) 2.5.dp else 1.5.dp
    // Organisations always keep their square shape so they are recognisable everywhere.
    val s = if (contact.isOrg || shape == null) avatarShape(contact, size) else shape
    Box(modifier.size(size)) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (glowColor != null) Modifier.glow(glowColor, size / 4, s, 0.7f) else Modifier)
                .then(if (ring) Modifier.border(ringWidth, if (glowColor != null) glowColor else color.copy(alpha = 0.85f), s) else Modifier)
                .padding(if (ring) ringWidth + 1.5.dp else 0.dp)
                .clip(s),
            contentAlignment = Alignment.Center,
        ) {
            if (photo != null) {
                Image(bitmap = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(listOf(color.copy(alpha = 0.55f), color.copy(alpha = 0.20f)))),
                    contentAlignment = Alignment.Center,
                ) {
                    if (contact.isOrg) {
                        Icon(orgIcon(contact.orgCategory), null, tint = Space.Text, modifier = Modifier.size(size * 0.46f))
                    } else {
                        Text(
                            Logic.initials(contact),
                            color = Space.Text,
                            fontFamily = Grotesk,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = (size.value * 0.36f).sp,
                        )
                    }
                }
            }
        }
        if (contact.isOrg && photo != null && size >= 30.dp) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.38f)
                    .clip(RoundedCornerShape(size * 0.12f))
                    .background(Space.Deep)
                    .border(1.dp, Space.Blue.copy(alpha = 0.7f), RoundedCornerShape(size * 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Business, null, tint = Space.Blue, modifier = Modifier.size(size * 0.24f))
            }
        }
    }
}

fun entryIcon(key: String): ImageVector = when (key) {
    "meet" -> Icons.Rounded.Groups
    "call" -> Icons.Rounded.Call
    "video" -> Icons.Rounded.Videocam
    "message" -> Icons.Rounded.ChatBubble
    "email" -> Icons.Rounded.Email
    "event" -> Icons.Rounded.Celebration
    "coffee" -> Icons.Rounded.LocalCafe
    "food" -> Icons.Rounded.Restaurant
    "drink" -> Icons.Rounded.LocalBar
    "sport" -> Icons.Rounded.FitnessCenter
    "game" -> Icons.Rounded.SportsEsports
    "music" -> Icons.Rounded.MusicNote
    "travel" -> Icons.Rounded.Flight
    "gift" -> Icons.Rounded.CardGiftcard
    "heart" -> Icons.Rounded.Favorite
    else -> Icons.Rounded.Star
}

/** Section = small uppercase title + glass card. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    GlassCard(modifier, padding = 12.dp) {
        SectionTitle(title, icon, action = if (action != null) { { action() } } else null)
        content()
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(text, style = MaterialTheme.typography.labelMedium, color = Space.Text)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Space.PanelHigh,
        title = { Text(title, style = MaterialTheme.typography.titleLargeEmphasized) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium, color = Space.TextDim) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm, color = Space.Coral) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
fun EmptyHint(title: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(14.dp).glow(Space.Orange, 12.dp).background(Space.Orange, CircleShape),
        )
        Text(title, style = MaterialTheme.typography.titleMediumEmphasized, color = Space.Text, modifier = Modifier.padding(top = 8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Space.TextDim, textAlign = TextAlign.Center)
    }
}
