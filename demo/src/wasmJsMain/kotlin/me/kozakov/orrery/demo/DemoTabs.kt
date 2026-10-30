package me.kozakov.orrery.demo

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import me.kozakov.orrery.compose.Agenda
import me.kozakov.orrery.compose.CalendarDefaults
import me.kozakov.orrery.compose.CalendarDensity
import me.kozakov.orrery.compose.CalendarNavHeader
import me.kozakov.orrery.compose.CalendarStyle
import me.kozakov.orrery.compose.CollapsibleCalendarScaffold
import me.kozakov.orrery.compose.CollapsibleDragHandle
import me.kozakov.orrery.compose.DayDecorators
import me.kozakov.orrery.compose.DaySchedule
import me.kozakov.orrery.compose.DefaultDay
import me.kozakov.orrery.compose.HorizontalCalendar
import me.kozakov.orrery.compose.OutDateClickPolicy
import me.kozakov.orrery.compose.WeekCalendar
import me.kozakov.orrery.compose.WeekEventBanners
import me.kozakov.orrery.compose.WeekSchedule
import me.kozakov.orrery.core.CalendarDay
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.DayInfo
import me.kozakov.orrery.core.DayPosition
import me.kozakov.orrery.core.EventOverlapMode

/** Multi-day events as title banners over each week row of a month grid. */
@Composable
private fun MonthTab(
    ui: DemoUiState,
    events: List<CalendarEvent<Nothing>>,
    eventsOn: (LocalDate) -> List<CalendarEvent<Nothing>>,
) {
    Column(Modifier.fillMaxSize()) {
        CalendarNavHeader(state = ui.monthState)
        HorizontalCalendar(
            state = ui.monthState,
            modifier = Modifier.fillMaxWidth(),
            weekOverlay = { week ->
                WeekEventBanners(
                    week = week,
                    events = events,
                    maxVisibleLanes = 3,
                    onEventClick = { event -> ui.selectedDay = event.span.startDate },
                    onOverflowClick = { date, _ -> ui.selectedDay = date },
                )
            },
            dayContent = { day ->
                BannerDayNumber(
                    day = day,
                    selected = day.date == ui.selectedDay,
                    isToday = day.date == ui.today,
                    onClick = { ui.selectedDay = day.date },
                )
            },
        )
        val dayEvents = eventsOn(ui.selectedDay)
        Card(Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${ui.selectedDay}", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (dayEvents.isEmpty()) "No events" else dayEvents.take(4).joinToString(", ") { it.title },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

/** A Month / TwoWeeks / Week collapsible calendar with a drag handle over the agenda. */
@Composable
private fun AgendaTab(
    ui: DemoUiState,
    eventsOn: (LocalDate) -> List<CalendarEvent<Nothing>>,
) {
    val selectedDay = ui.agendaSelection.selection.single ?: ui.today
    LaunchedEffect(selectedDay) { ui.agendaState.animateScrollToDate(selectedDay) }
    val decorators =
        listOf(
            DayDecorators.eventDotsForEvents(eventsByDate = eventsOn),
            DayDecorators.badge { date -> eventsOn(date).size },
        )
    CollapsibleCalendarScaffold(
        calendarState = ui.agendaMonthState,
        weekState = ui.agendaWeekState,
        state = ui.collapsible,
        enableDirectDrag = true,
        dragHandle = { CalendarDefaults.CollapsibleDragHandle() },
        dayContent = { day ->
            DefaultDay(
                day = day,
                selectionState = ui.agendaSelection,
                today = ui.today,
                decorators = decorators,
            )
        },
    ) {
        Agenda(
            events = eventsOn,
            state = ui.agendaState,
            today = ui.today,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Day / Week time grids limited to working hours, with side-by-side or stacked overlaps. */
@Composable
private fun ScheduleTab(
    ui: DemoUiState,
    eventsOn: (LocalDate) -> List<CalendarEvent<Nothing>>,
    now: LocalTime,
) {
    val overlapMode = if (ui.stackedOverlap) EventOverlapMode.Stacked() else EventOverlapMode.SideBySide
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(selected = !ui.weekMode, onClick = { ui.weekMode = false }, label = { Text("Day") })
            FilterChip(selected = ui.weekMode, onClick = { ui.weekMode = true }, label = { Text("Week") })
            FilterChip(
                selected = ui.stackedOverlap,
                onClick = { ui.stackedOverlap = !ui.stackedOverlap },
                label = { Text("Stacked") },
            )
        }
        Text(
            "Working hours $WORK_DAY_START_HOUR:00-$WORK_DAY_END_HOUR:00",
            Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (ui.weekMode) {
            WeekSchedule(
                events = eventsOn,
                state = ui.scheduleState,
                today = ui.today,
                currentTime = now,
                modifier = Modifier.fillMaxSize(),
                overlapMode = overlapMode,
            )
        } else {
            DaySchedule(
                events = eventsOn,
                state = ui.scheduleState,
                today = ui.today,
                currentTime = now,
                modifier = Modifier.fillMaxSize(),
                overlapMode = overlapMode,
            )
        }
    }
}

/** Hotel stays: check-in PM half, check-out AM half and turnover days, with nightly prices. */
@Composable
private fun StaysTab(ui: DemoUiState) {
    val style =
        if (ui.endpoint == EndpointOption.Full) {
            CalendarDefaults.roundedSquareStyle()
        } else {
            CalendarDefaults.hospitalityStyle(rangeEndpointStyle = ui.endpoint.style)
        }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Check-in/out",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            EndpointOption.entries.forEach { option ->
                FilterChip(
                    selected = option == ui.endpoint,
                    onClick = { ui.endpoint = option },
                    label = { Text(option.label) },
                )
            }
        }
        HorizontalDivider()
        CalendarNavHeader(state = ui.staysState)
        HorizontalCalendar(
            state = ui.staysState,
            dragSelection = ui.staysSelection,
            dayContent = { day ->
                DefaultDay(
                    day = day,
                    style = style,
                    turnoverDates = ui.turnoverDates,
                    selectionState = ui.staysSelection,
                    calendarState = ui.staysState,
                    outDateClickPolicy = OutDateClickPolicy.SelectAndScrollToMonth,
                    today = ui.today,
                    info =
                        if (day.date >= ui.today) {
                            DayInfo(label = "$" + nightlyPrice(day.date))
                        } else {
                            null
                        },
                )
            },
        )
        StaySummary(ui)
    }
}

@Composable
private fun StaySummary(ui: DemoUiState) {
    val range = ui.staysSelection.selection.range
    val summary =
        if (range == null) {
            "Drag to pick check-in and check-out"
        } else {
            val nights = range.start.daysUntil(range.endInclusive)
            val total = (0 until nights).sumOf { nightlyPrice(range.start.plus(it, DateTimeUnit.DAY)) }
            "${range.start} to ${range.endInclusive}, $nights nights, $$total"
        }
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Your stay", style = MaterialTheme.typography.titleMedium)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { ui.showPicker = true }) { Text("Picker") }
        }
    }
}

