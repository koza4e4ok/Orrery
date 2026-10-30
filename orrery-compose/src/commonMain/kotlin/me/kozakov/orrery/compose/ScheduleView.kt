@file:Suppress("TooManyFunctions")

package me.kozakov.orrery.compose

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import me.kozakov.orrery.core.AllDayBanner
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.DayScheduleLayout
import me.kozakov.orrery.core.EventOverlapMode
import me.kozakov.orrery.core.PositionedEvent
import me.kozakov.orrery.core.layoutDaySchedule
import me.kozakov.orrery.core.packAllDayBanners
import kotlin.math.roundToInt

/**
 * Horizontally paged single-day time-grid schedule view with an optional
 * all-day banner. Swiping moves one day; [state] holds the visible date, the
 * shared vertical scroll and the zoomable hour height.
 *
 * @param events Events for a date; called for each visible day, so index them
 *   up front (for example with `eventsByDate`).
 * @param currentTime Draws [nowIndicator] on [today]'s column when non-null.
 *   Pass it from your own clock so the view stays deterministic in tests.
 * @param userScrollEnabled Whether swiping between days is enabled. The time grid always scrolls vertically.
 * @param zoomGesturesEnabled Whether a two-finger pinch changes [ScheduleState.hourHeight].
 * @param onEmptySlotClick Called with the column's date and the tapped time, floored to
 *   the hour (to the half hour when [showHalfHourLines] is true).
 */
@Composable
public fun <T> DaySchedule(
    events: (LocalDate) -> List<CalendarEvent<T>>,
    modifier: Modifier = Modifier,
    state: ScheduleState = rememberScheduleState(),
    today: LocalDate = currentDate(),
    currentTime: LocalTime? = null,
    userScrollEnabled: Boolean = true,
    zoomGesturesEnabled: Boolean = true,
    showHalfHourLines: Boolean = false,
    minEventDurationMinutes: Int = 15,
    colors: ScheduleColors = ScheduleDefaults.colors(),
    dimensions: ScheduleDimensions = ScheduleDefaults.dimensions(),
    strings: ScheduleStrings = ScheduleDefaults.strings(),
    onEventClick: ((CalendarEvent<T>) -> Unit)? = null,
    onEmptySlotClick: ((LocalDate, LocalTime) -> Unit)? = null,
    dayHeader: (@Composable (LocalDate, Boolean) -> Unit)? = { date, isToday ->
        ScheduleDefaults.DayHeader(date, isToday, colors)
    },
    allDayEventContent: @Composable (CalendarEvent<T>) -> Unit = { event ->
        ScheduleDefaults.AllDayEventChip(event, colors, dimensions, onClick = onEventClick?.let { cb -> { cb(event) } })
    },
    hourLabel: @Composable (Int) -> Unit = { hour ->
        ScheduleDefaults.HourLabel(hour, colors)
    },
    nowIndicator: @Composable () -> Unit = {
        ScheduleDefaults.NowIndicator(colors, dimensions, contentDescription = strings.nowLabel)
    },
    eventContent: @Composable BoxScope.(PositionedEvent<T>) -> Unit = { positioned ->
        ScheduleDefaults.EventTile(
            positioned = positioned,
            colors = colors,
            dimensions = dimensions,
            onClick = onEventClick?.let { cb -> { cb(positioned.event) } },
        )
    },
) {
    PagedSchedule(
        daysPerPage = 1,
        events = events,
        modifier = modifier,
        state = state,
        options = ScheduleOptions(today, currentTime, userScrollEnabled, zoomGesturesEnabled, showHalfHourLines),
        minEventDurationMinutes = minEventDurationMinutes,
        overlapMode = EventOverlapMode.SideBySide,
        colors = colors,
        dimensions = dimensions,
        strings = strings,
        onEmptySlotClick = onEmptySlotClick,
        slots = ScheduleSlots(dayHeader, allDayEventContent, hourLabel, nowIndicator, eventContent),
    )
}

