package me.kozakov.orrery.core

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VisibleDaysAndMultiWeekTest {
    private val workWeek =
        setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        )

    @Test
    fun `daysOfWeek filters to visibleDaysOfWeek in firstDayOfWeek order`() {
        assertEquals(
            listOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ),
            daysOfWeek(DayOfWeek.MONDAY, workWeek),
        )
        // Even with Sunday firstDayOfWeek, hidden Sunday/Saturday are skipped and Mon-Fri stay ordered
        assertEquals(
            listOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ),
            daysOfWeek(DayOfWeek.SUNDAY, workWeek),
        )
        // Wednesday start rotates the work week to Wed, Thu, Fri, Mon, Tue
        assertEquals(
            listOf(
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
            ),
            daysOfWeek(DayOfWeek.WEDNESDAY, workWeek),
        )
    }

    @Test
    fun `daysOfWeek rejects empty visibleDaysOfWeek`() {
        assertFailsWith<IllegalArgumentException> {
            daysOfWeek(DayOfWeek.MONDAY, emptySet())
        }
    }

    @Test
    fun `monthGrid work-week keeps only visible weekdays and drops empty month rows`() {
        // August 2026: Aug 1 is Saturday, Aug 31 is Monday.
        // In a 7-day Monday-start grid, row 0 is Mon Jul 27..Fri Jul 31 (InDate) + Sat Aug 1..Sun Aug 2 (MonthDate).
        // When filtered to Mon-Fri, row 0 has zero MonthDate days and must be dropped for EndOfRow/None.
        val aug2026 = YearMonth(2026, 8)
        val grid = monthGrid(aug2026, DayOfWeek.MONDAY, OutDateStyle.EndOfRow, workWeek)
        assertTrue(grid.weeks.all { it.days.size == 5 })
        assertTrue(grid.weeks.all { week -> week.days.any { it.position == DayPosition.MonthDate } })
        // First visible row starts on Mon Aug 3
        val firstRowDays = grid.weeks.first().days
        assertEquals(LocalDate(2026, 8, 3), firstRowDays.first().date)
        assertEquals(DayPosition.MonthDate, firstRowDays.first().position)
        // Last visible row starts on Mon Aug 31 and ends on Fri Sep 4 (OutDate)
        val lastRowDays = grid.weeks.last().days
        assertEquals(LocalDate(2026, 8, 31), lastRowDays.first().date)
        assertEquals(LocalDate(2026, 9, 4), lastRowDays.last().date)
        assertEquals(DayPosition.OutDate, lastRowDays.last().position)
        assertEquals(5, grid.weeks.size)
    }

    @Test
    fun `monthGrid work-week drops trailing row when only weekend MonthDates were in it`() {
        // May 2026: May 30 is Saturday, May 31 is Sunday.
        // With Sunday start: row 5 starts on Sun May 31 (MonthDate) + Mon Jun 1..Sat Jun 6 (OutDate).
        // Filtered to Mon-Fri, row 5 has only Mon Jun 1..Fri Jun 5 (all OutDate) -> dropped!
        val may2026 = YearMonth(2026, 5)
        val grid = monthGrid(may2026, DayOfWeek.SUNDAY, OutDateStyle.EndOfRow, workWeek)
        assertEquals(5, grid.weeks.size)
        assertTrue(grid.weeks.all { week -> week.days.any { it.position == DayPosition.MonthDate } })

        // EndOfGrid keeps all 6 rows even when filtered
        val fixedGrid = monthGrid(may2026, DayOfWeek.SUNDAY, OutDateStyle.EndOfGrid, workWeek)
        assertEquals(6, fixedGrid.weeks.size)
        assertTrue(fixedGrid.weeks.all { it.days.size == 5 })
    }

    @Test
    fun `weekGrid and multiWeekGrid support 2-week mode and visibleDaysOfWeek`() {
        val anchor = LocalDate(2026, 7, 9) // Thursday
        val singleWorkWeek = weekGrid(anchor, DayOfWeek.MONDAY, workWeek)
        assertEquals(5, singleWorkWeek.days.size)
        assertEquals(LocalDate(2026, 7, 6), singleWorkWeek.days.first().date)
        assertEquals(LocalDate(2026, 7, 10), singleWorkWeek.days.last().date)

        val twoWeeks = multiWeekGrid(anchor, weeksCount = 2, firstDayOfWeek = DayOfWeek.MONDAY)
        assertEquals(2, twoWeeks.size)
        assertEquals(7, twoWeeks[0].days.size)
        assertEquals(7, twoWeeks[1].days.size)
        assertEquals(LocalDate(2026, 7, 6), twoWeeks[0].days.first().date)
        assertEquals(LocalDate(2026, 7, 12), twoWeeks[0].days.last().date)
        assertEquals(LocalDate(2026, 7, 13), twoWeeks[1].days.first().date)
        assertEquals(LocalDate(2026, 7, 19), twoWeeks[1].days.last().date)

        val twoWorkWeeks =
            multiWeekGrid(
                anchor = anchor,
                weeksCount = 2,
                firstDayOfWeek = DayOfWeek.MONDAY,
                visibleDaysOfWeek = workWeek,
            )
        assertEquals(2, twoWorkWeeks.size)
        assertEquals(5, twoWorkWeeks[0].days.size)
        assertEquals(5, twoWorkWeeks[1].days.size)
        assertEquals(LocalDate(2026, 7, 6), twoWorkWeeks[0].days.first().date)
        assertEquals(LocalDate(2026, 7, 10), twoWorkWeeks[0].days.last().date)
        assertEquals(LocalDate(2026, 7, 13), twoWorkWeeks[1].days.first().date)
        assertEquals(LocalDate(2026, 7, 17), twoWorkWeeks[1].days.last().date)
    }

    @Test
    fun `multiWeekGrid validates weeksCount and visibleDaysOfWeek`() {
        assertFailsWith<IllegalArgumentException> {
            multiWeekGrid(LocalDate(2026, 7, 9), weeksCount = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            multiWeekGrid(LocalDate(2026, 7, 9), weeksCount = 5)
        }
        assertFailsWith<IllegalArgumentException> {
            multiWeekGrid(LocalDate(2026, 7, 9), weeksCount = 2, visibleDaysOfWeek = emptySet())
        }
    }

    @Test
    fun `CalendarPages multi-week helpers step by visibleWeeksCount`() {
        val start = LocalDate(2026, 7, 1) // Wednesday -> week starts Mon Jun 29
        val end = LocalDate(2026, 7, 31) // Friday -> week starts Mon Jul 27 (5 total weeks)
        assertEquals(5, CalendarPages.weekCount(start, end, DayOfWeek.MONDAY, visibleWeeksCount = 1))
        assertEquals(3, CalendarPages.weekCount(start, end, DayOfWeek.MONDAY, visibleWeeksCount = 2))
        assertEquals(0, CalendarPages.weekIndex(start, LocalDate(2026, 7, 9), DayOfWeek.MONDAY, visibleWeeksCount = 2))
        assertEquals(1, CalendarPages.weekIndex(start, LocalDate(2026, 7, 15), DayOfWeek.MONDAY, visibleWeeksCount = 2))
        assertEquals(2, CalendarPages.weekIndex(start, LocalDate(2026, 7, 29), DayOfWeek.MONDAY, visibleWeeksCount = 2))
        assertEquals(
            LocalDate(2026, 7, 13),
            CalendarPages.weekAt(start, index = 1, firstDayOfWeek = DayOfWeek.MONDAY, visibleWeeksCount = 2),
        )
    }
}
