package dev.koza4e4ok.material.calendar.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

class AutoSelectTest {
    private val today = LocalDate(2026, 7, 9)

    @Test
    fun `FirstDayOfMonth selects the 1st, or today in the current month`() {
        assertEquals(
            LocalDate(2026, 8, 1),
            autoSelectDate(YearMonth(2026, 8), previous = today, AutoSelectDay.FirstDayOfMonth, today),
        )
        assertEquals(
            today,
            autoSelectDate(YearMonth(2026, 7), previous = null, AutoSelectDay.FirstDayOfMonth, today),
        )
    }

    @Test
    fun `LastSelectedDay keeps day-of-month, clamped to month length`() {
        val previous = LocalDate(2026, 8, 31)
        assertEquals(
            LocalDate(2026, 9, 30), // September has 30 days
            autoSelectDate(YearMonth(2026, 9), previous, AutoSelectDay.LastSelectedDay, today),
        )
        // current month wins over previous day-of-month
        assertEquals(
            today,
            autoSelectDate(YearMonth(2026, 7), previous, AutoSelectDay.LastSelectedDay, today),
        )
        // no previous selection -> the 1st
        assertEquals(
            LocalDate(2026, 9, 1),
            autoSelectDate(YearMonth(2026, 9), null, AutoSelectDay.LastSelectedDay, today),
        )
    }

    @Test
    fun `LastSelectedDayIgnoringCurrent never jumps to today`() {
        val previous = LocalDate(2026, 8, 25)
        assertEquals(
            LocalDate(2026, 7, 25),
            autoSelectDate(YearMonth(2026, 7), previous, AutoSelectDay.LastSelectedDayIgnoringCurrent, today),
        )
    }
}
