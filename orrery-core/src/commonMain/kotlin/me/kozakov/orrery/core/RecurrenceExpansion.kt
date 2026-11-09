package me.kozakov.orrery.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number

/** Consecutive periods without a candidate after which a fruitless rule stops. */
internal const val MAX_EMPTY_PERIODS = 1000

/** No series continues past this year (RFC 5545 dates have four-digit years). */
internal const val MAX_RECURRENCE_YEAR = 9999

/**
 * RFC 5545 section 3.3.10 expansion of [rule] for a series starting at [dtStart] (spec section
 * 1.4). Works on epoch days; [LocalDate]s are only created for the per-period month lookups the
 * rule actually needs.
 */
@Suppress("TooManyFunctions")
internal class RecurrenceExpansion(
    private val rule: RecurrenceRule,
    dtStart: LocalDate,
    policy: InvalidDatePolicy,
) {
    private val clamp = policy == InvalidDatePolicy.ClampToMonthEnd
    private val dtEpoch = dtStart.toEpochDays()
    private val dtYear = dtStart.year
    private val dtMonth = dtStart.month.number
    private val dtIsoDayOfWeek = dtStart.dayOfWeek.isoDayNumber
    private val dtMonthIndex = dtYear * 12L + (dtMonth - 1)
    private val weekStartIso = rule.weekStart.isoDayNumber
    private val dtWeekStart = weekStartEpochDay(dtEpoch, weekStartIso)
    private val maxEpoch = LocalDate(MAX_RECURRENCE_YEAR, 12, 31).toEpochDays()

    /** Day numbers a MONTHLY/YEARLY period selects: BYMONTHDAY, else DTSTART's day unless BYDAY expands. */
    private val monthDays: Set<Int> =
        when {
            rule.byMonthDay.isNotEmpty() -> rule.byMonthDay
            rule.byDay.isEmpty() -> setOf(dtStart.day)
            else -> emptySet()
        }
    private val months = BooleanArray(13).also { flags -> rule.byMonth.forEach { flags[it] = true } }
    private val everyWeekday =
        BooleanArray(8).also { flags ->
            rule.byDay.filter { it.ordinal == null }.forEach { flags[it.dayOfWeek.isoDayNumber] = true }
        }
    private val nthWeekdays: List<ByDay> = rule.byDay.filter { it.ordinal != null }

    /**
     * Start dates (epoch days) of the series in ascending order: DTSTART first, then every
     * generated date after it, bounded by COUNT (counted before EXDATE removal) and UNTIL.
     * EXDATE and window filtering are left to the caller.
     *
     * @param fromEpoch When COUNT is not set, periods that end before this day are skipped
     *   arithmetically (nothing in them can affect later dates).
     * @param toEpoch Generation stops once a period starts after this day.
     * @param onPeriod Called once per visited period (tests assert the bounded cost).
     */
    fun startEpochDays(
        fromEpoch: Long?,
        toEpoch: Long?,
        onPeriod: () -> Unit = {},
    ): Sequence<Long> {
        val untilEpoch = rule.until?.toEpochDays() ?: maxEpoch
        if (dtEpoch > untilEpoch) return emptySequence()
        val limit = minOf(untilEpoch, toEpoch ?: maxEpoch)
        val count = rule.count
        val firstPeriod = if (count == null && fromEpoch != null) firstPeriodFor(fromEpoch) else 0L
        val generated =
            periodCandidates(firstPeriod, limit, onPeriod)
                .flatMap { it.asSequence() }
                .filter { it > dtEpoch }
                .takeWhile { it <= limit }
        val series = sequenceOf(dtEpoch) + generated
        return if (count == null) series else series.take(count)
    }

    /** Candidate lists of consecutive periods, until a period starts after [limit] or the rule is fruitless. */
    private fun periodCandidates(
        firstPeriod: Long,
        limit: Long,
        onPeriod: () -> Unit,
    ): Sequence<List<Long>> =
        sequence {
            var period = firstPeriod
            var emptyPeriods = 0
            while (emptyPeriods < MAX_EMPTY_PERIODS && periodStart(period) <= limit) {
                onPeriod()
                val dates = candidates(period)
                yield(dates)
                emptyPeriods = if (dates.isEmpty()) emptyPeriods + 1 else 0
                period++
            }
        }

    /** First epoch day of period [period] (or of DTSTART's week/day for WEEKLY/DAILY). */
    private fun periodStart(period: Long): Long =
        when (rule.frequency) {
            Frequency.DAILY -> dtEpoch + period * rule.interval
            Frequency.WEEKLY -> dtWeekStart + 7L * period * rule.interval
            Frequency.MONTHLY -> monthStart(dtMonthIndex + period * rule.interval)
            Frequency.YEARLY -> monthStart((dtYear + period * rule.interval) * 12L)
        }

    private fun monthStart(monthIndex: Long): Long {
        val year = monthIndex.floorDiv(12L)
        if (year > MAX_RECURRENCE_YEAR) return Long.MAX_VALUE
        return firstEpochDayOfMonth(year.toInt(), monthIndex.mod(12L).toInt() + 1)
    }

    private fun firstPeriodFor(fromEpoch: Long): Long {
        val from = LocalDate.fromEpochDays(fromEpoch)
        val elapsed =
            when (rule.frequency) {
                Frequency.DAILY -> fromEpoch - dtEpoch
                Frequency.WEEKLY -> (weekStartEpochDay(fromEpoch, weekStartIso) - dtWeekStart) / 7
                Frequency.MONTHLY -> from.year * 12L + (from.month.number - 1) - dtMonthIndex
                Frequency.YEARLY -> (from.year - dtYear).toLong()
            }
        return if (elapsed <= 0) 0L else elapsed / rule.interval
    }

    /** Sorted, de-duplicated candidates of [period] after BYSETPOS (before dropping dates < DTSTART). */
    private fun candidates(period: Long): List<Long> {
        val dates = ArrayList<Long>()
        when (rule.frequency) {
            Frequency.DAILY -> dailyCandidate(dtEpoch + period * rule.interval, dates)
            Frequency.WEEKLY -> weeklyCandidates(dtWeekStart + 7L * period * rule.interval, dates)
            Frequency.MONTHLY -> monthlyCandidates(dtMonthIndex + period * rule.interval, dates)
            Frequency.YEARLY -> yearlyCandidates((dtYear + period * rule.interval).toInt(), dates)
        }
        return if (rule.bySetPos.isEmpty()) dates else applySetPos(dates, rule.bySetPos)
    }

    private fun dailyCandidate(
        epoch: Long,
        out: MutableList<Long>,
    ) {
        if (epoch > maxEpoch) return
        if (!matchesWeekdayWithoutOrdinal(epoch)) return
        if (rule.byMonth.isEmpty() && rule.byMonthDay.isEmpty()) {
            out.add(epoch)
            return
        }
        val date = LocalDate.fromEpochDays(epoch)
        val length = daysInMonth(date.year, date.month.number)
        val monthOk = rule.byMonth.isEmpty() || months[date.month.number]
        val dayOk = rule.byMonthDay.isEmpty() || rule.byMonthDay.any { resolveMonthDay(it, length, false) == date.day }
        if (monthOk && dayOk) out.add(epoch)
    }

    private fun weeklyCandidates(
        weekStart: Long,
        out: MutableList<Long>,
    ) {
        for (offset in 0..6) {
            val epoch = weekStart + offset
            val weekdayOk =
                if (rule.byDay.isEmpty()) isoDayOfWeek(epoch) == dtIsoDayOfWeek else everyWeekday[isoDayOfWeek(epoch)]
            if (weekdayOk && epoch <= maxEpoch && monthMatches(epoch)) out.add(epoch)
        }
    }

    private fun monthMatches(epoch: Long): Boolean =
        rule.byMonth.isEmpty() || months[LocalDate.fromEpochDays(epoch).month.number]

    private fun monthlyCandidates(
        monthIndex: Long,
        out: MutableList<Long>,
    ) {
        val year = monthIndex.floorDiv(12L).toInt()
        val month = monthIndex.mod(12L).toInt() + 1
        if (year > MAX_RECURRENCE_YEAR) return
        if (rule.byMonth.isNotEmpty() && !months[month]) return
        monthCandidates(year, month, yearStart = null, yearEnd = 0L, out = out)
    }

    private fun yearlyCandidates(
        year: Int,
        out: MutableList<Long>,
    ) {
        if (year > MAX_RECURRENCE_YEAR) return
        val selectedMonths =
            when {
                rule.byMonth.isNotEmpty() -> rule.byMonth.sorted()
                rule.byMonthDay.isEmpty() && rule.byDay.isEmpty() -> listOf(dtMonth)
                else -> (1..12).toList()
            }
        // BYDAY ordinals are relative to the month when BYMONTH is given, otherwise to the year.
        val yearStart = if (rule.byMonth.isEmpty()) firstEpochDayOfMonth(year, 1) else null
        val yearEnd = if (yearStart == null) 0L else yearStart + daysInYear(year) - 1
        for (month in selectedMonths) {
            monthCandidates(year, month, yearStart, yearEnd, out)
        }
    }

    /**
     * Adds the dates of [year]-[month] selected by the month-day set and BYDAY, in ascending
     * order. BYDAY ordinals are counted inside [yearStart]..[yearEnd] when [yearStart] is set,
     * otherwise inside the month.
     */
    private fun monthCandidates(
        year: Int,
        month: Int,
        yearStart: Long?,
        yearEnd: Long,
        out: MutableList<Long>,
    ) {
        val length = daysInMonth(year, month)
        val first = firstEpochDayOfMonth(year, month)
        val days = selectedMonthDays(length)
        val scopeStart = yearStart ?: first
        val scopeEnd = if (yearStart == null) first + length - 1 else yearEnd
        for (day in 1..length) {
            val epoch = first + day - 1
            if ((days == null || days[day]) && matchesWeekday(epoch, scopeStart, scopeEnd)) out.add(epoch)
        }
    }

    /** Flags indexed by day of month, or null when no month-day filter applies. */
    private fun selectedMonthDays(length: Int): BooleanArray? {
        if (monthDays.isEmpty()) return null
        val flags = BooleanArray(length + 1)
        for (value in monthDays) {
            val day = resolveMonthDay(value, length, clamp)
            if (day >= 1) flags[day] = true
        }
        return flags
    }

    private fun matchesWeekdayWithoutOrdinal(epoch: Long): Boolean =
        rule.byDay.isEmpty() || everyWeekday[isoDayOfWeek(epoch)]

    private fun matchesWeekday(
        epoch: Long,
        scopeStart: Long,
        scopeEnd: Long,
    ): Boolean {
        if (rule.byDay.isEmpty()) return true
        val weekday = isoDayOfWeek(epoch)
        if (everyWeekday[weekday]) return true
        val fromStart = ((epoch - scopeStart) / 7 + 1).toInt()
        val fromEnd = -((scopeEnd - epoch) / 7 + 1).toInt()
        return nthWeekdays.any {
            it.dayOfWeek.isoDayNumber == weekday && (it.ordinal == fromStart || it.ordinal == fromEnd)
        }
    }
}

