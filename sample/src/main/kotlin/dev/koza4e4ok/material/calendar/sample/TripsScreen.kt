package dev.koza4e4ok.material.calendar.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.koza4e4ok.material.calendar.compose.CalendarDefaults
import dev.koza4e4ok.material.calendar.compose.DefaultDay
import dev.koza4e4ok.material.calendar.compose.VerticalCalendar
import dev.koza4e4ok.material.calendar.compose.currentDate
import dev.koza4e4ok.material.calendar.compose.rememberCalendarSelectionState
import dev.koza4e4ok.material.calendar.compose.rememberCalendarState
import dev.koza4e4ok.material.calendar.core.CalendarPages
import dev.koza4e4ok.material.calendar.core.DayInfo
import dev.koza4e4ok.material.calendar.core.SelectionEvent
import dev.koza4e4ok.material.calendar.core.SelectionMode
import dev.koza4e4ok.material.calendar.core.SelectionPresets
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter

/** Hotel-booking flow: drag-select a stay, nightly prices, blackout dates. */
@Composable
internal fun TripsScreen() {
    val today = remember { currentDate() }
    val disabled = remember(today) { SampleData.blackoutDates(today) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val selection =
        rememberCalendarSelectionState(
            mode = SelectionMode.Range(minDays = 2),
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

    Scaffold(
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
