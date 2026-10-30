# v0.9.0 Advanced Layouts, Hospitality Ranges, Collapsible Gestures & Power Schedule — Design

**Date:** 2026-10-30
**Status:** Draft for review (Andrii); visual tokens to be tuned by Rei against Roborazzi snapshots
**Issues:** #81, #82, #83, #84, #85 (Tracking roadmap: #90)
**Owners:** Ritsuko (architecture), Rei (visual spec), Maya (implementation), Asuka (QA review)

## Context

v0.8.0 shipped focus rings, density/style presets, `DayDecorators.spanningEventBars` and the schedule views. v0.9.0 closes the most requested layout and interaction gaps seen in kizitonwose/Calendar, huanghaibin-dev/CalendarView, airbnb/HorizonCalendar, BoguszPawlowski/ComposeCalendar, WenchaoD/FSCalendar, aleksanderwozniak/table_calendar, SimformSolutionsPvtLtd/flutter_calendar_view and thellmund/Android-Week-View.

Ground rules (same as earlier specs):

- Pure layout/grid/selection math goes in `orrery-core` and is unit-tested without UI. Composables, state and drawing go in `orrery-compose`. No new Maven modules.
- Binary compatibility with 0.8.0 is kept: every new parameter is appended with a default and the previous signature stays as a `@Deprecated(level = HIDDEN)` bridge (the pattern used for `CalendarDayColors` / `CalendarDayShapes` in 0.8.0). Run `apiCheck` and update the `.api` dumps in each implementation PR.
- New strings follow the `orrery_` prefix rules (Android `values{,-uk,-de,-es,-zh-rCN,-ja}` plus `OrreryStrings.nonAndroid.kt`).

**Deliberately NOT building in v0.9.0:**

