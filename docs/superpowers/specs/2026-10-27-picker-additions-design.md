# Picker Additions: Range Presets, Month Picker, Date+Time Picker — Design

**Date:** 2026-10-27
**Status:** Proposed (awaiting Andrii's sign-off)
**Issue:** #24 (Tracking roadmap: #26)
**Owners:** Ritsuko (design), Rei (visual), Maya (implementation), Asuka (review)

## Context

`orrery-compose` ships `OrreryDatePickerDialog`, `OrreryDateRangePickerDialog` and the
in-place `MonthYearPicker(current, range, onSelect)` (year grid → month grid, decade
zoom). All are Compose Multiplatform (Android, Desktop JVM, iOS, wasmJs) since #20.
Issue #24 asks for three additions, one PR each, range presets first.

| # | Addition | PR | State |
|---|----------|----|-------|
| 1 | `DateRangePreset` chips in `OrreryDateRangePickerDialog` | #52 | Implemented (draft, in review) |
| 2 | `OrreryMonthPickerDialog` (month/year only, e.g. card expiry) | new | This spec |
| 3 | `OrreryDateTimePickerDialog` (date, then time) | new | This spec |

**Not building:** text input for month (MM/YY), time-of-day bounds, minute steps,
time-zone handling, a wheel/spinner style, a standalone time picker (Material 3 already
has `TimePicker`/`TimeInput`), a month-range picker.

---

## 1. Range presets (PR #52, recorded for completeness)

- `DateRangePreset(label: String, range: ClosedRange<LocalDate>)` in `orrery-compose`.
- `OrreryDateRangePickerDialog(..., presets: List<DateRangePreset> = emptyList())` as a new
  trailing parameter; the old signature stays as a `@Deprecated(HIDDEN)` delegating overload.
- Chips (M3 `FilterChip`, horizontal scroll) show in calendar mode only. Tap →
  `state.setSelection(start, end)` → `CalendarSelectionState.set`, so bounds,
  `minDays`/`maxDays` and disabled dates apply; a rejected preset leaves the selection
  unchanged and fires the event callback. A chip shows selected while the range equals it.
- `DatePickerDefaults.rangePresets(today, firstDayOfWeek)` = Last 7 days, Last 30 days,
  This week, This month, built from `SelectionPresets` in `orrery-core`.

Nothing further to design; what remains is review and merge of #52.

---

## 2. `OrreryMonthPickerDialog`

### API

```kotlin
@Stable
public class OrreryMonthPickerState internal constructor(/* ... */) {
    public var selectedMonth: YearMonth?            // null until picked
        private set
    public val range: ClosedRange<YearMonth>
    public fun select(month: YearMonth?)            // ignored when outside range
}

@Composable
public fun rememberOrreryMonthPickerState(
    initialSelectedMonth: YearMonth? = null,
    range: ClosedRange<YearMonth> = DatePickerDefaults.monthRange(), // current year ±100
): OrreryMonthPickerState

@Composable
public fun OrreryMonthPickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    state: OrreryMonthPickerState = rememberOrreryMonthPickerState(),
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    today: LocalDate = currentDate(),
    strings: MonthPickerStrings = DatePickerDefaults.monthPickerStrings(),
)
```

### Behavior

- Same shell as `OrreryDatePickerDialog`: `Dialog(usePlatformDefaultWidth = false)`,
  `Surface(extraLarge, tonalElevation = 6.dp, requiredWidth(360.dp))`, title
  (`labelLarge`), headline (`headlineLarge`) = `PlatformDateFormat.yearMonthTitle(selected)`
  or `strings.headlinePlaceholder`, then the grid, then the buttons.
- The body reuses `MonthYearPicker`'s grids. Extract its body into an internal
  `MonthYearPickerContent(current, selected, range, startInMonths, onSelect)`. The public
  `MonthYearPicker` keeps its signature and behavior and delegates with
  `selected = null, startInMonths = false`.
- The dialog opens on the month grid of the year of `selectedMonth ?: today` (clamped to
  `range`). Tapping the year title zooms out to the year grid, then decades (existing).
- Tapping a month calls `state.select(month)` and stays on the month grid. The selected
  month is highlighted like a selected day (`primary`); the current month gets the
  "today" outline. Months outside `range` are disabled (existing logic).
- `selectedMonth` survives configuration change (`rememberSaveable`; saver stores
  `year * 12 + monthNumber - 1`).
- Card-expiry recipe in KDoc and README: `range = thisMonth..thisMonth.plus(20, YEAR)`.

### Strings

`MonthPickerStrings(title, headlinePlaceholder)`: `orrery_month_picker_title` ("Select
month") and `orrery_month_picker_headline_placeholder` ("Month") in all six bundled
languages (Android resources and the non-Android tables).

### Tests

- Common: state selection, out-of-range ignored, initial-year clamping, saver round-trip.
- Android host: opens on the month grid; selecting updates the headline; disabled months
  are not clickable; year-title zoom-out still works.
- Roborazzi: light and dark snapshot with a selected month.
- Existing `MonthYearPicker` snapshots stay unchanged (proves the refactor is neutral).

---

## 3. `OrreryDateTimePickerDialog`

Two steps in one dialog: **Date** (Orrery calendar/text input, as in
`OrreryDatePickerDialog`) → **Time** (Material 3 `TimePicker` dial with a toggle to
`TimeInput`). One dialog avoids dialog-on-dialog hand-offs, which behave differently on
Desktop, iOS and wasm (focus, scrim, back handling).

### API

```kotlin
public enum class DateTimePickerStep { Date, Time }

@Stable
public class OrreryDateTimePickerState internal constructor(/* ... */) {
    public val dateState: OrreryDatePickerState
    public var hour: Int                            // 0..23
    public var minute: Int                          // 0..59
    public var is24Hour: Boolean
    public var step: DateTimePickerStep
    public val selectedDateTime: LocalDateTime?     // null until a date is picked
}

@Composable
public fun rememberOrreryDateTimePickerState(
    initialDateTime: LocalDateTime? = null,
    bounds: ClosedRange<LocalDate>? = null,
    disabled: DisabledDates = DisabledDates.None,
    is24Hour: Boolean = PlatformDateFormat.is24HourClock(),
): OrreryDateTimePickerState

@Composable
public fun OrreryDateTimePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,          // read state.selectedDateTime
    state: OrreryDateTimePickerState = rememberOrreryDateTimePickerState(),
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    colors: CalendarDayColors = CalendarDefaults.dayColors(),
    shapes: CalendarDayShapes = CalendarDefaults.dayShapes(),
    today: LocalDate = currentDate(),
    strings: DateTimePickerStrings = DatePickerDefaults.dateTimeStrings(),
)
```

### Behavior

- Headline shows the formatted date (`formatDate(Medium)`) and time; tapping either part
  jumps to that step.
- Date step: the body of `OrreryDatePickerDialog`, extracted to an internal
  `DatePickerContent` shared by both dialogs (no visual change to the date dialog).
  Buttons: dismiss and **Next** (enabled once a date is picked).
- Time step: Material 3 `TimePicker`/`TimeInput` driven by an internal
  `rememberTimePickerState(hour, minute, is24Hour)`, synced back into our state.
  Buttons: **Back**, dismiss, `confirmButton`.
- The public API exposes only Orrery and kotlinx-datetime types. If `TimePicker` is still
  `@ExperimentalMaterial3Api` in the pinned CMP Material 3 (1.9.0), opt in inside the
  file; consumers need no opt-in.
- New `expect fun PlatformDateFormat.is24HourClock(): Boolean` with actuals: Android
  `DateFormat.getBestDateTimePattern(locale, "jm")` contains `H`/`k`; Desktop the
  `DateFormat.getTimeInstance(SHORT, locale)` pattern; iOS
  `NSDateFormatter.dateFormatFromTemplate("j", 0, locale)`; wasmJs
  `Intl.DateTimeFormat(locale, {hour: "numeric"}).resolvedOptions().hourCycle`.
- The result is a `LocalDateTime`; the caller attaches a `TimeZone`. Bounds and disabled
  dates apply to the date only.
- The saver stores date (epoch day), hour, minute, step and is24Hour.

### Strings

`DateTimePickerStrings(title, next, back, timeTitle, switchToClock, switchToKeyboard)`,
keys `orrery_date_time_picker_*`, all six languages.

### Tests

- Host: Next disabled until a date is picked; Back keeps the date; time edits update
  `selectedDateTime`; bounds and disabled dates apply; saver round-trip; `is24Hour` honored.
- Per target: `is24HourClock()` for `en-US` (false) and `de-DE` (true).
- Roborazzi: date step and time step, light.

---

## Rollout

1. Merge #52 (presets).
2. PR "feat(compose): OrreryMonthPickerDialog (#24)".
3. PR "feat(compose): OrreryDateTimePickerDialog (#24)".

Each PR updates the ABI dumps, CHANGELOG `[Unreleased]`, the README picker section and the
sample Pickers screen. Close #24 after step 3.

## Open decisions for Andrii

1. Month dialog opens on the month grid (proposed) or on the year grid like
   `MonthYearPicker`?
2. Date+time: one two-step dialog (proposed) or two chained dialogs?
3. Keep time-of-day bounds (min/max time on a given date) out of scope for now (proposed)?
