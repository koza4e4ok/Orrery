package me.kozakov.orrery.calendars.persian

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersianDateTest {
    @Test
    fun knownDates() {
        assertEquals(PersianDate(1405, 1, 1), LocalDate(2026, 3, 21).toPersianDate())
        assertEquals(PersianDate(1403, 1, 1), LocalDate(2024, 3, 20).toPersianDate())
        assertEquals(PersianDate(1403, 12, 30), LocalDate(2025, 3, 20).toPersianDate())
        assertEquals(PersianDate(1357, 11, 22), LocalDate(1979, 2, 11).toPersianDate())
        assertEquals(PersianDate(1300, 1, 1), LocalDate(1921, 3, 21).toPersianDate())
        assertEquals(LocalDate(2026, 3, 21), PersianDate(1405, 1, 1).toLocalDate())
    }

    @Test
    fun leapYearsAndMonthLengths() {
        assertTrue(PersianDate(1403, 1, 1).isLeapYear())
        assertFalse(PersianDate(1404, 1, 1).isLeapYear())
        assertTrue(PersianDate(1399, 1, 1).isLeapYear())
        assertEquals(31, PersianDate(1404, 6, 1).monthLength())
        assertEquals(30, PersianDate(1404, 7, 1).monthLength())
        assertEquals(29, PersianDate(1404, 12, 1).monthLength())
        assertEquals(30, PersianDate(1403, 12, 1).monthLength())
        assertFailsWith<IllegalArgumentException> { PersianDate(1404, 12, 30).toLocalDate() }
    }

    @Test
    fun supportedRange() {
        assertEquals(PersianDate(-61, 1, 1), LocalDate(560, 3, 20).toPersianDate())
        assertEquals(PersianDate(3177, 1, 1), LocalDate(3798, 3, 20).toPersianDate())
        assertFailsWith<IllegalArgumentException> { LocalDate(560, 3, 19).toPersianDate() }
        assertFailsWith<IllegalArgumentException> { LocalDate(3800, 1, 1).toPersianDate() }
        assertFailsWith<IllegalArgumentException> { PersianDate(3178, 1, 1) }
    }

    @Test
    fun dailyRoundTripAndContinuity2020To2029() {
        var date = LocalDate(2020, 1, 1)
        var previous: PersianDate? = null
        while (date.year < 2030) {
            val persian = date.toPersianDate()
            assertEquals(date, persian.toLocalDate(), "$date")
            if (previous != null && persian.day > 1) {
                assertEquals(previous.day + 1, persian.day, "$date")
            } else if (previous != null) {
                assertEquals(previous.day, previous.monthLength(), "$date")
            }
            previous = persian
            date = date.plus(1, DateTimeUnit.DAY)
        }
    }
}
