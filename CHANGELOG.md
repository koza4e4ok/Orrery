# Changelog

## [Unreleased]

## [0.4.0] - 2026-07-18

### Added

- `HeatmapCalendar`: GitHub-style contribution graph — week columns over an
  arbitrary date range with month labels, weekday labels, legend, and a
  summary slot, backed by `rememberHeatmapCalendarState`.
- `HeatmapMonth`: static month grid of heat tiles for dashboards.
- `heatmapSummary()` (orrery-core): pure active-days/streak aggregation.
- `HeatmapStrings` / `HeatmapDefaults`: quantized color scale, legend and
  accessibility strings — all overridable for localization.

## [0.3.0] - 2026-07-18

### Added

- `OrreryDatePickerDialog`: Material-3-shaped single-date picker dialog
  (slot-based confirm/dismiss buttons) with calendar and locale-aware
  text-input modes, backed by `rememberOrreryDatePickerState`.
- `OrreryDateRangePickerDialog`: full-screen range picker with drag
  selection, sticky month headers, and dual text-input fields, backed by
  `rememberOrreryDateRangePickerState`.
- `DatePickerStrings` / `DatePickerDefaults.strings()`: every built-in
  string is an overridable parameter (English defaults).

## [0.2.0] - 2026-07-17

### Added

- `SelectionPresets.lastNDays`, `SelectionPresets.thisQuarter`, and
  `SelectionPresets.workweek`.
- `CalendarState.animateScrollToDate(date)`, the animated counterpart of
  `scrollToDate`.
- Segmented in-range style: a non-rectangular `CalendarDayShapes.inRangeShape`
  now draws a centered per-day fill instead of the connector band (the
  parameter previously had no effect).
- `DefaultDay(selectedElevation = ...)`: draws a shadow under the selected
  day's fill.
- `weekNumber` slot on `HorizontalCalendar` and `VerticalCalendar` for custom
  week-number content; `showWeekNumbers` still gates visibility.
- `MonthYearPicker`: decade level — tap the year grid's title to jump by
  decade.

### Fixed

- Day-cell ripple is clipped to `CalendarDayShapes.dayShape` instead of
  covering the whole rectangular cell.

## [0.1.0] - 2026-07-13

### Added

- `DisabledDates` (orrery-core): declarative unavailable dates — explicit
  dates, inclusive ranges, days of week, exclusive before/after bounds, and
  predicate escape hatches — honored by selection, drag-select, and rendered
  automatically by `DefaultDay`.
- `CalendarDayShapes` + `CalendarDefaults.dayShapes()`: configurable day cell,
  selection and in-range shapes, plus `TodayIndicator` styles (`Ring`,
  `FilledCircle`, `Underline`, `None`).
- `CalendarDayColors.unavailableContainerColor`.
- `DayDecorator` + `DayDecorators`: stackable DrawScope day decorations with
  `Behind`/`Over` layers and prebuilt factories — `eventDots`, `progressRing`,
  `progressBar`, `strikethrough` (over the day number), `underline`, and a
  GitHub-style `heatmap`.
- `CalendarNavHeader`: previous/next month buttons around an animated title;
  the title becomes clickable with a dropdown affordance via `onTitleClick`.
- `MonthYearPicker`: two-level year → month jump picker, range-aware.
- `CalendarState.animateScrollToToday()`.
- The in-range band now animates in when a range completes (driven by the
  existing `animateSelection` flag).
- Haptic feedback on day click, drag-select start, and drag boundary changes
  (`hapticsEnabled` on `DefaultDay`, `HorizontalCalendar`, `VerticalCalendar`).
- Keyboard/D-pad navigation: arrow keys move between days natively, wrap at
  row edges, and page the calendar when crossing into another month's grid
  (`keyboardNavigation` on `HorizontalCalendar` and `WeekCalendar`).
- Flow observation extensions: `CalendarState.visibleMonths()`,
  `WeekCalendarState.visibleWeeks()`, `CalendarSelectionState.selectionChanges()`.
- `SelectionPresets` (`nextDays`, `thisWeek`, `thisMonth`, `nextWeekend`) and
  `CalendarSelectionState.set(selection)` — programmatic selection validated
  like clicks (mode, bounds, disabled dates).
- `DayDecorators.badge`: count bubble at the cell's top-end (RTL-aware) with a
  `maxCount` cap rendering `"9+"`.
- `rememberAnimatedDayValues`: eases per-day decorator data (progress rings,
  heatmaps) toward new targets.
- Sample rework: four product-style screens (Agenda, Trips, Habits, Almanac)
  with a navigation bar, dynamic color, and realistic named data.
- `HorizontalCalendar` animates its height between months with different week
  counts — tracking the swipe 1:1, then springing to the settled page's height
  so content laid out below the calendar glides rather than jumps
  (`animateHeight`, default on).
- `CalendarSelectionState.isRangeStart`/`isRangeEnd` for styling range endpoints.

### Fixed

- Count badges (`DayDecorators.badge`) and other `Over` decorators are no longer
  clipped by the day cell shape — they render fully at the cell corner.
- The `TodayIndicator.Ring` marker is now a true circle on non-square cells
  instead of a stretched ellipse.
- A completed date range renders as one continuous rounded band that connects
  the endpoint fills, replacing the disjoint endpoints and sharp rectangular
  band.

### Changed

- **Breaking:** `SelectionEngine` and `rememberCalendarSelectionState` take
  `disabled: DisabledDates` instead of `interceptor: (LocalDate) -> Boolean`.
  Migrate `interceptor = { ... }` to `disabled = DisabledDates { predicate { ... } }`.
- **Breaking:** `CalendarDayColors.disabledContentColor` is renamed to
  `unavailableContentColor`.
- `DefaultDay` renders dates disabled by the selection state's `DisabledDates`
  automatically (unavailable colors, no click, disabled semantics); the
  `enabled` parameter remains as a manual override.
- **Breaking:** `DefaultDay`'s `decorator: (DrawScope.(CalendarDay) -> Unit)?`
  is replaced by `decorators: List<DayDecorator>`. Migrate
  `decorator = { day -> ... }` to `decorators = listOf(DayDecorator { day -> ... })`.
