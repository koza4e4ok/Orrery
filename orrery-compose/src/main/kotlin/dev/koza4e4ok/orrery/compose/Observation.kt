package dev.koza4e4ok.orrery.compose

import androidx.compose.runtime.snapshotFlow
import dev.koza4e4ok.orrery.core.CalendarWeek
import dev.koza4e4ok.orrery.core.Selection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.YearMonth

/** Emits the first visible month, deduplicated; collect outside composition. */
public fun CalendarState.visibleMonths(): Flow<YearMonth> = snapshotFlow { firstVisibleMonth }.distinctUntilChanged()

/** Emits the first visible week, deduplicated; collect outside composition. */
public fun WeekCalendarState.visibleWeeks(): Flow<CalendarWeek> =
    snapshotFlow {
        firstVisibleWeek
    }.distinctUntilChanged()

/** Emits every selection change, deduplicated; collect outside composition. */
public fun CalendarSelectionState.selectionChanges(): Flow<Selection> =
    snapshotFlow {
        selection
    }.distinctUntilChanged()
