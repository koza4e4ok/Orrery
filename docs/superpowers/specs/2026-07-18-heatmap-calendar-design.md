# HeatmapCalendar (0.4.0) — Design

**Date:** 2026-07-18
**Status:** Approved
**Sub-project:** 3 of 6 (feature roadmap: foundations → dialogs → **heatmap** → paging → providers → schedule view)

## Context

Orrery 0.3.0 ships calendars, selection, and dialogs. The only heat visualization is
`DayDecorators.heatmap`, which tints day cells inside a regular month calendar. This
sub-project ships standalone contribution-graph composables — the GitHub-profile-style
week-column graph plus a compact month-grid variant. All UI lands in `orrery-compose`;
streak math lands in `orrery-core`. Ships as **0.4.0**.

Decisions made during brainstorming:

- **Both variants** — `HeatmapCalendar` (week columns, horizontally scrollable) and
  `HeatmapMonth` (static month grid).
- **Lookup-lambda data** — `intensity: (LocalDate) -> Float?`; null = no data. Matches
  the decorator convention.
- **Tap callback only** — `onDayClick: ((LocalDate) -> Unit)?`; no built-in tooltip or
  selection state.
- **Full chrome, all toggleable** — month labels, weekday labels, legend, and a summary
  (counters) slot.
- **`colorScale: (Float) -> Color`** with a quantized 5-level default (GitHub-style
  buckets); continuous scales possible by passing a lerp.
- **Rendering: LazyRow of week columns** (approach A) — per-cell semantics and
  clickability for free, lazy over arbitrary ranges, consistent with the library's
  Lazy-based calendars. A single-Canvas renderer was rejected for its accessibility
  cost; reusing `DefaultDay` was rejected as month-major and over-featured.

Out of scope: built-in tooltips, vertical week-column orientation, selection support,
Compose Multiplatform.

## 1. State holder

New file `orrery-compose/.../HeatmapCalendar.kt`:

```kotlin
@Stable
public class HeatmapCalendarState internal constructor(...) {
    public val startDate: LocalDate                // clamped to week starts internally
    public val endDate: LocalDate
    public val firstDayOfWeek: DayOfWeek
    public val weekCount: Int
    public val firstVisibleWeekStart: LocalDate    // snapshot-observable

    public suspend fun scrollToDate(date: LocalDate)
    public suspend fun animateScrollToDate(date: LocalDate)

    public companion object { public val Saver: Saver<HeatmapCalendarState, *> }
}

@Composable
public fun rememberHeatmapCalendarState(
    startDate: LocalDate = currentDate().minus(364, DateTimeUnit.DAY),
    endDate: LocalDate = currentDate(),
    firstVisibleDate: LocalDate = endDate,         // starts at the most recent data
    firstDayOfWeek: DayOfWeek = firstDayOfWeekFromLocale(),
): HeatmapCalendarState
```

Wraps a `LazyListState`; week index math reuses `CalendarPages.weekIndex`/`weekCount`
from `orrery-core` (the `WeekCalendarState` pattern). Default range is the trailing 365
days ending today. Dates passed to the scroll functions are coerced into
`startDate..endDate`.

## 2. `HeatmapCalendar` (week-column graph)

```kotlin
@Composable
public fun HeatmapCalendar(
    intensity: (LocalDate) -> Float?,              // null = empty cell; values clamped to 0..1
    state: HeatmapCalendarState = rememberHeatmapCalendarState(),
    modifier: Modifier = Modifier,
    onDayClick: ((LocalDate) -> Unit)? = null,
    colorScale: (Float) -> Color = HeatmapDefaults.colorScale(),
    emptyCellColor: Color = HeatmapDefaults.emptyCellColor(),
    cellSize: Dp = 12.dp,
    cellSpacing: Dp = 2.dp,
    cellShape: Shape = RoundedCornerShape(2.dp),
    showMonthLabels: Boolean = true,
    showWeekdayLabels: Boolean = true,
    showLegend: Boolean = true,
    summary: (@Composable (HeatmapSummary) -> Unit)? = { HeatmapDefaults.Summary(it) },
    strings: HeatmapStrings = HeatmapDefaults.strings(),
)
```

Layout, top to bottom: month-label row → `LazyRow` of week columns (7 cells each,
top row = `firstDayOfWeek`) with the weekday-label column pinned at the leading edge →
a footer row with the summary slot at the start and the legend at the end. Cells
before `startDate` or after `endDate` in edge weeks render as blank spacers.

Behavior details:

- **Month labels** appear above the column containing the 1st of each month, suppressed
  when fewer than 3 columns have passed since the previous label (no overlap). They
  scroll with the columns (implemented as part of each week item's header line, not a
  separate lazy list — keeps them in sync for free).
- **Weekday labels** show the 1st/3rd/5th weekday from `firstDayOfWeek` in narrow locale
  form (`DayOfWeek.displayName(narrow = true)`), vertically aligned with their rows.
- **Legend**: `strings.legendLess` + six swatches (`emptyCellColor`, then `colorScale`
  sampled at 0f, 0.25f, 0.5f, 0.75f, 1f) + `strings.legendMore`.
