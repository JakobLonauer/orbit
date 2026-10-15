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
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.ui.theme.Space
import io.github.thelony97.orbit.ui.today.TodayCard

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

/**
 * Heute als Widget (PLAN 9 C1): the same cards as the row in the app – Rückrufe, Dranbleiben,
 * Treffen, Anlässe, Themen, „Heute vor N Jahren“ – each with its planet colour; a tap opens it.
 */
class TodayWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    companion object {
        suspend fun refresh(context: Context) {
            runCatching { TodayWidget().updateAll(context) }
        }
    }

    private data class Line(val title: String, val line: String?, val color: Color, val open: Intent)

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        OrbitRepository.init(context)
        val d = OrbitRepository.ensureLoaded()
        provideContent { GlanceTheme { Content(context, d, 0) } }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        OrbitRepository.init(context)
        val initial = OrbitRepository.ensureLoaded()
        val callbacks = runCatching { io.github.thelony97.orbit.call.Callbacks.load(context, initial).size }.getOrDefault(0)
        provideContent {
            val data by OrbitRepository.data.collectAsState(initial)
            GlanceTheme { Content(context, data ?: initial, callbacks) }
        }
    }

    private fun rows(context: Context, data: OrbitData, callbacks: Int): List<Line> = buildList {
        if (callbacks > 0) {
            add(
                Line(
                    if (callbacks == 1) "1 Rückruf" else "$callbacks Rückrufe", null, Space.Violet,
                    Intent(context, MainActivity::class.java).setAction(io.github.thelony97.orbit.actions.AppShortcuts.ACTION_CALLBACKS),
                ),
            )
        }
        val today = io.github.thelony97.orbit.domain.today()
        io.github.thelony97.orbit.call.Persist.all(context)
            .filter { java.time.Instant.ofEpochMilli(it.at).atZone(io.github.thelony97.orbit.domain.zone()).toLocalDate() == today }
            .forEach { p -> add(Line(io.github.thelony97.orbit.util.Fmt.time(p.at) + " · " + p.name, "Dranbleiben", Space.Teal, open(context, p.contactId))) }
        io.github.thelony97.orbit.ui.today.planCards(data).filterIsInstance<TodayCard.Plan>().forEach { card ->
            val (title, line) = io.github.thelony97.orbit.ui.today.planLines(card, Logic.shortName(card.contact))
            val color = if (card.item is io.github.thelony97.orbit.core.domain.TodayPlan.Occasion || card.item is io.github.thelony97.orbit.core.domain.TodayPlan.Deadline) Space.Orange
            else card.color?.let { Color(it) } ?: io.github.thelony97.orbit.ui.components.fallbackColor(card.contact.id)
            add(Line(title, line, color, open(context, card.contact.id)))
        }
    }

    private fun open(context: Context, contactId: String?): Intent = Intent(context, MainActivity::class.java).apply {
        if (contactId != null) {
            data = Uri.parse("orbit://contact/$contactId")
            putExtra(MainActivity.EXTRA_CONTACT, contactId)
        }
    }

    @Composable
    private fun Content(context: Context, data: OrbitData, callbacks: Int) {
        val size = LocalSize.current
        val rows = rows(context, data, callbacks)
        Column(
            GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(ColorProvider(Color(0xF20B0B15)))
                .cornerRadius(24.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text("Heute", style = TextStyle(color = ColorProvider(Space.TextDim), fontSize = 12.sp, fontWeight = FontWeight.Medium))
            Spacer(GlanceModifier.height(6.dp))
            if (rows.isEmpty()) {
                Box(GlanceModifier.fillMaxSize().clickable(actionStartActivity(Intent(context, MainActivity::class.java))), contentAlignment = Alignment.Center) {
                    Text("–", style = TextStyle(color = ColorProvider(Space.TextFaint), fontSize = 22.sp))
                }
                return@Column
            }
            val fit = ((size.height.value - 40) / 46).toInt().coerceAtLeast(1)
            rows.take(fit).forEach { r ->
                Row(GlanceModifier.fillMaxWidth().height(46.dp).clickable(actionStartActivity(r.open)), verticalAlignment = Alignment.CenterVertically) {
                    Box(GlanceModifier.size(10.dp).background(ColorProvider(r.color)).cornerRadius(5.dp)) {}
                    Spacer(GlanceModifier.width(10.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Text(r.title, maxLines = 1, style = TextStyle(color = ColorProvider(Space.Text), fontSize = 14.sp, fontWeight = FontWeight.Medium))
                        r.line?.let { Text(it, maxLines = 1, style = TextStyle(color = ColorProvider(Space.TextDim), fontSize = 12.sp)) }
                    }
                }
            }
        }
    }
}
