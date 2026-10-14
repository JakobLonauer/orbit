package io.github.thelony97.orbit.ui.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocalLibrary
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.actions.ContactActions
import io.github.thelony97.orbit.core.model.Link
import io.github.thelony97.orbit.core.model.OpenWith
import io.github.thelony97.orbit.ui.components.SpaceChip
import io.github.thelony97.orbit.ui.theme.Space

/** Address without scheme and „www.“, for the tile. */
fun linkHost(url: String): String =
    url.trim().removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore('/').ifBlank { url }

/** „Ohne Logo ein passendes Symbol“ (PLAN 3.7): from words in the title or address. */
fun linkIcon(l: Link): ImageVector {
    val t = (l.title + " " + l.url).lowercase()
    return when {
        listOf("bibliothek", "library", "biblio").any { it in t } -> Icons.Rounded.LocalLibrary
        listOf("mensa", "menu", "speise").any { it in t } -> Icons.Rounded.Restaurant
        listOf("mail", "outlook", "webmail").any { it in t } -> Icons.Rounded.Email
        listOf("bank", "george", "raiffeisen", "sparkasse").any { it in t } -> Icons.Rounded.AccountBalance
        listOf("termin", "kalender", "calendar", "booking").any { it in t } -> Icons.Rounded.CalendarMonth
        listOf("maps", "karte").any { it in t } -> Icons.Rounded.Map
        listOf("linkedin", "xing", "job").any { it in t } -> Icons.Rounded.Work
        // The school words last: „jku.at/bibliothek“ is the library, not the university.
        listOf("moodle", "kusss", "ph-online", "uni", "schule", "jku").any { it in t } -> Icons.Rounded.School
        else -> Icons.Rounded.Language
    }
}

private val TILE_COLORS = listOf(Space.Blue, Space.Violet, Space.Teal, Space.Orange, Space.Coral)

/**
 * Portale und Links (PLAN 3.7): three tiles per row with symbol, title, address and note. Tap
 * opens (Orbit's browser window, Chrome or an app); long-press: bearbeiten, nach vorne, anpinnen,
 * „Auf Startbildschirm“, entfernen.
 */
