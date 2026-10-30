package me.kozakov.orrery.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import me.kozakov.orrery.compose.AgendaState
import me.kozakov.orrery.compose.CalendarDensity
import me.kozakov.orrery.compose.CalendarSelectionState
import me.kozakov.orrery.compose.CalendarState
import me.kozakov.orrery.compose.CollapsibleCalendarAnchor
import me.kozakov.orrery.compose.CollapsibleCalendarState
import me.kozakov.orrery.compose.DatePickerDefaults
import me.kozakov.orrery.compose.OrreryDateRangePickerDialog
import me.kozakov.orrery.compose.OrreryDateRangePickerState
import me.kozakov.orrery.compose.RangeEndpointStyle
import me.kozakov.orrery.compose.ScheduleState
import me.kozakov.orrery.compose.WeekCalendarState
import me.kozakov.orrery.compose.currentDate
import me.kozakov.orrery.compose.rememberAgendaState
import me.kozakov.orrery.compose.rememberCalendarSelectionState
import me.kozakov.orrery.compose.rememberCalendarState
import me.kozakov.orrery.compose.rememberCollapsibleCalendarState
import me.kozakov.orrery.compose.rememberOrreryDateRangePickerState
import me.kozakov.orrery.compose.rememberScheduleState
import me.kozakov.orrery.compose.rememberWeekCalendarState
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.Selection
import me.kozakov.orrery.core.SelectionMode
import me.kozakov.orrery.core.eventsByDate

internal enum class DemoTab(
    val label: String,
    val color: Color,
) {
    Month("Month", Color(0xFF5C6BC0)),
    Agenda("Agenda", Color(0xFF64B5F6)),
    Schedule("Schedule", Color(0xFF81C784)),
    Stays("Stays", Color(0xFFFFB74D)),
    Styles("Styles", Color(0xFFE57373)),
}

/** How check-in / check-out days are drawn on the Stays tab. */
internal enum class EndpointOption(
    val label: String,
    val style: RangeEndpointStyle,
) {
    Diagonal("Diagonal", RangeEndpointStyle.SplitDiagonal),
    Split("Split", RangeEndpointStyle.SplitHorizontal),
    Full("Full cell", RangeEndpointStyle.FullCell),
}

/** The curated [CalendarStyle] presets shown on the Styles tab. */
internal enum class StylePreset(
    val label: String,
) {
    Default("Default"),
    Rounded("Rounded"),
    Segmented("Segmented"),
    Bordered("Bordered"),
}

/** Screen state of [DemoApp], hoisted so the tabs keep their state while switching. */
@Stable
@Suppress("LongParameterList")
internal class DemoUiState(
    val today: LocalDate,
    val monthState: CalendarState,
    val agendaMonthState: CalendarState,
    val agendaWeekState: WeekCalendarState,
    val collapsible: CollapsibleCalendarState,
    val agendaSelection: CalendarSelectionState,
    val agendaState: AgendaState,
    val scheduleState: ScheduleState,
    val staysState: CalendarState,
    val staysSelection: CalendarSelectionState,
    val stripState: WeekCalendarState,
    val stylesState: CalendarState,
    val stylesSelection: CalendarSelectionState,
    val pickerState: OrreryDateRangePickerState,
) {
    var tab: DemoTab by mutableStateOf(DemoTab.Month)
    var selectedDay: LocalDate by mutableStateOf(today)
    var weekMode: Boolean by mutableStateOf(false)
    var stackedOverlap: Boolean by mutableStateOf(false)
    var endpoint: EndpointOption by mutableStateOf(EndpointOption.Diagonal)
    var preset: StylePreset by mutableStateOf(StylePreset.Rounded)
    var density: CalendarDensity by mutableStateOf(CalendarDensity.Comfortable)
    var workWeekOnly: Boolean by mutableStateOf(false)
    var showPicker: Boolean by mutableStateOf(false)

    /** Back-to-back bookings on the Stays tab: one guest checks out, the next checks in. */
    val turnoverDates: Set<LocalDate> =
        setOf(today.plus(10, DateTimeUnit.DAY), today.plus(17, DateTimeUnit.DAY))
}

