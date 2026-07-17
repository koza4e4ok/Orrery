package me.kozakov.orrery.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.CalendarDay
import me.kozakov.orrery.core.CalendarMonth
import me.kozakov.orrery.core.CalendarPages
import me.kozakov.orrery.core.CalendarWeek
import me.kozakov.orrery.core.daysOfWeek
import me.kozakov.orrery.core.monthGrid

/** Horizontally paged month calendar (one month per page, snap paging). */
@Composable
public fun HorizontalCalendar(
    state: CalendarState = rememberCalendarState(),
    modifier: Modifier = Modifier,
    userScrollEnabled: Boolean = true,
    keyboardNavigation: Boolean = true,
    showWeekNumbers: Boolean = false,
    weekNumber: @Composable (CalendarWeek) -> Unit = { CalendarDefaults.WeekNumber(it) },
    animateHeight: Boolean = true,
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
    // Natural (unconstrained) height in px per month page. The pager tracks
    // the visible page's height so months with different week counts resize
    // instead of the list jumping to its tallest page.
    val pageHeights = remember(state) { mutableStateMapOf<Int, Int>() }
    val targetHeightPx by remember(state, animateHeight) {
        derivedStateOf {
            if (!animateHeight) return@derivedStateOf null
            val info = state.listState.layoutInfo
            val visible = info.visibleItemsInfo
            if (visible.isEmpty()) return@derivedStateOf null
            val start = info.viewportStartOffset
            val current = visible.lastOrNull { it.offset <= start } ?: visible.first()
            val currentHeight = pageHeights[current.index] ?: return@derivedStateOf null
            val next = visible.firstOrNull { it.index == current.index + 1 }
            if (next == null) {
                currentHeight.toFloat()
            } else {
                val nextHeight = pageHeights[next.index] ?: currentHeight
                val pageWidth = (next.offset - current.offset).toFloat().coerceAtLeast(1f)
                val fraction = ((start - current.offset).toFloat() / pageWidth).coerceIn(0f, 1f)
                currentHeight + (nextHeight - currentHeight) * fraction
            }
        }
    }
    // Track the target 1:1 while a drag/fling is in flight (finger-following),
    // then spring to the settled page height so the pager and everything below
    // it in the layout glide into place instead of snapping.
    val animatedHeight = remember(state) { Animatable(Float.NaN) }
    val scrolling = state.listState.isScrollInProgress
    LaunchedEffect(targetHeightPx, scrolling) {
        val target = targetHeightPx ?: return@LaunchedEffect
        when {
            animatedHeight.value.isNaN() || scrolling -> {
                animatedHeight.snapTo(target)
            }

            animatedHeight.value != target -> {
                animatedHeight.animateTo(
                    target,
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                )
            }
        }
    }
    val density = LocalDensity.current
    Column(modifier) {
        weekHeader?.invoke(this, daysOfWeek(state.firstDayOfWeek))
        val pagerModifier =
            if (animateHeight && !animatedHeight.value.isNaN()) {
                Modifier.height(with(density) { animatedHeight.value.toDp() }).clipToBounds()
            } else {
                Modifier
            }
        Box(pagerModifier) {
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
                        modifier =
                            Modifier
                                .fillParentMaxWidth()
                                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                                .onSizeChanged { if (it.height > 0) pageHeights[index] = it.height },
                        weekNumber = if (showWeekNumbers) weekNumber else null,
                        dragSelection = dragSelection,
                        hapticsEnabled = hapticsEnabled,
                        keyboardNavigation = keyboard,
                    )
                }
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
    weekNumber: @Composable (CalendarWeek) -> Unit = { CalendarDefaults.WeekNumber(it) },
    stickyMonthHeaders: Boolean = false,
    dragSelection: CalendarSelectionState? = null,
    hapticsEnabled: Boolean = true,
    weekHeader: (@Composable ColumnScope.(List<DayOfWeek>) -> Unit)? = {
        CalendarDefaults.WeekHeader(it, leadingSpacer = showWeekNumbers)
    },
    monthHeader: (@Composable ColumnScope.(CalendarMonth) -> Unit)? = { CalendarDefaults.MonthTitle(it) },
    dayContent: @Composable BoxScope.(CalendarDay) -> Unit,
) {
    val weekNumberSlot: (@Composable (CalendarWeek) -> Unit)? =
        if (showWeekNumbers) weekNumber else null
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
                            weekNumber = weekNumberSlot,
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
                        weekNumber = weekNumberSlot,
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
