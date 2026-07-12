package dev.koza4e4ok.material.calendar.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.koza4e4ok.material.calendar.compose.CalendarDefaults
import dev.koza4e4ok.material.calendar.compose.CalendarNavHeader
import dev.koza4e4ok.material.calendar.compose.CollapsibleCalendarScaffold
import dev.koza4e4ok.material.calendar.compose.DayDecorators
import dev.koza4e4ok.material.calendar.compose.DefaultDay
import dev.koza4e4ok.material.calendar.compose.HorizontalCalendar
import dev.koza4e4ok.material.calendar.compose.MonthYearPicker
import dev.koza4e4ok.material.calendar.compose.VerticalCalendar
import dev.koza4e4ok.material.calendar.compose.currentDate
import dev.koza4e4ok.material.calendar.compose.displayName
import dev.koza4e4ok.material.calendar.compose.rememberCalendarSelectionState
import dev.koza4e4ok.material.calendar.compose.rememberCalendarState
import dev.koza4e4ok.material.calendar.compose.rememberCollapsibleCalendarState
import dev.koza4e4ok.material.calendar.compose.rememberWeekCalendarState
import dev.koza4e4ok.material.calendar.core.DisabledDates
import dev.koza4e4ok.material.calendar.core.SelectionMode
import dev.koza4e4ok.material.calendar.lunar.LunarDayInfoProvider
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                var screen by remember { mutableStateOf(0) }
                Scaffold { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            TextButton(onClick = { screen = 0 }) { Text("Agenda") }
                            TextButton(onClick = { screen = 1 }) { Text("Vertical") }
                            TextButton(onClick = { screen = 2 }) { Text("Booking") }
                            TextButton(onClick = { screen = 3 }) { Text("Habits") }
                            TextButton(onClick = { screen = 4 }) { Text("Lunar") }
                        }
                        when (screen) {
                            0 -> AgendaDemo()
                            1 -> VerticalDemo()
                            2 -> BookingDemo()
                            3 -> HabitsDemo()
                            else -> LunarDemo()
                        }
                    }
                }
            }
        }
    }
}

/** Collapsible month/week calendar with an agenda list underneath. */
@Composable
private fun AgendaDemo() {
    val today = remember { currentDate() }
    val events =
        remember {
            (1..28 step 3).map { LocalDate(today.year, today.month, it) }.toSet()
        }
    val calendarState = rememberCalendarState()
    val weekState =
        rememberWeekCalendarState(
            startDate = LocalDate(today.year - 1, 1, 1),
            endDate = LocalDate(today.year + 1, 12, 31),
        )
    val collapsible = rememberCollapsibleCalendarState()
    val selection = rememberCalendarSelectionState(mode = SelectionMode.Single())
    val scope = rememberCoroutineScope()
    val eventDecorators =
        remember(events) {
            listOf(
                DayDecorators.eventDots { date ->
                    if (date in events) listOf(Color(0xFFE57373)) else emptyList()
                },
            )
        }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = calendarState.firstVisibleMonth.displayName(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
            )
            TextButton(onClick = {
                scope.launch { if (collapsible.isExpanded) collapsible.collapse() else collapsible.expand() }
            }) { Text(if (collapsible.isExpanded) "Collapse" else "Expand") }
        }
        CollapsibleCalendarScaffold(
            calendarState = calendarState,
            weekState = weekState,
            state = collapsible,
            dayContent = { day ->
                DefaultDay(
                    day = day,
                    selectionState = selection,
                    today = today,
                    decorators = eventDecorators,
                )
            },
        ) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(count = 30) { i ->
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("Agenda item ${i + 1}", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Scroll me up to collapse the calendar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        HorizontalDivider(Modifier.padding(top = 12.dp))
                    }
                }
            }
        }
    }
}

/** Booking-style range selection with nav header, jump picker, and today button. */
@Composable
private fun BookingDemo() {
    val today = remember { currentDate() }
    val disabled =
        remember(today) {
            DisabledDates {
                before(today)
                daysOfWeek(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
                range(today.plus(10, DateTimeUnit.DAY)..today.plus(12, DateTimeUnit.DAY))
            }
        }
    val selection =
        rememberCalendarSelectionState(
            mode = SelectionMode.Range(maxDays = 14),
            disabled = disabled,
        )
    val calendarState = rememberCalendarState()
    val scope = rememberCoroutineScope()
    var pickerVisible by remember { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CalendarNavHeader(
                state = calendarState,
                modifier = Modifier.weight(1f),
                onTitleClick = { pickerVisible = !pickerVisible },
            )
            TextButton(onClick = { scope.launch { calendarState.animateScrollToToday() } }) {
                Text("Today")
            }
        }
        AnimatedContent(targetState = pickerVisible, label = "bookingPicker") { showPicker ->
            if (showPicker) {
                MonthYearPicker(
                    current = calendarState.firstVisibleMonth,
                    range = calendarState.startMonth..calendarState.endMonth,
                    onSelect = { month ->
                        pickerVisible = false
                        scope.launch { calendarState.scrollToMonth(month) }
                    },
                )
            } else {
                HorizontalCalendar(
                    state = calendarState,
                    dayContent = { day ->
                        DefaultDay(
                            day = day,
                            selectionState = selection,
                            today = today,
                            shapes =
                                CalendarDefaults.dayShapes(
                                    dayShape = RoundedCornerShape(12.dp),
                                    selectedShape = RoundedCornerShape(12.dp),
                                ),
                        )
                    },
                )
            }
        }
    }
}

/** Habit tracker: progress rings for the current month and a completion heatmap. */
@Composable
private fun HabitsDemo() {
    val today = remember { currentDate() }
    val ringDecorators =
        listOf(
            DayDecorators.progressRing { date ->
                if (date.month == today.month) ((date.day * 37) % 101) / 100f else null
            },
        )
    val heatDecorators =
        listOf(
            DayDecorators.heatmap { date ->
                if (date.month == today.month) ((date.day * 53) % 101) / 100f else null
            },
        )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            "Habit progress",
            Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        HorizontalCalendar(
            state = rememberCalendarState(),
            dayContent = { day ->
                DefaultDay(day = day, today = today, decorators = ringDecorators)
            },
        )
        Text(
            "Completion heatmap",
            Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        HorizontalCalendar(
            state = rememberCalendarState(),
            dayContent = { day ->
                DefaultDay(day = day, today = today, decorators = heatDecorators)
            },
        )
    }
}

/** Month calendar with lunar day labels, festivals and solar terms from calendar-lunar. */
@Composable
private fun LunarDemo() {
    val today = remember { currentDate() }
    val provider = remember { LunarDayInfoProvider() }
    val selection = rememberCalendarSelectionState(mode = SelectionMode.Single())
    HorizontalCalendar(
        state = rememberCalendarState(),
        dayContent = { day ->
            DefaultDay(
                day = day,
                selectionState = selection,
                today = today,
                info = provider.info(day.date),
            )
        },
    )
}

/** Vertical month list: sticky headers, week numbers, long-press drag range selection. */
@Composable
private fun VerticalDemo() {
    val today = remember { currentDate() }
    val selection = rememberCalendarSelectionState(mode = SelectionMode.Range(maxDays = 30))
    VerticalCalendar(
        state =
            rememberCalendarState(
                startMonth = YearMonth(today.year, 1),
                endMonth = YearMonth(today.year + 1, 12),
            ),
        showWeekNumbers = true,
        stickyMonthHeaders = true,
        dragSelection = selection,
        dayContent = { day ->
            DefaultDay(day = day, selectionState = selection, today = today)
        },
    )
}
