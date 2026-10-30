@file:Suppress("TooManyFunctions")

package me.kozakov.orrery.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.Constraints
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
    HorizontalCalendarContent(
        state = state,
        modifier = modifier,
        userScrollEnabled = userScrollEnabled,
        keyboardNavigation = keyboardNavigation,
        showWeekNumbers = showWeekNumbers,
        weekNumber = weekNumber,
        animateHeight = animateHeight,
        dragSelection = dragSelection,
        hapticsEnabled = hapticsEnabled,
        visibleDaysOfWeek = state.visibleDaysOfWeek,
        rowHeight = CalendarRowHeight.WrapContent,
        weekHeader = weekHeader,
        monthHeader = monthHeader,
        slots = CalendarSlots(),
        dayContent = dayContent,
    )
}

/**
 * Horizontally paged month calendar (one month per page, snap paging) with
 * configurable [visibleDaysOfWeek] (for example a Mon–Fri work week) and
 * [rowHeight] ([CalendarRowHeight.WrapContent], [CalendarRowHeight.Fixed] or
 * [CalendarRowHeight.FillViewport]).
 */
@Suppress("LongParameterList")
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
    visibleDaysOfWeek: Set<DayOfWeek> = state.visibleDaysOfWeek,
    rowHeight: CalendarRowHeight = CalendarRowHeight.WrapContent,
    weekHeader: (@Composable ColumnScope.(List<DayOfWeek>) -> Unit)? = {
        CalendarDefaults.WeekHeader(it, leadingSpacer = showWeekNumbers)
    },
    monthHeader: (@Composable ColumnScope.(CalendarMonth) -> Unit)? = null,
    dayContent: @Composable BoxScope.(CalendarDay) -> Unit,
) {
    HorizontalCalendarContent(
        state = state,
        modifier = modifier,
        userScrollEnabled = userScrollEnabled,
        keyboardNavigation = keyboardNavigation,
        showWeekNumbers = showWeekNumbers,
        weekNumber = weekNumber,
        animateHeight = animateHeight,
        dragSelection = dragSelection,
        hapticsEnabled = hapticsEnabled,
        visibleDaysOfWeek = visibleDaysOfWeek,
        rowHeight = rowHeight,
        weekHeader = weekHeader,
        monthHeader = monthHeader,
        slots = CalendarSlots(),
        dayContent = dayContent,
    )
}

/**
 * Horizontally paged month calendar (one month per page, snap paging) with
 * per-week and per-month slots.
 *
 * @param weekOverlay Drawn over each week row's day columns (for example
 *   [WeekEventBanners]); the row grows when the overlay is taller.
 * @param weekFooter Drawn below each week row.
 * @param monthFooter Drawn below each month's grid.
 */
@Suppress("LongParameterList")
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
    weekOverlay: (@Composable BoxScope.(CalendarWeek) -> Unit)? = null,
    weekFooter: (@Composable ColumnScope.(CalendarWeek) -> Unit)? = null,
    monthFooter: (@Composable ColumnScope.(CalendarMonth) -> Unit)? = null,
    dayContent: @Composable BoxScope.(CalendarDay) -> Unit,
) {
    HorizontalCalendarContent(
        state = state,
        modifier = modifier,
        userScrollEnabled = userScrollEnabled,
        keyboardNavigation = keyboardNavigation,
        showWeekNumbers = showWeekNumbers,
        weekNumber = weekNumber,
        animateHeight = animateHeight,
        dragSelection = dragSelection,
        hapticsEnabled = hapticsEnabled,
        visibleDaysOfWeek = state.visibleDaysOfWeek,
        rowHeight = CalendarRowHeight.WrapContent,
        weekHeader = weekHeader,
        monthHeader = monthHeader,
        slots = CalendarSlots(weekOverlay, weekFooter, monthFooter),
        dayContent = dayContent,
    )
}

/** Optional per-week and per-month slots of the month and week calendars. */
private class CalendarSlots(
    val weekOverlay: (@Composable BoxScope.(CalendarWeek) -> Unit)? = null,
    val weekFooter: (@Composable ColumnScope.(CalendarWeek) -> Unit)? = null,
    val monthFooter: (@Composable ColumnScope.(CalendarMonth) -> Unit)? = null,
)

