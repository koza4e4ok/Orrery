package me.kozakov.orrery.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * Temporal extent of a [CalendarEvent].
 *
 * Making the span a sealed hierarchy prevents illegal combinations such as an
 * all-day event with a start time or a timed event missing one endpoint.
 */
public sealed interface EventSpan {
    public val startDate: LocalDate
    public val endDate: LocalDate

    /**
     * All-day or multi-day banner event spanning [startDate]..[endDate] inclusive.
     */
    public data class AllDay(
        override val startDate: LocalDate,
        override val endDate: LocalDate = startDate,
    ) : EventSpan {
        init {
            require(endDate >= startDate) {
                "endDate ($endDate) must not precede startDate ($startDate)"
            }
        }
    }

    /**
     * Timed event spanning [start]..[end] (supports same-day and overnight spans).
     */
    public data class Timed(
        public val start: LocalDateTime,
        public val end: LocalDateTime,
    ) : EventSpan {
        init {
            require(end >= start) {
                "end ($end) must not precede start ($start)"
            }
        }

        override val startDate: LocalDate get() = start.date
        override val endDate: LocalDate get() = end.date
    }
}

/**
 * Minimal, optional calendar event model shared across `DayDecorators`,
 * `Agenda`, and the time-grid schedule views (#21).
 *
 * @param T Optional caller-owned domain payload for click handling or custom slots.
 * @property id Stable identifier used as a Compose lazy-list/grid key and tie-breaker.
 * @property title Primary display title.
 * @property span Temporal extent ([EventSpan.AllDay] or [EventSpan.Timed]).
 * @property subtitle Optional secondary text (location, room, summary).
 * @property colorArgb Optional 0xAARRGGBB color hint (UI-agnostic in `orrery-core`).
 * @property payload Optional domain object attached by the consumer.
 */
public data class CalendarEvent<out T>(
    public val id: String,
    public val title: String,
    public val span: EventSpan,
    public val subtitle: String? = null,
    public val colorArgb: Long? = null,
    public val payload: T? = null,
) {
    /** True if this event covers any part of [date]. */
    public fun occursOn(date: LocalDate): Boolean = date >= span.startDate && date <= span.endDate
}

/**
 * Groups events by each [LocalDate] they cover within [range], sorted in
 * canonical display order per day:
 * 1. [EventSpan.AllDay] events (ordered by [EventSpan.startDate], then
 *    [CalendarEvent.title], then [CalendarEvent.id])
 * 2. [EventSpan.Timed] events (ordered by [EventSpan.Timed.start], then
 *    [EventSpan.Timed.end], then [CalendarEvent.title], then [CalendarEvent.id])
 *
 * Iterates via epoch days (matching Orrery's existing Bolt optimization) to avoid
 * `DateTimeUnit.DAY` allocation overhead.
 */
public fun <T> Iterable<CalendarEvent<T>>.eventsByDate(
    range: ClosedRange<LocalDate>,
): Map<LocalDate, List<CalendarEvent<T>>> {
    val rangeStartEpoch = range.start.toEpochDays()
    val rangeEndEpoch = range.endInclusive.toEpochDays()
    if (rangeStartEpoch > rangeEndEpoch) return emptyMap()

    val buckets = mutableMapOf<Int, MutableList<CalendarEvent<T>>>()
    for (event in this) {
        val startEpoch = maxOf(event.span.startDate.toEpochDays(), rangeStartEpoch)
        val endEpoch = minOf(event.span.endDate.toEpochDays(), rangeEndEpoch)
        for (epoch in startEpoch..endEpoch) {
            buckets.getOrPut(epoch) { mutableListOf() }.add(event)
        }
    }
    if (buckets.isEmpty()) return emptyMap()

    val result = LinkedHashMap<LocalDate, List<CalendarEvent<T>>>(buckets.size)
    for (epoch in buckets.keys.sorted()) {
        val list = buckets.getValue(epoch)
        list.sortWith(EventComparator)
        result[LocalDate.fromEpochDays(epoch)] = list
    }
    return result
}

private val EventComparator: Comparator<CalendarEvent<*>> =
    Comparator { a, b ->
        when (val spanA = a.span) {
            is EventSpan.AllDay -> {
                when (val spanB = b.span) {
                    is EventSpan.AllDay -> compareAllDay(a, spanA, b, spanB)
                    is EventSpan.Timed -> -1
                }
            }

            is EventSpan.Timed -> {
                when (val spanB = b.span) {
                    is EventSpan.AllDay -> 1
                    is EventSpan.Timed -> compareTimed(a, spanA, b, spanB)
                }
            }
        }
    }

private fun compareAllDay(
    a: CalendarEvent<*>,
    spanA: EventSpan.AllDay,
    b: CalendarEvent<*>,
    spanB: EventSpan.AllDay,
): Int {
    val cmpStart = spanA.startDate.compareTo(spanB.startDate)
    if (cmpStart != 0) return cmpStart
    val cmpTitle = a.title.compareTo(b.title)
    return if (cmpTitle != 0) cmpTitle else a.id.compareTo(b.id)
}

private fun compareTimed(
    a: CalendarEvent<*>,
    spanA: EventSpan.Timed,
    b: CalendarEvent<*>,
    spanB: EventSpan.Timed,
): Int {
    val cmpStart = spanA.start.compareTo(spanB.start)
    if (cmpStart != 0) return cmpStart
    val cmpEnd = spanA.end.compareTo(spanB.end)
    if (cmpEnd != 0) return cmpEnd
    val cmpTitle = a.title.compareTo(b.title)
    return if (cmpTitle != 0) cmpTitle else a.id.compareTo(b.id)
}
