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
 * @property recurrence Recurrence rule of a series master; [span] is the first occurrence
 *   (RFC 5545 `DTSTART`) and every occurrence keeps its duration. Null for plain events and for
 *   expanded occurrences. Expand with [occurrencesIn] or [expandRecurring]; [eventsByDate] does it
 *   automatically.
 * @property instance Set on occurrences produced by [occurrencesIn]: identifies the series and
 *   the occurrence start date.
 */
public data class CalendarEvent<out T>(
    public val id: String,
    public val title: String,
    public val span: EventSpan,
    public val subtitle: String? = null,
    public val colorArgb: Long? = null,
    public val payload: T? = null,
    public val recurrence: RecurrenceRule? = null,
    public val instance: RecurrenceInstance? = null,
) {
    /** Binary-compatible 6-argument constructor for callers compiled against 0.9.x. */
    @Deprecated(
        message = "Binary compatibility bridge for the 6-arg CalendarEvent constructor",
        level = DeprecationLevel.HIDDEN,
    )
    public constructor(
        id: String,
        title: String,
        span: EventSpan,
        subtitle: String? = null,
        colorArgb: Long? = null,
        payload: T? = null,
    ) : this(
        id = id,
        title = title,
        span = span,
        subtitle = subtitle,
        colorArgb = colorArgb,
        payload = payload,
        recurrence = null,
        instance = null,
    )

    /** Binary-compatible 6-argument copy bridge for callers compiled against 0.9.x. */
    @Deprecated(
        message = "Binary compatibility bridge for the 6-arg CalendarEvent.copy",
        level = DeprecationLevel.HIDDEN,
    )
    @Suppress("LongParameterList")
    public fun copy(
        id: String = this.id,
        title: String = this.title,
        span: EventSpan = this.span,
        subtitle: String? = this.subtitle,
        colorArgb: Long? = this.colorArgb,
        payload: @UnsafeVariance T? = this.payload,
    ): CalendarEvent<T> =
        CalendarEvent(
            id = id,
            title = title,
            span = span,
            subtitle = subtitle,
            colorArgb = colorArgb,
            payload = payload,
            recurrence = recurrence,
            instance = instance,
        )

    /** True if this event covers any part of [date]. */
    public fun occursOn(date: LocalDate): Boolean = date >= span.startDate && date <= span.endDate

    /**
     * Occurrences of this event that overlap [range], in chronological order.
     *
     * Without [recurrence] this is `listOf(this)` when [span] overlaps [range], otherwise empty.
     * For a series master every occurrence keeps the master's duration (`endDate - startDate` in
     * days, and the start and end times of a timed event), so a multi-day occurrence that began
     * before [range] but still overlaps it is included. [RecurrenceRule.count] is counted from
     * the master's start date, and [RecurrenceRule.exDates] remove whole occurrences by start
     * date.
     *
     * Each occurrence has `id = "<master id>@<yyyy-MM-dd start date>"`, no [recurrence], and
     * [instance] set to `RecurrenceInstance(master id, start date)`; the other fields are copied.
     */
    public fun occurrencesIn(
        range: ClosedRange<LocalDate>,
        invalidDatePolicy: InvalidDatePolicy = InvalidDatePolicy.SkipInvalid,
    ): List<CalendarEvent<T>> {
        val rangeStart = range.start.toEpochDays()
        val rangeEnd = range.endInclusive.toEpochDays()
        if (rangeStart > rangeEnd) return emptyList()
        val rule = recurrence
        if (rule == null) return if (overlaps(span, rangeStart, rangeEnd)) listOf(this) else emptyList()
        val durationDays = span.endDate.toEpochDays() - span.startDate.toEpochDays()
        val window = LocalDate.fromEpochDays(rangeStart - durationDays)..range.endInclusive
        return rule
            .occurrences(span.startDate, window, invalidDatePolicy)
            .map { start -> occurrenceAt(start, durationDays) }
            .filter { overlaps(it.span, rangeStart, rangeEnd) }
    }

    private fun occurrenceAt(
        start: LocalDate,
        durationDays: Long,
    ): CalendarEvent<T> {
        val end = LocalDate.fromEpochDays(start.toEpochDays() + durationDays)
        val shifted =
            when (val original = span) {
                is EventSpan.AllDay -> EventSpan.AllDay(start, end)
                is EventSpan.Timed -> {
                    EventSpan.Timed(
                        start = LocalDateTime(start, original.start.time),
                        end = LocalDateTime(end, original.end.time),
                    )
                }
            }
        return CalendarEvent(
            id = "$id@$start",
            title = title,
            span = shifted,
            subtitle = subtitle,
            colorArgb = colorArgb,
            payload = payload,
            recurrence = null,
            instance = RecurrenceInstance(seriesId = id, recurrenceId = start),
        )
    }
}

private fun overlaps(
    span: EventSpan,
    rangeStartEpoch: Long,
    rangeEndEpoch: Long,
): Boolean = span.startDate.toEpochDays() <= rangeEndEpoch && span.endDate.toEpochDays() >= rangeStartEpoch

/**
 * Expands every recurring event ([CalendarEvent.recurrence] set) into its occurrences that
 * overlap [range] and keeps every other event that overlaps [range], via
 * [CalendarEvent.occurrencesIn].
 *
 * The result follows the input order (each master's occurrences are chronological in its place)
 * and is not re-sorted. Expanded occurrences pass through unchanged, so the call is idempotent.
 */
public fun <T> Iterable<CalendarEvent<T>>.expandRecurring(
    range: ClosedRange<LocalDate>,
    invalidDatePolicy: InvalidDatePolicy = InvalidDatePolicy.SkipInvalid,
): List<CalendarEvent<T>> = flatMap { it.occurrencesIn(range, invalidDatePolicy) }

/**
 * Groups events by each [LocalDate] they cover within [range], sorted in
 * canonical display order per day:
 * 1. [EventSpan.AllDay] events (ordered by [EventSpan.startDate], then
 *    [CalendarEvent.title], then [CalendarEvent.id])
 * 2. [EventSpan.Timed] events (ordered by [EventSpan.Timed.start], then
 *    [EventSpan.Timed.end], then [CalendarEvent.title], then [CalendarEvent.id])
 *
 * Recurring events ([CalendarEvent.recurrence] set) are expanded first with [expandRecurring], so
 * each occurrence is bucketed under the dates it covers.
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

    val events = if (any { it.recurrence != null }) expandRecurring(range) else this
    val buckets = mutableMapOf<Long, MutableList<CalendarEvent<T>>>()
    for (event in events) {
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
