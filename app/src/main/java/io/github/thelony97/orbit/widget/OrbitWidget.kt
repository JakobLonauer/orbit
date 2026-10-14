package io.github.thelony97.orbit.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.thelony97.orbit.MainActivity
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.core.domain.AtFormat
import io.github.thelony97.orbit.domain.Index
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.domain.today
import io.github.thelony97.orbit.ui.theme.Space
import io.github.thelony97.orbit.util.Fmt

class OrbitWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OrbitWidget()
}

private data class WidgetRow(
    val contact: Contact,
    val title: String,
    val subtitle: String,
    val color: Color,
    val birthday: Boolean,
    val days: Long,
    /** From the pinned occasions (the header then says „Anlässe“). */
    val pinned: Boolean = false,
)

class OrbitWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    companion object {
        suspend fun refresh(context: Context) {
            runCatching { OrbitWidget().updateAll(context) }
        }
    }

    /** Widget picker preview with your own data (PLAN 1.3): one composition, no flows. */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        OrbitRepository.init(context)
        val d = OrbitRepository.ensureLoaded()
        provideContent { GlanceTheme { WidgetContent(context, d) } }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        OrbitRepository.init(context)
        val initial = OrbitRepository.ensureLoaded()
        provideContent {
            val data by OrbitRepository.data.collectAsState(initial)
            GlanceTheme {
                WidgetContent(context, data ?: initial)
            }
        }
    }

    /**
     * Anlässe-Widget (PLAN 3.0): the next pinned occasions of every kind; without pinned ones the
     * next birthdays. Never archived contacts; organisations only while they are shown.
     */
    private fun rows(data: OrbitData): List<WidgetRow> {
        val idx = Index(data)
        val todayDate = today()
        val lastFirst = data.settings.sortByLastName
        fun color(c: Contact) = c.groupIds.firstNotNullOfOrNull { idx.groups[it] }?.let { Color(it.color) } ?: Color(0xFF7986CB)
        val pinned = io.github.thelony97.orbit.core.domain.UpcomingIndex.build(data.contacts, todayDate) {
            io.github.thelony97.orbit.core.domain.Visibility.inLists(it, data.settings.showOrgs)
        }.filter { it.occasion.pinned }
        if (pinned.isNotEmpty()) {
            val nameOf = { id: String -> idx.contacts[id]?.let { Logic.shortName(it) }.orEmpty() }
            return pinned.mapNotNull { u ->
                val c = idx.contacts[u.ownerId] ?: return@mapNotNull null
                val sub = if (u.daysUntil == 0L) "heute" else "${Fmt.until(u.daysUntil)} · ${AtFormat.weekdayDate(u.date)}"
                WidgetRow(
                    c, io.github.thelony97.orbit.core.domain.ReminderPlan.phrase(u, nameOf), sub, color(c),
                    birthday = u.occasion.kind == io.github.thelony97.orbit.core.model.OccasionKind.BIRTHDAY, days = u.daysUntil, pinned = true,
                )
            }.sortedWith(compareBy({ it.days }, { it.title }))
        }
        return Logic.others(data).filter { !it.archived && !it.isOrg }.mapNotNull { c ->
            val b = c.birthday ?: return@mapNotNull null
            val days = Logic.daysUntil(b, todayDate)
            val age = Logic.turningAge(b, todayDate)
            val date = Logic.nextOccurrence(b, todayDate)
            val sub = (if (days == 0L) "heute" else "${Fmt.until(days)} · ${AtFormat.weekdayDate(date)}") +
                (age?.let { " · wird $it" } ?: "")
            WidgetRow(c, Logic.displayName(c, lastFirst), sub, color(c), birthday = true, days = days)
        }.sortedWith(compareBy({ it.days }, { it.title }))
            // The next 60 days; if that is empty, at least the next birthday.
            .let { all -> all.filter { it.days <= 60 }.ifEmpty { all.take(1) } }
    }

    @Composable
    private fun WidgetContent(context: Context, data: OrbitData) {
        val size = LocalSize.current
        val rows = rows(data)
        val next = rows.firstOrNull()
        val openApp = actionStartActivity(Intent(context, MainActivity::class.java))
        val onSurface = ColorProvider(Space.Text)
        val muted = ColorProvider(Space.TextDim)

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(ColorProvider(Color(0xF20B0B15)))
                .cornerRadius(24.dp)
                .padding(14.dp)
                .clickable(openApp),
        ) {
            if (size.width < 170.dp || size.height < 120.dp) {
                // Compact: the next birthday as one big number.
                Text("● Orbit", style = TextStyle(color = ColorProvider(Space.Orange), fontSize = 13.sp, fontWeight = FontWeight.Medium))
                Spacer(GlanceModifier.height(4.dp))
                if (next == null) {
                    Text("–", style = TextStyle(color = muted, fontSize = 40.sp, fontWeight = FontWeight.Bold))
                    return@Column
                }
                Text(
                    if (next.days == 0L) (if (next.birthday) "🎂" else "★") else "${next.days}",
                    style = TextStyle(color = ColorProvider(Space.Orange), fontSize = 40.sp, fontWeight = FontWeight.Bold),
                )
                Text(
                    if (next.days == 0L) Logic.shortName(next.contact) else "${if (next.days == 1L) "Tag" else "Tage"} · ${Logic.shortName(next.contact)}",
                    maxLines = 1,
                    style = TextStyle(color = onSurface, fontSize = 13.sp),
                )
                val cakes = rows.count { it.days == 0L }
                if (cakes > 1) {
                    Text("🎂 $cakes heute", style = TextStyle(color = muted, fontSize = 12.sp))
                }
                return@Column
            }

            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "● Orbit",
                    style = TextStyle(color = onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier.defaultWeight(),
                )
                Box(
                    modifier = GlanceModifier
                        .background(ColorProvider(Space.Orange))
                        .cornerRadius(12.dp)
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                ) {
                    Text(
                        if (rows.firstOrNull()?.pinned == true) "Anlässe" else "Geburtstage",
                        style = TextStyle(color = ColorProvider(Space.Void), fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    )
                }
            }
            Spacer(GlanceModifier.height(10.dp))
            if (rows.isEmpty()) {
                Text("Keine Geburtstage eingetragen", style = TextStyle(color = muted, fontSize = 14.sp))
                return@Column
            }
            val fit = ((size.height.value - 70) / 44).toInt().coerceAtLeast(1)
            rows.take(fit).forEach { row ->
                val open = actionStartActivity(
                    Intent(context, MainActivity::class.java)
                        .setData(Uri.parse("orbit://contact/${row.contact.id}"))
                        .putExtra(MainActivity.EXTRA_CONTACT, row.contact.id),
                )
                Row(
                    modifier = GlanceModifier.fillMaxWidth().height(44.dp).clickable(open),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = GlanceModifier.size(32.dp).background(row.color).cornerRadius(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (row.days == 0L) (if (row.birthday) "🎂" else "★") else Logic.initials(row.contact),
                            style = TextStyle(color = ColorProvider(Color.White), fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        )
                    }
                    Spacer(GlanceModifier.width(10.dp))
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(row.title, maxLines = 1, style = TextStyle(color = onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium))
                        Text(row.subtitle, maxLines = 1, style = TextStyle(color = muted, fontSize = 12.sp))
                    }
                }
            }
            if (rows.size > fit) {
                Text("+${rows.size - fit} weitere", style = TextStyle(color = muted, fontSize = 12.sp))
            }
        }
    }
}
