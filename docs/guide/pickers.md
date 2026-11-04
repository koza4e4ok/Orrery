# Date picker dialogs

M3-shaped dialog wrappers — `OrreryDatePickerDialog` (compact, single date) and
`OrreryDateRangePickerDialog` (full-screen, drag-select range) — with the same
`onDismissRequest`/`confirmButton` slot API as `androidx.compose.material3.DatePickerDialog`
(the compact dialog also takes a `dismissButton` slot; the range dialog dismisses via its
close glyph instead), plus a locale-aware text-input mode:

```kotlin
@Composable
fun PickDateButton() {
    var showDialog by remember { mutableStateOf(false) }
    val state = rememberOrreryDatePickerState()

    Button(onClick = { showDialog = true }) { Text("Pick date") }

    if (showDialog) {
        OrreryDatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = { showDialog = false },
                    enabled = state.selectedDate != null,
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } },
            state = state,
        )
    }
}
```

Read `state.selectedDate` (or `selectedStartDate`/`selectedEndDate` on the range state) in
your confirm button. The state factories accept an initial date/range, a
`yearRange`, `disabledDates`, and (range only) `minDays`/`maxDays` plus the
[booking guardrails](selection.md#booking-guardrails) `disallowDisabledInRange`,
`minRangeDays` and `maxRangeDays`; programmatic writes and typed input are validated
like taps. The headline slides up or down when the selected date moves forward or back. Both dialogs localize entirely through
`DatePickerStrings` — override any message via `DatePickerDefaults.strings(...)`.

```kotlin
val range = rememberOrreryDateRangePickerState(minDays = 2, disabledDates = blackoutDates)

OrreryDateRangePickerDialog(
    onDismissRequest = { showDialog = false },
    confirmButton = {
        TextButton(onClick = { save(range.selectedStartDate, range.selectedEndDate) }) {
            Text("Save")
        }
    },
    state = range,
    strings = DatePickerDefaults.strings(rangeTitle = "Select your stay"),
)
```

Add quick-pick chips above the range calendar with `presets`.
`DatePickerDefaults.rangePresets()` gives localized "Last 7 days", "Last 30 days",
"This week" and "This month" built from `SelectionPresets`; mix in your own
`DateRangePreset(label, range)`. A chip that breaks `minDays`/`maxDays`, bounds or
disabled dates is ignored on tap:

```kotlin
OrreryDateRangePickerDialog(
    onDismissRequest = { showDialog = false },
    confirmButton = { /* ... */ },
    state = range,
    presets = DatePickerDefaults.rangePresets() +
        DateRangePreset("Next 14 days", today..today.plus(13, DateTimeUnit.DAY)),
)
```

For a date and a time of day, `OrreryDateTimePickerDialog` shows the date picker
first, then **Next** opens a Material 3 time dial (with a keyboard toggle). Tap the
date or time in the headline to jump back. `bounds` and `disabled` restrict the date;
`is24Hour` defaults to the device's clock (the "Use 24-hour format" setting on Android,
the locale elsewhere), and the headline time is localized ("14:05", "2:05 PM",
"下午2:05"). Initial values are read once; write to `state.hour`, `state.minute` or
`state.is24Hour` to change them later. Pass `dateStrings` to localize the date step.
The result is a `LocalDateTime`, so attach the `TimeZone` you need:

```kotlin
val state = rememberOrreryDateTimePickerState(bounds = today..today.plus(90, DateTimeUnit.DAY))

OrreryDateTimePickerDialog(
    onDismissRequest = { showDialog = false },
    confirmButton = {
        TextButton(onClick = { book(state.selectedDateTime?.toInstant(TimeZone.currentSystemDefault())) }) {
            Text("Book")
        }
    },
    dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } },
    state = state,
)
```

For a month and year without a day, such as a card expiry date, use
`OrreryMonthPickerDialog`. It opens on the month grid of the selected (or current)
year; tap the year to zoom out to years and decades. Months outside `range` are
disabled, and `DatePickerDefaults.monthRange()` (the default) spans the current
year ±100:

```kotlin
val thisMonth = currentYearMonth()
val expiry = rememberOrreryMonthPickerState(range = thisMonth..thisMonth.plus(20, DateTimeUnit.YEAR))

OrreryMonthPickerDialog(
    onDismissRequest = { showDialog = false },
    confirmButton = {
        TextButton(
            onClick = { save(expiry.selectedMonth) },
            enabled = expiry.selectedMonth != null,
        ) { Text("OK") }
    },
    state = expiry,
)
```