@Composable
fun LinkGrid(
    links: List<Link>,
    /** The contact the links belong to (for the logo once it is loaded). */
    ownerId: String,
    onEdit: (Link) -> Unit,
    onChange: (List<Link>) -> Unit,
    onRemove: (Link) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        links.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { l -> LinkTileView(l, ownerId, links, onEdit, onChange, onRemove, Modifier.weight(1f)) }
                // Keep three columns on the last row.
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LinkTileView(
    l: Link,
    ownerId: String,
    all: List<Link>,
    onEdit: (Link) -> Unit,
    onChange: (List<Link>) -> Unit,
    onRemove: (Link) -> Unit,
    modifier: Modifier,
) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val tint = TILE_COLORS[(linkHost(l.url).hashCode() and 0x7fffffff) % TILE_COLORS.size]
    val shape = RoundedCornerShape(16.dp)
    // Echtes Logo (PLAN 1.3): loaded once, then from the file.
    androidx.compose.runtime.LaunchedEffect(l.id, l.url, l.logo == null) {
        if (l.logo == null) {
            io.github.thelony97.orbit.links.Logos.fetch(l.id, l.url)?.let { name ->
                io.github.thelony97.orbit.data.OrbitRepository.edit { d -> io.github.thelony97.orbit.data.Mutations.setLinkLogo(d, ownerId, l.id, name) }
            }
        }
    }
    val logo = io.github.thelony97.orbit.ui.components.rememberPhoto(l.logo)
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(104.dp)
                .clip(shape)
                .background(Space.Panel)
                .border(1.dp, if (l.pinned) Space.Orange.copy(alpha = 0.5f) else Space.Line, shape)
                .combinedClickable(onClick = { ContactActions.openLink(ctx, l) }, onLongClick = { menu = true })
                .padding(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(if (logo != null) Color.White else tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    if (logo != null) {
                        androidx.compose.foundation.Image(logo, null, modifier = Modifier.size(30.dp), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    } else {
                        Icon(linkIcon(l), null, tint = tint, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                if (l.pinned) Icon(Icons.Rounded.PushPin, "Angepinnt", tint = Space.Orange, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(l.title.ifBlank { linkHost(l.url) }, style = MaterialTheme.typography.labelLarge, color = Space.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                l.note.ifBlank { linkHost(l.url) },
                style = MaterialTheme.typography.labelSmall,
                color = if (l.note.isNotBlank()) Space.TextDim else Space.TextFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(menu, { menu = false }, containerColor = Space.PanelHigh) {
            DropdownMenuItem(text = { Text("Bearbeiten") }, onClick = { menu = false; onEdit(l) })
            if (all.firstOrNull()?.id != l.id) {
                DropdownMenuItem(text = { Text("Nach vorne") }, onClick = { menu = false; onChange(listOf(l) + all.filterNot { it.id == l.id }) })
            }
            DropdownMenuItem(
                text = { Text(if (l.pinned) "Lösen" else "Anpinnen") },
                onClick = { menu = false; onChange(all.map { if (it.id == l.id) it.copy(pinned = !it.pinned) else it }) },
            )
            DropdownMenuItem(text = { Text("Auf Startbildschirm") }, onClick = { menu = false; pinToHome(ctx, l) })
            DropdownMenuItem(text = { Text("Entfernen", color = Space.Coral) }, onClick = { menu = false; onRemove(l) })
        }
    }
}

/** „Auf Startbildschirm“ (PLAN 3.7): a launcher shortcut that opens the address. */
private fun pinToHome(ctx: android.content.Context, l: Link) {
    val raw = l.url.trim()
    val uri = android.net.Uri.parse(if (raw.startsWith("http")) raw else "https://$raw")
    val info = androidx.core.content.pm.ShortcutInfoCompat.Builder(ctx, "link-" + l.id)
        .setShortLabel(l.title.ifBlank { linkHost(l.url) }.take(24))
        // Mit Logo (PLAN 1.3) once it is there, Orbit's icon otherwise.
        .setIcon(
            l.logo?.let { runCatching { android.graphics.BitmapFactory.decodeFile(io.github.thelony97.orbit.data.OrbitRepository.photoFile(it).path) }.getOrNull() }
                ?.let { androidx.core.graphics.drawable.IconCompat.createWithAdaptiveBitmap(padForAdaptive(it)) }
                ?: androidx.core.graphics.drawable.IconCompat.createWithResource(ctx, io.github.thelony97.orbit.R.mipmap.ic_launcher),
        )
        .setIntent(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
        .build()
    if (!androidx.core.content.pm.ShortcutManagerCompat.requestPinShortcut(ctx, info, null)) {
        android.widget.Toast.makeText(ctx, "Der Startbildschirm nimmt keine Verknüpfungen an", android.widget.Toast.LENGTH_SHORT).show()
    }
}

/** Adresse, Titel, Notiz (never passwords) and how it opens. */
@Composable
fun LinkEditDialog(initial: Link, onDismiss: () -> Unit, onSave: (Link) -> Unit) {
    val url = rememberTextFieldState(initial.url)
    val title = rememberTextFieldState(initial.title)
    val note = rememberTextFieldState(initial.note)
    var openWith by remember { mutableStateOf(if (initial.openWith == OpenWith.APP && initial.appPackage == null) OpenWith.ORBIT_TAB else initial.openWith) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Space.PanelHigh,
        title = { Text(initial.title.ifBlank { linkHost(initial.url) }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(url to "Adresse", title to "Titel", note to "Notiz").forEach { (st, label) ->
                    OutlinedTextField(state = st, label = { Text(label) }, lineLimits = TextFieldLineLimits.SingleLine, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpaceChip("In Orbit", openWith == OpenWith.ORBIT_TAB, { openWith = OpenWith.ORBIT_TAB })
                    SpaceChip("Chrome", openWith == OpenWith.CHROME, { openWith = OpenWith.CHROME })
                    if (initial.appPackage != null) SpaceChip("App", openWith == OpenWith.APP, { openWith = OpenWith.APP })
                }
            }
        },
        confirmButton = {
            TextButton(enabled = url.text.isNotBlank(), onClick = {
                val u = url.text.toString().trim()
                onSave(
                    initial.copy(
                        url = if (u.startsWith("http://") || u.startsWith("https://")) u else "https://$u",
                        title = title.text.toString().trim(),
                        note = note.text.toString().trim(),
                        openWith = openWith,
                    ),
                )
                onDismiss()
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** Schnellzugriff-Chip (PLAN 3.7): tap opens the link, long-press the contact it belongs to. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickLinkChip(l: Link, onClick: () -> Unit, onLongClick: () -> Unit) {
    val tint = TILE_COLORS[(linkHost(l.url).hashCode() and 0x7fffffff) % TILE_COLORS.size]
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .height(34.dp)
            .clip(shape)
            .background(Space.Panel)
            .border(1.dp, tint.copy(alpha = 0.35f), shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 10.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val logo = io.github.thelony97.orbit.ui.components.rememberPhoto(l.logo)
        if (logo != null) {
            androidx.compose.foundation.Image(logo, null, modifier = Modifier.size(18.dp).clip(RoundedCornerShape(5.dp)))
        } else {
            Icon(linkIcon(l), null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.size(8.dp))
        Text(l.title.ifBlank { linkHost(l.url) }, style = MaterialTheme.typography.labelLarge, color = Space.Text, maxLines = 1)
    }
}

/** Adaptive icons crop the outer third: the logo sits centred on white inside the safe zone. */
private fun padForAdaptive(logo: android.graphics.Bitmap): android.graphics.Bitmap {
    val size = 216
    val out = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    canvas.drawColor(android.graphics.Color.WHITE)
    val inner = 96
    val off = ((size - inner) / 2).toFloat()
    canvas.drawBitmap(android.graphics.Bitmap.createScaledBitmap(logo, inner, inner, true), off, off, null)
    return out
}

/** Die Schublade (PLAN 1.3): every pinned link as a large tile; long-press opens its contact. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickDrawer(links: List<Pair<Link, String>>, onOpen: (Link) -> Unit, onOwner: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        links.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (l, owner) ->
                    val tint = TILE_COLORS[(linkHost(l.url).hashCode() and 0x7fffffff) % TILE_COLORS.size]
                    val logo = io.github.thelony97.orbit.ui.components.rememberPhoto(l.logo)
                    val shape = RoundedCornerShape(16.dp)
                    Column(
                        Modifier
                            .weight(1f)
                            .height(96.dp)
                            .clip(shape)
                            .background(Space.Panel)
                            .border(1.dp, tint.copy(alpha = 0.35f), shape)
                            .combinedClickable(onClick = { onOpen(l) }, onLongClick = { onOwner(owner) })
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(if (logo != null) Color.White else tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                            if (logo != null) androidx.compose.foundation.Image(logo, null, modifier = Modifier.size(36.dp))
                            else Icon(linkIcon(l), null, tint = tint, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(l.title.ifBlank { linkHost(l.url) }, style = MaterialTheme.typography.labelLarge, color = Space.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
