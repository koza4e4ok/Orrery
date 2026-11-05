package me.kozakov.orrery.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import me.kozakov.orrery.compose.Agenda
import me.kozakov.orrery.compose.CalendarDefaults
import me.kozakov.orrery.compose.CalendarNavHeader
import me.kozakov.orrery.compose.CollapsibleCalendarAnchor
import me.kozakov.orrery.compose.CollapsibleCalendarScaffold
import me.kozakov.orrery.compose.CollapsibleDragHandle
import me.kozakov.orrery.compose.DayDecorators
import me.kozakov.orrery.compose.DaySchedule
import me.kozakov.orrery.compose.DefaultDay
import me.kozakov.orrery.compose.HorizontalCalendar
import me.kozakov.orrery.compose.WeekEventBanners
import me.kozakov.orrery.compose.WeekSchedule
import me.kozakov.orrery.compose.YearCalendar
import me.kozakov.orrery.compose.currentDate
import me.kozakov.orrery.compose.rememberAgendaState
import me.kozakov.orrery.compose.rememberCalendarSelectionState
import me.kozakov.orrery.compose.rememberCalendarState
import me.kozakov.orrery.compose.rememberCollapsibleCalendarState
import me.kozakov.orrery.compose.rememberScheduleState
import me.kozakov.orrery.compose.rememberWeekCalendarState
import me.kozakov.orrery.compose.rememberYearCalendarState
import me.kozakov.orrery.core.CalendarDay
import me.kozakov.orrery.core.DayPosition
import me.kozakov.orrery.core.EventOverlapMode
import me.kozakov.orrery.core.Selection
import me.kozakov.orrery.core.SelectionMode
import me.kozakov.orrery.lunar.LunarDayInfoProvider

/** How the agenda screen shows the event dataset. */
private enum class AgendaMode(
    val label: String,
) {
    List("Agenda"),
    Month("Month"),
    Day("Day"),
    Week("Week"),
}

/** Working hours shown by the [DaySchedule] / [WeekSchedule] time grids. */
private const val WORK_DAY_START_HOUR = 7

private const val COMPACT_HEIGHT_DP = 480
private const val WORK_DAY_END_HOUR = 22

/** Hour heights the time grids' Zoom chip animates between. */
private val DefaultHourHeight = 60.dp
private val ZoomedHourHeight = 104.dp

/**
 * Personal agenda: a collapsible calendar (Month / TwoWeeks / Week stops, direct drag and a
 * grab handle) with event dots, count badges and a synchronized [Agenda]; a month grid with
 * multi-day [WeekEventBanners]; or the same events in a [DaySchedule] / [WeekSchedule] time
 * grid limited to working hours, with side-by-side or stacked overlapping events, page
 * chevrons and an animated zoom.
 */
