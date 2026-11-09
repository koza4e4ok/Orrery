package me.kozakov.orrery.compose

import androidx.compose.foundation.lazy.LazyListState
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.CalendarPages
import me.kozakov.orrery.core.OutDateStyle

/**
 * Indices of the items that have at least one pixel inside the viewport, or
 * null before the first layout. A stale layout (one that no longer contains
 * [LazyListState.firstVisibleItemIndex], e.g. a list that scrolled while it
 * was not composed) is ignored too, so callers fall back to the first
 * visible index instead of reporting pages that are no longer there.
 */
internal fun LazyListState.visibleIndexSpan(): IntRange? {
    val info = layoutInfo
    val start = info.viewportStartOffset
    val end = info.viewportEndOffset
    var min = Int.MAX_VALUE
    var max = Int.MIN_VALUE
    for (item in info.visibleItemsInfo) {
        if (item.size > 0 && item.offset + item.size > start && item.offset < end) {
            min = minOf(min, item.index)
            max = maxOf(max, item.index)
        }
    }
    return if (min <= max && firstVisibleItemIndex in min..max) min..max else null
}

/** [this] shifted by [days] days, via epoch days (no calendar math per step). */
internal fun LocalDate.shiftDays(days: Int): LocalDate = LocalDate.fromEpochDays(toEpochDays() + days)

/** First date a month page paints: the in-dates of its first row, or the 1st for [OutDateStyle.None]. */
internal fun monthGridStart(
    month: YearMonth,
    firstDayOfWeek: DayOfWeek,
    outDateStyle: OutDateStyle,
): LocalDate =
    if (outDateStyle == OutDateStyle.None) {
        month.firstDay
    } else {
        CalendarPages.weekStart(month.firstDay, firstDayOfWeek)
    }

/** Last date a month page paints: end of its last row, end of the 6-row grid, or the month's last day. */
internal fun monthGridEnd(
    month: YearMonth,
    firstDayOfWeek: DayOfWeek,
    outDateStyle: OutDateStyle,
): LocalDate =
    when (outDateStyle) {
        OutDateStyle.None -> month.lastDay
        OutDateStyle.EndOfRow -> CalendarPages.weekStart(month.lastDay, firstDayOfWeek).shiftDays(6)
        OutDateStyle.EndOfGrid -> CalendarPages.weekStart(month.firstDay, firstDayOfWeek).shiftDays(6 * 7 - 1)
    }

/**
 * Widens [exact] to [wantedStart]..[wantedEnd], clamping the widening to
 * [minBound]/[maxBound] (null = unbounded side). The result is never smaller
 * than [exact].
 */
internal fun widenWithin(
    exact: ClosedRange<LocalDate>,
    wantedStart: LocalDate,
    wantedEnd: LocalDate,
    minBound: LocalDate?,
    maxBound: LocalDate?,
): ClosedRange<LocalDate> {
    val start = if (minBound != null && wantedStart < minBound) minBound else wantedStart
    val end = if (maxBound != null && wantedEnd > maxBound) maxBound else wantedEnd
    return minOf(exact.start, start)..maxOf(exact.endInclusive, end)
}

/**
 * The range [visibleDateRangeFlow] emits for a [CollapsibleCalendarScaffold]:
 * the week strip at [CollapsibleCalendarAnchor.Week], the anchor week plus its
 * neighbour up to [CollapsibleCalendarAnchor.TwoWeeks], the month pager above.
 */
internal fun CollapsibleCalendarState.prefetchedDateRange(
    calendarState: CalendarState,
    weekState: WeekCalendarState,
    prefetchMonths: Int,
    prefetchWeeks: Int,
): ClosedRange<LocalDate> {
    val fraction = expandFraction
    val twoWeeks = CollapsibleCalendarAnchor.TwoWeeks.expandFraction
    return when {
        fraction > twoWeeks -> calendarState.prefetchedDateRange(prefetchMonths)
        fraction <= 0f -> weekState.prefetchedDateRange(prefetchWeeks)
        else -> twoWeekDateRange(calendarState, weekState, prefetchWeeks)
    }
}

/**
 * The two month-grid rows the scaffold shows between Week and TwoWeeks: the
 * anchor row and the next one, or the previous one when the anchor is the
 * grid's last row (mirrors CollapsibleGeometry's two-week top row).
 */
private fun twoWeekDateRange(
    calendarState: CalendarState,
    weekState: WeekCalendarState,
    prefetchWeeks: Int,
): ClosedRange<LocalDate> {
    val month = calendarState.firstVisibleMonth
    val firstDayOfWeek = calendarState.firstDayOfWeek
    val gridStart = CalendarPages.weekStart(month.firstDay, firstDayOfWeek)
    val leading = (month.firstDay.toEpochDays() - gridStart.toEpochDays()).toInt()
    val rows =
        if (calendarState.outDateStyle == OutDateStyle.EndOfGrid) 6 else (leading + month.numberOfDays + 6) / 7
    val anchorStart = CalendarPages.weekStart(weekState.firstVisibleWeekStart, firstDayOfWeek)
    val anchorOffset = (anchorStart.toEpochDays() - gridStart.toEpochDays()).toInt()
    val anchorRow = if (anchorOffset in 0 until rows * 7) anchorOffset / 7 else 0
    val topRow = anchorRow.coerceAtMost(rows - 2).coerceAtLeast(0)
    val exactStart = gridStart.shiftDays(topRow * 7)
    val exact = exactStart..exactStart.shiftDays(minOf(2, rows) * 7 - 1)
    if (prefetchWeeks == 0) return exact
    return widenWithin(
        exact = exact,
        wantedStart = exact.start.shiftDays(-7 * prefetchWeeks),
        wantedEnd = exact.endInclusive.shiftDays(7 * prefetchWeeks),
        minBound = calendarState.startMonth?.firstDay,
        maxBound = calendarState.endMonth?.lastDay,
    )
}