/**
 * Horizontally paged multi-day (default 7-day week) 24-hour schedule view.
 * Swiping moves a whole page of [daysPerPage] days. Seven-day pages start on
 * [ScheduleState.firstDayOfWeek]; other sizes are counted from the range start.
 * All-day events show as banners spanning the days they cover on the page.
 *
 * See [DaySchedule] for the shared parameters.
 */
@Composable
public fun <T> WeekSchedule(
    events: (LocalDate) -> List<CalendarEvent<T>>,
    modifier: Modifier = Modifier,
    state: ScheduleState = rememberScheduleState(),
    daysPerPage: Int = 7,
    today: LocalDate = currentDate(),
    currentTime: LocalTime? = null,
    userScrollEnabled: Boolean = true,
    zoomGesturesEnabled: Boolean = true,
    showHalfHourLines: Boolean = false,
    minEventDurationMinutes: Int = 15,
    colors: ScheduleColors = ScheduleDefaults.colors(),
    dimensions: ScheduleDimensions = ScheduleDefaults.dimensions(),
    strings: ScheduleStrings = ScheduleDefaults.strings(),
    onEventClick: ((CalendarEvent<T>) -> Unit)? = null,
    onEmptySlotClick: ((LocalDate, LocalTime) -> Unit)? = null,
    dayHeader: (@Composable (LocalDate, Boolean) -> Unit)? = { date, isToday ->
        ScheduleDefaults.DayHeader(date, isToday, colors)
    },
    allDayEventContent: @Composable (CalendarEvent<T>) -> Unit = { event ->
        ScheduleDefaults.AllDayEventChip(event, colors, dimensions, onClick = onEventClick?.let { cb -> { cb(event) } })
    },
    hourLabel: @Composable (Int) -> Unit = { hour ->
        ScheduleDefaults.HourLabel(hour, colors)
    },
    nowIndicator: @Composable () -> Unit = {
        ScheduleDefaults.NowIndicator(colors, dimensions, contentDescription = strings.nowLabel)
    },
    eventContent: @Composable BoxScope.(PositionedEvent<T>) -> Unit = { positioned ->
        ScheduleDefaults.EventTile(
            positioned = positioned,
            colors = colors,
            dimensions = dimensions,
            onClick = onEventClick?.let { cb -> { cb(positioned.event) } },
        )
    },
) {
    require(daysPerPage >= 1) { "daysPerPage ($daysPerPage) must be at least 1" }
    PagedSchedule(
        daysPerPage = daysPerPage,
        events = events,
        modifier = modifier,
        state = state,
        options = ScheduleOptions(today, currentTime, userScrollEnabled, zoomGesturesEnabled, showHalfHourLines),
        minEventDurationMinutes = minEventDurationMinutes,
        overlapMode = EventOverlapMode.SideBySide,
        colors = colors,
        dimensions = dimensions,
        strings = strings,
        onEmptySlotClick = onEmptySlotClick,
        slots = ScheduleSlots(dayHeader, allDayEventContent, hourLabel, nowIndicator, eventContent),
    )
}

/**
 * [DaySchedule] with a choice of [overlapMode] for overlapping timed events.
 * The visible hours come from [ScheduleState.minHour]..[ScheduleState.maxHour]
 * (see the `rememberScheduleState` overload taking `minHour` and `maxHour`).
 *
 * @param overlapMode [EventOverlapMode.SideBySide] (the default) splits the column
 *   between overlapping events; [EventOverlapMode.Stacked] cascades them with an indent.
 */
