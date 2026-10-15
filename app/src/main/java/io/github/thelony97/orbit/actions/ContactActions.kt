package io.github.thelony97.orbit.actions

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.Entry
import io.github.thelony97.orbit.data.EntrySource
import io.github.thelony97.orbit.data.Mutations
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.data.Settings
import io.github.thelony97.orbit.data.TypeIds
import io.github.thelony97.orbit.data.newId
import io.github.thelony97.orbit.data.now
import io.github.thelony97.orbit.notify.Notifications
import io.github.thelony97.orbit.sync.SyncEngine
import java.util.concurrent.TimeUnit

/** Quick actions from a contact. Each one leaves a history entry where Orbit can't see the result itself. */
object ContactActions {

    fun defaultNumber(c: Contact): String? =
        c.phones.getOrNull(c.defaultPhone)?.value?.takeIf { it.isNotBlank() }
            ?: c.phones.firstOrNull { it.value.isNotBlank() }?.value

    /** [sim]: a SIM that wins over the contact's rules (PLAN 9 B9: call back on the SIM it came in on). */
    fun call(ctx: Context, c: Contact, number: String?, settings: Settings, sim: String? = null) {
        val target = number ?: defaultNumber(c) ?: return toast(ctx, "Keine Nummer hinterlegt")
        // As phone app the call screen lists the topics; otherwise the reminder notification.
        if (settings.topicsDuringCall && !io.github.thelony97.orbit.call.PhoneRole.held(ctx)) Notifications.topics(ctx, c)
        val canCall = ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        // Wählregeln (PLAN 9 B10): +43 abroad, #31# for „Nummer unterdrücken“.
        val dial = io.github.thelony97.orbit.domain.Phones.dial(ctx, target, hide = c.hideNumber)
        val intent = Intent(if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.fromParts("tel", dial, null))
        // Dual-SIM pro Kontakt (PLAN 3.12), Person vor Gruppe (PLAN 9 B9): Telecom honours the account on ACTION_CALL.
        val rule = sim ?: io.github.thelony97.orbit.call.Sims.forContact(c, OrbitRepository.data.value?.groups.orEmpty())
        io.github.thelony97.orbit.call.Sims.handle(ctx, rule)?.let { intent.putExtra(android.telecom.TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it) }
        if (start(ctx, intent) && !SyncEngine.canReadCalls(ctx)) {
            log(c.id, TypeIds.CALL, "Anruf aus Orbit")
        }
    }

    fun sms(ctx: Context, c: Contact, number: String?) {
        val target = number ?: defaultNumber(c) ?: return toast(ctx, "Keine Nummer hinterlegt")
        val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", target, null))
        if (start(ctx, intent) && !SyncEngine.canReadSms(ctx)) {
            log(c.id, TypeIds.MESSAGE, "SMS aus Orbit")
        }
    }

