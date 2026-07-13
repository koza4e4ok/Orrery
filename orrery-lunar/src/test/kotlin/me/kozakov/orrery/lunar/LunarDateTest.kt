package me.kozakov.orrery.lunar

import kotlinx.datetime.LocalDate
import java.util.zip.GZIPInputStream
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
    fun `matches original LunarUtil for every day 1900-2099 and round-trips`() {
        val stream = checkNotNull(javaClass.getResourceAsStream("/golden/lunar-daily.csv.gz"))
        GZIPInputStream(stream).bufferedReader().useLines { lines ->
            var count = 0
            for (line in lines) {
                val (dateStr, y, m, d, leap) = line.split(",")
                val date = LocalDate.parse(dateStr)
                val expected = LunarDate(y.toInt(), m.toInt(), d.toInt(), leap == "1")
                assertEquals(expected, date.toLunarDate(), "toLunarDate at $date")
                assertEquals(date, expected.toLocalDate(), "round-trip at $date")
                count++
            }
            assertEquals(73049, count)
        }
    }
}
