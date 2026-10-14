package io.github.thelony97.orbit.ui.occasions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.core.designsystem.Corners
import io.github.thelony97.orbit.core.domain.BirthdayTab
import io.github.thelony97.orbit.core.domain.Visibility
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.domain.Index
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.domain.today
import io.github.thelony97.orbit.ui.Nav
import io.github.thelony97.orbit.ui.Route
import io.github.thelony97.orbit.ui.components.Avatar
import io.github.thelony97.orbit.ui.components.contactColor
import io.github.thelony97.orbit.ui.components.glow
import io.github.thelony97.orbit.ui.home.BottomBarSpace
import io.github.thelony97.orbit.ui.theme.Space
import java.time.LocalDate

/**
 * Reiter „Geburtstage“ des Anlässe-Tabs (PLAN 3.5), erste Fassung: alle Menschen mit Geburtstag
 * ab heute in Abschnitten, Datumskachel mit Wochentag, „wird 31“, Bezug zu heute. Jahresrad,
 * Gratuliert/Geschenk und die Eingabe für „ohne Geburtstag“ folgen; noch nicht in der Leiste.
 */
@Composable
fun BirthdaysScreen(data: OrbitData, idx: Index, nav: Nav, todayDate: LocalDate = today()) {
    val lastFirst = data.settings.sortByLastName
    val result = remember(data, todayDate) {
        BirthdayTab.build(data.contacts, todayDate, visible = { Visibility.inLists(it, showOrgs = false) }, name = { Logic.displayName(it, lastFirst) })
    }
    val byId = remember(data) { data.contacts.associateBy { it.id } }
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = BottomBarSpace + 16.dp),
    ) {
        item(key = "header") {
            Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp).height(38.dp), contentAlignment = Alignment.CenterStart) {
                Text("Geburtstage", style = MaterialTheme.typography.headlineSmallEmphasized, color = Space.Text)
            }
        }
        result.groups.forEach { g ->
            item(key = "s-${g.section}-${g.title}") {
                Text(
                    g.title,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = if (g.section == BirthdayTab.Section.TODAY) Space.Orange else Space.TextDim,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            g.rows.forEach { r ->
                val c = byId[r.contactId] ?: return@forEach
                item(key = "b-${r.contactId}") {
                    BirthdayRow(r, c, idx, lastFirst, Modifier.animateItem()) { nav.open(Route.Detail(c.id)) }
                }
            }
        }
        if (result.withoutBirthday > 0) {
            item(key = "without") {
                Text(
                    if (result.withoutBirthday == 1) "1 Kontakt ohne Geburtstag" else "${result.withoutBirthday} Kontakte ohne Geburtstag",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Space.TextFaint,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp),
                )
            }
        }
    }
}

private val WEEKDAYS = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")
private val SHORT_MONTHS = listOf("JÄN", "FEB", "MÄR", "APR", "MAI", "JUN", "JUL", "AUG", "SEP", "OKT", "NOV", "DEZ")

/** „heute“, „morgen“, „gestern“, „in 5 Tagen“, „vor 3 Tagen“. */
fun relativeDay(days: Long): String = when {
    days == 0L -> "heute"
    days == 1L -> "morgen"
    days == -1L -> "gestern"
    days < 0 -> "vor ${-days} Tagen"
    else -> "in $days Tagen"
}

@Composable
private fun BirthdayRow(r: BirthdayTab.Row, c: Contact, idx: Index, lastFirst: Boolean, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val today = r.daysUntil == 0L
    val past = r.daysUntil < 0
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(Corners.Row)
            .clickable(onClick = onOpen)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DateTile(r.date, today, past)
        Spacer(Modifier.width(12.dp))
        Avatar(c, contactColor(c, idx), 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                Logic.displayName(c, lastFirst),
                style = MaterialTheme.typography.bodyLarge,
                color = if (past) Space.TextDim else Space.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // The weekday only where it helps: „heute/morgen/gestern“ on the right says it already.
                listOfNotNull(
                    WEEKDAYS[r.date.dayOfWeek.value - 1].takeIf { kotlin.math.abs(r.daysUntil) > 1 },
                    r.turning?.let { if (past) "$it geworden" else "wird $it" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = Space.TextFaint,
            )
        }
        Text(
            relativeDay(r.daysUntil),
            style = MaterialTheme.typography.labelMedium,
            color = if (today) Space.Orange else Space.TextDim,
        )
    }
}

/** Day and month on a small tile; today glows in the sun's colour. */
@Composable
private fun DateTile(date: LocalDate, today: Boolean, past: Boolean) {
    val accent = if (today) Space.Orange else Space.Violet
    Column(
        Modifier
            .size(44.dp)
            .then(if (today) Modifier.glow(Space.Orange, 12.dp, Corners.Row, 0.5f) else Modifier)
            .clip(Corners.Row)
            .background(if (today) Space.Orange.copy(alpha = 0.18f) else Space.PanelHigh.copy(alpha = if (past) 0.5f else 1f))
            .border(1.dp, if (today) Space.Orange.copy(alpha = 0.6f) else Space.Line, Corners.Row),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            color = if (past) Space.TextDim else Space.Text,
        )
        Text(SHORT_MONTHS[date.monthValue - 1], style = MaterialTheme.typography.labelSmall, color = if (past) Space.TextFaint else accent.copy(alpha = 0.9f))
    }
}
