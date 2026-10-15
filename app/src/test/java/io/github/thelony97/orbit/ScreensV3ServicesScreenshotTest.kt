package io.github.thelony97.orbit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.thelony97.orbit.core.model.ContactKind
import io.github.thelony97.orbit.core.model.CustomField
import io.github.thelony97.orbit.core.model.LabeledValue
import io.github.thelony97.orbit.core.model.OrgLink
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.mail.MailAccounts
import io.github.thelony97.orbit.ui.Nav
import io.github.thelony97.orbit.ui.components.IdCard
import io.github.thelony97.orbit.ui.services.MyServicesRow
import io.github.thelony97.orbit.ui.services.myServices
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

/** Version 3.0-D (PLAN 9.6): Meine Dienste and the Ausweis with fixed data. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
@OptIn(ExperimentalRoborazziApi::class)
class ScreensV3ServicesScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Before fun init() {
        io.github.thelony97.orbit.ui.Session.startPlayed = true
        val ctx = RuntimeEnvironment.getApplication()
        OrbitRepository.init(ctx)
        MailAccounts.init(ctx)
    }

    private val nav = Nav(open = {}, back = {}, replace = {}, home = {})

    private fun org(id: String, name: String, hours: String = "", phone: String = "", block: (Contact) -> Contact = { it }) = block(
        Contact(
            id, kind = ContactKind.ORG, company = name, openingHours = hours, phoneHours = phone,
            phones = listOf(LabeledValue("Zentrale", "+43 732 771234")),
        ),
    )

    private val data = OrbitData(
        contacts = listOf(
            Contact("me", isMe = true, firstName = "Jakob"),
            org("gp", "Dr. Huber", "Mo-Fr 08:00-12:00", "Mo-Fr 07:30-11:00") {
                it.copy(vacationUntil = LocalDate.now().plusDays(9).toString(), vacationSubstitute = "gp2")
            },
            org("gp2", "Dr. Mayr", "Mo-Fr 08:00-12:00"),
            org("bank", "Raiffeisen Linz", "Mo-Fr 08:00-16:00") {
                it.copy(fields = listOf(CustomField("f", "Kundennummer", "4711 0815 22")))
            },
            org("car", "Werkstatt Gruber", "Mo-Fr 07:00-17:00; Sa 08:00-12:00"),
            org("ins", "Wiener Städtische", "24/7") {
                it.copy(fields = listOf(CustomField("p", "Polizzennummer", "S-4471-889-12")))
            },
        ),
        orgLinks = listOf(
            OrgLink("l1", "me", "gp", "gp"),
            OrgLink("l2", "me", "bank", "bank"),
            OrgLink("l3", "me", "car", AssignmentCustom, "Werkstatt"),
            OrgLink("l4", "me", "ins", "insurance"),
        ),
    )

    private fun moment(name: String, ms: Long, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { OrbitTheme { Box(Modifier.fillMaxSize().background(Space.Void)) { content() } } }
        compose.mainClock.advanceTimeBy(ms)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/screen_$name.png")
    }

    /** D1/D2: the tiles with status (Urlaub → Vertretung, offen/zu, Telefon). */
    @Test fun services() = moment("v3d_services", 600) {
        Column(Modifier.padding(top = 48.dp)) { MyServicesRow(myServices(data), data, nav) }
    }

    /** D4: a customer number as a card – big number, Code 128, holo. */
    @Test fun idCard() {
        compose.setContent {
            OrbitTheme { IdCard("Raiffeisen Linz", "Kundennummer", "4711 0815 22", Color(0xFFFFB86B), onDictate = {}, onClose = {}) }
        }
        compose.waitForIdle()
        Thread.sleep(300)
        compose.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/screen_v3d_idcard.png")
    }

    private companion object {
        const val AssignmentCustom = io.github.thelony97.orbit.core.model.AssignmentRoles.CUSTOM
    }
}
