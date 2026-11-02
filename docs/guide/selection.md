# Selection and disabled dates

## Selection

`rememberCalendarSelectionState` holds the selection and validates every change.
Pass it to `DefaultDay` (taps just work) and to a calendar's `dragSelection`
parameter for long-press drag-to-select ranges:

```kotlin
val selection = rememberCalendarSelectionState(
    mode = SelectionMode.Range(minDays = 2, maxDays = 14),
    disabled = blackoutDates,
    onEvent = { event ->
        // OutOfRange, Intercepted, RangeTooShort, RangeTooLong, TooManyDays
        snackbar("That stay is unavailable")
    },
)

VerticalCalendar(state = state, dragSelection = selection) { day ->
    DefaultDay(day, selectionState = selection)
}
```

Modes (`orrery-core`):

- `SelectionMode.Single(autoAdvance = false)` — one day; `autoAdvance` keeps a
  day selected while paging months (policy via `AutoSelectDay`)
- `SelectionMode.Range(minDays, maxDays)` — start/end range with inclusive
  day-count limits
- `SelectionMode.Multi(maxCount)` — toggle set, optionally capped
- `SelectionMode.Week(firstDayOfWeek = null, allowClear = true)` — a tap
  selects the whole 7-day week as a range; a null `firstDayOfWeek` follows the
  locale like the calendar does, so pass it only when the calendar overrides
  its own
- `SelectionMode.Month(allowClear = true)` — a tap selects the whole month as
  a range
- `SelectionMode.None` — display only

In `Week` and `Month` modes the period is clamped to `bounds`, a period that
contains a disabled day is rejected with `Intercepted`, tapping inside the
selected period clears it (unless `allowClear = false`), and long-press drag is
off. Programmatic writes (`set`, `initialSelection`) are snapped to the whole
week or month containing `rangeStart`.

Read and write programmatically — writes go through the same validation as taps,
so an invalid proposal is rejected and reported through `onEvent`:

```kotlin
selection.selection.range          // ClosedRange<LocalDate>? for Range mode
selection.set(SelectionPresets.nextWeekend(from = today)) // presets are pure factories
selection.clear()
```

`SelectionPresets` (in `orrery-core`) also has `nextDays`, `thisWeek`,
`thisMonth`, `lastNDays`, `thisQuarter`, and `workweek`.

## Disabled dates

`DisabledDates` is an immutable union built with a DSL. The selection engine
blocks these dates (single/multi taps, range endpoints, and any date inside a
proposed range) and `DefaultDay` renders them unavailable — no extra wiring:

```kotlin
val blackoutDates = DisabledDates {
    dates(LocalDate(2026, 12, 25))
    range(maintenanceStart..maintenanceEnd)
    daysOfWeek(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    before(currentDate())            // strictly before
    predicate { it.day == 13 }       // anything callable
}
```
