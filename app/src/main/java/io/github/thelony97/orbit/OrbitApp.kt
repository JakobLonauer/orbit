package io.github.thelony97.orbit

import android.app.Application
import io.github.thelony97.orbit.backup.Backups
import io.github.thelony97.orbit.data.Mutations
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.mail.MailAccounts
import io.github.thelony97.orbit.notify.DigestScheduler
import io.github.thelony97.orbit.notify.Notifications
import io.github.thelony97.orbit.sync.Sync
import io.github.thelony97.orbit.util.ErrorLog
import io.github.thelony97.orbit.widget.OrbitWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class OrbitApp : Application() {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Local error log (Einstellungen › Fehlerprotokoll). */
    val errorLog by lazy { ErrorLog(File(filesDir, "logs/errors.txt")) }

    override fun onCreate() {
        super.onCreate()
        errorLog.installUncaughtHandler()
        OrbitRepository.init(this)
        MailAccounts.init(this)
        OrbitRepository.onSaved = { ctx ->
            OrbitWidget.refresh(ctx)
            io.github.thelony97.orbit.widget.LinksWidget.refresh(ctx)
            io.github.thelony97.orbit.widget.FavoritesWidget.refresh(ctx)
            OrbitRepository.data.value?.let { io.github.thelony97.orbit.actions.AppShortcuts.update(ctx, it) }
        }
        Notifications.createChannels(this)
        scope.launch {
            OrbitRepository.ensureLoaded()
            var orphanPhotos = emptyList<String>()
            OrbitRepository.update { d ->
                val (cleaned, photos) = Mutations.purgeExpiredTrash(d)
                orphanPhotos = photos
                cleaned
            }
            orphanPhotos.forEach { OrbitRepository.photoFile(it).delete() }
            runCatching { io.github.thelony97.orbit.actions.AppShortcuts.update(this@OrbitApp, OrbitRepository.snapshot()) }
            DigestScheduler.schedule(this@OrbitApp)
            Sync.schedulePeriodic(this@OrbitApp)
            // E-Mail v2 (PLAN 3.9): every 6 hours with network, growing pause after errors – only
            // with an account at all.
            if (io.github.thelony97.orbit.mail.MailAccounts.accounts.value.isNotEmpty()) io.github.thelony97.orbit.mail.MailSync.schedulePeriodic(this@OrbitApp)
            else io.github.thelony97.orbit.mail.MailSync.cancelPeriodic(this@OrbitApp)
            Backups.schedule(this@OrbitApp)
            io.github.thelony97.orbit.widget.WidgetPreviews.refreshDaily(this@OrbitApp)
        }
    }
}
