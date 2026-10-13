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
import io.github.thelony97.orbit.ui.home.HomeScreen
import io.github.thelony97.orbit.ui.list.ContactListScreen
import io.github.thelony97.orbit.ui.verlauf.VerlaufScreen
import io.github.thelony97.orbit.ui.settings.ArchiveScreen
import io.github.thelony97.orbit.ui.settings.SettingsScreen
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
        // Screens are shown as they look after the Start-Moment.
        io.github.thelony97.orbit.ui.Session.startPlayed = true
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
            Contact("zoe", firstName = "Zoe", lastName = "Fischer", groupIds = listOf("fr")),
            Contact("emil", firstName = "Emil", lastName = "Brandl", groupIds = listOf("uni")),
            Contact("bea", firstName = "Bea", lastName = "Kern"),
            Contact("arzt", kind = ContactKind.ORG, orgCategory = "health", company = "Arzt Müller",
                phones = listOf(LabeledValue("Termine", "0732 111"))),
            Contact("alt1", firstName = "Kevin", lastName = "Maier", archived = true, position = "Trainer", company = "SV Linz",
                updatedAt = now - 400 * day, entries = listOf(e("k2", TypeIds.CALL, 500), e("k1", TypeIds.MEET, 700, "Training"))),
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

    @Test fun verlaufTab() = shot("verlauf") { VerlaufScreen(data, idx, nav) }

    @Test fun contactList() = shot("list") { ContactListScreen(data, idx, nav) }

    /** Mehrfachauswahl (PLAN 3.2): header turns into the bulk bar, picked avatars turn into checks. */
    @Test fun contactListSelecting() = shot("list_select") { ContactListScreen(data, idx, nav, initialSelection = setOf("anna", "tom")) }

    @Test fun contactDetail() = shot("detail") { ContactDetailScreen("anna", data, idx, nav) }

    @Test fun archive() = shot("archive") { ArchiveScreen(data, idx, nav) }

    @Test fun archivedDetail() = shot("detail_archived") { ContactDetailScreen("alt1", data, idx, nav) }

    // W6: every state designed – empty and very long.
    private val empty = OrbitData(contacts = listOf(Contact("me", isMe = true, firstName = "Jakob")), entryTypes = Defaults.entryTypes, settings = Settings(onboardingDone = true))

    @Test fun emptyHome() = shot("home_empty") { HomeScreen(empty, Index(empty), nav, tab = 0, onTab = {}) }

    @Test fun emptyVerlauf() = shot("verlauf_empty") { VerlaufScreen(empty, Index(empty), nav) }

    @Test fun longNameDetail() {
        val long = data.copy(contacts = data.contacts + Contact(
            "long", firstName = "Maximiliane-Theresia", lastName = "von Hohenzollern-Sigmaringen-Wolkenstein",
            position = "Leiterin der Abteilung für interdisziplinäre Bildungsforschung", company = "Pädagogische Hochschule Oberösterreich",
        ))
        shot("detail_long") { ContactDetailScreen("long", long, Index(long), nav) }
    }

    /** The whole contact page in PLAN 3.3 order: fields with copy, links, pinned note, organisations apart from people, add row. */
    @Config(qualifiers = "w411dp-h2400dp-xxhdpi")
    @Test fun fullDetail() {
        val kathi = Contact(
            "kathi", firstName = "Katharina", lastName = "Leitner", position = "Projektleitung", company = "JKU Linz",
            phones = listOf(LabeledValue("Mobil", "+43 676 5550123")),
            emails = listOf(LabeledValue("Privat", "kathi@example.at")),
            links = listOf(io.github.thelony97.orbit.core.model.Link("l1", "Portfolio", "https://kathi.example.at")),
            fields = listOf(io.github.thelony97.orbit.core.model.CustomField("f1", "Kundennummer", "KD-48213")),
            pinnedNote = "Lieber per SMS als anrufen",
            notes = "Lernt Japanisch, will 2027 nach Kyoto.",
            topics = listOf(CheckItem("kt1", "Wie war die Präsentation?")),
            entries = listOf(e("ka1", TypeIds.CALL, 1, sec = 300, incoming = true), e("ka2", TypeIds.MEET, 12, "Mittagessen Mensa")),
        ).withBirthday(inDays(40)) { "bd-kathi" }
        val full = data.copy(
            contacts = data.contacts + kathi + Contact("jku", kind = ContactKind.ORG, orgCategory = "work", company = "JKU Linz", phones = listOf(LabeledValue("Zentrale", "0732 2468"))),
            relations = listOf(
                io.github.thelony97.orbit.data.Relation("r1", "kathi", "arzt", "patient"),
                io.github.thelony97.orbit.data.Relation("r2", "kathi", "anna", "friend"),
            ),
        )
        shot("detail_full") { ContactDetailScreen("kathi", full, Index(full), nav) }
    }

    /** The shell: glass bar with the planet over the contact list (tab 0 – no map view). */
    @Test fun home() = shot("home") { HomeScreen(data, idx, nav, tab = 0, onTab = {}) }

    /** Start-Moment half-way: ring drawn, planet on its way (frozen clock). */
    @Test fun startMoment() {
        io.github.thelony97.orbit.ui.Session.startPlayed = false
        io.github.thelony97.orbit.security.AppLock.unlock()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            OrbitTheme { Box(Modifier.fillMaxSize().background(Space.Void)) { HomeScreen(data, idx, nav, tab = 0, onTab = {}) } }
        }
        compose.mainClock.advanceTimeBy(380)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/screen_start_moment.png")
        compose.mainClock.autoAdvance = true
        io.github.thelony97.orbit.ui.Session.startPlayed = true
    }

    @Test fun homeVerlauf() = shot("home_verlauf") { HomeScreen(data, idx, nav, tab = 1, onTab = {}) }

    @Test fun settings() = shot("settings") { SettingsScreen(data, nav) }

    @Test fun settingsArea() = shot("settings_security") { SettingsScreen(data, nav, area = "security") }

    /** Long-press preview lives in a Popup – capture the whole screen, not just the root. */
    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    @Test fun previewCard() {
        val anna = data.contacts.single { it.id == "anna" }
        compose.setContent {
            OrbitTheme {
                Box(Modifier.fillMaxSize().background(Space.Void)) {
                    io.github.thelony97.orbit.ui.list.PreviewCard(io.github.thelony97.orbit.ui.list.PreviewState(anna), idx)
                }
            }
        }
        compose.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/screen_preview.png")
    }

    /** Sharing as QR (PLAN 3.3) is a dialog window – capture the whole screen. */
    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    @Test fun qrCode() {
        val anna = data.contacts.single { it.id == "anna" }
        compose.setContent {
            OrbitTheme {
                Box(Modifier.fillMaxSize().background(Space.Void)) {
                    io.github.thelony97.orbit.ui.detail.QrDialog(anna, onDismiss = {})
                }
            }
        }
        compose.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/screen_qr.png")
    }

    /** Profilbild zuschneiden (PLAN 3.3): circle frame over a landscape picture. */
    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    @Test fun cropPhoto() {
        val bmp = android.graphics.Bitmap.createBitmap(900, 600, android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bmp).apply {
            val p = android.graphics.Paint()
            p.shader = android.graphics.LinearGradient(0f, 0f, 900f, 600f, 0xFF3A6FD8.toInt(), 0xFFE8A060.toInt(), android.graphics.Shader.TileMode.CLAMP)
            drawRect(0f, 0f, 900f, 600f, p)
            p.shader = null
            p.color = 0xFFF2D5C0.toInt()
            drawCircle(450f, 260f, 120f, p)
        }
        compose.setContent {
            OrbitTheme {
                Box(Modifier.fillMaxSize().background(Space.Void)) {
                    io.github.thelony97.orbit.ui.edit.CropDialog(bmp, onDismiss = {}, onCrop = {})
                }
            }
        }
        compose.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/screen_crop.png")
    }

    // R2 call screen (PLAN 3.12), not wired to telecom yet.
    private val callCard = io.github.thelony97.orbit.core.domain.CallerCard(
        "anna", "Anna Berger", role = "Lehrerin", note = "Fragt nach der Wohnung in Wien",
        customerNo = "KD-48213", lastAt = now - 3 * day, lastKind = "Anruf", topics = listOf("Wohnung in Wien?", "Konzert im Mai"),
    )
    private val noActions = object : io.github.thelony97.orbit.ui.call.CallActions {}

    @Test fun callIncomingLocked() = shot("call_incoming_locked") {
        io.github.thelony97.orbit.ui.call.CallScreen(io.github.thelony97.orbit.ui.call.CallUi("+43 660 1234567", callCard), noActions, now)
    }

    @Test fun callIncoming() = shot("call_incoming") {
        io.github.thelony97.orbit.ui.call.CallScreen(io.github.thelony97.orbit.ui.call.CallUi("+43 660 1234567", callCard, phoneUnlocked = true), noActions, now)
    }

    @Test fun callActive() = shot("call_active") {
        io.github.thelony97.orbit.ui.call.CallScreen(
            io.github.thelony97.orbit.ui.call.CallUi("+43 660 1234567", callCard, active = true, elapsed = 125, speaker = true, phoneUnlocked = true, doneTopics = setOf("Konzert im Mai")),
            noActions, now,
        )
    }

    @Test fun callArchived() = shot("call_archived") {
        io.github.thelony97.orbit.ui.call.CallScreen(io.github.thelony97.orbit.ui.call.CallUi("+43 660 1234567", callCard.copy(archived = true), phoneUnlocked = true), noActions, now)
    }

    @Test fun callUnknown() = shot("call_unknown") {
        io.github.thelony97.orbit.ui.call.CallScreen(io.github.thelony97.orbit.ui.call.CallUi("+43 732 555 123", null), noActions, now)
    }
}
