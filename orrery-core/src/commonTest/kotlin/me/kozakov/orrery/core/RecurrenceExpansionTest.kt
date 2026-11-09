package me.kozakov.orrery.core

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecurrenceExpansionTest {
    private fun d(iso: String): LocalDate = LocalDate.parse(iso)

    private fun dates(vararg iso: String): List<LocalDate> = iso.map { LocalDate.parse(it) }

    private val wide = d("1990-01-01")..d("2040-12-31")

    private fun expand(
        rrule: String,
        dtStart: String,
        range: ClosedRange<LocalDate> = wide,
        policy: InvalidDatePolicy = InvalidDatePolicy.SkipInvalid,
    ): List<LocalDate> = RecurrenceRule.parse(rrule).occurrences(d(dtStart), range, policy)

    // Alox1d's fixtures on #86 (RFC 5545 section 3.3.10).

    @Test
    fun `strictSkipInvalid - Jan 31 monthly COUNT=3 skips February and April without consuming COUNT`() {
        assertEquals(dates("2026-01-31", "2026-03-31", "2026-05-31"), expand("FREQ=MONTHLY;COUNT=3", "2026-01-31"))
    }

    @Test
    fun `strictSkipInvalid - BYMONTHDAY=-1 is the last day of every month`() {
        assertEquals(
            dates("2026-01-31", "2026-02-28", "2026-03-31"),
            expand("FREQ=MONTHLY;BYMONTHDAY=-1;COUNT=3", "2026-01-31"),
        )
        assertEquals(
            dates("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30"),
            RecurrenceRule.monthlyOnLastDay().copy(count = 4).occurrences(d("2026-01-31"), wide),
        )
    }

    @Test
    fun `strictSkipInvalid - BYMONTHDAY=30 skips February without consuming COUNT`() {
        assertEquals(
            dates("2026-01-30", "2026-03-30", "2026-04-30"),
            expand("FREQ=MONTHLY;BYMONTHDAY=30;COUNT=3", "2026-01-30"),
        )
    }

    @Test
    fun `windowClipsSeries - a sub-window never restarts COUNT`() {
        val rule = "FREQ=MONTHLY;COUNT=3"
        assertEquals(dates("2026-03-31"), expand(rule, "2026-01-31", d("2026-03-01")..d("2026-03-31")))
        assertEquals(emptyList(), expand(rule, "2026-01-31", d("2026-04-01")..d("2026-04-30")))
        assertEquals(dates("2026-05-31"), expand(rule, "2026-01-31", d("2026-05-01")..d("2026-05-31")))
        assertEquals(emptyList(), expand(rule, "2026-01-31", d("2026-06-01")..d("2026-12-31")))
    }

    @Test
    fun `exDateNoRefill - EXDATE subtracts from the COUNT bounded series`() {
        val rule = RecurrenceRule.parse("FREQ=MONTHLY;COUNT=3", exDates = setOf(d("2026-03-31")))
        assertEquals(dates("2026-01-31", "2026-05-31"), rule.occurrences(d("2026-01-31"), wide))
        assertEquals(emptyList(), rule.occurrences(d("2026-01-31"), d("2026-03-01")..d("2026-03-31")))
        assertEquals(dates("2026-01-31", "2026-05-31"), rule.asSequence(d("2026-01-31")).toList())
    }

    @Test
    fun `exDate on DTSTART removes it but still consumes COUNT`() {
        val rule = RecurrenceRule.parse("FREQ=DAILY;COUNT=3", exDates = setOf(d("2026-01-01")))
        assertEquals(dates("2026-01-02", "2026-01-03"), rule.occurrences(d("2026-01-01"), wide))
    }

    @Test
    fun `clampPolicy - ClampToMonthEnd clamps per month from the original day and consumes COUNT`() {
        val clamp = InvalidDatePolicy.ClampToMonthEnd
        assertEquals(
            dates("2026-01-31", "2026-02-28", "2026-03-31"),
            expand("FREQ=MONTHLY;COUNT=3", "2026-01-31", policy = clamp),
        )
        assertEquals(dates("2028-01-31", "2028-02-29"), expand("FREQ=MONTHLY;COUNT=2", "2028-01-31", policy = clamp))
        assertEquals(
            dates("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30"),
            expand("FREQ=MONTHLY;BYMONTHDAY=31;COUNT=4", "2026-01-31", policy = clamp),
        )
        assertEquals(
            dates("2024-02-29", "2025-02-28", "2026-02-28"),
            expand("FREQ=YEARLY;COUNT=3", "2024-02-29", policy = clamp),
        )
    }

    @Test
    fun `clampPolicy has no effect on BYDAY ordinal rules`() {
        val rule = "FREQ=MONTHLY;BYDAY=5MO;COUNT=2"
        assertEquals(
            expand(rule, "2026-03-30"),
            expand(rule, "2026-03-30", policy = InvalidDatePolicy.ClampToMonthEnd),
        )
    }

    @Test
    fun `leapDay - yearly Feb 29 skips common years`() {
        assertEquals(dates("2024-02-29", "2028-02-29", "2032-02-29"), expand("FREQ=YEARLY;COUNT=3", "2024-02-29"))
        assertEquals(
            dates("2024-02-29", "2028-02-29"),
            RecurrenceRule.yearly().occurrences(d("2024-02-29"), wide).take(2),
        )
    }

    @Test
    fun `nthWeekday - second Monday and last Friday`() {
        assertEquals(
            dates("2026-01-12", "2026-02-09", "2026-03-09"),
            expand("FREQ=MONTHLY;BYDAY=2MO;COUNT=3", "2026-01-12"),
        )
        assertEquals(
            dates("2026-01-30", "2026-02-27", "2026-03-27"),
            RecurrenceRule.monthlyOnNth(-1, DayOfWeek.FRIDAY).copy(count = 3).occurrences(d("2026-01-30"), wide),
        )
    }

    @Test
    fun `nthWeekday - fifth Monday skips months without one`() {
        assertEquals(
            dates("2026-03-30", "2026-06-29", "2026-08-31", "2026-11-30"),
            expand("FREQ=MONTHLY;BYDAY=5MO;COUNT=4", "2026-03-30"),
        )
    }

    @Test
    fun `nthWeekday - yearly 20MO and US Thanksgiving`() {
        // RFC 5545 section 3.8.5.3 example: every 20th Monday of the year.
        assertEquals(
            dates("1997-05-19", "1998-05-18", "1999-05-17"),
            expand("FREQ=YEARLY;BYDAY=20MO;COUNT=3", "1997-05-19"),
        )
        assertEquals(
            dates("2026-11-26", "2027-11-25", "2028-11-23"),
            expand("FREQ=YEARLY;BYMONTH=11;BYDAY=4TH;COUNT=3", "2026-11-26"),
        )
    }

    @Test
    fun `setPos - last weekday of the month and first and last`() {
        assertEquals(
            dates("2026-01-30", "2026-02-27", "2026-03-31"),
            expand("FREQ=MONTHLY;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=-1;COUNT=3", "2026-01-30"),
        )
        assertEquals(
            dates("2026-01-01", "2026-01-30", "2026-02-02", "2026-02-27"),
            expand("FREQ=MONTHLY;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=1,-1;COUNT=4", "2026-01-01"),
        )
        // RFC 5545 example: the third Tuesday, Wednesday or Thursday of the month.
        assertEquals(
            dates("1997-09-04", "1997-10-07", "1997-11-06"),
            expand("FREQ=MONTHLY;COUNT=3;BYDAY=TU,WE,TH;BYSETPOS=3", "1997-09-04"),
        )
    }

    @Test
    fun `weekStart - RFC 5545 biweekly WKST examples`() {
        assertEquals(
            dates("1997-08-05", "1997-08-10", "1997-08-19", "1997-08-24"),
            expand("FREQ=WEEKLY;INTERVAL=2;COUNT=4;BYDAY=TU,SU;WKST=MO", "1997-08-05"),
        )
        assertEquals(
            dates("1997-08-05", "1997-08-17", "1997-08-19", "1997-08-31"),
            expand("FREQ=WEEKLY;INTERVAL=2;COUNT=4;BYDAY=TU,SU;WKST=SU", "1997-08-05"),
        )
    }

    @Test
    fun `dtStartCounts - a DTSTART that does not match the rule is instance 1`() {
        assertEquals(
            dates("2026-11-02", "2026-11-03", "2026-11-10"),
            expand("FREQ=WEEKLY;BYDAY=TU;COUNT=3", "2026-11-02"),
        )
    }

    @Test
    fun `until is inclusive on the start date and a date-time UNTIL keeps the date part`() {
        val expected = dates("2026-01-01", "2026-01-02", "2026-01-03", "2026-01-04", "2026-01-05")
        assertEquals(expected, expand("FREQ=DAILY;UNTIL=20260105", "2026-01-01"))
        assertEquals(expected, expand("FREQ=DAILY;UNTIL=20260105T000000Z", "2026-01-01"))
        assertEquals(
            expected,
            RecurrenceRule
                .daily()
                .copy(until = d("2026-01-05"))
                .asSequence(d("2026-01-01"))
                .toList(),
        )
        assertEquals(emptyList(), expand("FREQ=DAILY;UNTIL=20251231", "2026-01-01"))
    }

    @Test
    fun `weekly defaults to the DTSTART weekday and daily filters by BY parts`() {
        assertEquals(dates("2026-01-07", "2026-01-14", "2026-01-21"), expand("FREQ=WEEKLY;COUNT=3", "2026-01-07"))
        assertEquals(
            dates("2026-01-31", "2026-03-31", "2026-05-31"),
            expand("FREQ=DAILY;BYMONTHDAY=-1;BYMONTH=1,3,5;COUNT=3", "2026-01-31"),
        )
        assertEquals(
            dates("2026-01-03", "2026-01-04", "2026-01-10"),
            expand("FREQ=DAILY;BYDAY=SA,SU;COUNT=3", "2026-01-03"),
        )
    }

    @Test
    fun `yearly expands BYMONTH BYMONTHDAY and year-relative BYDAY`() {
        assertEquals(
            dates("2026-01-15", "2026-06-15", "2027-01-15"),
            expand("FREQ=YEARLY;BYMONTH=1,6;COUNT=3", "2026-01-15"),
        )
        assertEquals(
            dates("2026-01-01", "2026-02-01", "2026-03-01"),
            expand("FREQ=YEARLY;BYMONTHDAY=1;COUNT=3", "2026-01-01"),
        )
        assertEquals(dates("2026-12-28", "2027-12-27"), expand("FREQ=YEARLY;BYDAY=-1MO;COUNT=2", "2026-12-28"))
    }

    @Test
    fun `window queries on unbounded rules fast-forward in a bounded number of periods`() {
        var periods = 0
        val from = d("2031-03-01").toEpochDays()
        val to = d("2031-03-31").toEpochDays()
        val days =
            RecurrenceExpansion(RecurrenceRule.daily(), d("2000-01-01"), InvalidDatePolicy.SkipInvalid)
                .startEpochDays(from, to) { periods++ }
                .filter { it in from..to }
                .toList()
        assertEquals(31, days.size)
        assertTrue(periods <= 32, "visited $periods periods")
        assertEquals(
            dates("2031-03-03", "2031-03-17", "2031-03-31"),
            RecurrenceRule
                .weekly(DayOfWeek.MONDAY, interval = 2)
                .occurrences(d("2000-01-03"), d("2031-03-01")..d("2031-03-31")),
        )
        assertEquals(
            dates("2031-03-31"),
            RecurrenceRule.monthlyOnLastDay().occurrences(d("2000-01-31"), d("2031-03-01")..d("2031-03-31")),
        )
    }

    @Test
    fun `a COUNT=1000 walk stops at the count`() {
        var periods = 0
        val start = d("2026-01-01")
        val all =
            RecurrenceExpansion(RecurrenceRule.parse("FREQ=DAILY;COUNT=1000"), start, InvalidDatePolicy.SkipInvalid)
                .startEpochDays(fromEpoch = null, toEpoch = null) { periods++ }
                .toList()
        assertEquals(1000, all.size)
        assertEquals(start.toEpochDays() + 999, all.last())
        assertTrue(periods <= 1001, "visited $periods periods")
    }

    @Test
    fun `a fruitless rule terminates after the empty-period bound`() {
        var periods = 0
        val rule = RecurrenceRule.parse("FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=30")
        val start = d("2026-01-01")
        val all =
            RecurrenceExpansion(rule, start, InvalidDatePolicy.SkipInvalid)
                .startEpochDays(fromEpoch = null, toEpoch = null) { periods++ }
                .toList()
        assertEquals(listOf(start.toEpochDays()), all)
        assertEquals(MAX_EMPTY_PERIODS, periods)
        assertEquals(listOf(start), rule.asSequence(start).toList())
    }

    @Test
    fun `unbounded sequences are lazy and an empty window returns nothing`() {
        val mondays =
            RecurrenceRule
                .weekly(DayOfWeek.MONDAY)
                .asSequence(d("2026-01-05"))
                .take(3)
                .toList()
        assertEquals(dates("2026-01-05", "2026-01-12", "2026-01-19"), mondays)
        assertEquals(emptyList(), RecurrenceRule.daily().occurrences(d("2026-01-01"), d("2026-02-01")..d("2026-01-01")))
        assertEquals(emptyList(), RecurrenceRule.daily().occurrences(d("2026-01-10"), d("2026-01-01")..d("2026-01-09")))
    }
}
