@file:OptIn(ExperimentalOrreryApi::class, InternalOrreryApi::class)

package me.kozakov.orrery.widget

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.ExperimentalOrreryApi
import me.kozakov.orrery.InternalOrreryApi
import me.kozakov.orrery.compose.internal.MonthGridModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SharedPrimitivesTest {
    @Test
    fun monthGridModelFromOrreryComposeIsUsableHere() {
        // 1 Feb 2026 is a Sunday: exactly four rows when weeks start on Sunday.
        val february = MonthGridModel(YearMonth(2026, 2), DayOfWeek.SUNDAY)
        assertEquals(4, february.rowCount)
        assertEquals(0, february.column(1))
        assertEquals(3, february.row(28))
        assertEquals(6, MonthGridModel(YearMonth(2026, 8), DayOfWeek.MONDAY).rowCount)
    }

    @Test
    fun rangesCompareByValue() {
        val november = YearMonth(2026, 11)
        val day = LocalDate(2026, 11, 14)
        assertEquals(CalendarSnapshotRange.Month(november), CalendarSnapshotRange.Month(november))
        assertEquals(CalendarSnapshotRange.Week(day), CalendarSnapshotRange.Week(day))
        val agenda = CalendarSnapshotRange.Agenda(LocalDate(2026, 11, 14))
        assertEquals(CalendarSnapshotRange.Agenda(LocalDate(2026, 11, 14), days = 7, maxRows = 4), agenda)
        assertEquals(agenda.hashCode(), CalendarSnapshotRange.Agenda(LocalDate(2026, 11, 14)).hashCode())
        assertNotEquals<CalendarSnapshotRange>(agenda, CalendarSnapshotRange.Agenda(LocalDate(2026, 11, 14), days = 3))
    }
}