@Composable
private fun rememberDemoUiState(today: LocalDate): DemoUiState {
    val month = YearMonth(today.year, today.month)
    val staysStart = today.plus(7, DateTimeUnit.DAY)
    val monthState = rememberCalendarState(firstVisibleMonth = month)
    val agendaMonthState = rememberCalendarState(firstVisibleMonth = month)
    val agendaWeekState =
        rememberWeekCalendarState(
            startDate = today.minus(DATA_DAYS, DateTimeUnit.DAY),
            endDate = today.plus(DATA_DAYS, DateTimeUnit.DAY),
            firstVisibleDate = today,
        )
    val collapsible =
        rememberCollapsibleCalendarState(
            initialAnchor = CollapsibleCalendarAnchor.Month,
            enableTwoWeekStop = true,
        )
    val agendaSelection =
        rememberCalendarSelectionState(mode = SelectionMode.Single(), initialSelection = Selection(single = today))
    val agendaState =
        rememberAgendaState(
            startDate = today.minus(DATA_DAYS, DateTimeUnit.DAY),
            endDate = today.plus(DATA_DAYS, DateTimeUnit.DAY),
            initialVisibleDate = today,
        )
    val scheduleState =
        rememberScheduleState(
            startDate = today.minus(DATA_DAYS, DateTimeUnit.DAY),
            endDate = today.plus(DATA_DAYS, DateTimeUnit.DAY),
            firstVisibleDate = today,
            initialScrollTime = LocalTime(WORK_DAY_START_HOUR, 0),
            minHour = WORK_DAY_START_HOUR,
            maxHour = WORK_DAY_END_HOUR,
        )
    val staysState = rememberCalendarState(firstVisibleMonth = YearMonth(staysStart.year, staysStart.month))
    val staysSelection = rememberCalendarSelectionState(mode = SelectionMode.Range(minDays = 2))
    val stripState =
        rememberWeekCalendarState(firstVisibleDate = today.minus(7, DateTimeUnit.DAY), visibleWeeksCount = 2)
    val stylesState = rememberCalendarState(firstVisibleMonth = month)
    val stylesSelection =
        rememberCalendarSelectionState(
            mode = SelectionMode.Range(),
            initialSelection =
                Selection(rangeStart = today.plus(2, DateTimeUnit.DAY), rangeEnd = today.plus(6, DateTimeUnit.DAY)),
        )
    val pickerState = rememberOrreryDateRangePickerState(initialDisplayedMonth = month)
    return remember {
        DemoUiState(
            today = today,
            monthState = monthState,
            agendaMonthState = agendaMonthState,
            agendaWeekState = agendaWeekState,
            collapsible = collapsible,
            agendaSelection = agendaSelection,
            agendaState = agendaState,
            scheduleState = scheduleState,
            staysState = staysState,
            staysSelection = staysSelection,
            stripState = stripState,
            stylesState = stylesState,
            stylesSelection = stylesSelection,
            pickerState = pickerState,
        )
    }
}

/**
 * Interactive tour of Orrery: a month grid with multi-day event banners, a collapsible
 * Month / TwoWeeks / Week calendar over the agenda, a working-hours Day / Week schedule with
 * stacked overlaps, hotel stays with split check-in / check-out days, and the style presets
 * under a two-week strip with a Mon–Fri filter.
 */
@Composable
internal fun DemoApp(
    platform: String,
    today: LocalDate = currentDate(),
    now: LocalTime = LocalTime(12, 0),
) {
    MaterialTheme(colorScheme = lightColorScheme()) {
        val ui = rememberDemoUiState(today)
        val events = remember(today) { buildDemoEvents(today) }
        val eventsByDate =
            remember(events) {
                events.eventsByDate(today.minus(DATA_DAYS, DateTimeUnit.DAY)..today.plus(DATA_DAYS, DateTimeUnit.DAY))
            }
        val eventsOn: (LocalDate) -> List<CalendarEvent<Nothing>> = { eventsByDate[it].orEmpty() }
        LaunchedEffect(ui.workWeekOnly) {
            val days = if (ui.workWeekOnly) WORK_WEEK else ALL_DAYS
            ui.stripState.visibleDaysOfWeek = days
            ui.stylesState.visibleDaysOfWeek = days
        }

        Scaffold(
            topBar = { DemoTopBar(platform) },
            bottomBar = {
                NavigationBar {
                    DemoTab.entries.forEach { entry ->
                        NavigationBarItem(
                            selected = ui.tab == entry,
                            onClick = { ui.tab = entry },
                            icon = { Box(Modifier.size(12.dp).background(entry.color, CircleShape)) },
                            label = { Text(entry.label) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (ui.tab) {
                    DemoTab.Month -> MonthTab(ui, events, eventsOn)
                    DemoTab.Agenda -> AgendaTab(ui, eventsOn)
                    DemoTab.Schedule -> ScheduleTab(ui, eventsOn, now)
                    DemoTab.Stays -> StaysTab(ui)
                    DemoTab.Styles -> StylesTab(ui, eventsOn)
                }
            }
        }
        if (ui.showPicker) {
            RangePickerDialog(ui)
        }
    }
}

@Composable
private fun DemoTopBar(platform: String) {
    Surface(tonalElevation = 2.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                ).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Orrery", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "  on $platform",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RangePickerDialog(ui: DemoUiState) {
    val presets = DatePickerDefaults.rangePresets(today = ui.today)
    OrreryDateRangePickerDialog(
        onDismissRequest = { ui.showPicker = false },
        confirmButton = {
            TextButton(
                onClick = {
                    ui.showPicker = false
                    val start = ui.pickerState.selectedStartDate
                    if (start != null) {
                        ui.staysSelection.set(
                            Selection(rangeStart = start, rangeEnd = ui.pickerState.selectedEndDate ?: start),
                        )
                    }
                },
            ) { Text("Save") }
        },
        state = ui.pickerState,
        today = ui.today,
        presets = presets,
    )
}
