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

### Changed

- **Breaking:** `SelectionEngine` and `rememberCalendarSelectionState` take
  `disabled: DisabledDates` instead of `interceptor: (LocalDate) -> Boolean`.
  Migrate `interceptor = { ... }` to `disabled = DisabledDates { predicate { ... } }`.
- **Breaking:** `CalendarDayColors.disabledContentColor` is renamed to
  `unavailableContentColor`.
- `DefaultDay` renders dates disabled by the selection state's `DisabledDates`
  automatically (unavailable colors, no click, disabled semantics); the
  `enabled` parameter remains as a manual override.

## [0.1.0] - TBD

Initial release: calendar-core (models, grid math, selection engine,
ISO week numbers), calendar-compose (Horizontal/Vertical/Week/Year
calendars, CollapsibleCalendarScaffold, drag-to-select, Material3
theming, a11y/RTL), calendar-lunar (lunar conversion, 24 solar terms,
festivals, trunk-branch years, LunarDayInfoProvider).
