package io.github.thelony97.orbit.live

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.thelony97.orbit.R
import io.github.thelony97.orbit.actions.ContactActions
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.notify.Notifications
import io.github.thelony97.orbit.notify.runAsync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Zur Zeit senden (PLAN 9 E4): a message that goes out at its time – an SMS by itself, a WhatsApp
 * as a notification with the text ready (one tap opens the chat). Plans live in a small file and
 * survive restarts.
 */
object Scheduled {

    @Serializable
    data class Plan(val id: String, val contactId: String, val text: String, val at: Long, val sms: Boolean)

    private const val FILE = "scheduled.json"
    private const val ID_BASE = 80_000
    const val EXTRA_ID = "plan"
    private val json = Json { ignoreUnknownKeys = true }

    private val _plans = MutableStateFlow<List<Plan>>(emptyList())
    val plans: StateFlow<List<Plan>> = _plans.asStateFlow()

    private fun file(ctx: Context) = File(ctx.filesDir, FILE)

    @Synchronized
    fun all(ctx: Context): List<Plan> {
        val list = runCatching { json.decodeFromString(ListSerializer(Plan.serializer()), file(ctx).readText()) }.getOrDefault(emptyList())
        _plans.value = list
        return list
    }

    @Synchronized
    private fun write(ctx: Context, list: List<Plan>) {
        runCatching { file(ctx).writeText(json.encodeToString(ListSerializer(Plan.serializer()), list)) }
        _plans.value = list
    }

    private fun id(planId: String) = ID_BASE + (planId.hashCode() and 0x0FFF)

    private fun pending(ctx: Context, p: Plan) = PendingIntent.getBroadcast(
        ctx, id(p.id), Intent(ctx, ScheduledReceiver::class.java).putExtra(EXTRA_ID, p.id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun alarm(ctx: Context, p: Plan) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        runCatching {
            if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, p.at, pending(ctx, p))
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, p.at, pending(ctx, p))
        }
    }

    fun plan(ctx: Context, p: Plan) {
        write(ctx, all(ctx).filterNot { it.id == p.id } + p)
        alarm(ctx, p)
    }

    fun cancel(ctx: Context, planId: String) {
        val p = all(ctx).firstOrNull { it.id == planId } ?: return
        ctx.getSystemService(AlarmManager::class.java)?.cancel(pending(ctx, p))
        write(ctx, all(ctx).filterNot { it.id == planId })
    }

    /** After a restart alarms are gone; overdue ones go out now. */
    fun restore(ctx: Context) = all(ctx).forEach { alarm(ctx, it.copy(at = maxOf(it.at, System.currentTimeMillis() + 2_000))) }

    /** The time has come: SMS out (and in the Verlauf), or the WhatsApp text as a notification. */
    @SuppressLint("MissingPermission")
    suspend fun fire(ctx: Context, planId: String) {
        val p = all(ctx).firstOrNull { it.id == planId } ?: return
        write(ctx, all(ctx).filterNot { it.id == planId })
        val c = OrbitRepository.ensureLoaded().contacts.firstOrNull { it.id == p.contactId } ?: return
        if (p.sms && ContactActions.smsDirect(ctx, c, p.text)) return
        val open = ContactActions.prepared(ctx, c, p.text) ?: return
        if (!Notifications.canPost(ctx)) return
        val builder = NotificationCompat.Builder(ctx, Notifications.CH_MEETING)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF5EE6CF.toInt())
            .setContentTitle("Jetzt an " + Logic.shortName(c))
            .setContentText(p.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(p.text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(ctx, id(p.id), open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        runCatching { NotificationManagerCompat.from(ctx).notify(id(p.id), builder.build()) }
    }
}

class ScheduledReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(Scheduled.EXTRA_ID) ?: return
        runAsync(context) { Scheduled.fire(context, id) }
    }
}
