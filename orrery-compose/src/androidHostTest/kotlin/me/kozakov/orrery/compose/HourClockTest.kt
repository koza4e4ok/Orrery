package me.kozakov.orrery.compose

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class HourClockTest {
    @Test
    fun hourClockFollowsTheLocale() {
        assertFalse(is24HourClock("en-US"))
        assertTrue(is24HourClock("de-DE"))
        assertFalse(localeUses24HourClock(Locale.US))
        assertTrue(localeUses24HourClock(Locale.GERMANY))
    }
}
