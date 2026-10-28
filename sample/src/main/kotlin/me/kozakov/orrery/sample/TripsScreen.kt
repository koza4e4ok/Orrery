package me.kozakov.orrery.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toJavaLocalDate
import me.kozakov.orrery.compose.CalendarDefaults
import me.kozakov.orrery.compose.DefaultDay
import me.kozakov.orrery.compose.VerticalCalendar
import me.kozakov.orrery.compose.currentDate
import me.kozakov.orrery.compose.rememberCalendarSelectionState
import me.kozakov.orrery.compose.rememberCalendarState
import me.kozakov.orrery.core.CalendarPages
import me.kozakov.orrery.core.DayInfo
import me.kozakov.orrery.core.SelectionEvent
import me.kozakov.orrery.core.SelectionMode
import me.kozakov.orrery.core.SelectionPresets
import java.time.format.DateTimeFormatter

/**
 * How a tap picks the stay: a drag-selected range, a whole week or a whole month.
 * Week leaves firstDayOfWeek null, so it follows the locale like the calendar.
 */
private enum class StayMode(
    val label: String,
    val mode: SelectionMode,
) {
    Range("Range", SelectionMode.Range(minDays = 2)),
    Week("Week", SelectionMode.Week()),
    Month("Month", SelectionMode.Month()),
}

/** Hotel-booking flow: drag-select a stay (or a week/month), nightly prices, blackout dates. */
@Composable
internal fun TripsScreen() {
    val today = remember { currentDate() }
    val disabled = remember(today) { SampleData.blackoutDates(today) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var stayMode by rememberSaveable { mutableStateOf(StayMode.Range) }
    val selection =
        rememberCalendarSelectionState(
            mode = stayMode.mode,
            disabled = disabled,
            onEvent = { event ->
                val message =
                    when (event) {
                        is SelectionEvent.RangeTooShort -> "Minimum stay is one night"
                        is SelectionEvent.Intercepted -> "That range includes an unavailable date"
                        else -> "That selection isn't available"
                    }
                scope.launch { snackbar.showSnackbar(message) }
            },
        )
    val shapes =
        CalendarDefaults.dayShapes(
            dayShape = RoundedCornerShape(12.dp),
            selectedShape = RoundedCornerShape(12.dp),
        )
    val startMonth = remember(today) { YearMonth(today.year, today.month) }

    // The app-level Scaffold already pads for the system bars.
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            TripSummaryBar(
                range = selection.selection.range,
                onConfirm = {
                    selection.selection.range?.let { range ->
                        val nights = range.start.daysUntil(range.endInclusive)
                        scope.launch {
                            snackbar.showSnackbar(
                                "Booked $nights night${if (nights == 1) "" else "s"} · $${SampleData.rangePrice(
                                    range,
                                )}",
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StayMode.entries.forEach { option ->
                    FilterChip(
                        selected = option == stayMode,
                        onClick = { stayMode = option },
                        label = { Text(option.label) },
                        modifier = Modifier.testTag("stay_mode_${option.name.lowercase()}"),
                    )
                }
            }
            Row(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(
                    onClick = { selection.set(SelectionPresets.nextWeekend(today)) },
                    label = { Text("Weekend") },
                )
                AssistChip(
                    onClick = { selection.set(SelectionPresets.nextDays(today, 7)) },
                    label = { Text("Next 7 days") },
                )
            }
            VerticalCalendar(
                state =
                    rememberCalendarState(
                        startMonth = startMonth,
                        endMonth = CalendarPages.monthAt(startMonth, 5),
                    ),
                modifier = Modifier.testTag("trips_calendar"),
                stickyMonthHeaders = true,
                dragSelection = selection,
                dayContent = { day ->
                    DefaultDay(
                        day = day,
                        selectionState = selection,
                        today = today,
                        shapes = shapes,
                        showOutDates = false,
                        info =
                            if (day.date >= today && !selection.isDisabled(day.date)) {
                                DayInfo(label = "$" + SampleData.nightlyPrice(day.date))
                            } else {
                                null
                            },
                    )
                },
            )
        }
    }
}

@Composable
private fun TripSummaryBar(
    range: ClosedRange<LocalDate>?,
    onConfirm: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (range != null) {
                val nights = range.start.daysUntil(range.endInclusive)
                Column(Modifier.weight(1f)) {
                    Text(
                        "${range.start.shortLabel()} → ${range.endInclusive.shortLabel()}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "$nights night${if (nights == 1) "" else "s"} · $${SampleData.rangePrice(range)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    "Select your stay",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onConfirm, enabled = range != null) { Text("Confirm") }
        }
    }
}

private fun LocalDate.shortLabel(): String = toJavaLocalDate().format(DateTimeFormatter.ofPattern("MMM d"))
