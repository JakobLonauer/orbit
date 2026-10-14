package io.github.thelony97.orbit.widget

import android.content.Context
import android.os.Build
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.setWidgetPreviews

/**
 * „Widgets: echte Vorschauen im Auswahlmenü“ (PLAN 1.3): from Android 15 on the widget picker
 * shows the widgets rendered with your own data (Glance generated previews). The system allows
 * about two updates per hour, so Orbit renews them at most once a day, after the app start.
 */
object WidgetPreviews {
    private const val PREFS = "widget_previews"
    private const val KEY = "last"

    suspend fun refreshDaily(ctx: Context) {
        if (Build.VERSION.SDK_INT < 35) return
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY, 0L) < 24 * 60 * 60_000L) return
        prefs.edit().putLong(KEY, now).apply()
        val manager = GlanceAppWidgetManager(ctx)
        runCatching {
            manager.setWidgetPreviews<OrbitWidgetReceiver>()
            manager.setWidgetPreviews<FavoritesWidgetReceiver>()
            manager.setWidgetPreviews<LinksWidgetReceiver>()
        }.onFailure { android.util.Log.w("Orbit", "widget previews", it) }
    }
}
