package io.github.thelony97.orbit.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings

data class PermissionInfo(
    val permission: String,
    val title: String,
    val why: String,
)

object Permissions {

    val core = listOf(
        PermissionInfo(Manifest.permission.READ_CALL_LOG, "Anrufliste", "Anrufe landen automatisch im Verlauf"),
        PermissionInfo(Manifest.permission.READ_SMS, "SMS", "SMS landen samt Text im Verlauf"),
        PermissionInfo(Manifest.permission.READ_PHONE_STATE, "Telefonstatus", "Erkennt Anrufe für Themen und Notiz danach"),
        PermissionInfo(Manifest.permission.CALL_PHONE, "Anrufen", "Wischen ruft direkt an"),
        PermissionInfo(Manifest.permission.POST_NOTIFICATIONS, "Benachrichtigungen", "Tägliche Übersicht um 9:00"),
    )

    val optional = listOf(
        PermissionInfo(Manifest.permission.READ_CONTACTS, "Handy-Kontakte", "Nur zum Übernehmen einzelner Kontakte"),
    )

    fun granted(ctx: Context, permission: String) =
        ctx.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    fun appInfoIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    const val RESTRICTED_HELP =
        "Android blockiert bei Apps außerhalb des Play Stores manchmal SMS und Anrufliste. " +
            "So schaltest du es frei: App-Info öffnen → oben rechts ⋮ → „Eingeschränkte Einstellungen zulassen“ → " +
            "bestätigen → zurück in Orbit und nochmal erlauben."
}