@Suppress("LongParameterList")
@Composable
private fun HorizontalCalendarContent(
    state: CalendarState,
    modifier: Modifier,
    userScrollEnabled: Boolean,
    keyboardNavigation: Boolean,
    showWeekNumbers: Boolean,
    weekNumber: @Composable (CalendarWeek) -> Unit,
    animateHeight: Boolean,
    dragSelection: CalendarSelectionState?,
    hapticsEnabled: Boolean,
    visibleDaysOfWeek: Set<DayOfWeek>,
    rowHeight: CalendarRowHeight,
    weekHeader: (@Composable ColumnScope.(List<DayOfWeek>) -> Unit)?,
    monthHeader: (@Composable ColumnScope.(CalendarMonth) -> Unit)?,
    slots: CalendarSlots,
    dayContent: @Composable BoxScope.(CalendarDay) -> Unit,
) {
    require(visibleDaysOfWeek.isNotEmpty()) { "visibleDaysOfWeek must not be empty" }
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
    val isFillViewport = rowHeight is CalendarRowHeight.FillViewport
    val effectiveAnimateHeight = animateHeight && !isFillViewport
    // Natural (unconstrained) height in px per month page. The pager tracks
    // the visible page's height so months with different week counts resize
    // instead of the list jumping to its tallest page.
    val pageHeights = remember(state) { mutableStateMapOf<Int, Int>() }
    val targetHeightPx by remember(state, effectiveAnimateHeight) {
        derivedStateOf {
            if (!effectiveAnimateHeight) return@derivedStateOf null
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
        weekHeader?.invoke(this, daysOfWeek(state.firstDayOfWeek, visibleDaysOfWeek))
        val pagerModifier =
            when {
                isFillViewport -> Modifier.weight(1f, fill = false)
                effectiveAnimateHeight && !animatedHeight.value.isNaN() ->
                    Modifier.height(with(density) { animatedHeight.value.toDp() }).clipToBounds()
                else -> Modifier
            }
        BoxWithConstraints(pagerModifier) {
            val hasBoundedHeight = constraints.maxHeight != Constraints.Infinity
            val fillBoundedHeight = isFillViewport && hasBoundedHeight
            LazyRow(
                state = state.listState,
                modifier = if (fillBoundedHeight) Modifier.fillMaxSize() else Modifier,
                flingBehavior = rememberSnapFlingBehavior(state.listState),
                userScrollEnabled = userScrollEnabled,
            ) {
                items(count = state.monthCount, key = { it }) { index ->
                    val pageModifier =
                        if (fillBoundedHeight) {
                            Modifier.fillParentMaxWidth().fillParentMaxHeight()
                        } else {
                            Modifier
                                .fillParentMaxWidth()
                                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                                .onSizeChanged { if (it.height > 0) pageHeights[index] = it.height }
                        }
                    MonthContent(
                        month = rememberMonth(state, index, visibleDaysOfWeek),
                        monthHeader = monthHeader,
                        dayContent = dayContent,
                        modifier = pageModifier,
                        weekNumber = if (showWeekNumbers) weekNumber else null,
                        dragSelection = dragSelection,
                        hapticsEnabled = hapticsEnabled,
                        keyboardNavigation = keyboard,
                        rowHeight = rowHeight,
                        fillBoundedHeight = fillBoundedHeight,
                        visibleDaysOfWeek = visibleDaysOfWeek,
                        weekOverlay = slots.weekOverlay,
                        weekFooter = slots.weekFooter,
                        monthFooter = slots.monthFooter,
                    )
                }
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
    VerticalCalendarContent(
        state = state,
        modifier = modifier,
        userScrollEnabled = userScrollEnabled,
        showWeekNumbers = showWeekNumbers,
        weekNumber = weekNumber,
        stickyMonthHeaders = stickyMonthHeaders,
        dragSelection = dragSelection,
        hapticsEnabled = hapticsEnabled,
        visibleDaysOfWeek = state.visibleDaysOfWeek,
        rowHeight = CalendarRowHeight.WrapContent,
        weekHeader = weekHeader,
        monthHeader = monthHeader,
        slots = CalendarSlots(),
        dayContent = dayContent,
    )
}

/**
 * Continuously scrolling vertical month list with configurable
 * [visibleDaysOfWeek] (for example a Mon–Fri work week) and [rowHeight].
 */
@Suppress("LongParameterList")
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
    visibleDaysOfWeek: Set<DayOfWeek> = state.visibleDaysOfWeek,
    rowHeight: CalendarRowHeight = CalendarRowHeight.WrapContent,
    weekHeader: (@Composable ColumnScope.(List<DayOfWeek>) -> Unit)? = {
        CalendarDefaults.WeekHeader(it, leadingSpacer = showWeekNumbers)
    },
    monthHeader: (@Composable ColumnScope.(CalendarMonth) -> Unit)? = { CalendarDefaults.MonthTitle(it) },
    dayContent: @Composable BoxScope.(CalendarDay) -> Unit,
) {
    VerticalCalendarContent(
        state = state,
        modifier = modifier,
        userScrollEnabled = userScrollEnabled,
        showWeekNumbers = showWeekNumbers,
        weekNumber = weekNumber,
        stickyMonthHeaders = stickyMonthHeaders,
        dragSelection = dragSelection,
        hapticsEnabled = hapticsEnabled,
        visibleDaysOfWeek = visibleDaysOfWeek,
        rowHeight = rowHeight,
        weekHeader = weekHeader,
        monthHeader = monthHeader,
        slots = CalendarSlots(),
        dayContent = dayContent,
    )
}

/**
 * Continuously scrolling vertical month list with per-week and per-month slots.
 *
 * @param weekOverlay Drawn over each week row's day columns (for example
 *   [WeekEventBanners]); the row grows when the overlay is taller.
 * @param weekFooter Drawn below each week row.
 * @param monthFooter Drawn below each month's grid (also with sticky headers).
 */
@Suppress("LongParameterList")
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
    weekOverlay: (@Composable BoxScope.(CalendarWeek) -> Unit)? = null,
    weekFooter: (@Composable ColumnScope.(CalendarWeek) -> Unit)? = null,
    monthFooter: (@Composable ColumnScope.(CalendarMonth) -> Unit)? = null,
    dayContent: @Composable BoxScope.(CalendarDay) -> Unit,
) {
    VerticalCalendarContent(
        state = state,
        modifier = modifier,
        userScrollEnabled = userScrollEnabled,
        showWeekNumbers = showWeekNumbers,
        weekNumber = weekNumber,
        stickyMonthHeaders = stickyMonthHeaders,
        dragSelection = dragSelection,
        hapticsEnabled = hapticsEnabled,
        visibleDaysOfWeek = state.visibleDaysOfWeek,
        rowHeight = CalendarRowHeight.WrapContent,
        weekHeader = weekHeader,
        monthHeader = monthHeader,
        slots = CalendarSlots(weekOverlay, weekFooter, monthFooter),
        dayContent = dayContent,
    )
}

@Suppress("LongParameterList")
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VerticalCalendarContent(
    state: CalendarState,
    modifier: Modifier,
    userScrollEnabled: Boolean,
    showWeekNumbers: Boolean,
    weekNumber: @Composable (CalendarWeek) -> Unit,
    stickyMonthHeaders: Boolean,
    dragSelection: CalendarSelectionState?,
    hapticsEnabled: Boolean,
    visibleDaysOfWeek: Set<DayOfWeek>,
    rowHeight: CalendarRowHeight,
    weekHeader: (@Composable ColumnScope.(List<DayOfWeek>) -> Unit)?,
    monthHeader: (@Composable ColumnScope.(CalendarMonth) -> Unit)?,
    slots: CalendarSlots,
    dayContent: @Composable BoxScope.(CalendarDay) -> Unit,
) {
    require(visibleDaysOfWeek.isNotEmpty()) { "visibleDaysOfWeek must not be empty" }
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
        weekHeader?.invoke(this, daysOfWeek(state.firstDayOfWeek, visibleDaysOfWeek))
        BoxWithConstraints {
            val effectiveRowHeight =
                if (rowHeight is CalendarRowHeight.FillViewport) {
                    val target =
                        if (constraints.maxHeight != Constraints.Infinity) {
                            (maxHeight / 6).coerceAtLeast(rowHeight.minRowHeight)
                        } else {
                            rowHeight.minRowHeight
                        }
                    CalendarRowHeight.Fixed(target)
                } else {
                    rowHeight
                }
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
                                monthHeader?.invoke(this, rememberMonth(state, index, visibleDaysOfWeek))
                            }
                        }
                        item(key = index) {
                            MonthContent(
                                month = rememberMonth(state, index, visibleDaysOfWeek),
                                monthHeader = null,
                                dayContent = dayContent,
                                modifier = Modifier.fillParentMaxWidth(),
                                weekNumber = weekNumberSlot,
                                dragSelection = dragSelection,
                                hapticsEnabled = hapticsEnabled,
                                rowHeight = effectiveRowHeight,
                                visibleDaysOfWeek = visibleDaysOfWeek,
                                weekOverlay = slots.weekOverlay,
                                weekFooter = slots.weekFooter,
                                monthFooter = slots.monthFooter,
                            )
                        }
                    }
                } else {
                    items(count = state.monthCount, key = { it }) { index ->
                        MonthContent(
                            month = rememberMonth(state, index, visibleDaysOfWeek),
                            monthHeader = monthHeader,
                            dayContent = dayContent,
                            modifier = Modifier.fillParentMaxWidth(),
                            weekNumber = weekNumberSlot,
                            dragSelection = dragSelection,
                            hapticsEnabled = hapticsEnabled,
                            rowHeight = effectiveRowHeight,
                            visibleDaysOfWeek = visibleDaysOfWeek,
                            weekOverlay = slots.weekOverlay,
                            weekFooter = slots.weekFooter,
                            monthFooter = slots.monthFooter,
                        )
                    }
                }
            }
        }
    }
}

