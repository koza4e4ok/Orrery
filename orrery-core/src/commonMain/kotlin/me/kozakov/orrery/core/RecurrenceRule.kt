package me.kozakov.orrery.core

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/**
 * An RFC 5545 section 3.3.10 recurrence rule (`RRULE`) at date granularity, plus the series'
 * `EXDATE` list.
 *
 * Supported parts: `FREQ` ([Frequency]), `INTERVAL`, `COUNT`, `UNTIL` (date part only),
 * `BYDAY`, `BYMONTHDAY`, `BYMONTH`, `BYSETPOS` and `WKST`. `BYYEARDAY`, `BYWEEKNO`, the time
 * parts, `RDATE` and `EXRULE` are rejected by [parse].
 *
 * Expansion contract ([occurrences], [asSequence]):
 * - The first instance is always `dtStart` and it counts toward [count], even when it does not
 *   match the rule's pattern (RFC 5545 `COUNT` paragraph).
 * - Dates that do not exist (`31 April`, `29 February` in a common year) are skipped and never
 *   consume [count] ([InvalidDatePolicy.SkipInvalid], the default). `BYMONTHDAY=-1` is the last
 *   day of every month ([monthlyOnLastDay]).
 * - [count] is always counted from `dtStart`: a query window clips the series and never
 *   restarts it.
 * - [exDates] are removed after the [count]/[until] bounded series is generated; removed dates
 *   are not refilled.
 * - A rule that keeps producing nothing (for example `FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=30`)
 *   stops after 1000 consecutive empty periods, and no series continues past `9999-12-31`.
 *
 * This is a plain class rather than a `data class` so that parts such as `BYYEARDAY` can be
 * added later without breaking binary compatibility; use [copy], [recurrenceRule] or the
 * companion factories to build rules. Instances are immutable and validated on construction:
 * an invalid combination throws [IllegalArgumentException].
 *
 * @property frequency `FREQ`.
 * @property interval `INTERVAL`, at least 1.
 * @property byDay `BYDAY`. Ordinals are allowed only for [Frequency.MONTHLY] (`±1..5`) and
 *   [Frequency.YEARLY] (`±1..53`; relative to the month when [byMonth] is set, otherwise to the
 *   year).
 * @property byMonthDay `BYMONTHDAY`, each in `1..31` or `-31..-1`; not allowed for
 *   [Frequency.WEEKLY].
 * @property byMonth `BYMONTH`, each in `1..12`.
 * @property bySetPos `BYSETPOS`, each in `1..366` or `-366..-1`; requires another `BY*` part.
 * @property count `COUNT`, at least 1. Must not be combined with [until].
 * @property until `UNTIL`, inclusive and compared with the occurrence start date.
 * @property weekStart `WKST`; affects [Frequency.WEEKLY] rules with an [interval] above 1.
 * @property exDates `EXDATE` dates removed from the series (matched against start dates).
 */
