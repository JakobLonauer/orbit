package io.github.thelony97.orbit.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import io.github.thelony97.orbit.core.domain.CallerCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class CallerNotificationTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private val nm = ctx.getSystemService(NotificationManager::class.java)

    @Before fun setUp() {
        shadowOf(ctx).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Notifications.createChannels(ctx)
    }

    private fun posted(id: Int): Notification? = shadowOf(nm).getNotification(id)

    @Test fun callerInfoKeepsSecretsOffTheLockScreen() {
        val card = CallerCard("a", "Anna Berger", role = "Lehrerin", note = "Geheime Notiz", customerNo = "KD-1", topics = listOf("Wohnung"))
        Notifications.callerInfo(ctx, card, "+43 660 1234567")
        val n = posted(Notifications.ID_CALLER)
        assertNotNull(n)
        n!!
        assertEquals("Anna Berger", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(Notification.VISIBILITY_PRIVATE, n.visibility)
        val pub = n.publicVersion!!
        assertEquals("Anna Berger", pub.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Lehrerin", pub.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        val big = n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()
        assertTrue(big.contains("Geheime Notiz") && big.contains("Wohnung") && big.contains("KD-1"))
        assertFalse(pub.extras.toString().contains("Geheime"))
        // PLAN 9 B7: a customer number to read out comes first (at most three buttons).
        assertEquals(listOf("Diktieren", "In 15 Min erinnern", "In 1 Std erinnern"), n.actions.map { it.title.toString() })
    }

    @Test fun callbackReminderHasCallButton() {
        Notifications.callback(ctx, "Anna", "+436601234567", "a")
        val n = posted(Notifications.callbackId("+436601234567"))!!
        assertEquals("Zurückrufen: Anna", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Anrufen", n.actions.single().title.toString())
    }

    @Test fun idleClearsTheCallerCard() {
        Notifications.callerInfo(ctx, CallerCard("a", "Anna"), "1")
        Notifications.cancelTopics(ctx)
        assertEquals(null, posted(Notifications.ID_CALLER))
    }
}