/** Horizontally paged single-week (or multi-week) calendar. */
@Composable
public fun WeekCalendar(
    state: WeekCalendarState,
    modifier: Modifier = Modifier,
    userScrollEnabled: Boolean = true,
    keyboardNavigation: Boolean = true,
    weekHeader: (@Composable ColumnScope.(List<DayOfWeek>) -> Unit)? = { CalendarDefaults.WeekHeader(it) },
    dayContent: @Composable BoxScope.(CalendarDay) -> Unit,
) {
    WeekCalendarContent(
        state = state,
        modifier = modifier,
        userScrollEnabled = userScrollEnabled,
        keyboardNavigation = keyboardNavigation,
        visibleWeeksCount = state.visibleWeeksCount,
        visibleDaysOfWeek = state.visibleDaysOfWeek,
        weekHeader = weekHeader,
        slots = CalendarSlots(),
        dayContent = dayContent,
    )
}

/**
 * Horizontally paged week or multi-week calendar configured with
 * [visibleWeeksCount] (`1..4`, e.g. `2` for a two-week calendar view) and
 * [visibleDaysOfWeek] (e.g. Monday–Friday work week).
 */
@Suppress("LongParameterList")
@Composable
public fun WeekCalendar(
    state: WeekCalendarState,
    modifier: Modifier = Modifier,
    userScrollEnabled: Boolean = true,
    keyboardNavigation: Boolean = true,
    visibleWeeksCount: Int = state.visibleWeeksCount,
    visibleDaysOfWeek: Set<DayOfWeek> = state.visibleDaysOfWeek,
    weekHeader: (@Composable ColumnScope.(List<DayOfWeek>) -> Unit)? = { CalendarDefaults.WeekHeader(it) },
    dayContent: @Composable BoxScope.(CalendarDay) -> Unit,
) {
    WeekCalendarContent(
        state = state,
        modifier = modifier,
        userScrollEnabled = userScrollEnabled,
        keyboardNavigation = keyboardNavigation,
        visibleWeeksCount = visibleWeeksCount,
        visibleDaysOfWeek = visibleDaysOfWeek,
        weekHeader = weekHeader,
        slots = CalendarSlots(),
        dayContent = dayContent,
    )
}

