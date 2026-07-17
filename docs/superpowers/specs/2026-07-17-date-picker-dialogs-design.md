# Date Picker Dialogs (0.3.0) — Design

**Date:** 2026-07-17
**Status:** Approved
**Sub-project:** 2 of 6 (feature roadmap: foundations → **dialogs** → heatmap → paging → providers → schedule view)

## Context

Orrery 0.2.0 ships the calendar grid, selection engine, `CalendarNavHeader`, and `MonthYearPicker`, but no dialog wrappers. People replacing Material 3's stock date pickers must build the dialog chrome themselves. This sub-project ships two M3-shaped dialogs so Orrery works as a drop-in replacement. All code lands in `orrery-compose` (material3 is already a dependency; no new module). Ships as **0.3.0**.

Decisions made during brainstorming:

- **M3-style slot API** — `onDismissRequest` + `confirmButton`/`dismissButton` slots, familiar to `DatePickerDialog` users; button text stays in the caller's hands.
- **Text-input mode included** — both dialogs get M3's picker↔keyboard toggle.
- **Range dialog is full-screen** (M3 `DateRangePickerDialog` style); single-date dialog is compact.
- **New state holders** (`OrreryDatePickerState`, `OrreryDateRangePickerState`) mirroring `rememberDatePickerState`, wrapping Orrery's `CalendarState` + `CalendarSelectionState` internally.

Out of scope: landscape-specific layouts, embedded (non-dialog) picker composables, Compose Multiplatform.

## 1. State holders

New file `orrery-compose/.../DatePickerState.kt`:

```kotlin
public enum class DatePickerDisplayMode { Picker, Input }

@Stable
public class OrreryDatePickerState internal constructor(...) {
    public var selectedDate: LocalDate?            // snapshot-observable; null = nothing chosen
    public var displayMode: DatePickerDisplayMode
    public val displayedMonth: YearMonth           // from the internal CalendarState
}

@Composable
public fun rememberOrreryDatePickerState(
    initialDate: LocalDate? = null,
    initialDisplayedMonth: YearMonth = initialDate?.let { YearMonth(it.year, it.month) } ?: currentYearMonth(),
    yearRange: IntRange = 1971..2055,              // matches CalendarState defaults
    initialDisplayMode: DatePickerDisplayMode = DatePickerDisplayMode.Picker,
    disabledDates: DisabledDates? = null,
): OrreryDatePickerState                            // rememberSaveable-backed
```

`OrreryDateRangePickerState` / `rememberOrreryDateRangePickerState` are the analog with `selectedStartDate`/`selectedEndDate` (both `LocalDate?`) and `minDays: Int = 1`, `maxDays: Int = Int.MAX_VALUE` passed through to `SelectionMode.Range`. Setting `selectedDate`(s) programmatically goes through the selection engine's validation; disabled and out-of-range dates are rejected in both picker and input modes by the same engine.

## 2. Single-date dialog (compact)

New file `orrery-compose/.../DatePickerDialog.kt`:

```kotlin
@Composable
public fun OrreryDatePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    state: OrreryDatePickerState = rememberOrreryDatePickerState(),
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    colors: CalendarDayColors = CalendarDefaults.dayColors(),
    shapes: CalendarDayShapes = CalendarDefaults.dayShapes(),
    strings: DatePickerStrings = DatePickerDefaults.strings(),
)
```

