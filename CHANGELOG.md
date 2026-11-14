# Changelog

## [Unreleased]

### Added

- Heatmaps (#123, #124): `HeatmapDefaults.colorScale(levels, from, to)` ramps toward
  any target color (existing calls are unchanged), and the public
  `HeatmapDefaults.Levels` and `HeatmapDefaults.levelOf(value)` give the level a
  cell announces, which the default scale also uses. `HeatmapMonth` takes
  `showLegend` (default `false`) for the same legend row as `HeatmapCalendar`.
- Opt-in markers in `orrery-core` (#89): `@ExperimentalOrreryApi` (warning) for API
  without a compatibility promise and `@InternalOrreryApi` (error) for declarations
  that are public only for sibling `orrery-*` modules. Declarations marked with them are
  left out of the ABI dumps, and `@InternalOrreryApi` packages are left out of the API docs.
- Interactive `DaySchedule` / `WeekSchedule` editing (#88): `onEmptySlotLongPress`,
  `onSlotCreate`, `onEventMove` and `onEventResize` (long press to create or move on
  touch, press-and-drag with a mouse, resize handles), snapping to `snapMinutes`,
  a themeable drag ghost (`ghostContent`, `ScheduleDefaults.DragGhost`,
  `ScheduleDragPreview`, `ScheduleDragKind`), edge auto-scroll, keyboard editing
  (`Alt`+arrows, a column caret with `Enter`) and TalkBack custom actions. All
  callbacks default to `null`, so existing schedules are unchanged. An `endTime`
  that is not after `startTime` means 00:00 of the following day. The previous
  overloads, `ScheduleStrings`, `ScheduleDimensions` and `ScheduleDefaults.strings`
  / `dimensions` signatures stay as hidden binary-compatible bridges, and the new
  `ScheduleDimensions` / `ScheduleStrings` constructor parameters have defaults
  (English for the strings), so existing constructor calls still compile.
- RFC 5545 recurrence in `orrery-core` (#86): `RecurrenceRule` (`FREQ` DAILY to
  YEARLY, `INTERVAL`, `COUNT`, `UNTIL`, `BYDAY` with ordinals, `BYMONTHDAY`,
  `BYMONTH`, `BYSETPOS`, `WKST`, `EXDATE`) with an RRULE/EXDATE parser and
  canonical formatter, the `recurrenceRule {}` DSL and companion factories.
  Expansion follows RFC 5545 section 3.3.10: invalid dates such as 31 February
  are skipped without consuming `COUNT` (`BYMONTHDAY=-1` is the last day of the
  month, `InvalidDatePolicy.ClampToMonthEnd` is an explicit opt-in), `COUNT` is
  counted from `DTSTART` whatever the query window, and `EXDATE` removes dates
  without refilling. Unsupported parts (`BYYEARDAY`, `BYWEEKNO`, time parts,
  `RDATE`, `EXRULE`, sub-daily frequencies) are rejected.
- `CalendarEvent.recurrence` and `CalendarEvent.instance`, `occurrencesIn` and
  `expandRecurring`; multi-day occurrences keep their duration. `eventsByDate`
  expands recurring events automatically. The previous 6-argument constructor and
  `copy` stay as hidden binary-compatible bridges.
- Viewport date ranges for lazy loading (#87): `CalendarState.visibleDateRange` and
  `WeekCalendarState.visibleDateRange` (the dates painted by every page that is at
  least partly on screen), plus `visibleDateRangeFlow(prefetchMonths = 1)`,
  `visibleDateRangeFlow(prefetchWeeks = 1)` and
  `CollapsibleCalendarState.visibleDateRangeFlow(calendarState, weekState, ...)`,
  which widen the range by whole months/weeks, clamp it to the configured bounds
  and only emit changes.
- Range selection across the two months of the dual-month
  `OrreryDateRangePickerDialog` (#88b): a long-press drag can start in one month
  and end in the other (in either order), and holding a drag near the left or
  right edge pages one month after a short pause, then one more every 400 ms
  while it stays there; with a mouse or stylus, hovering previews the
  range the next click would make in both months; arrow keys move between the
  two months without scrolling (`Shift+Arrow` extends the range across them)
  and page by exactly one month past either side.

### Changed

- Heatmaps (#122): the default color scale starts level 1 at 35% of the way from
  `surfaceVariant` to `primary` (was 20%) and rises linearly to `primary`, so the
  lowest level is easier to tell apart from an empty day. Heatmap colors change
  for levels 1-4.
- `CalendarDefaults.MiniMonth` draws the whole month on one canvas with shared,
  cached text layouts instead of composing about 40 nodes per month, so
  `YearCalendar` pages compose and scroll faster. The month is now one
  accessibility node described by its month and year (with "today" and selected
  state) instead of reading every day number. Signatures are unchanged.

### Experimental

- New module `me.kozakov.orrery:orrery-widget` (#89, in progress): the scaffold for the
  headless calendar snapshot renderer, with `CalendarSnapshotRange` (`Month`, `Week`,
  `Agenda`). Every public declaration is `@ExperimentalOrreryApi` and has no
  compatibility promise in 1.0.x. It requires exactly the same `orrery-compose`
  version (`strictly` in the Gradle metadata, `[x.y.z]` in the POM).
- `orrery-widget` snapshot model (#89, in progress): `CalendarSnapshotSpec` (range, today,
  selection, day info tint, event dot counts, agenda events, first day of week, title,
  weekday and outside-day switches), `CalendarSnapshotColors` and
  `CalendarSnapshotDefaults.colors(colorScheme)` / `colors()` (the `MiniMonth` colour
  mapping), `CalendarSnapshotStyle` (text sizes, padding, corner radius, `minTextScale`)
  and `CalendarSnapshotOutput` (pixel size up to 4096 px a side and 4 194 304 px in total,
  density, font resolver, layout direction). All are plain classes with `copy`, all
  `@ExperimentalOrreryApi`. `CalendarSnapshotRange.Agenda.days` is limited to 1..366.
  The internal painter draws month, week and agenda strips; text shrinks with the cells
  down to `minTextScale` of its size at font scale 1, so a large system font never hides
  the day numbers. The render functions follow in a later change.

## [0.9.2] - 2026-11-05

### Changed

- Agenda rows, schedule tiles and schedule hour labels follow the device's
  12/24-hour setting (new `is24Hour` parameter on `AgendaDefaults.EventRow`,
  `ScheduleDefaults.HourLabel` and `ScheduleDefaults.EventTile`; the previous
  signatures stay as hidden binary-compatible bridges), and the schedule's hour
  gutter widens to fit its labels.

### Added

- `WeekCalendar` and `CollapsibleCalendarScaffold` take `showWeekNumbers` /
  `weekNumber` like `HorizontalCalendar`; the previous signatures stay as hidden
  binary-compatible bridges.

### Fixed

- `DayDecorators.progressRing` sits 3dp inside the cell, so it no longer
  overlaps the today ring.
- `DefaultDay` shows turnover days in the Full-cell range style, filled like the
  range with a thin diagonal divider (previously they were ignored).
- Schedule all-day rows take at most about a quarter of a short page, so
  landscape and split-screen windows keep a visible time grid.
- `MonthYearPicker` outlines the current year in the year grid, like the
  current month.
- `WeekEventBanners` dims a banner that lies only on out-of-month days, matching
  those days.
- Schedule tiles too narrow for their whole time range show only the start
  time instead of cutting the range off mid-way.
- `DefaultDay` keeps a 6dp band above and below a labelled day's text when it
  has decorators, so dots and bars no longer draw over the label at large font
  sizes.
- `CollapsibleCalendarScaffold` measures its rows, so a month with tall rows
  (large text, labels) is no longer clipped and the Two Weeks/Week stops show the
  right rows; `dayHeight` is now the minimum row height.
- `WeekEventBanners` lanes grow to fit one line of text, so banner titles are no
  longer clipped at large font sizes.
- `AgendaDefaults.EventRow` widens its time column to fit the time on one line
  at large font sizes, and limits titles to two lines and subtitles to one.
- Date, month and date-range picker headlines stay on one line, stepping down to
  a smaller type style at large font sizes.
- Month event banners (`packWeekEventBanners`) order same-day timed events by
  start time instead of title, with all-day events first, so the earliest
  events stay visible when a day overflows.
- Schedule event tiles and all-day chips are filled opaque, so hour grid lines
  no longer cross their text.
- Schedule tile titles show only the lines that fit the tile and fall back to
  one ellipsized line instead of breaking their first word mid-way.
- Schedule all-day chips draw an end square and flush when the event continues
  before or after the visible days, matching month-view banners.
- The schedule's top hour label slides fully into view instead of being half
  hidden under the header.
- `DefaultDay` dims an out-of-month day's secondary label and all its
  decorators (dots, badges, bars, rings) like its number; decorators draw at
  38% opacity there.
- `DayDecorators.spanningEventBars` keeps each multi-day event on one lane
  across its week row (new `firstDayOfWeek` parameter; the previous signature
  stays as a hidden binary-compatible bridge), and it and `stackedBars` no
  longer draw over a day's secondary label.
- `HeatmapCalendar` month labels no longer widen their week column, which
  caused uneven, off-by-one gaps between months.
- `OrreryDateRangePickerDialog` hides out-of-month days, so boundary weeks (and
  selected ranges) are no longer drawn under two months.
- `MonthYearPicker` switches the month grid to short names (with full-name
  accessibility labels) instead of breaking a name mid-word when the grid is
  narrow.
- The date-time picker headline stays on one line, stepping down to a smaller
  type style when the date and time don't fit side by side.
- Almanac sample: the weekday header stays aligned when week numbers are on.

## [0.9.1] - 2026-11-04

### Added

- Booking range guardrails in `orrery-core` and `orrery-compose` (#102).
  `SelectionEngine`, `CalendarSelectionState` / `rememberCalendarSelectionState`
  and `OrreryDateRangePickerState` / `rememberOrreryDateRangePickerState` take
  `disallowDisabledInRange` (default `false`), `minRangeDays` (default `1`) and
  `maxRangeDays` (default `null`), counted in inclusive days (one night is
  `minRangeDays = 2`). On the tap that would complete a `SelectionMode.Range`,
  a span across a disabled date restarts the range at the tapped date when
  `disallowDisabledInRange = true` (`Intercepted`), a span longer than
  `maxRangeDays` restarts it there too (`RangeTooLong`), and a span shorter than
  `minRangeDays` is ignored and keeps the start (`RangeTooShort`). Programmatic
  writes (`set`, picker text input) are checked against the same limits. They
  combine with `SelectionMode.Range(minDays, maxDays)`, which keeps its behavior.
  `minRangeDays < 1` or `maxRangeDays < minRangeDays` throws. The previous
  constructors and `remember*` signatures stay as hidden binary-compatible bridges.
- Relative navigation helpers in `orrery-compose` (#102):
  `CalendarState.(animate)ScrollToNextMonth()` / `(animate)ScrollToPreviousMonth()`,
  `WeekCalendarState.(animate)ScrollToNextWeek()` / `(animate)ScrollToPreviousWeek()`
  (one page of `visibleWeeksCount` weeks), and `ScheduleState.(animate)ScrollToNextPage()`
  / `(animate)ScrollToPreviousPage()` (one page of `daysPerPage` days) plus
  `animateScrollToNextDay()` / `animateScrollToPreviousDay()`. All of them clamp
  to the state's bounds.
- `ScheduleState.animateHourHeightTo(target, animationSpec)` (#101) animates the
  hour height (clamped to `minHourHeight..maxHourHeight`) and scales the vertical
  scroll so the time at the top of the grid stays in place, for toolbar zoom
  buttons and shortcuts next to pinch-to-zoom.
- `WeekCalendar(animateHeight = true)` springs the strip's height when
  `visibleWeeksCount` changes (#101), and `CalendarNavHeader(expanded = ...)`
  rotates the title's dropdown chevron while a jump picker is open.
- Weekend styling in `orrery-compose` (#103). `CalendarDefaults.WeekHeader` and
  `CalendarDefaults.DaysOfWeekHeader` take `contentColor`, `weekendDays`
  (default Saturday and Sunday) and `weekendContentColor`;
  `CalendarDayColors` / `CalendarDefaults.dayColors(...)` take
  `weekendContentColor` and `weekendDays`, which `DefaultDay` uses for weekend
  day numbers below the selected, in-range, out-date, unavailable and today
  states. The weekend color defaults to the regular content color, so nothing
  changes until you set it. The previous overloads stay as hidden bridges.
- Keyboard navigation in `VerticalCalendar` and multi-row `WeekCalendar`
  (`visibleWeeksCount` 1..4) (#103). `VerticalCalendar` gets
  `keyboardNavigation` (default `true`): arrow keys, `Home` / `End` and
  `PageUp` / `PageDown` (`Shift` for a year) work as in `HorizontalCalendar`, and
  moving past a month's first or last row scrolls the list to the target month.

### Changed

- `CollapsibleCalendarScaffold` morphs between Month, TwoWeeks and Week without
  fading the month grid, so the active week row no longer blinks at the end of a
  collapse, and drags and flings settle on a velocity-aware spring instead of a
  fixed 240 ms tween (#101).
- `DefaultDay` animates its content color and hover fill (150 ms, `snap()` when
  `animateSelection = false`); `DaySchedule` / `WeekSchedule` slide timed events
  to their new lanes when `overlapMode` changes; the date and range picker
  headlines slide up or down depending on the direction of the new date (#101).
- Full RTL mirroring (#103): under `LayoutDirection.Rtl`, `DefaultDay` mirrors
  range bands and `SplitHorizontal` / `SplitDiagonal` check-in / check-out
  halves, the schedule grid draws its day dividers next to the hour gutter on the
  right, and `Left` / `Right` arrow keys move one day forward / back to follow
  the mirrored grid.
- `:sample`: Trips adds a "Stay rules" chip (1–13 nights, no stay across a
  booked night) and tints Friday and Saturday nights; Agenda's Day and Week grids
  get page chevrons and an animated Zoom chip; Habits pages the two-week strip
  with chevrons; the Almanac style studio uses the month step helpers and adds an
  RTL preview.

## [0.9.0] - 2026-10-30

### Added

- Hospitality / booking range endpoints in `orrery-compose` (#83).
  `CalendarDayShapes.rangeEndpointStyle` takes a `RangeEndpointStyle`:
  `FullCell` (the default, unchanged rendering), `SplitHorizontal` (the range
  start fills the PM half facing the next day, the range end the AM half facing
  the previous day) or `SplitDiagonal` (bottom-right / top-left triangles), in
  `inRangeContainerColor` so a stay reads as one band; halves mirror in RTL.
  `CalendarDefaults.hospitalityStyle(...)` is a preset (`SplitDiagonal` by
  default). A new `DefaultDay(day, style, turnoverDates, ...)` overload draws
  both halves with a 2.dp gap on turnover days (check-out AM + check-in PM in
  one cell) and takes `calendarState` / `outDateClickPolicy`.
- `OutDateClickPolicy` (`Ignore`, `SelectOnly`, `SelectAndScrollToMonth`) and
  `CalendarSelectionState.onDayClick(day, calendarState, outDateClickPolicy,
coroutineScope)`: taps on in-dates / out-dates can be ignored, only select, or
  select and animate the calendar to the date's month (#83).
- `SelectionMode.Single(allowClear)` in `orrery-core` (#83). `allowClear = true`
  lets a tap on the selected day clear the selection; the default `false` keeps
  the 0.8.0 behavior, where the selected day stays selected. The 2-argument
  constructor and `copy` stay as hidden binary-compatibility bridges.
- Two-week (`visibleWeeksCount = 1..4`) mode, `visibleDaysOfWeek` work-week
  filter and `CalendarRowHeight` (`WrapContent`, `Fixed`, `FillViewport`) (#82).
  `orrery-core` adds `daysOfWeek(firstDayOfWeek, visibleDaysOfWeek)`,
  `monthGrid(..., visibleDaysOfWeek)` (dropping week rows that have zero
  in-month days after filtering unless `OutDateStyle.EndOfGrid`),
  `weekGrid(..., visibleDaysOfWeek)`, `multiWeekGrid(anchor, weeksCount, ...)`
  and multi-week `CalendarPages` helpers. `orrery-compose` adds additive
  overloads on `rememberWeekCalendarState`, `WeekCalendar`,
  `rememberCalendarState`, `HorizontalCalendar` and `VerticalCalendar`, plus
  `CalendarDefaults.DaysOfWeekHeader` and `WeekCalendarState.visiblePages()`.
- Schedule view power features (#85). `orrery-core`: `EventOverlapMode`
  (`SideBySide`, `Stacked(laneIndentFraction = 0.15f)`), a
  `layoutDaySchedule(date, events, minDurationMinutes, minHour, maxHour,
overlapMode)` overload that clips timed events to the visible
  `minHour:00..maxHour:00` window and can cascade overlapping events, and
  `packAllDayBanners` / `AllDayBanner` to lay out multi-day all-day events
  across day columns. `orrery-compose`: `ScheduleState.minHour` /
  `maxHour` (new `rememberScheduleState(..., minHour, maxHour)` overload),
  `ScheduleState.scrollToHour` / `animateScrollToHour` /
  `scrollToCurrentTime`, and `DaySchedule` / `WeekSchedule` overloads with
  an `overlapMode` parameter. Existing signatures are unchanged.
- Direct drag and a Month / TwoWeeks / Week morph on `CollapsibleCalendarScaffold`
  in `orrery-compose` (#84). A new overload adds `enableDirectDrag` (default
  `true`: vertical drags on the week header and calendar grid collapse and
  expand it even when `content` does not scroll) and an optional `dragHandle`
  slot, with `CalendarDefaults.CollapsibleDragHandle()` as a Material 3 grab bar;
  the existing signature also gets direct drag. `CollapsibleCalendarAnchor`
  (`Week` 0f, `TwoWeeks` 0.35f, `Month` 1f) names the snap stops,
  `rememberCollapsibleCalendarState(initialAnchor, enableTwoWeekStop)` and
  `rememberCollapsibleCalendarState(initialExpanded, enableTwoWeekStop)` turn on
  the two-row stop, and `CollapsibleCalendarState` gains `currentAnchor`,
  `enableTwoWeekStop` and `animateTo(anchor)`. Releases settle on the next stop
  in the fling direction or the nearest one. The collapsing grid keeps the week
  strip's row in view, and the scaffold keeps `weekState` and `calendarState`
  in sync, so paging weeks while collapsed moves the month along.
- `ScrollSource` (`Initial`, `UserDrag`, `Programmatic`) and
  `lastScrollSource` on `CalendarState`, `WeekCalendarState` and
  `YearCalendarState`, plus `CalendarState.visibleMonthChanges()` and
  `WeekCalendarState.visibleWeekChanges()` flows emitting `VisibleMonthChange` /
  `VisibleWeekChange` with the source of each change (#84).
- Multi-day event title banners (#81). `packWeekEventBanners(weekDates, events,
maxVisibleLanes = 3)` in `orrery-core` packs the events overlapping a week row
  into greedy lanes (earlier start, then longer span, then title, then id) and
  returns `WeekBannerPackResult`: one `WeekEventBanner` per visible event (start
  / end column, lane, `continuesFromPreviousWeek` / `continuesIntoNextWeek`)
  plus per-column `overflowCountByColumn`. `WeekEventBanners` in
  `orrery-compose` draws them as single-line, ellipsized title pills in the
  event's `colorArgb` (or `primaryContainer`) with a readable text color, cut
  flat where an event continues into the previous or next week, and a
  clickable `+N` indicator per overflowing column (`overflowLabel` customizes
  the text). A `WeekEventBanners(week, events, ...)` overload packs for you.
- `weekOverlay` and `weekFooter` slots on `HorizontalCalendar`,
  `VerticalCalendar` and `WeekCalendar`, and a `monthFooter` slot on
  `HorizontalCalendar` and `VerticalCalendar` (#81). They come as new
  overloads; the existing signatures are unchanged. Long-press drag selection
  keeps hitting the right day when overlays or footers make week rows uneven.

### Changed

- The `DaySchedule` / `WeekSchedule` all-day header shows each multi-day
  all-day event as one banner spanning the days it covers on the page instead
  of a separate chip per day; the "+N" overflow now counts hidden banners per
  column (#85).

## [0.8.0] - 2026-10-28

### Added

- `CalendarDensity` (`Compact` 36.dp, `Comfortable` 48.dp, `Expanded` 56.dp
  min cell height; `Compact` hides secondary labels) and `CalendarStyle`
  (colors, shapes, density, cell border and selected elevation) in
  `orrery-compose` (#66). `CalendarDefaults.style(...)` builds the default
  Material 3 circle style, and `CalendarDefaults.roundedSquareStyle(...)`,
  `CalendarDefaults.segmentedRangeStyle(...)` and
  `CalendarDefaults.borderedCardStyle(...)` are curated presets (rounded-square
  cells with a continuous band, per-day rounded range segments, and
  `outlineVariant`-bordered Expanded cards with a 2.dp selected elevation). A new
  `DefaultDay(day, style, ...)` overload applies a `CalendarStyle`; the
  existing `DefaultDay` signature is unchanged.
- `DayDecorators.spanningEventBars`, `DayDecorators.stackedBars`, and
  `DayDecorators.rangePreview` in `orrery-compose`, plus a redesigned universal
  `:sample` showcase app with `SampleHeroHeader`, in-place `YearCalendar`
  overview jump, live-switchable visual theme presets (_M3 Default_, _Meizu
  Badges_, _Colorful Glow_, _Solar Progress_, _Stacked & Spanning_, _Rounded &
  Card_), and a dynamic controls bottom sheet (#67).
- Visible keyboard / D-pad focus rings and pointer hover states (#61).
  `DefaultDay` draws a ring of `CalendarDayShapes.focusedBorderWidth` (default
  `2.dp`) in `CalendarDayColors.focusedBorderColor` (default
  `todayIndicatorColor`) around the day shape while focused (with a 1.dp contrasting inner halo on
  selected days), unclipped by the cell, and fills available, unselected days with
  `CalendarDayColors.hoverContainerColor` (default `contentColor` at 8% alpha)
  while hovered on Desktop and Web. `CalendarDefaults.dayColors(...)` and
  `CalendarDefaults.dayShapes(...)` take the matching parameters; the previous
  constructors and overloads stay as hidden binary-compatible bridges.
  Interactive `HeatmapCalendar` cells (with `onDayClick`) draw a 2.dp
  `onSurface` ring just outside the focused tile, and `YearCalendar`
  mini-month cells a 2.dp `primary` ring inside the focused cell.
- Full keyboard navigation in `HorizontalCalendar` and `WeekCalendar` (#62).
  `Up` / `Down` on the top / bottom row page to the previous / next month
  (week in `WeekCalendar`) and focus the date 7 days earlier / later;
  `PageUp` / `PageDown` jump one month (one year with `Shift`) and focus the
  same day, clamped to the month's length; `Home` / `End` focus the first /
  last day of the week row. With a `SelectionMode.Range` `dragSelection`,
  `Shift` + arrow keys move focus and extend the range from its fixed end to
  the newly focused day, validated like a drag (min / max length, disabled
  days, bounds).
- Picker dialogs place initial focus (#62): `OrreryDatePickerDialog`,
  `OrreryDateTimePickerDialog` and `OrreryDateRangePickerDialog` focus the
  date (or start-date) text field when opened in or switched to input mode,
  and, in keyboard input mode, the selected (or today's) day cell of the
  visible month in calendar mode.
- `Escape` / Back in `MonthYearPicker` and `OrreryMonthPickerDialog` drills up
  from a year's month grid or from the decade grid to the year grid before
  the host dialog dismisses (#62).
- Clickable event items in `DaySchedule` / `WeekSchedule`
  (`ScheduleDefaults.EventTile`, `ScheduleDefaults.AllDayEventChip`) and
  `Agenda` (`AgendaDefaults.EventRow`) draw a 2.dp `onSurface` focus ring
  inside the item when keyboard-focused (#62).
- `CalendarNavHeader` "Today" action (#64): with `showTodayButton = true` a
  compact, localized (`todayLabel`, EN/DE/ES/JA/UK/ZH-CN) text button appears
  after the next-month chevron while the visible month differs from `today`
  (default `currentYearMonth()`) and `today` lies within the state's month range;
  clicking it animates back to `today`. The previous overload stays as a hidden
  binary-compatible bridge.
- Richer `CalendarDefaults.MiniMonth` in `YearCalendar` (#64): `today` is drawn
  on a filled `primary` circle with `onPrimary` text, the new optional
  `selection: Selection?` shows selected dates and ranges on a subtle
  `primaryContainer` pill, and day numbers whose optional
  `dayInfo: ((LocalDate) -> DayInfo?)?` reports `isHoliday` use the `primary`
  color. The previous overload stays as a hidden binary-compatible bridge.

### Changed

- `DefaultDay` keeps the secondary `DayInfo.label` on one centered line with
  2 dp side padding and an ellipsis, so long holiday or calendar names ("Day of
  Defenders and Defendresses of Ukraine") no longer wrap or shift the day
  number (#63).
- `DefaultDay` fades each half of the range connector band on its own, so
  range start and end connectors now fade in and out like in-range days when a
  range is completed, changed or cleared, instead of appearing or vanishing at
  once (#63).
- In `DefaultDay`, `DayDecorators.eventDots` draw in the selected content color
  on selected days (a new `eventDots(selectedColor, ...)` overload picks the
  color) and sit closer
  to the bottom edge on days with a secondary label; `DayDecorators.badge` gets
  a 1 dp outline on selected days (new `selectedOutlineColor` parameter, default
  the selected content color) so it stays distinct from the selection fill. The
  previous overloads stay for binary compatibility (#63).
- `OrreryDatePickerDialog`, `OrreryDateTimePickerDialog` and
  `OrreryMonthPickerDialog` scroll their body on short windows (for example a
  420 dp tall landscape phone), and `OrreryDateRangePickerDialog` fits its
  calendar or scrolling text-input pane into the height left under the top bar,
  so the confirm and dismiss buttons are never pushed out of the dialog (#65).
- `OrreryDateTimePickerDialog` slides and fades between the Date and Time steps
  and puts the active step's headline part (date or time) on a
  `secondaryContainer` pill with `onSecondaryContainer` text; both parts are
  selectable tabs for accessibility services (#65).
- `DaySchedule` / `WeekSchedule` default event tiles and all-day chips with a
  custom `CalendarEvent.colorArgb` pick `onSurface` or `inverseOnSurface`
  text, whichever contrasts more with the tinted background, so pale or very
  bright event colors stay legible in light and dark themes (#65).

## [0.7.0] - 2026-10-28

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

- `OrreryDateTimePickerDialog`: a date, then a time of day, in one dialog. The
  Date step is the `OrreryDatePickerDialog` body (calendar or text input,
  unchanged); **Next** opens a Material 3 `TimePicker` dial with a toggle to
  `TimeInput`. Adds `DateTimePickerStep`, `OrreryDateTimePickerState` /
  `rememberOrreryDateTimePickerState(initialDateTime, bounds, disabled, is24Hour)`
  (the result is a `LocalDateTime`; bounds and disabled dates apply to the date)
  and `DateTimePickerStrings` / `DatePickerDefaults.dateTimeStrings()` in the six
  bundled languages; the dialog's `dateStrings: DatePickerStrings` parameter
  (default `DatePickerDefaults.strings()`) localizes the date step. `is24Hour`
  defaults to the "Use 24-hour format" setting on Android and to the locale's
  clock on Desktop, iOS and Web; it and `initialDateTime` are read once. The
  headline time uses the locale's format and AM/PM text ("2:05 PM", "下午2:05").
  Consumers need no Material 3 experimental opt-in (#24).

- `OrreryMonthPickerDialog` for picking a month and year without a day (for
  example a card expiry date), with `OrreryMonthPickerState` /
  `rememberOrreryMonthPickerState(initialSelectedMonth, range)`,
  `DatePickerDefaults.monthRange()` (current year ±100) and
  `MonthPickerStrings` / `DatePickerDefaults.monthPickerStrings()` in the six
  bundled languages. It opens on the month grid of the selected (or current)
  year and reuses `MonthYearPicker`'s year and decade grids, whose look and
  behavior are unchanged (#24).

- Preset chips in `OrreryDateRangePickerDialog`: the new `presets` parameter
  (default empty, so the dialog looks the same) shows a scrollable row of
  `DateRangePreset(label, range)` chips in calendar mode; tapping one selects
  its range through the selection engine and scrolls to it.
  `DatePickerDefaults.rangePresets(today, firstDayOfWeek)` builds "Last 7
  days", "Last 30 days", "This week" and "This month" from `SelectionPresets`,
  with labels in the six bundled languages (#24).

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

[Unreleased]: https://github.com/koza4e4ok/Orrery/compare/v0.9.2...HEAD
[0.9.2]: https://github.com/koza4e4ok/Orrery/compare/v0.9.1...v0.9.2
[0.9.1]: https://github.com/koza4e4ok/Orrery/compare/v0.9.0...v0.9.1
[0.9.0]: https://github.com/koza4e4ok/Orrery/compare/v0.8.0...v0.9.0
[0.8.0]: https://github.com/koza4e4ok/Orrery/compare/v0.7.0...v0.8.0
[0.7.0]: https://github.com/koza4e4ok/Orrery/compare/v0.6.0...v0.7.0
[0.6.0]: https://github.com/koza4e4ok/Orrery/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/koza4e4ok/Orrery/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/koza4e4ok/Orrery/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/koza4e4ok/Orrery/compare/v0.1.0...v0.3.0
[0.1.0]: https://github.com/koza4e4ok/Orrery/releases/tag/v0.1.0