/**
 * Horizontally paged single-week calendar with per-week slots.
 *
 * @param weekOverlay Drawn over the week's day columns (for example
 *   [WeekEventBanners]); the row grows when the overlay is taller.
 * @param weekFooter Drawn below the week row.
 */
@Suppress("LongParameterList")
@Composable
public fun WeekCalendar(
    state: WeekCalendarState,
    modifier: Modifier = Modifier,
    userScrollEnabled: Boolean = true,
    keyboardNavigation: Boolean = true,
    weekHeader: (@Composable ColumnScope.(List<DayOfWeek>) -> Unit)? = { CalendarDefaults.WeekHeader(it) },
    weekOverlay: (@Composable BoxScope.(CalendarWeek) -> Unit)? = null,
    weekFooter: (@Composable ColumnScope.(CalendarWeek) -> Unit)? = null,
    dayContent: @Composable BoxScope.(CalendarDay) -> Unit,
) {
    WeekCalendarContent(
        state = state,
        modifier = modifier,
        userScrollEnabled = userScrollEnabled,
        keyboardNavigation = keyboardNavigation,
        visibleWeeksCount = state.visibleWeeksCount,
        visibleDaysOfWeek = state.visibleDaysOfWeek,
        weekHeader = weekHeader,
        slots = CalendarSlots(weekOverlay, weekFooter),
        dayContent = dayContent,
    )
}

