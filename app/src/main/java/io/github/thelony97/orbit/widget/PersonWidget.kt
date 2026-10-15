package io.github.thelony97.orbit.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
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
import io.github.thelony97.orbit.R
import io.github.thelony97.orbit.actions.ContactActions
import io.github.thelony97.orbit.core.domain.AtFormat
import io.github.thelony97.orbit.core.domain.ReminderPlan
import io.github.thelony97.orbit.core.domain.UpcomingIndex
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.domain.today
import io.github.thelony97.orbit.notify.CallActionActivity
import io.github.thelony97.orbit.ui.theme.OrbitTheme
import io.github.thelony97.orbit.ui.theme.Space
import java.time.temporal.ChronoUnit

class PersonWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PersonWidget()
}

/** Which contact each placed Person-Widget shows. */
object PersonWidgets {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("person-widgets", Context.MODE_PRIVATE)
    fun set(ctx: Context, widgetId: Int, contactId: String) = prefs(ctx).edit().putString(widgetId.toString(), contactId).apply()
    fun get(ctx: Context, widgetId: Int): String? = prefs(ctx).getString(widgetId.toString(), null)
}

/** Person-Widget (PLAN 9 E18): photo, name, Anrufen/WhatsApp, the next occasion and when you last had contact. */
class PersonWidget : GlanceAppWidget() {

    companion object {
        suspend fun refresh(context: Context) {
            runCatching { PersonWidget().updateAll(context) }
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        OrbitRepository.init(context)
        val initial = OrbitRepository.ensureLoaded()
        val widgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrDefault(-1)
        provideContent {
            val live by OrbitRepository.data.collectAsState(initial)
            val data = live ?: initial
            val c = PersonWidgets.get(context, widgetId)?.let { cid -> data.contacts.firstOrNull { it.id == cid && it.deletedAt == null } }
            GlanceTheme { if (c != null) Content(context, c, data) else Empty(context) }
        }
    }

    @Composable
    private fun Empty(context: Context) {
        Box(
            GlanceModifier.fillMaxSize().appWidgetBackground().background(ColorProvider(Color(0xF20B0B15))).cornerRadius(24.dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
            contentAlignment = Alignment.Center,
        ) { Text("Orbit", style = TextStyle(color = ColorProvider(Space.TextDim), fontSize = 13.sp)) }
    }

    @Composable
    private fun Content(context: Context, c: Contact, data: OrbitData) {
        val open = actionStartActivity(Intent(context, MainActivity::class.java).setData(Uri.parse("orbit://contact/${c.id}")).putExtra(MainActivity.EXTRA_CONTACT, c.id))
        val today = today()
        val next = UpcomingIndex.build(data.contacts, today) { it.deletedAt == null }.filter { it.ownerId == c.id || it.withId == c.id }.minByOrNull { it.daysUntil }
        val last = c.entries.maxByOrNull { it.at }?.let { e ->
            val d = java.time.Instant.ofEpochMilli(e.at).atZone(io.github.thelony97.orbit.domain.zone()).toLocalDate()
            "zuletzt " + AtFormat.ago(ChronoUnit.DAYS.between(d, today))
        }
        Column(
            GlanceModifier.fillMaxSize().appWidgetBackground().background(ColorProvider(Color(0xF20B0B15))).cornerRadius(24.dp).padding(12.dp),
        ) {
            Row(GlanceModifier.fillMaxWidth().clickable(open), verticalAlignment = Alignment.CenterVertically) {
                val photo = c.photo?.let { thumb(it) }
                if (photo != null) {
                    Image(ImageProvider(photo), null, GlanceModifier.size(52.dp).cornerRadius(26.dp))
                } else {
                    Box(GlanceModifier.size(52.dp).background(ColorProvider(Color(0xFF7986CB))).cornerRadius(26.dp), contentAlignment = Alignment.Center) {
                        Text(Logic.initials(c), style = TextStyle(color = ColorProvider(Color.White), fontSize = 18.sp, fontWeight = FontWeight.Bold))
                    }
                }
                Spacer(GlanceModifier.width(10.dp))
                Column {
                    Text(Logic.shortName(c), maxLines = 1, style = TextStyle(color = ColorProvider(Space.Text), fontSize = 16.sp, fontWeight = FontWeight.Medium))
                    next?.let { u ->
                        val name = { id: String -> data.contacts.firstOrNull { it.id == id }?.let { Logic.shortName(it) }.orEmpty() }
                        Text(ReminderPlan.phrase(u, name) + " · " + AtFormat.until(u.daysUntil), maxLines = 1, style = TextStyle(color = ColorProvider(Space.Orange), fontSize = 12.sp))
                    }
                    last?.let { Text(it, maxLines = 1, style = TextStyle(color = ColorProvider(Space.TextDim), fontSize = 12.sp)) }
                }
            }
            Spacer(GlanceModifier.height(10.dp))
            if (ContactActions.defaultNumber(c) != null) {
                Row(GlanceModifier.fillMaxWidth()) {
                    Action(R.drawable.ic_widget_call, Space.Violet, actionStartActivity(CallActionActivity.intent(context, c.id, whatsapp = false)), GlanceModifier.defaultWeight())
                    Spacer(GlanceModifier.width(8.dp))
                    Action(R.drawable.ic_widget_chat, Space.Teal, actionStartActivity(CallActionActivity.intent(context, c.id, whatsapp = true)), GlanceModifier.defaultWeight())
                }
            }
        }
    }

    @Composable
    private fun Action(icon: Int, tint: Color, action: androidx.glance.action.Action, modifier: GlanceModifier = GlanceModifier) {
        Box(modifier.height(40.dp).background(ColorProvider(Space.Panel)).cornerRadius(20.dp).clickable(action), contentAlignment = Alignment.Center) {
            Image(ImageProvider(icon), null, GlanceModifier.size(20.dp), colorFilter = ColorFilter.tint(ColorProvider(tint)))
        }
    }

    private fun thumb(name: String): Bitmap? = runCatching {
        val file = OrbitRepository.photoFile(name)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 128) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}

/** Placing the widget: pick the person (search-free, A–Z, favourites first). */
class PersonWidgetConfigActivity : androidx.activity.ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return finish()
        OrbitRepository.init(applicationContext)
        setContent {
            OrbitTheme {
                val data by OrbitRepository.data.collectAsState()
                val people = data?.contacts.orEmpty().filter { it.deletedAt == null && !it.archived && !it.isMe }
                    .sortedWith(compareByDescending<Contact> { it.favorite }.thenBy { Logic.displayName(it, false).lowercase() })
                LazyColumn(androidx.compose.ui.Modifier.fillMaxSize().padding(16.dp)) {
                    items(people, key = { it.id }) { c -> PickRow(c) { pick(widgetId, c) } }
                }
            }
        }
        lifecycleScope.launch { OrbitRepository.ensureLoaded() }
    }

    @Composable
    private fun PickRow(c: Contact, onPick: () -> Unit) {
        androidx.compose.material3.Text(
            Logic.displayName(c, false), style = MaterialTheme.typography.bodyLarge, color = Space.Text,
            modifier = androidx.compose.ui.Modifier.clickable(onClick = onPick).padding(vertical = 12.dp),
        )
    }

    private fun pick(widgetId: Int, c: Contact) {
        PersonWidgets.set(this, widgetId, c.id)
        lifecycleScope.launch { PersonWidget.refresh(applicationContext) }
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}
