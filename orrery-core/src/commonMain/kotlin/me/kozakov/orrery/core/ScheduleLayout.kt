package me.kozakov.orrery.core

import kotlinx.datetime.LocalDate

private const val MINUTES_PER_DAY: Int = 24 * 60
private const val HOURS_PER_DAY: Int = 24
private const val MAX_STACKED_LEFT_FRACTION: Float = 0.75f

/**
 * How [layoutDaySchedule] arranges timed events that overlap in time.
 */
public sealed interface EventOverlapMode {
    /**
     * Overlapping events share the column width in parallel lanes and widen to
     * the right across lanes that are free for their whole span (the default).
     */
    public data object SideBySide : EventOverlapMode

    /**
     * Overlapping events cascade: lane `n` starts at `n * laneIndentFraction` of
     * the column width (capped at 75%) and extends to the right edge, so later
     * events partially cover earlier ones.
     *
     * @property laneIndentFraction Horizontal indent per lane in `0f..1f` (default 15%).
     */
    public data class Stacked(
        public val laneIndentFraction: Float = 0.15f,
    ) : EventOverlapMode {
        init {
            require(laneIndentFraction in 0f..1f) {
                "laneIndentFraction ($laneIndentFraction) must be in 0..1"
            }
        }
    }
}

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
): DayScheduleLayout<T> =
    layoutDayScheduleImpl(date, events, minDurationMinutes, 0, HOURS_PER_DAY, EventOverlapMode.SideBySide)

/**
 * Computes [DayScheduleLayout] for [events] occurring on [date] inside the
 * visible `minHour:00..maxHour:00` window.
 *
 * Timed events are clipped to the window; events entirely before [minHour] or
 * starting at or after [maxHour] are left out (an event starting exactly at
 * `minHour:00` is kept even when it has zero length). [PositionedEvent.startMinute],
 * [PositionedEvent.endMinute] and [PositionedEvent.displayEndMinute] stay
 * minutes from midnight, within `minHour * 60..maxHour * 60`.
 *
 * Overlapping events are grouped into connected clusters and packed greedily
 * into the lowest free lane. [overlapMode] then decides the horizontal geometry:
 * [EventOverlapMode.SideBySide] splits the width between lanes (as the
 * three-argument overload does), while [EventOverlapMode.Stacked] indents lane `n`
 * by `n * laneIndentFraction` (at most 75%) and lets every event reach the right edge.
 *
 * @param minDurationMinutes Minimum visual height in minutes (default 15 minutes).
 * @param minHour First visible hour, `0..23` (default 0).
 * @param maxHour End of the visible window (exclusive hour), `minHour + 1..24` (default 24).
 * @param overlapMode Horizontal arrangement of overlapping events (default [EventOverlapMode.SideBySide]).
 */
@Suppress("LongParameterList")
public fun <T> layoutDaySchedule(
    date: LocalDate,
    events: Iterable<CalendarEvent<T>>,
    minDurationMinutes: Int = 15,
    minHour: Int = 0,
    maxHour: Int = HOURS_PER_DAY,
    overlapMode: EventOverlapMode = EventOverlapMode.SideBySide,
): DayScheduleLayout<T> = layoutDayScheduleImpl(date, events, minDurationMinutes, minHour, maxHour, overlapMode)

@Suppress("LongParameterList")
private fun <T> layoutDayScheduleImpl(
    date: LocalDate,
    events: Iterable<CalendarEvent<T>>,
    minDurationMinutes: Int,
    minHour: Int,
    maxHour: Int,
    overlapMode: EventOverlapMode,
): DayScheduleLayout<T> {
    require(minDurationMinutes >= 0) { "minDurationMinutes ($minDurationMinutes) must not be negative" }
    require(minHour in 0 until HOURS_PER_DAY) { "minHour ($minHour) must be in 0..23" }
    require(maxHour in minHour + 1..HOURS_PER_DAY) { "maxHour ($maxHour) must be in ${minHour + 1}..24" }
    val window = VisibleWindow(minHour * 60, maxHour * 60)
    val allDay = mutableListOf<CalendarEvent<T>>()
    val timed = mutableListOf<Interval<T>>()
    for (event in events) {
        if (!event.occursOn(date)) continue
        when (val span = event.span) {
            is EventSpan.AllDay -> allDay.add(event)
            is EventSpan.Timed -> span.clipTo(date, minDurationMinutes, window)?.let { timed.add(Interval(event, it)) }
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
            packCluster(date, timed.subList(clusterStart, index), overlapMode, positioned)
            clusterStart = index
            clusterMaxEnd = Int.MIN_VALUE
        }
        clusterMaxEnd = maxOf(clusterMaxEnd, interval.minutes.displayEnd)
    }
    if (timed.isNotEmpty()) {
        packCluster(date, timed.subList(clusterStart, timed.size), overlapMode, positioned)
    }
    return DayScheduleLayout(date = date, allDayEvents = allDay, timedEvents = positioned)
}

