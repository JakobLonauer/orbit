package io.github.thelony97.orbit.phonebook

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.app.Activity
import android.app.Service
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.Context
import android.content.Intent
import android.content.SyncResult
import android.os.Bundle
import android.os.IBinder
import io.github.thelony97.orbit.MainActivity
import kotlinx.coroutines.runBlocking

/**
 * The account „Orbit“ in Android's accounts (PLAN 9 A1). It has no password and no server; it only
 * owns Orbit's rows in the address book. Adding it from the system settings is not possible –
 * Orbit's own switch does that.
 */
class OrbitAuthenticatorService : Service() {
    private val authenticator by lazy { Authenticator(this) }
    override fun onBind(intent: Intent?): IBinder? = authenticator.iBinder
}

private class Authenticator(ctx: Context) : AbstractAccountAuthenticator(ctx) {
    private fun unsupported() = Bundle().apply {
        putInt(AccountManager.KEY_ERROR_CODE, AccountManager.ERROR_CODE_UNSUPPORTED_OPERATION)
        putString(AccountManager.KEY_ERROR_MESSAGE, "In Orbit einschalten")
    }

    override fun editProperties(response: AccountAuthenticatorResponse?, accountType: String?): Bundle? = null

    override fun addAccount(
        response: AccountAuthenticatorResponse?,
        accountType: String?,
        authTokenType: String?,
        requiredFeatures: Array<out String>?,
        options: Bundle?,
    ): Bundle = unsupported()

    override fun confirmCredentials(response: AccountAuthenticatorResponse?, account: Account?, options: Bundle?): Bundle? = null

    override fun getAuthToken(response: AccountAuthenticatorResponse?, account: Account?, authTokenType: String?, options: Bundle?): Bundle = unsupported()

    override fun getAuthTokenLabel(authTokenType: String?): String? = null

    override fun updateCredentials(response: AccountAuthenticatorResponse?, account: Account?, authTokenType: String?, options: Bundle?): Bundle = unsupported()

    override fun hasFeatures(response: AccountAuthenticatorResponse?, account: Account?, features: Array<out String>?): Bundle =
        Bundle().apply { putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false) }
}

/**
 * Android only lists an account's contacts with a sync adapter (and reads `contacts.xml` from it).
 * Orbit writes on its own after every save; a sync the system asks for runs that same write.
 */
class OrbitSyncService : Service() {
    override fun onBind(intent: Intent?): IBinder? = adapter(applicationContext).syncAdapterBinder

    companion object {
        @Volatile private var instance: SyncAdapter? = null
        private fun adapter(ctx: Context): SyncAdapter =
            instance ?: synchronized(this) { instance ?: SyncAdapter(ctx).also { instance = it } }
    }
}

private class SyncAdapter(ctx: Context) : AbstractThreadedSyncAdapter(ctx, true) {
    override fun onPerformSync(account: Account?, extras: Bundle?, authority: String?, provider: ContentProviderClient?, syncResult: SyncResult?) {
        runBlocking { PhoneBookSync.writeNow(context) }
    }
}

/**
 * „In Orbit öffnen“ and the own-field rows in a system contact card (PLAN 9 A3): opens the
 * contact in Orbit.
 */
class PhoneBookRowActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.data?.let { PhoneBookSync.contactIdOf(this, it) }
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .apply { if (id != null) putExtra(MainActivity.EXTRA_CONTACT, id) },
        )
        finish()
    }
}

/** PLAN 9 A13: the same for the Orbit calendar – a sync the system asks for writes it once. */
class OrbitCalendarSyncService : Service() {
    override fun onBind(intent: Intent?): IBinder? = adapter(applicationContext).syncAdapterBinder

    companion object {
        @Volatile private var instance: CalendarSyncAdapter? = null
        private fun adapter(ctx: Context): CalendarSyncAdapter =
            instance ?: synchronized(this) { instance ?: CalendarSyncAdapter(ctx).also { instance = it } }
    }
}

private class CalendarSyncAdapter(ctx: Context) : AbstractThreadedSyncAdapter(ctx, true) {
    override fun onPerformSync(account: Account?, extras: Bundle?, authority: String?, provider: ContentProviderClient?, syncResult: SyncResult?) {
        runBlocking { OrbitCalendar.writeNow(context) }
    }
}