@Suppress("LongParameterList")
@Composable
private fun WeekCalendarContent(
    state: WeekCalendarState,
    modifier: Modifier,
    userScrollEnabled: Boolean,
    keyboardNavigation: Boolean,
    visibleWeeksCount: Int,
    visibleDaysOfWeek: Set<DayOfWeek>,
    weekHeader: (@Composable ColumnScope.(List<DayOfWeek>) -> Unit)?,
    slots: CalendarSlots,
    dayContent: @Composable BoxScope.(CalendarDay) -> Unit,
) {
    require(visibleWeeksCount in 1..4) { "visibleWeeksCount ($visibleWeeksCount) must be in 1..4" }
    require(visibleDaysOfWeek.isNotEmpty()) { "visibleDaysOfWeek must not be empty" }
    LaunchedEffect(visibleWeeksCount, visibleDaysOfWeek) {
        if (state.visibleWeeksCount != visibleWeeksCount) {
            state.visibleWeeksCount = visibleWeeksCount
        }
        if (state.visibleDaysOfWeek != visibleDaysOfWeek) {
            state.visibleDaysOfWeek = visibleDaysOfWeek
        }
    }
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
        weekHeader?.invoke(this, daysOfWeek(state.firstDayOfWeek, visibleDaysOfWeek))
        val pageCount = state.pageCount(visibleWeeksCount)
        LazyRow(
            state = state.listState,
            flingBehavior = rememberSnapFlingBehavior(state.listState),
            userScrollEnabled = userScrollEnabled,
        ) {
            items(count = pageCount, key = { it }) { index ->
                val weeks = state.weeksAt(index, visibleWeeksCount, visibleDaysOfWeek)
                val rowCount = weeks.size
                val weekFooter = slots.weekFooter
                Column(Modifier.fillParentMaxWidth()) {
                    weeks.forEachIndexed { row, week ->
                        Row(Modifier.fillMaxWidth()) {
                            val colCount = week.days.size
                            val rowFocusRequesters = rememberRowFocusRequesters(keyboard != null, colCount)
                            WeekDays(week, slots.weekOverlay) {
                                week.days.forEachIndexed { col, day ->
                                    DayCell(
                                        day = day,
                                        col = col,
                                        colCount = colCount,
                                        keyboard = keyboard,
                                        modifier = Modifier.weight(1f),
                                        dayContent = dayContent,
                                        row = row,
                                        rowCount = rowCount,
                                        rowFocusRequesters = rowFocusRequesters,
                                        visibleDaysOfWeek = visibleDaysOfWeek,
                                    )
                                }
                            }
                        }
                        weekFooter?.invoke(this, week)
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
    visibleDaysOfWeek: Set<DayOfWeek> = state.visibleDaysOfWeek,
): CalendarMonth {
    val start = state.effectiveStartMonth
    val firstDayOfWeek = state.firstDayOfWeek
    val outDateStyle = state.outDateStyle
    return remember(start, firstDayOfWeek, outDateStyle, visibleDaysOfWeek, index) {
        monthGrid(
            yearMonth = CalendarPages.monthAt(start, index),
            firstDayOfWeek = firstDayOfWeek,
            outDateStyle = outDateStyle,
            visibleDaysOfWeek = visibleDaysOfWeek,
        )
    }
}
