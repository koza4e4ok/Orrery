package dev.koza4e4ok.material.calendar.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.koza4e4ok.material.calendar.compose.DefaultDay
import dev.koza4e4ok.material.calendar.compose.HorizontalCalendar
import dev.koza4e4ok.material.calendar.compose.WeekCalendar
import dev.koza4e4ok.material.calendar.compose.currentDate
import dev.koza4e4ok.material.calendar.compose.displayName
import dev.koza4e4ok.material.calendar.compose.rememberCalendarSelectionState
import dev.koza4e4ok.material.calendar.compose.rememberCalendarState
import dev.koza4e4ok.material.calendar.compose.rememberWeekCalendarState
import dev.koza4e4ok.material.calendar.core.SelectionMode
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                DemoScreen()
            }
        }
    }
}

@Composable
private fun DemoScreen() {
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
    val selection = rememberCalendarSelectionState(mode = SelectionMode.Range(maxDays = 14))
    val scope = rememberCoroutineScope()

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = {
                    scope.launch {
                        calendarState.animateScrollToMonth(
                            calendarState.firstVisibleMonth.minus(1, DateTimeUnit.MONTH),
                        )
                    }
                }) { Text("<") }
                Text(
                    text = calendarState.firstVisibleMonth.displayName(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                )
                TextButton(onClick = {
                    scope.launch {
                        calendarState.animateScrollToMonth(
                            calendarState.firstVisibleMonth.plus(1, DateTimeUnit.MONTH),
                        )
                    }
                }) { Text(">") }
            }
            HorizontalCalendar(
                state = calendarState,
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
            )
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text(
                "Week view",
                Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            WeekCalendar(
                state = weekState,
                dayContent = { day ->
                    DefaultDay(day = day, selectionState = selection, today = today)
                },
            )
        }
    }
}