    fun whatsapp(ctx: Context, c: Contact, number: String?) {
        val target = number ?: defaultNumber(c) ?: return toast(ctx, "Keine Nummer hinterlegt")
        val digits = target.filter { it.isDigit() || it == '+' }.let {
            when {
                it.startsWith("+") -> it.drop(1)
                it.startsWith("00") -> it.drop(2)
                it.startsWith("0") -> "43" + it.drop(1)
                else -> it
            }
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits"))
        if (start(ctx, intent)) log(c.id, TypeIds.MESSAGE, "WhatsApp")
    }

    /** The greeting „Gratulieren“ fills in: the contact's own template (Du/Sie, Sprache; PLAN 3.5). */
    fun greeting(c: Contact): String = io.github.thelony97.orbit.core.domain.Greetings.text(c)

    /**
     * „Gratulieren“ (PLAN 3.5): WhatsApp with the greeting filled in if it is installed, otherwise
     * an SMS with it. Returns whether something opened.
     */
    fun congratulate(ctx: Context, c: Contact, text: String): Boolean {
        val intent = prepared(ctx, c, text) ?: run { toast(ctx, "Keine Nummer hinterlegt"); return false }
        return start(ctx, intent)
    }

    /** The chat prepared with [text]: WhatsApp when installed, else the SMS app; null without a number. */
    fun prepared(ctx: Context, c: Contact, text: String): Intent? {
        val target = defaultNumber(c) ?: return null
        val pm = ctx.packageManager
        val whatsapp = listOf("com.whatsapp", "com.whatsapp.w4b").any { runCatching { pm.getLaunchIntentForPackage(it) != null }.getOrDefault(false) }
        return if (whatsapp) {
            val digits = target.filter { it.isDigit() || it == '+' }.let {
                when {
                    it.startsWith("+") -> it.drop(1)
                    it.startsWith("00") -> it.drop(2)
                    it.startsWith("0") -> "43" + it.drop(1)
                    else -> it
                }
            }
            Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits?text=" + Uri.encode(text)))
        } else {
            Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", target, null)).putExtra("sms_body", text)
        }
    }

    /**
     * PLAN 9 C3 „Spät dran“: [text] to [c] – straight as an SMS when Jakob allowed Orbit to send
     * them, otherwise WhatsApp (or the SMS app) prepared with it. True when it went out by itself.
     */
    /** PLAN 9 E2 Faden: an SMS straight from the reply line (mobile first); false = not allowed / no number. */
    @android.annotation.SuppressLint("MissingPermission")
    fun smsDirect(ctx: Context, c: Contact, text: String): Boolean {
        if (ctx.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return false
        val numbers = c.phones.filter { it.value.isNotBlank() }
        val mobile = numbers.firstOrNull { io.github.thelony97.orbit.core.domain.NumberInfo.of(it.value)?.kind == io.github.thelony97.orbit.core.domain.NumberInfo.Kind.MOBILE }
        val to = (mobile ?: numbers.firstOrNull())?.value ?: return false
        val sent = runCatching {
            val sms = ctx.getSystemService(android.telephony.SmsManager::class.java)
            val parts = sms.divideMessage(text)
            // PLAN 9 E14: the network's answer marks a number that cannot be reached.
            val report = io.github.thelony97.orbit.notify.SmsSentReceiver.pending(ctx, c.id, to)
            sms.sendMultipartTextMessage(to, null, parts, ArrayList(List(parts.size) { report }), null)
        }.isSuccess
        if (sent) log(c.id, TypeIds.MESSAGE, text)
        return sent
    }

    /** PLAN 9 E2: the mail app with recipient, subject and text (Jakob sends). */
    fun mailDraft(ctx: Context, c: Contact, subject: String, body: String): Boolean {
        val to = c.emails.firstOrNull { it.value.isNotBlank() }?.value ?: return false
        val uri = Uri.parse("mailto:" + Uri.encode(to) + "?subject=" + Uri.encode(subject) + "&body=" + Uri.encode(body))
        return start(ctx, Intent(Intent.ACTION_SENDTO, uri))
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun late(ctx: Context, c: Contact, text: String): Boolean {
        val mobile = c.phones.firstOrNull { p ->
            p.value.isNotBlank() && io.github.thelony97.orbit.core.domain.NumberInfo.of(p.value)?.kind == io.github.thelony97.orbit.core.domain.NumberInfo.Kind.MOBILE
        }?.value
        val canSend = ctx.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        if (canSend && mobile != null) {
            val sent = runCatching {
                ctx.getSystemService(android.telephony.SmsManager::class.java).sendTextMessage(mobile, null, text, null, null)
            }.isSuccess
            if (sent) {
                log(c.id, TypeIds.MESSAGE, text)
                return true
            }
        }
        congratulate(ctx, c, text)
        return false
    }

    fun email(ctx: Context, c: Contact, address: String?) {
        val target = address ?: c.emails.firstOrNull { it.value.isNotBlank() }?.value
            ?: return toast(ctx, "Keine E-Mail hinterlegt")
        val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", target, null))
        if (start(ctx, intent)) log(c.id, TypeIds.EMAIL, "E-Mail aus Orbit")
    }

    /** Opens the address in Google Maps (app if installed, otherwise the browser). */
    fun maps(ctx: Context, address: String) {
        if (address.isBlank()) return toast(ctx, "Keine Adresse hinterlegt")
        start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(address))))
    }

