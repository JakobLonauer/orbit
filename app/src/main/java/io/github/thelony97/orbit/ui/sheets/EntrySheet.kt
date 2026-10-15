package io.github.thelony97.orbit.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.data.Entry
import io.github.thelony97.orbit.data.EntrySource
import io.github.thelony97.orbit.data.EntryType
import io.github.thelony97.orbit.data.TypeIds
import io.github.thelony97.orbit.data.newId
import io.github.thelony97.orbit.domain.zone
import io.github.thelony97.orbit.ui.components.GhostButton
import io.github.thelony97.orbit.ui.components.GlowButton
import io.github.thelony97.orbit.ui.components.SpaceChip
import io.github.thelony97.orbit.ui.components.SpaceField
import io.github.thelony97.orbit.ui.components.entryIcon
import io.github.thelony97.orbit.ui.theme.Space
import io.github.thelony97.orbit.util.Fmt
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/** Add or edit a history entry. */
@Composable
fun EntrySheet(
    title: String,
    types: List<EntryType>,
    initial: Entry?,
    defaultType: String = TypeIds.MEET,
    onSave: (Entry) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    /** An organisation's hidden members (PLAN 3.6): the chip says who the conversation was with. */
    members: List<io.github.thelony97.orbit.core.model.Member> = emptyList(),
    initialMember: String? = null,
    /** „Mitarbeiter mit einem Feld anlegen“: creates one from the typed text, returns its id. */
    onNewMember: ((String) -> String?)? = null,
    /** PLAN 3.10 „Fotos an Einträge hängen“: the contact's pictures to choose from. */
    photos: List<io.github.thelony97.orbit.data.GalleryPhoto> = emptyList(),
    /** New pictures from the phone: imported into the contact's folder, returned to attach. */
    onAddPhotos: (suspend (List<android.net.Uri>) -> List<io.github.thelony97.orbit.data.GalleryPhoto>)? = null,
    /** PLAN 9 E8: the open topics – ticked in a Treffen or Anruf they become „Besprochen: …“ and are done. */
    topics: List<io.github.thelony97.orbit.core.model.CheckItem> = emptyList(),
    onDiscussed: (List<String>) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val start = initial?.at?.let { Instant.ofEpochMilli(it).atZone(zone()).toLocalDateTime() }
        ?: LocalDateTime.now(zone()).withSecond(0).withNano(0)
    var typeId by remember { mutableStateOf(initial?.typeId ?: defaultType) }
    var date by remember { mutableStateOf(start.toLocalDate()) }
    var time by remember { mutableStateOf(start.toLocalTime()) }
    val note = rememberTextFieldState(initial?.note.orEmpty())
    var member by remember { mutableStateOf(initial?.memberId ?: initialMember) }
    var attached by remember { mutableStateOf(initial?.attachmentIds.orEmpty()) }
    var extra by remember { mutableStateOf<List<io.github.thelony97.orbit.data.GalleryPhoto>>(emptyList()) }
    val sheetScope = androidx.compose.runtime.rememberCoroutineScope()
    val photoPicker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        val add = onAddPhotos ?: return@rememberLauncherForActivityResult
        if (uris.isNotEmpty()) sheetScope.launch {
            val added = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { add(uris) }
            extra = added + extra
            attached = attached + added.map { it.id }
        }
    }
    var discussed by remember { mutableStateOf(emptySet<String>()) }
    var addingMember by remember { mutableStateOf(false) }
    val newMember = rememberTextFieldState()
    // PLAN 9 D5 „Auftrag“: Betrag, Sterne, Rechnung.
    val amount = rememberTextFieldState(initial?.amountCents?.let { io.github.thelony97.orbit.core.domain.Jobs.format(it).removeSuffix(" €") }.orEmpty())
    var stars by remember { androidx.compose.runtime.mutableIntStateOf(initial?.stars ?: 0) }
    val invoicePicker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        val add = onAddPhotos ?: return@rememberLauncherForActivityResult
        if (uri != null) sheetScope.launch {
            val added = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { add(listOf(uri)) }
            extra = added + extra
            attached = attached + added.map { it.id }
        }
    }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val fromSystem = initial != null && initial.source != EntrySource.MANUAL

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Space.PanelHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLargeEmphasized, color = Space.Text)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // "Gratuliert"/"Geschenk" are ticked in the birthday tab (R3), not logged here.
                types.filter { it.id !in occasionTypes || it.id == initial?.typeId }.forEach { t ->
                    SpaceChip(t.name, typeId == t.id, { typeId = t.id }, leading = entryIcon(t.icon))
                }
            }

            if (!fromSystem) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpaceChip(
                        if (date == LocalDate.now(zone())) "Heute" else Fmt.weekdayDate(date),
                        false, { pickDate = true }, leading = Icons.Rounded.CalendarMonth,
                    )
                    SpaceChip("%02d:%02d".format(time.hour, time.minute), false, { pickTime = true }, leading = Icons.Rounded.Schedule)
                }
            } else {
                Text(Fmt.dateTime(initial!!.at), style = MaterialTheme.typography.labelLarge, color = Space.TextDim)
            }

            // Mit wem (hidden members only; tap again = the organisation itself).
            if (members.isNotEmpty() || onNewMember != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    members.forEach { m ->
                        SpaceChip(m.name, member == m.id, { member = if (member == m.id) null else m.id }, accent = Space.Blue)
                    }
                    if (onNewMember != null && !addingMember) {
                        SpaceChip("Mitarbeiter", false, { addingMember = true }, leading = Icons.Rounded.Add, accent = Space.Blue)
                    }
                }
                if (addingMember && onNewMember != null) {
                    io.github.thelony97.orbit.ui.components.SpaceSearchField(
                        state = newMember,
                        placeholder = "Name, Rolle",
                        leading = Icons.Rounded.Add,
                        onSubmit = {
                            onNewMember(newMember.text.toString())?.let { member = it }
                            newMember.edit { replace(0, length, "") }
                            addingMember = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            val job = typeId == io.github.thelony97.orbit.core.model.BuiltinTypes.JOB
            SpaceField(note, if (job) "Was" else "Notiz", singleLine = false, placeholder = if (job) "Therme Service" else "Worüber habt ihr gesprochen?")
            val talk = typeId == TypeIds.MEET || typeId == TypeIds.CALL || typeId == TypeIds.VIDEO
            val open = topics.filter { !it.done }
            if (initial == null && talk && open.isNotEmpty()) {
                // Themen abhaken (PLAN 9 E8): each tick a Tick, the ticked ones lit.
                val tick = io.github.thelony97.orbit.core.designsystem.rememberHaptics()
                Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    open.forEach { t ->
                        val on = t.id in discussed
                        io.github.thelony97.orbit.ui.components.SpaceChip(
                            t.text, on,
                            {
                                tick.perform(io.github.thelony97.orbit.core.designsystem.Haptic.Tick)
                                discussed = if (on) discussed - t.id else discussed + t.id
                            },
                            leading = if (on) Icons.Rounded.Check else null,
                        )
                    }
                }
            }
            if (job) {
                JobFields(
                    amount, stars, { stars = it },
                    invoice = (extra + photos).firstOrNull { it.id in attached && !it.mime.startsWith("image/") }?.let { "Rechnung" },
                    onInvoice = if (onAddPhotos == null) null else {
                        {
                            io.github.thelony97.orbit.security.AppLock.allowExternal()
                            invoicePicker.launch(arrayOf("application/pdf", "image/*"))
                        }
                    },
                )
            }

            // Fotos am Eintrag (PLAN 3.10): tap selects, „+“ brings new ones from the phone.
            if (onAddPhotos != null || photos.isNotEmpty()) {
                val choices = (extra + photos.filter { it.mime.startsWith("image/") }).distinctBy { it.id }
                    .sortedByDescending { it.id in attached }.take(60)
                Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (onAddPhotos != null) {
                        Box(
                            Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Space.Panel)
                                .clickable {
                                    io.github.thelony97.orbit.security.AppLock.allowExternal()
                                    photoPicker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.AddPhotoAlternate, "Foto anhängen", tint = Space.Violet) }
                    }
                    choices.forEach { p ->
                        val on = p.id in attached
                        Box(
                            Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))
                                .border(2.dp, if (on) Space.Violet else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(12.dp))
                                .clickable { attached = if (on) attached - p.id else attached + p.id },
                        ) {
                            io.github.thelony97.orbit.ui.detail.PhotoThumb(p.file, Modifier.fillMaxSize().graphicsLayer { alpha = if (on) 1f else 0.55f })
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (onDelete != null) {
                    GhostButton("Löschen", { onDelete(); onDismiss() }, icon = Icons.Rounded.Delete, color = Space.Coral)
                }
                Spacer(Modifier.weight(1f))
                GlowButton("Speichern", {
                    val at = LocalDateTime.of(date, time).atZone(zone()).toInstant().toEpochMilli()
                    val talkType = typeId == TypeIds.MEET || typeId == TypeIds.CALL || typeId == TypeIds.VIDEO
                    val talked = if (talkType) topics.filter { it.id in discussed && !it.done } else emptyList()
                    val typed = note.text.toString().trim()
                    val text = if (initial == null && talked.isNotEmpty()) {
                        listOf(typed, "Besprochen: " + talked.joinToString(", ") { it.text }).filter { it.isNotEmpty() }.joinToString("\n")
                    } else {
                        typed
                    }
                    if (initial == null && talked.isNotEmpty()) onDiscussed(talked.map { it.id })
                    val isJob = typeId == io.github.thelony97.orbit.core.model.BuiltinTypes.JOB
                    val cents = if (isJob) io.github.thelony97.orbit.core.domain.Jobs.parseAmount(amount.text.toString()) else null
                    val rated = if (isJob) stars.takeIf { it > 0 } else null
                    val entry = if (initial != null) {
                        initial.copy(
                            typeId = typeId, at = if (fromSystem) initial.at else at, note = text, memberId = member, attachmentIds = attached,
                            amountCents = cents, stars = rated,
                        )
                    } else {
                        // contactId is set by Mutations.addEntry.
                        Entry(id = newId(), contactId = "", typeId = typeId, at = at, note = text, memberId = member, attachmentIds = attached, amountCents = cents, stars = rated)
                    }
                    onSave(entry)
                    onDismiss()
                }, icon = Icons.Rounded.Check)
            }
        }
    }

    if (pickDate) DateDialog(initial = date, onDismiss = { pickDate = false }, onPick = { date = it })
    if (pickTime) TimeDialog(initial = time, onDismiss = { pickTime = false }, onPick = { time = it })
}

