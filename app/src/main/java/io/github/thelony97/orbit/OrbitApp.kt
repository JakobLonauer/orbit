package io.github.thelony97.orbit

import android.app.Application
import io.github.thelony97.orbit.backup.Backups
import io.github.thelony97.orbit.data.Mutations
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.mail.MailAccounts
import io.github.thelony97.orbit.notify.DigestScheduler
import io.github.thelony97.orbit.notify.Notifications
import io.github.thelony97.orbit.sync.Sync
import io.github.thelony97.orbit.widget.OrbitWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OrbitApp : Application() {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        OrbitRepository.init(this)
        MailAccounts.init(this)
        OrbitRepository.onSaved = { ctx -> OrbitWidget.refresh(ctx) }
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
            DigestScheduler.schedule(this@OrbitApp)
            Sync.schedulePeriodic(this@OrbitApp)
            Backups.schedule(this@OrbitApp)
        }
    }
}
