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
import io.github.thelony97.orbit.domain.DueState
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
)

class OrbitWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    companion object {
        suspend fun refresh(context: Context) {
            runCatching { OrbitWidget().updateAll(context) }
        }
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

    private fun rows(data: OrbitData): Pair<List<WidgetRow>, Int> {
        val idx = Index(data)
        val todayDate = today()
        val lastFirst = data.settings.sortByLastName
        val active = Logic.others(data).filterNot { it.archived }
        fun color(c: Contact) = c.groupIds.firstNotNullOfOrNull { idx.groups[it] }?.let { Color(it.color) } ?: Color(0xFF7986CB)

        val birthdays = active.mapNotNull { c ->
            val b = c.birthday ?: return@mapNotNull null
            val days = Logic.daysUntil(b, todayDate)
            if (days > 7) return@mapNotNull null
            val age = Logic.turningAge(b, todayDate)
            val sub = (if (days == 0L) "Geburtstag heute" else "Geburtstag ${Fmt.until(days)}") + (age?.let { " · wird $it" } ?: "")
            days to WidgetRow(c, Logic.displayName(c, lastFirst), sub, color(c), true)
        }.sortedBy { it.first }.map { it.second }

        val due = active.mapNotNull { c ->
            val info = Logic.due(c, idx, todayDate)
            if (info.state != DueState.DUE) return@mapNotNull null
            val since = Logic.daysSinceLastContact(c, todayDate)
            val sub = since?.let { "zuletzt ${Fmt.ago(it)}" } ?: "noch kein Kontakt"
            (since ?: Long.MAX_VALUE) to WidgetRow(c, Logic.displayName(c, lastFirst), sub, color(c), false)
        }.sortedByDescending { it.first }.map { it.second }

        return (birthdays + due) to due.size
    }

    @Composable
    private fun WidgetContent(context: Context, data: OrbitData) {
        val size = LocalSize.current
        val (rows, dueCount) = rows(data)
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
                // Compact: just the number.
                Text("● Orbit", style = TextStyle(color = ColorProvider(Space.Orange), fontSize = 13.sp, fontWeight = FontWeight.Medium))
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    "$dueCount",
                    style = TextStyle(color = ColorProvider(Space.Violet), fontSize = 40.sp, fontWeight = FontWeight.Bold),
                )
                Text(
                    if (dueCount == 1) "Person fällig" else "Personen fällig",
                    style = TextStyle(color = onSurface, fontSize = 13.sp),
                )
                val cakes = rows.count { it.birthday && it.subtitle.startsWith("Geburtstag heute") }
                if (cakes > 0) {
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
                        .background(ColorProvider(Space.Violet))
                        .cornerRadius(12.dp)
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                ) {
                    Text(
                        "$dueCount fällig",
                        style = TextStyle(color = ColorProvider(Space.Void), fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    )
                }
            }
            Spacer(GlanceModifier.height(10.dp))
            if (rows.isEmpty()) {
                Text(
                    "Alles im grünen Bereich ✨",
                    style = TextStyle(color = muted, fontSize = 14.sp),
                )
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
                            if (row.birthday) "🎂" else Logic.initials(row.contact),
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