private class VisibleWindow(
    val start: Int,
    val end: Int,
)

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
    window: VisibleWindow,
): ClippedMinutes? {
    // An overnight event ending exactly at midnight does not reach into [date].
    val endsAtMidnightOfDate = end.date == date && end.hour == 0 && end.minute == 0
    if (start.date < date && endsAtMidnightOfDate) return null
    val dayStart = if (start.date < date) 0 else start.hour * 60 + start.minute
    val dayEnd = if (end.date > date) MINUTES_PER_DAY else end.hour * 60 + end.minute
    // Entirely outside the visible window: ends by minHour, or starts at or after maxHour.
    if (dayStart >= window.end || (dayEnd <= window.start && dayStart < window.start)) return null
    val startMinute = dayStart.coerceIn(window.start, window.end)
    val endMinute = dayEnd.coerceIn(startMinute, window.end)
    val displayEnd = maxOf(endMinute, (startMinute + minDurationMinutes).coerceAtMost(window.end))
    return ClippedMinutes(startMinute, endMinute, displayEnd)
}

private fun <T> packCluster(
    date: LocalDate,
    cluster: List<Interval<T>>,
    overlapMode: EventOverlapMode,
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
        val leftFraction: Float
        val widthFraction: Float
        when (overlapMode) {
            is EventOverlapMode.Stacked -> {
                leftFraction =
                    (interval.column * overlapMode.laneIndentFraction).coerceAtMost(MAX_STACKED_LEFT_FRACTION)
                widthFraction = 1f - leftFraction
            }
            EventOverlapMode.SideBySide -> {
                var span = 1
                while (interval.column + span < totalColumns &&
                    cluster.isColumnFree(interval.column + span, interval)
                ) {
                    span++
                }
                leftFraction = interval.column.toFloat() / totalColumns
                widthFraction = span.toFloat() / totalColumns
            }
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
                leftFraction = leftFraction,
                widthFraction = widthFraction,
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

/**
 * A multi-day all-day event placed across a row of day columns, as returned by
 * [packAllDayBanners].
 *
 * @property event Source [EventSpan.AllDay] event.
 * @property startColumn Index of the first column the banner covers.
 * @property endColumnInclusive Index of the last column the banner covers (`>= startColumn`).
 * @property laneIndex 0-based row in the all-day header; overlapping banners never share a lane.
 * @property clippedStart True when the event starts before the first covered column's date.
 * @property clippedEnd True when the event ends after the last covered column's date.
 */
public data class AllDayBanner<out T>(
    public val event: CalendarEvent<T>,
    public val startColumn: Int,
    public val endColumnInclusive: Int,
    public val laneIndex: Int,
    public val clippedStart: Boolean,
    public val clippedEnd: Boolean,
)

/**
 * Packs the [EventSpan.AllDay] events among [events] into banners spanning the
 * day columns [dates] (for example one page of a week schedule).
 *
 * Each all-day event covering at least one of [dates] yields one banner from its
 * first to its last covered column; timed events and events outside [dates] are
 * ignored, and repeated ids are kept once. Banners are ordered by start column,
 * longer spans first, then title and id, and each takes the lowest lane that is
 * free across all its columns. [dates] are expected in ascending order.
 */
public fun <T> packAllDayBanners(
    dates: List<LocalDate>,
    events: List<CalendarEvent<T>>,
): List<AllDayBanner<T>> {
    if (dates.isEmpty()) return emptyList()
    val placed = mutableListOf<AllDayBanner<T>>()
    val seen = HashSet<String>()
    for (event in events) {
        if (event.span !is EventSpan.AllDay || !seen.add(event.id)) continue
        val first = dates.indexOfFirst { event.occursOn(it) }
        if (first < 0) continue
        val last = dates.indexOfLast { event.occursOn(it) }
        placed.add(
            AllDayBanner(
                event = event,
                startColumn = first,
                endColumnInclusive = last,
                laneIndex = 0,
                clippedStart = event.span.startDate < dates[first],
                clippedEnd = event.span.endDate > dates[last],
            ),
        )
    }
    placed.sortWith(
        compareBy<AllDayBanner<T>>(
            { it.startColumn },
            { it.startColumn - it.endColumnInclusive },
            { it.event.title },
            { it.event.id },
        ),
    )
    // Greedy lane packing: each lane remembers the last column it occupies.
    val laneEnds = mutableListOf<Int>()
    return placed.map { banner ->
        var lane = laneEnds.indexOfFirst { it < banner.startColumn }
        if (lane < 0) {
            lane = laneEnds.size
            laneEnds.add(banner.endColumnInclusive)
        } else {
            laneEnds[lane] = banner.endColumnInclusive
        }
        banner.copy(laneIndex = lane)
    }
}
