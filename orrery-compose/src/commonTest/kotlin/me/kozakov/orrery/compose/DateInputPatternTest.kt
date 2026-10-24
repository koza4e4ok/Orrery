package me.kozakov.orrery.compose

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DateInputPatternTest {
    @Test
    fun parsesLocaleOrderedPatterns() {
        assertEquals(LocalDate(2026, 7, 15), parseStrictDate("07/15/2026", "MM/dd/yyyy"))
        assertEquals(LocalDate(2026, 7, 15), parseStrictDate("15.07.2026", "dd.MM.yyyy"))
        assertEquals(LocalDate(2026, 7, 5), parseStrictDate("2026/07/05", "yyyy/MM/dd"))
    }

    @Test
    fun twoLetterFieldsNeedTwoDigits() {
        assertNull(parseStrictDate("7/15/2026", "MM/dd/yyyy"))
        assertNull(parseStrictDate("07/15/26", "MM/dd/yyyy"))
        assertEquals(LocalDate(2026, 7, 5), parseStrictDate("7/5/2026", "M/d/yyyy"))
    }

    @Test
    fun rejectsImpossibleDatesAndTrailingText() {
        assertNull(parseStrictDate("02/30/2026", "MM/dd/yyyy"))
        assertNull(parseStrictDate("02/29/2025", "MM/dd/yyyy"))
        assertNull(parseStrictDate("13/01/2026", "MM/dd/yyyy"))
        assertNull(parseStrictDate("07/15/2026x", "MM/dd/yyyy"))
        assertNull(parseStrictDate("07-15-2026", "MM/dd/yyyy"))
        assertNull(parseStrictDate("", "MM/dd/yyyy"))
        assertEquals(LocalDate(2024, 2, 29), parseStrictDate("02/29/2024", "MM/dd/yyyy"))
    }

    @Test
    fun quotedLiteralsAreMatchedVerbatim() {
        val pattern = "yyyy'年'MM'月'dd'日'"
        assertEquals(LocalDate(2026, 7, 15), parseStrictDate("2026年07月15日", pattern))
        assertEquals("2026年07月15日", formatInputDate(LocalDate(2026, 7, 15), pattern))
        assertEquals("o'clock 2026", formatInputDate(LocalDate(2026, 7, 15), "'o''clock' yyyy"))
    }

    @Test
    fun formatsWithZeroPadding() {
        assertEquals("07/05/2026", formatInputDate(LocalDate(2026, 7, 5), "MM/dd/yyyy"))
        assertEquals("7/5/2026", formatInputDate(LocalDate(2026, 7, 5), "M/d/yyyy"))
        assertEquals("05.07.0987", formatInputDate(LocalDate(987, 7, 5), "dd.MM.yyyy"))
    }

    @Test
    fun formatAndParseRoundTrip() {
        val date = LocalDate(1999, 12, 31)
        for (pattern in listOf("MM/dd/yyyy", "dd.MM.yyyy", "yyyy-MM-dd", "d/M/yyyy")) {
            assertEquals(date, parseStrictDate(formatInputDate(date, pattern), pattern), pattern)
        }
    }

    @Test
    fun normalizesShortPatternsToInputPatterns() {
        assertEquals("MM/dd/yyyy", normalizeInputPattern("M/d/yy"))
        assertEquals("dd.MM.yyyy", normalizeInputPattern("dd.MM.yy"))
        assertEquals("yyyy/MM/dd", normalizeInputPattern("y/MM/dd"))
        assertEquals("dd/MM/yyyy", normalizeInputPattern("dd/MM/y"))
    }
}
