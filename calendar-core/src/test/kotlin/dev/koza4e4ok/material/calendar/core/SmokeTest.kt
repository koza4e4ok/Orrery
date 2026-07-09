package dev.koza4e4ok.material.calendar.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class SmokeTest {
    @Test
    fun `kotlinx datetime is on the classpath`() {
        val date = LocalDate(2026, 7, 9)
        assertEquals(9, date.day)
    }
}
