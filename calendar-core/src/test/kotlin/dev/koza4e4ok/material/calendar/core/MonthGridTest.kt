package dev.koza4e4ok.material.calendar.core

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MonthGridTest {
    // July 2026: 1st is a Wednesday, 31 days.
    private val july2026 = YearMonth(2026, 7)

    @Test
    fun `known month with Monday week start`() {
        val grid = monthGrid(july2026, DayOfWeek.MONDAY)
        assertEquals(5, grid.weeks.size) // offset 2 + 31 days = 33 -> 5 rows
        val firstWeek = grid.weeks.first().days
        assertEquals(LocalDate(2026, 6, 29), firstWeek[0].date)
        assertEquals(DayPosition.InDate, firstWeek[0].position)
        assertEquals(DayPosition.InDate, firstWeek[1].position)
        assertEquals(LocalDate(2026, 7, 1), firstWeek[2].date)
        assertEquals(DayPosition.MonthDate, firstWeek[2].position)
        val lastWeek = grid.weeks.last().days
        assertEquals(LocalDate(2026, 8, 2), lastWeek[6].date)
        assertEquals(DayPosition.OutDate, lastWeek[6].position)
    }

    @Test
    fun `week start variants shift the offset`() {
        // Sunday start: July 1 2026 (Wed) has offset 3 -> (3+31+6)/7 = 5 rows
        assertEquals(5, monthGrid(july2026, DayOfWeek.SUNDAY).weeks.size)
        assertEquals(
            LocalDate(2026, 6, 28),
            monthGrid(july2026, DayOfWeek.SUNDAY)
                .weeks
                .first()
                .days[0]
                .date,
        )
        // Saturday start: offset 4 -> 5 rows, grid starts Sat Jun 27
        assertEquals(
            LocalDate(2026, 6, 27),
            monthGrid(july2026, DayOfWeek.SATURDAY)
                .weeks
                .first()
                .days[0]
                .date,
        )
    }

    @Test
    fun `EndOfGrid always yields 6 rows`() {
        // Feb 2027: 28 days, Feb 1 is a Monday -> minimal grid is exactly 4 rows
        val feb2027 = YearMonth(2027, 2)
        assertEquals(4, monthGrid(feb2027, DayOfWeek.MONDAY, OutDateStyle.EndOfRow).weeks.size)
        val fixed = monthGrid(feb2027, DayOfWeek.MONDAY, OutDateStyle.EndOfGrid)
        assertEquals(6, fixed.weeks.size)
        // Rows 5-6 are all out-dates continuing consecutively into March
        assertEquals(LocalDate(2027, 3, 1), fixed.weeks[4].days[0].date)
        assertTrue(fixed.weeks[4].days.all { it.position == DayPosition.OutDate })
    }

    @Test
    fun `grid invariants hold for every month 1900-2099 and each week start`() {
        for (year in 1900..2099) {
            for (month in 1..12) {
                for (fdow in listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.SATURDAY)) {
                    val ym = YearMonth(year, month)
                    val grid = monthGrid(ym, fdow)
                    val days = grid.weeks.flatMap { it.days }
                    // every row has 7 cells
                    assertTrue(grid.weeks.all { it.days.size == 7 })
                    // dates are consecutive
                    days.zipWithNext().forEach { (a, b) ->
                        assertEquals(a.date.toEpochDays() + 1, b.date.toEpochDays())
                    }
                    // first cell is on the configured first day of week
                    assertEquals(fdow, days.first().date.dayOfWeek)
                    // MonthDate count equals the real month length (java.time oracle)
                    val oracle =
                        java.time.YearMonth
                            .of(year, month)
                            .lengthOfMonth()
                    assertEquals(oracle, days.count { it.position == DayPosition.MonthDate })
                    // positions are ordered: InDate* MonthDate* OutDate*
                    val positions = days.map { it.position }
                    assertEquals(positions.sorted(), positions)
                }
            }
        }
    }

    @Test
    fun `weekGrid returns the week containing the anchor`() {
        val week = weekGrid(LocalDate(2026, 7, 9), DayOfWeek.MONDAY) // Thursday
        assertEquals(7, week.days.size)
        assertEquals(LocalDate(2026, 7, 6), week.days.first().date)
        assertEquals(LocalDate(2026, 7, 12), week.days.last().date)
        assertTrue(week.days.all { it.position == DayPosition.MonthDate })
    }
}
