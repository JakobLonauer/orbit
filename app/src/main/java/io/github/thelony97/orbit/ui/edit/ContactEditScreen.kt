package io.github.thelony97.orbit.ui.edit

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.focus.onFocusChanged
import io.github.thelony97.orbit.core.designsystem.Motion
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.ContactKind
import io.github.thelony97.orbit.ui.components.orgIcon
import io.github.thelony97.orbit.data.Defaults
import io.github.thelony97.orbit.data.LabeledValue
import io.github.thelony97.orbit.core.model.CustomField
import io.github.thelony97.orbit.core.model.Link
import io.github.thelony97.orbit.data.Mutations
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.data.newId
import io.github.thelony97.orbit.domain.Index
import io.github.thelony97.orbit.security.AppLock
import io.github.thelony97.orbit.sync.Sync
import io.github.thelony97.orbit.ui.Nav
import io.github.thelony97.orbit.ui.Photos
import io.github.thelony97.orbit.ui.Route
import io.github.thelony97.orbit.ui.components.Avatar
import io.github.thelony97.orbit.ui.components.ConfirmDialog
import io.github.thelony97.orbit.ui.components.GlassCard
import io.github.thelony97.orbit.ui.components.GlowButton
import io.github.thelony97.orbit.ui.components.SaveMorphButton
import io.github.thelony97.orbit.ui.components.OrbButton
import io.github.thelony97.orbit.ui.components.PhotoCache
import io.github.thelony97.orbit.ui.components.SectionTitle
import io.github.thelony97.orbit.ui.components.SpaceBackground
import io.github.thelony97.orbit.ui.components.SpaceChip
import io.github.thelony97.orbit.ui.components.SpaceField
import io.github.thelony97.orbit.ui.components.contactColor
import io.github.thelony97.orbit.ui.components.glow
import io.github.thelony97.orbit.ui.sheets.DayOfYearDialog
import io.github.thelony97.orbit.ui.theme.Space
import io.github.thelony97.orbit.util.Fmt
import kotlinx.coroutines.launch

/** [fresh]: handed over by another app or the phone book – lights up teal once (PLAN 9 A2). */
private class ValueRow(label: String, value: String, val fresh: Boolean = false) {
    var label by mutableStateOf(label)
    val state = TextFieldState(value)
}

/**
 * „Zu bestehendem Kontakt“ (PLAN 9 A2, INSERT_OR_EDIT): what another app handed over joins the
 * contact – new numbers, mails and addresses are added, nothing is replaced.
 */
internal fun mergeIncoming(base: Contact, incoming: Contact): Contact {
    fun add(old: List<LabeledValue>, new: List<LabeledValue>, key: (String) -> String) =
        old + new.filter { n -> n.value.isNotBlank() && old.none { key(it.value) == key(n.value) } }
            .distinctBy { key(it.value) }
    return base.copy(
        phones = add(base.phones, incoming.phones) { v -> v.filter(Char::isDigit).takeLast(9) },
        emails = add(base.emails, incoming.emails) { v -> v.trim().lowercase() },
        addresses = add(base.addresses, incoming.addresses) { v -> v.trim().lowercase() },
        company = base.company.ifBlank { incoming.company },
        position = base.position.ifBlank { incoming.position },
        notes = listOf(base.notes, incoming.notes).filter { it.isNotBlank() }.distinct().joinToString("\n"),
    )
}

/** Title suggestions (PLAN 9 A10) – the Austrian forms that fit Frau/Herr, both without a choice. */
private fun titleOffers(salutation: String): List<String> = when (salutation) {
    "Frau" -> listOf("Mag.a", "Dr.in", "DIin", "Ing.in", "Prof.in", "BEd", "BSc", "MSc")
    "Herr" -> listOf("Mag.", "Dr.", "DI", "Ing.", "Prof.", "BEd", "BSc", "MSc")
    else -> listOf("Mag.a", "Mag.", "Dr.in", "Dr.", "DI", "Ing.", "BEd", "MSc")
}

/**
 * Handed-over values light up teal at their rim once (PLAN 9 A2 Moment) – one after the other,
 * 40 ms apart, in 150 ms, then they settle in 350 ms.
 */
@Composable
private fun rememberLit(fresh: Boolean, order: Int): (() -> Float)? {
    val a = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(fresh) {
        if (!fresh) return@LaunchedEffect
        kotlinx.coroutines.delay(Motion.MEDIUM_MS + 40L * order)
        a.animateTo(1f, androidx.compose.animation.core.tween(Motion.FAST_MS, easing = Motion.Standard))
        kotlinx.coroutines.delay(700)
        a.animateTo(0f, androidx.compose.animation.core.tween(Motion.EMPHASIZED_MS, easing = Motion.Standard))
    }
    return if (fresh) ({ a.value }) else null
}