@Suppress("LongParameterList")
public class RecurrenceRule(
    public val frequency: Frequency,
    public val interval: Int = 1,
    public val byDay: Set<ByDay> = emptySet(),
    public val byMonthDay: Set<Int> = emptySet(),
    public val byMonth: Set<Int> = emptySet(),
    public val bySetPos: Set<Int> = emptySet(),
    public val count: Int? = null,
    public val until: LocalDate? = null,
    public val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    public val exDates: Set<LocalDate> = emptySet(),
) {
    init {
        validateRecurrenceRule(this)
    }

    /** Returns a copy of this rule with the given parts replaced. */
    @Suppress("LongParameterList")
    public fun copy(
        frequency: Frequency = this.frequency,
        interval: Int = this.interval,
        byDay: Set<ByDay> = this.byDay,
        byMonthDay: Set<Int> = this.byMonthDay,
        byMonth: Set<Int> = this.byMonth,
        bySetPos: Set<Int> = this.bySetPos,
        count: Int? = this.count,
        until: LocalDate? = this.until,
        weekStart: DayOfWeek = this.weekStart,
        exDates: Set<LocalDate> = this.exDates,
    ): RecurrenceRule =
        RecurrenceRule(
            frequency = frequency,
            interval = interval,
            byDay = byDay,
            byMonthDay = byMonthDay,
            byMonth = byMonth,
            bySetPos = bySetPos,
            count = count,
            until = until,
            weekStart = weekStart,
            exDates = exDates,
        )

    /**
     * Formats the rule as a canonical RFC 5545 `RRULE` value, for example
     * `RRULE:FREQ=MONTHLY;BYMONTHDAY=-1`. Parts are emitted in a fixed order (`FREQ`,
     * `INTERVAL`, `COUNT`/`UNTIL`, `BYMONTH`, `BYMONTHDAY`, `BYDAY`, `BYSETPOS`, `WKST`) with
     * defaults omitted, so `parse(rule.toRfc5545String(), rule.exDates) == rule`. [exDates] are
     * not included; see [exDatesToRfc5545String].
     *
     * @param includePrefix Whether to start with `RRULE:`.
     */
    public fun toRfc5545String(includePrefix: Boolean = true): String = RecurrenceCodec.format(this, includePrefix)

    /** Formats [exDates] as `EXDATE;VALUE=DATE:20260331,20260430` (ascending), or null when empty. */
    public fun exDatesToRfc5545String(): String? = RecurrenceCodec.formatExDates(exDates)

    /**
     * Start dates of the series that begins at [dtStart] and fall inside [range], ascending,
     * with [exDates] removed. [count] is counted from [dtStart], not from the window.
     */
    public fun occurrences(
        dtStart: LocalDate,
        range: ClosedRange<LocalDate>,
        invalidDatePolicy: InvalidDatePolicy = InvalidDatePolicy.SkipInvalid,
    ): List<LocalDate> {
        val fromEpoch = range.start.toEpochDays()
        val toEpoch = range.endInclusive.toEpochDays()
        if (fromEpoch > toEpoch) return emptyList()
        val excluded = exDateEpochDays()
        val dates =
            RecurrenceExpansion(this, dtStart, invalidDatePolicy)
                .startEpochDays(fromEpoch, toEpoch)
                .filter { it >= fromEpoch && it <= toEpoch && it !in excluded }
                .map { LocalDate.fromEpochDays(it) }
        return dates.toList()
    }

    /**
     * Lazy sequence of every start date of the series that begins at [dtStart], ascending, with
     * [exDates] removed. It is finite when [count] or [until] is set; otherwise bound it with
     * `take` or `takeWhile`.
     */
    public fun asSequence(
        dtStart: LocalDate,
        invalidDatePolicy: InvalidDatePolicy = InvalidDatePolicy.SkipInvalid,
    ): Sequence<LocalDate> {
        val excluded = exDateEpochDays()
        return RecurrenceExpansion(this, dtStart, invalidDatePolicy)
            .startEpochDays(fromEpoch = null, toEpoch = null)
            .filter { it !in excluded }
            .map { LocalDate.fromEpochDays(it) }
    }

    private fun exDateEpochDays(): Set<Long> = exDates.mapTo(HashSet(exDates.size)) { it.toEpochDays() }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RecurrenceRule) return false
        return frequency == other.frequency &&
            interval == other.interval &&
            byDay == other.byDay &&
            byMonthDay == other.byMonthDay &&
            byMonth == other.byMonth &&
            bySetPos == other.bySetPos &&
            count == other.count &&
            until == other.until &&
            weekStart == other.weekStart &&
            exDates == other.exDates
    }

    override fun hashCode(): Int {
        var result = frequency.hashCode()
        result = 31 * result + interval
        result = 31 * result + byDay.hashCode()
        result = 31 * result + byMonthDay.hashCode()
        result = 31 * result + byMonth.hashCode()
        result = 31 * result + bySetPos.hashCode()
        result = 31 * result + (count ?: 0)
        result = 31 * result + until.hashCode()
        result = 31 * result + weekStart.hashCode()
        result = 31 * result + exDates.hashCode()
        return result
    }

    override fun toString(): String =
        "RecurrenceRule(frequency=$frequency, interval=$interval, byDay=$byDay, byMonthDay=$byMonthDay, " +
            "byMonth=$byMonth, bySetPos=$bySetPos, count=$count, until=$until, weekStart=$weekStart, " +
            "exDates=$exDates)"

    public companion object {
        /**
         * Parses an RFC 5545 `RRULE` value such as `FREQ=MONTHLY;BYDAY=2MO;COUNT=12`, with or
         * without the `RRULE:` prefix; keys and values are case-insensitive. `UNTIL` accepts
         * `yyyyMMdd`, `yyyyMMdd'T'HHmmss` and `yyyyMMdd'T'HHmmss'Z'` and keeps only the date.
         *
         * @param exDates Dates to exclude; see [parseExDates].
         * @throws IllegalArgumentException for unknown, unsupported (`BYYEARDAY`, `BYWEEKNO`,
         *   `BYHOUR`, `BYMINUTE`, `BYSECOND`, `RDATE`, `EXRULE`, sub-daily `FREQ`), duplicate or
         *   empty parts, malformed or out-of-range values, and invalid combinations.
         */
        public fun parse(
            rrule: String,
            exDates: Set<LocalDate> = emptySet(),
        ): RecurrenceRule = RecurrenceCodec.parse(rrule, exDates)

        /** Like [parse], but returns null instead of throwing for invalid or unsupported input. */
        public fun parseOrNull(
            rrule: String,
            exDates: Set<LocalDate> = emptySet(),
        ): RecurrenceRule? =
            try {
                RecurrenceCodec.parse(rrule, exDates)
            } catch (ignored: IllegalArgumentException) {
                null
            }

        /**
         * Parses an `EXDATE` property (`EXDATE;VALUE=DATE:20260331,20260430`) or a bare comma
         * separated list of `yyyyMMdd` dates; date-time values keep only the date.
         *
         * @throws IllegalArgumentException for malformed values.
         */
        public fun parseExDates(exDate: String): Set<LocalDate> = RecurrenceCodec.parseExDates(exDate)

        /** Every [interval] days. */
        public fun daily(interval: Int = 1): RecurrenceRule = RecurrenceRule(Frequency.DAILY, interval = interval)

        /** Every [interval] weeks on [days], or on the start date's weekday when [days] is empty. */
        public fun weekly(
            vararg days: DayOfWeek,
            interval: Int = 1,
        ): RecurrenceRule =
            RecurrenceRule(
                frequency = Frequency.WEEKLY,
                interval = interval,
                byDay = days.mapTo(LinkedHashSet()) { ByDay(it) },
            )

        /** Monthly on [day] (`BYMONTHDAY`); months without that day are skipped. */
        public fun monthlyOnDay(day: Int): RecurrenceRule = RecurrenceRule(Frequency.MONTHLY, byMonthDay = setOf(day))

        /** Monthly on the last day of the month (`BYMONTHDAY=-1`). */
        public fun monthlyOnLastDay(): RecurrenceRule = RecurrenceRule(Frequency.MONTHLY, byMonthDay = setOf(-1))

        /** Monthly on the [ordinal]-th [day] of the month, for example `2MO` or `-1FR`. */
        public fun monthlyOnNth(
            ordinal: Int,
            day: DayOfWeek,
        ): RecurrenceRule = RecurrenceRule(Frequency.MONTHLY, byDay = setOf(ByDay(day, ordinal)))

        /** Yearly on the start date's month and day (a 29 February start skips common years). */
        public fun yearly(): RecurrenceRule = RecurrenceRule(Frequency.YEARLY)
    }
}

