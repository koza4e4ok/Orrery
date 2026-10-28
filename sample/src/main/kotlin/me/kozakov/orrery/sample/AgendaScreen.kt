package me.kozakov.orrery.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import me.kozakov.orrery.compose.CalendarNavHeader
import me.kozakov.orrery.compose.CollapsibleCalendarScaffold
import me.kozakov.orrery.compose.DayDecorators
import me.kozakov.orrery.compose.DaySchedule
import me.kozakov.orrery.compose.DefaultDay
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
import me.kozakov.orrery.core.Selection
import me.kozakov.orrery.core.SelectionMode
import me.kozakov.orrery.lunar.LunarDayInfoProvider

/** How the agenda screen shows the event dataset. */
private enum class AgendaMode(
    val label: String,
) {
    List("Agenda List"),
    Day("Day Grid"),
    Week("Week Grid"),
}

/**
 * Personal agenda: collapsible calendar with spanning event bars, event dots, count badges,
 * and synchronized [Agenda], or the same events in a [DaySchedule] / [WeekSchedule] time grid.
 */
@Composable
internal fun AgendaScreen() {
    val today = remember { currentDate() }
    var mode by rememberSaveable { mutableStateOf(AgendaMode.List) }
    var showYearOverview by rememberSaveable { mutableStateOf(false) }
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
        )
    val collapsible = rememberCollapsibleCalendarState()
    val selection =
        rememberCalendarSelectionState(
            mode = SelectionMode.Single(),
            initialSelection = Selection(single = today),
        )
    val scope = rememberCoroutineScope()
    val events = remember(rangeStart, rangeEnd) { SampleData.eventsFor(rangeStart..rangeEnd) }
    val lunarProvider = remember { LunarDayInfoProvider() }
    val decorators =
        listOf(
            DayDecorators.eventDotsForEvents(eventsByDate = { date -> events[date].orEmpty() }),
            DayDecorators.badge { date -> events[date].orEmpty().size },
        )
    val selectedDay = selection.selection.single ?: today
    LaunchedEffect(selectedDay) {
        scheduleState.scrollToDate(selectedDay)
        agendaState.animateScrollToDate(selectedDay)
    }
    // The month header has no calendar under it in the time-grid modes; keep it on the paged-to date.
    val scheduleDate = scheduleState.firstVisibleDate
    LaunchedEffect(mode, scheduleDate) {
        if (mode != AgendaMode.List) calendarState.animateScrollToDate(scheduleDate)
    }

    val dayEventsCount = events[selectedDay].orEmpty().size
    val lunarSubtitle = remember(selectedDay) { lunarProvider.info(selectedDay)?.label ?: "Schedule" }

    Column(Modifier.fillMaxSize()) {
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
            CalendarNavHeader(state = calendarState)
            when (mode) {
                AgendaMode.Day ->
                    DaySchedule(
                        events = { date -> events[date].orEmpty() },
                        state = scheduleState,
                        today = today,
                        currentTime = now,
                        modifier = Modifier.fillMaxSize(),
                    )

                AgendaMode.Week ->
                    WeekSchedule(
                        events = { date -> events[date].orEmpty() },
                        state = scheduleState,
                        today = today,
                        currentTime = now,
                        modifier = Modifier.fillMaxSize(),
                    )

                AgendaMode.List ->
                    CollapsibleCalendarScaffold(
                        calendarState = calendarState,
                        weekState = weekState,
                        state = collapsible,
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

private fun systemTimeOfDay(): LocalTime {
    val now = java.time.LocalTime.now()
    return LocalTime(now.hour, now.minute)
}
