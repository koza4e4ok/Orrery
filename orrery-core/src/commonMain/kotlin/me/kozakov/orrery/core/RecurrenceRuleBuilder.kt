package me.kozakov.orrery.core

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/**
 * Builds a [RecurrenceRule] with a Kotlin DSL:
 *
 * ```kotlin
 * val rule = recurrenceRule(Frequency.MONTHLY) {
 *     nth(2, DayOfWeek.MONDAY)
 *     count = 12
 *     exDate(LocalDate(2026, 5, 11))
 * }
 * ```
 *
 * The rule is validated by the [RecurrenceRule] constructor when [block] returns.
 */
public fun recurrenceRule(
    frequency: Frequency,
    block: RecurrenceRuleBuilder.() -> Unit = {},
): RecurrenceRule = RecurrenceRuleBuilder(frequency).apply(block).build()

/** Receiver of [recurrenceRule]. Repeated calls accumulate values. */
public class RecurrenceRuleBuilder internal constructor(
    private val frequency: Frequency,
) {
    /** `INTERVAL`, at least 1. */
    public var interval: Int = 1

    /** `COUNT`; must not be combined with [until]. */
    public var count: Int? = null

    /** `UNTIL` (inclusive, on the occurrence start date); must not be combined with [count]. */
    public var until: LocalDate? = null

    /** `WKST`. */
    public var weekStart: DayOfWeek = DayOfWeek.MONDAY

    private val byDay = LinkedHashSet<ByDay>()
    private val byMonthDay = LinkedHashSet<Int>()
    private val byMonth = LinkedHashSet<Int>()
    private val bySetPos = LinkedHashSet<Int>()
    private val exDates = LinkedHashSet<LocalDate>()

    /** Every [days] in the period (`BYDAY=MO,WE`). */
    public fun byDay(vararg days: DayOfWeek) {
        days.mapTo(byDay) { ByDay(it) }
    }

    /** The [ordinal]-th [day] of the period (`2MO`, `-1FR`); MONTHLY and YEARLY only. */
    public fun nth(
        ordinal: Int,
        day: DayOfWeek,
    ) {
        byDay += ByDay(day, ordinal)
    }

    /** The last [day] of the period; same as `nth(-1, day)`. */
    public fun last(day: DayOfWeek) {
        nth(-1, day)
    }

    /** `BYMONTHDAY` values (`1..31`, or `-31..-1` from the end of the month). */
    public fun byMonthDay(vararg days: Int) {
        byMonthDay += days.toList()
    }

    /** `BYMONTH` values (`1..12`). */
    public fun byMonth(vararg months: Int) {
        byMonth += months.toList()
    }

    /** `BYSETPOS` values (`1..366`, or `-366..-1` from the end of the period). */
    public fun bySetPos(vararg positions: Int) {
        bySetPos += positions.toList()
    }

    /** Dates removed from the series (`EXDATE`). */
    public fun exDate(vararg dates: LocalDate) {
        exDates += dates
    }

    internal fun build(): RecurrenceRule =
        RecurrenceRule(
            frequency = frequency,
            interval = interval,
            byDay = byDay.toSet(),
            byMonthDay = byMonthDay.toSet(),
            byMonth = byMonth.toSet(),
            bySetPos = bySetPos.toSet(),
            count = count,
            until = until,
            weekStart = weekStart,
            exDates = exDates.toSet(),
        )
}
