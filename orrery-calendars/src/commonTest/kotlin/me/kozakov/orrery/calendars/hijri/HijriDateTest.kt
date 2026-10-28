package me.kozakov.orrery.calendars.hijri

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HijriDateTest {
    @Test
    fun knownUmmAlQuraDates() {
        assertEquals(HijriDate(1447, 9, 1), LocalDate(2026, 2, 18).toHijriDate())
        assertEquals(HijriDate(1446, 1, 1), LocalDate(2024, 7, 7).toHijriDate())
        assertEquals(HijriDate(1445, 10, 1), LocalDate(2024, 4, 10).toHijriDate())
        assertEquals(HijriDate(1400, 1, 1), LocalDate(1979, 11, 21).toHijriDate())
        assertEquals(LocalDate(2026, 2, 18), HijriDate(1447, 9, 1).toLocalDate())
    }

    @Test
    fun ummAlQuraRangeEnds() {
        assertEquals(HijriDate(1300, 1, 1), LocalDate(1882, 11, 12).toHijriDate())
        assertEquals(HijriDate(1600, 12, 30), LocalDate(2174, 11, 25).toHijriDate())
        assertFailsWith<IllegalArgumentException> { LocalDate(1882, 11, 11).toHijriDate() }
        assertFailsWith<IllegalArgumentException> { LocalDate(2174, 11, 26).toHijriDate() }
        assertFailsWith<IllegalArgumentException> { HijriDate(1601, 1, 1).toLocalDate() }
        assertFailsWith<IllegalArgumentException> { HijriDate(1299, 12, 1).monthLength() }
    }

    @Test
    fun monthLengths() {
        assertEquals(30, HijriDate(1447, 9, 1).monthLength())
        assertEquals(29, HijriDate(1447, 2, 1).monthLength())
        assertFailsWith<IllegalArgumentException> { HijriDate(1447, 2, 30).toLocalDate() }
        assertFailsWith<IllegalArgumentException> { HijriDate(1447, 13, 1) }
        assertFailsWith<IllegalArgumentException> { HijriDate(1447, 1, 31) }
    }

    @Test
    fun tabularEpochAndLeapYears() {
        assertEquals(LocalDate(622, 7, 19), HijriDate(1, 1, 1, HijriVariant.Tabular).toLocalDate())
        assertEquals(HijriDate(1, 1, 1, HijriVariant.Tabular), LocalDate(622, 7, 19).toHijriDate(HijriVariant.Tabular))
        val leapYears = setOf(2, 5, 7, 10, 13, 16, 18, 21, 24, 26, 29)
        for (year in 1..60) {
            val expected = if ((year - 1) % 30 + 1 in leapYears) 30 else 29
            assertEquals(expected, HijriDate(year, 12, 1, HijriVariant.Tabular).monthLength(), "year $year")
        }
        // Unbounded: works far outside the Umm al-Qura table.
        val old = LocalDate(1500, 1, 1)
        assertEquals(old, old.toHijriDate(HijriVariant.Tabular).toLocalDate())
    }

    @Test
    fun dailyRoundTripAndContinuity2020To2029() {
        for (variant in HijriVariant.entries) {
            var date = LocalDate(2020, 1, 1)
            var previous: HijriDate? = null
            while (date.year < 2030) {
                val hijri = date.toHijriDate(variant)
                assertEquals(date, hijri.toLocalDate(), "$variant $date")
                if (previous != null && hijri.day > 1) {
                    assertEquals(previous.day + 1, hijri.day, "$variant $date")
                } else if (previous != null) {
                    assertEquals(previous.day, previous.monthLength(), "$variant $date")
                }
                previous = hijri
                date = date.plus(1, DateTimeUnit.DAY)
            }
        }
    }
}
