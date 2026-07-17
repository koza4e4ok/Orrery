package me.kozakov.orrery.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Material-3-shaped date picker dialog over Orrery's calendar. Selection
 * lives in [state]; read state.selectedDate in your confirm button. The
 * headline toggles between calendar picking and locale-formatted text
 * input. Strings localize via [strings].
 */
@Composable
public fun OrreryDatePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    state: OrreryDatePickerState = rememberOrreryDatePickerState(),
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    colors: CalendarDayColors = CalendarDefaults.dayColors(),
    shapes: CalendarDayShapes = CalendarDefaults.dayShapes(),
    today: LocalDate = currentDate(),
    strings: DatePickerStrings = DatePickerDefaults.strings(),
) {
    val headlineFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val scope = rememberCoroutineScope()
    Dialog(
        onDismissRequest = onDismissRequest,
        // Platform default width leaves the Surface's width ambiguous, which thrashes
        // measurement indefinitely once an OutlinedTextField is in the composition.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = modifier.requiredWidth(360.dp),
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    strings.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        state.selectedDate?.toJavaLocalDate()?.format(headlineFormatter)
                            ?: strings.headlinePlaceholder,
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        val next =
                            if (state.displayMode == DatePickerDisplayMode.Picker) {
                                DatePickerDisplayMode.Input
                            } else {
                                DatePickerDisplayMode.Picker
                            }
                        if (next == DatePickerDisplayMode.Picker) {
                            state.selectedDate?.let { date ->
                                scope.launch { state.calendar.scrollToMonth(YearMonth(date.year, date.month)) }
                            }
                        }
                        state.displayMode = next
                    }) {
                        if (state.displayMode == DatePickerDisplayMode.Picker) {
                            KeyboardGlyph(contentDescription = strings.switchToInputDescription)
                        } else {
                            CalendarGlyph(contentDescription = strings.switchToPickerDescription)
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                AnimatedContent(
                    targetState = state.displayMode,
                    transitionSpec = { fadeIn().togetherWith(fadeOut()) },
                    label = "datePickerMode",
                ) { mode ->
                    when (mode) {
                        DatePickerDisplayMode.Picker -> {
                            PickerPane(state, colors, shapes, today, strings)
                        }

                        DatePickerDisplayMode.Input -> {
                            DateInputField(
                                value = state.selectedDate,
                                onValueChange = { state.selectedDate = it },
                                label = strings.inputLabel,
                                bounds = state.bounds,
                                isDisabled = state.selection::isDisabled,
                                strings = strings,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}

@Composable
private fun PickerPane(
    state: OrreryDatePickerState,
    colors: CalendarDayColors,
    shapes: CalendarDayShapes,
    today: LocalDate,
    strings: DatePickerStrings,
) {
    val scope = rememberCoroutineScope()
    var showMonthPicker by rememberSaveable { mutableStateOf(false) }
    Column {
        CalendarNavHeader(
            state = state.calendar,
            onTitleClick = { showMonthPicker = !showMonthPicker },
            previousMonthContentDescription = strings.previousMonthDescription,
            nextMonthContentDescription = strings.nextMonthDescription,
        )
        if (showMonthPicker) {
            MonthYearPicker(
                current = state.displayedMonth,
                range = YearMonth(state.yearRange.first, 1)..YearMonth(state.yearRange.last, 12),
                onSelect = { month ->
                    showMonthPicker = false
                    scope.launch { state.calendar.scrollToMonth(month) }
                },
                modifier = Modifier.height(320.dp),
            )
        } else {
            HorizontalCalendar(state = state.calendar) { day ->
                DefaultDay(
                    day = day,
                    selectionState = state.selection,
                    today = today,
                    colors = colors,
                    shapes = shapes,
                )
            }
        }
    }
}
