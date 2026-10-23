package me.kozakov.orrery.lunar

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * JVM-only exhaustive golden-fixture and java.time oracle checks.
 * Common tests cover portable boundary cases and invariants on all targets.
 */
class GoldenFixturesTest {
    @Test
    fun `lunar fixture has one row per day 1900-2099`() {
        val rows =
            GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/golden/lunar-daily.csv.gz")))
                .bufferedReader()
                .readLines()
        assertEquals(73049, rows.size)
        assertTrue(rows.first().startsWith("1900-01-01,"))
        assertTrue(rows.last().startsWith("2099-12-31,"))
    }

    @Test
    fun `solar terms fixture has 24 terms per year 1900-2099`() {
        val rows =
            checkNotNull(javaClass.getResourceAsStream("/golden/solar-terms.csv"))
                .bufferedReader()
                .readLines()
        assertEquals(200 * 24, rows.size)
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

    @Test
    fun `matches original SolarTermUtil for 1900-2099`() {
        val golden =
            checkNotNull(javaClass.getResourceAsStream("/golden/solar-terms.csv"))
                .bufferedReader()
                .readLines()
                .map { it.split(",") }
                .groupBy(
                    keySelector = { it[0].toInt() },
                    valueTransform = { LocalDate.parse(it[1]) to it[2].toInt() },
                )
        for (year in 1900..2099) {
            val actual = solarTermsFor(year).map { (date, term) -> date to term.ordinal }.toSet()
            assertEquals(checkNotNull(golden[year]).toSet(), actual, "year $year")
        }
    }

    @Test
    fun `nthWeekday matches java time across 1900-2099`() {
        for (year in 1900..2099) {
            val expected =
                java.time.LocalDate
                    .of(year, 11, 1)
                    .with(
                        java.time.temporal.TemporalAdjusters
                            .dayOfWeekInMonth(4, java.time.DayOfWeek.THURSDAY),
                    )
            val actual = nthWeekday(year, Month.NOVEMBER, DayOfWeek.THURSDAY, 4)
            assertEquals(expected.dayOfMonth, actual.day, "year $year")
        }
    }
}