    fun web(ctx: Context, url: String) {
        val clean = url.trim()
        if (clean.isEmpty()) return
        val full = if (clean.startsWith("http://") || clean.startsWith("https://")) clean else "https://$clean"
        start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(full)))
    }

    /**
     * Opens a portal (PLAN 3.7): in Orbit's browser window (Custom Tabs: Chrome's sign-ins stay,
     * Zurück führt zu Orbit), in Chrome, or – chosen per link – an installed app.
     */
    fun openLink(ctx: Context, link: io.github.thelony97.orbit.core.model.Link) {
        val raw = link.url.trim()
        if (raw.isEmpty()) return
        val uri = Uri.parse(if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw")
        io.github.thelony97.orbit.security.AppLock.allowExternal()
        // Quiet tries: a missing Chrome or app falls back without a toast.
        fun quiet(intent: Intent): Boolean = runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
        val opened = when (link.openWith) {
            io.github.thelony97.orbit.core.model.OpenWith.APP -> link.appPackage
                ?.let { runCatching { ctx.packageManager.getLaunchIntentForPackage(it) }.getOrNull() }
                ?.let { quiet(it) } == true
            io.github.thelony97.orbit.core.model.OpenWith.CHROME ->
                quiet(Intent(Intent.ACTION_VIEW, uri).setPackage("com.android.chrome"))
            io.github.thelony97.orbit.core.model.OpenWith.ORBIT_TAB -> runCatching {
                androidx.browser.customtabs.CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .setColorScheme(androidx.browser.customtabs.CustomTabsIntent.COLOR_SCHEME_DARK)
                    .build()
                    .launchUrl(ctx, uri)
                true
            }.getOrDefault(false)
        }
        // No Chrome, no app: the phone's browser.
        if (!opened) start(ctx, Intent(Intent.ACTION_VIEW, uri))
    }

    /** Calls a bare number (Rückruf-Stapel, unknown callers) – straight away when allowed, else the dial pad. */
    fun callNumber(ctx: Context, number: String, sim: String? = null) {
        val canCall = ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val dial = io.github.thelony97.orbit.domain.Phones.dial(ctx, number)
        val intent = Intent(if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.fromParts("tel", dial, null))
        io.github.thelony97.orbit.call.Sims.handle(ctx, sim)?.let { intent.putExtra(android.telecom.TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it) }
        start(ctx, intent)
    }

    fun dialNumber(ctx: Context, number: String) {
        start(ctx, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
    }

    /** Adds an app-action entry unless the same kind was logged in the last 30 minutes. */
    private fun log(contactId: String, typeId: String, note: String) {
        val cutoff = now() - TimeUnit.MINUTES.toMillis(30)
        OrbitRepository.edit { d ->
            val c = d.contacts.firstOrNull { it.id == contactId } ?: return@edit d
            val recent = c.entries.any { it.source == EntrySource.APP_ACTION && it.typeId == typeId && it.at > cutoff }
            if (recent) d
            else Mutations.addEntry(d, contactId, Entry(id = newId(), contactId = contactId, typeId = typeId, at = now(), note = note, source = EntrySource.APP_ACTION))
        }
    }

    private fun start(ctx: Context, intent: Intent): Boolean {
        if (ctx !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            toast(ctx, "Keine passende App gefunden")
            false
        } catch (_: SecurityException) {
            toast(ctx, "Aktion nicht erlaubt")
            false
        }
    }

    private fun toast(ctx: Context, text: String) {
        io.github.thelony97.orbit.ui.components.Undo.note(ctx, text, ok = false)
    }
}
