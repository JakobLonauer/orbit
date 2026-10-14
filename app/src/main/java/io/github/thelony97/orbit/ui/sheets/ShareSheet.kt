package io.github.thelony97.orbit.ui.sheets

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.core.domain.ShareIn
import io.github.thelony97.orbit.core.model.CheckItem
import io.github.thelony97.orbit.core.model.ContactKind
import io.github.thelony97.orbit.core.model.LabeledValue
import io.github.thelony97.orbit.core.model.Link
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.data.newId
import io.github.thelony97.orbit.data.now
import io.github.thelony97.orbit.domain.Index
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.ui.Nav
import io.github.thelony97.orbit.ui.Route
import io.github.thelony97.orbit.ui.components.Avatar
import io.github.thelony97.orbit.ui.components.GlowButton
import io.github.thelony97.orbit.ui.components.SpaceChip
import io.github.thelony97.orbit.ui.components.SpaceSearchField
import io.github.thelony97.orbit.ui.components.contactColor
import io.github.thelony97.orbit.ui.theme.Space
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What came in through „Teilen“: parsed text and/or pictures (read while Orbit is in front). */
data class PendingShare(val item: ShareIn.Item?, val images: List<Uri>, val documents: Boolean = false)

/**
 * Teilen-Eingang (PLAN 3.8): a place from Maps → new organisation or an existing contact; a page →
 * link at a contact; text → note or topic; pictures → a contact's photo folder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(share: PendingShare, data: OrbitData, idx: Index, nav: Nav, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val item = share.item
    var asTopic by remember { mutableStateOf(false) }
    val search = rememberTextFieldState()
    val q = search.text.toString()
    val wantsOrg = item is ShareIn.Item.Place
    val targets = Logic.active(data)
        .filter { !it.archived && !it.isMe && Logic.matches(it, q, idx) }
        // A place most likely belongs to an organisation: those first.
        .sortedWith(compareByDescending<Contact> { wantsOrg && it.isOrg }.thenBy { Logic.sortKey(it, data.settings.sortByLastName) })
        .take(40)

    fun apply(target: Contact) {
        val name = Logic.shortName(target)
        when {
            share.images.isNotEmpty() -> scope.launch {
                withContext(NonCancellable) {
                    val added = if (share.documents) io.github.thelony97.orbit.ui.detail.GalleryStore.importDocs(ctx.applicationContext, share.images)
                    else io.github.thelony97.orbit.ui.detail.GalleryStore.import(ctx.applicationContext, share.images)
                    OrbitRepository.editContact(target.id) { it.copy(gallery = added.asReversed().map { a -> a.copy(contactId = target.id) } + it.gallery) }
                }
            }
            item is ShareIn.Item.Place -> OrbitRepository.editContact(target.id) { c ->
                // Maps-Link exact to the place; the address only where there is none yet.
                val withAddress = if (c.address.isBlank() && item.address.isNotBlank()) c.withAddress(item.address) else c
                withAddress.copy(mapsUrl = item.mapsUrl)
            }
            item is ShareIn.Item.Page -> OrbitRepository.editContact(target.id) { c ->
                if (c.links.any { it.url == item.url }) c else c.copy(links = c.links + Link(newId(), item.title, item.url))
            }
            item is ShareIn.Item.Text -> OrbitRepository.editContact(target.id) { c ->
                if (asTopic) c.copy(topics = c.topics + CheckItem(newId(), item.text.take(200), createdAt = now()))
                else c.copy(notes = listOf(c.notes, item.text).filter { it.isNotBlank() }.joinToString("\n"))
            }
        }
        Toast.makeText(ctx, "Bei $name gespeichert", Toast.LENGTH_SHORT).show()
        onDone()
        nav.open(Route.Detail(target.id))
    }

    ModalBottomSheet(onDismissRequest = onDone, sheetState = state, containerColor = Space.PanelHigh) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp).navigationBarsPadding().padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val (title, sub) = when {
                share.images.isNotEmpty() && share.documents -> (if (share.images.size == 1) "1 PDF" else "${share.images.size} PDFs") to ""
                share.images.isNotEmpty() -> (if (share.images.size == 1) "1 Bild" else "${share.images.size} Bilder") to ""
                item is ShareIn.Item.Place -> item.name.ifBlank { "Ort" } to item.address
                item is ShareIn.Item.Page -> item.title to io.github.thelony97.orbit.ui.detail.linkHost(item.url)
                item is ShareIn.Item.Text -> item.text to ""
                else -> "" to ""
            }
            Text(title, style = MaterialTheme.typography.titleLargeEmphasized, color = Space.Text, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = Space.TextDim)
            if (item is ShareIn.Item.Place && share.images.isEmpty()) {
                GlowButton("Neue Organisation", {
                    val draft = Contact(
                        id = newId(), kind = ContactKind.ORG, company = item.name, mapsUrl = item.mapsUrl,
                        addresses = if (item.address.isNotBlank()) listOf(LabeledValue("Adresse", item.address)) else emptyList(),
                    )
                    onDone()
                    nav.open(Route.Edit(prefill = OrbitRepository.json.encodeToString(Contact.serializer(), draft)))
                }, icon = Icons.Rounded.Business, color = Space.Blue)
            }
            if (item is ShareIn.Item.Text && share.images.isEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpaceChip("Notiz", !asTopic, { asTopic = false })
                    SpaceChip("Thema", asTopic, { asTopic = true })
                }
            }
            SpaceSearchField(search, "Kontakt", Modifier.fillMaxWidth(), leading = Icons.Rounded.Search)
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(targets, key = { it.id }) { c ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { apply(c) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(c, contactColor(c, idx), 34.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(Logic.displayName(c, data.settings.sortByLastName), color = Space.Text)
                    }
                }
            }
        }
    }
}
