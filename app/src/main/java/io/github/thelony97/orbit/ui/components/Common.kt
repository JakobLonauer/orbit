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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CardGiftcard
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.domain.Index
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.ui.theme.onColor
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

/** Stable colour per contact: first group colour, otherwise derived from the id. */
fun contactColor(c: Contact, idx: Index): Color {
    c.groupIds.firstNotNullOfOrNull { idx.groups[it] }?.let { return Color(it.color) }
    val palette = listOf(0xFF7986CB, 0xFF4DB6AC, 0xFFFFB74D, 0xFFBA68C8, 0xFF4FC3F7, 0xFFF06292, 0xFF81C784)
    return Color(palette[(c.id.hashCode() and 0x7FFFFFFF) % palette.size])
}

@Composable
fun Avatar(
    contact: Contact,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    ring: Color? = null,
) {
    val photo = rememberPhoto(contact.photo)
    val base = modifier
        .size(size)
        .then(if (ring != null) Modifier.border(2.dp, ring, shape) else Modifier)
        .clip(shape)
    if (photo != null) {
        Image(bitmap = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = base)
    } else {
        Box(base.background(color), contentAlignment = Alignment.Center) {
            Text(
                Logic.initials(contact),
                color = onColor(color),
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.38f).sp,
            )
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

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (icon != null) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                action?.invoke()
            }
            Spacer(Modifier.size(6.dp))
            content()
        }
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.22f), modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
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
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
fun EmptyHint(title: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLargeEmphasized, color = MaterialTheme.colorScheme.onSurface)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
