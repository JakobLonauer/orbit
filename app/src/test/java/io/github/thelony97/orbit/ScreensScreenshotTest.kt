package io.github.thelony97.orbit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.thelony97.orbit.data.CheckItem
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.ContactKind
import io.github.thelony97.orbit.data.DayOfYear
import io.github.thelony97.orbit.data.Defaults
import io.github.thelony97.orbit.data.Entry
import io.github.thelony97.orbit.data.EntrySource
import io.github.thelony97.orbit.data.Group
import io.github.thelony97.orbit.data.LabeledValue
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.data.Settings
import io.github.thelony97.orbit.data.TypeIds
import io.github.thelony97.orbit.domain.Index
import io.github.thelony97.orbit.mail.MailAccounts
import io.github.thelony97.orbit.ui.Nav
import io.github.thelony97.orbit.ui.detail.ContactDetailScreen
import io.github.thelony97.orbit.ui.list.ContactListScreen
import io.github.thelony97.orbit.ui.orbit.OrbitScreen
import io.github.thelony97.orbit.ui.settings.ArchiveScreen
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

/** Real screens with fake data – the visual check for every update (PLAN 6, Wow-Gate). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ScreensScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Before fun init() {
        val ctx = RuntimeEnvironment.getApplication()
        OrbitRepository.init(ctx)
        MailAccounts.init(ctx)
    }

    private val day = 24L * 60 * 60 * 1000
    private val now = System.currentTimeMillis()
    private val today = LocalDate.now()
    private fun inDays(n: Long) = today.plusDays(n).let { DayOfYear(it.dayOfMonth, it.monthValue, 1996) }
    private fun e(id: String, type: String, daysAgo: Long, note: String = "", sec: Long? = null, incoming: Boolean? = null) =
        Entry(id = id, contactId = "", typeId = type, at = now - daysAgo * day - 3_600_000, note = note, durationSec = sec, incoming = incoming,
            source = if (type == TypeIds.CALL) EntrySource.CALL_LOG else EntrySource.MANUAL)

    private val groups = listOf(
        Group("fam", "Familie", Defaults.groupColors[0], pinned = true),
        Group("fr", "Freunde", Defaults.groupColors[5], pinned = true),
        Group("uni", "Uni", Defaults.groupColors[3]),
    )
    private val data = OrbitData(
        contacts = listOf(
            Contact("me", isMe = true, firstName = "Jakob", createdAt = 1),
            Contact("anna", firstName = "Anna", lastName = "Berger", groupIds = listOf("fr"), favorite = true,
                phones = listOf(LabeledValue("Mobil", "+43 660 1234567")),
                position = "Lehrerin",
                topics = listOf(CheckItem("t1", "Wohnung in Wien?")),
                gifts = listOf(CheckItem("g1", "Bücher von Zweig")),
                entries = listOf(e("a1", TypeIds.CALL, 0, sec = 754, incoming = true), e("a2", TypeIds.MEET, 6, "Kaffee am Hauptplatz"), e("a3", TypeIds.MESSAGE, 2)))
                .withBirthday(inDays(0)) { "bd-anna" },
            Contact("papa", firstName = "Peter", lastName = "Lonauer", groupIds = listOf("fam"),
                phones = listOf(LabeledValue("Mobil", "+43 664 7654321")),
                entries = listOf(e("p1", TypeIds.CALL, 3, sec = 1820, incoming = false)))
                .withBirthday(inDays(7)) { "bd-papa" },
            Contact("tom", firstName = "Tom", lastName = "Huber", groupIds = listOf("uni"),
                entries = listOf(e("t1", TypeIds.MEET, 21, "Lernen für Linguistik"))),
            Contact("lisa", firstName = "Lisa", lastName = "Wagner", groupIds = listOf("fr"),
                entries = listOf(e("l1", TypeIds.MESSAGE, 95))),
            Contact("neu", firstName = "Mara", lastName = "Novak", groupIds = listOf("uni")),
            Contact("arzt", kind = ContactKind.ORG, orgCategory = "health", company = "Arzt Müller",
                phones = listOf(LabeledValue("Termine", "0732 111"))),
            Contact("alt1", firstName = "Kevin", lastName = "Maier", archived = true, position = "Trainer", company = "SV Linz",
                updatedAt = now - 400 * day, entries = listOf(e("k1", TypeIds.MEET, 700, "Training"), e("k2", TypeIds.CALL, 500))),
            Contact("alt2", firstName = "Sabine", lastName = "Gruber", archived = true, updatedAt = now - 20 * day,
                groupIds = listOf("fr"), notes = "Umgezogen nach Graz"),
        ),
        groups = groups,
        entryTypes = Defaults.entryTypes,
        settings = Settings(onboardingDone = true),
    )
    private val idx = Index(data)
    private val nav = Nav(open = {}, back = {}, replace = {}, home = {})

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            OrbitTheme { Box(Modifier.fillMaxSize().background(Space.Void)) { content() } }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/screen_$name.png")
    }

    @Test fun orbitTab() = shot("orbit") { OrbitScreen(data, idx, nav) }

    @Test fun contactList() = shot("list") { ContactListScreen(data, idx, nav) }

    @Test fun contactDetail() = shot("detail") { ContactDetailScreen("anna", data, idx, nav) }

    @Test fun archive() = shot("archive") { ArchiveScreen(data, idx, nav) }
}
