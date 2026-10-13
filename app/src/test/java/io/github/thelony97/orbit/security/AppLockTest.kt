package io.github.thelony97.orbit.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class AppLockTest {

    private fun leaveAndReturn(delaySeconds: Int, away: Duration) {
        AppLock.unlock()
        AppLock.onStop(changingConfigurations = false, lockEnabled = true, delaySeconds = delaySeconds)
        ShadowSystemClock.advanceBy(away)
        AppLock.onStart()
    }

    @Test fun locksAtOnceWithoutDelay() {
        AppLock.unlock()
        AppLock.onStop(changingConfigurations = false, lockEnabled = true, delaySeconds = 0)
        assertTrue(AppLock.locked.value)
    }

    @Test fun shortAbsenceStaysOpenWithDelay() {
        leaveAndReturn(60, Duration.ofSeconds(30))
        assertFalse(AppLock.locked.value)
    }

    @Test fun longAbsenceLocksWithDelay() {
        leaveAndReturn(60, Duration.ofSeconds(61))
        assertTrue(AppLock.locked.value)
        leaveAndReturn(300, Duration.ofMinutes(4))
        assertFalse(AppLock.locked.value)
        leaveAndReturn(300, Duration.ofMinutes(5))
        assertTrue(AppLock.locked.value)
    }

    @Test fun rotationAndOwnPickersNeverLock() {
        AppLock.unlock()
        AppLock.onStop(changingConfigurations = true, lockEnabled = true, delaySeconds = 0)
        assertFalse(AppLock.locked.value)
        AppLock.allowExternal()
        AppLock.onStop(changingConfigurations = false, lockEnabled = true, delaySeconds = 0)
        assertFalse(AppLock.locked.value)
        AppLock.onStart()
    }

    @Test fun ownPickerOrShareSheetLocksAfterALongTrip() {
        AppLock.unlock()
        AppLock.allowExternal()
        AppLock.onStop(changingConfigurations = false, lockEnabled = true, delaySeconds = 0)
        ShadowSystemClock.advanceBy(Duration.ofMinutes(6))
        AppLock.onStart()
        assertTrue(AppLock.locked.value)
    }

    @Test fun shareSheetCancelledThenHomeLocks() {
        // See-through share sheet: Orbit resumes without a stop, the next leave counts again.
        AppLock.unlock()
        AppLock.allowExternal()
        AppLock.onResume()
        AppLock.onStop(changingConfigurations = false, lockEnabled = true, delaySeconds = 0)
        assertTrue(AppLock.locked.value)
    }

    @Test fun disabledLockNeverLocks() {
        leaveAndReturn(0, Duration.ofMinutes(10))
        // delay 0 locks in onStop already; with the lock switched off nothing happens.
        AppLock.unlock()
        AppLock.onStop(changingConfigurations = false, lockEnabled = false, delaySeconds = 0)
        ShadowSystemClock.advanceBy(Duration.ofMinutes(10))
        AppLock.onStart()
        assertFalse(AppLock.locked.value)
    }
}
