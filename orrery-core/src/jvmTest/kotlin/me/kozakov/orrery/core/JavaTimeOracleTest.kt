package me.kozakov.orrery.core

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaLocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Cross-checks core date math against java.time. JVM-only: the oracle is not
 * available on iOS or Wasm; the common suites cover those targets with fixed cases.
 */
class JavaTimeOracleTest {
    @Test
    fun `isoWeekNumber matches java time ISO oracle across 13 years`() {
        var date = LocalDate(2019, 1, 1)
        val end = LocalDate(2031, 12, 31)
        while (date <= end) {
            val oracle =
                date.toJavaLocalDate().get(
                    java.time.temporal.WeekFields.ISO
                        .weekOfWeekBasedYear(),
                )
            assertEquals(oracle, date.isoWeekNumber(), "at $date")
            date = date.plus(1, DateTimeUnit.DAY)
        }
    }

    @Test
    fun `YearMonth built-ins match java time oracle for every month 1900-2099`() {
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
