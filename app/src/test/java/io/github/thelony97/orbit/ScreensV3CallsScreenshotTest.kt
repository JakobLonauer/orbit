package io.github.thelony97.orbit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.thelony97.orbit.call.Callbacks
import io.github.thelony97.orbit.call.Persist
import io.github.thelony97.orbit.call.Silenced
import io.github.thelony97.orbit.core.domain.CallbackStack
import io.github.thelony97.orbit.core.domain.CallerCard
import io.github.thelony97.orbit.core.domain.FamilyMember
import io.github.thelony97.orbit.core.domain.NumberInfo
import io.github.thelony97.orbit.core.model.CalendarDay
import io.github.thelony97.orbit.core.model.CustomField
import io.github.thelony97.orbit.core.model.Macro
import io.github.thelony97.orbit.core.model.MacroStep
import io.github.thelony97.orbit.core.model.MacroStepKind
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.ContactKind
import io.github.thelony97.orbit.data.Defaults
import io.github.thelony97.orbit.data.Entry
import io.github.thelony97.orbit.data.EntrySource
import io.github.thelony97.orbit.data.LabeledValue
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.data.Settings
import io.github.thelony97.orbit.data.TypeIds
import io.github.thelony97.orbit.domain.Index
import io.github.thelony97.orbit.mail.MailAccounts
import io.github.thelony97.orbit.ui.Nav
import io.github.thelony97.orbit.ui.call.CallActions
import io.github.thelony97.orbit.ui.call.CallScreen
import io.github.thelony97.orbit.ui.call.CallUi
import io.github.thelony97.orbit.ui.call.MacroChip
import io.github.thelony97.orbit.ui.call.MacroPlaying
import io.github.thelony97.orbit.ui.call.PersistUi
import io.github.thelony97.orbit.ui.call.SimTag
import io.github.thelony97.orbit.ui.call.UnknownCaller
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

