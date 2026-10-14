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
import io.github.thelony97.orbit.core.domain.QuickLinks
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.ui.detail.linkHost
import io.github.thelony97.orbit.ui.theme.Space

class LinksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LinksWidget()
}

/** Angepinnte Links als Widget (PLAN 3.7): a tap opens the portal straight away. */
class LinksWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    companion object {
        suspend fun refresh(context: Context) {
            runCatching { LinksWidget().updateAll(context) }
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
        val items = QuickLinks.pinned(data.contacts) { Logic.displayName(it, false) }
        Column(
            GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(ColorProvider(Color(0xF20B0B15)))
                .cornerRadius(24.dp)
                .padding(12.dp),
        ) {
            if (items.isEmpty()) {
                Box(GlanceModifier.fillMaxSize().clickable(actionStartActivity(Intent(context, MainActivity::class.java))), contentAlignment = Alignment.Center) {
                    Text("Keine angepinnten Links", style = TextStyle(color = ColorProvider(Space.TextDim), fontSize = 13.sp))
                }
                return@Column
            }
            val columns = if (size.width >= 220.dp) 3 else 2
            val rows = ((size.height.value - 8) / 58).toInt().coerceAtLeast(1)
            items.take(columns * rows).chunked(columns).forEach { row ->
                Row(GlanceModifier.fillMaxWidth().height(54.dp).padding(bottom = 4.dp)) {
                    row.forEach { q ->
                        val l = q.link
                        val raw = l.url.trim()
                        val open = actionStartActivity(Intent(Intent.ACTION_VIEW, Uri.parse(if (raw.startsWith("http")) raw else "https://$raw")))
                        Row(
                            GlanceModifier.defaultWeight().height(50.dp).background(ColorProvider(Space.Panel)).cornerRadius(14.dp).padding(horizontal = 8.dp).clickable(open),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val logo = l.logo?.let { runCatching { android.graphics.BitmapFactory.decodeFile(OrbitRepository.photoFile(it).path) }.getOrNull() }
                            if (logo != null) {
                                Image(ImageProvider(logo), null, GlanceModifier.size(22.dp).cornerRadius(6.dp))
                                Spacer(GlanceModifier.width(6.dp))
                            }
                            Text(
                                l.title.ifBlank { linkHost(l.url) },
                                maxLines = 1,
                                style = TextStyle(color = ColorProvider(Space.Text), fontSize = 13.sp, fontWeight = FontWeight.Medium),
                            )
                        }
                        Spacer(GlanceModifier.width(4.dp))
                    }
                    repeat(columns - row.size) { Spacer(GlanceModifier.defaultWeight()) }
                }
            }
        }
    }
}
