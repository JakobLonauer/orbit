package io.github.thelony97.orbit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.thelony97.orbit.core.domain.MyNumbers
import io.github.thelony97.orbit.core.domain.ShareIn
import io.github.thelony97.orbit.core.model.CustomField
import io.github.thelony97.orbit.core.model.Greeting
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.ContactKind
import io.github.thelony97.orbit.data.Defaults
import io.github.thelony97.orbit.data.LabeledValue
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.data.Settings
import io.github.thelony97.orbit.domain.Index
import io.github.thelony97.orbit.mail.MailAccounts
import io.github.thelony97.orbit.phonebook.PhoneBookFresh
import io.github.thelony97.orbit.ui.Nav
import io.github.thelony97.orbit.ui.components.DictationView
import io.github.thelony97.orbit.ui.detail.ContactDetailScreen
import io.github.thelony97.orbit.ui.detail.ShareFieldsSheet
import io.github.thelony97.orbit.ui.edit.ContactEditScreen
import io.github.thelony97.orbit.ui.list.ContactListScreen
import io.github.thelony97.orbit.ui.sheets.PendingShare
import io.github.thelony97.orbit.ui.sheets.ShareSheet
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

/** The screens and parts of Version 3.0 (PLAN 9) with fixed data – Wow-Gate material. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
@OptIn(ExperimentalRoborazziApi::class)
class ScreensV3ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Before fun init() {
        io.github.thelony97.orbit.ui.Session.startPlayed = true
        val ctx = RuntimeEnvironment.getApplication()
        OrbitRepository.init(ctx)
        MailAccounts.init(ctx)
    }

    private val anna = Contact(
        "anna", firstName = "Anna", lastName = "Huber", title = "Mag.a", nickname = "Anni", salutation = "Frau",
        greeting = Greeting(formal = true), position = "Lehrerin", company = "BRG Linz", favorite = true,
        phones = listOf(LabeledValue("Mobil", "+43 660 1234567"), LabeledValue("Arbeit", "0732 2468 12")),
        emails = listOf(LabeledValue("Arbeit", "anna.huber@brg.at")),
        addresses = listOf(LabeledValue("Privat", "Landstraße 12, 4020 Linz")),
        fields = listOf(CustomField("f1", "Kundennummer", "K-4821-77")),
    ).withBirthday(io.github.thelony97.orbit.data.DayOfYear(17, 5, 1990)) { "bd" }
    private val mayr = Contact("mayr", firstName = "Sepp", lastName = "Mayr", phones = listOf(LabeledValue("Mobil", "+43 664 5550001")))
    private val meier = Contact("meier", firstName = "Lena", lastName = "Meier")
    private val bank = Contact(
        "bank", kind = ContactKind.ORG, company = "Raiffeisen Linz",
        phones = listOf(LabeledValue("Zentrale", "0732 6596")), numberPrefixes = listOf("0732 6596"),
        fields = listOf(CustomField("b1", "Kundennummer", "RB-100 2834")),
    )
    private val me = Contact(
        "me", isMe = true, firstName = "Jakob",
        fields = listOf(CustomField("m1", "SV-Nr.", "1234 170597"), CustomField("m2", "IBAN", "AT61 1904 3002 3457 3201")),
    )
    private val data = OrbitData(
        contacts = listOf(me, anna, mayr, meier, bank, Contact("tom", firstName = "Tom", lastName = "Berger")),
        entryTypes = Defaults.entryTypes,
        settings = Settings(onboardingDone = true, phoneBook = true),
    )
    private val idx = Index(data)
    private val nav = Nav(open = {}, back = {}, replace = {}, home = {})

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent { OrbitTheme { Box(Modifier.fillMaxSize().background(Space.Void)) { content() } } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/screen_$name.png")
    }

    private fun screen(name: String, content: @Composable () -> Unit) {
        compose.setContent { OrbitTheme { Box(Modifier.fillMaxSize().background(Space.Void)) { content() } } }
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/screen_$name.png")
    }

    /** PLAN 9 A10/A8: Titel, Rufname, Anrede, Du/Sie, Klingelton (phone book on). */
    @Test fun editPerson() = shot("v3_edit_person") { ContactEditScreen("anna", null, data, idx, nav) }

    /** PLAN 9 A2: a number another app handed over lights up teal. */
    @Test fun editIntake() {
        val draft = Contact("new", firstName = "Max", lastName = "Muster", phones = listOf(LabeledValue("Mobil", "0660 9998887")))
        val json = OrbitRepository.json.encodeToString(Contact.serializer(), draft)
        compose.mainClock.autoAdvance = false
        compose.setContent { OrbitTheme { Box(Modifier.fillMaxSize().background(Space.Void)) { ContactEditScreen(null, json, data, idx, nav) } } }
        compose.mainClock.advanceTimeBy(100)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/screen_v3_edit_intake.png")
    }

    /** PLAN 9 A10: the head with title and Rufname. */
    @Test fun detailTitled() = shot("v3_detail_titled") { ContactDetailScreen("anna", data, idx, nav) }

    /** PLAN 9 A11: „Klingt ähnlich“ behind the exact hits. */
    @Test fun searchSoundsAlike() {
        compose.setContent { OrbitTheme { Box(Modifier.fillMaxSize().background(Space.Void)) { ContactListScreen(data, idx, nav) } } }
        compose.onNode(hasSetTextAction()).performTextInput("Maier")
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/screen_v3_search_alike.png")
    }

    /** PLAN 9 C1/A0: the Heute row with ghosts. */
    @Test fun todayGhosts() = shot("v3_today_ghosts") {
        Column(Modifier.padding(top = 48.dp)) {
            TodayRow(
                listOf(
                    TodayCard.PhoneBookOffer,
                    TodayCard.PhoneBookNew(listOf(PhoneBookFresh.Item(1, "Max Muster", 501), PhoneBookFresh.Item(2, "Eva Gruber", 502), PhoneBookFresh.Item(3, "Ali Kaya", 503))),
                ),
                nav,
            )
        }
    }

    /** PLAN 9 A12: Weitergeben mit Feldwahl. */
    @Test fun shareFields() = screen("v3_share_fields") { ShareFieldsSheet(anna, onDismiss = {}, onQr = {}) }

    /** PLAN 9 A4: a marked number that belongs to an organisation's range. */
    @Test fun markedNumber() = screen("v3_marked_number") {
        ShareSheet(PendingShare(ShareIn.Item.Number("0732 6596 410"), emptyList()), data, idx, nav, onDone = {})
    }

    /** PLAN 9 A9: Nummern-Schublade. */
    @Test fun numbersDrawer() = shot("v3_numbers") {
        io.github.thelony97.orbit.notify.NumbersDrawer(
            onClose = {},
            preview = listOf(
                MyNumbers.Item(null, "SV-Nr.", "1234 170597"),
                MyNumbers.Item(null, "IBAN", "AT61 1904 3002 3457 3201"),
                MyNumbers.Item("Raiffeisen Linz", "Kundennummer", "RB-100 2834"),
                MyNumbers.Item("Wiener Städtische", "Polizze", "WS-55-1029-3"),
            ),
        )
    }

    /** PLAN 9 B7/A9: Diktier-Ansicht. */
    @Test fun dictation() = shot("v3_dictation") { DictationView("IBAN", "AT61 1904 3002 3457 3201", onClose = {}) }
}
