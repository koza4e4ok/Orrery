package me.kozakov.orrery.compose

import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.CalendarWeek
import me.kozakov.orrery.core.Selection

/**
 * What brought the current first visible page of a calendar into view; see
 * [CalendarState.lastScrollSource], [WeekCalendarState.lastScrollSource] and
 * [YearCalendarState.lastScrollSource].
 */
public enum class ScrollSource {
    /** The page the state was created (or restored) with; nothing has scrolled yet. */
    Initial,

    /** A pointer drag or fling by the user. */
    UserDrag,

    /** A `scrollTo*` / `animateScrollTo*` call (including keyboard navigation and range updates). */
    Programmatic,
}

/** A visible-month change emitted by [visibleMonthChanges], with the [source] that caused it. */
public data class VisibleMonthChange(
    public val month: YearMonth,
    public val source: ScrollSource,
)

/** A visible-week change emitted by [visibleWeekChanges], with the [source] that caused it. */
public data class VisibleWeekChange(
    public val week: CalendarWeek,
    public val source: ScrollSource,
)

/** Emits the first visible month, deduplicated; collect outside composition. */
public fun CalendarState.visibleMonths(): Flow<YearMonth> = snapshotFlow { firstVisibleMonth }.distinctUntilChanged()

/** Emits the first visible week, deduplicated; collect outside composition. */
public fun WeekCalendarState.visibleWeeks(): Flow<CalendarWeek> =
    snapshotFlow {
        firstVisibleWeek
    }.distinctUntilChanged()

/** Emits the visible page of week rows, deduplicated; collect outside composition. */
public fun WeekCalendarState.visiblePages(): Flow<List<CalendarWeek>> =
    snapshotFlow {
        firstVisibleWeeks
    }.distinctUntilChanged()

/**
 * Emits the first visible month together with the [ScrollSource] that brought
 * it into view (`Initial` first), deduplicated by month; collect outside composition.
 */
public fun CalendarState.visibleMonthChanges(): Flow<VisibleMonthChange> =
    snapshotFlow { VisibleMonthChange(firstVisibleMonth, lastScrollSource) }
        .distinctUntilChanged { old, new -> old.month == new.month }

/**
 * Emits the first visible week together with the [ScrollSource] that brought
 * it into view (`Initial` first), deduplicated by week; collect outside composition.
 */
public fun WeekCalendarState.visibleWeekChanges(): Flow<VisibleWeekChange> =
    snapshotFlow { VisibleWeekChange(firstVisibleWeek, lastScrollSource) }
        .distinctUntilChanged { old, new -> old.week == new.week }

/**
 * Emits the dates a lazy loader should have ready for this month pager:
 * [CalendarState.visibleDateRange] widened by [prefetchMonths] whole months on
 * each side (`0` = exactly the visible grids), clamped to
 * [CalendarState.startMonth]..[CalendarState.endMonth] but never narrower than
 * the visible range. The first value is emitted immediately; later values only
 * when the range changes (including mid-swipe, when a second month enters the
 * viewport). Not debounced: apply `debounce` downstream if loads are costly.
 * Collect outside composition.
 *
 * @throws IllegalArgumentException when [prefetchMonths] is negative.
 */
public fun CalendarState.visibleDateRangeFlow(prefetchMonths: Int = 1): Flow<ClosedRange<LocalDate>> {
    require(prefetchMonths >= 0) { "prefetchMonths ($prefetchMonths) must be >= 0" }
    return snapshotFlow { prefetchedDateRange(prefetchMonths) }.distinctUntilChanged()
}

/**
 * Emits the dates a lazy loader should have ready for this week strip:
 * [WeekCalendarState.visibleDateRange] widened by [prefetchWeeks] week rows on
 * each side (`0` = exactly the visible pages), clamped to the weeks containing
 * [WeekCalendarState.startDate] and [WeekCalendarState.endDate] but never
 * narrower than the visible range. Emits immediately, then on every change;
 * collect outside composition.
 *
 * @throws IllegalArgumentException when [prefetchWeeks] is negative.
 */
public fun WeekCalendarState.visibleDateRangeFlow(prefetchWeeks: Int = 1): Flow<ClosedRange<LocalDate>> {
    require(prefetchWeeks >= 0) { "prefetchWeeks ($prefetchWeeks) must be >= 0" }
    return snapshotFlow { prefetchedDateRange(prefetchWeeks) }.distinctUntilChanged()
}

/**
 * Emits the dates a lazy loader should have ready for a
 * [CollapsibleCalendarScaffold] driven by this state, [calendarState] and
 * [weekState] (pass the same states as to the scaffold):
 *
 * - [CollapsibleCalendarAnchor.Week] (`expandFraction == 0`): the week strip's
 *   page widened by [prefetchWeeks] week rows, as for [WeekCalendarState].
 * - Up to [CollapsibleCalendarAnchor.TwoWeeks]: the anchor week row plus its
 *   neighbour, widened by [prefetchWeeks] week rows.
 * - Above TwoWeeks: the month pager's range widened by [prefetchMonths], as
 *   for [CalendarState].
 *
 * While the calendar is dragged the larger range of the two stops it is
 * between is reported, so partly visible rows stay loaded; a settle animation
 * emits at most once per stop crossed. The range shrinks on collapse: keep
 * cached data instead of evicting it on a smaller range. Collect outside
 * composition.
 *
 * @throws IllegalArgumentException when [prefetchMonths] or [prefetchWeeks] is negative.
 */
public fun CollapsibleCalendarState.visibleDateRangeFlow(
    calendarState: CalendarState,
    weekState: WeekCalendarState,
    prefetchMonths: Int = 1,
    prefetchWeeks: Int = 1,
): Flow<ClosedRange<LocalDate>> {
    require(prefetchMonths >= 0) { "prefetchMonths ($prefetchMonths) must be >= 0" }
    require(prefetchWeeks >= 0) { "prefetchWeeks ($prefetchWeeks) must be >= 0" }
    return snapshotFlow {
        prefetchedDateRange(calendarState, weekState, prefetchMonths, prefetchWeeks)
    }.distinctUntilChanged()
}

/** Emits every selection change, deduplicated; collect outside composition. */
public fun CalendarSelectionState.selectionChanges(): Flow<Selection> =
    snapshotFlow {
        selection
    }.distinctUntilChanged()
