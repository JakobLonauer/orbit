package io.github.thelony97.orbit

import android.Manifest
import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import io.github.thelony97.orbit.actions.AppShortcuts
import io.github.thelony97.orbit.core.model.Contract
import io.github.thelony97.orbit.core.model.CustomField
import io.github.thelony97.orbit.core.model.Greeting
import io.github.thelony97.orbit.core.model.Link
import io.github.thelony97.orbit.core.model.Loan
import io.github.thelony97.orbit.core.model.Macro
import io.github.thelony97.orbit.core.model.MacroStep
import io.github.thelony97.orbit.core.model.MacroStepKind
import io.github.thelony97.orbit.core.model.MailRef
import io.github.thelony97.orbit.core.model.Member
import io.github.thelony97.orbit.core.model.OccasionKind
import io.github.thelony97.orbit.core.model.SmartGroup
import io.github.thelony97.orbit.core.model.WifiNet
import io.github.thelony97.orbit.data.Anniversary
import io.github.thelony97.orbit.data.CheckItem
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.ContactKind
import io.github.thelony97.orbit.data.DayOfYear
import io.github.thelony97.orbit.data.Defaults
import io.github.thelony97.orbit.data.Entry
import io.github.thelony97.orbit.data.EntrySource
import io.github.thelony97.orbit.data.GalleryPhoto
import io.github.thelony97.orbit.data.GeoPoint
import io.github.thelony97.orbit.data.Group
import io.github.thelony97.orbit.data.LabeledValue
import io.github.thelony97.orbit.data.Meeting
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.data.OrgLink
import io.github.thelony97.orbit.data.Relation
import io.github.thelony97.orbit.data.Settings
import io.github.thelony97.orbit.data.SmsMessage
import io.github.thelony97.orbit.data.TypeIds
import io.github.thelony97.orbit.links.RealPhotos
import io.github.thelony97.orbit.mail.MailAccounts
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.time.LocalDate

