package io.github.thelony97.orbit.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.core.domain.OsmPlace
import io.github.thelony97.orbit.core.model.GeoPoint
import io.github.thelony97.orbit.core.model.LabeledValue
import io.github.thelony97.orbit.core.model.Link
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.newId
import io.github.thelony97.orbit.links.Osm
import io.github.thelony97.orbit.ui.theme.Space

/**
 * „Daten ergänzen“ (PLAN 3.8): what OpenStreetMap knows, each value with its own tick; only the
 * ticked ones are taken. New facts start ticked, replacements of existing ones do not.
 */
@Composable
fun OsmDialog(org: Contact, onDismiss: () -> Unit, onApply: ((Contact) -> Contact) -> Unit) {
    val ctx = LocalContext.current
    var result by remember { mutableStateOf<Osm.Result?>(null) }
    LaunchedEffect(org.id) { result = Osm.lookup(ctx, OsmPlace.query(org.company.ifBlank { org.firstName }, org.address)) }

    data class Offer(val label: String, val value: String, val replaces: Boolean, val apply: (Contact) -> Contact)
    val found = (result as? Osm.Result.Hit)?.found
    val offers = remember(found) {
        if (found == null) emptyList() else buildList {
            found.website?.let { w ->
                val have = org.links.any { linkHost(it.url) == linkHost(w) }
                if (!have) add(Offer("Webseite", w, false) { c -> c.copy(links = c.links + Link(newId(), "Webseite", w)) })
            }
            found.phone?.let { p ->
                val digits = p.filter { it.isDigit() }.takeLast(8)
                if (org.phones.none { it.value.filter { d -> d.isDigit() }.endsWith(digits) }) {
                    add(Offer("Telefon", p, false) { c -> c.copy(phones = c.phones + LabeledValue("Zentrale", p)) })
                }
            }
            found.openingHours?.takeIf { it != org.openingHours }?.let { h ->
                add(Offer("Öffnungszeiten", h, org.openingHours.isNotBlank()) { c -> c.copy(openingHours = h) })
            }
            found.address.takeIf { it.isNotBlank() && it != org.address }?.let { a ->
                add(Offer("Adresse", a, org.address.isNotBlank()) { c -> c.withAddress(a) })
            }
            val lat = found.lat
            val lon = found.lon
            if (lat != null && lon != null && org.geo == null) {
                add(Offer("Ort auf der Karte", "%.5f, %.5f".format(lat, lon), false) { c -> c.copy(geo = GeoPoint(lat, lon, c.address)) })
            }
        }
    }
    var ticked by remember(offers) { mutableStateOf(offers.indices.filter { !offers[it].replaces }.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Space.PanelHigh,
        title = { Text(found?.name?.ifBlank { null } ?: "Daten ergänzen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    result == null -> Text("Suche …", color = Space.TextDim)
                    result == Osm.Result.Offline -> Text("Keine Verbindung", color = Space.Coral)
                    found == null -> Text("Nichts gefunden", color = Space.TextDim)
                    offers.isEmpty() -> Text("Nichts Neues", color = Space.TextDim)
                }
                offers.forEachIndexed { i, o ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { ticked = if (i in ticked) ticked - i else ticked + i },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(i in ticked, { ticked = if (it) ticked + i else ticked - i })
                        Spacer(Modifier.width(4.dp))
                        Column(Modifier.weight(1f)) {
                            Text(o.value, style = MaterialTheme.typography.bodyMedium, color = Space.Text)
                            Text(o.label + if (o.replaces) " · ersetzt" else "", style = MaterialTheme.typography.labelMedium, color = Space.TextFaint)
                        }
                    }
                }
                // The licence wants the source named (ODbL).
                if (found != null) Text("Quelle: © OpenStreetMap-Mitwirkende", style = MaterialTheme.typography.labelSmall, color = Space.TextFaint, modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = {
            io.github.thelony97.orbit.ui.components.DialogConfirm(enabled = ticked.isNotEmpty(), onClick = {
                val chosen = offers.filterIndexed { i, _ -> i in ticked }
                onApply { c -> chosen.fold(c) { acc, o -> o.apply(acc) } }
                onDismiss()
            }) { Text("Übernehmen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen", color = io.github.thelony97.orbit.ui.theme.Space.TextDim) } },
    )
}
