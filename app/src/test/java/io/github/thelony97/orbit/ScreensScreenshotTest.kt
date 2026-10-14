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
                topics = listOf(CheckItem("t1", "Wohnung in Wien?"), CheckItem("t2", "Konzertkarten fürs Frühjahr", remindOn = "2026-10-20")),
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
                phones = listOf(LabeledValue("Termine", "0732 111")),
                links = listOf(io.github.thelony97.orbit.core.model.Link("al1", "Termin buchen", "https://arzt-mueller.at/termine", pinned = true))),
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
        val full = fullData()
        shot("detail_full") { ContactDetailScreen("kathi", full, Index(full), nav) }
    }

    /** The organisation's side of the Zuordnungen: „Aus deinen Kontakten“ (PLAN 3.6). */
    @Test fun orgDetail() {
        val full = fullData()
        shot("detail_org") { ContactDetailScreen("jku", full, Index(full), nav) }
    }

    /** Fotos-Seite (PLAN 3.10): month headers, three per row, scrubber at the edge. */
    @Test fun photosPage() {
        val month = 31L * 86_400_000L
        val pics = (0 until 14).map { i ->
            io.github.thelony97.orbit.data.GalleryPhoto(id = "p$i", contactId = "", file = "missing-$i.jpg", addedAt = now - i * month / 3, takenAt = now - i * month / 3)
        }
        val withPics = data.copy(contacts = data.contacts.map { if (it.id == data.contacts.first { c -> !c.isMe && !c.isOrg }.id) it.copy(gallery = pics) else it })
        shot("photos") { io.github.thelony97.orbit.ui.detail.PhotosScreen(null, withPics, Index(withPics), nav) }
    }

    /** Teilen-Eingang (PLAN 3.8): a place from Google Maps. */
    @Test fun shareSheet() {
        compose.setContent {
            OrbitTheme {
                Box(Modifier.fillMaxSize().background(Space.Void)) {
                    io.github.thelony97.orbit.ui.sheets.ShareSheet(
                        io.github.thelony97.orbit.ui.sheets.PendingShare(
                            io.github.thelony97.orbit.core.domain.ShareIn.Item.Place("Hausarztpraxis Dr. Huber", "Landstraße 12, 4020 Linz", "https://maps.app.goo.gl/x"),
                            emptyList(),
                        ),
                        data, idx, nav, onDone = {},
                    )
                }
            }
        }
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/screen_share.png")
    }

    /** A hidden member's own page (PLAN 3.6): role, Durchwahl, conversations. */
    @Test fun memberPage() {
        val full = fullData()
        shot("member") { io.github.thelony97.orbit.ui.detail.MemberScreen("jku", "m1", full, Index(full), nav) }
    }

    private fun fullData(): io.github.thelony97.orbit.data.OrbitData {
        val kathi = Contact(
            "kathi", firstName = "Katharina", lastName = "Leitner", position = "Projektleitung", company = "JKU Linz",
            phones = listOf(LabeledValue("Mobil", "+43 676 5550123")),
            emails = listOf(LabeledValue("Privat", "kathi@example.at")),
            links = listOf(io.github.thelony97.orbit.core.model.Link("l1", "Portfolio", "https://kathi.example.at")),
            fields = listOf(io.github.thelony97.orbit.core.model.CustomField("f1", "Kundennummer", "KD-48213")),
            pinnedNote = "Lieber per SMS als anrufen",
            notes = "Lernt Japanisch, will 2027 nach Kyoto.",
            topics = listOf(CheckItem("kt1", "Wie war die Präsentation?", remindOn = "2026-10-20")),
            entries = listOf(e("ka1", TypeIds.CALL, 1, sec = 300, incoming = true), e("ka2", TypeIds.MEET, 12, "Mittagessen Mensa")),
        ).withBirthday(inDays(40)) { "bd-kathi" }
        val full = data.copy(
            contacts = data.contacts + kathi + Contact("jku", kind = ContactKind.ORG, orgCategory = "work", company = "JKU Linz", phones = listOf(LabeledValue("Zentrale", "0732 2468")), openingHours = "Mo-Fr 07:00-19:00; Sa 08:00-12:00",
                members = listOf(
                    io.github.thelony97.orbit.core.model.Member("m1", "Herr Mayer", role = "Studienservice", phones = listOf(LabeledValue("Durchwahl", "0732 2468-1234"))),
                    io.github.thelony97.orbit.core.model.Member("m2", "Frau Huber", role = "Prüfungsamt"),
                ),
                entries = listOf(io.github.thelony97.orbit.data.Entry("j1", "jku", TypeIds.CALL, at = now - 3 * 86_400_000L, note = "Anrechnung geklärt", memberId = "m1")),
                links = listOf(
                    io.github.thelony97.orbit.core.model.Link("p1", "KUSSS", "https://kusss.jku.at", note = "k12345678", pinned = true),
                    io.github.thelony97.orbit.core.model.Link("p2", "Moodle", "https://moodle.jku.at"),
                    io.github.thelony97.orbit.core.model.Link("p3", "Mensa", "https://www.mensen.at"),
                    io.github.thelony97.orbit.core.model.Link("p4", "Bibliothek", "https://www.jku.at/bibliothek"),
                ),
            ),
            relations = listOf(
                io.github.thelony97.orbit.data.Relation("r2", "kathi", "anna", "friend"),
            ),
            // Zuordnungen (R4): Hausarzt and work, Anna also at the JKU.
            orgLinks = listOf(
                io.github.thelony97.orbit.core.model.OrgLink("l1", "kathi", "arzt", "gp"),
                io.github.thelony97.orbit.core.model.OrgLink("l2", "kathi", "jku", "lead"),
                io.github.thelony97.orbit.core.model.OrgLink("l3", "anna", "jku", "colleague"),
            ),
        )
        return full
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

    /** Anlass bearbeiten (PLAN 3.5): a wedding day shared with Tom, pinned, two reminders. */
    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    @Test fun occasionDialog() {
        val wedding = io.github.thelony97.orbit.data.Anniversary(
            "w", io.github.thelony97.orbit.core.model.OccasionKind.WEDDING, date = DayOfYear(16, 10, 2021),
            pinned = true, reminders = listOf(0, 7), sharedWith = "tom", note = "Restaurant reservieren",
        )
        compose.setContent {
            OrbitTheme {
                Box(Modifier.fillMaxSize().background(Space.Void)) {
                    io.github.thelony97.orbit.ui.detail.OccasionDialog(
                        initial = wedding, people = data.contacts.filter { !it.isMe && !it.isOrg }, allowBirthday = false,
                        lastFirst = false, onDismiss = {}, onSave = {},
                    )
                }
            }
        }
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/screen_occasion_dialog.png")
    }

    /** Geburtstag bearbeiten mit Gratulations-Vorlage (Sie, English) und dem Text, den sie schickt. */
    @Test fun birthdayDialog() {
        val person = data.contacts.first { !it.isMe && !it.isOrg }.copy(greeting = io.github.thelony97.orbit.core.model.Greeting(formal = true, language = "en"))
        val birthday = io.github.thelony97.orbit.data.Anniversary(
            "b", io.github.thelony97.orbit.core.model.OccasionKind.BIRTHDAY, date = DayOfYear(14, 10, 1996),
            reminders = io.github.thelony97.orbit.data.Anniversary.BIRTHDAY_REMINDERS,
        )
        compose.setContent {
            OrbitTheme {
                Box(Modifier.fillMaxSize().background(Space.Void)) {
                    io.github.thelony97.orbit.ui.detail.OccasionDialog(
                        initial = birthday, people = emptyList(), allowBirthday = true,
                        lastFirst = false, onDismiss = {}, greetingFor = person, onSave = {},
                    )
                }
            }
        }
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/screen_birthday_dialog.png")
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

    @Test fun callDialing() = shot("call_dialing") {
        io.github.thelony97.orbit.ui.call.CallScreen(io.github.thelony97.orbit.ui.call.CallUi("+43 660 1234567", callCard, active = true, dialing = true, phoneUnlocked = true), noActions, now)
    }

    @Test fun callPickSim() = shot("call_sim") {
        io.github.thelony97.orbit.ui.call.CallScreen(
            io.github.thelony97.orbit.ui.call.CallUi("+43 660 1234567", callCard, active = true, dialing = true, phoneUnlocked = true, accounts = listOf("A1", "Magenta")),
            noActions, now,
        )
    }

    @Test fun callSms() = shot("call_sms") {
        io.github.thelony97.orbit.ui.call.CallScreen(io.github.thelony97.orbit.ui.call.CallUi("+43 660 1234567", callCard), noActions, now, smsOpen = true)
    }

    @Test fun callEnded() = shot("call_ended") {
        io.github.thelony97.orbit.ui.call.CallScreen(
            io.github.thelony97.orbit.ui.call.CallUi("+43 660 1234567", callCard, active = true, ended = true, elapsed = 754, phoneUnlocked = true, doneTopics = setOf("Wohnung in Wien?")),
            noActions, now,
        )
    }

    @Test fun callArchived() = shot("call_archived") {
        io.github.thelony97.orbit.ui.call.CallScreen(io.github.thelony97.orbit.ui.call.CallUi("+43 660 1234567", callCard.copy(archived = true), phoneUnlocked = true), noActions, now)
    }

    @Test fun dialpad() = shot("dialpad") {
        // „73“: Peter by name (Pe…), Arzt Müller by number (0732 …).
        io.github.thelony97.orbit.ui.call.Dialpad(data, idx, onCall = {}, onClose = {}, initial = "73")
    }

    @Test fun dialpadEmpty() = shot("dialpad_empty") {
        io.github.thelony97.orbit.ui.call.Dialpad(data, idx, onCall = {}, onClose = {})
    }

    @Test fun callUnknown() = shot("call_unknown") {
        io.github.thelony97.orbit.ui.call.CallScreen(io.github.thelony97.orbit.ui.call.CallUi("+43 732 555 123", null, unknownLine = "3 Anrufe · zuletzt vor 2 Tagen"), noActions, now)
    }

    @Test fun birthdays() = shot("birthdays") {
        io.github.thelony97.orbit.ui.occasions.BirthdaysScreen(data, idx, nav)
    }

    @Test fun occasionsAll() = shot("anlaesse_alle") {
        io.github.thelony97.orbit.ui.occasions.BirthdaysScreen(data, idx, nav, initialReiter = 2)
    }

    /** Filter & Sortierung der Anlässe (PLAN 3.5): Art, Zeitraum, nur runde, Gruppe, Organisationen. */
    @Test fun occasionFilter() {
        compose.setContent {
            OrbitTheme {
                Box(Modifier.fillMaxSize().background(Space.Void)) {
                    io.github.thelony97.orbit.ui.occasions.OccasionFilterSheet(
                        io.github.thelony97.orbit.ui.occasions.OccasionView(
                            kinds = setOf(io.github.thelony97.orbit.core.model.OccasionKind.WEDDING), withinDays = 30, onlyRound = true,
                            sort = io.github.thelony97.orbit.core.domain.OccasionList.Sort.CALENDAR,
                        ),
                        data.groups, kinds = io.github.thelony97.orbit.core.model.OccasionKind.entries.take(3), hasOrgs = true, onChange = {}, onDismiss = {},
                    )
                }
            }
        }
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/screen_anlaesse_filter.png")
    }

    @Test fun homeOccasionsTab() = shot("home_anlaesse") {
        HomeScreen(data, idx, nav, tab = io.github.thelony97.orbit.ui.home.HomeTab.ANLAESSE, onTab = {})
    }

    @Test fun callWaiting() = shot("call_two") {
        io.github.thelony97.orbit.ui.call.CallScreen(
            io.github.thelony97.orbit.ui.call.CallUi("+43 660 1234567", callCard, active = true, elapsed = 312, phoneUnlocked = true, otherCall = "Tom Huber · gehalten", canMerge = true),
            noActions, now,
        )
    }

    @Test fun callBluetoothAndBirthday() = shot("call_bluetooth") {
        io.github.thelony97.orbit.ui.call.CallScreen(
            io.github.thelony97.orbit.ui.call.CallUi(
                "+43 660 1234567",
                callCard.copy(birthday = io.github.thelony97.orbit.core.model.CalendarDay(today.plusDays(2).dayOfMonth, today.plusDays(2).monthValue, 1996)),
                active = true, elapsed = 65, phoneUnlocked = true,
                route = io.github.thelony97.orbit.ui.call.AudioRoute.BLUETOOTH, routeName = "Pixel Buds", moreRoutes = true,
            ),
            noActions, now,
        )
    }
}
