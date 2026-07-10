package dev.koza4e4ok.material.calendar.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.koza4e4ok.material.calendar.core.CalendarDay
import dev.koza4e4ok.material.calendar.core.CalendarMonth
import dev.koza4e4ok.material.calendar.core.CalendarPages
import dev.koza4e4ok.material.calendar.core.CalendarWeek
import dev.koza4e4ok.material.calendar.core.daysOfWeek
import dev.koza4e4ok.material.calendar.core.monthGrid
import kotlinx.datetime.DayOfWeek

/** Horizontally paged month calendar (one month per page, snap paging). */
@Composable
public fun HorizontalCalendar(
    state: CalendarState = rememberCalendarState(),
    modifier: Modifier = Modifier,
    userScrollEnabled: Boolean = true,
    showWeekNumbers: Boolean = false,
    weekHeader: (@Composable ColumnScope.(List<DayOfWeek>) -> Unit)? = {
        CalendarDefaults.WeekHeader(it, leadingSpacer = showWeekNumbers)
    },
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
                    weekNumber =
                        if (showWeekNumbers) {
                            { CalendarDefaults.WeekNumber(it) }
                        } else {
                            null
                        },
                )
            }
        }
    }
}

/** Continuously scrolling vertical month list. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
public fun VerticalCalendar(
    state: CalendarState = rememberCalendarState(),
    modifier: Modifier = Modifier,
    userScrollEnabled: Boolean = true,
    showWeekNumbers: Boolean = false,
    stickyMonthHeaders: Boolean = false,
    weekHeader: (@Composable ColumnScope.(List<DayOfWeek>) -> Unit)? = {
        CalendarDefaults.WeekHeader(it, leadingSpacer = showWeekNumbers)
    },
    monthHeader: (@Composable ColumnScope.(CalendarMonth) -> Unit)? = { CalendarDefaults.MonthTitle(it) },
    dayContent: @Composable BoxScope.(CalendarDay) -> Unit,
) {
    val weekNumber: (@Composable (CalendarWeek) -> Unit)? =
        if (showWeekNumbers) {
            { CalendarDefaults.WeekNumber(it) }
        } else {
            null
        }
    val sticky = stickyMonthHeaders && monthHeader != null
    LaunchedEffect(sticky) {
        val targetItemsPerMonth = if (sticky) 2 else 1
        if (state.itemsPerMonth != targetItemsPerMonth) {
            val visible = state.firstVisibleMonth
            state.itemsPerMonth = targetItemsPerMonth
            state.scrollToMonth(visible)
        }
    }
    Column(modifier) {
        weekHeader?.invoke(this, daysOfWeek(state.firstDayOfWeek))
        LazyColumn(
            state = state.listState,
            userScrollEnabled = userScrollEnabled,
        ) {
            if (sticky) {
                for (index in 0 until state.monthCount) {
                    stickyHeader(key = "header-$index") {
                        Column(
                            Modifier
                                .fillParentMaxWidth()
                                .background(MaterialTheme.colorScheme.surface),
                        ) {
                            monthHeader?.invoke(this, rememberMonth(state, index))
                        }
                    }
                    item(key = index) {
                        MonthContent(
                            month = rememberMonth(state, index),
                            monthHeader = null,
                            dayContent = dayContent,
                            modifier = Modifier.fillParentMaxWidth(),
                            weekNumber = weekNumber,
                        )
                    }
                }
            } else {
                items(count = state.monthCount, key = { it }) { index ->
                    MonthContent(
                        month = rememberMonth(state, index),
                        monthHeader = monthHeader,
                        dayContent = dayContent,
                        modifier = Modifier.fillParentMaxWidth(),
                        weekNumber = weekNumber,
                    )
                }
            }
        }
    }
}

/** Horizontally paged single-week calendar. */
@Composable
public fun WeekCalendar(
    state: WeekCalendarState,
    modifier: Modifier = Modifier,
    userScrollEnabled: Boolean = true,
    weekHeader: (@Composable ColumnScope.(List<DayOfWeek>) -> Unit)? = { CalendarDefaults.WeekHeader(it) },
    dayContent: @Composable BoxScope.(CalendarDay) -> Unit,
) {
    Column(modifier) {
        weekHeader?.invoke(this, daysOfWeek(state.firstDayOfWeek))
        LazyRow(
            state = state.listState,
            flingBehavior = rememberSnapFlingBehavior(state.listState),
            userScrollEnabled = userScrollEnabled,
        ) {
            items(count = state.weekCount, key = { it }) { index ->
                val week = state.weekAt(index)
                Row(Modifier.fillParentMaxWidth()) {
                    week.days.forEach { day ->
                        Box(Modifier.weight(1f)) { dayContent(day) }
                    }
                }
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
