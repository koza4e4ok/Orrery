package me.kozakov.orrery.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus
import me.kozakov.orrery.compose.CalendarDefaults
import me.kozakov.orrery.compose.CalendarNavHeader
import me.kozakov.orrery.compose.DayDecorators
import me.kozakov.orrery.compose.DefaultDay
import me.kozakov.orrery.compose.HeatmapCalendar
import me.kozakov.orrery.compose.HeatmapMonth
import me.kozakov.orrery.compose.HorizontalCalendar
import me.kozakov.orrery.compose.currentDate
import me.kozakov.orrery.compose.rememberAnimatedDayValues
import me.kozakov.orrery.compose.rememberCalendarState
import me.kozakov.orrery.compose.rememberHeatmapCalendarState

/** Habit tracker: per-habit animated progress rings, streaks, overall heatmap. */
@Composable
internal fun HabitsScreen() {
    val today = remember { currentDate() }
    val month = remember(today) { YearMonth(today.year, today.month) }
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val selectedHabit = SampleData.habits[selectedIndex]
    val progress =
        remember(selectedHabit, month, today) {
            SampleData.habitProgress(selectedHabit, month, today)
        }
    // Each calendar shows data for the month it is paged to, not only the current one.
    val ringState = rememberCalendarState()
    val heatState = rememberCalendarState()
    val ringMonth = ringState.firstVisibleMonth
    val heatMonth = heatState.firstVisibleMonth
    val ringProgress =
        remember(selectedHabit, ringMonth, today) {
            SampleData.habitProgress(selectedHabit, ringMonth, today)
        }
    val animatedProgress = rememberAnimatedDayValues(ringProgress)
    val ringDecorators =
        listOf(DayDecorators.progressRing(color = selectedHabit.color, progress = animatedProgress))
    val overall =
        remember(heatMonth, today) {
            val maps = SampleData.habits.map { SampleData.habitProgress(it, heatMonth, today) }
            buildMap<LocalDate, Float> {
                maps.flatMap { it.keys }.distinct().forEach { date ->
                    put(date, maps.mapNotNull { it[date] }.average().toFloat())
                }
            }
        }
    val heatDecorators = listOf(DayDecorators.heatmap { date -> overall[date] })
    val heatFill = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val scope = rememberCoroutineScope()
    val streakCount = SampleData.streak(progress, today)
    val completionPct = SampleData.monthCompletion(progress)

    Column(Modifier.fillMaxSize()) {
        SampleHeroHeader(
            date = today,
            today = today,
            subtitle = "${selectedHabit.name} · $streakCount-day streak · $completionPct%",
            accentColor = selectedHabit.color,
            onTodayClick = {
                scope.launch {
                    ringState.animateScrollToToday()
                    heatState.animateScrollToToday()
                }
            },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SampleData.habits.forEachIndexed { index, habit ->
                    FilterChip(
                        selected = index == selectedIndex,
                        onClick = { selectedIndex = index },
                        label = { Text(habit.name) },
                    )
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text(
                            "$streakCount-day streak",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = selectedHabit.color,
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            "$completionPct% this month",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    CalendarNavHeader(state = ringState)
                    HorizontalCalendar(
                        state = ringState,
                        dayContent = { day ->
                            DefaultDay(day = day, today = today, decorators = ringDecorators)
                        },
                    )
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(
                        "All habits",
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    CalendarNavHeader(state = heatState)
                    HorizontalCalendar(
                        state = heatState,
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
            val yearHeat =
                remember(today) {
                    buildMap<LocalDate, Float> {
                        repeat(13) { back ->
                            val m = month.plus(-back, DateTimeUnit.MONTH)
                            val maps = SampleData.habits.map { SampleData.habitProgress(it, m, today) }
                            maps.flatMap { it.keys }.distinct().forEach { date ->
                                put(date, maps.mapNotNull { it[date] }.average().toFloat())
                            }
                        }
                    }
                }
            var pickedDay by rememberSaveable { mutableStateOf<String?>(null) }
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Year in review",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    HeatmapCalendar(
                        intensity = { date -> yearHeat[date] },
                        state = rememberHeatmapCalendarState(),
                        onDayClick = { pickedDay = it.toString() },
                    )
                    pickedDay?.let {
                        Text(
                            "Selected: $it",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "This month",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    HeatmapMonth(
                        yearMonth = month,
                        intensity = { date -> yearHeat[date] },
                        onDayClick = { pickedDay = it.toString() },
                        showDayNumbers = true,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
