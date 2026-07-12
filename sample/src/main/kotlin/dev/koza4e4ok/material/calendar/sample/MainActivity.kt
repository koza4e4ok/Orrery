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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.koza4e4ok.material.calendar.compose.DayDecorators
import dev.koza4e4ok.material.calendar.compose.DefaultDay
import dev.koza4e4ok.material.calendar.compose.HorizontalCalendar
import dev.koza4e4ok.material.calendar.compose.currentDate
import dev.koza4e4ok.material.calendar.compose.rememberAnimatedDayValues
import dev.koza4e4ok.material.calendar.compose.rememberCalendarSelectionState
import dev.koza4e4ok.material.calendar.compose.rememberCalendarState
import dev.koza4e4ok.material.calendar.core.SelectionMode
import dev.koza4e4ok.material.calendar.lunar.LunarDayInfoProvider
import kotlinx.datetime.LocalDate
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
                            TextButton(onClick = { screen = 1 }) { Text("Trips") }
                            TextButton(onClick = { screen = 2 }) { Text("Habits") }
                            TextButton(onClick = { screen = 3 }) { Text("Lunar") }
                        }
                        when (screen) {
                            0 -> AgendaScreen()
                            1 -> TripsScreen()
                            2 -> HabitsDemo()
                            else -> LunarDemo()
                        }
                    }
                }
            }
        }
    }
}

/** Habit tracker: progress rings for the current month and a completion heatmap. */
@Composable
private fun HabitsDemo() {
    val today = remember { currentDate() }
    var seed by remember { mutableIntStateOf(37) }
    val targets =
        remember(seed, today) {
            (1..28).associate { d ->
                LocalDate(today.year, today.month, d) to ((d * seed) % 101) / 100f
            }
        }
    val animatedProgress = rememberAnimatedDayValues(targets)
    val ringDecorators = listOf(DayDecorators.progressRing(progress = animatedProgress))
    val heatDecorators =
        listOf(
            DayDecorators.heatmap { date ->
                if (date.month == today.month) ((date.day * 53) % 101) / 100f else null
            },
        )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Habit progress",
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = { seed = seed % 89 + 7 }) { Text("Shuffle") }
        }
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
