package io.github.thelony97.orbit.call

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Orbit als Telefon-App (PLAN 3.12). Only Jakob turns it on: the in-call service and the dial
 * screen are disabled in the manifest, so without the switch nothing changes – no chooser entry
 * for phone numbers, no binding by Telecom. If anything goes wrong while Orbit holds the role,
 * Telecom falls back to the preinstalled phone app; a call is never lost to Orbit.
 */
object PhoneRole {

    private fun parts(ctx: Context) = listOf(
        ComponentName(ctx, OrbitInCallService::class.java),
        ComponentName(ctx, DialerActivity::class.java),
    )

    private fun roles(ctx: Context): RoleManager? = ctx.getSystemService(RoleManager::class.java)

    fun available(ctx: Context): Boolean = runCatching { roles(ctx)?.isRoleAvailable(RoleManager.ROLE_DIALER) == true }.getOrDefault(false)

    fun held(ctx: Context): Boolean = runCatching { roles(ctx)?.isRoleHeld(RoleManager.ROLE_DIALER) == true }.getOrDefault(false)

    private fun partsOn(ctx: Context): Boolean = parts(ctx).any {
        ctx.packageManager.getComponentEnabledSetting(it) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }

    private fun setParts(ctx: Context, on: Boolean) {
        parts(ctx).forEach {
            ctx.packageManager.setComponentEnabledSetting(
                it,
                // Off = back to the manifest default, which is disabled.
                if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                PackageManager.DONT_KILL_APP,
            )
        }
    }

    /** Enables the parts and returns the system's own role dialog; null if this phone has no such role. */
    fun requestIntent(ctx: Context): Intent? {
        if (!available(ctx)) return null
        setParts(ctx, true)
        return runCatching { roles(ctx)?.createRequestRoleIntent(RoleManager.ROLE_DIALER) }.getOrNull()
    }

    /**
     * Without the role the parts go back off (role declined, or switched back in the system's
     * default apps) – so Orbit never shows up as a dialer nobody chose. Never touches anything
     * while Orbit holds the role.
     */
    fun tidy(ctx: Context) {
        if (!held(ctx) && partsOn(ctx)) runCatching { setParts(ctx, false) }
    }

    /** The system page where the phone app is chosen – the way back is always there. */
    fun defaultAppsIntent(): Intent = Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
