package me.kozakov.orrery.compose

import kotlinx.datetime.LocalDate
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/** JDK 17 has no skeleton API, so Desktop approximates the day header pattern from the LONG date pattern. */
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
    fun eraGoesWithTheYear() {
        assertEquals("d MMM", abbreviatedMonthDayPattern("d MMMM G y"))
        assertEquals("M月d日", abbreviatedMonthDayPattern("G y年M月d日"))
        assertEquals("MMM d", abbreviatedMonthDayPattern("MMMM d, y G"))
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

    /** Pinned to JDK 17's CLDR data, so a JDK data change shows up here. */
    @Test
    fun realJdkHeadersArePinned() {
        val date = LocalDate(2026, 11, 14)
        val expected =
            listOf(
                Triple("en-GB", "EEE, d MMM", "Sat, 14 Nov"),
                Triple("de", "EEE, d. MMM", "Sa., 14. Nov."),
                Triple("ja", "EEE, M月d日", "土, 11月14日"),
                Triple("ar", "EEE, d MMM", "السبت, 14 نوفمبر"),
                // LONG is "d MMMM G y"; the era used to leak as "ส., 14 พ.ย. ค.ศ.".
                Triple("th", "EEE, d MMM", "ส., 14 พ.ย."),
            )
        for ((tag, pattern, header) in expected) {
            assertEquals(pattern, localeDayHeaderPattern(Locale.forLanguageTag(tag)), tag)
            assertEquals(header, dayHeader(date, tag).replace('\u00A0', ' '), tag)
        }
    }
}
