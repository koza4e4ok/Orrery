package dev.koza4e4ok.material.calendar.core

import kotlinx.datetime.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals

class DaysOfWeekTest {
    @Test
    fun `starts at the given day and wraps over the week`() {
        assertEquals(
            listOf(
                DayOfWeek.SUNDAY,
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY,
            ),
            daysOfWeek(DayOfWeek.SUNDAY),
        )
        assertEquals(DayOfWeek.SATURDAY, daysOfWeek(DayOfWeek.SATURDAY).first())
        assertEquals(DayOfWeek.FRIDAY, daysOfWeek(DayOfWeek.SATURDAY).last())
        assertEquals(7, daysOfWeek().size)
    }
}
