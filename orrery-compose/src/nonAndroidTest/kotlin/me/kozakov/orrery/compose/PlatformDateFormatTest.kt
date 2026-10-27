package me.kozakov.orrery.compose

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Platform formatter contract on Desktop, iOS and Web, independent of the host locale. */
class PlatformDateFormatTest {
    @Test
    fun monthNamesAreDistinctAndNonBlank() {
        val full = Month.entries.map { PlatformDateFormat.monthName(it, short = false) }
        val short = Month.entries.map { PlatformDateFormat.monthName(it, short = true) }
        assertTrue(full.none { it.isBlank() } && short.none { it.isBlank() }, "$full / $short")
        assertEquals(12, full.toSet().size, full.toString())
        assertEquals(12, short.toSet().size, short.toString())
    }

    @Test
    fun weekdayNamesAreDistinctAndNonBlank() {
        val short = DayOfWeek.entries.map { PlatformDateFormat.weekdayName(it, narrow = false) }
        val narrow = DayOfWeek.entries.map { PlatformDateFormat.weekdayName(it, narrow = true) }
        assertTrue(short.none { it.isBlank() } && narrow.none { it.isBlank() }, "$short / $narrow")
        assertEquals(7, short.toSet().size, short.toString())
    }

    @Test
    fun firstDayOfWeekIsSundayMondayOrSaturday() {
        val first = PlatformDateFormat.firstDayOfWeek()
        assertTrue(first in setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.SATURDAY), first.toString())
    }

    @Test
    fun datesIncludeTheYear() {
        val date = LocalDate(2026, 7, 15)
        for (style in DateFormattingStyle.entries) {
            val text = PlatformDateFormat.formatDate(date, style)
            assertTrue("2026" in text, "$style: $text")
        }
        val title = PlatformDateFormat.yearMonthTitle(YearMonth(2026, Month.JULY))
        assertTrue("2026" in title, title)
        val header = PlatformDateFormat.dayHeader(date)
        assertTrue(header.isNotBlank() && "2026" !in header, header)
    }

    @Test
    fun earlyYearsAreNotShiftedIntoTheTwentiethCentury() {
        val text = PlatformDateFormat.formatDate(LocalDate(42, 3, 1), DateFormattingStyle.Long)
        assertTrue("42" in text && "1942" !in text, text)
    }

    @Test
    fun inputPatternRoundTrips() {
        val pattern = PlatformDateFormat.inputDatePattern()
        for (field in listOf("yyyy", "MM", "dd")) {
            assertTrue(field in pattern, "$field in $pattern")
        }
        for (date in listOf(LocalDate(2026, 11, 22), LocalDate(1999, 1, 5))) {
            assertEquals(date, parseStrictDate(formatInputDate(date, pattern), pattern), pattern)
        }
    }

    @Test
    fun hourClockFollowsTheLocale() {
        assertEquals(false, is24HourClock("en-US"))
        assertEquals(true, is24HourClock("de-DE"))
        // The current locale resolves without throwing; its value depends on the host.
        PlatformDateFormat.is24HourClock()
    }

    @Test
    fun uppercasesLatinText() {
        assertEquals("TODAY", PlatformDateFormat.uppercase("today"))
    }

    @Test
    fun formatsBuiltInTemplates() {
        assertEquals("Date must match MM/dd/yyyy", "Date must match %s".formatTemplate("MM/dd/yyyy"))
        assertEquals("1990s", "%ds".formatTemplate(1990))
        assertEquals("Jul 1, level 2 of 4", "%1\$s, level %2\$d of %3\$d".formatTemplate("Jul 1", 2, 4))
        assertEquals("4 of 2", "%2\$d of %1\$d".formatTemplate(2, 4))
    }

    @Test
    fun currentYearMonthMatchesCurrentDate() {
        val today = currentDate()
        assertTrue(today.year >= 2026, today.toString())
        assertEquals(YearMonth(today.year, today.month), currentYearMonth())
    }
}
