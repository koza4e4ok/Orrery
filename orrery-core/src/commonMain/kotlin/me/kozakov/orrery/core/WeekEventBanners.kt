package me.kozakov.orrery.core

import kotlinx.datetime.LocalDate

/**
 * One multi-day event placed on a week row by [packWeekEventBanners].
 *
 * Columns index into [WeekBannerPackResult.weekDates]; a banner covers
 * [startColumn]..[endColumnInclusive] in lane [laneIndex] (0 = topmost).
 *
 * @property event The packed event.
 * @property startColumn First covered column (0-based).
 * @property endColumnInclusive Last covered column (0-based, inclusive).
 * @property laneIndex Horizontal lane, `0 until maxVisibleLanes`.
 * @property continuesFromPreviousWeek True when the event starts before the
 *   first date of the week (the banner is clipped on its start side).
 * @property continuesIntoNextWeek True when the event ends after the last
 *   date of the week (the banner is clipped on its end side).
 */
public data class WeekEventBanner<out T>(
    public val event: CalendarEvent<T>,
    public val startColumn: Int,
    public val endColumnInclusive: Int,
    public val laneIndex: Int,
    public val continuesFromPreviousWeek: Boolean,
    public val continuesIntoNextWeek: Boolean,
) {
    /** Number of columns the banner covers. */
    public val columnSpan: Int get() = endColumnInclusive - startColumn + 1
}

/**
 * Result of [packWeekEventBanners] for one week row.
 *
 * @property weekDates The dates of the row's columns, in column order.
 * @property banners Banners that fit in the visible lanes, in packing order.
 * @property overflowCountByColumn For each column of [weekDates], how many
 *   covering events did not fit in the visible lanes (shown as `+N`).
 */
public data class WeekBannerPackResult<out T>(
    public val weekDates: List<LocalDate>,
    public val banners: List<WeekEventBanner<T>>,
    public val overflowCountByColumn: List<Int>,
)

/**
 * Packs the [events] that overlap [weekDates] into horizontal lanes for a
 * Google Calendar-style banner row.
 *
 * Events are placed greedily, in this order: earlier start date first, then
 * longer span (in days) first, then [CalendarEvent.title], then
 * [CalendarEvent.id]. Each event takes the lowest lane in
 * `0 until maxVisibleLanes` that is free across all of its columns. An event
 * that fits in no lane adds one to [WeekBannerPackResult.overflowCountByColumn]
 * for every column it covers.
 *
 * Both [EventSpan.AllDay] and [EventSpan.Timed] events are packed by the dates
 * they touch. An event covers column `i` when `weekDates[i]` lies within its
 * start..end dates, so [weekDates] may skip days (for example a work week);
 * events that fall only on skipped days are left out.
 *
 * @param weekDates Dates of the week row's columns, strictly ascending.
 * @param events Candidate events; ones outside [weekDates] are ignored.
 * @param maxVisibleLanes Number of banner lanes to fill; must be >= 0.
 */
public fun <T> packWeekEventBanners(
    weekDates: List<LocalDate>,
    events: List<CalendarEvent<T>>,
    maxVisibleLanes: Int = 3,
): WeekBannerPackResult<T> {
    require(maxVisibleLanes >= 0) { "maxVisibleLanes ($maxVisibleLanes) must not be negative" }
    require(weekDates.zipWithNext().all { (a, b) -> a < b }) { "weekDates must be strictly ascending" }
    if (weekDates.isEmpty()) return WeekBannerPackResult(weekDates, emptyList(), emptyList())

    val firstDate = weekDates.first()
    val lastDate = weekDates.last()
    val candidates =
        events
            .mapNotNull { event -> event.columnsIn(weekDates)?.let { columns -> PackCandidate(event, columns) } }
            .sortedWith(PackingOrder)

    // Last occupied column per lane. Candidates arrive in non-decreasing start
    // column order, so a lane is free for a banner when it ends before the banner starts.
    val laneEnds = IntArray(maxVisibleLanes) { -1 }
    val overflow = IntArray(weekDates.size)
    val banners = ArrayList<WeekEventBanner<T>>(candidates.size)
    for ((event, columns) in candidates) {
        val lane = laneEnds.indexOfFirst { it < columns.first }
        if (lane < 0) {
            for (column in columns) overflow[column]++
        } else {
            laneEnds[lane] = columns.last
            banners +=
                WeekEventBanner(
                    event = event,
                    startColumn = columns.first,
                    endColumnInclusive = columns.last,
                    laneIndex = lane,
                    continuesFromPreviousWeek = event.span.startDate < firstDate,
                    continuesIntoNextWeek = event.span.endDate > lastDate,
                )
        }
    }
    return WeekBannerPackResult(weekDates, banners, overflow.toList())
}

private data class PackCandidate<out T>(
    val event: CalendarEvent<T>,
    val columns: IntRange,
)

/** Columns of [weekDates] this event covers, or null when it covers none. */
private fun CalendarEvent<*>.columnsIn(weekDates: List<LocalDate>): IntRange? {
    val start = weekDates.indexOfFirst { it >= span.startDate }
    val end = weekDates.indexOfLast { it <= span.endDate }
    return if (start < 0 || end < start) null else start..end
}

private val PackingOrder: Comparator<PackCandidate<*>> =
    compareBy<PackCandidate<*>> { it.event.span.startDate }
        .thenByDescending { it.event.span.endDate.toEpochDays() - it.event.span.startDate.toEpochDays() }
        .thenBy { it.event.title }
        .thenBy { it.event.id }
