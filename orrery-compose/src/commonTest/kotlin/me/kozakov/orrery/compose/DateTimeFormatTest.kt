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
    fun rewritesTimePatternsToTheRequestedClock() {
        assertEquals("h:mm a", timePatternForClock("h:mm a", is24Hour = false))
        assertEquals("HH:mm", timePatternForClock("HH:mm", is24Hour = true))
        assertEquals("HH:mm", timePatternForClock("h:mm a", is24Hour = true))
        assertEquals("HH:mm", timePatternForClock("ah:mm", is24Hour = true))
        assertEquals("h:mm a", timePatternForClock("HH:mm", is24Hour = false))
        assertEquals("h 'h' mm a", timePatternForClock("HH 'h' mm", is24Hour = false))
        assertEquals("HH 'Uhr' mm", timePatternForClock("h 'Uhr' mm a", is24Hour = true))
    }
}
