package me.kozakov.orrery.compose

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DateTimeFormatTest {
    @Test
    fun detects24HourPatterns() {
        assertTrue(patternUses24HourClock("HH:mm"))
        assertTrue(patternUses24HourClock("k:mm"))
        assertFalse(patternUses24HourClock("h:mm a"))
        assertFalse(patternUses24HourClock("a h:mm"))
        assertFalse(patternUses24HourClock("h 'Uhr' a"))
        assertTrue(patternUses24HourClock("H 'h' mm"))
    }

    @Test
    fun formatsHeadlineTimes() {
        assertEquals("14:05", formatTimeOfDay(14, 5, is24Hour = true))
        assertEquals("00:00", formatTimeOfDay(0, 0, is24Hour = true))
        assertEquals("2:05 PM", formatTimeOfDay(14, 5, is24Hour = false))
        assertEquals("12:00 AM", formatTimeOfDay(0, 0, is24Hour = false))
        assertEquals("12:30 PM", formatTimeOfDay(12, 30, is24Hour = false))
    }
}
