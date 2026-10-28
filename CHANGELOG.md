# Changelog

## [Unreleased]

### Added

- New `orrery-calendars` module: Hijri (`HijriVariant.UmmAlQura` for
  1300–1600 AH, `HijriVariant.Tabular` unbounded) and Persian (Jalali,
  -61–3177 AP) conversions with `HijriDayInfoProvider` and
  `PersianDayInfoProvider` (English, Arabic and Persian strings). Checked for
  every day against java.time `HijrahDate` and ICU4J (#23).
- New `orrery-holidays` module: a rule engine (`HolidayRule`, `HolidayDate`,
  `Observance`, `HolidayCalendar`), built-in calendars for US, GB, DE and UA in
  `PublicHolidays`, and `PublicHolidayDayInfoProvider`. Checked against
  python-holidays for 2000–2050 (UA 2000–2021) (#23).

- `OrreryMonthPickerDialog` for picking a month and year without a day (for
  example a card expiry date), with `OrreryMonthPickerState` /
  `rememberOrreryMonthPickerState(initialSelectedMonth, range)`,
  `DatePickerDefaults.monthRange()` (current year ±100) and
  `MonthPickerStrings` / `DatePickerDefaults.monthPickerStrings()` in the six
  bundled languages. It opens on the month grid of the selected (or current)
  year and reuses `MonthYearPicker`'s year and decade grids, whose look and
  behavior are unchanged (#24).

- `SelectionMode.Week(firstDayOfWeek, allowClear)` and
  `SelectionMode.Month(allowClear)`: a tap selects the whole week or month
  containing the day as a range, so range rendering and TalkBack range states
  apply unchanged. The period is clamped to the selection bounds, rejected with
  `SelectionEvent.Intercepted` when any day in it is disabled, and cleared by
  tapping inside it. Calendars skip long-press drag-to-select in these modes
  (#25). `firstDayOfWeek` defaults to null, which
  `rememberCalendarSelectionState` resolves to the locale's first day of week
  (the calendar's default) and a bare `SelectionEngine` to Monday.
  Programmatic writes (`set`, `SelectionEngine.replace`, `initialSelection`)
  are snapped to the whole week or month containing `rangeStart`.

### Changed

- Build: Kotlin 2.4.21, Android Gradle Plugin 9.4.1, Compose BOM 2026.09.00,
  Compose Multiplatform 1.12.2, detekt 2.0.0-alpha.6, Roborazzi 1.77.0 and
  Robolectric 4.17 (#35).
- `compileSdk` is 37, because the Compose 1.12 artifacts require it; apps
  depending on `orrery-compose` must also compile against API 37. `minSdk`
  stays 23.

## [0.6.0] - 2026-10-27

### Added

- Preset chips in `OrreryDateRangePickerDialog`: the new `presets` parameter
  (default empty, so the dialog looks the same) shows a scrollable row of
  `DateRangePreset(label, range)` chips in calendar mode; tapping one selects
  its range through the selection engine and scrolls to it.
  `DatePickerDefaults.rangePresets(today, firstDayOfWeek)` builds "Last 7
  days", "Last 30 days", "This week" and "This month" from `SelectionPresets`,
  with labels in the six bundled languages (#24).
- `orrery-compose` targets iOS (`iosArm64`, `iosSimulatorArm64`) and Web
  (`wasmJs`) through Compose Multiplatform, next to Android and Desktop (#20).
  There is no `iosX64` (Intel simulator) variant: Compose Multiplatform 1.11
  does not publish one. `orrery-core` and `orrery-lunar` keep `iosX64`. Month and weekday names, date headlines and the date input
  pattern come from `NSDateFormatter` on iOS and `Intl.DateTimeFormat` on the
  Web; built-in strings use the same six bundled translations as Desktop.
  The Android and Desktop APIs are unchanged.

- `CalendarEvent` and `EventSpan` (`AllDay`, `Timed`) in `orrery-core` with
  `occursOn` and `eventsByDate` range indexing, plus `Agenda`, `AgendaState` /
  `rememberAgendaState`, `AgendaDefaults` / `AgendaStrings`, and
  `DayDecorators.eventDotsForEvents` in `orrery-compose` (#22).
- `DaySchedule` and `WeekSchedule` 24-hour time-grid views in `orrery-compose`
  with `ScheduleState` / `rememberScheduleState` (paged dates, shared vertical
  scroll, `scrollToTime`, pinch-to-zoom `hourHeight`) and `ScheduleDefaults`
  (`ScheduleColors`, `ScheduleDimensions`, `ScheduleStrings` and default day
  header, all-day chip, hour label, now line and event tile). Overlap packing
  lives in `orrery-core` as `layoutDaySchedule`, `DayScheduleLayout` and
  `PositionedEvent`. Adds the localized `orrery_schedule_now` string (#21).
- Richer `DayInfo` and provider composition (#19): `DayInfo` adds `priority: Int`
  (with standard tiers in `DayInfoPriority`), `colorArgb: Long?` and
  `payload: Any?` (extracted via `info.payloadAs<T>()`), while preserving
  binary compatibility with 0.5.x callers. `DayInfoProvider.plus` (`a + b`)
  combines two providers by priority, merging `isHoliday` and deduplicating
  `tags`. `LunarDayInfoProvider` populates `priority` and attaches a
  `LunarDayMetadata` payload. `DefaultDay` tints holiday labels with
  `CalendarDayColors.holidayLabelColor` and honors `DayInfo.colorArgb`.
- `orrery-compose` ships a baseline profile, generated from the sample's
  `HorizontalCalendar` and `VerticalCalendar` scroll journeys, so its calendar
  code (and the `orrery-core` code it calls) is compiled ahead of time in apps
  instead of being interpreted and JIT-compiled on first use (#17).
- Localized built-in strings: date picker dialogs, `CalendarNavHeader`
  chevron descriptions, heatmap legend/summary/day descriptions and the
  `MonthYearPicker` decade labels now resolve from Android string resources.
  Ships English, Ukrainian, German, Spanish, Simplified Chinese (zh-CN) and
  Japanese; other locales fall back to English. Every string can still be
  overridden (#15).
- `DefaultDay` reports range start, range end, in range, today and
  unavailable as its accessibility state description, so TalkBack announces
  them after the date (e.g. "July 9, 2026, Range start, Today"). The strings
  come from the new `CalendarDayStrings` / `CalendarDefaults.dayStrings()`,
  are localized like the other built-in strings, and can be overridden through
  `DefaultDay`'s new `strings` parameter (#16).

### Changed

- **Breaking:** `CalendarDayColors` gained a `holidayLabelColor` constructor
  parameter (#19), and `CalendarDefaults.dayColors()` a matching parameter.
  Source using defaults still compiles, but the change is binary-incompatible:
  code compiled against 0.5.x that calls either one must be recompiled
  (#45).
- `DayInfoProvider.plus` falls back to the lower-priority entry's `label` when
  the higher-priority entry has none, so a label-less holiday no longer hides
  e.g. the lunar date label (#45).
- **Breaking:** `DatePickerDefaults.strings()` and `HeatmapDefaults.strings()`
  are now `@Composable` (they read the current locale's resources), so they
  must be called from composition. Calls from non-composable code no longer
  compile, and code compiled against the previous signature must be
  recompiled (#15).
- Interactive elements now report `Role.Button` to accessibility services, so
  TalkBack announces them as buttons: day cells in `DefaultDay`, clickable
  days in `HeatmapCalendar` and `HeatmapMonth`, months in `YearCalendar`,
  and the clickable title of `CalendarNavHeader` (#4, #6).
- Date picker text input opens the numeric keyboard. In
  `OrreryDateRangePickerDialog` the IME action moves from the start date to
  the end date field (`Next`), then `Done` (#2).
- Faster date iteration: `SelectionEngine`, `CalendarSelectionState`,
  `heatmapSummary()`, `HeatmapCalendar`, `monthGrid` and `weekGrid` walk
  dates by epoch day instead of repeated `LocalDate.plus(1, DAY)`
  (#1, #3, #5, #9).

## [0.5.0] - 2026-07-18

### Added

- Unbounded (infinite) scrolling for `CalendarState` and `WeekCalendarState`:
  bounds are now nullable and default to null (open); one-sided ranges
  supported. Far `animateScrollTo*` jumps teleport near the target before
  animating.

### Changed

- **Breaking:** `CalendarState.startMonth`/`endMonth` and
  `WeekCalendarState.startDate`/`endDate` are now nullable;
  `rememberWeekCalendarState`'s `startDate`/`endDate` parameters are now
  optional. The default calendar range changes from 1971–2055 to unbounded.

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

> Never tagged or published to Maven Central. These changes first shipped in
> 0.3.0, which was the next published release.

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

[Unreleased]: https://github.com/koza4e4ok/Orrery/compare/v0.6.0...HEAD
[0.6.0]: https://github.com/koza4e4ok/Orrery/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/koza4e4ok/Orrery/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/koza4e4ok/Orrery/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/koza4e4ok/Orrery/compare/v0.1.0...v0.3.0
[0.1.0]: https://github.com/koza4e4ok/Orrery/releases/tag/v0.1.0
