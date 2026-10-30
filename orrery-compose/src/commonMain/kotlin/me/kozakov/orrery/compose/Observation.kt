package me.kozakov.orrery.compose

import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
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

/** Emits every selection change, deduplicated; collect outside composition. */
public fun CalendarSelectionState.selectionChanges(): Flow<Selection> =
    snapshotFlow {
        selection
    }.distinctUntilChanged()