private val occasionTypes = setOf(
    io.github.thelony97.orbit.core.model.BuiltinTypes.CONGRATS,
    io.github.thelony97.orbit.core.model.BuiltinTypes.GIFT,
    // PLAN 9 D6: the Beleg of a settled loan comes from the Verliehen section.
    io.github.thelony97.orbit.core.model.BuiltinTypes.SETTLED,
)

/** PLAN 9 D5: Betrag (as typed, „340“ or „1.240,50“), up to five stars (tap the same again = none), the invoice. */
@Composable
private fun JobFields(
    amount: androidx.compose.foundation.text.input.TextFieldState,
    stars: Int,
    onStars: (Int) -> Unit,
    invoice: String?,
    onInvoice: (() -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SpaceField(
            amount, "Betrag €", Modifier.weight(1f), placeholder = "340",
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
        )
        Row {
            for (n in 1..io.github.thelony97.orbit.core.domain.Jobs.MAX_STARS) {
                Icon(
                    if (n <= stars) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    "$n Sterne",
                    tint = if (n <= stars) Space.Orange else Space.TextFaint,
                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(50)).clickable { onStars(if (stars == n) 0 else n) }.padding(5.dp),
                )
            }
        }
    }
    if (onInvoice != null) {
        SpaceChip(invoice ?: "Rechnung", invoice != null, onInvoice, leading = Icons.Rounded.Receipt, accent = Space.Orange)
    }
}
