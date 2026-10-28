package me.kozakov.orrery.holidays

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Compares the built-in calendars with fixtures generated from python-holidays by
 * tools/golden/holidays_fixtures.py. For every subdivision and year the holidays themselves
 * must match by date and rule id, and the set of days off (holidays plus substitute days)
 * must match by date; the two libraries may attach a substitute day to different holidays
 * when several fall on one weekend. Sourced disagreements are listed in
 * golden/holidays-exceptions.csv.
 */
class GoldenHolidaysTest {
    private data class Row(
        val date: LocalDate,
        val subdivision: String,
        val ruleId: String,
        val substitute: Boolean,
    )

    private data class KnownDifference(
        val country: String,
        val subdivision: String,
        val date: LocalDate,
        val ours: String,
        val oracle: String,
    )

    private fun lines(resource: String): List<String> =
        checkNotNull(javaClass.getResourceAsStream(resource)) { resource }
            .bufferedReader()
            .readLines()
            .filter { it.isNotBlank() }

    private val exceptions =
        lines("/golden/holidays-exceptions.csv").drop(1).map { line ->
            val (country, subdivision, date, ours, oracle) = line.split(",", limit = 6)
            KnownDifference(country, subdivision, LocalDate.parse(date), ours, oracle)
        }

    @Test
    fun unitedStatesMatchesOracle2000To2050() = check("US", 2000..2050)

    @Test
    fun unitedKingdomMatchesOracle2000To2050() = check("GB", 2000..2050)

    @Test
    fun germanyMatchesOracle2000To2050() = check("DE", 2000..2050)

    // python-holidays lists Ukrainian holidays as working days under martial law from 2022.
    @Test
    fun ukraineMatchesOracle2000To2021() = check("UA", 2000..2021)

    @Test
    fun everyExceptionHasASource() {
        lines("/golden/holidays-exceptions.csv").drop(1).forEach { line ->
            assertTrue(line.split(",", limit = 6).last().startsWith("http"), line)
        }
    }

    private fun check(
        country: String,
        years: IntRange,
    ) {
        val rows =
            lines("/golden/holidays-${country.lowercase()}.csv").map { line ->
                val (date, subdivision, ruleId, substitute) = line.split(",")
                Row(LocalDate.parse(date), subdivision, ruleId, substitute == "1")
            }
        val calendar = checkNotNull(PublicHolidays.forCountry(country))
        val failures = mutableListOf<String>()
        rows.groupBy { it.subdivision }.forEach { (subdivision, subdivisionRows) ->
            val byYear = subdivisionRows.groupBy { it.date.year }
            for (year in years) {
                val oracle = withExceptions(country, subdivision, byYear[year].orEmpty())
                val ours =
                    calendar
                        .holidaysIn(year, subdivision.ifEmpty { null })
                        .filter { it.type == HolidayType.Public || it.type == HolidayType.Bank }
                val expected = oracle.filterNot { it.substitute }.map { it.date to it.ruleId }.toSet()
                val actual = ours.filterNot { it.isSubstitute }.map { it.date to it.ruleId }.toSet()
                val expectedDays = oracle.map { it.date }.toSet()
                val actualDays = ours.map { it.date }.toSet()
                if (expected != actual || expectedDays != actualDays) {
                    failures +=
                        "$country '$subdivision' $year: " +
                        "holidays missing ${expected - actual} extra ${actual - expected}; " +
                        "days off missing ${expectedDays - actualDays} extra ${actualDays - expectedDays}"
                }
            }
        }
        val report = failures.take(MAX_REPORTED).joinToString("\n")
        assertTrue(failures.isEmpty(), "${failures.size} mismatches:\n$report")
    }

    private fun withExceptions(
        country: String,
        subdivision: String,
        rows: List<Row>,
    ): List<Row> {
        var result = rows
        exceptions.filter { it.country == country && it.subdivision == subdivision }.forEach { e ->
            result = result.filterNot { it.date == e.date && it.ruleId == e.oracle && !it.substitute }
            if (e.ours != "-") result = result + Row(e.date, subdivision, e.ours, false)
        }
        return result
    }

    private companion object {
        const val MAX_REPORTED = 200
    }
}
