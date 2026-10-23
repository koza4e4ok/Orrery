package me.kozakov.orrery.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import me.kozakov.orrery.compose.Agenda
import me.kozakov.orrery.compose.CalendarNavHeader
import me.kozakov.orrery.compose.CollapsibleCalendarScaffold
import me.kozakov.orrery.compose.DayDecorators
import me.kozakov.orrery.compose.DefaultDay
import me.kozakov.orrery.compose.currentDate
import me.kozakov.orrery.compose.rememberAgendaState
import me.kozakov.orrery.compose.rememberCalendarSelectionState
import me.kozakov.orrery.compose.rememberCalendarState
import me.kozakov.orrery.compose.rememberCollapsibleCalendarState
import me.kozakov.orrery.compose.rememberWeekCalendarState
import me.kozakov.orrery.core.Selection
import me.kozakov.orrery.core.SelectionMode

/** Personal agenda: collapsible calendar with event dots, count badges, and synchronized [Agenda]. */
@Composable
internal fun AgendaScreen() {
    val today = remember { currentDate() }
    val rangeStart = remember(today) { today.minus(90, DateTimeUnit.DAY) }
    val rangeEnd = remember(today) { today.plus(90, DateTimeUnit.DAY) }
    val calendarState = rememberCalendarState()
    val weekState =
        rememberWeekCalendarState(
            startDate = LocalDate(today.year - 1, 1, 1),
            endDate = LocalDate(today.year + 1, 12, 31),
        )
    val agendaState =
        rememberAgendaState(
            startDate = rangeStart,
            endDate = rangeEnd,
            initialVisibleDate = today,
        )
    val collapsible = rememberCollapsibleCalendarState()
    val selection =
        rememberCalendarSelectionState(
            mode = SelectionMode.Single(),
            initialSelection = Selection(single = today),
        )
    val scope = rememberCoroutineScope()
    val events = remember(rangeStart, rangeEnd) { SampleData.eventsFor(rangeStart..rangeEnd) }
    val decorators =
        listOf(
            DayDecorators.eventDotsForEvents(eventsByDate = { date -> events[date].orEmpty() }),
            DayDecorators.badge { date -> events[date].orEmpty().size },
        )
    val selectedDay = selection.selection.single
    LaunchedEffect(selectedDay) {
        if (selectedDay != null) {
            agendaState.animateScrollToDate(selectedDay)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CalendarNavHeader(state = calendarState, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                scope.launch {
                    calendarState.animateScrollToToday()
                    agendaState.animateScrollToDate(today)
                }
                selection.set(Selection(single = today))
            }) { Text("Today") }
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