Built on `androidx.compose.ui.window.Dialog` + `Surface` (the same construction as M3's `DatePickerDialog`). Content top-to-bottom:

1. Label (`strings.title`, default "Select date").
2. Headline: the selected date formatted with `DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)` (e.g. "Jul 17, 2026"), or `strings.headlinePlaceholder` when null.
3. Display-mode toggle `IconButton` — Canvas-drawn keyboard/calendar glyphs (the `NavChevron` precedent; no material-icons dependency).
4. `AnimatedContent` on `state.displayMode`:
   - **Picker:** `CalendarNavHeader` (its `onTitleClick` opens `MonthYearPicker` in place — the existing hook) above a `HorizontalCalendar` of `DefaultDay`s wired to the internal selection state (`SelectionMode.Single`).
   - **Input:** the shared text field from §4.
5. Buttons row rendering `dismissButton` (if any) then `confirmButton`.

## 3. Range dialog (full-screen)

New file `orrery-compose/.../DateRangePickerDialog.kt`:

```kotlin
@Composable
public fun OrreryDateRangePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    state: OrreryDateRangePickerState = rememberOrreryDateRangePickerState(),
    modifier: Modifier = Modifier,
    colors: CalendarDayColors = CalendarDefaults.dayColors(),
    shapes: CalendarDayShapes = CalendarDefaults.dayShapes(),
    strings: DatePickerStrings = DatePickerDefaults.strings(),
)
```

`Dialog(properties = DialogProperties(usePlatformDefaultWidth = false))` → full-screen `Surface`, M3 `DateRangePickerDialog` style:

- Top bar: close affordance (Canvas ✕) firing `onDismissRequest`, label (`strings.rangeTitle`, default "Select dates"), and the `confirmButton` slot trailing (M3's "Save" position). No `dismissButton` slot — the ✕ is the dismiss affordance.
- Headline: "start – end" with each side formatted `FormatStyle.MEDIUM` or a per-side placeholder.
- Display-mode toggle.
- **Picker:** weekday header + `VerticalCalendar` (sticky month titles) with `SelectionMode.Range(minDays, maxDays)` and `dragSelection` enabled.
- **Input:** two text fields (start/end) with cross-field validation — end ≥ start, min/max days — plus the per-field validation from §4.

## 4. Text-input mode (shared internals)

New internal file `orrery-compose/.../DateInput.kt`:

- Placeholder/hint pattern from `DateTimeFormatterBuilder.getLocalizedDateTimePattern(FormatStyle.SHORT, null, IsoChronology.INSTANCE, locale)` uppercased (e.g. "MM/DD/YYYY" for en-US).
- Parsing via `DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)` with `ResolverStyle.STRICT`.
- `OutlinedTextField` with `isError` + `supportingText` for three error cases: unparseable (`strings.invalidFormatError`, includes the pattern), outside `yearRange`/bounds (`strings.outOfRangeError`), disabled date (`strings.disabledDateError`).
- Valid input writes through to the state, so toggling back to picker mode shows the typed date selected and scrolled into view.

## 5. Strings

All user-visible defaults live in one `@Immutable` `DatePickerStrings` class built by `DatePickerDefaults.strings(...)` — every value an English-default parameter (the `previousMonthContentDescription` precedent; no Android string resources, callers localize by passing values): `title`, `rangeTitle`, `headlinePlaceholder`, `rangeStartPlaceholder`, `rangeEndPlaceholder`, `inputLabel`, `rangeStartInputLabel`, `rangeEndInputLabel`, `invalidFormatError`, `outOfRangeError`, `disabledDateError`, `switchToInputDescription`, `switchToPickerDescription`, `closeDescription`.

## 6. Testing

- **Interaction tests:** select → confirm-enabled flow (via `selectedDate` observation); mode toggle preserves selection both directions; parse-error display; disabled/out-of-range input rejected; range min/max enforced across fields; `rememberSaveable` state survival via `StateRestorationTester`.
- **Snapshot tests (Roborazzi, existing conventions — fixed July 2026 dates, light + dark):** single dialog picker and input modes; range dialog picker and input modes.
- **Sample:** new "Pickers" screen demonstrating both dialogs (buttons that open them, result text), keeping `:sample:assembleDebug` honest.
- **Verification:** full CI suite — `ktlintCheck detekt checkKotlinAbi :orrery-core:test :orrery-lunar:test :orrery-compose:testDebugUnitTest :orrery-compose:verifyRoborazziDebug :sample:assembleDebug`.

## Release

Ships as **0.3.0**: changelog entry, README feature bullets + a short "Date picker dialogs" usage snippet, version bump pair (`release: 0.3.0`, then `0.3.1-SNAPSHOT`) after user confirmation. All changes additive; no migration notes.
