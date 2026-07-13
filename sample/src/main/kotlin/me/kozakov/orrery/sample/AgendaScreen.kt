package me.kozakov.orrery.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaLocalDate
import me.kozakov.orrery.compose.CalendarNavHeader
import me.kozakov.orrery.compose.CollapsibleCalendarScaffold
import me.kozakov.orrery.compose.DayDecorators
import me.kozakov.orrery.compose.DefaultDay
import me.kozakov.orrery.compose.currentDate
import me.kozakov.orrery.compose.rememberCalendarSelectionState
import me.kozakov.orrery.compose.rememberCalendarState
import me.kozakov.orrery.compose.rememberCollapsibleCalendarState
import me.kozakov.orrery.compose.rememberWeekCalendarState
import me.kozakov.orrery.core.Selection
import me.kozakov.orrery.core.SelectionMode
import java.time.format.DateTimeFormatter

/** Personal agenda: collapsible calendar with event dots and count badges. */
@Composable
internal fun AgendaScreen() {
    val today = remember { currentDate() }
    val calendarState = rememberCalendarState()
    val weekState =
        rememberWeekCalendarState(
            startDate = LocalDate(today.year - 1, 1, 1),
            endDate = LocalDate(today.year + 1, 12, 31),
        )
    val collapsible = rememberCollapsibleCalendarState()
    val selection =
        rememberCalendarSelectionState(
            mode = SelectionMode.Single(),
            initialSelection = Selection(single = today),
        )
    val scope = rememberCoroutineScope()
    val visibleMonth = calendarState.firstVisibleMonth
    val events =
        remember(visibleMonth) {
            val previous = visibleMonth.firstDay.minus(1, DateTimeUnit.MONTH)
            val next = visibleMonth.firstDay.plus(1, DateTimeUnit.MONTH)
            SampleData.eventsFor(YearMonth(previous.year, previous.month)) +
                SampleData.eventsFor(visibleMonth) +
                SampleData.eventsFor(YearMonth(next.year, next.month))
        }
    val decorators =
        listOf(
            DayDecorators.eventDots { date -> events[date].orEmpty().map { it.color }.distinct() },
            DayDecorators.badge { date -> events[date].orEmpty().size },
        )
    val selectedDay = selection.selection.single ?: today
    val dayEvents = events[selectedDay].orEmpty()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CalendarNavHeader(state = calendarState, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                scope.launch { calendarState.animateScrollToToday() }
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
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Text(
                        text =
                            if (selectedDay == today) {
                                "TODAY · ${selectedDay.headerLabel()}"
                            } else {
                                selectedDay.headerLabel().uppercase()
                            },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (dayEvents.isEmpty()) {
                    item {
                        Text(
                            "No events",
                            Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(count = dayEvents.size) { index -> EventRow(dayEvents[index]) }
                }
            }
        }
    }
}

private fun LocalDate.headerLabel(): String = toJavaLocalDate().format(DateTimeFormatter.ofPattern("EEE, MMM d"))

@Composable
private fun EventRow(event: Event) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(36.dp).background(event.color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(12.dp))
        Text(
            event.time,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Text(event.title, style = MaterialTheme.typography.bodyLarge)
    }
}