/** Version 3.0-B (PLAN 9.4): calls with autopilot, with fixed data – Wow-Gate material. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
@OptIn(ExperimentalRoborazziApi::class)
class ScreensV3CallsScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val ctx get() = RuntimeEnvironment.getApplication()

    @Before fun init() {
        io.github.thelony97.orbit.ui.Session.startPlayed = true
        OrbitRepository.init(ctx)
        MailAccounts.init(ctx)
    }

    private val now = System.currentTimeMillis()
    private val day = 86_400_000L
    private val today = LocalDate.now()
    private val noActions = object : CallActions {}
    private val nav = Nav(open = {}, back = {}, replace = {}, home = {})

    private val steps = listOf(
        MacroStep(MacroStepKind.KEY, "1"),
        MacroStep(MacroStepKind.PAUSE, ms = 4_000),
        MacroStep(MacroStepKind.KEY, "3"),
        MacroStep(MacroStepKind.FIELD, "b1"),
        MacroStep(MacroStepKind.KEY, "#"),
    )
    private val bank = Contact(
        "bank", kind = ContactKind.ORG, company = "Raiffeisen Linz",
        phones = listOf(LabeledValue("Service", "0732 6596")),
        fields = listOf(CustomField("b1", "Kundennummer", "RB-100 2834")),
        macros = listOf(Macro("m1", "Kundenservice", steps), Macro("m2", "Karte sperren", listOf(MacroStep(MacroStepKind.KEY, "2"), MacroStep(MacroStepKind.WAIT)))),
    )
    private val anna = Contact(
        "anna", firstName = "Anna", lastName = "Huber",
        phones = listOf(LabeledValue("Mobil", "+43 660 1234567"), LabeledValue("Arbeit", "0732 2468 12")),
        entries = listOf(
            Entry("e1", "anna", TypeIds.CALL, at = now - 2 * 3_600_000L, note = "Termin fix", durationSec = 340, incoming = true, source = EntrySource.CALL_LOG),
            Entry("e2", "anna", TypeIds.CALL, at = now - day - 3_600_000L, durationSec = 95, incoming = false, source = EntrySource.CALL_LOG),
        ),
    )
    private val data = OrbitData(
        contacts = listOf(Contact("me", isMe = true, firstName = "Jakob"), anna, bank),
        entryTypes = Defaults.entryTypes,
        settings = Settings(onboardingDone = true),
    )
    private val idx = Index(data)

    private val bankCard = CallerCard("bank", "Raiffeisen Linz", org = true, customerNo = "RB-100 2834")
    private val annaCard = CallerCard(
        "anna", "Anna Huber", role = "Lehrerin",
        anrede = "Sie · Frau Mag.a Huber",
        family = listOf(
            FamilyMember("Luki", "partner"),
            FamilyMember("Mia", "child", CalendarDay(today.plusDays(3).dayOfMonth, today.plusDays(3).monthValue, today.year - 7)),
            FamilyMember("Ben", "child", CalendarDay(2, 2, today.year - 4)),
        ),
    )

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent { OrbitTheme { Box(Modifier.fillMaxSize().background(Space.Void)) { content() } } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/screen_$name.png")
    }

    /** Moments with running animations: the clock stands still after [ms]. */
    private fun moment(name: String, ms: Long, screen: Boolean = false, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { OrbitTheme { Box(Modifier.fillMaxSize().background(Space.Void)) { content() } } }
        compose.mainClock.advanceTimeBy(ms)
        if (screen) captureScreenRoboImage("build/outputs/roborazzi/screen_$name.png")
        else compose.onRoot().captureRoboImage("build/outputs/roborazzi/screen_$name.png")
    }

    /** B1: unknown number – the card in final shape, Herold still being asked (scan light). */
    @Test fun unknownSearching() = moment("v3b_unknown_searching", 600) {
        CallScreen(CallUi("+43 732 555 123", null, unknown = UnknownCaller(line = "Linz · Festnetz", searching = true)), noActions, now)
    }

    /** B1: Herold knew it – name, trade and address sharpened in. */
    @Test fun unknownFound() = moment("v3b_unknown_found", 900) {
        CallScreen(
            CallUi(
                "+43 732 555 123", null, phoneUnlocked = true,
                unknown = UnknownCaller(line = "Linz · Festnetz", name = "Installateur Gruber GmbH", trade = "Installateur", address = "Hauptstraße 4, 4020 Linz"),
            ),
            noActions, now,
        )
    }

    /** B2: a premium number is orange from the first moment. */
    @Test fun unknownCosts() = moment("v3b_unknown_costs", 400) {
        CallScreen(CallUi("0900 123456", null, unknown = UnknownCaller(line = "0900 · teuer", costs = true)), noActions, now)
    }

    /** B1: after the call, Herold's ghost „Als Organisation anlegen“. */
    @Test fun afterHerold() = moment("v3b_after_herold", 600) {
        CallScreen(
            CallUi(
                "+43 732 555 123", null, active = true, ended = true, elapsed = 184, phoneUnlocked = true,
                unknown = UnknownCaller(line = "Linz · Festnetz", name = "Installateur Gruber GmbH", trade = "Installateur", address = "Hauptstraße 4, 4020 Linz"),
            ),
            noActions, now,
        )
    }

    /** B4/B7/B6: talking to the bank – macros, numbers to read out, Warteschleife. */
    @Test fun activeOrg() = moment("v3b_active_org", 400) {
        CallScreen(
            CallUi(
                "0732 6596", bankCard, active = true, elapsed = 48, phoneUnlocked = true,
                macros = listOf(MacroChip("m1", "Kundenservice"), MacroChip("m2", "Karte sperren")),
                dictation = listOf("Kundennummer" to "RB-100 2834", "IBAN" to "AT61 1904 3002 3457 3201"),
            ),
            noActions, now,
        )
    }

    /** B4 Moment: the keypad plays itself – the field's digit lit as a spark. */
    @Test fun macroPlaying() = moment("v3b_macro_key", 300) {
        CallScreen(
            CallUi("0732 6596", bankCard, active = true, elapsed = 21, phoneUnlocked = true, playing = MacroPlaying("Kundenservice", key = '2', fromField = true)),
            noActions, now,
        )
    }

    /** B4: a pause running as a segment bar. */
    @Test fun macroPause() = moment("v3b_macro_pause", 300) {
        CallScreen(
            CallUi("0732 6596", bankCard, active = true, elapsed = 19, phoneUnlocked = true, playing = MacroPlaying("Kundenservice", pauseMs = 4_000, pauseSince = System.currentTimeMillis() - 2_400)),
            noActions, now,
        )
    }

    /** B4: waiting for the hotline – „Weiter“ pulses. */
    @Test fun macroWait() = moment("v3b_macro_wait", 500) {
        CallScreen(CallUi("0732 6596", bankCard, active = true, elapsed = 35, phoneUnlocked = true, playing = MacroPlaying("Karte sperren", waiting = true)), noActions, now)
    }

    /** B4/B7 after the call: Vorgangsnummern as chips, the typed keys as a macro ghost. */
    @Test fun afterMacro() = moment("v3b_after_macro", 600) {
        CallScreen(
            CallUi(
                "0732 6596", bankCard, active = true, ended = true, elapsed = 412, phoneUnlocked = true,
                recorded = "1 · 4 s · 3 · Kundennummer · #", cases = listOf("4471209", "88123"),
            ),
            noActions, now,
        )
    }

    /** B7: Familie im Blick and Anrede while it rings. */
    @Test fun family() = moment("v3b_family", 400) {
        CallScreen(CallUi("+43 660 1234567", annaCard, phoneUnlocked = true), noActions, now)
    }

    /** B9: two SIMs – the ringing one's colour round the screen. */
    @Test fun simEdge() = moment("v3b_sim_edge", 400) {
        CallScreen(CallUi("+43 660 1234567", annaCard, phoneUnlocked = true, sim = SimTag("Arbeit", 0xFFFFB45E.toInt())), noActions, now)
    }

    /** B6: Warteschleife – dark, the waiting time big. */
    @Test fun queue() = moment("v3b_queue", 600) {
        CallScreen(CallUi("0732 6596", bankCard, active = true, elapsed = 760, phoneUnlocked = true, queueSince = System.currentTimeMillis() - 754_000), noActions, now)
    }

    /** B5: the card at the planned time, like an alarm. */
    @Test fun persistAlarm() = moment("v3b_persist_alarm", 500) {
        io.github.thelony97.orbit.ui.call.PersistAlarmCard("Ordination Dr. Huber", "0732 77 12 34", at = now, phoneApp = true, onCall = {}, onLater = {}, onCancel = {})
    }

    /** B5 Moment: between two tries, the ring counting down. */
    @Test fun persistPause() = moment("v3b_persist_pause", 300) {
        io.github.thelony97.orbit.ui.call.PersistPause(
            PersistUi("Ordination Dr. Huber", "0732 77 12 34", tries = 7, nextAt = System.currentTimeMillis() + 9_400, last = "Besetzt"),
            onNow = {}, onStop = {},
        )
    }

    /** B5: own call busy – one offer. */
    @Test fun busyOffer() = moment("v3b_busy_offer", 300) {
        io.github.thelony97.orbit.ui.call.BusyOffer("Ordination Dr. Huber", onPersist = {}, onClose = {})
    }

    /** B5: holding „Anrufen“ – numbers and Dranbleiben. */
    @Test fun callHold() = moment("v3b_call_hold", 800, screen = true) {
        io.github.thelony97.orbit.ui.detail.CallHoldSheet(anna, data, onDismiss = {}, onSetDefault = {})
    }

    /** B4/B5: the bank with its macros and a planned Dranbleiben. */
    @Test fun detailMacrosPersist() {
        Persist.plan(ctx, "0732 6596", "Raiffeisen Linz", "bank", now + 3_600_000L)
        shot("v3b_detail_bank") { io.github.thelony97.orbit.ui.detail.ContactDetailScreen("bank", data, idx, nav) }
    }

    /** B3: the Rückruf-Stapel – a contact, a Herold hit, a costly number. */
    @Test fun callbackStack() = moment("v3b_callbacks", 400) {
        val items = listOf(
            Callbacks.Item(
                CallbackStack.Card("cb:a", "+43 660 1234567", CallbackStack.Source.MISSED, 2, now - 40 * 60_000L),
                anna, "Anna Huber", NumberInfo.of("+43 660 1234567"), null, warn = false,
            ),
            Callbacks.Item(
                CallbackStack.Card("cb:b", "0732 555123", CallbackStack.Source.LATER, 1, now - 3 * 3_600_000L),
                null, "Installateur Gruber GmbH", NumberInfo.of("0732 555123"), null, warn = false,
            ),
            Callbacks.Item(
                CallbackStack.Card("cb:c", "+216 71 123 456", CallbackStack.Source.MISSED, 1, now - day),
                null, "+216 71 123 456", NumberInfo.of("+216 71 123 456"), null, warn = true,
            ),
        )
        io.github.thelony97.orbit.ui.callbacks.CallbackStackScreen(data, idx, nav, preview = items)
    }

    /** B8: the Verlauf with the filter's thin coral traces. */
    @Test fun verlaufSilenced() {
        Silenced.add(ctx, "+216 71 123 456", "Land")
        Silenced.add(ctx, "+216 71 123 456", "Land")
        Silenced.add(ctx, "0900 123456", "Mehrwert")
        compose.setContent { OrbitTheme { Box(Modifier.fillMaxSize().background(Space.Void)) { io.github.thelony97.orbit.ui.verlauf.VerlaufScreen(data, idx, nav) } } }
        compose.waitForIdle()
        Thread.sleep(400)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/screen_v3b_verlauf_silenced.png")
    }

    /** B2: dialling a premium number – orange line, the call button wants a second tap. */
    @Test fun dialpadCosts() = moment("v3b_dialpad_costs", 400) {
        io.github.thelony97.orbit.ui.call.Dialpad(data, idx, onCall = {}, onClose = {}, initial = "0900123456")
    }
}
