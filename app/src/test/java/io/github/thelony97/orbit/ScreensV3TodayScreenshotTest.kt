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
import io.github.thelony97.orbit.core.domain.LiveMeeting
import io.github.thelony97.orbit.core.domain.TodayPlan
import io.github.thelony97.orbit.core.model.Meeting
import io.github.thelony97.orbit.core.model.OccasionKind
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.mail.MailAccounts
import io.github.thelony97.orbit.ui.Nav
import io.github.thelony97.orbit.ui.live.Live
import io.github.thelony97.orbit.ui.live.LiveIsland
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
import java.time.LocalDate

/** Version 3.0-C (PLAN 9.5): Heute and the Live-Insel with fixed data. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
@OptIn(ExperimentalRoborazziApi::class)
class ScreensV3TodayScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Before fun init() {
        io.github.thelony97.orbit.ui.Session.startPlayed = true
        val ctx = RuntimeEnvironment.getApplication()
        OrbitRepository.init(ctx)
        MailAccounts.init(ctx)
    }

    private val nav = Nav(open = {}, back = {}, replace = {}, home = {})
    private val now = System.currentTimeMillis()
    private val today = LocalDate.now()
    private val anna = Contact("anna", firstName = "Anna", lastName = "Huber")
    private val tom = Contact("tom", firstName = "Tom", lastName = "Berger")
    private val lena = Contact("lena", firstName = "Lena", lastName = "Meier")

    private fun moment(name: String, ms: Long, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { OrbitTheme { Box(Modifier.fillMaxSize().background(Space.Void)) { content() } } }
        compose.mainClock.advanceTimeBy(ms)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/screen_$name.png")
    }

    private val cards = listOf(
        TodayCard.Callbacks(2, listOf("Anna Huber", "Installateur Gruber"), warn = false),
        TodayCard.Persist(io.github.thelony97.orbit.call.Persist.Plan("p", "0732 771234", "Ordination Dr. Huber", null, now + 3_600_000L)),
        TodayCard.Plan(TodayPlan.Meet("meet:m", "tom", "m", now + 2 * 3_600_000L, "Café Central"), tom, color = 0xFF6FB7FF),
        TodayCard.Plan(TodayPlan.Occasion("occ:a", "anna", null, OccasionKind.BIRTHDAY, "", 30), anna),
        TodayCard.Plan(TodayPlan.Topic("topic:t", "lena", "t", "Wohnung in Wien?", today), lena),
        TodayCard.Plan(TodayPlan.YearsAgo("ago:e", "tom", "e", 3, "meet", "Konzert in Wien"), tom, kindName = "Treffen"),
    )

    /** C1: the row, every card kind, once each – after the staggered entry. */
    @Test fun todayRow() = moment("v3c_today", 900) {
        Column(Modifier.padding(top = 48.dp)) {
            TodayRow(cards.take(2), nav)
            TodayRow(cards.drop(2).take(2), nav, Modifier.padding(top = 16.dp))
            TodayRow(cards.drop(4), nav, Modifier.padding(top = 16.dp))
        }
    }

    /** C1 Moment: the cards gliding in (mid-way). */
    @Test fun todayEntering() = moment("v3c_today_enter", 120) {
        Column(Modifier.padding(top = 48.dp)) { TodayRow(cards.drop(2), nav) }
    }

    /** C2/C3: the island for a meeting soon, a running one, a call and Dranbleiben. */
    @Test fun island() = moment("v3c_island", 600) {
        Column(Modifier.padding(top = 48.dp)) {
            LiveIsland(Live.Meeting(LiveMeeting.Phase.Soon(Meeting("m", "anna", now + 12 * 60_000L, now + 72 * 60_000L), 12), anna), nav)
            LiveIsland(Live.Meeting(LiveMeeting.Phase.On(Meeting("n", "tom", now - 60_000L, now + 25 * 60_000L), 25), tom), nav, Modifier.padding(top = 16.dp))
            LiveIsland(Live.Call("Raiffeisen Linz", now - 95_000L), nav, Modifier.padding(top = 16.dp))
            LiveIsland(Live.Redial("Ordination Dr. Huber", 7, now + 9_000L), nav, Modifier.padding(top = 16.dp))
        }
    }
}
