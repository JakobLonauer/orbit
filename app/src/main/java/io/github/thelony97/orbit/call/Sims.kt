package io.github.thelony97.orbit.call

import android.content.Context
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager

/** The phone's SIMs for „Dual-SIM pro Kontakt“ (PLAN 3.12). Empty without permission or with one SIM. */
object Sims {
    data class Sim(val id: String, val label: String)

    // Without READ_PHONE_STATE the SecurityException is caught (runCatching) and the list stays empty.
    @android.annotation.SuppressLint("MissingPermission")
    private fun handles(ctx: Context): List<PhoneAccountHandle> =
        runCatching { ctx.getSystemService(TelecomManager::class.java)?.callCapablePhoneAccounts }.getOrNull().orEmpty()

    fun list(ctx: Context): List<Sim> {
        val telecom = ctx.getSystemService(TelecomManager::class.java) ?: return emptyList()
        return handles(ctx).mapIndexed { i, h ->
            val label = runCatching { telecom.getPhoneAccount(h)?.label?.toString() }.getOrNull()?.takeIf { it.isNotBlank() } ?: "SIM ${i + 1}"
            Sim(h.id, label)
        }
    }

    /** The handle for a stored SIM id; null if that SIM is gone (then the phone decides as usual). */
    fun handle(ctx: Context, id: String?): PhoneAccountHandle? = id?.let { wanted -> handles(ctx).firstOrNull { it.id == wanted } }

    /**
     * PLAN 9 B9 Dual-SIM-Regeln: the person's own SIM, else the first of their groups that has one
     * (Person vor Gruppe); null = the phone decides.
     */
    fun forContact(c: io.github.thelony97.orbit.data.Contact, groups: List<io.github.thelony97.orbit.data.Group>): String? =
        c.simId ?: c.groupIds.firstNotNullOfOrNull { id -> groups.firstOrNull { it.id == id }?.simId }

    /** The SIM a call came in on, for its colour edge and label (two SIMs only). */
    data class Tag(val label: String, val color: Int)

    fun tag(ctx: Context, handle: PhoneAccountHandle?): Tag? {
        if (handle == null) return null
        val all = handles(ctx)
        if (all.size < 2) return null
        val telecom = ctx.getSystemService(TelecomManager::class.java) ?: return null
        val account = runCatching { telecom.getPhoneAccount(handle) }.getOrNull()
        val index = all.indexOfFirst { it.id == handle.id }.coerceAtLeast(0)
        val label = account?.label?.toString()?.takeIf { it.isNotBlank() } ?: "SIM ${index + 1}"
        // The system's own SIM colour; without one a fixed pair (blue, orange).
        val color = account?.highlightColor?.takeIf { it != 0 } ?: if (index == 0) 0xFF6FB7FF.toInt() else 0xFFFFB45E.toInt()
        return Tag(label, color)
    }
}