@Composable
internal fun AgendaScreen() {
    val today = remember { currentDate() }
    var mode by rememberSaveable { mutableStateOf(AgendaMode.List) }
    var showYearOverview by rememberSaveable { mutableStateOf(false) }
    var stackedOverlap by rememberSaveable { mutableStateOf(false) }
    var zoomed by rememberSaveable { mutableStateOf(false) }
    var now by remember { mutableStateOf(systemTimeOfDay()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            now = systemTimeOfDay()
        }
    }
    val rangeStart = remember(today) { today.minus(90, DateTimeUnit.DAY) }
    val rangeEnd = remember(today) { today.plus(90, DateTimeUnit.DAY) }
    val calendarState = rememberCalendarState()
    val weekState =
        rememberWeekCalendarState(
            startDate = LocalDate(today.year - 1, 1, 1),
            endDate = LocalDate(today.year + 1, 12, 31),
        )
    val yearState = rememberYearCalendarState(firstVisibleYear = today.year)
    val agendaState =
        rememberAgendaState(
            startDate = rangeStart,
            endDate = rangeEnd,
            initialVisibleDate = today,
        )
    val scheduleState =
        rememberScheduleState(
            startDate = rangeStart,
            endDate = rangeEnd,
            firstVisibleDate = today,
            minHour = WORK_DAY_START_HOUR,
            maxHour = WORK_DAY_END_HOUR,
        )
    val overlapMode = if (stackedOverlap) EventOverlapMode.Stacked() else EventOverlapMode.SideBySide
    val collapsible =
        rememberCollapsibleCalendarState(
            initialAnchor = CollapsibleCalendarAnchor.Month,
            enableTwoWeekStop = true,
        )
    val selection =
        rememberCalendarSelectionState(
            mode = SelectionMode.Single(),
            initialSelection = Selection(single = today),
        )
    val scope = rememberCoroutineScope()
    val events = remember(rangeStart, rangeEnd) { SampleData.eventsFor(rangeStart..rangeEnd) }
    val bannerEvents = remember(events) { events.values.flatten().distinctBy { it.id } }
    val lunarProvider = remember { LunarDayInfoProvider() }
    val decorators =
        listOf(
            DayDecorators.eventDotsForEvents(eventsByDate = { date -> events[date].orEmpty() }),
            DayDecorators.badge { date -> events[date].orEmpty().size },
        )
    val selectedDay = selection.selection.single ?: today
    // Sync the views to a newly selected day, but not again after rotation, which would undo paging.
    var syncedDay by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedDay) {
        if (syncedDay == selectedDay.toString()) return@LaunchedEffect
        syncedDay = selectedDay.toString()
        scheduleState.scrollToDate(selectedDay)
        agendaState.animateScrollToDate(selectedDay)
    }
    // The month header has no calendar under it in the time-grid modes; keep it on the paged-to date.
    val scheduleDate = scheduleState.firstVisibleDate
    LaunchedEffect(mode, scheduleDate) {
        if (mode == AgendaMode.Day || mode == AgendaMode.Week) calendarState.animateScrollToDate(scheduleDate)
    }

    val dayEventsCount = events[selectedDay].orEmpty().size
    val lunarSubtitle = remember(selectedDay) { lunarProvider.info(selectedDay)?.label ?: "Schedule" }

    // Short windows (landscape phones) drop the big date header so the time grid keeps its room.
    val compactHeight = LocalConfiguration.current.screenHeightDp < COMPACT_HEIGHT_DP
    Column(Modifier.fillMaxSize()) {
        if (!compactHeight) {
            SampleHeroHeader(
                date = selectedDay,
                today = today,
                subtitle = "$lunarSubtitle · $dayEventsCount event${if (dayEventsCount == 1) "" else "s"}",
                yearOverviewActive = showYearOverview,
                onTodayClick = {
                    showYearOverview = false
                    scope.launch {
                        calendarState.animateScrollToToday()
                        weekState.animateScrollToDate(today)
                        agendaState.animateScrollToDate(today)
                        scheduleState.animateScrollToDate(today)
                    }
                    selection.set(Selection(single = today))
                },
                onToggleYearOverview = { showYearOverview = !showYearOverview },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AgendaMode.entries.forEach { option ->
                FilterChip(
                    selected = option == mode && !showYearOverview,
                    onClick = {
                        showYearOverview = false
                        mode = option
                    },
                    label = { Text(option.label) },
                )
            }
        }
        if (!showYearOverview && (mode == AgendaMode.Day || mode == AgendaMode.Week)) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // One page is a day in Day mode and a week in Week mode.
                IconButton(onClick = { scope.launch { scheduleState.animateScrollToPreviousPage() } }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous page")
                }
                IconButton(onClick = { scope.launch { scheduleState.animateScrollToNextPage() } }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next page")
                }
                FilterChip(
                    selected = stackedOverlap,
                    onClick = { stackedOverlap = !stackedOverlap },
                    label = { Text("Stacked overlap") },
                )
                FilterChip(
                    selected = zoomed,
                    onClick = {
                        zoomed = !zoomed
                        scope.launch {
                            scheduleState.animateHourHeightTo(if (zoomed) ZoomedHourHeight else DefaultHourHeight)
                        }
                    },
                    label = { Text("Zoom") },
                )
            }
            if (!compactHeight) {
                Text(
                    "Working hours $WORK_DAY_START_HOUR:00–$WORK_DAY_END_HOUR:00",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider()
        if (showYearOverview) {
            YearCalendar(
                state = yearState,
                modifier = Modifier.fillMaxSize().padding(8.dp),
                onMonthClick = { ym: YearMonth ->
                    showYearOverview = false
                    scope.launch {
                        calendarState.scrollToMonth(ym)
                        weekState.scrollToDate(ym.firstDay)
                        agendaState.scrollToDate(ym.firstDay)
                        scheduleState.scrollToDate(ym.firstDay)
                    }
                    selection.set(Selection(single = ym.firstDay))
                },
            )
        } else {
            // In short windows the time-grid modes rely on their day header for the dates.
            if (!compactHeight || mode == AgendaMode.List || mode == AgendaMode.Month) {
                CalendarNavHeader(state = calendarState)
            }
            when (mode) {
                AgendaMode.Day ->
                    DaySchedule(
                        events = { date -> events[date].orEmpty() },
                        state = scheduleState,
                        today = today,
                        currentTime = now,
                        modifier = Modifier.fillMaxSize(),
                        overlapMode = overlapMode,
                    )

                AgendaMode.Week ->
                    WeekSchedule(
                        events = { date -> events[date].orEmpty() },
                        state = scheduleState,
                        today = today,
                        currentTime = now,
                        modifier = Modifier.fillMaxSize(),
                        overlapMode = overlapMode,
                    )

                // Multi-day events as Google Calendar-style title banners over each week row.
                AgendaMode.Month ->
                    HorizontalCalendar(
                        state = calendarState,
                        modifier = Modifier.fillMaxWidth(),
                        weekOverlay = { week ->
                            WeekEventBanners(
                                week = week,
                                events = bannerEvents,
                                maxVisibleLanes = 3,
                                onEventClick = { event -> selection.set(Selection(single = event.span.startDate)) },
                                onOverflowClick = { date, _ ->
                                    selection.set(Selection(single = date))
                                    mode = AgendaMode.List
                                },
                            )
                        },
                        dayContent = { day ->
                            BannerDayNumber(
                                day = day,
                                selected = day.date == selectedDay,
                                isToday = day.date == today,
                                onClick = { selection.set(Selection(single = day.date)) },
                            )
                        },
                    )

                AgendaMode.List ->
                    CollapsibleCalendarScaffold(
                        calendarState = calendarState,
                        weekState = weekState,
                        state = collapsible,
                        enableDirectDrag = true,
                        dragHandle = { CalendarDefaults.CollapsibleDragHandle() },
                        dayContent = { day ->
                            DefaultDay(
                                day = day,
                                selectionState = selection,
                                today = today,
                                decorators = decorators,
                            )
                        },
                    ) {
                        Agenda(
                            events = { date -> events[date].orEmpty() },
                            state = agendaState,
                            today = today,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
            }
        }
    }
}

/** A compact day number that leaves room below it for the [WeekEventBanners] lanes. */
@Composable
private fun BannerDayNumber(
    day: CalendarDay,
    selected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val background =
        when {
            selected -> colors.primary
            isToday -> colors.primaryContainer
            else -> Color.Transparent
        }
    val content =
        when {
            selected -> colors.onPrimary
            day.position != DayPosition.MonthDate -> colors.onSurfaceVariant.copy(alpha = 0.5f)
            isToday -> colors.onPrimaryContainer
            else -> colors.onSurface
        }
    Box(
        Modifier.fillMaxWidth().height(24.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(22.dp).background(background, CircleShape), contentAlignment = Alignment.Center) {
            Text("${day.date.day}", style = MaterialTheme.typography.labelMedium, color = content)
        }
    }
}

private fun systemTimeOfDay(): LocalTime {
    val now = java.time.LocalTime.now()
    return LocalTime(now.hour, now.minute)
}
