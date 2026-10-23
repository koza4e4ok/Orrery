package me.kozakov.orrery.lunar

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LunarDateTest {
    @Test
    fun `known Chinese New Year dates`() {
        assertEquals(LunarDate(2024, 1, 1), LocalDate(2024, 2, 10).toLunarDate())
        assertEquals(LunarDate(2025, 1, 1), LocalDate(2025, 1, 29).toLunarDate())
        assertEquals(LunarDate(2026, 1, 1), LocalDate(2026, 2, 17).toLunarDate())
        assertEquals(LocalDate(2026, 2, 17), LunarDate(2026, 1, 1).toLocalDate())
    }

    @Test
    fun `2025 has a leap sixth month`() {
        val leapStart = LunarDate(2025, 6, 1, isLeapMonth = true).toLocalDate()
        assertEquals(LunarDate(2025, 6, 1, isLeapMonth = true), leapStart.toLunarDate())
        // the non-leap sixth month comes first and is a different date
        val normalStart = LunarDate(2025, 6, 1, isLeapMonth = false).toLocalDate()
        assertTrue(normalStart < leapStart)
    }

    @Test
    fun `boundary dates and every day 1900-2099 round-trip`() {
        assertEquals(LunarDate(1899, 12, 1), LocalDate(1900, 1, 1).toLunarDate())
        assertEquals(LunarDate(2099, 11, 20), LocalDate(2099, 12, 31).toLunarDate())

        var date = LocalDate(1900, 1, 1)
        val end = LocalDate(2099, 12, 31)
        var count = 0
        while (date <= end) {
            val lunar = date.toLunarDate()
            assertEquals(date, lunar.toLocalDate(), "round-trip at $date")
            date = date.plus(1, DateTimeUnit.DAY)
            count++
        }
        assertEquals(73049, count)
    }
}