- Drag-and-drop event rescheduling or resize handles in the schedule.
- Per-week arbitrary row heights (`CalendarRowHeight` is per calendar).
- Re-flowing multi-day events across hidden weekday columns (they are clipped to visible columns only).
- Time-zone conversion in the schedule views (events stay in the caller's local date-time).
- A clock polling loop; `currentTime` stays caller-supplied.
- A multi-booking `SelectionMode` (bookings are caller-fed, see 3.1).

Phases follow the roadmap: Phase 1 = #81 + #82, Phase 2 = #83 + #84, Phase 3 = #85. #82 must land before #81's slots (hidden columns change banner geometry) and before #84 (the TwoWeeks stop uses `visibleWeeksCount = 2`).

---

## 1. #81 Multi-day Week Event Banners and weekOverlay / monthFooter Slots

### 1.1 Existing solution check

`DayDecorators.spanningEventBars` (0.8.0) draws thin per-cell bars without text; it cannot show a title across cells or a "+N more" count. Upstream libraries leave this to callers. A text banner is one measured element spanning cells, so it is a week-row overlay, not a decorator. `spanningEventBars` stays unchanged for dense, dot-style use.

### 1.2 Core: lane packing (`orrery-core`, new `WeekEventBanners.kt`)

```kotlin
public data class WeekEventBanner<out T>(
    public val event: CalendarEvent<T>,
    public val startColumn: Int,          // 0-based first covered visible column
    public val span: Int,                 // columns covered, >= 1
    public val lane: Int,                 // 0-based, < maxVisibleLanes
    public val continuesBefore: Boolean,  // event starts before the first column (flat start cap)
    public val continuesAfter: Boolean,   // event ends after the last column (flat end cap)
)

public data class WeekBannerPackResult<out T>(
    public val banners: List<WeekEventBanner<T>>,   // ordered by (lane, startColumn)
    public val overflowByColumn: List<Int>,         // size == weekDates.size; hidden events per column
    public val laneCount: Int,                      // lanes actually used, 0..maxVisibleLanes
)

public fun <T> packWeekEventBanners(
    weekDates: List<LocalDate>,
    events: Iterable<CalendarEvent<T>>,
    maxVisibleLanes: Int = 3,
): WeekBannerPackResult<T>
```

**Which events are banners:** events occurring on at least one of `weekDates` that are `EventSpan.AllDay` (including single-day, `span = 1`) or `Timed` with `startDate != endDate`. Single-day timed events are not banners.

**Algorithm (deterministic):**

1. `weekDates` are the *visible* dates of the row in column order (already filtered by `visibleDaysOfWeek`, #82); require non-empty, strictly increasing, `maxVisibleLanes >= 0`.
2. Clip each event to visible columns: `startColumn` = first visible date >= event start, end column = last visible date <= event end; skip events touching no visible column. `continuesBefore = event.startDate < weekDates.first()`, `continuesAfter = event.endDate > weekDates.last()`.
3. Sort by span descending, then `startColumn`, `span.startDate`, `title`, `id`. Longer first keeps long banners on top lanes (Google Calendar behaviour).
4. Greedy lanes: `laneEnd[lane]` holds the last used column; place in the lowest lane with `laneEnd < startColumn`. Lanes `>= maxVisibleLanes` are not drawn.
5. Every column covered by an event without a visible lane increments `overflowByColumn[col]`.
6. The renderer, not the packer, decides whether to reserve a lane for the "+N more" label: it calls the packer with `maxVisibleLanes = lanes - 1` when it needs the reserved row, and draws the label in the freed row for columns with overflow.

Complexity O(E log E + E·L) per week row.

**Core tests (`WeekEventBannersTest`):** span/offset; overlapping events get lanes 0/1; non-overlapping reuse lane 0; longer-first ordering; clip flags for an event spanning three weeks (packed per week); overflow counts with 5 events and `maxVisibleLanes = 2`; single-day all-day has `span = 1`; `maxVisibleLanes = 0`; a 5-day (work-week) row drops a Sat–Sun-only event; multi-day timed is a banner, single-day timed is not; stable tie order.

### 1.3 Compose: `WeekEventBanners`, `WeekBannerDefaults` and slots

```kotlin
@Composable
public fun <T> WeekEventBanners(
    weekDates: List<LocalDate>,
    events: Iterable<CalendarEvent<T>>,
    modifier: Modifier = Modifier,
    maxVisibleLanes: Int = WeekBannerDefaults.MaxVisibleLanes,
    colors: WeekBannerColors = WeekBannerDefaults.colors(),
    dimensions: WeekBannerDimensions = WeekBannerDefaults.dimensions(),
    onEventClick: ((CalendarEvent<T>) -> Unit)? = null,
    onOverflowClick: ((LocalDate) -> Unit)? = null,
    bannerContent: @Composable (WeekEventBanner<T>) -> Unit = { WeekBannerDefaults.Banner(it, colors, dimensions) },
    overflowContent: @Composable (LocalDate, Int) -> Unit = { _, n -> WeekBannerDefaults.OverflowLabel(n, colors) },
)
```

A custom `Layout` spanning the row width: column width = `maxWidth / weekDates.size`; a banner is placed at `x = columnWidth * startColumn`, `y = lane * (laneHeight + laneSpacing)`, width `columnWidth * span - gap`. RTL mirrors columns and swaps flat caps. A second overload accepts a precomputed `WeekBannerPackResult<T>`.

**Visual spec (Rei to tune):**

```kotlin
@Immutable class WeekBannerColors(
    val defaultContainerColor: Color,  // primaryContainer
    val defaultContentColor: Color,    // onPrimaryContainer
    val overflowLabelColor: Color,     // onSurfaceVariant
)
@Immutable class WeekBannerDimensions(
    val laneHeight: Dp,        // 18.dp
    val laneSpacing: Dp,       // 2.dp
    val cornerRadius: Dp,      // 4.dp; 0.dp on a flat (continued) side
    val horizontalInset: Dp,   // 2.dp on a rounded side, 0 on a flat side
    val topOffset: Dp,         // 22.dp: space below the day number
    val maxVisibleLanes: Int,  // 3
)
```

- Text: `labelSmall`, one line, ellipsis, 4.dp start padding. For a clipped banner the title is drawn at the first visible column, so continued banners show the title in every week.
- Fill: `Color(event.colorArgb)` at alpha 0.18 (light) / 0.28 (dark) with `readableContentColor` text, i.e. the same palette as `ScheduleDefaults`; the private `eventPalette` moves to a shared internal file instead of being duplicated.
- Overflow label: "+N more" (`orrery_week_banner_more`), `labelSmall`, tappable with a 24.dp minimum height when `onOverflowClick != null`.
- Focus: existing 2.dp `eventFocusRing` when clickable. Semantics: `"$title, $start to $end"`, `Role.Button` when clickable.

**Slots (optional, default `null`, zero cost when absent):**

| Composable | New parameters |
|---|---|
| `HorizontalCalendar`, `VerticalCalendar` | `weekOverlay: (@Composable BoxScope.(CalendarWeek) -> Unit)? = null`, `monthFooter: (@Composable ColumnScope.(CalendarMonth) -> Unit)? = null`, `weekOverlayHeight: Dp = 0.dp` |
| `WeekCalendar` | `weekOverlay` as above, `weekFooter: (@Composable ColumnScope.(CalendarWeek) -> Unit)? = null` (a week strip has no month, so the footer is per page of weeks), `weekOverlayHeight` |

- `weekOverlay` is a `Box` laid over each week row (excluding the week-number column) above the day cells. The container installs no pointer handling; only banners are clickable, so taps elsewhere reach day cells.
- Cells must not be covered: when `weekOverlay != null`, `MonthContent` adds `weekOverlayHeight` to the row height (use `WeekBannerDefaults.reservedHeight(lanes)`), and the overlay is positioned below the day-number area via `topOffset`.
- `monthFooter` renders after the last week row of each page. In `HorizontalCalendar` the existing `animateHeight` measures the whole page, so footer height animates with no new code.

**Tests:** core above; Compose: banner columns in LTR and RTL; tap under a banner reaches the day; `onEventClick`; "+N" when lanes exceeded; footer height included in the HorizontalCalendar page height; Roborazzi `weekBannersLight/Dark` (three overlapping banners, a continued banner, overflow) for VerticalCalendar and WeekCalendar.

---

## 2. #82 N-week Mode, visibleDaysOfWeek and CalendarRowHeight

### 2.1 Core: `visibleDaysOfWeek`

```kotlin
public fun daysOfWeek(
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    visibleDaysOfWeek: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
): List<DayOfWeek>

public fun monthGrid(yearMonth: YearMonth, firstDayOfWeek: DayOfWeek = MONDAY,
    outDateStyle: OutDateStyle = OutDateStyle.EndOfRow,
    visibleDaysOfWeek: Set<DayOfWeek> = DayOfWeek.entries.toSet()): CalendarMonth

public fun weekGrid(anchor: LocalDate, firstDayOfWeek: DayOfWeek = MONDAY,
    visibleDaysOfWeek: Set<DayOfWeek> = DayOfWeek.entries.toSet()): CalendarWeek
```

- The set must be non-empty (`require`). Order always starts at `firstDayOfWeek`: Mon–Fri with `firstDayOfWeek = SUNDAY` yields Mon, Tue, Wed, Thu, Fri.
- `monthGrid` builds the full 7-column grid exactly as today (same offset, same row count, same In/Out-date semantics) and then drops hidden columns. This keeps existing row-count tests valid and the grid stable between full-week and work-week views.
- **Empty row rule:** after filtering, a row without any visible day belonging to the month (for example a trailing row whose only month days fell on hidden weekend columns) is removed for `EndOfRow` and `None`. With `EndOfGrid` the 6 rows stay fixed.
- Invariant (tested): `week.days.size == visibleDaysOfWeek.size` in `monthGrid`; `weekGrid` returns 1..7 days.
- `CalendarPages.weekStart` is unchanged; weeks still begin at `firstDayOfWeek`.
- `SelectionMode.Week` still selects the full 7-day range; hidden days stay part of it and of `DisabledDates` checks (Open decision 1).

### 2.2 Compose: `visibleDaysOfWeek`

- `CalendarState` and `WeekCalendarState` gain `var visibleDaysOfWeek: Set<DayOfWeek>` (snapshot state), a constructor / `remember*State` parameter defaulting to all days, saved as a bitmask `Int` appended to the saver list. Restore tolerates older saved lists without the field. State is the single source of truth, so composables get no duplicate parameter.
- `HorizontalCalendar`, `VerticalCalendar`, `WeekCalendar` pass it into `monthGrid` / `weekGrid` and `daysOfWeek(...)`. `CalendarDefaults.WeekHeader(daysOfWeek: List<DayOfWeek>)` already takes a list, so it renders only visible labels. The issue's `DaysOfWeekHeader` name is added as `CalendarDefaults.DaysOfWeekHeader(...)`, a thin alias forwarding to `WeekHeader` (no rename).
- Cells use `Modifier.weight(1f)`, so fewer columns get wider automatically.
- **Keyboard:** replace the hard-coded `DAYS_PER_WEEK` grid width with the visible count. Left/Right at a row edge pages to the next/previous *visible* day (Friday + Right goes to next Monday); Up/Down keep the ±7-day offset (same weekday); PageUp/PageDown clamp to the next visible day in the movement direction; row focus requesters are sized by visible columns.
- **Drag selection:** `dayAt` column math divides by the visible count.
- `HeatmapCalendar` / `YearCalendar` are out of scope.

### 2.3 Compose: `visibleWeeksCount`

```kotlin
public class WeekCalendarState(..., visibleWeeksCount: Int = 1) {   // 1..4
    public var visibleWeeksCount: Int                                // setter validates 1..4
    public val firstVisibleWeeks: List<CalendarWeek>                 // weeks of the first visible page
    public suspend fun scrollToWeek(date: LocalDate)                 // makes date's week the first row
}
rememberWeekCalendarState(..., visibleWeeksCount: Int = 1)
```

- A page is `visibleWeeksCount` consecutive week rows; pages are counted in blocks from an anchor week (the same scheme as `SchedulePaging`). The state stores an `anchorWeekOffset` (0..visibleWeeksCount-1, part of the saver) so `scrollToWeek(date)` can start a page exactly at a given week and later swipes advance by `visibleWeeksCount` weeks. `scrollToDate` keeps showing the page containing the date.
- `weekCount` keeps meaning "number of weeks"; paging uses a new internal page count. `firstVisibleWeek` stays (first row of the first page).
- `FAR_JUMP_WEEKS` is compared in pages: `max(1, FAR_JUMP_WEEKS / visibleWeeksCount)`.
- `WeekCalendar` renders each page as a `Column` of rows of `DayCell`; snap paging is unchanged. Up/Down at the first/last row target ±7 days and page through the existing `keyboard.page` path.
- Observation: `visibleWeeks()` is unchanged; add `visiblePages(): Flow<List<CalendarWeek>>`.
- No `format` parameter on `WeekCalendar`; Month/TwoWeeks/Week switching is #84's `CollapsibleCalendarFormat`.

### 2.4 `CalendarRowHeight`

```kotlin
public sealed interface CalendarRowHeight {
    public data class Fixed(public val height: Dp) : CalendarRowHeight   // every row exactly height
    public data object WrapContent : CalendarRowHeight                   // today's behaviour
    public data object FillViewport : CalendarRowHeight                  // rows share the viewport height
}
```

New parameter `rowHeight: CalendarRowHeight = CalendarRowHeight.WrapContent` on `HorizontalCalendar`, `VerticalCalendar`, `WeekCalendar` (internal parameter on `MonthContent`).

- `Fixed`: each row gets `Modifier.height(h)`; cell content clips if the caller picks a height below content.
- `WrapContent`: unchanged.
- `FillViewport`:
  - HorizontalCalendar: `animateHeight` is disabled, each page uses `fillParentMaxHeight()`, rows use `weight(1f)`. 5- and 6-row months both fill the same height (rows taller in 5-row months). Needs bounded height; with unbounded height (inside a `verticalScroll`) it falls back to `WrapContent`, documented in KDoc.
  - VerticalCalendar: each row is viewport height / 6 (so a 6-row month is about one screen); a deliberate approximation, documented.
  - WeekCalendar: the page's rows share the available height.
- `CollapsibleCalendarScaffold` keeps `dayHeight` (deterministic morph maths); no `rowHeight` there in v0.9.0.
- `DefaultDay` changes its root from `heightIn(min)` to also fill the height when the row height is imposed, so selection circles stay centred in tall rows.

### 2.5 Tests

Core: filtered order for each `firstDayOfWeek`; column count equals visible count; trailing-row removal in `EndOfRow`/`None`, 6 fixed rows in `EndOfGrid`; Sat/Sun-only in-dates; filtered `weekGrid`; `require` on empty set. Compose: 5 header labels; Right on Friday pages to Monday; `visibleWeeksCount = 2` shows 14 cells and a swipe moves 2 weeks; saver round trip including a legacy list; `FillViewport` rows equal height. Roborazzi: `workWeekMonthLight/Dark`, `twoWeekLight/Dark`, `fillViewportLight`.

---

## 3. #83 Hospitality Range Endpoints, Out-date Taps, Non-empty Selection

### 3.1 `RangeEndpointStyle` and turnover days

```kotlin
public enum class RangeEndpointStyle {
    FullCell,         // endpoints are full selected cells (today)
    SplitHorizontal,  // start fills the second half (PM / check-in); end fills the first half (AM / check-out)
    SplitDiagonal,    // start fills the lower-right triangle; end fills the upper-left triangle
}
```

**Placement**

- `CalendarDayShapes` gains `rangeEndpointStyle: RangeEndpointStyle = FullCell` (appended to the primary constructor; hidden bridge for the previous one), mirrored in `CalendarDefaults.dayShapes(...)` with a hidden bridge.
- `CalendarStyle` needs no structural change (it holds `shapes`). New preset `CalendarDefaults.hospitalityStyle(endpointStyle = SplitHorizontal, density = Comfortable, colors = dayColors())`: `inRangeShape = RectangleShape` (continuous band) plus the chosen endpoint style.
- `DefaultDay` reads `shapes.rangeEndpointStyle`.

**Turnover day** means one date that ends one booking and starts another. `CalendarSelectionState` holds a single range, so a turnover cell cannot be derived from it. Add a small caller-fed model:

```kotlin
@Immutable
public class RangeOverlay(
    public val ranges: List<ClosedRange<LocalDate>>,
    public val colors: List<Color?> = emptyList(),  // per range container colour; null = inRangeContainerColor
)
// DefaultDay (both overloads): bookedRanges: RangeOverlay? = null   (appended; hidden bridges)
```

Pure per-cell resolution in `orrery-core`:

```kotlin
public enum class RangeCellRole { None, Start, Middle, End, Single, Turnover }
public data class RangeCell(val role: RangeCellRole, val endingRange: Int?, val startingRange: Int?)
public fun resolveRangeCell(date: LocalDate, ranges: List<ClosedRange<LocalDate>>): RangeCell
```

- `Turnover`: the date is `endInclusive` of one range and `start` of a different one. A one-day range is `Single`, never a turnover with itself.
- Overlapping ranges are caller data errors: the first range wins; the function never throws (tested).

**Rendering in `DefaultDay`** (draw order unchanged: band, then clipped fill layer, then overlay):

- `FullCell`: selection endpoints unchanged. A booked turnover cell is always drawn split horizontally, since two full cells cannot share one.
- `SplitHorizontal`: an end endpoint fills the start-side half (left in LTR, mirrored in RTL), a start endpoint the other half; the band continues on the inner side. Halves span the full cell height inside `clipRect`, intersected with `dayShape` so rounded styles still look right. When two halves have different colours a 1.dp `surface` separator is drawn between them so adjacent bookings do not merge.
- `SplitDiagonal`: the cell is cut along the bottom-left to top-right diagonal; a stay's end is the upper-left triangle (AM), its start the lower-right triangle (PM); turnover draws both with the separator line. Triangles are `Path`s clipped to the `dayShape` outline; mirrored in RTL.
- Day-number colour: `readableContentColor` against the fill of the half under the number's centre; for a turnover cell use `contentColor` on the default tinted halves (inRange container at alpha 0.9). Rei finalises.
- Animation: full-cell fills keep the scale spring; split endpoints fade alpha (200 ms, same spec as band halves) because a scaled half would detach from the cell edge.
- Accessibility: when the style is not `FullCell` or the cell is a turnover, the state description uses `orrery_day_state_check_in` / `_check_out` / `_turnover` ("Check-out and check-in"); `FullCell` keeps the existing range strings.

**Selection engine:** no change needed for rendering. A turnover date stays selectable as a new start because the caller disables only the genuinely unavailable nights through `DisabledDates`; documented in a sample recipe.

**Tests:** core `resolveRangeCell` (start, middle, end, single, turnover, adjacent but not touching, overlap safety). Compose: state-description strings; Roborazzi `hospitalityHorizontalLight/Dark`, `hospitalityDiagonalLight/Dark` (selection plus a booked range with a turnover day), and an RTL snapshot.

### 3.2 `OutDateClickPolicy`

```kotlin
public enum class OutDateClickPolicy {
    Ignore,                  // in/out-date cells do nothing on tap
    SelectOnly,              // selection applied, no scroll (0.8.0 behaviour)
    SelectAndScrollToMonth,  // selection applied and the calendar animates to the tapped date's month
}
```

- Parameter `outDateClickPolicy: OutDateClickPolicy = SelectOnly` on `HorizontalCalendar` (the issue's scope) and `VerticalCalendar` (symmetry). **Default `SelectOnly` preserves 0.8.0 behaviour.**
- `dayContent` is user code, so the calendar provides the policy via composition locals: `LocalOutDateClickPolicy` and `LocalCalendarScrollRequester` (set to `{ scope.launch { state.animateScrollToMonth(it) } }`). `DefaultDay` reads them for `day.position != MonthDate`:
  - `Ignore`: no click action; rendered with the normal out-date colour (not the "unavailable" style) and without the disabled semantic.
  - `SelectOnly`: current behaviour.
  - `SelectAndScrollToMonth`: after `selectionState?.click(date)` calls the scroll requester with the date's month.
- Public helper `rememberOutDateClickHandler(selectionState): (CalendarDay) -> Unit` applies the active policy in custom `dayContent`.
- Explicit selection never triggers `AutoSelectDay`. Irrelevant when out-dates are hidden (`OutDateStyle.None` with `showOutDates = false`).
- Tests: each policy (selection changed or not, page changed or not, `onEvent` called); keyboard Enter follows the policy.

### 3.3 `SelectionMode.Single(autoAdvance, autoSelectDay, allowClear = true)`

```kotlin
public data class Single(
    val autoAdvance: Boolean = false,
    val autoSelectDay: AutoSelectDay = AutoSelectDay.FirstDayOfMonth,
    val allowClear: Boolean = true,
) : SelectionMode
```

- `allowClear = true`: tapping the selected date clears the selection (like `Week`/`Month`). `allowClear = false`: tapping the selected date is a no-op, so the UI can never empty the selection (programmatic `clear()` and `set(Selection.Empty)` still work; documented).
- **Behaviour change warning:** in 0.8.0 `Single` never deselects (`SelectionEngineSingleMultiTest` asserts it). With the specified default `allowClear = true`, plain `Single()` now toggles off. Mitigations: changelog entry; the picker dialogs and `DatePickerState` construct `Single(allowClear = false)` internally so a dialog cannot lose its selection by a tap. Open decision 2 describes the alternative default.
- Binary compat: hidden secondary constructor `Single(autoAdvance, autoSelectDay)` and hidden two-argument `copy` bridge.
- Engine:
```kotlin
is SelectionMode.Single ->
    when {
        current.single == date && mode.allowClear -> SelectionResult(Selection.Empty, emptyList())
        current.single == date -> SelectionResult(current, emptyList())
        else -> SelectionResult(current.copy(single = date), emptyList())
    }
```
  Bounds and `disabled` checks run first, so tapping a disabled selected date is `Intercepted`, not a clear.
- `autoAdvance` month paging still applies `AutoSelectDay`; it never toggles an existing selection off.
- Tests: toggle off and on; no-op with `allowClear = false`; disabled selected date intercepted; `replace(Selection.Empty)` allowed for both; dialogs never empty by tap. Update the existing 0.8.0 test (rename it to cover `allowClear = false`).

---

## 4. #84 Direct Drag, 3-stop Morph, Week Sync and ScrollSource

### 4.1 Current behaviour

The scaffold animates `expandFraction` (1 = month, 0 = one week row) only from the body's `NestedScrollConnection` and `expand()/collapse()`. Without a scrollable body, or when dragging the calendar itself, nothing happens, and the week shown when collapsed is not tied to the month.

### 4.2 Formats and stops

```kotlin
public enum class CollapsibleCalendarFormat { Month, TwoWeeks, Week }
```

- `expandFraction` stays 1f..0f for existing consumers. Stop fractions: Week = 0, Month = 1, TwoWeeks = `dayHeight / (monthHeight - dayHeight)` (height `2 * dayHeight` lies on the same linear height line, so TwoWeeks is just one more snap target and the height formula `lerp(dayHeight, monthHeight, f)` needs no piecewise logic). If the month has 2 rows or fewer the TwoWeeks stop is skipped.
- State API: `val format: CollapsibleCalendarFormat` (nearest stop, snapshot-observable), `suspend fun animateToFormat(format)`; `expand()` (to Month) and `collapse()` (to Week) stay; `isExpanded = format == Month`. New overload `rememberCollapsibleCalendarState(initialFormat)`; the saver stores the ordinal and still restores old Boolean-saved values (true = Month, false = Week).

### 4.3 Direct vertical drag

New scaffold parameters (appended; hidden bridge for the old signature):

```kotlin
showDragHandle: Boolean = false,
dragHandle: (@Composable BoxScope.(CollapsibleCalendarState) -> Unit)? = null,  // null = default pill
calendarDragEnabled: Boolean = true,       // drag on the calendar surface itself
allowedFormats: Set<CollapsibleCalendarFormat> = CollapsibleCalendarFormat.entries.toSet(),
focusDate: LocalDate? = null,              // see 4.4
onFormatChange: ((CollapsibleCalendarFormat) -> Unit)? = null,
```

- **Surface drag** (when `gesturesEnabled && calendarDragEnabled`): `Modifier.draggable(Orientation.Vertical)` on the calendar container. Compose touch-slop and axis arbitration keep horizontal swipes with the pager (`LazyRow`) and vertical drags with the scaffold. Delta maps to `state.dragBy(delta / rangePx)` with `rangePx = (monthHeight - dayHeight).toPx()`.
- **Settle:** on release project `f' = f + velocity * 0.15s / rangePx`, snap to the nearest stop in `allowedFormats` with the existing `tween(240)`, then fire `onFormatChange` once if the settled format changed. (0.15 s projection and the easing are Rei's to tune.)
- **Drag handle:** a default pill (32.dp × 4.dp, `outlineVariant`, 8.dp vertical padding, 48.dp minimum touch target) under the calendar with the same draggable, a click that cycles Month → TwoWeeks → Week → Month (skipping disallowed formats), semantics (`stateDescription` = current format, custom accessibility actions Expand and Collapse), a focus ring, and keyboard Up/Down to step formats. This is the non-gesture accessibility path.
- **Nested scroll:** the existing connection shares `dragBy/settle`; `onPreFling` now snaps to the nearest *allowed* stop rather than only 0 or 1. Priority is unchanged: collapse while the body scrolls up; expand when the body is at its top and the drag is downward.
- With a non-scrolling body, the surface drag and handle are the only morph inputs, which fixes the issue.

### 4.4 Rendering and synced week paging

- While dragging, the month grid is shown clipped to the container height. Rows translate so the row containing `focusDate` stays visible while collapsing: `translationY = -(1 - f) * rowOffsetPx` (replacing the 0.8.0 alpha cross-fade that always clipped to the top row).
- `focusDate` resolution: the parameter if given; else the first day of the currently visible collapsed block; else the first day of the visible month. The collapsed block is the week (or two-week block) containing `focusDate` if it lies in the visible month, else the first week of the visible month.
- When the settled format is TwoWeeks the scaffold shows `WeekCalendar` with `visibleWeeksCount = 2`, at Week with 1. The scaffold mutates the caller's `weekState.visibleWeeksCount` only at settle (never mid-drag, to avoid rebuilding paging during a gesture); this is documented. Swap from month grid to week calendar uses a 120 ms cross-fade (Rei to tune).
- **Sync while collapsed:** paging the `WeekCalendar` updates `calendarState` to the month containing the middle of the visible block via `scrollToMonth` (non-animated, recorded as `ScrollSource.Programmatic`), so the title stays right and re-expansion starts on the correct month.
- **Sync while expanded:** paging months scrolls `weekState` to the week containing `focusDate` when it falls in the new month, else to the month's first week.
- A re-entrancy flag stops the two directions re-triggering each other. The scaffold forces `weekState.firstDayOfWeek` and `visibleDaysOfWeek` to match `calendarState` (documented single source of truth).

### 4.5 `ScrollSource` and state-level `userScrollEnabled`

```kotlin
public enum class ScrollSource { Initial, UserDrag, Programmatic }
```

`CalendarState`, `WeekCalendarState` and `YearCalendarState` each gain:

- `val lastScrollSource: ScrollSource` (snapshot-observable, starts as `Initial`, not saved).
- `var userScrollEnabled: Boolean` (constructor and `remember*State` parameter, default true, not saved). The composables' existing `userScrollEnabled` parameter is kept; effective value = parameter AND state value.

Tracking needs no extra gesture detectors:

- All programmatic entry points (`scrollToMonth`, `animateScrollToMonth`, `scrollToDate`, `animateScrollToDate`, `scrollToYear`, `animateScrollToYear`, `updateRange`) set `lastScrollSource = Programmatic` before touching `listState` and keep an in-flight counter decremented in `finally`.
- A `LaunchedEffect` in each calendar collects `listState.interactionSource.interactions`: a `DragInteraction.Start` means a user drag or fling (animated scrolls emit none) and sets `UserDrag`. The counter protects the window before the first frame.
- With `userScrollEnabled = false` the source can only become `Programmatic`.
- Observation helpers: `scrollSourceChanges(): Flow<ScrollSource>` and `visibleMonthsWithSource()` / `visibleWeeksWithSource()` / `visibleYearsWithSource()` (so analytics can ignore programmatic scrolls).

### 4.6 Tests

Gesture tests: vertical swipe on the calendar goes Month → TwoWeeks (short) or Week (long/fast) and back with a non-scrolling body; horizontal swipe still pages; handle tap cycles; handle keyboard Up/Down; `allowedFormats = {Month, Week}` skips TwoWeeks; collapsed week swipe updates `calendarState.firstVisibleMonth` and re-expansion shows it; month paging updates `weekState`; `onFormatChange` fires once per settle; `ScrollSource` goes Initial → Programmatic (`scrollToMonth`) → UserDrag (`swipeLeft`) on all three states; `userScrollEnabled = false` blocks swipes. Roborazzi: month, two-weeks and week stops light/dark with and without handle, plus a mid-drag frame at f = 0.6.

---

## 5. #85 ScheduleView Power Upgrades

### 5.1 What already exists

The brief and issue say "ScheduleView"; the public composables are `DaySchedule` and `WeekSchedule` sharing one internal `PagedSchedule`. No new public `ScheduleView` or `SchedulePager` type is added; parameters go on both composables. Already present in 0.8.0: horizontal 1/3/5/7-day paging via `daysPerPage`, pinch-to-zoom (`zoomGesturesEnabled`, `ScheduleState.zoomBy`), `scrollToTime`. v0.9.0 therefore hardens zoom (5.6) rather than re-adding it, and adds the missing pieces: hour window, stacked overlap, multi-day all-day banners, `scrollToCurrentTime/scrollToHour`.

### 5.2 Core: `minHour`, `maxHour`, `EventOverlapMode`

```kotlin
public sealed interface EventOverlapMode {
    public data object SideBySide : EventOverlapMode            // parallel columns (0.7.0 behaviour)
    public data class Stacked(public val offsetFraction: Float = 0.15f) : EventOverlapMode {
        init { require(offsetFraction in 0f..0.5f) }
    }
}

public fun <T> layoutDaySchedule(
    date: LocalDate,
    events: Iterable<CalendarEvent<T>>,
    minDurationMinutes: Int = 15,
    minHour: Int = 0,                                  // 0..23
    maxHour: Int = 24,                                 // minHour + 1 .. 24
    overlapMode: EventOverlapMode = EventOverlapMode.SideBySide,
): DayScheduleLayout<T>
```

- `require(minHour in 0..23 && maxHour in (minHour + 1)..24)`.
- **Window clipping:** the layout window is `minHour*60 .. maxHour*60`. `PositionedEvent` minutes stay *absolute minutes of the day* so labels stay correct. Events entirely outside are dropped from `timedEvents`; partly outside events get `startMinute = max(start, window start)` and `displayEndMinute = min(.., window end)`, and two new defaulted fields `clippedStart: Boolean = false`, `clippedEnd: Boolean = false` (hidden `copy`/constructor bridges). `endMinute` keeps the real end for the time label. The overnight-ending-at-midnight exclusion is evaluated before window clipping.
- **Stacked geometry:** same cluster partitioning and lane assignment as today (so paint order is deterministic), then with `lane = min(columnIndex, 3)`:
  - `leftFraction = lane * offsetFraction`, `widthFraction = 1f - leftFraction` (right edges aligned, later lanes narrower and on top).
  - `columnCount` stays informational; `columnIndex` is paint order. The rightward-expansion step is skipped.
  - A non-overlapping event is full width. Sort in a cluster is (start asc, displayEnd desc), so long events sit at the bottom.
- `SideBySide` with default arguments is byte-for-byte the 0.8.0 result (regression test).

**Core tests (extend `ScheduleLayoutTest`):** stacked offsets 0 / 0.15 / 0.30 and right-aligned widths; lane cap at 4; window drops outside events and flags clipped ones; overnight events with a window; `require` failures; default-args regression.

### 5.3 Compose parameters

On `DaySchedule` and `WeekSchedule` (appended before the slot lambdas; hidden bridge for the old signature each): `minHour: Int = 0`, `maxHour: Int = 24`, `overlapMode: EventOverlapMode = SideBySide`.

- Grid height = `hourHeight * (maxHour - minHour)`; gutter labels for `minHour until maxHour`; grid lines `0..(maxHour - minHour)`. `y = (minute - minHour*60) / 60f * hourPx`. The now-line is hidden (not clamped) outside the window. `onEmptySlotClick`'s `timeAt` adds `minHour*60` back. The `hourLabel` slot receives the absolute hour.
- `rememberScheduleState(initialScrollTime)` is clamped into the window on first composition. A stored scroll offset is clamped by `ScrollState` if the window shrinks.
- Stacked painting: `Modifier.zIndex(positioned.columnIndex.toFloat())` on each event box in stacked mode so the topmost tile wins hit-testing. Stacked tiles use an **opaque** container (`container.compositeOver(surface)`) because translucent overlapping tints look muddy, plus a `stackedTileSeparatorWidth` (1.dp) `surface` outline on the leading edge.
- New `ScheduleDimensions` fields (appended, hidden bridge): `stackedTileSeparatorWidth: Dp = 1.dp`, `clippedEdgeMarkerHeight: Dp = 4.dp` (notch at a clipped top/bottom edge), `allDayBannerCornerRadius: Dp = 6.dp`.

### 5.4 `scrollToCurrentTime()` / `scrollToHour(hour)`

```kotlin
public suspend fun scrollToHour(hour: Int)            // hour clamped to minHour..maxHour-1; hour at viewport top
public suspend fun animateScrollToHour(hour: Int)
public suspend fun scrollToCurrentTime(
    currentTime: LocalTime? = null,   // null -> clock helper already used by CurrentDate.kt
    today: LocalDate? = null,         // null -> currentDate()
    animate: Boolean = true,
)
```

- `scrollToTime(time, density)` stays. The new methods need density and the hour window without composition: `PagedSchedule` calls `state.attachEnvironment(density, minHour, maxHour)` with the same attach/detach lifecycle as the pager. Calls made while detached store a *pending request* that runs on next attach, so `LaunchedEffect(Unit) { state.scrollToCurrentTime() }` is safe on first composition.
- Target offset for the current time: `max(0, nowOffsetPx - viewportHeightPx / 3)` clamped to `0..maxValue` (viewport from `verticalScrollState.viewportSize`).
- If `today` is outside the current page, `animateScrollToDate(today)` runs first, then the vertical scroll, sequentially.
- Explicit `currentTime`/`today` parameters keep tests deterministic; the library still has no polling clock.

### 5.5 Multi-day all-day header banners in WeekSchedule

This supersedes the 0.7.0 resolved decision "per-column chips, no spanning bars in v1".

- `AllDayRow` becomes `AllDayBannerRow`, reusing **#81**: `packWeekEventBanners(pageDates, allDay + multiDayTimed, lanes)` and composing `WeekEventBanners` with `bannerContent = { ScheduleDefaults.AllDayEventChip(...) }` so there is one banner implementation. The `allDayEventContent: (CalendarEvent<T>) -> Unit` slot keeps its signature and is used as the banner body, sized to the span.
- Multi-day *timed* events appear as a header banner and also on the grid per day (Google Calendar parity); Open decision 5.
- Header height = `lanes * allDayChipHeight + (lanes - 1) * 2.dp`. `maxVisibleAllDayRows` (3) keeps meaning "lanes". Overflow shows "+N" per column (`ScheduleStrings.moreLabel`); tapping it sets `ScheduleState.allDayExpanded` (not saved), which shows all lanes up to 50% of the viewport with an internal scroll.
- `DaySchedule` (one column) degrades to `span = 1` chips on the same code path with 0.8.0-identical visuals when no event spans days.
- A banner continuing from the previous page shows the flat leading cap; `daysPerPage` blocks that do not align with weeks (for example 3) clip the same way.

### 5.6 Pinch-to-zoom hardening

- Focal anchoring is already grid-relative, so it stays correct with `minHour`.
- Dynamic lower bound: `effectiveMin = max(minHourHeight, viewportHeight / (maxHour - minHour))` so a 9–17 window cannot zoom out into empty space.
- Optional discrete zoom: `ScheduleState` parameter `hourHeightSteps: List<Dp>? = null`; when set, a finished pinch settles to the nearest step with a 150 ms animation. Default null (continuous); not part of the saver.
- Tests: clamps; centroid time stays within 1 px; single-finger scroll unaffected; effective minimum with a window; discrete settle.

### 5.7 Tests and snapshots

Compose: `minHour = 8, maxHour = 18` renders 10 gutter labels and no 07:00 tile; stacked z-order and tap hits the top tile; `scrollToHour(14)` puts 14:00 at the top (±1 px); `scrollToCurrentTime` places the now-line about one third from the top and pages to `today`; pending request applied on attach; a banner spans three columns and shows flat caps across weeks; "+N" expands. Roborazzi: `weekScheduleWindowedLight/Dark`, `weekScheduleStackedLight/Dark`, `weekScheduleAllDayBannersLight/Dark`. Sample: the Day / Week / List switcher in `AgendaScreen` gets controls for overlap mode, hour window and zoom steps.

---

## 6. Cross-cutting

### 6.1 Compatibility summary

| Area | Change | Compatibility |
|---|---|---|
| `daysOfWeek`, `monthGrid`, `weekGrid` | trailing `visibleDaysOfWeek` | hidden old-signature functions |
| `CalendarState`, `WeekCalendarState`, `YearCalendarState` | new properties, saver fields | savers tolerate old lists |
| `CalendarDayShapes`, `dayShapes` | `rangeEndpointStyle` | hidden bridges |
| `DefaultDay` (both overloads) | `bookedRanges` | hidden bridges |
| `SelectionMode.Single` | `allowClear` | **behaviour change by default** (3.3) |
| Horizontal/Vertical/WeekCalendar | slots, `outDateClickPolicy`, `rowHeight`, `weekOverlayHeight` | hidden bridges |
| Collapsible scaffold and state | new params, 3 stops | saver accepts old Boolean |
| `layoutDaySchedule`, `PositionedEvent`, `ScheduleDimensions` | trailing params/fields | hidden bridges |
| `DaySchedule`/`WeekSchedule` | `minHour`, `maxHour`, `overlapMode` | hidden bridges |

### 6.2 Localization

New strings in EN, DE, ES, UK, ZH-CN, JA: `orrery_week_banner_more`, `orrery_day_state_check_in`, `orrery_day_state_check_out`, `orrery_day_state_turnover`, `orrery_collapsible_handle`, `orrery_collapsible_format_month`, `orrery_collapsible_format_two_weeks`, `orrery_collapsible_format_week`, `orrery_collapsible_expand`, `orrery_collapsible_collapse`, `orrery_schedule_more`.

### 6.3 Platforms

All logic is `commonMain`; the pointer APIs used (`draggable`, `pointerInput`, `nestedScroll`) exist on Android, Desktop, iOS and wasmJs. On Desktop/Web the drag handle gets the pointer-hover highlight (`hoverContainerColor` token) and mouse-wheel scrolling keeps feeding the nested-scroll connection. Roborazzi snapshots stay Android/JVM only.

### 6.4 Rollout order (for Maya)

1. `orrery-core` pieces with tests, one PR each: `visibleDaysOfWeek` (#82), `WeekEventBanners` (#81), `resolveRangeCell` + `Single.allowClear` (#83), `ScheduleLayout` window and overlap (#85).
2. Compose: #82 (grid width, `rowHeight`, `visibleWeeksCount`), then #81 slots, then #83 rendering, #84, #85.
3. Sample app: Studio toggles per feature in the same PR as the feature.
4. README feature table and 0.9.0 changelog, including the `Single.allowClear` behaviour note.

---

## 7. Open Decisions for Andrii (with recommended defaults)

1. **visibleDaysOfWeek and `SelectionMode.Week`:** hidden days stay part of the selected 7-day range and of disabled-date checks.
   - *Recommended default:* keep the full week range (selection semantics independent of presentation).
2. **Default of `Single.allowClear`:** `true` (as specified; consistent with Week/Month) changes 0.8.0 behaviour where a tap on the selected date keeps it.
   - *Recommended default:* ship `true`, call it out in the changelog, force `false` inside picker dialogs. Alternative: default `false` for zero behaviour change.
3. **`OutDateClickPolicy` default:** `SelectOnly` (0.8.0) vs `SelectAndScrollToMonth`.
   - *Recommended default:* `SelectOnly`.
4. **Stacked offset unit:** fraction of column width (per brief) vs the issue text's dp.
   - *Recommended default:* fraction (resolution independent); a dp variant can be added later to the sealed interface without breaking it.
5. **Multi-day timed events in the all-day header:** also shown as header banners.
   - *Recommended default:* yes (Google Calendar parity); add a `showMultiDayTimedInHeader` opt-out only if QA objects.
6. **Turnover input:** caller-fed `RangeOverlay` vs a dedicated multi-range selection mode.
   - *Recommended default:* `RangeOverlay` (additive, no engine change); a multi-booking mode is a possible v0.10 follow-up.
