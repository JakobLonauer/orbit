package io.github.thelony97.orbit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.thelony97.orbit.core.domain.Bounces
import io.github.thelony97.orbit.core.model.BuiltinTypes
import io.github.thelony97.orbit.core.model.CheckItem
import io.github.thelony97.orbit.core.model.LabeledValue
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.domain.Index
import io.github.thelony97.orbit.mail.MailAccounts
import io.github.thelony97.orbit.phonebook.CalendarGhosts
import io.github.thelony97.orbit.ui.Nav
import io.github.thelony97.orbit.ui.theme.OrbitTheme
import io.github.thelony97.orbit.ui.theme.Space
import io.github.thelony97.orbit.ui.today.TodayCard
import io.github.thelony97.orbit.ui.today.TodayRow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 3.0-G Feinschliff II: Geschenke leer/neu, Termin mit Fotos, tote Mail-Adresse, rollende Zeit. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
@OptIn(ExperimentalRoborazziApi::class)
class ScreensV3GScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Before fun init() {
        io.github.thelony97.orbit.ui.Session.startPlayed = true
        val ctx = RuntimeEnvironment.getApplication()
        OrbitRepository.init(ctx)
        MailAccounts.init(ctx)
    }

    private val nav = Nav(open = {}, back = {}, replace = {}, home = {})
    private val anna = Contact(
        "anna", firstName = "Anna", lastName = "Huber",
        phones = listOf(LabeledValue("Mobil", "0664 1234567")),
        emails = listOf(LabeledValue("Privat", "anna@huber.at"), LabeledValue("Uni", "anna.huber@jku.at")),
        gifts = listOf(CheckItem("g1", "Buch „Der Hobbit“", priceCents = 2_490)),
    )
    private val tom = Contact("tom", firstName = "Tom", lastName = "Berger")
    private val data = OrbitData(contacts = listOf(Contact("me", isMe = true, firstName = "Jakob"), anna, tom), entryTypes = BuiltinTypes.all)

    private fun moment(name: String, ms: Long, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { OrbitTheme { Box(Modifier.fillMaxSize().background(Space.Void)) { content() } } }
        compose.mainClock.advanceTimeBy(ms)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/screen_$name.png")
    }

    private fun screen(name: String, content: @Composable () -> Unit) {
        compose.setContent { OrbitTheme { Box(Modifier.fillMaxSize().background(Space.Void)) { content() } } }
        compose.waitForIdle()
        Thread.sleep(400)
        compose.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/screen_$name.png")
    }

    /** W6: no gifts – one sentence, one action. */
    @Test fun giftsEmpty() = moment("v3g_gifts_empty", 600) {
        val empty = data.copy(contacts = data.contacts.map { it.copy(gifts = emptyList()) })
        io.github.thelony97.orbit.ui.occasions.GiftBoardScreen(empty, nav)
    }

    /** A new idea for Anna (after „Für wen?“). */
    @Test fun giftAdd() = screen("v3g_gift_add") {
        // Anna picked in the row of next birthdays.
        io.github.thelony97.orbit.ui.occasions.GiftBoardScreen(data, nav, initialAdd = "anna")
    }

    /** E9: a finished appointment with its photos as a suggestion (tiles in their final form). */
    @Test fun meetWithPhotos() {
        // Seven „photos“: soft gradients in different hues; the third cannot be read (left out).
        io.github.thelony97.orbit.ui.today.MeetThumbs.load = { _, id ->
            if (id == 3L) null
            else {
                val bmp = android.graphics.Bitmap.createBitmap(144, 144, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bmp)
                val hue = (id * 47f) % 360f
                val paint = android.graphics.Paint().apply {
                    shader = android.graphics.LinearGradient(
                        0f, 0f, 144f, 144f,
                        android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.45f, 0.85f)),
                        android.graphics.Color.HSVToColor(floatArrayOf((hue + 40f) % 360f, 0.6f, 0.45f)),
                        android.graphics.Shader.TileMode.CLAMP,
                    )
                }
                canvas.drawRect(0f, 0f, 144f, 144f, paint)
                bmp.asImageBitmap()
            }
        }
        meetShot()
    }

    private fun meetShot() = screen("v3g_meet_photos") {
        val now = System.currentTimeMillis()
        val item = CalendarGhosts.Item("cal:1:1", "anna", "Kaffee mit Anna", now - 3 * 3_600_000L, now - 2 * 3_600_000L, photos = (1L..7L).toList())
        val plain = CalendarGhosts.Item("cal:2:2", "tom", "Laufen mit Tom", now - 26 * 3_600_000L, now - 25 * 3_600_000L)
        Column(Modifier.padding(top = 48.dp)) {
            TodayRow(listOf(TodayCard.CalendarMeet(item, anna)), nav)
            TodayRow(listOf(TodayCard.CalendarMeet(plain, tom)), nav, Modifier.padding(top = 16.dp))
        }
    }

    /** E9/E17: „Termin → Fotos“ among the Automatiken, switched on. */
    @Test fun automationsPhotos() = moment("v3g_auto", 400) {
        val on = data.copy(settings = data.settings.copy(meetPhotos = true))
        Column(Modifier.padding(top = 48.dp)) { io.github.thelony97.orbit.ui.settings.AutomationsPage(on, onGroups = {}) }
    }

    /** E14: a bounce marked Anna's private address – orange, „nicht erreicht“. */
    @Test fun deadMail() = moment("v3g_dead_mail", 900) {
        val bounced = Bounces.apply(anna, failed = mapOf("anna@huber.at" to 1L), heard = emptyMap())
        val d = data.copy(contacts = data.contacts.map { if (it.id == "anna") bounced else it })
        io.github.thelony97.orbit.ui.detail.ContactDetailScreen("anna", d, Index(d), nav)
    }

    /** PLAN 1.2: the time rolls digit by digit – caught in the middle of „heute 23:55“ → „morgen 00:00“. */
    @Test fun rollingTime() = moment("v3g_rolling", 1_075) {
        var text by remember { mutableStateOf("heute 23:55") }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(1_000)
            text = "morgen 00:00"
        }
        Box(Modifier.padding(top = 120.dp, start = 32.dp)) {
            io.github.thelony97.orbit.core.designsystem.RollingText(
                text, style = androidx.compose.material3.MaterialTheme.typography.displaySmall, color = Space.Text, direction = 1,
            )
        }
    }

    /** E19: the list pinched together – one line per contact, smaller planets. */
    @Test fun denseList() = moment("v3g_list_dense", 900) {
        val many = (1..14).map { i ->
            Contact("p$i", firstName = listOf("Anna", "Bernd", "Clara", "David", "Eva", "Felix", "Gerda")[i % 7], lastName = "Muster$i")
        }
        val d = data.copy(contacts = data.contacts + many, settings = data.settings.copy(listDense = true))
        io.github.thelony97.orbit.ui.list.ContactListScreen(d, Index(d), nav)
    }
}