- **Interaction**: cells are clickable only when `onDayClick != null`; ripple bounded by
  `cellShape`.
- **A11y**: each cell sets `contentDescription` via `strings.dayDescription` /
  `strings.dayDescriptionEmpty` templates (e.g. "July 8, 2026, level 3 of 5" / "July 8,
  2026, no data"). Level is the 1-based quantization bucket of the clamped intensity:
  `min(levels, floor(value * levels) + 1)`, so 0f → 1 and 1f → `levels`.
- **RTL** mirrors through `LazyRow` (columns run end-to-start; weekday column stays
  leading).

## 3. `HeatmapMonth` (month grid)

New file `orrery-compose/.../HeatmapMonth.kt`:

```kotlin
@Composable
public fun HeatmapMonth(
    yearMonth: YearMonth,
    intensity: (LocalDate) -> Float?,
    modifier: Modifier = Modifier,
    onDayClick: ((LocalDate) -> Unit)? = null,
    colorScale: (Float) -> Color = HeatmapDefaults.colorScale(),
    emptyCellColor: Color = HeatmapDefaults.emptyCellColor(),
    cellShape: Shape = RoundedCornerShape(4.dp),
    showDayNumbers: Boolean = false,
    showWeekdayHeader: Boolean = true,
    firstDayOfWeek: DayOfWeek = firstDayOfWeekFromLocale(),
)
```

Static, non-scrolling grid over `orrery-core`'s `monthGrid(yearMonth, firstDayOfWeek,
OutDateStyle.EndOfRow)`. Cells divide the available width equally (the
`CalendarDefaults.MiniMonth` precedent) with a fixed 1:1 aspect; out-dates render blank.
`showDayNumbers = true` overlays the day-of-month number in a contrasting color
(luminance-picked, the HabitsScreen idiom). Weekday header reuses
`CalendarDefaults.WeekHeader`. Same a11y template as the graph.

## 4. Defaults, strings, summary

New file `orrery-compose/.../HeatmapDefaults.kt`:

```kotlin
public object HeatmapDefaults {
    @Composable public fun colorScale(levels: Int = 5): (Float) -> Color
        // quantizes 0..1 into `levels` buckets, lerping surfaceVariant -> primary;
        // stable across recomposition (remembered against the theme colors)
    @Composable public fun emptyCellColor(): Color            // surfaceVariant at low alpha
    public fun strings(
        legendLess: String = "Less",
        legendMore: String = "More",
        dayDescription: String = "%1$s, level %2$d of %3$d",   // date, level, maxLevel
        dayDescriptionEmpty: String = "%1$s, no data",
        summaryActiveDays: String = "%d active days",
        summaryStreak: String = "%d-day streak",
    ): HeatmapStrings
    @Composable public fun Summary(summary: HeatmapSummary)   // "N active days · M-day streak"
}

@Immutable public class HeatmapStrings(...)                    // fields above
```

`orrery-core` gains `HeatmapSummary.kt`:

```kotlin
public data class HeatmapSummary(
    public val activeDays: Int,
    public val currentStreak: Int,     // consecutive active days ending at range end
    public val longestStreak: Int,
)

public fun heatmapSummary(
    range: ClosedRange<LocalDate>,
    isActive: (LocalDate) -> Boolean,
): HeatmapSummary
```

Pure single-pass scan; `currentStreak` counts back from `range.endInclusive`.
`HeatmapCalendar` computes it in composition with `isActive = { (intensity(it) ?: 0f) > 0f }`,
so lambdas reading snapshot state recompute automatically. Requires `updateKotlinAbi`.

## 5. Testing

House conventions (fixed dates July 2026, today = 2026-07-15; Roborazzi via
`verifyRoborazziDebug`; dialog rule not needed — no dialogs here):

- **Core** (`HeatmapSummaryTest`): empty range, no active days, all active, gaps
  (longest vs current), streak ending exactly at range end, single-day range.
- **Compose interaction** (`HeatmapCalendarTest`): click reports the correct date;
  no `onDayClick` → no clickable semantics; month/weekday labels and legend appear and
  toggle off; scroll starts at range end; `scrollToDate` updates
  `firstVisibleWeekStart`; state survives restoration (`StateRestorationTester`).
- **Compose interaction** (`HeatmapMonthTest`): click date mapping, day numbers toggle,
  out-dates not clickable.
- **Snapshots**: `HeatmapCalendarSnapshotTest` (graphLight, graphDark `+night`,
  graphNoChrome) and `HeatmapMonthSnapshotTest` (monthLight, monthDark) — seeded with a
  deterministic intensity function over July 2025–July 2026.

## 6. Sample app

`HabitsScreen` gains a "Year in review" card: `HeatmapCalendar` over the trailing year
fed by the existing all-habits completion data, `onDayClick` selecting that date's
detail, plus a compact `HeatmapMonth` card for the current month. No new screen; no
`MainActivity` change.

## 7. Release

0.4.0: CHANGELOG entry, README Features bullet + Usage subsection ("Heatmaps"),
`updateKotlinAbi` for the core addition, full CI verification, release commit pair
after user confirmation. No tagging/pushing/publishing by Claude.
