# Changelog

## [Unreleased]

### Added

- `DisabledDates` (calendar-core): declarative unavailable dates — explicit
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

## [0.1.0] - TBD

Initial release: calendar-core (models, grid math, selection engine,
ISO week numbers), calendar-compose (Horizontal/Vertical/Week/Year
calendars, CollapsibleCalendarScaffold, drag-to-select, Material3
theming, a11y/RTL), calendar-lunar (lunar conversion, 24 solar terms,
festivals, trunk-branch years, LunarDayInfoProvider).
