package dev.koza4e4ok.material.calendar.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import dev.koza4e4ok.material.calendar.core.CalendarDay
import dev.koza4e4ok.material.calendar.core.CalendarMonth
import dev.koza4e4ok.material.calendar.core.CalendarPages
import dev.koza4e4ok.material.calendar.core.CalendarWeek
import dev.koza4e4ok.material.calendar.core.daysOfWeek
import dev.koza4e4ok.material.calendar.core.monthGrid
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.YearMonth

/** Horizontally paged month calendar (one month per page, snap paging). */
@Composable
public fun HorizontalCalendar(
    state: CalendarState = rememberCalendarState(),
    modifier: Modifier = Modifier,
    userScrollEnabled: Boolean = true,
    keyboardNavigation: Boolean = true,
    showWeekNumbers: Boolean = false,
    dragSelection: CalendarSelectionState? = null,
    hapticsEnabled: Boolean = true,
    weekHeader: (@Composable ColumnScope.(List<DayOfWeek>) -> Unit)? = {
        CalendarDefaults.WeekHeader(it, leadingSpacer = showWeekNumbers)
    },
    monthHeader: (@Composable ColumnScope.(CalendarMonth) -> Unit)? = null,
    dayContent: @Composable BoxScope.(CalendarDay) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val keyboard =
        if (keyboardNavigation) {
            remember(state, scope) {
                CalendarKeyboardNavigation(scope) { date ->
                    state.animateScrollToMonth(YearMonth(date.year, date.month))
                }
            }
        } else {
            null
        }
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
                    dragSelection = dragSelection,
                    hapticsEnabled = hapticsEnabled,
                    keyboardNavigation = keyboard,
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
    dragSelection: CalendarSelectionState? = null,
    hapticsEnabled: Boolean = true,
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
                            dragSelection = dragSelection,
                            hapticsEnabled = hapticsEnabled,
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
                        dragSelection = dragSelection,
                        hapticsEnabled = hapticsEnabled,
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
    keyboardNavigation: Boolean = true,
    weekHeader: (@Composable ColumnScope.(List<DayOfWeek>) -> Unit)? = { CalendarDefaults.WeekHeader(it) },
    dayContent: @Composable BoxScope.(CalendarDay) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val keyboard =
        if (keyboardNavigation) {
            remember(state, scope) {
                CalendarKeyboardNavigation(scope) { date -> state.animateScrollToDate(date) }
            }
        } else {
            null
        }
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
                    week.days.forEachIndexed { col, day ->
                        DayCell(
                            day = day,
                            col = col,
                            keyboard = keyboard,
                            modifier = Modifier.weight(1f),
                            dayContent = dayContent,
                        )
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
