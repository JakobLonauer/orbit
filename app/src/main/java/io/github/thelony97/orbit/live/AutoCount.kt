package io.github.thelony97.orbit.live

import android.content.Context

/** How often each automation ran (PLAN 9 E17 „Zähler“) – small, per device, any process. */
object AutoCount {
    const val SCREEN = "screen"
    const val NUDGE = "nudge"
    const val BUSY_SMS = "busy-sms"
    const val MEET_PHOTOS = "meet-photos"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("auto-count", Context.MODE_PRIVATE)

    @Synchronized
    fun bump(ctx: Context, id: String) {
        val p = prefs(ctx)
        p.edit().putInt(id, p.getInt(id, 0) + 1).apply()
    }

    fun get(ctx: Context, id: String): Int = prefs(ctx).getInt(id, 0)
}
