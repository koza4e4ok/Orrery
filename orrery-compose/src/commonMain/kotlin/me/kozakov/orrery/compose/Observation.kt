package me.kozakov.orrery.compose

import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.CalendarWeek
import me.kozakov.orrery.core.Selection

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

/** Emits every selection change, deduplicated; collect outside composition. */
public fun CalendarSelectionState.selectionChanges(): Flow<Selection> =
    snapshotFlow {
        selection
    }.distinctUntilChanged()