@Composable
public fun <T> DaySchedule(
    events: (LocalDate) -> List<CalendarEvent<T>>,
    modifier: Modifier = Modifier,
    state: ScheduleState = rememberScheduleState(),
    today: LocalDate = currentDate(),
    currentTime: LocalTime? = null,
    userScrollEnabled: Boolean = true,
    zoomGesturesEnabled: Boolean = true,
    showHalfHourLines: Boolean = false,
    minEventDurationMinutes: Int = 15,
    overlapMode: EventOverlapMode = EventOverlapMode.SideBySide,
    colors: ScheduleColors = ScheduleDefaults.colors(),
    dimensions: ScheduleDimensions = ScheduleDefaults.dimensions(),
    strings: ScheduleStrings = ScheduleDefaults.strings(),
    onEventClick: ((CalendarEvent<T>) -> Unit)? = null,
    onEmptySlotClick: ((LocalDate, LocalTime) -> Unit)? = null,
    dayHeader: (@Composable (LocalDate, Boolean) -> Unit)? = { date, isToday ->
        ScheduleDefaults.DayHeader(date, isToday, colors)
    },
    allDayEventContent: @Composable (CalendarEvent<T>) -> Unit = { event ->
        ScheduleDefaults.AllDayEventChip(event, colors, dimensions, onClick = onEventClick?.let { cb -> { cb(event) } })
    },
    hourLabel: @Composable (Int) -> Unit = { hour ->
        ScheduleDefaults.HourLabel(hour, colors)
    },
    nowIndicator: @Composable () -> Unit = {
        ScheduleDefaults.NowIndicator(colors, dimensions, contentDescription = strings.nowLabel)
    },
    eventContent: @Composable BoxScope.(PositionedEvent<T>) -> Unit = { positioned ->
        ScheduleDefaults.EventTile(
            positioned = positioned,
            colors = colors,
            dimensions = dimensions,
            onClick = onEventClick?.let { cb -> { cb(positioned.event) } },
        )
    },
) {
    PagedSchedule(
        daysPerPage = 1,
        events = events,
        modifier = modifier,
        state = state,
        options = ScheduleOptions(today, currentTime, userScrollEnabled, zoomGesturesEnabled, showHalfHourLines),
        minEventDurationMinutes = minEventDurationMinutes,
        overlapMode = overlapMode,
        colors = colors,
        dimensions = dimensions,
        strings = strings,
        onEmptySlotClick = onEmptySlotClick,
        slots = ScheduleSlots(dayHeader, allDayEventContent, hourLabel, nowIndicator, eventContent),
    )
}

/**
 * [WeekSchedule] with a choice of [overlapMode] for overlapping timed events.
 * The visible hours come from [ScheduleState.minHour]..[ScheduleState.maxHour]
 * (see the `rememberScheduleState` overload taking `minHour` and `maxHour`).
 *
 * @param overlapMode [EventOverlapMode.SideBySide] (the default) splits each column
 *   between overlapping events; [EventOverlapMode.Stacked] cascades them with an indent.
 */
@Composable
public fun <T> WeekSchedule(
    events: (LocalDate) -> List<CalendarEvent<T>>,
    modifier: Modifier = Modifier,
    state: ScheduleState = rememberScheduleState(),
    daysPerPage: Int = 7,
    today: LocalDate = currentDate(),
    currentTime: LocalTime? = null,
    userScrollEnabled: Boolean = true,
    zoomGesturesEnabled: Boolean = true,
    showHalfHourLines: Boolean = false,
    minEventDurationMinutes: Int = 15,
    overlapMode: EventOverlapMode = EventOverlapMode.SideBySide,
    colors: ScheduleColors = ScheduleDefaults.colors(),
    dimensions: ScheduleDimensions = ScheduleDefaults.dimensions(),
    strings: ScheduleStrings = ScheduleDefaults.strings(),
    onEventClick: ((CalendarEvent<T>) -> Unit)? = null,
    onEmptySlotClick: ((LocalDate, LocalTime) -> Unit)? = null,
    dayHeader: (@Composable (LocalDate, Boolean) -> Unit)? = { date, isToday ->
        ScheduleDefaults.DayHeader(date, isToday, colors)
    },
    allDayEventContent: @Composable (CalendarEvent<T>) -> Unit = { event ->
        ScheduleDefaults.AllDayEventChip(event, colors, dimensions, onClick = onEventClick?.let { cb -> { cb(event) } })
    },
    hourLabel: @Composable (Int) -> Unit = { hour ->
        ScheduleDefaults.HourLabel(hour, colors)
    },
    nowIndicator: @Composable () -> Unit = {
        ScheduleDefaults.NowIndicator(colors, dimensions, contentDescription = strings.nowLabel)
    },
    eventContent: @Composable BoxScope.(PositionedEvent<T>) -> Unit = { positioned ->
        ScheduleDefaults.EventTile(
            positioned = positioned,
            colors = colors,
            dimensions = dimensions,
            onClick = onEventClick?.let { cb -> { cb(positioned.event) } },
        )
    },
) {
    require(daysPerPage >= 1) { "daysPerPage ($daysPerPage) must be at least 1" }
    PagedSchedule(
        daysPerPage = daysPerPage,
        events = events,
        modifier = modifier,
        state = state,
        options = ScheduleOptions(today, currentTime, userScrollEnabled, zoomGesturesEnabled, showHalfHourLines),
        minEventDurationMinutes = minEventDurationMinutes,
        overlapMode = overlapMode,
        colors = colors,
        dimensions = dimensions,
        strings = strings,
        onEmptySlotClick = onEmptySlotClick,
        slots = ScheduleSlots(dayHeader, allDayEventContent, hourLabel, nowIndicator, eventContent),
    )
}

