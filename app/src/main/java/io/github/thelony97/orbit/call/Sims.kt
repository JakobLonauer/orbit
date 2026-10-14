package io.github.thelony97.orbit.call

import android.content.Context
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager

/** The phone's SIMs for „Dual-SIM pro Kontakt“ (PLAN 3.12). Empty without permission or with one SIM. */
object Sims {
    data class Sim(val id: String, val label: String)

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
}
