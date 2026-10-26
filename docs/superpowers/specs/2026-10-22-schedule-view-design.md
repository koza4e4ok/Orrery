# Time-Grid Day/Week Schedule View (0.7.0) — Design

**Date:** 2026-10-22
**Status:** Ready (approved by Andrii 2026-10-23; Section 4 tokens finalized 2026-10-26)
**Issue:** #21 (Co-designed with #22 event model & agenda; tracking roadmap: #26)
**Owners:** Ritsuko (architecture & layout algorithm), Rei (visual spec), Maya (implementation), Asuka (review)

## Context

The Orrery repository is tagged `week-view`, but `WeekCalendar` is a single-row day strip. A vertically scrolling 24-hour time-grid schedule view (Google Calendar / Apple Calendar style) for single-day and multi-day/week layouts is the biggest missing component in the library.

This specification is **co-designed with #22 (Event Model & Agenda)**:

- Both components consume `CalendarEvent<T>` and `EventSpan` (`EventSpan.AllDay` and `EventSpan.Timed`) from `orrery-core`.
- Pure interval-packing and overlap geometry live in `orrery-core` (`ScheduleLayout.kt`) so the layout math is 100% deterministic and unit-tested without UI dependencies.
- Composable views (`DaySchedule`, `WeekSchedule`, `ScheduleState`, `ScheduleDefaults`) live in `orrery-compose`.

**Deliberate non-goals (what we are NOT building):**

- No built-in drag-and-drop event rescheduling or resize handles in v1 (events are tap-interactive via `onEventClick`, and empty time slots are tap-interactive via `onEmptySlotClick`).
- No automatic system-clock polling loop inside `DaySchedule`/`WeekSchedule`; `currentTime: LocalTime?` is passed as state by the caller so snapshots and tests remain 100% deterministic.

---

## 1. Overlap Layout Algorithm (`orrery-core`)

New file `orrery-core/src/commonMain/kotlin/me/kozakov/orrery/core/ScheduleLayout.kt`:

### Data Structures

```kotlin
package me.kozakov.orrery.core

import kotlinx.datetime.LocalDate

/**
 * Computed 2D layout box for a single [CalendarEvent] on a specific [date]'s
 * 24-hour timeline (0..1440 minutes from midnight).
 *
 * @property event Source calendar event.
 * @property date The day column this placement belongs to.
 * @property startMinute Clamped start minute in `0..1440`.
 * @property endMinute Clamped end minute in `0..1440`.
 * @property displayEndMinute Effective visual bottom minute (`>= startMinute + minDurationMinutes`).
 * @property columnIndex 0-based horizontal lane index within the overlap cluster.
 * @property columnCount Total parallel lanes in the overlap cluster (`>= 1`).
 * @property leftFraction Normalized horizontal start offset in `0f..1f`.
 * @property widthFraction Normalized horizontal width in `0f..1f`.
 */
public data class PositionedEvent<out T>(
    public val event: CalendarEvent<T>,
    public val date: LocalDate,
    public val startMinute: Int,
    public val endMinute: Int,
    public val displayEndMinute: Int,
    public val columnIndex: Int,
    public val columnCount: Int,
    public val leftFraction: Float,
    public val widthFraction: Float,
)

/**
 * Partitioned events for a single [date] column: all-day banner items plus
 * non-overlapping 2D-packed timed placements.
 */
public data class DayScheduleLayout<out T>(
    public val date: LocalDate,
    public val allDayEvents: List<CalendarEvent<T>>,
    public val timedEvents: List<PositionedEvent<T>>,
)

/**
 * Computes [DayScheduleLayout] for [events] occurring on [date].
 *
 * @param minDurationMinutes Minimum visual height in minutes so 0-minute or 5-minute
 *   events remain legible and tappable (default 15 minutes).
 */
public fun <T> layoutDaySchedule(
    date: LocalDate,
    events: Iterable<CalendarEvent<T>>,
    minDurationMinutes: Int = 15,
): DayScheduleLayout<T>
```

### Algorithm: Connected Cluster + Greedy Lane Packing

For a given `date`:

