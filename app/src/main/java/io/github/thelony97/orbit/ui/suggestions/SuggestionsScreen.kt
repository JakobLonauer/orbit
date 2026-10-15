package io.github.thelony97.orbit.ui.suggestions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.actions.ContactActions
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.ContactKind
import io.github.thelony97.orbit.data.LabeledValue
import io.github.thelony97.orbit.data.Mutations
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.data.newId
import io.github.thelony97.orbit.domain.Phones
import io.github.thelony97.orbit.domain.toLocalDate
import io.github.thelony97.orbit.domain.today
import io.github.thelony97.orbit.ui.Nav
import io.github.thelony97.orbit.ui.Route
import io.github.thelony97.orbit.ui.components.EmptyHint
import io.github.thelony97.orbit.util.Fmt
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import io.github.thelony97.orbit.ui.components.OrbButton
import io.github.thelony97.orbit.ui.components.SpaceBackground
import io.github.thelony97.orbit.ui.components.glass
import io.github.thelony97.orbit.ui.theme.Space
import java.time.temporal.ChronoUnit

@Composable
fun SuggestionsScreen(data: OrbitData, nav: Nav) {
    val ctx = LocalContext.current
    val iso = Phones.countryIso(ctx)
    val list = data.unknownNumbers
        .filter { !it.dismissed && it.calls + it.sms >= 2 }
        .sortedWith(compareByDescending<io.github.thelony97.orbit.data.UnknownNumber> { it.calls + it.sms }.thenByDescending { it.lastAt })

    SpaceBackground {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OrbButton(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück", nav.back, size = 38.dp)
                Spacer(Modifier.width(12.dp))
                Text("Vorschläge", style = MaterialTheme.typography.titleLargeEmphasized, color = Space.Text)
            }
        },
    ) { padding ->
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyHint("Keine Vorschläge", "")
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 32.dp)) {
            items(list, key = { it.key }) { u ->
                val days = ChronoUnit.DAYS.between(u.lastAt.toLocalDate(), today())
                // PLAN 9 B1: what the number tells offline, and Herold's answer if Orbit already asked.
                val known by androidx.compose.runtime.produceState<Pair<io.github.thelony97.orbit.core.domain.NumberInfo.Info?, io.github.thelony97.orbit.core.domain.Herold.Hit?>?>(null, u.key) {
                    value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val info = runCatching { io.github.thelony97.orbit.core.domain.NumberInfo.of(u.display, iso) }.getOrNull()
                        info to info?.let { io.github.thelony97.orbit.call.CallerLookup.known(ctx, it.e164)?.hit }
                    }
                }
                val info = known?.first
                val hit = known?.second
                val title = u.cachedName ?: hit?.name
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp).glass(RoundedCornerShape(20.dp)),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title ?: Phones.pretty(u.display, iso), style = MaterialTheme.typography.titleMediumEmphasized)
                        val line = listOfNotNull(
                            Phones.pretty(u.display, iso).takeIf { title != null },
                            hit?.category?.takeIf { it.isNotBlank() } ?: info?.line?.takeIf { it.isNotBlank() },
                        ).joinToString(" · ")
                        if (line.isNotBlank()) Text(line, style = MaterialTheme.typography.bodyMedium, color = if (info?.costs == true) Space.Orange else Space.TextDim)
                        Text(
                            listOfNotNull(
                                u.calls.takeIf { it > 0 }?.let { if (it == 1) "1 Anruf" else "$it Anrufe" },
                                u.sms.takeIf { it > 0 }?.let { if (it == 1) "1 SMS" else "$it SMS" },
                                "zuletzt ${Fmt.ago(days)}",
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                            FilledTonalButton(onClick = {
                                val name = u.cachedName.orEmpty().trim()
                                val first = name.substringBefore(' ')
                                val last = if (name.contains(' ')) name.substringAfter(' ') else ""
                                val prefill = Contact(
                                    id = newId(),
                                    firstName = first,
                                    lastName = last,
                                    phones = listOf(LabeledValue("Mobil", u.display)),
                                )
                                nav.open(Route.Edit(prefill = OrbitRepository.json.encodeToString(Contact.serializer(), prefill)))
                            }) { Text("Mensch") }
                            FilledTonalButton(onClick = {
                                // Herold's name, address and trade come along (PLAN 9 B1).
                                val prefill = if (hit != null) {
                                    io.github.thelony97.orbit.call.HeroldDraft.of(u.display, org = true, hit = hit)
                                } else {
                                    Contact(
                                        id = newId(),
                                        kind = ContactKind.ORG,
                                        company = u.cachedName.orEmpty().trim(),
                                        phones = listOf(LabeledValue("Arbeit", u.display)),
                                    )
                                }
                                nav.open(Route.Edit(prefill = OrbitRepository.json.encodeToString(Contact.serializer(), prefill)))
                            }) { Text("Organisation") }
                            TextButton(onClick = { ContactActions.dialNumber(ctx, u.display) }) { Text("Anrufen") }
                            TextButton(onClick = { OrbitRepository.edit { d -> Mutations.dismissUnknown(d, u.key) } }) { Text("Ignorieren") }
                        }
                    }
                }
            }
        }
    }
}
}
