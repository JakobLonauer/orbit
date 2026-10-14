package io.github.thelony97.orbit.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
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
import io.github.thelony97.orbit.R
import io.github.thelony97.orbit.actions.ContactActions
import io.github.thelony97.orbit.core.domain.Shortcuts
import io.github.thelony97.orbit.core.domain.Visibility
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.data.now
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.notify.CallActionActivity
import io.github.thelony97.orbit.ui.theme.Space

class FavoritesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FavoritesWidget()
}

/** Favoriten-Widget (PLAN 3.14): anrufen or WhatsApp with one tap; the name opens the contact. */
class FavoritesWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    companion object {
        suspend fun refresh(context: Context) {
            runCatching { FavoritesWidget().updateAll(context) }
        }
    }

    /** Widget picker preview with your own data (PLAN 1.3): one composition, no flows. */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        OrbitRepository.init(context)
        val d = OrbitRepository.ensureLoaded()
        provideContent { GlanceTheme { Content(context, d) } }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        OrbitRepository.init(context)
        val initial = OrbitRepository.ensureLoaded()
        provideContent {
            val data by OrbitRepository.data.collectAsState(initial)
            GlanceTheme { Content(context, data ?: initial) }
        }
    }

    @Composable
    private fun Content(context: Context, data: OrbitData) {
        val size = LocalSize.current
        val favs = Shortcuts.topFavorites(data.contacts, now(), { Visibility.inLists(it, data.settings.showOrgs) }, { Logic.displayName(it, false) })
        Column(
            GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(ColorProvider(Color(0xF20B0B15)))
                .cornerRadius(24.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (favs.isEmpty()) {
                Box(GlanceModifier.fillMaxSize().clickable(actionStartActivity(Intent(context, MainActivity::class.java))), contentAlignment = Alignment.Center) {
                    Text("Keine Favoriten", style = TextStyle(color = ColorProvider(Space.TextDim), fontSize = 13.sp))
                }
                return@Column
            }
            val fit = ((size.height.value - 16) / 52).toInt().coerceAtLeast(1)
            favs.take(fit).forEach { c -> FavRow(context, c, data) }
        }
    }

    @Composable
    private fun FavRow(context: Context, c: Contact, data: OrbitData) {
        val open = actionStartActivity(
            Intent(context, MainActivity::class.java)
                .setData(Uri.parse("orbit://contact/${c.id}"))
                .putExtra(MainActivity.EXTRA_CONTACT, c.id),
        )
        val number = ContactActions.defaultNumber(c)
        Row(GlanceModifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(GlanceModifier.defaultWeight().height(52.dp).clickable(open), verticalAlignment = Alignment.CenterVertically) {
                val photo = c.photo?.let { thumb(it) }
                if (photo != null) {
                    Image(ImageProvider(photo), null, GlanceModifier.size(36.dp).cornerRadius(18.dp))
                } else {
                    val color = c.groupIds.firstNotNullOfOrNull { id -> data.groups.firstOrNull { it.id == id } }?.let { Color(it.color) } ?: Color(0xFF7986CB)
                    Box(GlanceModifier.size(36.dp).background(color).cornerRadius(18.dp), contentAlignment = Alignment.Center) {
                        Text(Logic.initials(c), style = TextStyle(color = ColorProvider(Color.White), fontSize = 13.sp, fontWeight = FontWeight.Bold))
                    }
                }
                Spacer(GlanceModifier.width(10.dp))
                Text(
                    Logic.displayName(c, data.settings.sortByLastName),
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(Space.Text), fontSize = 14.sp, fontWeight = FontWeight.Medium),
                )
            }
            if (number != null) {
                Action(R.drawable.ic_widget_chat, Space.Teal, actionStartActivity(CallActionActivity.intent(context, c.id, whatsapp = true)))
                Spacer(GlanceModifier.width(6.dp))
                Action(R.drawable.ic_widget_call, Space.Violet, actionStartActivity(CallActionActivity.intent(context, c.id, whatsapp = false)))
            }
        }
    }

    @Composable
    private fun Action(icon: Int, tint: Color, action: androidx.glance.action.Action) {
        Box(
            GlanceModifier.size(40.dp).background(ColorProvider(Space.Panel)).cornerRadius(20.dp).clickable(action),
            contentAlignment = Alignment.Center,
        ) {
            Image(ImageProvider(icon), null, GlanceModifier.size(20.dp), colorFilter = ColorFilter.tint(ColorProvider(tint)))
        }
    }

    /** Small enough for the widget's binder limit. */
    private fun thumb(name: String): Bitmap? = runCatching {
        val file = OrbitRepository.photoFile(name)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 96) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}