1. **Partition & Clip:**
   - Events with `EventSpan.AllDay` (where `event.occursOn(date)`) go to `allDayEvents`.
   - Events with `EventSpan.Timed` covering `date` are clipped to `date`'s `00:00..24:00` window:
     - `startMinute = if (span.start.date < date) 0 else span.start.hour * 60 + span.start.minute`
     - `endMinute = if (span.end.date > date) 1440 else span.end.hour * 60 + span.end.minute`
     - `displayEndMinute = maxOf(endMinute, (startMinute + minDurationMinutes).coerceAtMost(1440))`
     - *(Edge case: an overnight event ending at exactly `00:00` on `date` with `span.start.date < date` is excluded from `date`'s column so it does not render a phantom 15-minute sliver at midnight.)*
2. **Sort:**
   - Sort timed intervals by `startMinute` ascending, then `displayEndMinute` descending (longer events first), then `event.id`.
3. **Cluster Partitioning:**
   - Walk sorted intervals, maintaining `clusterMaxEnd`. Whenever `next.startMinute >= clusterMaxEnd`, flush the current connected overlap cluster and start a new one. Events in separate clusters never influence each other's column widths.
4. **Greedy Column Assignment (within each cluster):**
   - Maintain a list of columns `columns: MutableList<Int>` storing the `displayEndMinute` of the last event placed in each column.
   - For each interval, assign it to the lowest index `col` where `columns[col] <= interval.startMinute`. If no column is free, append a new column.
5. **Rightward Span Expansion:**
   - After assigning `columnIndex` for all events in a cluster with `totalColumns = columns.size`, each event checks how many immediately adjacent columns to its right (`col + 1 ..< totalColumns`) have no overlapping event during `startMinute ..< displayEndMinute`.
   - `leftFraction = columnIndex.toFloat() / totalColumns`
   - `widthFraction = spanColumns.toFloat() / totalColumns`

Complexity: $O(N \log N + N \cdot C)$ per day where $C$ is the maximum concurrency (typically $\le 5$), with zero allocations outside the result list.

---

## 2. State Holder: `ScheduleState` (`orrery-compose`)

New file `orrery-compose/src/commonMain/kotlin/me/kozakov/orrery/compose/ScheduleState.kt`:

```kotlin
@Stable
public class ScheduleState internal constructor(
    startDate: LocalDate?,
    endDate: LocalDate?,
    firstVisibleDate: LocalDate,
    firstDayOfWeek: DayOfWeek,
    initialHourHeight: Dp,
    public val minHourHeight: Dp,
    public val maxHourHeight: Dp,
    initialScrollMinute: Int,
) {
    public var startDate: LocalDate? by mutableStateOf(startDate)
        private set
    public var endDate: LocalDate? by mutableStateOf(endDate)
        private set
    public var firstDayOfWeek: DayOfWeek by mutableStateOf(firstDayOfWeek)

    /** Height of one 60-minute slot; mutable for pinch-to-zoom or programmatic zoom. */
    public var hourHeight: Dp by mutableStateOf(initialHourHeight.coerceIn(minHourHeight, maxHourHeight))

    /** Vertical scroll state of the 24-hour time grid (shared across columns). */
    public val verticalScrollState: ScrollState

    /** First visible date (in DaySchedule) or week start date (in WeekSchedule). */
    public val firstVisibleDate: LocalDate

    public suspend fun scrollToDate(date: LocalDate)
    public suspend fun animateScrollToDate(date: LocalDate)

    /** Scrolls the vertical time grid so [time] is at the top of the viewport. */
    public suspend fun scrollToTime(time: LocalTime, density: Density)
    public suspend fun animateScrollToTime(time: LocalTime, density: Density)

    public companion object {
        public val Saver: Saver<ScheduleState, Any>
    }
}

@Composable
public fun rememberScheduleState(
    startDate: LocalDate? = null,
    endDate: LocalDate? = null,
    firstVisibleDate: LocalDate = currentDate(),
    firstDayOfWeek: DayOfWeek = firstDayOfWeekFromLocale(),
    initialHourHeight: Dp = 60.dp,
    minHourHeight: Dp = 36.dp,
    maxHourHeight: Dp = 160.dp,
    initialScrollTime: LocalTime = LocalTime(8, 0),
): ScheduleState
```

- **Unbounded paging:** Reuses `WeekCalendarState`'s effective bounds (`LocalDate(-19999, 1, 1)..LocalDate(19999, 12, 31)`) and 52-week far-jump teleport threshold.
- **Pinch-to-zoom focal anchoring:** When `zoomGesturesEnabled = true`, a two-finger vertical pinch scales `state.hourHeight` within `minHourHeight..maxHourHeight` and simultaneously adjusts `verticalScrollState.dispatchRawDelta` so the time of day under the pinch centroid remains stationary under the user's fingers.

---

## 3. Composables: `DaySchedule` & `WeekSchedule` (`orrery-compose`)

New file `orrery-compose/src/commonMain/kotlin/me/kozakov/orrery/compose/ScheduleView.kt`:

```kotlin
/**
 * Horizontally paged single-day 24-hour schedule view with optional all-day banner.
 */
@Composable
public fun <T> DaySchedule(
    events: (LocalDate) -> List<CalendarEvent<T>>,
    modifier: Modifier = Modifier,
    state: ScheduleState = rememberScheduleState(),
    today: LocalDate = currentDate(),
    currentTime: LocalTime? = null,
    userScrollEnabled: Boolean = true,
    zoomGesturesEnabled: Boolean = true,
    showHalfHourLines: Boolean = false,
    minEventDurationMinutes: Int = 15,
    colors: ScheduleColors = ScheduleDefaults.colors(),
    dimensions: ScheduleDimensions = ScheduleDefaults.dimensions(),
    strings: ScheduleStrings = ScheduleDefaults.strings(),
    onEventClick: ((CalendarEvent<T>) -> Unit)? = null,
    onEmptySlotClick: ((LocalDate, LocalTime) -> Unit)? = null,
    dayHeader: (@Composable (LocalDate, Boolean) -> Unit)? = { date, isToday ->
        ScheduleDefaults.DayHeader(date, isToday, colors)
    },
    allDayEventContent: @Composable (CalendarEvent<T>) -> Unit = { event ->
        ScheduleDefaults.AllDayEventChip(event, colors, dimensions, onClick = onEventClick?.let { cb -> { cb(event) } })
    },
    hourLabel: @Composable (Int) -> Unit = { hour ->
        ScheduleDefaults.HourLabel(hour, colors)
    },
    nowIndicator: @Composable () -> Unit = {
        ScheduleDefaults.NowIndicator(colors, dimensions, contentDescription = strings.nowLabel)
    },
    eventContent: @Composable BoxScope.(PositionedEvent<T>) -> Unit = { positioned ->
        ScheduleDefaults.EventTile(
            positioned = positioned,
            colors = colors,
            dimensions = dimensions,
            onClick = onEventClick?.let { cb -> { cb(positioned.event) } },
        )
    },
)

/**
 * Horizontally paged multi-day (default 7-day week) 24-hour schedule view.
 */
@Composable
public fun <T> WeekSchedule(
    events: (LocalDate) -> List<CalendarEvent<T>>,
    modifier: Modifier = Modifier,
    state: ScheduleState = rememberScheduleState(),
    daysPerPage: Int = 7,
    today: LocalDate = currentDate(),
    currentTime: LocalTime? = null,
    userScrollEnabled: Boolean = true,
    zoomGesturesEnabled: Boolean = true,
    showHalfHourLines: Boolean = false,
    minEventDurationMinutes: Int = 15,
    colors: ScheduleColors = ScheduleDefaults.colors(),
    dimensions: ScheduleDimensions = ScheduleDefaults.dimensions(),
    strings: ScheduleStrings = ScheduleDefaults.strings(),
    onEventClick: ((CalendarEvent<T>) -> Unit)? = null,
    onEmptySlotClick: ((LocalDate, LocalTime) -> Unit)? = null,
    dayHeader: (@Composable (LocalDate, Boolean) -> Unit)? = { date, isToday ->
        ScheduleDefaults.DayHeader(date, isToday, colors)
    },
    allDayEventContent: @Composable (CalendarEvent<T>) -> Unit = { event ->
        ScheduleDefaults.AllDayEventChip(event, colors, dimensions, onClick = onEventClick?.let { cb -> { cb(event) } })
    },
    hourLabel: @Composable (Int) -> Unit = { hour ->
        ScheduleDefaults.HourLabel(hour, colors)
    },
    nowIndicator: @Composable () -> Unit = {
        ScheduleDefaults.NowIndicator(colors, dimensions, contentDescription = strings.nowLabel)
    },
    eventContent: @Composable BoxScope.(PositionedEvent<T>) -> Unit = { positioned ->
        ScheduleDefaults.EventTile(
            positioned = positioned,
            colors = colors,
            dimensions = dimensions,
            onClick = onEventClick?.let { cb -> { cb(positioned.event) } },
        )
    },
)
```

- `onEmptySlotClick` reports the tapped column's date and the tapped time floored to the hour (to the half hour when `showHalfHourLines = true`).
- Each page is self-contained (day headers, all-day row, hour gutter, and day columns), so the gutter pages with the columns; the vertical `ScrollState` is shared by all pages.
- `DaySchedule` pages one day per swipe. `WeekSchedule` with `daysPerPage = 7` pages whole weeks starting on `state.firstDayOfWeek`; other `daysPerPage` values page in blocks counted from the range start.

### Layout Anatomy

```text
┌──────────┬──────────┬──────────┬──────────┬──────────┬──────────┬──────────┬──────────┐
│  Gutter  │  Mon 13  │  Tue 14  │  Wed 15* │  Thu 16  │  Fri 17  │  Sat 18  │  Sun 19  │ ← dayHeader row
├──────────┼──────────┼──────────┼──────────┼──────────┼──────────┼──────────┼──────────┤
│ All-day  │ [Offsite]│          │ [Release]│          │          │          │          │ ← allDayBanner (auto-hides if empty)
├──────────┼──────────┴──────────┴──────────┴──────────┴──────────┴──────────┴──────────┤
│  08:00   │ ─────────┬──────────┬──────────┬──────────┬──────────┬──────────┬───────── │ ▲
│          │ [08:00   │          │ [Standup]│          │          │          │          │ │
│  09:00   │  Run]    │          ├─────┬────┤          │          │          │          │ │ Vertically
│          │          │          │[Des]│[1:1│          │          │          │          │ │ scrollable
│  10:00   │ ─────────┼──────────●═════╪════╡──────────┼──────────┼──────────┼───────── │ │ 24h canvas
│          │          │        (nowIndicator on today) │          │          │          │ ▼
└──────────┴─────────────────────┴──────────┴──────────┴──────────┴──────────┴──────────┘
```

### Custom `Layout` Placement for Timed Events

Each day column uses a lightweight custom `Layout` (inside the shared `Modifier.verticalScroll(state.verticalScrollState)` container):

- Column total height = `(state.hourHeight * 24).roundToPx()`.
- Each `PositionedEvent<T>` child is measured with:
  - `width = (columnWidthPx * positioned.widthFraction - tileHorizontalGapPx).roundToInt().coerceAtLeast(0)`
  - `height = ((positioned.displayEndMinute - positioned.startMinute) / 60f * hourHeightPx - tileVerticalGapPx).roundToInt().coerceAtLeast(0)`
- Placed at:
  - `x = (columnWidthPx * positioned.leftFraction).roundToInt()`
  - `y = (positioned.startMinute / 60f * hourHeightPx).roundToInt()`
- **Now-line indicator:** Rendered when `currentTime != null` on `date == today` at `y = ((currentTime.hour * 60 + currentTime.minute) / 60f * hourHeightPx)`.

---

## 4. Visual Spec Tokens (Handoff Contract with Rei)

`ScheduleDefaults.kt` (`orrery-compose/src/commonMain/kotlin/me/kozakov/orrery/compose/`) exposes theme-aware tokens that Rei (`rei_design`) can tune for visual hierarchy, contrast, and density. They follow the existing `CalendarDefaults` / `AgendaDefaults` conventions: plain `@Immutable` classes without constructor defaults, built by `ScheduleDefaults.colors()` (`@Composable`, reads `MaterialTheme.colorScheme`), `ScheduleDefaults.dimensions()` (non-composable, like `CalendarDefaults.dayShapes()`), and `ScheduleDefaults.strings()` (`@Composable @ReadOnlyComposable`, resolved through `orreryString`).

```kotlin
@Immutable
public class ScheduleColors(
    public val gridLineColor: Color,              // outlineVariant.copy(alpha = 0.5f)
    public val halfHourLineColor: Color,          // outlineVariant.copy(alpha = 0.25f)
    public val hourLabelColor: Color,             // onSurfaceVariant (AgendaDefaults time label)
    public val headerContentColor: Color,         // onSurfaceVariant (AgendaDefaults.DayHeader, non-today)
    public val todayHeaderColor: Color,           // primary (AgendaDefaults.DayHeader today, CalendarDefaults todayContentColor)
    public val nowIndicatorColor: Color,          // primary (CalendarDefaults todayIndicatorColor)
    public val defaultEventContainerColor: Color, // primaryContainer (CalendarDefaults inRangeContainerColor)
    public val defaultEventContentColor: Color,   // onPrimaryContainer
    public val defaultEventAccentColor: Color,    // primary (AgendaDefaults.EventRow bar fallback)
)

@Immutable
public class ScheduleDimensions(
    public val timeGutterWidth: Dp,       // 56.dp
    public val tileCornerRadius: Dp,      // 6.dp
    public val tileAccentBarWidth: Dp,    // 3.dp
    public val tileHorizontalGap: Dp,     // 2.dp
    public val tileVerticalGap: Dp,       // 1.dp
    public val nowLineStrokeWidth: Dp,    // 2.dp
    public val nowDotRadius: Dp,          // 4.dp
    public val allDayChipHeight: Dp,      // 20.dp
    public val maxVisibleAllDayRows: Int, // 3; extra all-day events collapse into a "+N" label
)

@Immutable
public class ScheduleStrings(
    public val allDayLabel: String, // reuses orrery_agenda_all_day ("All day")
    public val nowLabel: String,    // new orrery_schedule_now ("Now"), NowIndicator content description
)
```

Hour labels use the 24-hour `HH:00` form in v1 (no locale-specific 12-hour clock yet). New strings follow the localization rules from #15/#20: Android `res/values{,-uk,-de,-es,-zh-rCN,-ja}/strings.xml` with the `orrery_` prefix plus the matching `OrreryStrings.nonAndroid.kt` tables.

When `event.colorArgb != null`, `ScheduleDefaults.EventTile` and `ScheduleDefaults.AllDayEventChip` derive the fill as `Color(event.colorArgb).copy(alpha = 0.18f)` (light) / `0.28f` (dark, detected from `MaterialTheme.colorScheme.surface.luminance() < 0.5f`), the leading accent stripe as `Color(event.colorArgb)`, and `onSurface` text on top. Without `colorArgb` they use `defaultEventContainerColor` / `defaultEventContentColor` / `defaultEventAccentColor`.

---

## 5. Testing & Sample App Plan

- **Core unit tests (`ScheduleLayoutTest`):**
  - Non-overlapping events get `columnIndex = 0, columnCount = 1, widthFraction = 1f`.
  - Pairwise and 3-way overlapping clusters assign `0, 1, 2` and `widthFraction = 1/3f`.
  - Staggered chain (A overlaps B, B overlaps C, A does not overlap C): C reuses column 0 (`columnCount = 2`).
  - Rightward expansion: in a 3-column cluster where column 2 is empty during event B's span, B expands across columns 1..2 (`widthFraction = 2/3f`).
  - Overnight clipping (`22:00` Day 1 → `02:00` Day 2) and exact-midnight boundary (`00:00` end excluded from Day 2).
  - Zero-duration / 5-minute events clamp visual height to `minDurationMinutes = 15`.
- **Compose interaction tests (`ScheduleViewTest`):**
  - Renders 24 hour labels, day headers, all-day chips, and timed tiles.
  - `onEventClick` and `onEmptySlotClick` report the exact `CalendarEvent` or `(LocalDate, LocalTime)`.
  - `scrollToTime(LocalTime(14, 0))` positions 14:00 at the viewport top.
  - `nowIndicator` renders only when `currentTime != null` and `today` is visible.
  - `ScheduleState.Saver` restores `firstVisibleDate`, `hourHeight`, and scroll offset.
- **Roborazzi snapshot tests (`ScheduleViewSnapshotTest`):**
  - `dayScheduleLight`, `dayScheduleDark` (showing overlapping cluster + all-day banner + now-line at `10:15`).
  - `weekScheduleLight`, `weekScheduleDark`.
- **Sample app:**
  - Add a `Day / Week / List` mode switcher in `sample/AgendaScreen.kt` so users can toggle between the collapsible month+agenda view (#22), `DaySchedule`, and `WeekSchedule` (#21) over the same `CalendarEvent` dataset.

---

## 6. Resolved Decisions (Andrii, 2026-10-23)

1. **All-day events in `WeekSchedule` (v1):** Render per-column all-day chips in each covered day's header (no multi-column spanning bars in v1).
2. **`WeekSchedule` horizontal paging:** Page-snapping one week (`daysPerPage`) per swipe, matching `WeekCalendar`.
3. **Visual spec review:** `ScheduleColors` / `ScheduleDimensions` consolidated against `CalendarDefaults` / `AgendaDefaults` (2026-10-26): the now-line uses `primary` (not `error`), a `headerContentColor` token covers non-today headers, and dimension defaults moved into `ScheduleDefaults.dimensions()`. Rei can tune values against the implementation PR's Roborazzi snapshots.
