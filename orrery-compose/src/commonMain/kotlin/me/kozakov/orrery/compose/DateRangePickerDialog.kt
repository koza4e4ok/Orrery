package me.kozakov.orrery.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/**
 * Full-screen Material-3-shaped date range picker dialog over Orrery's
 * calendar. Selection lives in [state]; read state.selectedStartDate /
 * state.selectedEndDate in your confirm button. The headline toggles
 * between calendar picking and locale-formatted text input for both
 * endpoints. There is no dismiss-button slot — the close glyph in the
 * top bar dismisses. Strings localize via [strings].
 *
 * [presets] adds a horizontally scrolling row of chips above the calendar
 * (calendar mode only); tapping one selects its range and scrolls to it.
 * Pass [DatePickerDefaults.rangePresets] for the standard set; the default
 * empty list shows no row.
 */
@Composable
public fun OrreryDateRangePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    state: OrreryDateRangePickerState = rememberOrreryDateRangePickerState(),
    modifier: Modifier = Modifier,
    colors: CalendarDayColors = CalendarDefaults.dayColors(),
    shapes: CalendarDayShapes = CalendarDefaults.dayShapes(),
    today: LocalDate = currentDate(),
    strings: DatePickerStrings = DatePickerDefaults.strings(),
    presets: List<DateRangePreset> = emptyList(),
) {
    val scope = rememberCoroutineScope()
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = modifier.fillMaxSize()) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    IconButton(onClick = onDismissRequest) {
                        CloseGlyph(contentDescription = strings.closeDescription)
                    }
                    Text(
                        strings.rangeTitle,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    confirmButton()
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 24.dp),
                ) {
                    val start =
                        state.selectedStartDate?.let { PlatformDateFormat.formatDate(it, DateFormattingStyle.Medium) }
                            ?: strings.rangeStartPlaceholder
                    val end =
                        state.selectedEndDate?.let { PlatformDateFormat.formatDate(it, DateFormattingStyle.Medium) }
                            ?: strings.rangeEndPlaceholder
                    Text(
                        "$start – $end",
                        style = MaterialTheme.typography.headlineSmall,
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
                            state.selectedStartDate?.let { date ->
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
                // The body takes only the height left under the top bar, so on short
                // windows the calendar (or the scrolling input pane) shrinks instead of
                // pushing anything out of the dialog.
                AnimatedContent(
                    targetState = state.displayMode,
                    transitionSpec = { fadeIn().togetherWith(fadeOut()) },
                    label = "rangePickerMode",
                    modifier = Modifier.weight(1f),
                ) { mode ->
                    when (mode) {
                        DatePickerDisplayMode.Picker -> {
                            Column {
                                if (presets.isNotEmpty()) {
                                    RangePresetChips(presets, state) { start ->
                                        val month = YearMonth(start.year, start.month)
                                        scope.launch { state.calendar.scrollToMonth(month) }
                                    }
                                }
                                val initialFocus = rememberInitialDayFocus(state.selectedStartDate ?: today)
                                VerticalCalendar(
                                    state = state.calendar,
                                    stickyMonthHeaders = true,
                                    dragSelection = state.selection,
                                ) { day ->
                                    DefaultDay(
                                        day = day,
                                        modifier = initialFocus(day),
                                        selectionState = state.selection,
                                        today = today,
                                        colors = colors,
                                        shapes = shapes,
                                    )
                                }
                            }
                        }

                        DatePickerDisplayMode.Input -> {
                            RangeInputPane(state, strings)
                        }
                    }
                }
            }
        }
    }
}

@Deprecated("Kept for binary compatibility; use the overload with presets.", level = DeprecationLevel.HIDDEN)
@Composable
public fun OrreryDateRangePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    state: OrreryDateRangePickerState = rememberOrreryDateRangePickerState(),
    modifier: Modifier = Modifier,
    colors: CalendarDayColors = CalendarDefaults.dayColors(),
    shapes: CalendarDayShapes = CalendarDefaults.dayShapes(),
    today: LocalDate = currentDate(),
    strings: DatePickerStrings = DatePickerDefaults.strings(),
) {
    OrreryDateRangePickerDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        state = state,
        modifier = modifier,
        colors = colors,
        shapes = shapes,
        today = today,
        strings = strings,
        presets = emptyList(),
    )
}

/**
 * Preset chip row of the range dialog. A chip shows as selected while the
 * state's range equals its preset; [onApplied] receives the new start date
 * when the selection engine accepted the preset.
 */
@Composable
private fun RangePresetChips(
    presets: List<DateRangePreset>,
    state: OrreryDateRangePickerState,
    onApplied: (LocalDate) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
    ) {
        presets.forEach { preset ->
            val start = preset.range.start
            val end = preset.range.endInclusive
            FilterChip(
                selected = state.selectedStartDate == start && state.selectedEndDate == end,
                onClick = {
                    state.setSelection(start, end)
                    if (state.selectedStartDate == start && state.selectedEndDate == end) onApplied(start)
                },
                label = { Text(preset.label) },
            )
        }
    }
}

/**
 * Start/end text-input face of the range dialog. Local text state
 * (`rememberSaveable`-friendly `String`s, since `LocalDate?` has no
 * built-in saver) tracks each field; every keystroke proposes the
 * current pair to [state] through the selection engine.
 */
@Composable
private fun RangeInputPane(
    state: OrreryDateRangePickerState,
    strings: DatePickerStrings,
) {
    var startText by rememberSaveable { mutableStateOf(state.selectedStartDate?.toString() ?: "") }
    var endText by rememberSaveable { mutableStateOf(state.selectedEndDate?.toString() ?: "") }

    fun parsedStart(): LocalDate? = startText.takeIf(String::isNotEmpty)?.let(LocalDate::parse)

    fun parsedEnd(): LocalDate? = endText.takeIf(String::isNotEmpty)?.let(LocalDate::parse)

    fun apply() {
        state.setSelection(parsedStart(), parsedEnd())
    }

    val start = parsedStart()
    val end = parsedEnd()
    // On rejection the engine keeps the PREVIOUS selection, so "did the
    // proposed pair apply?" is a comparison, not a null check.
    val pairApplied = state.selectedStartDate == start && state.selectedEndDate == end
    val rangeError = if (start != null && end != null && !pairApplied) strings.invalidRangeError else null
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        DateInputField(
            value = start,
            onValueChange = {
                startText = it?.toString() ?: ""
                apply()
            },
            label = strings.rangeStartInputLabel,
            bounds = state.bounds,
            isDisabled = state.selection::isDisabled,
            strings = strings,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            requestInitialFocus = true,
        )
        DateInputField(
            value = end,
            onValueChange = {
                endText = it?.toString() ?: ""
                apply()
            },
            label = strings.rangeEndInputLabel,
            bounds = state.bounds,
            isDisabled = state.selection::isDisabled,
            strings = strings,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            externalError = rangeError,
        )
    }
}
