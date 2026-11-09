package me.kozakov.orrery.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Compares the expansion engine with fixtures generated from python-dateutil by
 * tools/golden/rrule_fixtures.py (pseudo-random corpus of supported rules, each with a full and
 * a sub-window query). Intentional divergences from dateutil are listed with their source in
 * golden/rrule-exceptions.csv. JVM-only because the fixtures are test resources.
 */
class RecurrenceOracleTest {
    private fun lines(resource: String): List<String> =
        checkNotNull(javaClass.getResourceAsStream(resource)) { resource }
            .bufferedReader()
            .readLines()
            .filter { it.isNotBlank() }

    private fun date(value: String): LocalDate =
        LocalDate(value.substring(0, 4).toInt(), value.substring(4, 6).toInt(), value.substring(6).toInt())

    private fun dates(value: String): List<LocalDate> = value.split(' ').filter { it.isNotEmpty() }.map(::date)

    @Test
    fun `expansion matches the python-dateutil oracle`() {
        val fixtures = lines("/golden/rrule-fixtures.txt")
        assertTrue(fixtures.size >= 400, "only ${fixtures.size} fixtures")
        val failures = mutableListOf<String>()
        for (line in fixtures) {
            val (dtStart, rrule, from, to, expected) = line.split('|')
            val rule = RecurrenceRule.parse(rrule)
            val actual = rule.occurrences(date(dtStart), date(from)..date(to))
            if (actual != dates(expected)) failures += "$line\n  ours: $actual"
            if (rule.count != null || rule.until != null) {
                val all = rule.asSequence(date(dtStart)).filter { it >= date(from) && it <= date(to) }.toList()
                if (all != actual) failures += "$line\n  asSequence: $all"
            }
        }
        assertTrue(failures.isEmpty(), "${failures.size} mismatches:\n" + failures.take(20).joinToString("\n"))
    }

    @Test
    fun `documented divergences from the oracle hold`() {
        val rows = lines("/golden/rrule-exceptions.csv").drop(1)
        assertTrue(rows.isNotEmpty())
        for (row in rows) {
            val (dtStart, rrule, ours, oracle, source) = row.split('|')
            assertTrue(source.contains("RFC 5545") || source.contains("ClampToMonthEnd"), row)
            assertTrue(ours != oracle, row)
            val clamp = source.contains("ClampToMonthEnd")
            val policy = if (clamp) InvalidDatePolicy.ClampToMonthEnd else InvalidDatePolicy.SkipInvalid
            val actual = RecurrenceRule.parse(rrule).asSequence(date(dtStart), policy).toList()
            assertEquals(dates(ours), actual, row)
        }
    }
}