/** The curated style presets and densities, under a two-week strip with a Mon–Fri filter. */
@Composable
private fun StylesTab(
    ui: DemoUiState,
    eventsOn: (LocalDate) -> List<CalendarEvent<Nothing>>,
) {
    val style = ui.preset.style(ui.density)
    val dots = DayDecorators.eventDotsForEvents(eventsByDate = eventsOn)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Two weeks", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            FilterChip(
                selected = ui.workWeekOnly,
                onClick = { ui.workWeekOnly = !ui.workWeekOnly },
                label = { Text("Mon-Fri") },
            )
        }
        WeekCalendar(
            state = ui.stripState,
            dayContent = { day -> DefaultDay(day = day, today = ui.today, decorators = listOf(dots)) },
        )
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        ChipRow(StylePreset.entries, ui.preset, { it.label }) { ui.preset = it }
        ChipRow(CalendarDensity.entries, ui.density, { it.name }) { ui.density = it }
        CalendarNavHeader(state = ui.stylesState)
        HorizontalCalendar(
            state = ui.stylesState,
            dragSelection = ui.stylesSelection,
            dayContent = { day ->
                DefaultDay(
                    day = day,
                    style = style,
                    selectionState = ui.stylesSelection,
                    today = ui.today,
                    info = dayInfo.info(day.date),
                    decorators = listOf(dots),
                )
            },
        )
    }
}

@Composable
private fun <T> ChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
            )
        }
    }
}

@Composable
private fun StylePreset.style(density: CalendarDensity): CalendarStyle =
    when (this) {
        StylePreset.Default -> CalendarDefaults.style(density = density)
        StylePreset.Rounded -> CalendarDefaults.roundedSquareStyle(density = density)
        StylePreset.Segmented -> CalendarDefaults.segmentedRangeStyle(density = density)
        StylePreset.Bordered -> CalendarDefaults.borderedCardStyle(density = density)
    }