/**
 * The whole app as on the phone (15.10.: no contact page opened any more – the crash sat in a path
 * the screenshot tests never took). Real MainActivity and navigation, every permission granted,
 * the network for pictures faked on, a varied data set with odd values; every contact is opened and
 * scrolled to the end, edited and discarded; tabs, settings areas and shortcuts are walked.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AppSmokeTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var realFetch: (String) -> ByteArray?

    private val day = 86_400_000L
    private val now = System.currentTimeMillis()
    private val today = LocalDate.now()

    @Before fun start() {
        val app = RuntimeEnvironment.getApplication()
        // What Jakob allowed on the phone – Robolectric denies everything by default.
        shadowOf(app).grantPermissions(
            Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS, Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR,
            Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS, Manifest.permission.CALL_PHONE,
            Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        // WorkManager as an inert stub: requests are accepted, nothing runs.
        val nothing = java.util.concurrent.Executor { }
        runCatching { androidx.work.WorkManager.initialize(app, androidx.work.Configuration.Builder().setExecutor(nothing).setTaskExecutor(nothing).build()) }
        io.github.thelony97.orbit.ui.Session.startPlayed = true
        OrbitRepository.init(app)
        OrbitRepository.resetForTest(app)
        MailAccounts.init(app)
        runBlocking { OrbitRepository.replaceAll(data()) }
        OrbitRepository.photoFile("p-tom.jpg").outputStream().use { picture(300).compress(Bitmap.CompressFormat.JPEG, 90, it) }
        // Pictures from the net (E16) as on the phone: Gravatar knows nobody, websites have an icon.
        realFetch = RealPhotos.fetch
        RealPhotos.reset()
        RealPhotos.offline = false
        RealPhotos.fetch = { url -> if ("apple-touch-icon" in url) png(256) else null }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        settle()
    }

    @After fun stop() {
        runCatching { scenario.close() }
        RealPhotos.offline = true
        RealPhotos.fetch = realFetch
        RealPhotos.reset()
        MainActivity.pendingContact.value = null
        MainActivity.pendingShortcut.value = null
    }

    private fun picture(side: Int): Bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.rgb(90, 140, 220)) }

    private fun png(side: Int): ByteArray = ByteArrayOutputStream().also { picture(side).compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

    private fun inDays(n: Long) = today.plusDays(n).let { DayOfYear(it.dayOfMonth, it.monthValue, 1990) }

    private fun e(id: String, type: String, daysAgo: Double, note: String = "", source: EntrySource = EntrySource.MANUAL) =
        Entry(id = id, contactId = "", typeId = type, at = now - (daysAgo * day).toLong(), note = note, source = source)

    // ---- the data: every kind of contact, the 3.0 fields, odd and empty values ----

    private fun data(): OrbitData {
        val anna = Contact(
            "anna", firstName = "Anna", lastName = "Huber", nickname = "Anni", favorite = true, groupIds = listOf("fr", "uni"),
            phones = listOf(LabeledValue("Mobil", "+43 660 1234567"), LabeledValue("Alt", "abc")),
            emails = listOf(LabeledValue("Privat", "anna@example.at"), LabeledValue("", "kaputt")),
            addresses = listOf(LabeledValue("Privat", "Hauptplatz 1\n4020 Linz")),
            position = "Lehrerin", company = "BRG Linz", howWeMet = "Uni, Linguistik", greeting = Greeting(formal = true, language = "en"),
            notes = "Mag Jazz. Kennt @Tom von der Uni.", pinnedNote = "Lieber SMS als anrufen",
            topics = listOf(CheckItem("t1", "Wohnung in Wien?", createdAt = now - 3 * day), CheckItem("t2", "Konzert", remindOn = today.plusDays(2).toString()),
                CheckItem("t3", "Kaputtes Datum", remindOn = "2026-13-45"), CheckItem("t4", "Erledigt", done = true)),
            gifts = listOf(CheckItem("g1", "Buch „Der Hobbit“", priceCents = 2_490), CheckItem("g2", "Schal", bought = true, priceCents = 3_900), CheckItem("g3", "Kerze", done = true)),
            giftBudgetCents = 6_000,
            links = listOf(Link("l1", "Portfolio", "https://anna.example.at"), Link("l2", "", "kein link"), Link("l3", "Leer", "")),
            fields = listOf(CustomField("f1", "Kundennummer", "KD-48213"), CustomField("f2", "", "")),
            loans = listOf(Loan("lo1", "Pizza", amountCents = 1_250, toThem = true, since = today.minusDays(9).toString()), Loan("lo2", "Bohrmaschine", toThem = false, since = ""),
                Loan("lo3", "Buch", since = "kaputt", settledOn = today.toString())),
            wifi = listOf(WifiNet("w1", "Annas WLAN", "geheim123"), WifiNet("w2", "Gast", "", "nopass"), WifiNet("w3", "", "x", "WEP")),
            geo = GeoPoint(48.306, 14.286, "Hauptplatz 1, Linz"),
            dates = listOf(
                Anniversary("o1", OccasionKind.WEDDING, date = inDays(5)), Anniversary("o2", OccasionKind.CUSTOM, label = "Erster Job", date = DayOfYear(1, 9, null)),
                Anniversary("o3", OccasionKind.MET, date = inDays(-2), sharedWith = "tom"),
            ),
            entries = listOf(
                e("a1", TypeIds.CALL, 0.1, source = EntrySource.CALL_LOG).copy(durationSec = 754, incoming = true),
                e("a2", TypeIds.CALL, 0.5, source = EntrySource.CALL_LOG).copy(durationSec = 0, incoming = true),
                e("a3", TypeIds.MESSAGE, 1.0, source = EntrySource.SMS).copy(
                    messages = listOf(
                        SmsMessage(1, now - day, true, "Meine neue Nummer: 0664 9876543"),
                        SmsMessage(2, now - day + 60_000, true, "Ich wohne jetzt in der Landstraße 12, 4020 Linz – schreib an anna.neu@example.at"),
                        SmsMessage(3, now - day + 120_000, false, "Super 😀"),
                    ),
                ),
                e("a4", TypeIds.MEET, 6.0, "Kaffee am Hauptplatz mit @Tom").copy(withIds = listOf("tom"), stars = 4, amountCents = 3_450),
                e("a5", "note", 8.0, "Sprachnotiz").copy(audio = "voice-a5.m4a", wave = "0123456789zz"),
                e("a6", TypeIds.EMAIL, 9.0, source = EntrySource.MAIL).copy(
                    mails = listOf(MailRef("acc", "INBOX", 1, 7, now - 9 * day, true, "Hallo", "anna@example.at")),
                ),
                e("a7", "geloeschter-typ", 40.0, "Typ gibt es nicht mehr"),
                e("a8", TypeIds.EVENT, 400.0, "Hochzeit").copy(occasionId = "o1", occasionYear = 2025, attachmentIds = listOf("ph1", "fehlt")),
                e("a9", TypeIds.CALL, 800.0, source = EntrySource.CALL_LOG).copy(durationSec = 61, incoming = false),
            ),
            gallery = listOf(GalleryPhoto("ph1", "anna", "missing-1.jpg", addedAt = now - 400 * day, takenAt = now - 400 * day)),
        ).withBirthday(inDays(0)) { "bd-anna" }
        val tom = Contact(
            "tom", firstName = "Tom", lastName = "Berger", photo = "p-tom.jpg", groupIds = listOf("uni"),
            phones = listOf(LabeledValue("Mobil", "0676 5550123")),
            entries = listOf(e("t1", TypeIds.MEET, 21.0, "Lernen für Linguistik"), e("t1b", TypeIds.MESSAGE, 2.0, "Hast du @Anna gesehen?")),
        ).withBirthday(DayOfYear(29, 2, null)) { "bd-tom" }
        val jku = Contact(
            "jku", kind = ContactKind.ORG, orgCategory = "work", company = "JKU Linz", trade = "Universität",
            phones = listOf(LabeledValue("Zentrale", "0732 2468")), emailDomains = listOf("jku.at"),
            openingHours = "Mo-Fr 07:00-19:00; Sa 08:00-12:00", phoneHours = "immer?", vacationUntil = today.plusDays(4).toString(), vacationSubstitute = "anna",
            numberPrefixes = listOf("0732 2468"), mapsUrl = "https://maps.example/jku", dead = listOf("0732 0000"),
            links = listOf(Link("p1", "KUSSS", "https://kusss.jku.at", note = "k12345678", pinned = true), Link("p2", "Moodle", "moodle.jku.at")),
            members = listOf(Member("m1", "Herr Mayer", role = "Studienservice", phones = listOf(LabeledValue("Durchwahl", "0732 2468-1234"))), Member("m2", "")),
            fields = listOf(CustomField("jf1", "Matrikelnummer", "k12345678")),
            macros = listOf(Macro("ma1", "Studienservice", listOf(MacroStep(MacroStepKind.KEY, "1"), MacroStep(MacroStepKind.PAUSE, ms = 1_500),
                MacroStep(MacroStepKind.FIELD, "jf1"), MacroStep(MacroStepKind.WAIT), MacroStep(MacroStepKind.FIELD, "gibt-es-nicht")))),
            contracts = listOf(
                Contract("c1", "Studienbeitrag", "SB-1", start = "2025-01-15", minMonths = 24, renewMonths = 12, noticeMonths = 3, price = "363,36 €/Semester"),
                Contract("c2", "Gekündigt", start = "2024-03-01", cancelledOn = today.minusDays(30).toString()),
                Contract("c3", "Kaputt", start = "kaputt", minMonths = -3, noticeDays = 400),
                Contract("c4", "Ohne Beginn", start = null, renewMonths = 0),
            ),
            entries = listOf(Entry("j1", "jku", TypeIds.CALL, at = now - 3 * day, note = "Anrechnung geklärt", memberId = "m1")),
        )
        val bank = Contact("bank", kind = ContactKind.ORG, orgCategory = "health", company = "", emails = listOf(LabeledValue("", "service@bank.example")))
        val fox = Contact(
            "fox", firstName = "🦊", lastName = "Fuchs-Müller-Lüdenscheidt-Oberösterreich", title = "Mag.ª Dr.", salutation = "Liebe Frau Fuchs",
            notes = "Lang ".repeat(1_500), phones = listOf(LabeledValue("", "+49 30 1234567"), LabeledValue("", "")), hideNumber = true,
        )
        val nobody = Contact("nobody")
        val lea = Contact("lea", nickname = "Lea").withBirthday(DayOfYear(29, 2, null)) { "bd-lea" }
        val kevin = Contact("kevin", firstName = "Kevin", lastName = "Maier", archived = true, archivedAt = now - 30 * day, entries = listOf(e("k1", TypeIds.CALL, 500.0)))
        val gone = Contact("gone", firstName = "Weg", lastName = "Gelöscht", deletedAt = now - day)
        return OrbitData(
            contacts = listOf(Contact("me", isMe = true, firstName = "Jakob", lastName = "Lonauer"), anna, tom, jku, bank, fox, nobody, lea, kevin, gone),
            groups = listOf(Group("fr", "Freunde", Defaults.groupColors[5], pinned = true), Group("uni", "Uni", Defaults.groupColors[3])),
            entryTypes = Defaults.entryTypes,
            relations = listOf(Relation("r1", "anna", "tom", "friend"), Relation("r2", "tom", "gibt-es-nicht", "friend")),
            orgLinks = listOf(OrgLink("ol1", "anna", "jku", "colleague"), OrgLink("ol2", "tom", "jku", "custom", label = "Tutor")),
            meetings = listOf(Meeting("mt1", "anna", start = now - 600_000, end = now + 3_000_000, place = "Café Traxlmayr")),
            settings = Settings(
                onboardingDone = true, lockEnabled = false, autoUpdateCheck = false, showOrgs = true,
                smartGroups = listOf(SmartGroup("s1", "Uni-Geburtstage", "#uni hat:geburtstag")),
                eventName = "Hochzeit Tom", eventDay = today.toString(),
            ),
        )
    }

    // ---- helpers ----

    /** Lets the background work (pictures, files) land and the UI follow. */
    private fun settle(rounds: Int = 5) {
        repeat(rounds) {
            compose.waitForIdle()
            Thread.sleep(50)
        }
        compose.waitForIdle()
    }

    private fun waitFor(what: String, seconds: Int = 10, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + seconds * 1_000L
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "Nicht erreicht: $what" }
            compose.waitForIdle()
            Thread.sleep(50)
        }
    }

    private fun shown(description: String) = compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()

    private fun shownText(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun back() {
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    /** The page's big vertical list (not the chip rows inside it). */
    private fun page(): SemanticsNodeInteraction = compose.onAllNodes(
        SemanticsMatcher("page list") { it.config.contains(SemanticsActions.ScrollToIndex) && it.boundsInRoot.height > 1_200f },
    ).onFirst()

    /** Every item of the page once on screen (lazy items only compose there). */
    private fun scrollThrough() {
        if (compose.onAllNodes(SemanticsMatcher("page list") { it.config.contains(SemanticsActions.ScrollToIndex) && it.boundsInRoot.height > 1_200f })
                .fetchSemanticsNodes().isEmpty()
        ) return
        var i = 0
        while (i < 200) {
            try {
                page().performScrollToIndex(i)
            } catch (end: IllegalArgumentException) {
                break
            }
            settle(1)
            i += 2
        }
        page().performScrollToIndex(0)
        settle(2)
    }

    private fun open(id: String) {
        MainActivity.pendingContact.value = id
        waitFor("Seite von $id offen") { shown("Bearbeiten") }
        settle()
    }

    private fun home() {
        back()
        waitFor("zurück in der Liste") { !shown("Bearbeiten") && !shown("Abbrechen") }
        settle(2)
    }

    /** The edit page closes through „Abbrechen“ → „Verwerfen“. */
    private fun discardEdit() {
        waitFor("Bearbeiten-Seite offen") { shown("Abbrechen") }
        settle()
        compose.onAllNodesWithContentDescription("Abbrechen").onFirst().performClick()
        waitFor("Verwerfen-Frage") { shownText("Verwerfen") }
        compose.onAllNodesWithText("Verwerfen").onFirst().performClick()
        waitFor("Bearbeiten-Seite zu") { !shown("Abbrechen") }
        settle(2)
    }

    private val people = listOf("me", "anna", "tom", "jku", "bank", "fox", "nobody", "lea", "kevin")

    // ---- the walks ----

    @Test fun everyContactOpensAndScrolls() {
        // „gone“ lies in the trash – a notification may still point to it.
        (people + "gone").forEach { id ->
            open(id)
            scrollThrough()
            home()
        }
    }

    @Test fun everyContactEditsAndDiscards() {
        people.forEach { id ->
            open(id)
            compose.onAllNodesWithContentDescription("Bearbeiten").onFirst().performClick()
            discardEdit()
            waitFor("wieder auf der Seite von $id") { shown("Bearbeiten") }
            home()
        }
    }

    /** E16 end to end: the website's icon appears as a dashed planet; a tap makes it the photo. */
    @Test fun foundPictureBecomesThePhoto() {
        open("jku")
        waitFor("Bild der Webseite geladen") { OrbitRepository.photoFile("cand-jku.jpg").exists() }
        settle()
        page().performScrollToNode(hasText("Als Foto"))
        settle()
        compose.onAllNodesWithText("Als Foto").onFirst().performClick()
        waitFor("Bild übernommen") { OrbitRepository.data.value?.contacts?.firstOrNull { it.id == "jku" }?.photo != null }
        settle()
        assertNotNull(OrbitRepository.data.value?.contacts?.first { it.id == "jku" }?.photo)
    }

    /** The 3.0 parts of a page do what they promise: Erwähnt, Geister aus SMS, a note from the Faden. */
    @Test fun pagePartsWorkAsIntended() {
        open("tom")
        waitFor("„Erwähnt“ bei Tom") { runCatching { page().performScrollToNode(hasText("Erwähnt", substring = true)) }.isSuccess }
        home()
        open("anna")
        waitFor("Geist aus Annas SMS") { runCatching { page().performScrollToNode(hasText("0664 9876543")) }.isSuccess }
        page().performScrollToIndex(0)
        settle()
        // The orb switches SMS → WhatsApp → Mail → Notiz (the reply line is the last such orb on the page).
        listOf("SMS", "WhatsApp", "Mail").forEach { mode ->
            compose.onAllNodesWithContentDescription(mode).onLast().performClick()
            settle(2)
        }
        waitFor("Antwortzeile auf Notiz") { shown("Notiz") }
        compose.onAllNodes(hasSetTextAction()).onLast().performTextInput("Testnotiz aus dem Faden")
        settle(2)
        compose.onAllNodesWithContentDescription("Senden").onLast().performClick()
        waitFor("Notiz im Verlauf") {
            OrbitRepository.data.value?.contacts?.firstOrNull { it.id == "anna" }?.entries?.any { it.note == "Testnotiz aus dem Faden" } == true
        }
        settle()
    }

    @Test fun tabsSettingsAndShortcuts() {
        listOf("Verlauf", "Anlässe", "Kontakte").forEach { tab ->
            compose.onAllNodesWithContentDescription(tab).onFirst().performClick()
            settle()
            scrollThrough()
        }
        compose.onAllNodesWithContentDescription("Einstellungen").onFirst().performClick()
        waitFor("Einstellungen offen") { shown("Zurück") }
        settle()
        listOf("Mitschreiben", "Automatiken", "Benachrichtigungen", "Kontakte", "Sicherheit & Backup", "Über Orbit").forEach { area ->
            page().performScrollToNode(hasText(area))
            settle()
            compose.onAllNodesWithText(area).onFirst().performClick()
            settle()
            scrollThrough()
            back()
        }
        back()
        // App shortcuts / QS tile: the Rückruf-Stapel and a new contact.
        MainActivity.pendingShortcut.value = AppShortcuts.ACTION_CALLBACKS
        waitFor("Rückruf-Stapel offen") { shown("Zurück") }
        settle()
        back()
        waitFor("zurück in der Liste") { !shown("Zurück") }
        MainActivity.pendingShortcut.value = AppShortcuts.ACTION_NEW
        discardEdit()
    }
}
