package dev.koza4e4ok.orrery.lunar

import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
}
