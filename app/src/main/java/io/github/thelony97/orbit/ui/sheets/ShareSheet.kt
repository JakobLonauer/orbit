package io.github.thelony97.orbit.ui.sheets

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import io.github.thelony97.orbit.core.domain.Matcher
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
import io.github.thelony97.orbit.ui.components.GhostButton
import io.github.thelony97.orbit.ui.components.GlowButton
import io.github.thelony97.orbit.ui.components.SpaceChip
import io.github.thelony97.orbit.ui.components.SpaceSearchField
import io.github.thelony97.orbit.ui.components.contactColor
import io.github.thelony97.orbit.ui.theme.Space
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What came in through „Teilen“: parsed text and/or pictures (read while Orbit is in front).
 * [targetId]: shared straight to a person from the system share sheet (PLAN 9 A5) – no picking.
 */
data class PendingShare(
    val item: ShareIn.Item?,
    val images: List<Uri>,
    val documents: Boolean = false,
    val targetId: String? = null,
    /** PLAN 9 A2 (INSERT_OR_EDIT): everything the other app handed over, for „Neuer Kontakt“. */
    val draft: Contact? = null,
)

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
    // PLAN 9 A4: a marked number or address – who has it already (organisation ranges included).
    val iso = remember { io.github.thelony97.orbit.domain.Phones.countryIso(ctx) }
    val haptics = io.github.thelony97.orbit.core.designsystem.rememberHaptics()
    val matcher = remember(data.contacts) { io.github.thelony97.orbit.domain.PhoneMatcher(data.contacts, iso) }
    val match = remember(item, matcher) { (item as? ShareIn.Item.Number)?.let { matcher.matchCall(it.number) } }
    val owner: Contact? = remember(item, data.contacts, match) {
        when (item) {
            is ShareIn.Item.Number -> match?.let { m -> data.contacts.firstOrNull { it.id == m.contactId && it.deletedAt == null && !it.archived } }
            is ShareIn.Item.Email -> item.address.lowercase().let { a ->
                fun ownsMail(c: Contact) = c.emails.any { it.value.trim().lowercase() == a } ||
                    c.emailDomains.any { d -> a.endsWith("@" + d.trim().removePrefix("@").lowercase()) }
                data.contacts.firstOrNull { c -> c.deletedAt == null && !c.archived && !c.isMe && ownsMail(c) }
            }
            else -> null
        }
    }
    // PLAN 9 A4/B2: a number in an organisation's range – its Durchwahl can be kept there.
    val extension: String? = remember(item, owner, match) {
        if (owner == null || item !is ShareIn.Item.Number || match?.via != io.github.thelony97.orbit.core.domain.Matcher.Via.PREFIX) null
        else matcher.extension(item.number, owner.numberPrefixes)
    }
    val valueShare = (item is ShareIn.Item.Number || item is ShareIn.Item.Email) && share.images.isEmpty()
    // The owner already has exactly this value: shown once, in the banner. Anything else (a
    // range or domain match, a card to merge) keeps the owner as a target.
    val ownerHasIt = owner != null && valueShare && share.draft == null && when (item) {
        is ShareIn.Item.Number -> owner.phones.any { Matcher.tail(it.value) == Matcher.tail(item.number) }
        is ShareIn.Item.Email -> owner.emails.any { it.value.trim().equals(item.address.trim(), ignoreCase = true) }
        else -> false
    }
    val targets = Logic.active(data)
        .filter { !it.archived && !it.isMe && !(ownerHasIt && it.id == owner?.id) && Logic.matches(it, q, idx) }
        // A place most likely belongs to an organisation: those first.
        .sortedWith(
            compareByDescending<Contact> { it.id == owner?.id }
                .thenByDescending { wantsOrg && it.isOrg }
                .thenBy { Logic.sortKey(it, data.settings.sortByLastName) },
        )
        .take(40)

    fun apply(target: Contact) {
        // INSERT_OR_EDIT (PLAN 9 A2): everything the other app handed over joins this contact on its edit page.
        share.draft?.let { draft ->
            onDone()
            nav.open(Route.Edit(id = target.id, prefill = OrbitRepository.json.encodeToString(Contact.serializer(), draft)))
            return
        }
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
            // PLAN 9 A4: the number or address joins the contact (nothing replaced, Undo).
            item is ShareIn.Item.Number -> {
                haptics.perform(io.github.thelony97.orbit.core.designsystem.Haptic.Confirm)
                addNumber(target, item.number, iso)
            }
            item is ShareIn.Item.Email -> {
                haptics.perform(io.github.thelony97.orbit.core.designsystem.Haptic.Confirm)
                addMail(target, item.address)
            }
        }
        // Confirmed at the place it went (PLAN 9.2, no toast): the contact's page opens.
        if (item !is ShareIn.Item.Number && item !is ShareIn.Item.Email) haptics.perform(io.github.thelony97.orbit.core.designsystem.Haptic.Confirm)
        onDone()
        nav.open(Route.Detail(target.id))
    }

    // PLAN 9 A5: shared straight to a person – nothing to pick, it lands there at once.
    val direct = share.targetId?.let { id -> data.contacts.firstOrNull { it.id == id && it.deletedAt == null && !it.archived } }
    if (direct != null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { apply(direct) }
        return
    }

    ModalBottomSheet(onDismissRequest = onDone, sheetState = state, containerColor = Space.PanelHigh) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val (title, sub) = when {
                share.images.isNotEmpty() && share.documents -> (if (share.images.size == 1) "1 PDF" else "${share.images.size} PDFs") to ""
                share.images.isNotEmpty() -> (if (share.images.size == 1) "1 Bild" else "${share.images.size} Bilder") to ""
                item is ShareIn.Item.Place -> item.name.ifBlank { "Ort" } to item.address
                item is ShareIn.Item.Page -> item.title to io.github.thelony97.orbit.ui.detail.linkHost(item.url)
                item is ShareIn.Item.Text -> item.text to ""
                item is ShareIn.Item.Number -> item.number to ""
                item is ShareIn.Item.Email -> item.address to ""
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
            // PLAN 9 A4: number or address – „gehört zu …“ is a fact (same banner as in the search);
            // a Durchwahl in an organisation's range is a ghost; otherwise a new contact with it.
            if ((item is ShareIn.Item.Number || item is ShareIn.Item.Email) && share.images.isEmpty()) {
                if (owner != null) {
                    io.github.thelony97.orbit.ui.components.Banner(
                        icon = if (owner.isOrg) Icons.Rounded.Business else Icons.Rounded.Person,
                        color = if (owner.isOrg) Space.Blue else contactColor(owner, idx),
                        text = "gehört zu " + Logic.displayName(owner, data.settings.sortByLastName),
                        // A card handed over to complete someone („Zu bestehendem“) goes into the owner.
                        onClick = { if (share.draft != null) apply(owner) else { onDone(); nav.open(Route.Detail(owner.id)) } },
                        action = if (share.draft != null) "Ergänzen" else "Öffnen",
                        edge = 0.dp,
                    )
                    if (extension != null && item is ShareIn.Item.Number) {
                        io.github.thelony97.orbit.ui.components.Ghost(
                            source = "Im Nummernbereich von " + Logic.displayName(owner, false),
                            onAccept = {
                                addNumber(owner, item.number, iso, label = "Durchwahl $extension")
                                onDone()
                                nav.open(Route.Detail(owner.id))
                            },
                            onDismiss = null,
                            modifier = Modifier.fillMaxWidth(),
                            accent = Space.Blue,
                            dismissAxis = null,
                        ) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Add, null, tint = Space.Blue, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(12.dp))
                                Text("Durchwahl $extension speichern", style = MaterialTheme.typography.bodyLarge, color = Space.Text)
                            }
                        }
                    }
                }
                val newContact = {
                    val draft = share.draft ?: when (item) {
                        is ShareIn.Item.Number -> Contact(id = newId(), phones = listOf(LabeledValue("Mobil", item.number)))
                        is ShareIn.Item.Email -> Contact(id = newId(), emails = listOf(LabeledValue("Privat", item.address)))
                        else -> Contact(id = newId())
                    }
                    onDone()
                    nav.open(Route.Edit(prefill = OrbitRepository.json.encodeToString(Contact.serializer(), draft)))
                }
                // With an owner the new contact is the side way, not the main action.
                if (owner == null) GlowButton("Neuer Kontakt", newContact, icon = Icons.Rounded.PersonAdd)
                else GhostButton("Neuer Kontakt", newContact, icon = Icons.Rounded.PersonAdd)
            }
            if (item is ShareIn.Item.Text && share.images.isEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpaceChip("Notiz", !asTopic, { haptics.perform(io.github.thelony97.orbit.core.designsystem.Haptic.Tick); asTopic = false })
                    SpaceChip("Thema", asTopic, { haptics.perform(io.github.thelony97.orbit.core.designsystem.Haptic.Tick); asTopic = true })
                }
            }
            SpaceSearchField(search, "Kontakt", Modifier.fillMaxWidth(), leading = Icons.Rounded.Search)
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(targets, key = { it.id }) { c ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { apply(c) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(c, contactColor(c, idx), 34.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(Logic.displayName(c, data.settings.sortByLastName), color = Space.Text, modifier = Modifier.weight(1f))
                        // What a tap does: it adds there.
                        Icon(
                            if (share.images.isNotEmpty()) Icons.Rounded.AddPhotoAlternate else Icons.Rounded.Add,
                            null,
                            tint = Space.TextDim,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Adds [number] to [target] unless it is there already (same last nine digits). Undo takes away
 * exactly the row that was added – never one the contact had before.
 */
private fun addNumber(target: Contact, number: String, iso: String, label: String? = null) {
    val key = number.filter(Char::isDigit).takeLast(9)
    if (target.phones.any { it.value.filter(Char::isDigit).takeLast(9) == key }) return
    val landline = io.github.thelony97.orbit.domain.Phones.normalize(number, iso)?.let { io.github.thelony97.orbit.core.domain.NumberKind.isLandline(it) } == true
    val label = label ?: when {
        target.isOrg -> "Zentrale"
        landline -> "Privat"
        else -> "Mobil"
    }
    val row = LabeledValue(label, number)
    OrbitRepository.editContact(target.id) { c ->
        if (c.phones.any { it.value.filter(Char::isDigit).takeLast(9) == key }) c else c.copy(phones = c.phones + row)
    }
    io.github.thelony97.orbit.ui.components.Undo.offer("Nummer bei ${Logic.shortName(target)}") {
        OrbitRepository.editContact(target.id) { c ->
            val at = c.phones.indexOfLast { it == row }
            if (at < 0) c else c.copy(phones = c.phones.toMutableList().apply { removeAt(at) })
        }
    }
}

/** Like [addNumber] for a mail address. */
private fun addMail(target: Contact, address: String) {
    if (target.emails.any { it.value.equals(address, ignoreCase = true) }) return
    val row = LabeledValue(if (target.isOrg) "Arbeit" else "Privat", address)
    OrbitRepository.editContact(target.id) { c ->
        if (c.emails.any { it.value.equals(address, ignoreCase = true) }) c else c.copy(emails = c.emails + row)
    }
    io.github.thelony97.orbit.ui.components.Undo.offer("E-Mail bei ${Logic.shortName(target)}") {
        OrbitRepository.editContact(target.id) { c ->
            val at = c.emails.indexOfLast { it == row }
            if (at < 0) c else c.copy(emails = c.emails.toMutableList().apply { removeAt(at) })
        }
    }
}
