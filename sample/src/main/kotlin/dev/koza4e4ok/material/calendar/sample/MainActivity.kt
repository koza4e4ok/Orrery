package dev.koza4e4ok.material.calendar.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.koza4e4ok.material.calendar.compose.CollapsibleCalendarScaffold
import dev.koza4e4ok.material.calendar.compose.DefaultDay
import dev.koza4e4ok.material.calendar.compose.HorizontalCalendar
import dev.koza4e4ok.material.calendar.compose.VerticalCalendar
import dev.koza4e4ok.material.calendar.compose.currentDate
import dev.koza4e4ok.material.calendar.compose.displayName
import dev.koza4e4ok.material.calendar.compose.rememberCalendarSelectionState
import dev.koza4e4ok.material.calendar.compose.rememberCalendarState
import dev.koza4e4ok.material.calendar.compose.rememberCollapsibleCalendarState
import dev.koza4e4ok.material.calendar.compose.rememberWeekCalendarState
import dev.koza4e4ok.material.calendar.core.SelectionMode
import dev.koza4e4ok.material.calendar.lunar.LunarDayInfoProvider
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

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
                            TextButton(onClick = { screen = 2 }) { Text("Lunar") }
                        }
                        when (screen) {
                            0 -> AgendaDemo()
                            1 -> VerticalDemo()
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
                    decorator = { d ->
                        if (d.date in events) {
                            drawCircle(
                                color = Color(0xFFE57373),
                                radius = 3.dp.toPx(),
                                center = Offset(size.width / 2, size.height - 6.dp.toPx()),
                            )
                        }
                    },
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
