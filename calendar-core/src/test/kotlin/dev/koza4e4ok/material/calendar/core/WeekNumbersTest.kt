package dev.koza4e4ok.material.calendar.core

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaLocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class WeekNumbersTest {
    @Test
    fun `known ISO week numbers`() {
        assertEquals(1, LocalDate(2026, 1, 1).isoWeekNumber()) // Thursday
        assertEquals(53, LocalDate(2021, 1, 1).isoWeekNumber()) // Friday -> week 53 of 2020
        assertEquals(28, LocalDate(2026, 7, 9).isoWeekNumber())
    }

    @Test
    fun `matches java time ISO oracle across 13 years`() {
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
}
