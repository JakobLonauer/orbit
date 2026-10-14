package io.github.thelony97.orbit.notify

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.thelony97.orbit.MainActivity
import io.github.thelony97.orbit.actions.AppShortcuts
import io.github.thelony97.orbit.core.domain.Shortcuts
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.data.now
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Schnelleinstellungs-Kachel „Notiz zum letzten Anruf“ (PLAN 3.14): opens the newest call of the
 * Verlauf for a note. The tile itself shows only the time – never a name, it is on the lock screen.
 */
class LastCallTile : TileService() {

    private val scope = kotlinx.coroutines.MainScope()

    override fun onStartListening() {
        super.onStartListening()
        show()
        // Orbit not running yet: load once, then show the time.
        if (OrbitRepository.data.value == null) scope.launch {
            OrbitRepository.init(applicationContext)
            runCatching { OrbitRepository.ensureLoaded() }
            show()
        }
    }

    private fun show() {
        val tile = qsTile ?: return
        val data = OrbitRepository.data.value
        val last = data?.let { Shortcuts.lastCall(it.contacts, now()) }
        tile.state = if (data == null || last != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = last?.let { time(it.at) }
        tile.updateTile()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        val go = {
            val intent = Intent(this, MainActivity::class.java)
                .setAction(AppShortcuts.ACTION_LAST_CALL)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        if (isLocked) unlockAndRun { go() } else go()
    }

    private fun time(at: Long): String {
        val today = SimpleDateFormat("yyyyMMdd", Locale.ROOT)
        val sameDay = today.format(Date(at)) == today.format(Date())
        return SimpleDateFormat(if (sameDay) "HH:mm" else "d. MMM", Locale.GERMAN).format(Date(at))
    }
}
