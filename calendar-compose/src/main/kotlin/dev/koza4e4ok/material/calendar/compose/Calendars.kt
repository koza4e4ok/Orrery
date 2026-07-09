package dev.koza4e4ok.material.calendar.compose

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.koza4e4ok.material.calendar.core.CalendarDay
import dev.koza4e4ok.material.calendar.core.CalendarMonth
import dev.koza4e4ok.material.calendar.core.CalendarPages
import dev.koza4e4ok.material.calendar.core.daysOfWeek
import dev.koza4e4ok.material.calendar.core.monthGrid
import kotlinx.datetime.DayOfWeek

/** Horizontally paged month calendar (one month per page, snap paging). */
@Composable
public fun HorizontalCalendar(
    state: CalendarState = rememberCalendarState(),
    modifier: Modifier = Modifier,
    userScrollEnabled: Boolean = true,
    weekHeader: (@Composable ColumnScope.(List<DayOfWeek>) -> Unit)? = { CalendarDefaults.WeekHeader(it) },
    monthHeader: (@Composable ColumnScope.(CalendarMonth) -> Unit)? = null,
    dayContent: @Composable BoxScope.(CalendarDay) -> Unit,
) {
    Column(modifier) {
        weekHeader?.invoke(this, daysOfWeek(state.firstDayOfWeek))
        LazyRow(
            state = state.listState,
            flingBehavior = rememberSnapFlingBehavior(state.listState),
            userScrollEnabled = userScrollEnabled,
        ) {
            items(count = state.monthCount, key = { it }) { index ->
                MonthContent(
                    month = rememberMonth(state, index),
                    monthHeader = monthHeader,
                    dayContent = dayContent,
                    modifier = Modifier.fillParentMaxWidth(),
                )
            }
        }
    }
}

/** Continuously scrolling vertical month list. */
@Composable
public fun VerticalCalendar(
    state: CalendarState = rememberCalendarState(),
    modifier: Modifier = Modifier,
    userScrollEnabled: Boolean = true,
    weekHeader: (@Composable ColumnScope.(List<DayOfWeek>) -> Unit)? = { CalendarDefaults.WeekHeader(it) },
    monthHeader: (@Composable ColumnScope.(CalendarMonth) -> Unit)? = { CalendarDefaults.MonthTitle(it) },
    dayContent: @Composable BoxScope.(CalendarDay) -> Unit,
) {
    Column(modifier) {
        weekHeader?.invoke(this, daysOfWeek(state.firstDayOfWeek))
        LazyColumn(
            state = state.listState,
            userScrollEnabled = userScrollEnabled,
        ) {
            items(count = state.monthCount, key = { it }) { index ->
                MonthContent(
                    month = rememberMonth(state, index),
                    monthHeader = monthHeader,
                    dayContent = dayContent,
                    modifier = Modifier.fillParentMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun rememberMonth(
    state: CalendarState,
    index: Int,
): CalendarMonth {
    val start = state.startMonth
    val firstDayOfWeek = state.firstDayOfWeek
    val outDateStyle = state.outDateStyle
    return remember(start, firstDayOfWeek, outDateStyle, index) {
        monthGrid(CalendarPages.monthAt(start, index), firstDayOfWeek, outDateStyle)
    }
}
