package me.kozakov.orrery.compose

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/** The JDK has no skeleton API, so Desktop derives the day header pattern from the LONG date pattern. */
class DayHeaderPatternTest {
    @Test
    fun yearAndItsSeparatorsAreDropped() {
        assertEquals("MMM d", abbreviatedMonthDayPattern("MMMM d, y"))
        assertEquals("d MMM", abbreviatedMonthDayPattern("d MMMM y 'р'."))
        assertEquals("d. MMM", abbreviatedMonthDayPattern("d. MMMM y"))
        assertEquals("M月d日", abbreviatedMonthDayPattern("y年M月d日"))
        assertEquals("d 'de' MMM", abbreviatedMonthDayPattern("d 'de' MMMM 'de' y"))
        assertEquals("MMM d", abbreviatedMonthDayPattern("MMM d"))
    }

    @Test
    fun quotedLiteralsAreKeptWhole() {
        assertEquals("d 'y''s' MMM", abbreviatedMonthDayPattern("d 'y''s' MMMM y"))
    }

    @Test
    fun localesGetTheirOwnOrder() {
        assertEquals("EEE, MMM d", localeDayHeaderPattern(Locale.US))
        assertEquals("EEE, d MMM", localeDayHeaderPattern(Locale.forLanguageTag("uk-UA")))
    }
}
