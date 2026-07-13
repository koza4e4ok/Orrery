package me.kozakov.orrery.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.compose.CalendarDefaults
import me.kozakov.orrery.compose.DayDecorators
import me.kozakov.orrery.compose.DefaultDay
import me.kozakov.orrery.compose.HorizontalCalendar
import me.kozakov.orrery.compose.currentDate
import me.kozakov.orrery.compose.rememberAnimatedDayValues
import me.kozakov.orrery.compose.rememberCalendarState

/** Habit tracker: per-habit animated progress rings, streaks, overall heatmap. */
@Composable
internal fun HabitsScreen() {
    val today = remember { currentDate() }
    val month = remember(today) { YearMonth(today.year, today.month) }
    var selectedHabit by remember { mutableStateOf(SampleData.habits.first()) }
    val progress =
        remember(selectedHabit, month, today) {
            SampleData.habitProgress(selectedHabit, month, today)
        }
    val animatedProgress = rememberAnimatedDayValues(progress)
    val ringDecorators =
        listOf(DayDecorators.progressRing(color = selectedHabit.color, progress = animatedProgress))
    val overall =
        remember(month, today) {
            val maps = SampleData.habits.map { SampleData.habitProgress(it, month, today) }
            buildMap<LocalDate, Float> {
                maps.flatMap { it.keys }.distinct().forEach { date ->
                    put(date, maps.mapNotNull { it[date] }.average().toFloat())
                }
            }
        }
    val heatDecorators = listOf(DayDecorators.heatmap { date -> overall[date] })
    val heatFill = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SampleData.habits.forEach { habit ->
                FilterChip(
                    selected = habit == selectedHabit,
                    onClick = { selectedHabit = habit },
                    label = { Text(habit.name) },
                )
            }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                "${SampleData.streak(progress, today)}-day streak",
                style = MaterialTheme.typography.titleMedium,
                color = selectedHabit.color,
            )
            Spacer(Modifier.width(16.dp))
            Text(
                "${SampleData.monthCompletion(progress)}% this month",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalCalendar(
            state = rememberCalendarState(),
            dayContent = { day ->
                DefaultDay(day = day, today = today, decorators = ringDecorators)
            },
        )
        Text("All habits", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
        HorizontalCalendar(
            state = rememberCalendarState(),
            dayContent = { day ->
                val heat = overall[day.date]
                val colors =
                    if (heat != null) {
                        val onHeat =
                            if (lerp(surface, heatFill, heat.coerceIn(0f, 1f)).luminance() < 0.5f) {
                                Color.White
                            } else {
                                Color.Black
                            }
                        CalendarDefaults.dayColors(contentColor = onHeat, todayContentColor = onHeat)
                    } else {
                        CalendarDefaults.dayColors()
                    }
                DefaultDay(day = day, today = today, colors = colors, decorators = heatDecorators)
            },
        )
    }
}
