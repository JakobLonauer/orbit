package io.github.thelony97.orbit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.thelony97.orbit.core.domain.OrbitLine
import io.github.thelony97.orbit.core.model.BuiltinTypes
import io.github.thelony97.orbit.core.model.CalendarDay
import io.github.thelony97.orbit.core.model.CheckItem
import io.github.thelony97.orbit.core.model.LabeledValue
import io.github.thelony97.orbit.core.model.Occasion
import io.github.thelony97.orbit.core.model.OccasionKind
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.mail.MailAccounts
import io.github.thelony97.orbit.ui.Nav
import io.github.thelony97.orbit.ui.theme.OrbitTheme
import io.github.thelony97.orbit.ui.theme.Space
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalDateTime

/** Version 3.0-E (PLAN 9.7): Orbit-Zeile, Antwortzeile, Mitternachts-Rad, Stapel, Board, Automatiken. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
@OptIn(ExperimentalRoborazziApi::class)
class ScreensV3EScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Before fun init() {
        io.github.thelony97.orbit.ui.Session.startPlayed = true
        val ctx = RuntimeEnvironment.getApplication()
        OrbitRepository.init(ctx)
        MailAccounts.init(ctx)
    }

    private val nav = Nav(open = {}, back = {}, replace = {}, home = {})
    private val today = LocalDate.now()
    private val anna = Contact(
        "anna", firstName = "Anna", lastName = "Huber", phones = listOf(LabeledValue("", "0664 1234567")),
        dates = listOf(Occasion("b", OccasionKind.BIRTHDAY, date = CalendarDay(today.plusDays(3).dayOfMonth, today.plusDays(3).monthValue, 1996))),
        topics = listOf(CheckItem("t1", "Wohnung in Wien?", createdAt = 1), CheckItem("t2", "Konzertkarten", createdAt = 2)),
        gifts = listOf(CheckItem("g1", "Buch „Der Hobbit“", priceCents = 2_490), CheckItem("g2", "Schal", bought = true, priceCents = 3_900)),
        giftBudgetCents = 6_000,
    )
    private val tom = Contact("tom", firstName = "Tom", lastName = "Berger", gifts = listOf(CheckItem("g3", "Kerze", done = true, bought = true, priceCents = 1_500)))
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

    /** E1: „Anna Kaffee morgen 15 Uhr +Wohnung“ – the parts as chips. */
    @Test fun orbitLine() = moment("v3e_line", 900) {
        val line = OrbitLine.parse("Anna Kaffee morgen 15 Uhr +Wohnung", data.contacts, data.entryTypes, LocalDateTime.now())
        Column(Modifier.padding(top = 48.dp)) {
            io.github.thelony97.orbit.ui.list.OrbitLineChips(line, onClick = {})
            io.github.thelony97.orbit.ui.list.OrbitLineChips(OrbitLine.parse("#uni hat:geburtstag ort:linz", data.contacts, data.entryTypes, LocalDateTime.now()), onClick = {}, onKeep = {})
        }
    }

    /** E2/E4/E6: the answer line with a planned message and the snippets. */
    @Test fun replyBar() = moment("v3e_reply", 600) {
        Box(Modifier.fillMaxSize()) {
            io.github.thelony97.orbit.ui.detail.ReplyBar(anna, data, Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
        }
    }

    /** E4: the Mitternachts-Rad, pulled to the birthday's midnight. */
    @Test fun midnightWheel() = screen("v3e_wheel") {
        io.github.thelony97.orbit.ui.detail.MidnightWheel(anna, data, onPick = {}, onDismiss = {})
    }

    /** E8: the Themen-Stapel. */
    @Test fun topics() = moment("v3e_topics", 600) {
        io.github.thelony97.orbit.ui.callbacks.TopicStackScreen(data, io.github.thelony97.orbit.domain.Index(data), nav)
    }

    /** E7: the Geschenke-Board with budget. */
    @Test fun gifts() = moment("v3e_gifts", 600) {
        io.github.thelony97.orbit.ui.occasions.GiftBoardScreen(data, nav)
    }

    /** E17: Automatiken. */
    @Test fun automations() = moment("v3e_auto", 400) {
        Column(Modifier.padding(top = 48.dp)) { io.github.thelony97.orbit.ui.settings.AutomationsPage(data, onGroups = {}) }
    }
}
