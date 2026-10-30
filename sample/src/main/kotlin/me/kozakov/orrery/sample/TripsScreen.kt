package me.kozakov.orrery.sample

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaLocalDate
import me.kozakov.orrery.compose.CalendarDefaults
import me.kozakov.orrery.compose.CalendarStyle
import me.kozakov.orrery.compose.DayDecorators
import me.kozakov.orrery.compose.DefaultDay
import me.kozakov.orrery.compose.RangeEndpointStyle
import me.kozakov.orrery.compose.VerticalCalendar
import me.kozakov.orrery.compose.currentDate
import me.kozakov.orrery.compose.rememberCalendarSelectionState
import me.kozakov.orrery.compose.rememberCalendarState
import me.kozakov.orrery.core.CalendarPages
import me.kozakov.orrery.core.DayInfo
import me.kozakov.orrery.core.Selection
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

/** How check-in / check-out days are drawn: whole cells, or PM / AM halves. */
private enum class EndpointOption(
    val label: String,
    val style: RangeEndpointStyle,
) {
    Diagonal("Diagonal", RangeEndpointStyle.SplitDiagonal),
    Split("Split", RangeEndpointStyle.SplitHorizontal),
    Full("Full cell", RangeEndpointStyle.FullCell),
}

/**
 * Hotel-booking flow: drag-select a stay (or a week/month), nightly prices, blackout dates,
 * and hospitality range endpoints (check-in PM half, check-out AM half, turnover days).
 */
@Composable
internal fun TripsScreen() {
    val today = remember { currentDate() }
    val disabled = remember(today) { SampleData.blackoutDates(today) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var stayMode by rememberSaveable { mutableStateOf(StayMode.Range) }
    var endpointOption by rememberSaveable { mutableStateOf(EndpointOption.Diagonal) }
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
    val dayStyle =
        if (endpointOption.style == RangeEndpointStyle.FullCell) {
            CalendarStyle(
                colors = CalendarDefaults.dayColors(),
                shapes =
                    CalendarDefaults.dayShapes(
                        dayShape = RoundedCornerShape(12.dp),
                        selectedShape = RoundedCornerShape(12.dp),
                    ),
            )
        } else {
            CalendarDefaults.hospitalityStyle(rangeEndpointStyle = endpointOption.style)
        }
    // Back-to-back bookings: one guest checks out in the morning, the next checks in after noon.
    val turnoverDates =
        remember(today) {
            setOf(today.plus(20, DateTimeUnit.DAY), today.plus(27, DateTimeUnit.DAY))
        }
    val startMonth = remember(today) { YearMonth(today.year, today.month) }
    val calendarState =
        rememberCalendarState(
            startMonth = startMonth,
            endMonth = CalendarPages.monthAt(startMonth, 5),
        )

    val range = selection.selection.range
    val activeDate = selection.selection.rangeStart ?: today
    val heroSubtitle =
        if (range != null) {
            val nights = range.start.daysUntil(range.endInclusive)
            "$nights night${if (nights == 1) "" else "s"} · $${SampleData.rangePrice(range)}"
        } else if (selection.selection.rangeStart != null) {
            "Pick checkout date"
        } else {
            "From $${SampleData.nightlyPrice(today)} / night"
        }

    val previewTarget =
        selection.selection.rangeStart?.let { anchor ->
            if (selection.selection.rangeEnd == null) {
                val candidate = anchor.plus(3, DateTimeUnit.DAY)
                if (!selection.isDisabled(candidate)) candidate else null
            } else {
                null
            }
        }
    val previewDecorator =
        DayDecorators.rangePreview(
            selectionState = selection,
            targetDate = previewTarget,
        )

    // The app-level Scaffold already pads for the system bars.
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            TripSummaryBar(
                range = selection.selection.range,
                onConfirm = {
                    selection.selection.range?.let { selectedRange ->
                        val nights = selectedRange.start.daysUntil(selectedRange.endInclusive)
                        scope.launch {
                            snackbar.showSnackbar(
                                "Booked $nights night${if (nights == 1) "" else "s"} · $${SampleData.rangePrice(
                                    selectedRange,
                                )}",
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SampleHeroHeader(
                date = activeDate,
                today = today,
                subtitle = heroSubtitle,
                onTodayClick = {
                    scope.launch { calendarState.animateScrollToMonth(startMonth) }
                },
            )
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
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
                Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
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
                if (selection.selection != Selection.Empty) {
                    AssistChip(
                        onClick = { selection.clear() },
                        label = { Text("Clear") },
                    )
                }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Check-in/out",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                EndpointOption.entries.forEach { option ->
                    FilterChip(
                        selected = option == endpointOption,
                        onClick = { endpointOption = option },
                        label = { Text(option.label) },
                        modifier = Modifier.testTag("endpoint_style_${option.name.lowercase()}"),
                    )
                }
            }
            HorizontalDivider(Modifier.padding(top = 4.dp))
            VerticalCalendar(
                state = calendarState,
                modifier = Modifier.weight(1f).testTag("trips_calendar"),
                stickyMonthHeaders = true,
                dragSelection = selection,
                dayContent = { day ->
                    DefaultDay(
                        day = day,
                        style = dayStyle,
                        turnoverDates = turnoverDates,
                        selectionState = selection,
                        today = today,
                        showOutDates = false,
                        decorators = listOf(previewDecorator),
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
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
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
