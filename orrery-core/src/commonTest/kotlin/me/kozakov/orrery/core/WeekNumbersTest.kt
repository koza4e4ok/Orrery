package me.kozakov.orrery.core

import kotlinx.datetime.LocalDate
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
    fun `ISO week-year boundaries`() {
        assertEquals(1, LocalDate(2024, 12, 30).isoWeekNumber()) // Monday -> week 1 of 2025
        assertEquals(53, LocalDate(2026, 12, 31).isoWeekNumber()) // Thursday, 2026 has 53 weeks
        assertEquals(53, LocalDate(2027, 1, 3).isoWeekNumber()) // Sunday -> still week 53 of 2026
        assertEquals(1, LocalDate(2027, 1, 4).isoWeekNumber()) // Monday
    }
}