private fun validateRecurrenceRule(rule: RecurrenceRule) {
    require(rule.interval >= 1) { "interval must be >= 1, was ${rule.interval}" }
    val count = rule.count
    require(count == null || count >= 1) { "count must be >= 1, was $count" }
    require(count == null || rule.until == null) { "count and until must not both be set" }
    for (month in rule.byMonth) {
        require(month in 1..12) { "byMonth values must be in 1..12, was $month" }
    }
    for (day in rule.byMonthDay) {
        require(day != 0 && day in -31..31) { "byMonthDay values must be in 1..31 or -31..-1, was $day" }
    }
    require(rule.byMonthDay.isEmpty() || rule.frequency != Frequency.WEEKLY) {
        "byMonthDay must not be used with FREQ=WEEKLY, was ${rule.byMonthDay}"
    }
    for (position in rule.bySetPos) {
        require(position != 0 && position in -366..366) {
            "bySetPos values must be in 1..366 or -366..-1, was $position"
        }
    }
    val hasOtherByPart = rule.byDay.isNotEmpty() || rule.byMonthDay.isNotEmpty() || rule.byMonth.isNotEmpty()
    require(rule.bySetPos.isEmpty() || hasOtherByPart) { "bySetPos requires byDay, byMonthDay or byMonth" }
    validateByDayOrdinals(rule)
}

private fun validateByDayOrdinals(rule: RecurrenceRule) {
    val maxOrdinal =
        when (rule.frequency) {
            Frequency.MONTHLY -> 5
            Frequency.YEARLY -> 53
            Frequency.DAILY, Frequency.WEEKLY -> 0
        }
    for (day in rule.byDay) {
        val ordinal = day.ordinal ?: continue
        require(maxOrdinal > 0) {
            "byDay ordinals are only allowed with FREQ=MONTHLY or FREQ=YEARLY, was $ordinal${day.dayOfWeek}"
        }
        require(ordinal in -maxOrdinal..maxOrdinal) {
            "byDay ordinal must be in -$maxOrdinal..-1 or 1..$maxOrdinal for FREQ=${rule.frequency}, was $ordinal"
        }
    }
}
