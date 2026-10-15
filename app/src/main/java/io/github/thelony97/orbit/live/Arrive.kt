package io.github.thelony97.orbit.live

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import io.github.thelony97.orbit.R
import io.github.thelony97.orbit.core.domain.Arrival
import io.github.thelony97.orbit.core.model.BuiltinTypes
import io.github.thelony97.orbit.core.model.HistoryEntry
import io.github.thelony97.orbit.data.Mutations
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.data.newId
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.domain.zone
import io.github.thelony97.orbit.notify.Notifications
import io.github.thelony97.orbit.notify.runAsync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ankommen (PLAN 9 D8): 100 m fences around places with content; arriving puts the place on the
 * island and in a quiet live notification, leaving writes „Besuch 14:05–14:40“ into the Verlauf.
 * Off until Jakob switches it on (then background location is asked once).
 */
object Arrive {

    data class Here(val contactId: String, val since: Long)

    private const val PREFS = "arrive"
    private const val KEY_ID = "id"
    private const val KEY_AT = "at"
    private const val KEY_SET = "set"
    private const val ID_NOTIFY = 1011
    private const val REQUEST = 7_100

    private val _here = MutableStateFlow<Here?>(null)
    val here: StateFlow<Here?> = _here.asStateFlow()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Reads the stored arrival (the receiver may run in a fresh process). */
    fun load(ctx: Context) {
        val p = prefs(ctx)
        val id = p.getString(KEY_ID, null)
        _here.value = id?.let { Here(it, p.getLong(KEY_AT, 0L)) }
    }

    fun allowed(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            ctx.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun pending(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, REQUEST, Intent(ctx, ArriveReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    /** Fences follow the data: set when on and allowed, removed otherwise; only when the set changed. */
    @SuppressLint("MissingPermission")
    fun sync(ctx: Context, data: OrbitData, force: Boolean = false) {
        val client = LocationServices.getGeofencingClient(ctx)
        val on = data.settings.arrive && allowed(ctx)
        val places = if (on) Arrival.places(data.contacts) else emptyList()
        val sig = places.joinToString("|") { "${it.contactId}@${it.lat},${it.lng}" }
        if (!force && prefs(ctx).getString(KEY_SET, null) == sig) return
        prefs(ctx).edit().putString(KEY_SET, sig).apply()
        runCatching { client.removeGeofences(pending(ctx)) }
        if (places.isEmpty()) {
            if (!on) leave(ctx, null)
            return
        }
        val fences = places.map { p ->
            Geofence.Builder()
                .setRequestId(p.contactId)
                .setCircularRegion(p.lat, p.lng, Arrival.RADIUS_M)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                .setLoiteringDelay(LOITER_MS)
                .build()
        }
        val request = GeofencingRequest.Builder().setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER).addGeofences(fences).build()
        runCatching { client.addGeofences(request, pending(ctx)) }
    }

    fun arrive(ctx: Context, data: OrbitData, contactId: String) {
        if (_here.value?.contactId == contactId) return
        val now = System.currentTimeMillis()
        prefs(ctx).edit().putString(KEY_ID, contactId).putLong(KEY_AT, now).apply()
        _here.value = Here(contactId, now)
        notifyHere(ctx, data, contactId, now)
    }

    /** Leaving [contactId] (null = whatever is current): the Beleg, then quiet. */
    fun leave(ctx: Context, contactId: String?) {
        val cur = _here.value ?: run { load(ctx); _here.value } ?: return
        if (contactId != null && cur.contactId != contactId) return
        prefs(ctx).edit().remove(KEY_ID).remove(KEY_AT).apply()
        _here.value = null
        NotificationManagerCompat.from(ctx).cancel(ID_NOTIFY)
        val end = System.currentTimeMillis()
        val note = Arrival.visitNote(cur.since, end, zone()) ?: return
        val entry = HistoryEntry(newId(), cur.contactId, BuiltinTypes.MEET, cur.since, note = note, durationSec = (end - cur.since) / 1_000)
        OrbitRepository.edit { d -> if (d.contacts.none { it.id == cur.contactId }) d else Mutations.addEntry(d, cur.contactId, entry) }
    }

    @SuppressLint("MissingPermission")
    private fun notifyHere(ctx: Context, data: OrbitData, contactId: String, since: Long) {
        val c = data.contacts.firstOrNull { it.id == contactId } ?: return
        if (!Notifications.canPost(ctx)) return
        val builder = NotificationCompat.Builder(ctx, Notifications.CH_MEETING)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF4FE3C1.toInt())
            .setContentTitle("Bei " + Logic.shortName(c))
            .setContentText(Arrival.summary(c))
            .setStyle(NotificationCompat.InboxStyle().also { st -> c.topics.filterNot { it.done }.take(5).forEach { st.addLine("• " + it.text) } })
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(Notifications.openContact(ctx, c.id, ID_NOTIFY))
            .setRequestPromotedOngoing(true)
            .setShortCriticalText(Logic.shortName(c))
            .setUsesChronometer(true)
            .setWhen(since)
        runCatching { NotificationManagerCompat.from(ctx).notify(ID_NOTIFY, builder.build()) }
    }

    private const val LOITER_MS = 60_000
}

class ArriveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        val ids = event.triggeringGeofences?.map { it.requestId }.orEmpty()
        runAsync(context) {
            val data = OrbitRepository.ensureLoaded()
            if (!data.settings.arrive) return@runAsync
            Arrive.load(context)
            when (event.geofenceTransition) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> ids.firstOrNull()?.let { Arrive.arrive(context, data, it) }
                Geofence.GEOFENCE_TRANSITION_EXIT -> ids.forEach { Arrive.leave(context, it) }
            }
        }
    }
}
