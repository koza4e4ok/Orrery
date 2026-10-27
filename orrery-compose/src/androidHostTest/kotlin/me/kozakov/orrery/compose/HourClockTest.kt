package me.kozakov.orrery.compose

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
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

    @Test
    fun formatsTimeOfDayForTheLocale() {
        assertEquals("2:05 PM", time(14, 5, is24Hour = false, tag = "en-US"))
        assertEquals("12:00 AM", time(0, 0, is24Hour = false, tag = "en-US"))
        assertEquals("14:05", time(14, 5, is24Hour = true, tag = "en-US"))
        assertEquals("00:00", time(0, 0, is24Hour = true, tag = "de-DE"))
        val zh = time(14, 5, is24Hour = false, tag = "zh-CN")
        assertTrue(zh, "下午" in zh && "2:05" in zh && "PM" !in zh)
    }

    // Newer CLDR data puts a narrow no-break space before AM/PM.
    private fun time(
        hour: Int,
        minute: Int,
        is24Hour: Boolean,
        tag: String,
    ) = formatTime(hour, minute, is24Hour, tag).replace('\u202F', ' ').replace('\u00A0', ' ')
}
