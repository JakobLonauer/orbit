package io.github.thelony97.orbit.call

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Anruf-Filter (PLAN 9 B8): only Jakob turns it on – the screening service is disabled in the
 * manifest until he asks, then the system's own role dialog decides. Replaces the phone's spam
 * protection, so it is never switched on by itself.
 */
object ScreeningRole {

    private fun part(ctx: Context) = ComponentName(ctx, OrbitScreeningService::class.java)

    private fun roles(ctx: Context): RoleManager? = ctx.getSystemService(RoleManager::class.java)

    fun available(ctx: Context): Boolean = runCatching { roles(ctx)?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) == true }.getOrDefault(false)

    fun held(ctx: Context): Boolean = runCatching { roles(ctx)?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true }.getOrDefault(false)

    private fun setPart(ctx: Context, on: Boolean) = runCatching {
        ctx.packageManager.setComponentEnabledSetting(
            part(ctx),
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
            PackageManager.DONT_KILL_APP,
        )
    }

    fun requestIntent(ctx: Context): Intent? {
        if (!available(ctx)) return null
        setPart(ctx, true)
        return runCatching { roles(ctx)?.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING) }.getOrNull()
    }

    /** Without the role the service goes back off. */
    fun tidy(ctx: Context) {
        val on = ctx.packageManager.getComponentEnabledSetting(part(ctx)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        if (!held(ctx) && on) setPart(ctx, false)
    }
}
