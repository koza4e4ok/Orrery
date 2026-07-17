# Foundations & Polish (0.2.0) — Design

**Date:** 2026-07-17
**Status:** Approved
**Sub-project:** 1 of 6 (feature roadmap: foundations → dialogs → heatmap → paging → providers → schedule view)

## Context

Orrery 0.1.x ships month/week/year calendars, single/range/multi selection, decorators, and a collapsible scaffold. This sub-project fixes one dead API parameter, fills small API gaps, and extends snapshot coverage. It ships as 0.2.0. Pre-1.0, so breaking changes are acceptable; this design makes none.

Out of scope (later sub-projects): date picker dialogs, HeatmapCalendar, infinite paging, Hebrew/Islamic providers, event model + schedule view. KMP is dropped entirely.

## 1. Segmented in-range style (`inRangeShape`)

`CalendarDayShapes.inRangeShape` exists but nothing reads it; `drawRangeBand` always draws a rectangle. Wire it up with two modes:

- **`RectangleShape` (default):** current behavior, unchanged — a continuous band drawn by `drawRangeBand`.
- **Any other shape:** segmented mode. Skip `drawRangeBand`. Each in-range day draws its own centered fill via the existing `drawDayShape(shapes.inRangeShape, colors.inRangeContainerColor)`, sized to `size.minDimension` like the selection fill. Range endpoints keep their `selectedShape` fill and draw no half-band.

Mode detection: `shapes.inRangeShape === RectangleShape` (identity). The existing `bandAlpha` animation drives the segmented fill's alpha. Document both modes in the `inRangeShape` KDoc.

**Files:** `orrery-compose/.../DefaultDay.kt`, `CalendarDefaults.kt` (KDoc only).

## 2. Animated programmatic scrolling

`CalendarState` has instant `scrollToMonth`/`scrollToDate` and animated `animateScrollToToday`, but no animated scroll to an arbitrary target. Add:

- `CalendarState.animateScrollToMonth(month: YearMonth)` (suspend)
- `CalendarState.animateScrollToDate(date: LocalDate)` (suspend)
- `WeekCalendarState.animateScrollToWeek(date: LocalDate)` (suspend)

Each delegates to the underlying pager/lazy-list `animateScrollToItem`, the same path `animateScrollToToday` uses. Targets outside `startMonth..endMonth` coerce to the bound, matching the instant variants. No new animation system.

**Files:** `orrery-compose/.../CalendarState.kt`, `orrery-compose/.../WeekCalendarState.kt`.

## 3. Selected-day elevation

Add `selectedElevation: Dp = 0.dp` to `DefaultDay`. Default keeps the current flat look.

When `selectedElevation > 0` and the day is selected, the centered-square layer (same geometry as the ripple layer added for shaped ripples) gains `Modifier.shadow(elevation, shapes.selectedShape)`. Animate the elevation with `animateDpAsState` so the shadow appears with the selection spring and disappears on deselect.

Elevation is a `DefaultDay` parameter, not a `CalendarDayShapes` field: it is neither shape nor color, and Material keeps elevation separate (`CardDefaults.cardElevation`).

**Files:** `orrery-compose/.../DefaultDay.kt`.

## 4. SelectionPresets expansion

Add to `SelectionPresets`, mirroring the existing `nextDays`/`thisWeek`/`thisMonth`/`nextWeekend` implementations:

- `lastNDays(n: Int)` — the n days ending today, inclusive.
- `thisQuarter()` — first to last day of the current calendar quarter.
- `workweek()` — Monday through Friday of the current week.

**Files:** `orrery-core/.../SelectionPresets.kt`, `SelectionPresetsTest.kt`.

## 5. Custom week-number slot

`showWeekNumbers` renders only ISO week numbers via `CalendarDefaults.WeekNumber`. Add a slot to `HorizontalCalendar` and `VerticalCalendar`:

```kotlin
weekNumber: @Composable (CalendarWeek) -> Unit = { CalendarDefaults.WeekNumber(it) }
```

`showWeekNumbers` still gates visibility; the slot controls content. Existing callers see no change.

**Files:** `orrery-compose/.../Calendars.kt`.

## 6. Decade level in MonthYearPicker

`MonthYearPicker` zooms year → month. Add one more zoom-out following the same pattern: tapping the year-grid title shows a decade grid ("1990s", "2000s", …); tapping a decade jumps the year grid to that decade. Decades wholly outside `range` are disabled, like out-of-range months today. No new configuration parameter — the level is always reachable.

**Files:** `orrery-compose/.../MonthYearPicker.kt`, `MonthYearPickerTest.kt`.

## Testing

- **Core unit tests:** the three new presets in `SelectionPresetsTest` (boundaries: quarter edges, week start, n = 1).
- **Snapshot tests (Roborazzi, existing conventions in `SnapshotTest.kt` — fixed dates, light/dark/RTL where relevant):** segmented in-range fill, selected-day elevation, decade grid, custom week-number slot.
- **Interaction tests:** decade-level navigation in `MonthYearPickerTest`; animated scroll targets in a state test.
- **Verification:** `./gradlew :orrery-core:test :orrery-compose:testDebugUnitTest ktlintCheck detekt`.

## Release

Ships as **0.2.0**: README section updates for each feature, changelog entry, version bump. All changes are additive; no migration notes needed.