private class ScheduleOptions(
    val today: LocalDate,
    val currentTime: LocalTime?,
    val userScrollEnabled: Boolean,
    val zoomGesturesEnabled: Boolean,
    val showHalfHourLines: Boolean,
)

private class ScheduleSlots<T>(
    val dayHeader: (@Composable (LocalDate, Boolean) -> Unit)?,
    val allDayEventContent: @Composable (CalendarEvent<T>) -> Unit,
    val hourLabel: @Composable (Int) -> Unit,
    val nowIndicator: @Composable () -> Unit,
    val eventContent: @Composable BoxScope.(PositionedEvent<T>) -> Unit,
)

@Composable
private fun <T> PagedSchedule(
    daysPerPage: Int,
    events: (LocalDate) -> List<CalendarEvent<T>>,
    modifier: Modifier,
    state: ScheduleState,
    options: ScheduleOptions,
    minEventDurationMinutes: Int,
    overlapMode: EventOverlapMode,
    colors: ScheduleColors,
    dimensions: ScheduleDimensions,
    strings: ScheduleStrings,
    onEmptySlotClick: ((LocalDate, LocalTime) -> Unit)?,
    slots: ScheduleSlots<T>,
) {
    val startDate = state.effectiveStartDate
    val endDate = state.effectiveEndDate
    val firstDayOfWeek = state.firstDayOfWeek
    val paging =
        remember(startDate, endDate, firstDayOfWeek, daysPerPage) {
            SchedulePaging.create(startDate, endDate, firstDayOfWeek, daysPerPage)
        }
    val pagerState =
        remember(state, paging) {
            PagerState(currentPage = paging.pageOf(state.firstVisibleDate)) { paging.pageCount }
        }
    DisposableEffect(state, pagerState, paging) {
        state.attach(pagerState, paging)
        onDispose { state.detach(pagerState) }
    }
    LaunchedEffect(state, pagerState, paging) {
        snapshotFlow { pagerState.settledPage }.collect { page -> state.onPageSettled(paging, page) }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        userScrollEnabled = options.userScrollEnabled,
        verticalAlignment = Alignment.Top,
    ) { page ->
        val dates = remember(paging, page) { paging.datesAt(page) }
        val layouts =
            dates.map { date ->
                val dayEvents = events(date)
                remember(date, dayEvents, minEventDurationMinutes, state.minHour, state.maxHour, overlapMode) {
                    layoutDaySchedule(
                        date = date,
                        events = dayEvents,
                        minDurationMinutes = minEventDurationMinutes,
                        minHour = state.minHour,
                        maxHour = state.maxHour,
                        overlapMode = overlapMode,
                    )
                }
            }
        SchedulePage(
            layouts = layouts,
            state = state,
            options = options,
            colors = colors,
            dimensions = dimensions,
            strings = strings,
            onEmptySlotClick = onEmptySlotClick,
            slots = slots,
        )
    }
}