/** The small × that clears a value (same everywhere on the page). */
@Composable
private fun ClearButton(onClick: () -> Unit) {
    Icon(
        Icons.Rounded.Close, "Entfernen",
        tint = Space.TextFaint,
        modifier = Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onClick).padding(7.dp),
    )
}

/** Two free-text halves (link title + address, field label + value) of one editable row. */
private class PairRow(val id: String, first: String, second: String) {
    val first = TextFieldState(first)
    val second = TextFieldState(second)
}

@Composable
fun ContactEditScreen(id: String?, prefill: String?, data: OrbitData, idx: Index, nav: Nav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val incoming: Contact? = remember(prefill) {
        prefill?.let { runCatching { OrbitRepository.json.decodeFromString(Contact.serializer(), it) }.getOrNull() }
    }
    val original: Contact = remember(id, prefill) {
        val existing = id?.let { idx.contacts[it] }
        when {
            // INSERT_OR_EDIT / „Zu … hinzufügen“: the handed-over values join the existing contact.
            existing != null && incoming != null -> mergeIncoming(existing, incoming)
            existing != null -> existing
            incoming != null -> incoming
            else -> Contact(id = newId())
        }
    }
    /** Values that came from outside light up once (PLAN 9 A2) – for an existing contact only what joined it. */
    val freshValues = remember(prefill) {
        val before = id?.let { idx.contacts[it] }
        incoming?.let { inc ->
            (inc.phones + inc.emails).map { it.value.trim() }.filter { v ->
                v.isNotEmpty() && before?.let { b -> (b.phones + b.emails).none { it.value.trim() == v } } != false
            }.toSet()
        }.orEmpty()
    }
    val freshFields: Set<String> = remember(prefill) {
        val before = id?.let { idx.contacts[it] } ?: Contact(id = "")
        if (incoming == null) emptySet() else buildSet {
            fun take(key: String, now: String, was: String) { if (now.isNotBlank() && now != was) add(key) }
            take("first", original.firstName, before.firstName)
            take("last", original.lastName, before.lastName)
            take("title", original.title, before.title)
            take("nickname", original.nickname, before.nickname)
            take("company", original.company, before.company)
            take("position", original.position, before.position)
            take("address", original.address, before.address)
            take("notes", original.notes, before.notes)
        }
    }
    // The order they light up in: top to bottom as the page shows them.
    val litOrder = listOf("first", "last", "company", "position", "title", "nickname", "phones", "emails", "address", "notes")
    @Composable fun lit(key: String) = rememberLit(key in freshFields, litOrder.indexOf(key))
    val haptics = io.github.thelony97.orbit.core.designsystem.rememberHaptics()
    val isNew = id == null
    // The page edits a copy taken now; an undo tapped afterwards would be overwritten by Save.
    androidx.compose.runtime.LaunchedEffect(Unit) { io.github.thelony97.orbit.ui.components.Undo.clear() }

    val first = remember { TextFieldState(original.firstName) }
    val last = remember { TextFieldState(original.lastName) }
    // PLAN 9 A10: Titel, Rufname, Anrede and Du/Sie.
    val titleField = remember { TextFieldState(original.title) }
    var titleFocused by remember { mutableStateOf(false) }
    val nickname = remember { TextFieldState(original.nickname) }
    var salutation by remember { mutableStateOf(original.salutation) }
    var formal by remember { mutableStateOf(original.greeting?.formal == true) }
    // PLAN 9 A8: own ringtone in the phone book (null = the group's, else the phone's).
    var ringtone by remember { mutableStateOf(original.ringtone) }
    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val picked = result.data?.getParcelableExtra(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI, android.net.Uri::class.java)
            // „Standard“ means: no own tone, the group's or the phone's applies.
            ringtone = picked?.takeIf { !android.media.RingtoneManager.isDefault(it) }?.toString()
        }
    }
    val company = remember { TextFieldState(original.company) }
    val position = remember { TextFieldState(original.position) }
    val address = remember { TextFieldState(original.address) }
    val howWeMet = remember { TextFieldState(original.howWeMet) }
    val notes = remember { TextFieldState(original.notes) }
    val pinnedNote = remember { TextFieldState(original.pinnedNote) }
    val hours = remember { TextFieldState(original.openingHours) }
    // PLAN 9 D2: Telefonzeiten and „Urlaub bis …“ with Vertretung.
    val phoneHoursField = remember { TextFieldState(original.phoneHours) }
    var vacationUntil by remember { mutableStateOf(original.vacationUntil?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }) }
    var vacationSubstitute by remember { mutableStateOf(original.vacationSubstitute) }
    var vacationDialog by remember { mutableStateOf(false) }
    val prefixes = remember { TextFieldState(original.numberPrefixes.joinToString(", ")) }
    val domains = remember { TextFieldState(original.emailDomains.joinToString(", ")) }
    val links = remember { mutableStateListOf<PairRow>().apply { original.links.forEach { add(PairRow(it.id, it.title, it.url)) } } }
    val fields = remember { mutableStateListOf<PairRow>().apply { original.fields.forEach { add(PairRow(it.id, it.label, it.value)) } } }
    var kind by remember { mutableStateOf(original.kind) }
    var orgCategory by remember { mutableStateOf(original.orgCategory ?: Defaults.orgCategories.first().key) }
    val isOrg = kind == ContactKind.ORG
    val isMe = original.isMe
    val phones = remember { mutableStateListOf<ValueRow>().apply { original.phones.forEach { add(ValueRow(it.label, it.value, it.value.trim() in freshValues)) } } }
    val emails = remember { mutableStateListOf<ValueRow>().apply { original.emails.forEach { add(ValueRow(it.label, it.value, it.value.trim() in freshValues)) } } }
    var defaultPhone by remember { mutableStateOf(original.defaultPhone) }
    var simId by remember { mutableStateOf(original.simId) }
    // PLAN 9 B10: #31# for this contact.
    var hideNumber by remember { mutableStateOf(original.hideNumber) }
    val sims = remember { io.github.thelony97.orbit.call.Sims.list(ctx) }
    var birthday by remember { mutableStateOf(original.birthday) }
    var groups by remember { mutableStateOf(original.groupIds.toSet()) }
    var favorite by remember { mutableStateOf(original.favorite) }
    var photo by remember { mutableStateOf(original.photo) }
    var birthdayDialog by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    // Picked pictures go through the crop frame first (PLAN 3.3).
    var cropSource by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val bitmap = Photos.decode(ctx, uri)
            if (bitmap != null) {
                cropSource = bitmap
            } else {
                Toast.makeText(ctx, "Bild konnte nicht gelesen werden", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun build(): Contact {
        val phoneValues = phones.map { LabeledValue(it.label, it.state.text.toString().trim()) }.filter { it.value.isNotEmpty() }
        val emailValues = emails.map { LabeledValue(it.label, it.state.text.toString().trim()) }.filter { it.value.isNotEmpty() }
        return original.copy(
            kind = kind,
            orgCategory = if (kind == ContactKind.ORG) orgCategory else null,
            firstName = first.text.toString().trim(),
            lastName = last.text.toString().trim(),
            title = if (kind == ContactKind.ORG) original.title else titleField.text.toString().trim(),
            nickname = if (kind == ContactKind.ORG) original.nickname else nickname.text.toString().trim(),
            salutation = if (kind == ContactKind.ORG) original.salutation else salutation,
            greeting = when {
                kind == ContactKind.ORG -> original.greeting
                formal -> (original.greeting ?: io.github.thelony97.orbit.core.model.Greeting()).copy(formal = true)
                else -> original.greeting?.copy(formal = false)
            },
            ringtone = ringtone,
            company = company.text.toString().trim(),
            position = position.text.toString().trim(),
            howWeMet = howWeMet.text.toString().trim(),
            notes = notes.text.toString().trim(),
            pinnedNote = pinnedNote.text.toString().trim(),
            openingHours = if (kind == ContactKind.ORG) hours.text.toString().trim() else original.openingHours,
            phoneHours = if (kind == ContactKind.ORG) phoneHoursField.text.toString().trim() else original.phoneHours,
            vacationUntil = if (kind == ContactKind.ORG) vacationUntil?.toString() else original.vacationUntil,
            vacationSubstitute = if (kind == ContactKind.ORG && vacationUntil != null) vacationSubstitute else null,
            // Regeln (PLAN 3.6): numbers and mail addresses that belong to the organisation.
            numberPrefixes = if (kind == ContactKind.ORG) splitList(prefixes.text.toString()) else original.numberPrefixes,
            emailDomains = if (kind == ContactKind.ORG) splitList(domains.text.toString()).map { it.lowercase().removePrefix("@") } else original.emailDomains,
            // Rows keep their id, so note, logo and "open with" of a link survive editing.
            links = links.mapNotNull { row ->
                val url = row.second.text.toString().trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val full = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
                val title = row.first.text.toString().trim()
                original.links.firstOrNull { it.id == row.id }?.copy(title = title, url = full) ?: Link(row.id, title, full)
            },
            fields = fields.mapNotNull { row ->
                val value = row.second.text.toString().trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                CustomField(row.id, row.first.text.toString().trim(), value)
            },
            phones = phoneValues,
            defaultPhone = defaultPhone.coerceIn(0, (phoneValues.size - 1).coerceAtLeast(0)),
            simId = simId,
            hideNumber = hideNumber,
            emails = emailValues,
            groupIds = data.groups.map { it.id }.filter { it in groups },
            favorite = favorite,
            photo = photo,
        )
            .withAddress(address.text.toString())
            .withBirthday(birthday, ::newId)
    }

    var savedId by remember { mutableStateOf<String?>(null) }
    // „Diese Nummer gibt es schon“ (PLAN 3.14): asked once per save, then it is Jakob's call.
    var duplicate by remember { mutableStateOf<io.github.thelony97.orbit.core.domain.Duplicates.Hit?>(null) }
    var duplicateOk by remember { mutableStateOf(false) }
    val iso = remember { io.github.thelony97.orbit.domain.Phones.countryIso(ctx) }

    /** Persists; the page closes after the save button's check morph (see [leave]). */
    fun save(): Boolean {
        val contact = build()
        if ((contact.isOrg && contact.company.isBlank()) || (contact.firstName.isBlank() && contact.lastName.isBlank() && contact.company.isBlank())) {
            Toast.makeText(ctx, "Bitte einen Namen eingeben", Toast.LENGTH_SHORT).show()
            return false
        }
        // Compared with what is stored – merged-in numbers (PLAN 9 A2) count as new.
        val stored = idx.contacts[contact.id]
        if (!duplicateOk) {
            val newNumbers = contact.phones.filter { p -> stored == null || stored.phones.none { it.value == p.value } }
            io.github.thelony97.orbit.core.domain.Duplicates.sameNumber(contact.copy(phones = newNumbers), data.contacts) { io.github.thelony97.orbit.domain.Phones.normalize(it, iso) }
                ?.let { duplicate = it; return false }
        }
        val oldPhoto = original.photo
        if (oldPhoto != null && oldPhoto != contact.photo && !isNew) {
            Photos.delete(oldPhoto)
            PhotoCache.evict(oldPhoto)
        }
        val phonesChanged = stored == null || contact.phones.map { it.value } != stored.phones.map { it.value }
        OrbitRepository.edit { d -> Mutations.upsertContact(d, contact) }
        if (phonesChanged && contact.phones.isNotEmpty()) Sync.backfill(ctx, contact.id)
        savedId = contact.id
        return true
    }

    fun leave() {
        val id = savedId ?: return
        if (isNew) nav.replace(Route.Detail(id)) else nav.back()
    }

    fun discard() {
        if (photo != original.photo) Photos.delete(photo)
        if (isNew && prefill != null) Photos.delete(original.photo)
        nav.back()
    }

    BackHandler { confirmDiscard = true }

    SpaceBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            // Top bar
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OrbButton(Icons.Rounded.Close, "Abbrechen", { confirmDiscard = true }, size = 38.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    when {
                        isMe -> "Ich"
                        isNew && isOrg -> "Neue Organisation"
                        isNew -> "Neuer Kontakt"
                        else -> "Bearbeiten"
                    },
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = Space.Text,
                    modifier = Modifier.weight(1f),
                )
                SaveMorphButton(onSave = { save() }, onDone = { leave() })
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Photo + names
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        Avatar(
                            build().copy(photo = photo),
                            contactColor(original, idx),
                            84.dp,
                            shape = MaterialShapes.Cookie9Sided.toShape(),
                            glowColor = Space.Violet,
                        )
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 4.dp, y = 4.dp)
                                .size(32.dp)
                                .glow(Space.Violet, 10.dp)
                                .clip(CircleShape)
                                .background(Space.Violet)
                                .clickable {
                                    AppLock.allowExternal()
                                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.PhotoCamera, "Foto wählen", tint = Space.Void, modifier = Modifier.size(16.dp)) }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isOrg) {
                            SpaceField(company, "", placeholder = "Name (z. B. Hausarztpraxis Dr. Huber)", keyboardOptions = caps, singleLine = false, lit = lit("company"))
                            SpaceField(position, "", placeholder = "Abteilung / Ansprechpartner", keyboardOptions = caps, lit = lit("position"))
                        } else {
                            SpaceField(first, "", placeholder = if (isMe) "Dein Name" else "Vorname", keyboardOptions = caps, lit = lit("first"))
                            SpaceField(last, "", placeholder = "Nachname", keyboardOptions = caps, lit = lit("last"))
                        }
                    }
                }
                // Logo der Webseite als Bild (PLAN 3.6), offered only – it is taken on a tap.
                val logoLink = original.links.firstOrNull { it.logo != null }
                if (isOrg && photo == null && logoLink != null) {
                    Text(
                        "Logo von ${io.github.thelony97.orbit.ui.detail.linkHost(logoLink.url)} als Bild",
                        style = MaterialTheme.typography.labelLarge,
                        color = Space.Blue,
                        modifier = Modifier
                            .padding(start = 26.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable { scope.launch { io.github.thelony97.orbit.links.Logos.asPhoto(logoLink.logo!!)?.let { photo = it } } }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }
                if (photo != null) {
                    Text(
                        "Foto entfernen",
                        style = MaterialTheme.typography.labelLarge,
                        color = Space.Coral,
                        modifier = Modifier
                            .padding(start = 26.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable { photo = null }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }

                if (!isMe) {
                    GlassCard {
                        SectionTitle("Art")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SpaceChip("Mensch", !isOrg, {
                                kind = ContactKind.PERSON
                            }, leading = Icons.Rounded.Person)
                            SpaceChip("Organisation", isOrg, {
                                if (!isOrg && company.text.isBlank()) {
                                    val name = listOf(first.text, last.text).joinToString(" ").trim()
                                    if (name.isNotEmpty()) company.setTextAndPlaceCursorAtEnd(name)
                                }
                                kind = ContactKind.ORG
                            }, leading = Icons.Rounded.Business, accent = Space.Blue)
                        }
                        if (isOrg) {
                            Spacer(Modifier.size(10.dp))
                            SectionTitle("Kategorie")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Defaults.orgCategories.forEach { cat ->
                                    SpaceChip(cat.label, orgCategory == cat.key, { orgCategory = cat.key }, leading = orgIcon(cat.key), accent = Space.Blue)
                                }
                            }
                        }
                    }
                }

                // PLAN 9 A10: Titel and Rufname; Anrede and Du/Sie for everyone but yourself. Below
                // „Art“, so switching to an organisation never moves what is under the finger.
                AnimatedVisibility(
                    !isOrg,
                    enter = expandVertically(androidx.compose.animation.core.tween(Motion.MEDIUM_MS, easing = Motion.Emphasized)) +
                        fadeIn(androidx.compose.animation.core.tween(Motion.MEDIUM_MS)),
                    exit = shrinkVertically(androidx.compose.animation.core.tween(Motion.FAST_MS)) +
                        fadeOut(androidx.compose.animation.core.tween(Motion.FAST_MS)),
                ) {
                    GlassCard {
                        SectionTitle("Anrede")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SpaceField(
                                titleField, "Titel",
                                Modifier.weight(0.42f).onFocusChanged { titleFocused = it.hasFocus },
                                lit = lit("title"),
                            )
                            SpaceField(nickname, "Rufname", Modifier.weight(0.58f), keyboardOptions = caps, lit = lit("nickname"))
                        }
                        // While the title field is in use: the forms that fit Frau/Herr, narrowed by what is typed.
                        val typed = titleField.text.toString().trim()
                        val offers = titleOffers(salutation).filter { typed.isEmpty() || (it.startsWith(typed, ignoreCase = true) && !it.equals(typed, ignoreCase = true)) }
                        AnimatedVisibility(
                            titleFocused && offers.isNotEmpty(),
                            enter = expandVertically(androidx.compose.animation.core.tween(Motion.MEDIUM_MS, easing = Motion.Emphasized)) +
                                fadeIn(androidx.compose.animation.core.tween(Motion.MEDIUM_MS)),
                            exit = shrinkVertically(androidx.compose.animation.core.tween(Motion.FAST_MS)) +
                                fadeOut(androidx.compose.animation.core.tween(Motion.FAST_MS)),
                        ) {
                            FlowRow(
                                Modifier.padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                offers.forEach { t ->
                                    SpaceChip(t, false, {
                                        haptics.perform(io.github.thelony97.orbit.core.designsystem.Haptic.Tick)
                                        titleField.setTextAndPlaceCursorAtEnd(t)
                                    })
                                }
                            }
                        }
                        if (!isMe) {
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                listOf("Frau", "Herr").forEach { s ->
                                    SpaceChip(s, salutation == s, {
                                        haptics.perform(io.github.thelony97.orbit.core.designsystem.Haptic.Tick)
                                        salutation = if (salutation == s) "" else s
                                    })
                                }
                                Spacer(Modifier.weight(1f))
                                SpaceChip("Du", !formal, {
                                    haptics.perform(io.github.thelony97.orbit.core.designsystem.Haptic.Tick)
                                    formal = false
                                })
                                SpaceChip("Sie", formal, {
                                    haptics.perform(io.github.thelony97.orbit.core.designsystem.Haptic.Tick)
                                    formal = true
                                })
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    !isOrg,
                    enter = expandVertically(androidx.compose.animation.core.tween(Motion.MEDIUM_MS, easing = Motion.Emphasized)) +
                        fadeIn(androidx.compose.animation.core.tween(Motion.MEDIUM_MS)),
                    exit = shrinkVertically(androidx.compose.animation.core.tween(Motion.FAST_MS)) +
                        fadeOut(androidx.compose.animation.core.tween(Motion.FAST_MS)),
                ) {
                    GlassCard {
                        SectionTitle("Arbeit / Schule")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SpaceField(company, "", Modifier.weight(1f), placeholder = "Firma / Schule / Uni", keyboardOptions = caps, lit = if (isOrg) null else lit("company"))
                            SpaceField(position, "", Modifier.weight(1f), placeholder = "Rolle", keyboardOptions = caps, lit = if (isOrg) null else lit("position"))
                        }
                    }
                }

                GlassCard {
                    SectionTitle("Telefon")
                    ValueList(phones, Defaults.phoneLabels, "Nummer", KeyboardType.Phone, defaultPhone, { defaultPhone = it }, "Nummer hinzufügen", litOrder.indexOf("phones"))
                    // Dual-SIM pro Kontakt (PLAN 3.12) – only on a phone with two SIMs (W5).
                    if (sims.size >= 2) {
                        Spacer(Modifier.size(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SpaceChip("Automatisch", simId == null || sims.none { it.id == simId }, { simId = null })
                            sims.forEach { sim -> SpaceChip(sim.label, simId == sim.id, { simId = sim.id }) }
                        }
                    }
                    // PLAN 9 B10 Wählregel: the own number stays hidden when calling this contact.
                    if (!isMe) {
                        Spacer(Modifier.size(8.dp))
                        SpaceChip(
                            "Nummer unterdrücken", hideNumber, { hideNumber = !hideNumber },
                            leading = Icons.Rounded.VisibilityOff,
                            accent = io.github.thelony97.orbit.ui.theme.Space.Coral,
                        )
                    }
                    // Klingel-Signatur (PLAN 9 A8): lives in the phone book, so only with it (W5).
                    if (data.settings.phoneBook && !isMe) {
                        Spacer(Modifier.size(8.dp))
                        val toneName by androidx.compose.runtime.produceState<String?>(null, ringtone) {
                            value = ringtone?.let { uri ->
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    runCatching { android.media.RingtoneManager.getRingtone(ctx, android.net.Uri.parse(uri))?.getTitle(ctx) }.getOrNull()
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SpaceChip(
                                if (ringtone == null) "Klingelton" else toneName ?: "Klingelton",
                                ringtone != null,
                                {
                                    AppLock.allowExternal()
                                    ringtonePicker.launch(
                                        android.content.Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER)
                                            .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_RINGTONE)
                                            .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                            .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                            .putExtra(
                                                android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                                ringtone?.let { android.net.Uri.parse(it) } ?: android.provider.Settings.System.DEFAULT_RINGTONE_URI,
                                            ),
                                    )
                                },
                                leading = Icons.Rounded.MusicNote,
                            )
                            if (ringtone != null) ClearButton { ringtone = null }
                        }
                    }
                    Spacer(Modifier.size(8.dp))
                    SectionTitle("E-Mail")
                    ValueList(emails, Defaults.emailLabels, "E-Mail", KeyboardType.Email, null, null, "E-Mail hinzufügen", litOrder.indexOf("emails"))
                    Spacer(Modifier.size(8.dp))
                    SectionTitle("Adresse")
                    SpaceField(address, "", singleLine = false, placeholder = "Straße, PLZ Ort", keyboardOptions = caps, lit = lit("address"))
                    Spacer(Modifier.size(8.dp))
                    SectionTitle("Links")
                    PairList(
                        links, "Titel", "www.beispiel.at", "Link hinzufügen",
                        firstWeight = 0.4f,
                        secondKeyboard = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        suggestions = emptyList(),
                    )
                }

                // PLAN 3.3: Kundennummer, Versicherungsnummer, Matrikelnummer … each copyable on the page.
                GlassCard {
                    SectionTitle("Eigene Felder")
                    PairList(
                        fields, "Bezeichnung", "Wert", "Feld hinzufügen",
                        firstWeight = 0.45f,
                        secondKeyboard = KeyboardOptions.Default,
                        suggestions = if (isOrg) listOf("Kundennummer", "Vertragsnummer", "Mitgliedsnummer")
                        else listOf("Kundennummer", "Versicherungsnummer", "Matrikelnummer"),
                    )
                }

                if (isOrg) GlassCard {
                    SectionTitle("Regeln")
                    SpaceField(prefixes, "Nummernbereich", placeholder = "0732 2468")
                    Spacer(Modifier.size(8.dp))
                    SpaceField(domains, "E-Mail-Domain", placeholder = "jku.at")
                }

                // Öffnungszeiten (PLAN 3.6), shown live on the page; the line below is what it will say now.
                if (isOrg) GlassCard {
                    SectionTitle("Öffnungszeiten")
                    SpaceField(hours, "", placeholder = "Mo-Fr 08:00-12:00,13:00-17:00; Sa 09:00-12:00")
                    val week = io.github.thelony97.orbit.core.domain.OpeningHours.parse(hours.text.toString())
                    if (week != null) {
                        val st = io.github.thelony97.orbit.core.domain.OpeningHours.status(week, java.time.LocalDateTime.now(io.github.thelony97.orbit.domain.zone()))
                        Text(
                            io.github.thelony97.orbit.core.domain.OpeningHours.line(st) { d -> Fmt.weekdayShort(d) },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (st is io.github.thelony97.orbit.core.domain.OpeningHours.Status.Open) Space.Teal else Space.TextDim,
                            modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                        )
                    }
                    // PLAN 9 D2: phone hours (same writing), holidays count by themselves.
                    Spacer(Modifier.size(8.dp))
                    SpaceField(phoneHoursField, "Telefon", placeholder = "Mo-Fr 07:30-11:00")
                    Spacer(Modifier.size(8.dp))
                    // „Urlaub bis …“ with the one who stands in.
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SpaceChip(
                            vacationUntil?.let { "Urlaub bis ${it.dayOfMonth}.${it.monthValue}." } ?: "Urlaub",
                            vacationUntil != null,
                            { vacationDialog = true },
                            leading = Icons.Rounded.BeachAccess,
                            accent = Space.Orange,
                        )
                        if (vacationUntil != null) ClearButton { vacationUntil = null }
                    }
                    if (vacationUntil != null) {
                        val others = data.contacts.filter { it.isOrg && it.id != original.id && it.deletedAt == null && !it.archived && it.orgCategory == orgCategory }.take(8)
                        if (others.isNotEmpty()) {
                            Spacer(Modifier.size(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                others.forEach { o ->
                                    SpaceChip(
                                        "→ " + io.github.thelony97.orbit.domain.Logic.displayName(o, false),
                                        vacationSubstitute == o.id,
                                        { vacationSubstitute = if (vacationSubstitute == o.id) null else o.id },
                                        accent = Space.Orange,
                                    )
                                }
                            }
                        }
                    }
                }
                if (vacationDialog) {
                    io.github.thelony97.orbit.ui.sheets.DateDialog(
                        initial = vacationUntil ?: java.time.LocalDate.now().plusDays(7),
                        onDismiss = { vacationDialog = false },
                        onPick = { vacationUntil = it },
                        minDate = java.time.LocalDate.now(),
                    )
                }

                GlassCard {
                    if (!isOrg) {
                        SectionTitle("Geburtstag")
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SpaceChip(birthday?.let { Fmt.dayOfYear(it) } ?: "Datum wählen", birthday != null, { birthdayDialog = true }, leading = Icons.Rounded.Cake, accent = Space.Orange)
                            if (birthday != null) ClearButton { birthday = null }
                        }
                    }
                    if (!isMe) {
                    if (!isOrg) Spacer(Modifier.size(10.dp))
                    SectionTitle("Gruppen")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        data.groups.forEach { g ->
                            val selected = g.id in groups
                            SpaceChip(
                                g.name,
                                selected,
                                { groups = if (selected) groups - g.id else groups + g.id },
                                dot = Color(g.color),
                                accent = Color(g.color),
                            )
                        }
                    }
                    }
                }

                GlassCard {
                    SectionTitle(if (isOrg) "Notizen" else "Über")
                    if (!isMe) {
                        SpaceField(pinnedNote, "Angepinnt", placeholder = "", keyboardOptions = caps)
                        Spacer(Modifier.size(8.dp))
                    }
                    if (!isOrg && !isMe) {
                        SpaceField(howWeMet, "Wie kennengelernt", singleLine = false, keyboardOptions = caps)
                        Spacer(Modifier.size(8.dp))
                    }
                    SpaceField(notes, if (isOrg) "" else "Notizen", singleLine = false, keyboardOptions = caps, placeholder = "", lit = lit("notes"))
                    if (!isMe) Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, null, tint = Space.Orange, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Favorit", style = MaterialTheme.typography.bodyMedium, color = Space.Text, modifier = Modifier.weight(1f))
                        Switch(checked = favorite, onCheckedChange = { favorite = it }, colors = spaceSwitch())
                    }
                }
            }
        }
    }

    if (birthdayDialog) {
        DayOfYearDialog(initial = birthday, onDismiss = { birthdayDialog = false }, onPick = { birthday = it })
    }
    cropSource?.let { bitmap ->
        CropDialog(
            bitmap = bitmap,
            onDismiss = { cropSource = null },
            onCrop = { square ->
                cropSource = null
                scope.launch {
                    Photos.storeSquare(bitmap, square)?.let { name ->
                        if (photo != original.photo) Photos.delete(photo)
                        photo = name
                    }
                }
            },
        )
    }
    duplicate?.let { hit ->
        val other = data.contacts.firstOrNull { it.id == hit.contactId }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { duplicate = null },
            containerColor = Space.PanelHigh,
            title = { Text("Diese Nummer gibt es schon") },
            text = { Text(hit.number + " · " + (other?.let { io.github.thelony97.orbit.domain.Logic.displayName(it, false) + if (it.archived) " (Archiv)" else "" } ?: ""), color = Space.TextDim) },
            confirmButton = {
                io.github.thelony97.orbit.ui.components.DialogConfirm(onClick = {
                    duplicate = null
                    duplicateOk = true
                    if (save()) leave()
                }) { Text("Trotzdem speichern") }
            },
            dismissButton = {
                if (other != null) androidx.compose.material3.TextButton(onClick = { duplicate = null; nav.open(Route.Detail(other.id)) }) {
                    Text(io.github.thelony97.orbit.domain.Logic.shortName(other) + " öffnen")
                }
            },
        )
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Änderungen verwerfen?",
            text = "Was du hier eingegeben hast, wird nicht gespeichert.",
            confirm = "Verwerfen",
            onConfirm = { discard() },
            onDismiss = { confirmDiscard = false },
        )
    }
}

