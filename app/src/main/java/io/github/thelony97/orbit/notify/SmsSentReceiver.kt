package io.github.thelony97.orbit.notify

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.thelony97.orbit.data.OrbitRepository

/**
 * Tote Daten (PLAN 9 E14): the network's answer to an SMS Orbit sent. A failure marks the number
 * (orange in the card); a later success takes the mark away again.
 */
class SmsSentReceiver : BroadcastReceiver() {

    companion object {
        private const val ACTION = "io.github.thelony97.orbit.SMS_SENT"
        private const val CONTACT = "contact"
        private const val NUMBER = "number"

        fun pending(ctx: Context, contactId: String, number: String): PendingIntent = PendingIntent.getBroadcast(
            ctx, (contactId + number).hashCode(),
            Intent(ctx, SmsSentReceiver::class.java).setAction(ACTION).putExtra(CONTACT, contactId).putExtra(NUMBER, number),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(CONTACT) ?: return
        val number = intent.getStringExtra(NUMBER) ?: return
        val failed = resultCode != Activity.RESULT_OK
        runAsync(context) {
            OrbitRepository.ensureLoaded()
            OrbitRepository.editContact(id) { c ->
                when {
                    failed && number !in c.dead -> c.copy(dead = c.dead + number)
                    !failed && number in c.dead -> c.copy(dead = c.dead - number)
                    else -> c
                }
            }
        }
    }
}
