package dev.koza4e4ok.orrery.core

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.monthsUntil
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the kotlinx-datetime YearMonth built-ins the library depends on.
 * A behavior change here on a version bump must be caught before it
 * silently shifts grid math.
 */
class YearMonthBuiltInsTest {
    @Test
    fun `plus months crosses year boundaries in both directions`() {
        assertEquals(YearMonth(2027, 1), YearMonth(2026, 12).plus(1, DateTimeUnit.MONTH))
        assertEquals(YearMonth(2025, 12), YearMonth(2026, 1).plus(-1, DateTimeUnit.MONTH))
        assertEquals(YearMonth(2028, 7), YearMonth(2026, 7).plus(24, DateTimeUnit.MONTH))
    }

    @Test
    fun `monthsUntil is signed and zero on self`() {
        assertEquals(1, YearMonth(2026, 12).monthsUntil(YearMonth(2027, 1)))
        assertEquals(-13, YearMonth(2027, 2).monthsUntil(YearMonth(2026, 1)))
        assertEquals(0, YearMonth(2026, 7).monthsUntil(YearMonth(2026, 7)))
    }

    @Test
    fun `firstDay lastDay numberOfDays handle leap years`() {
        assertEquals(LocalDate(2024, 2, 1), YearMonth(2024, 2).firstDay)
        assertEquals(LocalDate(2024, 2, 29), YearMonth(2024, 2).lastDay)
        assertEquals(29, YearMonth(2024, 2).numberOfDays)
        assertEquals(28, YearMonth(2100, 2).numberOfDays) // 2100 is not a leap year
        assertEquals(31, YearMonth(2026, 12).numberOfDays)
    }

    @Test
    fun `matches java time oracle for every month 1900-2099`() {
        for (year in 1900..2099) {
            for (month in 1..12) {
                val oracle = java.time.YearMonth.of(year, month)
                val ym = YearMonth(year, month)
                assertEquals(oracle.lengthOfMonth(), ym.numberOfDays, "numberOfDays $year-$month")
                assertEquals(oracle.atEndOfMonth().dayOfMonth, ym.lastDay.day, "lastDay $year-$month")
            }
        }
    }
}