private val caps = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)

@Composable
fun spaceSwitch() = SwitchDefaults.colors(
    checkedThumbColor = Space.Void,
    checkedTrackColor = Space.Violet,
    checkedBorderColor = Space.Violet,
    uncheckedThumbColor = Space.TextDim,
    uncheckedTrackColor = Space.PanelHighest,
    uncheckedBorderColor = Space.LineStrong,
)

@Composable
private fun ValueList(
    rows: SnapshotStateList<ValueRow>,
    labels: List<String>,
    hint: String,
    keyboard: KeyboardType,
    defaultIndex: Int?,
    onDefault: ((Int) -> Unit)?,
    addLabel: String,
    /** Where these rows are in the order handed-over values light up (PLAN 9 A2). */
    litOrder: Int = 0,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEachIndexed { i, row -> androidx.compose.runtime.key(row) {
            var menu by remember { mutableStateOf(false) }
            // Handed-over values light up at the field's rim once, then settle (PLAN 9 A2).
            val lit = rememberLit(row.fresh, litOrder + i)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    // One label column: every value starts at the same x.
                    SpaceChip(row.label, false, { menu = true }, modifier = Modifier.widthIn(min = 84.dp), centered = true)
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Space.PanelHigh) {
                        labels.forEach { l ->
                            DropdownMenuItem(text = { Text(l) }, onClick = { row.label = l; menu = false })
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                SpaceField(row.state, "", Modifier.weight(1f), placeholder = hint, keyboardOptions = KeyboardOptions(keyboardType = keyboard), lit = lit)
                if (onDefault != null && rows.size > 1) {
                    Icon(
                        if (defaultIndex == i) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        "Standard",
                        tint = Space.Orange,
                        modifier = Modifier.size(32.dp).clip(CircleShape).clickable { onDefault(i) }.padding(7.dp),
                    )
                }
                Icon(
                    Icons.Rounded.Close, "Entfernen",
                    tint = Space.TextFaint,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable {
                            rows.removeAt(i)
                            if (onDefault != null && defaultIndex != null && defaultIndex >= rows.size) onDefault(0)
                        }
                        .padding(7.dp),
                )
            }
        } }
        SpaceChip(addLabel, false, { rows.add(ValueRow(labels.first(), "")) }, leading = Icons.Rounded.Add)
    }
}

