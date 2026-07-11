package dev.koza4e4ok.material.calendar.core

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/**
 * Immutable set of unavailable dates: explicit dates, inclusive ranges,
 * days of week, exclusive before/after bounds, and predicate escape
 * hatches for dynamic availability. Entries combine as a union. Build
 * with the [DisabledDates] factory function.
 */
public class DisabledDates internal constructor(
    private val dates: Set<LocalDate>,
    private val ranges: List<ClosedRange<LocalDate>>,
    private val daysOfWeek: Set<DayOfWeek>,
    private val before: LocalDate?,
    private val after: LocalDate?,
    private val predicates: List<(LocalDate) -> Boolean>,
) {
    public operator fun contains(date: LocalDate): Boolean =
        date in dates ||
            date.dayOfWeek in daysOfWeek ||
            (before != null && date < before) ||
            (after != null && date > after) ||
            ranges.any { date in it } ||
            predicates.any { it(date) }

    public companion object {
        /** Disables nothing. */
        public val None: DisabledDates =
            DisabledDates(emptySet(), emptyList(), emptySet(), null, null, emptyList())
    }
}

public class DisabledDatesBuilder internal constructor() {
    private val dates = mutableSetOf<LocalDate>()
    private val ranges = mutableListOf<ClosedRange<LocalDate>>()
    private val daysOfWeek = mutableSetOf<DayOfWeek>()
    private var before: LocalDate? = null
    private var after: LocalDate? = null
    private val predicates = mutableListOf<(LocalDate) -> Boolean>()

    public fun dates(vararg dates: LocalDate) {
        this.dates += dates
    }

    public fun dates(dates: Collection<LocalDate>) {
        this.dates += dates
    }

    /** Disables every date in [range], endpoints included. */
    public fun range(range: ClosedRange<LocalDate>) {
        ranges += range
    }

    public fun daysOfWeek(vararg days: DayOfWeek) {
        daysOfWeek += days
    }

    /** Disables dates strictly before [date]; repeated calls keep the widest bound. */
    public fun before(date: LocalDate) {
        before = before?.let { maxOf(it, date) } ?: date
    }

    /** Disables dates strictly after [date]; repeated calls keep the widest bound. */
    public fun after(date: LocalDate) {
        after = after?.let { minOf(it, date) } ?: date
    }

    public fun predicate(predicate: (LocalDate) -> Boolean) {
        predicates += predicate
    }

    internal fun build(): DisabledDates {
        val empty =
            listOf(dates, ranges, daysOfWeek, predicates).all { it.isEmpty() } &&
                before == null &&
                after == null
        return if (empty) {
            DisabledDates.None
        } else {
            DisabledDates(dates.toSet(), ranges.toList(), daysOfWeek.toSet(), before, after, predicates.toList())
        }
    }
}

public fun DisabledDates(block: DisabledDatesBuilder.() -> Unit): DisabledDates =
    DisabledDatesBuilder().apply(block).build()
