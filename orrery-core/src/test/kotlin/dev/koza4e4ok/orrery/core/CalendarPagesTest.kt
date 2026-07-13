package dev.koza4e4ok.orrery.core

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

class CalendarPagesTest {
    private val start = YearMonth(1971, 1)

    @Test
    fun `month index matches the original pager formula`() {
        // original: 12 * (year - minYear) + month - minYearMonth
        assertEquals(0, CalendarPages.monthIndex(start, YearMonth(1971, 1)))
        assertEquals(666, CalendarPages.monthIndex(start, YearMonth(2026, 7)))
        assertEquals(YearMonth(2026, 7), CalendarPages.monthAt(start, 666))
        assertEquals(12 * (2055 - 1971) + 12, CalendarPages.monthCount(start, YearMonth(2055, 12)))
    }

    @Test
    fun `monthAt is inverse of monthIndex across the whole default range`() {
        for (i in 0 until CalendarPages.monthCount(start, YearMonth(2055, 12))) {
            assertEquals(i, CalendarPages.monthIndex(start, CalendarPages.monthAt(start, i)))
        }
    }

    @Test
    fun `weekStart snaps to the configured first day of week`() {
        val thursday = LocalDate(2026, 7, 9)
        assertEquals(LocalDate(2026, 7, 6), CalendarPages.weekStart(thursday, DayOfWeek.MONDAY))
        assertEquals(LocalDate(2026, 7, 5), CalendarPages.weekStart(thursday, DayOfWeek.SUNDAY))
        assertEquals(LocalDate(2026, 7, 4), CalendarPages.weekStart(thursday, DayOfWeek.SATURDAY))
        // a date already on the week start maps to itself
        assertEquals(LocalDate(2026, 7, 6), CalendarPages.weekStart(LocalDate(2026, 7, 6), DayOfWeek.MONDAY))
    }

    @Test
    fun `week index buckets dates by visual row`() {
        val rangeStart = LocalDate(2026, 7, 1) // Wednesday
        // Same visual week (Mon Jun 29 - Sun Jul 5) -> index 0
        assertEquals(0, CalendarPages.weekIndex(rangeStart, LocalDate(2026, 7, 5), DayOfWeek.MONDAY))
        // Mon Jul 6 starts the next row -> index 1
        assertEquals(1, CalendarPages.weekIndex(rangeStart, LocalDate(2026, 7, 6), DayOfWeek.MONDAY))
        assertEquals(1, CalendarPages.weekIndex(rangeStart, LocalDate(2026, 7, 9), DayOfWeek.MONDAY))
    }

    @Test
    fun `week count is inclusive of both endpoint weeks`() {
        // Jul 1 2026 (Wed) .. Jul 31 2026 (Fri), Monday start:
        // rows: Jun29, Jul6, Jul13, Jul20, Jul27 -> 5 weeks
        assertEquals(
            5,
            CalendarPages.weekCount(LocalDate(2026, 7, 1), LocalDate(2026, 7, 31), DayOfWeek.MONDAY),
        )
        // single-day range -> 1 week
        assertEquals(
            1,
            CalendarPages.weekCount(LocalDate(2026, 7, 9), LocalDate(2026, 7, 9), DayOfWeek.MONDAY),
        )
    }
}
