package me.kozakov.orrery.core

import kotlinx.datetime.LocalDate

private const val MINUTES_PER_DAY: Int = 24 * 60

/**
 * Computed 2D layout box for a single [CalendarEvent] on a specific [date]'s
 * 24-hour timeline (0..1440 minutes from midnight).
 *
 * @property event Source calendar event.
 * @property date The day column this placement belongs to.
 * @property startMinute Clamped start minute in `0..1440`.
 * @property endMinute Clamped end minute in `0..1440`.
 * @property displayEndMinute Effective visual bottom minute (`>= startMinute + minDurationMinutes`,
 *   capped at 1440).
 * @property columnIndex 0-based horizontal lane index within the overlap cluster.
 * @property columnCount Total parallel lanes in the overlap cluster (`>= 1`).
 * @property leftFraction Normalized horizontal start offset in `0f..1f`.
 * @property widthFraction Normalized horizontal width in `0f..1f`.
 */
public data class PositionedEvent<out T>(
    public val event: CalendarEvent<T>,
    public val date: LocalDate,
    public val startMinute: Int,
    public val endMinute: Int,
    public val displayEndMinute: Int,
    public val columnIndex: Int,
    public val columnCount: Int,
    public val leftFraction: Float,
    public val widthFraction: Float,
)

/**
 * Partitioned events for a single [date] column: all-day banner items plus
 * non-overlapping 2D-packed timed placements.
 *
 * @property allDayEvents [EventSpan.AllDay] events covering [date], ordered by start date, title and id.
 * @property timedEvents Timed placements ordered by start minute, longer events first.
 */
public data class DayScheduleLayout<out T>(
    public val date: LocalDate,
    public val allDayEvents: List<CalendarEvent<T>>,
    public val timedEvents: List<PositionedEvent<T>>,
)

/**
 * Computes [DayScheduleLayout] for [events] occurring on [date].
 *
 * Timed events are clipped to [date]'s `00:00..24:00` window, grouped into
 * connected overlap clusters, packed greedily into the lowest free column, and
 * then widened to the right across columns that are free for their whole span.
 * Events in separate clusters never affect each other's widths. An overnight
 * event that ends exactly at `00:00` on [date] is not placed on [date].
 *
 * @param minDurationMinutes Minimum visual height in minutes so 0-minute or 5-minute
 *   events remain legible and tappable (default 15 minutes).
 */
public fun <T> layoutDaySchedule(
    date: LocalDate,
    events: Iterable<CalendarEvent<T>>,
    minDurationMinutes: Int = 15,
): DayScheduleLayout<T> {
    require(minDurationMinutes >= 0) { "minDurationMinutes ($minDurationMinutes) must not be negative" }
    val allDay = mutableListOf<CalendarEvent<T>>()
    val timed = mutableListOf<Interval<T>>()
    for (event in events) {
        if (!event.occursOn(date)) continue
        when (val span = event.span) {
            is EventSpan.AllDay -> allDay.add(event)
            is EventSpan.Timed -> span.clipTo(date, minDurationMinutes)?.let { timed.add(Interval(event, it)) }
        }
    }
    allDay.sortWith(compareBy<CalendarEvent<T>>({ it.span.startDate }, { it.title }, { it.id }))
    timed.sortWith(
        compareBy<Interval<T>>({ it.minutes.start }, { -it.minutes.displayEnd }, { it.event.id }),
    )

    val positioned = ArrayList<PositionedEvent<T>>(timed.size)
    var clusterStart = 0
    var clusterMaxEnd = Int.MIN_VALUE
    for (index in timed.indices) {
        val interval = timed[index]
        if (index > clusterStart && interval.minutes.start >= clusterMaxEnd) {
            packCluster(date, timed.subList(clusterStart, index), positioned)
            clusterStart = index
            clusterMaxEnd = Int.MIN_VALUE
        }
        clusterMaxEnd = maxOf(clusterMaxEnd, interval.minutes.displayEnd)
    }
    if (timed.isNotEmpty()) {
        packCluster(date, timed.subList(clusterStart, timed.size), positioned)
    }
    return DayScheduleLayout(date = date, allDayEvents = allDay, timedEvents = positioned)
}

private class ClippedMinutes(
    val start: Int,
    val end: Int,
    val displayEnd: Int,
)

private class Interval<T>(
    val event: CalendarEvent<T>,
    val minutes: ClippedMinutes,
) {
    var column: Int = 0
}

private fun EventSpan.Timed.clipTo(
    date: LocalDate,
    minDurationMinutes: Int,
): ClippedMinutes? {
    // An overnight event ending exactly at midnight does not reach into [date].
    val endsAtMidnightOfDate = end.date == date && end.hour == 0 && end.minute == 0
    if (start.date < date && endsAtMidnightOfDate) return null
    val startMinute = if (start.date < date) 0 else start.hour * 60 + start.minute
    val endMinute = if (end.date > date) MINUTES_PER_DAY else end.hour * 60 + end.minute
    val displayEnd = maxOf(endMinute, (startMinute + minDurationMinutes).coerceAtMost(MINUTES_PER_DAY))
    return ClippedMinutes(startMinute, endMinute, displayEnd)
}

private fun <T> packCluster(
    date: LocalDate,
    cluster: List<Interval<T>>,
    out: MutableList<PositionedEvent<T>>,
) {
    // Greedy lane packing: each column remembers the visual end of its last event.
    val columnEnds = mutableListOf<Int>()
    for (interval in cluster) {
        val free = columnEnds.indexOfFirst { it <= interval.minutes.start }
        if (free >= 0) {
            interval.column = free
            columnEnds[free] = interval.minutes.displayEnd
        } else {
            interval.column = columnEnds.size
            columnEnds.add(interval.minutes.displayEnd)
        }
    }
    val totalColumns = columnEnds.size
    for (interval in cluster) {
        var span = 1
        while (interval.column + span < totalColumns && cluster.isColumnFree(interval.column + span, interval)) {
            span++
        }
        out.add(
            PositionedEvent(
                event = interval.event,
                date = date,
                startMinute = interval.minutes.start,
                endMinute = interval.minutes.end,
                displayEndMinute = interval.minutes.displayEnd,
                columnIndex = interval.column,
                columnCount = totalColumns,
                leftFraction = interval.column.toFloat() / totalColumns,
                widthFraction = span.toFloat() / totalColumns,
            ),
        )
    }
}

private fun List<Interval<*>>.isColumnFree(
    column: Int,
    during: Interval<*>,
): Boolean = none { it.column == column && it.overlaps(during) }

private fun Interval<*>.overlaps(other: Interval<*>): Boolean =
    minutes.start < other.minutes.displayEnd && other.minutes.start < minutes.displayEnd