/** Rows of two free-text fields; empty rows are dropped on save. Suggestions fill the first half. */
@Composable
private fun PairList(
    rows: SnapshotStateList<PairRow>,
    firstHint: String,
    secondHint: String,
    addLabel: String,
    firstWeight: Float,
    secondKeyboard: KeyboardOptions,
    suggestions: List<String>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEachIndexed { i, row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                SpaceField(row.first, "", Modifier.weight(firstWeight), placeholder = firstHint, keyboardOptions = caps)
                Spacer(Modifier.width(6.dp))
                SpaceField(row.second, "", Modifier.weight(1f - firstWeight), placeholder = secondHint, keyboardOptions = secondKeyboard)
                Icon(
                    Icons.Rounded.Close, "Entfernen",
                    tint = Space.TextFaint,
                    modifier = Modifier.size(32.dp).clip(CircleShape).clickable { rows.removeAt(i) }.padding(7.dp),
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SpaceChip(addLabel, false, { rows.add(PairRow(newId(), "", "")) }, leading = Icons.Rounded.Add)
            // Suggestions only while nothing is entered yet (W5: no clutter once used).
            if (rows.isEmpty()) suggestions.forEach { s ->
                SpaceChip(s, false, { rows.add(PairRow(newId(), s, "")) })
            }
        }
    }
}

/** „0732 2468, 0732 2469“ → two entries; empty pieces dropped. */
private fun splitList(text: String): List<String> = text.split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