@Composable
private fun <T> SchedulePage(
    layouts: List<DayScheduleLayout<T>>,
    state: ScheduleState,
    options: ScheduleOptions,
    colors: ScheduleColors,
    dimensions: ScheduleDimensions,
    strings: ScheduleStrings,
    onEmptySlotClick: ((LocalDate, LocalTime) -> Unit)?,
    slots: ScheduleSlots<T>,
) {
    val hourHeight = state.hourHeight
    val window = HourWindow(state.minHour, state.maxHour)
    val dayHeader = slots.dayHeader
    val banners =
        remember(layouts) {
            packAllDayBanners(layouts.map { it.date }, layouts.flatMap { it.allDayEvents })
        }
    val showAllDay = banners.isNotEmpty()
    Column(Modifier.fillMaxSize()) {
        if (dayHeader != null || showAllDay) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        val y = size.height - 0.5.dp.toPx()
                        drawLine(colors.gridLineColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                    },
            ) {
                if (dayHeader != null) {
                    Row(Modifier.fillMaxWidth()) {
                        Spacer(Modifier.width(dimensions.timeGutterWidth))
                        for (layout in layouts) {
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                dayHeader(layout.date, layout.date == options.today)
                            }
                        }
                    }
                }
                if (showAllDay) {
                    AllDayRow(banners, layouts.size, colors, dimensions, strings, slots.allDayEventContent)
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .pinchToZoomHours(state, options.zoomGesturesEnabled)
                .verticalScroll(state.verticalScrollState),
        ) {
            Row(Modifier.fillMaxWidth().height(hourHeight * window.hourCount)) {
                HourGutter(hourHeight, window, dimensions.timeGutterWidth, slots.hourLabel)
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .gridLines(layouts.size, hourHeight, window.hourCount, options.showHalfHourLines, colors),
                ) {
                    for (layout in layouts) {
                        key(layout.date) {
                            DayColumn(
                                layout = layout,
                                nowMinute =
                                    options.currentTime
                                        ?.takeIf { layout.date == options.today }
                                        ?.let { it.hour * 60 + it.minute }
                                        ?.takeIf { it in window.startMinute..window.endMinute },
                                hourHeight = hourHeight,
                                window = window,
                                dimensions = dimensions,
                                slotMinutes = if (options.showHalfHourLines) 30 else 60,
                                onEmptySlotClick = onEmptySlotClick,
                                nowIndicator = slots.nowIndicator,
                                eventContent = slots.eventContent,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Visible `startHour:00..endHour:00` slice of the day drawn by the time grid. */
private class HourWindow(
    val startHour: Int,
    val endHour: Int,
) {
    val hourCount: Int get() = endHour - startHour
    val startMinute: Int get() = startHour * 60
    val endMinute: Int get() = endHour * 60
}

/**
 * All-day header: [banners] span the columns they cover, one lane per row. When
 * there are more lanes than [ScheduleDimensions.maxVisibleAllDayRows], the last
 * row instead shows a "+N" count of the hidden banners under each column.
 */
@Composable
private fun <T> AllDayRow(
    banners: List<AllDayBanner<T>>,
    columnCount: Int,
    colors: ScheduleColors,
    dimensions: ScheduleDimensions,
    strings: ScheduleStrings,
    allDayEventContent: @Composable (CalendarEvent<T>) -> Unit,
) {
    val maxRows = dimensions.maxVisibleAllDayRows.coerceAtLeast(1)
    val laneCount = (banners.maxOfOrNull { it.laneIndex } ?: -1) + 1
    val visibleLanes = if (laneCount <= maxRows) laneCount else maxRows - 1
    val visible = banners.filter { it.laneIndex < visibleLanes }
    val hiddenPerColumn = IntArray(columnCount)
    for (banner in banners) {
        if (banner.laneIndex < visibleLanes) continue
        for (column in banner.startColumn..banner.endColumnInclusive) hiddenPerColumn[column]++
    }
    val overflowColumns = (0 until columnCount).filter { hiddenPerColumn[it] > 0 }
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = strings.allDayLabel,
            modifier = Modifier.width(dimensions.timeGutterWidth).padding(end = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = colors.hourLabelColor,
            textAlign = TextAlign.End,
            maxLines = 2,
        )
        Layout(
            content = {
                for (banner in visible) {
                    key(banner.event.id) {
                        Box(propagateMinConstraints = true) { allDayEventContent(banner.event) }
                    }
                }
                for (column in overflowColumns) {
                    Text(
                        text = "+${hiddenPerColumn[column]}",
                        modifier = Modifier.padding(horizontal = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.hourLabelColor,
                        maxLines = 1,
                    )
                }
            },
            modifier = Modifier.weight(1f),
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            val columnWidth = width.toFloat() / columnCount
            val inset = 1.dp.roundToPx()
            val rowGap = 2.dp.roundToPx()
            val rowHeight = dimensions.allDayChipHeight.roundToPx()
            val rows = visibleLanes + if (overflowColumns.isEmpty()) 0 else 1
            val height = (rows * rowHeight + (rows - 1) * rowGap).coerceAtLeast(0)
            val placeables =
                measurables.mapIndexed { index, measurable ->
                    if (index < visible.size) {
                        val banner = visible[index]
                        val span = banner.endColumnInclusive - banner.startColumn + 1
                        val bannerWidth = (columnWidth * span).roundToInt() - 2 * inset
                        measurable.measure(Constraints.fixed(bannerWidth.coerceAtLeast(0), rowHeight))
                    } else {
                        val maxWidth = (columnWidth.roundToInt() - 2 * inset).coerceAtLeast(0)
                        measurable.measure(
                            Constraints(maxWidth = maxWidth, minHeight = rowHeight, maxHeight = rowHeight),
                        )
                    }
                }
            layout(width, height) {
                placeables.forEachIndexed { index, placeable ->
                    if (index < visible.size) {
                        val banner = visible[index]
                        val x = (columnWidth * banner.startColumn).roundToInt() + inset
                        placeable.placeRelative(x, banner.laneIndex * (rowHeight + rowGap))
                    } else {
                        val column = overflowColumns[index - visible.size]
                        val x = (columnWidth * column).roundToInt() + inset
                        placeable.placeRelative(x, visibleLanes * (rowHeight + rowGap))
                    }
                }
            }
        }
    }
}

@Composable
private fun HourGutter(
    hourHeight: Dp,
    window: HourWindow,
    width: Dp,
    hourLabel: @Composable (Int) -> Unit,
) {
    Layout(
        content = {
            for (hour in window.startHour until window.endHour) {
                Box(contentAlignment = Alignment.TopEnd) { hourLabel(hour) }
            }
        },
        modifier = Modifier.width(width).fillMaxHeight(),
    ) { measurables, constraints ->
        val hourPx = hourHeight.toPx()
        val labelConstraints = Constraints(minWidth = constraints.maxWidth, maxWidth = constraints.maxWidth)
        val placeables = measurables.map { it.measure(labelConstraints) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { row, placeable ->
                // Center each label on its hour line, kept inside the gutter at both ends.
                val maxY = (constraints.maxHeight - placeable.height).coerceAtLeast(0)
                val y = (row * hourPx - placeable.height / 2f).roundToInt().coerceIn(0, maxY)
                placeable.placeRelative(0, y)
            }
        }
    }
}

@Composable
private fun <T> DayColumn(
    layout: DayScheduleLayout<T>,
    nowMinute: Int?,
    hourHeight: Dp,
    window: HourWindow,
    dimensions: ScheduleDimensions,
    slotMinutes: Int,
    onEmptySlotClick: ((LocalDate, LocalTime) -> Unit)?,
    nowIndicator: @Composable () -> Unit,
    eventContent: @Composable BoxScope.(PositionedEvent<T>) -> Unit,
    modifier: Modifier,
) {
    val date = layout.date
    val tapModifier =
        if (onEmptySlotClick != null) {
            Modifier.pointerInput(date, hourHeight, window.startHour, window.endHour, slotMinutes, onEmptySlotClick) {
                detectTapGestures { offset ->
                    onEmptySlotClick(date, timeAt(offset.y, hourHeight.toPx(), window, slotMinutes))
                }
            }
        } else {
            Modifier
        }
    val timed = layout.timedEvents
    Layout(
        content = {
            for (positioned in timed) {
                key(positioned.event.id) {
                    Box(propagateMinConstraints = true) { eventContent(positioned) }
                }
            }
            if (nowMinute != null) {
                Box { nowIndicator() }
            }
        },
        modifier = modifier.then(tapModifier),
    ) { measurables, constraints ->
        val hourPx = hourHeight.toPx()
        val gapX = dimensions.tileHorizontalGap.toPx()
        val gapY = dimensions.tileVerticalGap.toPx()
        val columnWidth = constraints.maxWidth
        val placeables =
            measurables.mapIndexed { index, measurable ->
                if (index < timed.size) {
                    val positioned = timed[index]
                    val width = (columnWidth * positioned.widthFraction - gapX).roundToInt().coerceAtLeast(0)
                    val minutes = positioned.displayEndMinute - positioned.startMinute
                    val height = (minutes / 60f * hourPx - gapY).roundToInt().coerceAtLeast(0)
                    measurable.measure(Constraints.fixed(width, height))
                } else {
                    measurable.measure(Constraints(minWidth = columnWidth, maxWidth = columnWidth))
                }
            }
        layout(columnWidth, constraints.maxHeight) {
            placeables.forEachIndexed { index, placeable ->
                if (index < timed.size) {
                    val positioned = timed[index]
                    placeable.placeRelative(
                        x = (columnWidth * positioned.leftFraction).roundToInt(),
                        y = ((positioned.startMinute - window.startMinute) / 60f * hourPx).roundToInt(),
                        zIndex = positioned.columnIndex.toFloat(),
                    )
                } else if (nowMinute != null) {
                    val y = ((nowMinute - window.startMinute) / 60f * hourPx - placeable.height / 2f).roundToInt()
                    placeable.placeRelative(0, y)
                }
            }
        }
    }
}

private fun timeAt(
    y: Float,
    hourPx: Float,
    window: HourWindow,
    slotMinutes: Int,
): LocalTime {
    val minute = (window.startMinute + (y / hourPx * 60f).toInt()).coerceIn(window.startMinute, window.endMinute - 1)
    val floored = minute / slotMinutes * slotMinutes
    return LocalTime(floored / 60, floored % 60)
}

private fun Modifier.gridLines(
    dayCount: Int,
    hourHeight: Dp,
    hourCount: Int,
    showHalfHourLines: Boolean,
    colors: ScheduleColors,
): Modifier =
    drawBehind {
        val hourPx = hourHeight.toPx()
        val stroke = 1.dp.toPx()
        for (hour in 0..hourCount) {
            val y = hour * hourPx
            drawLine(colors.gridLineColor, Offset(0f, y), Offset(size.width, y), stroke)
            if (showHalfHourLines && hour < hourCount) {
                val half = y + hourPx / 2f
                drawLine(colors.halfHourLineColor, Offset(0f, half), Offset(size.width, half), stroke)
            }
        }
        for (day in 0 until dayCount) {
            val x = size.width * day / dayCount
            drawLine(colors.gridLineColor, Offset(x, 0f), Offset(x, size.height), stroke)
        }
    }

/**
 * Two-finger pinch scales [ScheduleState.hourHeight], keeping the time under the
 * pinch centroid in place. Single-finger input passes through to scrolling and paging.
 */
private fun Modifier.pinchToZoomHours(
    state: ScheduleState,
    enabled: Boolean,
): Modifier {
    if (!enabled) return this
    return pointerInput(state) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } >= 2) {
                    val zoom = event.calculateZoom()
                    if (zoom != 1f) {
                        state.zoomBy(zoom, event.calculateCentroid(useCurrent = true).y, this)
                    }
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }
}