/**
 * Resolves a BYMONTHDAY value for a month of [length] days: negative values count from the end.
 * Returns 0 when the day does not exist, unless [clamp] maps a too-large positive day to [length].
 */
internal fun resolveMonthDay(
    value: Int,
    length: Int,
    clamp: Boolean,
): Int =
    when {
        value > length -> if (clamp) length else 0
        value > 0 -> value
        else -> (length + value + 1).coerceAtLeast(0)
    }

/** Picks 1-based (negative: from the end) positions out of the sorted per-period [dates]. */
internal fun applySetPos(
    dates: List<Long>,
    positions: Set<Int>,
): List<Long> {
    val selected = HashSet<Long>()
    for (position in positions) {
        val index = if (position > 0) position - 1 else dates.size + position
        if (index in dates.indices) selected.add(dates[index])
    }
    return selected.sorted()
}

/** ISO day of week (1 = Monday) of an epoch day; 1970-01-01 was a Thursday. */
internal fun isoDayOfWeek(epoch: Long): Int = (epoch + 3).mod(7L).toInt() + 1

internal fun weekStartEpochDay(
    epoch: Long,
    weekStartIso: Int,
): Long = epoch - (isoDayOfWeek(epoch) - weekStartIso).mod(7)

internal fun isLeapYear(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

internal fun daysInYear(year: Int): Int = if (isLeapYear(year)) 366 else 365

internal fun daysInMonth(
    year: Int,
    month: Int,
): Int =
    when (month) {
        2 -> if (isLeapYear(year)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

internal fun firstEpochDayOfMonth(
    year: Int,
    month: Int,
): Long = LocalDate(year, month, 1).toEpochDays()
