# v1.0.0 Phase 2: Adaptive UI — Dual-Month Range Picker and Interactive Schedule — Design

**Date:** 2026-11-09
**Status:** Draft for review (Andrii); visual pass by Rei, implementation by Maya, QA by Asuka
**Issues:** #88 (tracking roadmap: #91)
**Base:** main at cf1c334 (v0.9.2 plus Phase 1: #86 recurrence, #87 viewport flows)
**Owners:** Ritsuko (architecture), Rei (visual spec), Maya (implementation), Asuka (QA review)

## Context

Two 0.9.x gaps remain before the 1.0.0 API lock (#89):

1. `OrreryDateRangePickerDialog` is a full-screen single-column `VerticalCalendar` on every window size. On tablets, desktop and web it wastes the width and cannot show a range that crosses a month boundary without scrolling.
2. `DaySchedule` / `WeekSchedule` are read-only apart from `onEventClick` and `onEmptySlotClick`. Hosts cannot create, move or resize events by gesture or keyboard.

Ground rules (same as Phase 1):

- All logic is `commonMain` in `orrery-compose`; no new modules, no new dependencies, nothing in `orrery-core`. Pointer APIs used (`awaitEachGesture`, `pointerInput`, `onPreviewKeyEvent`, `semantics`) exist on Android, Desktop, iOS and wasmJs.
- Binary compatibility with 0.9.2 is kept: new parameters are inserted before the slot lambdas (precedent: #85 `minHour/overlapMode`), the previous signature stays as a `@Deprecated(level = HIDDEN)` bridge, and every PR runs `./gradlew checkKotlinAbi` after `updateKotlinAbi` for the three dumps (`orrery-compose/api/android`, `api/desktop`, `orrery-compose.klib.api`). Lesson from #63: a HIDDEN bridge of a **non-composable** function with defaults loses its `$default` method, so add an overload instead; all functions below are composable, where the bridge pattern already works (`OrreryDateRangePickerDialog` presets bridge).
- Pure gesture math (snapping, clamping) is `internal` in `orrery-compose` and unit-tested in `commonTest`. It stays internal so the 1.0 API surface does not freeze it.
- Gradle runs only in CI (no JDK on the agent host); PRs must show green CI.

### Existing solutions check

- **Dual-month pickers:** Material 3 `DateRangePicker` is single-column; maxkeppeler/sheets-compose-dialogs#8/#29 ask for it but ship nothing; Airbnb HorizonCalendar and kizitonwose/Calendar leave it to the host. Nothing KMP-ready to adopt. Orrery already has the parts (`CalendarState` month pager, `MonthContent`, `DefaultDay`, drag selection, `rangePreview`).
- **Time-grid editing:** Android-Week-View and flutter_calendar_view use a long-press-to-drag model with ghost tiles; no Compose Multiplatform library does. **Decision: implement in-tree** with the long-press model.

**Deliberately NOT building in Phase 2:**

- Dragging an event across **pages** (auto-flipping the pager at the horizontal edge), and dragging all-day banners or month-grid chips. Moves stay inside the visible page (open decision 7).
- Move/resize of multi-day timed events (start date ≠ end date) and of all-day events. They stay click-only.
- Series editing: callbacks receive the occurrence (`event.instance != null` for recurring ones); "this/following/all" is the host's concern (as in Phase 1).
- Overlap/conflict prevention. The host decides in the callback.
- Persisting edits. The schedule is stateless: it never mutates events, the host updates its list and the new layout is recomposed.
- Dual-month in `HorizontalCalendar`/`VerticalCalendar` themselves, or more than two months (open decision 3).
- Pointer-drag between the two range endpoints of an existing range ("grab and move the end handle"); range drag stays the 0.9 long-press drag.

---

## 1. Dual-month side-by-side OrreryDateRangePickerDialog

### 1.1 Public API

```kotlin
package me.kozakov.orrery.compose

/** How [OrreryDateRangePickerDialog] arranges its calendar. */
public sealed interface DateRangeCalendarLayoutMode {
    /** The 0.9 layout: one vertically scrolling column of months. */
    public data object SingleMonth : DateRangeCalendarLayoutMode

    /** Two consecutive months side by side regardless of the available width. */
    public data object DualMonth : DateRangeCalendarLayoutMode

    /** [DualMonth] when the dialog's available width is at least [minDualMonthWidth], else [SingleMonth]. */
    @Immutable
    public class Adaptive(
        public val minDualMonthWidth: Dp = DatePickerDefaults.MinDualMonthWidth,   // 640.dp
    ) : DateRangeCalendarLayoutMode {
        // equals / hashCode / toString on minDualMonthWidth (plain class, so more options can be added after 1.0)
    }
}

// DatePickerDefaults.MinDualMonthWidth: Dp = 640.dp  (new const-like member; Adaptive's default)

@Composable
public fun OrreryDateRangePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    state: OrreryDateRangePickerState = rememberOrreryDateRangePickerState(),
    modifier: Modifier = Modifier,
    colors: CalendarDayColors = CalendarDefaults.dayColors(),
    shapes: CalendarDayShapes = CalendarDefaults.dayShapes(),
    today: LocalDate = currentDate(),
    strings: DatePickerStrings = DatePickerDefaults.strings(),
    presets: List<DateRangePreset> = emptyList(),
    layoutMode: DateRangeCalendarLayoutMode = DateRangeCalendarLayoutMode.Adaptive(),
)
```

- The 0.9.2 signature (with `presets`, without `layoutMode`) becomes a `@Deprecated("Kept for binary compatibility; use the overload with layoutMode.", level = HIDDEN)` bridge that delegates with `layoutMode = Adaptive()` (the existing no-presets bridge keeps delegating to the presets one). Source-compatible: `layoutMode` is last and defaulted.
- `OrreryDateRangePickerState` and `rememberOrreryDateRangePickerState` are **unchanged** (no ABI change). The layout mode is a presentation choice that depends on the window, so it belongs to the dialog, not the saveable state. `state.displayedMonth` keeps meaning "month at the first visible page", i.e. the **left** month in dual mode (right month in RTL is the same month, shown mirrored).
- **Default is `Adaptive()` (open decision 1):** the issue acceptance criterion says width ≥ 640.dp switches to two months. This changes the look of existing tablet/desktop apps on upgrade; the CHANGELOG calls it out and `layoutMode = SingleMonth` restores 0.9.2.

### 1.2 Resolution and dialog sizing

```kotlin
Dialog(onDismissRequest, DialogProperties(usePlatformDefaultWidth = false)) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val dual = when (layoutMode) {
            SingleMonth -> false
            DualMonth -> true
            is Adaptive -> maxWidth >= layoutMode.minDualMonthWidth
        }
        Surface(
            modifier = if (dual) modifier.widthIn(max = DualMonthDialogMaxWidth /* 840.dp */)
                                .heightIn(max = DualMonthDialogMaxHeight /* 720.dp */)
                                .wrapContentHeight()
                       else modifier.fillMaxSize(),
            shape = if (dual) MaterialTheme.shapes.extraLarge else RectangleShape,
            tonalElevation = if (dual) 6.dp else 0.dp,
        ) { /* top bar, headline, presets, body */ }
    }
}
```

- Width is measured from the dialog **window** (the `BoxWithConstraints` above the `Surface`), not from the Surface, so the Surface's own max width cannot flip the mode back and forth.
- Dual mode is a centred card: width ≤ 840.dp (two months need ≥ 2 × 7 × 40.dp + gutters ≈ 640.dp; 840.dp leaves ≥ 48.dp cells), height ≤ 720.dp, `extraLarge` shape, tonal elevation 6.dp (same recipe as `OrreryDatePickerDialog`). Below that size it fills the window. forced `DualMonth` on a window narrower than 560.dp still renders two months with the cell size shrunk (documented as "you asked for it"); a `require` is not used.
- Short windows (compact height): the body is wrapped in `verticalScroll`, as in the other dialogs since #65.
- Top bar, headline, display-mode toggle and presets chips are unchanged. In dual mode the **Input** face lays the two `DateInputField`s in a `Row` (weight 1f each, 16.dp gap) instead of a column.
- Rotation/resize flips the mode live. `CalendarState` is shared, so the visible month, selection and display mode survive (see 1.3 for the paging-index handling).

### 1.3 Layout of the dual calendar (internal)

```kotlin
@Composable
internal fun DualMonthRangeCalendar(
    state: OrreryDateRangePickerState,
    today: LocalDate,
    colors: CalendarDayColors,
    shapes: CalendarDayShapes,
    strings: DatePickerStrings,
    modifier: Modifier = Modifier,
)
```

Structure (top to bottom, 24.dp horizontal padding, 16.dp gap between the months):

1. **DualMonthNavHeader**: `[‹]  July 2026   |   August 2026  [›]  [Today]`. The two titles (`PlatformDateFormat.yearMonthTitle`, `titleMedium`) are centred over their month columns; chevrons are `IconButton` with the existing `NavChevron` (auto-mirrors in RTL) and `strings.previousMonthDescription/nextMonthDescription`; **Today** is a `TextButton` (`OrreryString.Today`, already in 6 locales) shown only while neither visible month is today's (and today is in `yearRange`), exactly like `CalendarNavHeader.showTodayButton`.
2. **Pager**: a `LazyRow` on the **same `state.calendar`** (`listState`), one item per month, each item `fillParentMaxWidth(0.5f)`, `rememberSnapFlingBehavior`. Because items are half a viewport wide, `firstVisibleMonth` is M and M + 1 is automatically the right neighbour: one source of truth, no second state, `visibleDateRange` (Phase 1) already reports both months. Each item is `Column { weekday header; MonthContent }` (`MonthContent` unchanged, `showOutDates = false`, as in the single dialog) so swiping slides whole months.
3. Reached end of range: `LazyRow` cannot scroll past its content, so the last reachable left month is `endMonth − 1` (the picker never shows an empty right pane). `displayedMonth` then reports that month even if `initialDisplayedMonth` was the final one; documented.

**Navigation (synchronised, always one month):**

| Action | Effect |
|---|---|
| ‹ / › | `animateScrollToPreviousMonth()` / `animateScrollToNextMonth()` (shifts both panes by 1). ‹ disabled when M == start, › disabled when M + 1 == end. |
| Today | if today's month is M or M + 1: no scroll (already visible), else `animateScrollToMonth(todayMonth)` so today's month becomes the **left** pane. |
| Swipe | snaps to a month boundary; the visible pair is always (M, M + 1). |
| Preset chip / switching Input → Picker | `scrollToMonth(start's month)` as today (start is the left pane). |
| PageUp / PageDown on a day | focus moves to the same day ±1 month (existing `pageByPeriod`). See 1.4. |

**Switching between modes without a visual flash.** `VerticalCalendar(stickyMonthHeaders = true)` runs `CalendarState` with `itemsPerMonth = 2` (header + grid item per month); the dual pager needs 1. Mixing them for a frame would show month 2m. Add an `internal fun CalendarState.useItemsPerMonth(n: Int)` that, **synchronously during composition** (called from `remember(layoutKey)` in the mode's composable, before the list measures): reads `firstVisibleMonth` with the old value, sets `itemsPerMonth = n` and calls `listState.requestScrollToItem(index)`. The single-month branch calls `useItemsPerMonth(2)` the same way (replacing its `LaunchedEffect` for the dialog case; the standalone `VerticalCalendar` keeps its own effect).

### 1.4 Selection across months

- **Tap/click:** unchanged: `DefaultDay(selectionState = state.selection)` in both months calls `CalendarSelectionState.click`. Range start in M, end in M + 1 just works; guardrails (`minRangeDays`, `maxRangeDays`, `disallowDisabledInRange`) are applied by the engine, so there is **no dual-specific selection code**.
- **Hover preview (pointer devices):** while `selection.rangeStart != null && rangeEnd == null`, hovering a day in either month draws `DayDecorators.rangePreview(selection, previewDate)` (the #67 decorator, existing) across both months. `previewDate` is a snapshot `mutableStateOf<LocalDate?>` held by `DualMonthRangeCalendar`, driven by a container-level `pointerInput` listening to `PointerEventType.Move/Enter/Exit` with `PointerType.Mouse/Stylus` only (touch has no hover). Exit or rangeEnd set → `null`. Disabled dates and dates that would violate guardrails still preview (the click then fires the usual event); no "invalid preview" colour in v1.
- **Drag across months (long-press drag):** the single-month `MonthContent` drag handler only knows its own month. In dual mode the `pointerInput` moves up to the **container** wrapping the `LazyRow` and `MonthContent` is called with `dragSelection = null` for dragging (key navigation still needs the `CalendarSelectionState`, so a new internal `MonthContent` parameter `selectionForKeys` carries it). The container handler hit-tests like `MonthContent.dayAt` but in two steps:
  1. pick the month item from `listState.layoutInfo.visibleItemsInfo` whose `[offset, offset + size)` contains the x coordinate. In RTL `offset` is measured from the right edge, so use `x' = width − x` first;
  2. inside the month use the existing row/column arithmetic (extract it into `internal fun monthDayAt(month, weekNumberLeading, size, offset, isRtl, rowTops)`, shared by `MonthContent`; the extraction must keep the current behaviour byte-for-byte, covered by `DragSelectTest`).
  The long-press gate (`detectDragGesturesAfterLongPress`) and haptics (LongPress start, `SegmentFrequentTick` per new date) are the same. During the drag, horizontal motion within 24.dp of the container's left/right edge auto-pages by one month at most every 400 ms (a drag from M to M + 3 needs this; same spirit as the vertical auto-scroll in 2.6). Swipe scrolling is naturally blocked because the long-press drag consumes the pointer.
- **Out dates:** `showOutDates = false` in both panes; the blank cells of a month are not focusable, and a date is never shown twice.

### 1.5 Keyboard (cross-month)

Everything in 0.8.0/0.9.1 keeps working (`Home/End`, `Ctrl+Home/End`, `PageUp/Down`, `Shift+PageUp/Down` by year, `Shift+Arrow` range extension). Dual mode changes only **how an arrow reaches its target**:

- Plain focus search cannot be trusted across the two `LazyRow` items (after `Jul 31` the nearest focusable to the right may be a blank cell, or in RTL the wrong pane). `CalendarKeyboardNavigation` gets `val dualMonth: Boolean = false` (constructor default keeps source compatibility; it is internal) and, when true, `moveByArrow` **always** resolves the target date arithmetically and calls `keyboard.page(target)` (consuming the event) instead of falling through to `focusManager.moveFocus`. `Shift` still runs `extendRange(anchor, target)` first, so `Shift+→` on `Jul 31` extends the range into `Aug 1` and focus lands there.
- The dual `pageToDate` used by `keyboard.page`:
  ```kotlin
  suspend fun pageToDate(target: LocalDate) {
      val m = YearMonth(target.year, target.month)
      val left = calendar.firstVisibleMonth
      when {
          m == left || m == left.plus(1) -> Unit                           // visible: only move focus
          m < left -> calendar.animateScrollToMonth(m)                      // backwards: target becomes the left pane
          else -> calendar.animateScrollToMonth(maxOf(m.minus(1), start))   // forwards: target becomes the right pane
      }
  }
  ```
  then `pendingFocus.value = target` as today. Moving forward therefore slides by one month at a time instead of jumping two; moving between the panes never scrolls.
- **RTL:** `horizontalArrowDayOffset` already maps `DirectionRight` to −1 day under RTL; the earlier month M is the right pane in RTL, so "Right = earlier day" is visually consistent and no extra mirroring is needed. Both panes share one `CalendarKeyboardNavigation` instance, so `pendingFocus` works across items.
- Initial focus: `rememberInitialDayFocus(selectedStart ?: today)` as today (a no-op when that date is not in M/M + 1).

### 1.6 RTL and visual spec

- `LazyRow` and `Row` mirror under `LayoutDirection.Rtl`: M (earlier) on the right, M + 1 on the left; chevrons mirror (the `NavChevron` already flips; ‹ stays "previous"). Range band halves and split endpoints already mirror in `DefaultDay`.
- Each month column: 12.dp inner horizontal padding; weekday header in `labelSmall` as in `CalendarDefaults.WeekHeader`; month titles live in the nav header (no per-month title inside the item, so the month grid stays flush with its header).
- A subtle `outlineVariant` 1.dp vertical divider between the two months (`HorizontalDivider` rotated is not available; draw with `drawBehind` in the pager container), 16.dp inset top and bottom.
- Rei confirms: month title weight (titleMedium vs titleSmall), divider yes/no, and the dual card's 24.dp vs 28.dp corner radius (open decision 8).

### 1.7 Accessibility

- Each month column is `semantics { isTraversalGroup = true; contentDescription = "<Month Year>" }` so TalkBack/Switch Access reads month 1 fully then month 2 (logical order, not left/right: in RTL the first group is on the right).
- The nav header title row has `liveRegion = Polite` and a combined description `"<M title> – <M+1 title>"` (built from `yearMonthTitle`, no new string) so paging is announced once.
- Day cells keep `DefaultDay`'s date content description and `stateDescription` (range start/end/in range/today/unavailable), so a range crossing the two panes is described per day.
- Chevron, Today, close and toggle controls reuse existing `strings.*` and `OrreryString.Today`. **No new strings for the picker** (open decision 9 asks whether `Today` should also appear when today's month is the right pane; recommended default: no).
- Touch target ≥ 48.dp for chevrons and Today (existing `IconButton`/`TextButton`).

---

## 2. Interactive ScheduleView

"SchedulePager" in the issue is not a public type: the public composables are `DaySchedule` and `WeekSchedule` over one internal `PagedSchedule` (decision recorded in the v0.9.0 spec section 5.1). Parameters go on both; **no `SchedulePager` type is added** (open decision 2).

### 2.1 Public API

Added to `DaySchedule` and `WeekSchedule` (the overloads **with** `overlapMode` become the full signature; the two 0.9.2 overlap-mode overloads and the two 0.8.0 overloads all become `@Deprecated(level = HIDDEN)` bridges, which also removes today's four-way default-overload ambiguity):

```kotlin
@Composable
public fun <T> DaySchedule(      // WeekSchedule identical plus daysPerPage after state
    events: (LocalDate) -> List<CalendarEvent<T>>,
    modifier: Modifier = Modifier,
    state: ScheduleState = rememberScheduleState(),
    today: LocalDate = currentDate(),
    currentTime: LocalTime? = null,
    userScrollEnabled: Boolean = true,
    zoomGesturesEnabled: Boolean = true,
    showHalfHourLines: Boolean = false,
    minEventDurationMinutes: Int = 15,
    overlapMode: EventOverlapMode = EventOverlapMode.SideBySide,
    colors: ScheduleColors = ScheduleDefaults.colors(),
    dimensions: ScheduleDimensions = ScheduleDefaults.dimensions(),
    strings: ScheduleStrings = ScheduleDefaults.strings(),
    onEventClick: ((CalendarEvent<T>) -> Unit)? = null,
    onEmptySlotClick: ((LocalDate, LocalTime) -> Unit)? = null,
    // --- new in 1.0 (all default to null/off: zero behaviour change) ---
    onEmptySlotLongPress: ((date: LocalDate, time: LocalTime) -> Unit)? = null,
    onSlotCreate: ((date: LocalDate, startTime: LocalTime, endTime: LocalTime) -> Unit)? = null,
    onEventMove: ((event: CalendarEvent<T>, newDate: LocalDate, newStartTime: LocalTime, newEndTime: LocalTime) -> Unit)? = null,
    onEventResize: ((event: CalendarEvent<T>, newStartTime: LocalTime, newEndTime: LocalTime) -> Unit)? = null,
    snapMinutes: Int = 15,
    hapticsEnabled: Boolean = true,
    ghostContent: @Composable BoxScope.(ScheduleDragPreview<T>) -> Unit = { preview ->
        ScheduleDefaults.DragGhost(preview, colors, dimensions)
    },
    // --- existing slots, unchanged ---
    dayHeader: (@Composable (LocalDate, Boolean) -> Unit)? = { date, isToday -> ScheduleDefaults.DayHeader(date, isToday, colors) },
    allDayEventContent: @Composable (CalendarEvent<T>) -> Unit = { /* unchanged */ },
    hourLabel: @Composable (Int) -> Unit = { /* unchanged */ },
    nowIndicator: @Composable () -> Unit = { /* unchanged */ },
    eventContent: @Composable BoxScope.(PositionedEvent<T>) -> Unit = { /* unchanged, plus handles, see 2.4 */ },
)

public enum class ScheduleDragKind { Create, Move, ResizeStart, ResizeEnd }

/** What the ghost tile shows while a gesture or keyboard edit is in flight. */
public class ScheduleDragPreview<out T>(
    public val kind: ScheduleDragKind,
    public val event: CalendarEvent<T>?,     // null for Create
    public val date: LocalDate,              // column the ghost is over
    public val startTime: LocalTime,
    public val endTime: LocalTime,           // may be 00:00 meaning end of day, see 2.3
)
```

- **Binary compat:** each previous overload gets `@Deprecated("Kept for binary compatibility; use the overload with editing callbacks.", level = HIDDEN)` and delegates with the new parameters at their defaults. A jvm-style reflective test (`ScheduleBinaryCompatTest` in `androidHostTest`, modelled on `DayInfoBinaryCompatTest`) looks up the four old methods by their parameter lists (including the Compose `Composer` / `$changed` / `$default` tail) and asserts they exist; `checkKotlinAbi` is the primary gate.
- `ScheduleStrings` (public class, constructor `(allDayLabel, nowLabel)`) gains the editing labels of 2.7 as additional constructor parameters; the 2-arg constructor stays as a `HIDDEN` secondary constructor that fills English fallbacks (only binary callers that built `ScheduleStrings` by hand see them; `ScheduleDefaults.strings()` resolves locale resources). `ScheduleDefaults.strings(...)` gets the new defaulted parameters plus a HIDDEN 2-arg bridge.
- `ScheduleDimensions` gains `resizeHandleWidth: Dp = 28.dp`, `resizeHandleThickness: Dp = 4.dp`, `resizeHandleTouchSize: Dp = 32.dp`, `ghostBorderWidth: Dp = 2.dp` (HIDDEN 9-arg bridge, same technique as #85).
- `ScheduleDefaults.DragGhost(preview, colors, dimensions, modifier)` is the default ghost slot; `ScheduleDefaults.EventTile` gets no new required parameters (handles are drawn by the schedule, not by the tile, so custom `eventContent` gets editing for free).
- Interaction is enabled **per callback**: no callback → no gesture, no handle, no semantics, no extra pointer nodes (verified in 2.9). `snapMinutes` must divide 60 or be a multiple of 60 up to 1440; `require(snapMinutes in 1..60 && 60 % snapMinutes == 0)` keeps the grid visually consistent (so 5, 10, 15, 20, 30, 60).

### 2.2 Gestures and who owns the pointer

Gestures are attached **above** the day columns, inside the vertically scrolling grid, so a drag can cross columns in a week:

```
Box(verticalScroll)                         // existing
 └ Row(gutter | columns)                    // existing; DayColumn tiles register bounds
 └ ScheduleInteractionLayer(controller)     // NEW: ghost drawing + auto-scroll + create gesture
```

- `ScheduleDragController` (internal, `@Stable`, `remember` per `PagedSchedule`): `active: ScheduleDragPreview<*>?`, `sourceEventId: String?`, the page's column bounds, the grid's `hourPx` and `HourWindow`. Tile and column modifiers feed pointer positions in **grid coordinates** (`positionInParent` chain of the grid `Box`, which is inside the scroll container, so scrolling needs no correction).
- **Create** (`Modifier.scheduleCreateGesture` on each `DayColumn`, next to the existing `detectTapGestures` for `onEmptySlotClick`; the tap detector stays and loses to the drag by consumption): `awaitEachGesture`; touch → `awaitLongPressOrCancellation` (platform `longPressTimeoutMillis`, 400 ms), mouse/stylus → `awaitTouchSlopOrCancellation` (press-and-drag creates immediately, no long press; wheel scrolling is unaffected). Only starts on empty space: tiles and handles sit above the column and consume first.
- **Move** (`Modifier.scheduleMoveGesture` wrapping each timed tile's `eventContent` box): same long-press/slop split; lift = `HapticFeedbackType.LongPress`, source tile drawn at 0.35 alpha, ghost follows.
- **Resize** (handle zones, 2.4): `detectVerticalDragGestures`-style immediate drag (no long press) because the handle zone is small and explicit.
- **Disambiguation with scroll / pager / pinch** (the part QA should attack):
  1. Before the long press fires (touch), moving more than `touchSlop` cancels the gesture and the event is not consumed, so `verticalScroll` and the `HorizontalPager` take over exactly as today.
  2. After the long press fires, the layer consumes every `PointerInputChange` of that pointer; parents see consumed changes, so neither scroll nor pager moves. `PagedSchedule` additionally passes `userScrollEnabled = options.userScrollEnabled && controller.active == null` to the pager as a belt-and-braces guard.
  3. A **second pointer** going down (pinch-to-zoom starts in the Initial pass and consumes at ≥ 2 pointers) **cancels** the drag: ghost dismissed, no callback, haptics none.
  4. Handle drags start only inside the 32.dp handle zone (2.4), never on the whole tile edge, so scrolling with a finger resting on a tile border keeps working.
  5. Pointer cancel, focus loss, Escape key (desktop), the composable leaving composition, `events` changing the dragged event away: ghost dismissed, no callback (Escape = standard cancel).
- **Auto-scroll:** while a Move/Create/Resize pointer is within 32.dp of the visible top/bottom edge of the scroll viewport, `verticalScrollState.dispatchRawDelta` runs per frame (`withFrameNanos`) at 0 → 240.dp/s ramping with proximity (linear), stopping at `0..maxValue`. The pointer's grid-space y is recomputed each frame, so the ghost keeps tracking while the grid scrolls.

### 2.3 Quantisation, clamping and callback semantics

All pure, `internal` in `ScheduleEditing.kt` (commonMain), unit-tested without UI. Inputs are **minutes from midnight**; the visible window is `minMinute = state.minHour*60`, `maxMinute = state.maxHour*60`.

```kotlin
internal fun minuteAt(yPx: Float, hourPx: Float, window: HourWindow): Int       // unsnapped, clamped to [min, max]
internal fun snapNearest(minute: Int, snap: Int): Int                            // (minute + snap/2) / snap * snap
internal fun snapFloor(minute: Int, snap: Int): Int
internal fun snapCeil(minute: Int, snap: Int): Int

/** Create: anchor floors to the grid, the pointer rounds up; at least one snap step long. */
internal fun createRange(anchorMinute: Int, pointerMinute: Int, snap: Int, min: Int, max: Int): MinuteRange
/** Move: keeps the duration, shifts (never shrinks) to stay inside [min, max]. */
internal fun moveRange(start: Int, end: Int, deltaMinutes: Int, snap: Int, min: Int, max: Int): MinuteRange
/** Resize: the moved edge snaps; the range keeps at least max(snap, minEventDurationMinutes) minutes. */
internal fun resizeStart(start: Int, end: Int, pointerMinute: Int, snap: Int, minDuration: Int, min: Int): MinuteRange
internal fun resizeEnd(start: Int, end: Int, pointerMinute: Int, snap: Int, minDuration: Int, max: Int): MinuteRange
internal class MinuteRange(val start: Int, val end: Int)
```

Rules (each is a named unit test, 2.8):

1. **Create:** `start = snapFloor(anchor)`, `end = max(start + snap, snapCeil(pointer))`, both clamped into `[min, max]`; dragging above the anchor yields `start = snapFloor(pointer)`, `end = snapCeil(anchor)` (ghost grows upward). A long press without movement: `end = min(start + 60, max)` (`DefaultCreateDurationMinutes = 60`, internal const; open decision 5). With `snapMinutes = 60` the one-hour default is exactly one snap step.
2. **Move:** `newStart = snapNearest(start + delta)` where `delta` is the pointer's vertical travel since lift in minutes; duration is preserved (including a non-multiple of `snap`, e.g. 50 minutes stays 50); if `newStart < min` shift to `min`, if `newEnd > max` shift so `end == max`. The **date** is the column under the pointer (`clamp(columnIndex, 0, daysOnPage − 1)`; RTL maps x from the right edge). No cross-page move (open decision 7).
3. **Resize:** top handle moves `start` within `[min, end − minDuration]`, bottom handle moves `end` within `[start + minDuration, max]`. `minDuration = max(snapMinutes, minEventDurationMinutes)`. Resize never changes the date and never crosses midnight.
4. **Midnight:** `LocalTime` has no 24:00. A range ending at `maxMinute == 1440` is reported as `endTime = LocalTime(0, 0)`; KDoc on every callback says "**an `endTime` that is not after `startTime` means 00:00 of the following day**" (open decision 6; alternative `LocalTime(23, 59)` loses a minute and breaks snapping). The ghost label prints `24:00` (`clockLabel(1440, is24Hour)` already supports it).
5. **Which event is interactive:** only `EventSpan.Timed` with `start.date == end.date`. Clipped tiles (the event is cut by `minHour/maxHour` in the window) are move-only: their resize handle on the clipped side is not drawn, and a move uses the **real** start/end (`event.span`), not the clipped ones, so the duration is never silently shortened. Events outside the window are not on the grid and therefore not editable.
6. **Callbacks fire once, on release** (pointer up) with the final snapped values, and only when the result differs from the original (a lift-and-drop in place is a no-op). While dragging, nothing is called; the host's events are not touched, so the layout (`layoutDaySchedule`) does not re-run per pointer move.
   - `onEmptySlotLongPress(date, time)`: `time = snapFloor(pressMinute)`. If `onSlotCreate == null` it fires **at long-press recognition** (no ghost drag follows). If `onSlotCreate != null` and the pointer is released without moving beyond `touchSlop`, it fires **on release** (and `onSlotCreate` does **not** fire); if the pointer moved, `onSlotCreate(date, start, end)` fires instead. So exactly one of the two fires per gesture.
   - `onEventMove(event, newDate, newStart, newEnd)`: the event keeps its identity; the host builds `event.copy(span = EventSpan.Timed(newDate.atTime(newStart), endDate.atTime(newEnd)))` (for a recurring occurrence it receives the occurrence with `event.instance` set).
   - `onEventResize(event, newStart, newEnd)`: date implied by `event.span.startDate`.

### 2.4 Visual spec (for Rei; defaults, all themeable)

- **Resize handles** (shown when `onEventResize != null` and the tile is at least 36.dp high; shorter tiles get the **bottom** handle only; tiles below 20.dp get none and resize via keyboard/semantics): a 28.dp × 4.dp rounded pill (`tileCornerRadius` / 2) in the tile's content colour at 0.8 alpha, centred on the top and bottom edge, inset 3.dp **inside** the tile. The touch zone is 32.dp tall × 48.dp wide (`resizeHandleTouchSize`) centred on the pill, entirely inside the tile bounds (never on the neighbour tile). Pointer hover over a handle sets the vertical-resize cursor (`PointerIcon.Hand` fallback on platforms without a resize icon; `pointerHoverIcon` is `commonMain`). Handles fade in with the tile on desktop; on touch they are always visible when the callback is set (discoverability).
- **Drag ghost** (`DragGhost`): same size and shape as a tile at the snapped position; container = event/primary colour at 0.55 alpha composited over the surface; **`ghostBorderWidth` (2.dp) outline** in the accent colour; shadow elevation 4.dp (Move) / none (Create, Resize); content = the tile's title (Create: none) and the **time chip** — a `labelMedium` pill `"09:15 – 10:30"` in `inverseSurface` / `inverseOnSurface` floated 6.dp above the ghost (below it when the ghost is within 24.dp of the grid top). The source tile stays in place at 0.35 alpha during Move; during Resize the source tile is hidden (the ghost replaces it).
- **Snap feedback:** a 1.dp `primary` guide line across the target column(s) at the snapped start; `HapticFeedbackType.SegmentFrequentTick` whenever the snapped minute changes (when `hapticsEnabled`).
- **Out-of-bounds feedback:** when the pointer is above `minMinute` / below `maxMinute` the ghost stays clamped and the guide line turns `error` colour.
- **Create ghost default:** `primaryContainer` at 0.55 with the `primary` outline and the time chip only (no title).
- RTL: handles and chip are centred (no mirroring needed); the gutter and column order mirror as already implemented.
- **Dark theme:** the ghost uses the same opacity recipe as the tiles' opaque composited fill (#85), so contrast needs no special case; `readableContentColor` is reused for the chip text.

### 2.5 Keyboard and accessibility equivalents

Every gesture has a keyboard / assistive path, no pointer required.

- **Move/resize focused tile** (tiles are already focusable when `onEventClick != null`, with `eventFocusRing`; they become focusable when any editing callback is set): `Alt+↑ / Alt+↓` move by `snapMinutes`; `Alt+← / Alt+→` move to the previous/next day of the page (RTL swaps, via `horizontalArrowDayOffset`); `Alt+Shift+↑ / ↓` move the **end** edge by `snapMinutes`; `Alt+Ctrl+↑ / ↓` move the **start** edge. Each key press commits through the same clamping rules and fires the callback immediately (step-wise; the host's updated event then recomposes the tile under focus: the tile keeps focus because its `key(event.id)` is stable). `Escape` has no effect outside a pointer drag.
- **Create from the keyboard:** a `DayColumn` is focusable when `onEmptySlotLongPress` or `onSlotCreate` is set; focused, it shows a slot caret (a 2.dp `primary` line plus the create ghost at `snapMinutes` height); `↑ / ↓` move the caret by `snapMinutes` (auto-scrolling), `Enter / Space` fire `onEmptySlotLongPress(date, caretTime)` (or `onSlotCreate(date, caret, caret + 60)` when `onEmptySlotLongPress == null`), `Shift+↓` grows the ghost before `Enter`. `Tab` order stays columns then tiles in z-order.
- **TalkBack / assistive:** timed tiles get `semantics { customActions = … }` (labels in 2.7): "Move earlier", "Move later", "Previous day", "Next day" (Week only), "Extend end", "Shorten end", each by `snapMinutes` and each routed through the same commit path as the keys. Day columns expose "Create event at HH:MM" actions for the caret time (and an `onClick` semantics action when `onEmptySlotClick` is set, which is a small a11y improvement for the existing callback). During a pointer drag the time chip has `liveRegion = Polite` so screen-reader users with a pointer hear the snapped time.
- `CalendarEvent.title` remains the accessible name; the resize handles are `clearAndSetSemantics`-free decorations (not separate accessibility nodes) because the custom actions replace them.

### 2.6 Implementation notes

- `DayColumn` already builds its tile layout in a custom `Layout` and animates horizontal fractions; the handle pills are drawn inside the tile's `eventContent` box (an `overlay` `Modifier.drawWithContent` + pointer zones) so that custom `eventContent` gets them; the handle zones are two tiny `Box`es with `Modifier.pointerInput` only when `onEventResize != null` and the tile is tall enough.
- Tiles register `(eventId, date) → Rect` in the controller only when move is enabled (used by the layer to find the source tile's bounds for the ghost start).
- `PagedSchedule` already attaches `ScheduleState`; editing state is **not** part of `ScheduleState` nor the saver (transient; nothing to restore).
- Ghost layer is a single `Box` with `Modifier.offset`/`size` driven by `controller.active` (state read in layout, not composition, so a drag does not recompose the grid).
- `snapMinutes` also feeds `timeAt` for `onEmptySlotClick`? **No**: tap keeps flooring to the hour/half hour (existing, tested); only editing uses `snapMinutes`.
- Test seam for goldens: `internal val LocalScheduleDragOverride = compositionLocalOf<ScheduleDragPreview<*>?> { null }` read by the ghost layer; snapshot tests provide it so mid-drag frames are deterministic without injecting held pointers.

### 2.7 Strings

New `ScheduleStrings` / `OrreryString` entries, all six locales (EN, DE, ES, UK, ZH-CN, JA), prefixed `orrery_schedule_`: `move_earlier`, `move_later`, `move_previous_day`, `move_next_day`, `extend_end`, `shorten_end`, `create_at` (template `"Create event at %1$s"`, formatted by the caller, templates are raw as before), `drag_time_range` (`"%1$s – %2$s"` for the chip description). The picker needs none.

### 2.8 `:sample` showcase

- **TripsScreen:** a chip row "Picker layout: **Auto / Dual / Single**" (testTags `picker_layout_auto/dual/single`) and an `AssistChip("Wide preview")` (tag `trips_open_picker`) that opens `OrreryDateRangePickerDialog(layoutMode = chosen, presets = DatePickerDefaults.rangePresets())` seeded from the current stay (`rememberOrreryDateRangePickerState(initialStartDate, initialEndDate, minRangeDays/maxRangeDays/disallowDisabledInRange` from the Stay rules chip, `disabledDates = blackouts` so guardrails are visible across the two panes). "Save" calls `selection.set(Selection(rangeStart, rangeEnd))`. `PickersScreen`'s range card gets the same three-way layout chip.
- **AgendaScreen:** an "Edit" `FilterChip` in the Day/Week toolbar row (tag `agenda_edit`). The screen's immutable `events` map becomes derived: `base events + created − overrides` held in `mutableStateMapOf<String, CalendarEvent<…>>` and a `SnapshotStateList` for created events, recomputed through `eventsByDate`; with Edit on, `DaySchedule/WeekSchedule` get `onEmptySlotLongPress`/`onSlotCreate` (adds "New event" in a palette colour), `onEventMove`, `onEventResize` and a `SnackbarHostState` message "Moved <title> to Tue 14:30 – 15:15" with **Undo**. Working hours 7–22 remain, so the clamping is visible. The existing `Stacked overlap` and `Zoom` chips keep working during editing (zoom changes `hourPx` mid-session; the controller reads `hourPx` per frame).

---

## 3. Testing and Golden Strategy

### 3.1 Pure unit tests (`orrery-compose/src/commonTest`, `ScheduleEditingTest`)

snapNearest/Floor/Ceil for every `snapMinutes` in {5, 10, 15, 20, 30, 60} including exact multiples and ±1; `createRange` (downward, upward drag, zero-length long press → 60 min, `max` clamp near 24:00, `snap = 60`); `moveRange` (duration preserved, 50-minute duration, shift not shrink at both window edges, delta 0 → equal); `resizeStart/End` (min duration = `max(snap, minEventDurationMinutes)`, cannot cross the other edge, window clamp); midnight mapping (`1440 → LocalTime(0, 0)`); dual-month helpers: `dualMonthPageTarget(left, target)` table (visible, before, after, at range ends), `monthDayAt` in LTR/RTL for rows 4–6.

### 3.2 Compose UI tests (`androidHostTest`, Robolectric; style of `DragSelectTest` / `KeyboardNavigationTest` / `ScheduleViewPowerTest`)

**`DualMonthRangePickerTest`** (`@Config(qualifiers = "w900dp-h800dp")` for dual, `w411dp-h2400dp` for single):
- `Adaptive` renders two month titles at 900.dp and one column at 411.dp; `DualMonth` forced at 411.dp renders two; `SingleMonth` forced at 900.dp renders the old layout; custom `Adaptive(minDualMonthWidth = 1000.dp)` at 900.dp stays single; resizing live (`Config` change) keeps `selectedStartDate` and `displayedMonth`.
- Left pane = `displayedMonth` (July), right = August; ‹ / › shift both by one; ‹ disabled at `yearRange` start, › disabled with `M + 1 == end` (cannot reach an empty right pane); **Today** hidden while today's month is M or M + 1, shown otherwise; click scrolls so today's month is the left pane but **does not scroll** when it is the right pane.
- **Cross-month range:** click `Jul 28`, then `Aug 4` → `selectedStart/End`; guardrails: `minRangeDays = 3` ignores `Jul 31 → Aug 1`; `disallowDisabledInRange` with a disabled `Aug 2` restarts at the tapped date; `maxRangeDays`.
- **Hover preview:** `performMouseInput { moveTo(day) }` after the start click draws the preview (pixel test in the style of `FocusHoverTest.markerPixels` at cell centres in **both** months); exit clears it.
- **Cross-month drag:** long-press at `Jul 30`, move to `Aug 5` in the right pane, release → range set; drag to the right edge of the right pane auto-pages one month.
- **Keyboard:** focus `Jul 31`, `→` focuses `Aug 1` with **no** scroll (right pane visible); `Shift+→` from `Jul 31` extends the range into August; `←` from `Aug 1` returns to `Jul 31`; `↑` from the first row of the right pane lands in the left pane; forward off the right pane scrolls by exactly one month and focuses the target; backward off the left pane scrolls so the target is the left pane; PageDown ±1 month, Shift+PageDown ±1 year; **RTL:** `DirectionRight` = previous day, left pane is the right-hand one (`LocalLayoutDirection provides Rtl` plus bounds assertion `left pane x > right pane x`).
- **Mode switch:** single → dual → single keeps the visible month (guards the `itemsPerMonth` handling of 1.3: assert no frame with a wrong month via `displayedMonth` sampled after each `waitForIdle`).
- **Semantics:** two traversal groups with month descriptions; header `liveRegion`; `DateRangePickerDialogTest` existing tests remain green unchanged (single-month at 411.dp).
- **Binary compat:** `DateRangePickerBinaryCompatTest` reflects the old `OrreryDateRangePickerDialog` signatures.

**`ScheduleEditingTest`** (compose; `@Config` default phone size, mouse and touch injection):
- **Empty-slot long press:** touch long press at 10:20 with `snapMinutes = 15`, `onSlotCreate = null` → `onEmptySlotLongPress(date, 10:15)` once; with `onSlotCreate` and no move → `onEmptySlotLongPress` if set (else create 10:15–11:15), never both; long press then drag down 50.dp (at 60.dp/h ≈ 50 min) → `onSlotCreate(10:15, 11:15)`, `onEmptySlotLongPress` **not** called; drag upward; clamp at `minHour`/`maxHour` (working-hours window 8–18); mouse press-drag creates without the long press; **a normal swipe on empty space still scrolls and pages** (`swipeLeft` pages, vertical swipe scrolls, no callback).
- **Move:** long press tile, drag +90.dp → start 09:00 → 10:30, duration kept; drag to the next column in week mode → `newDate` next day; drop in place → no callback; clipped event (06:30–08:30 in a 8–18 window) moves by its real duration; multi-day and all-day events have no gesture; `Escape` mid-drag cancels; second finger (`pinch`) cancels without callback; `snapMinutes = 30` / `5`.
- **Resize:** drag bottom handle +30.dp → end +30 min; top handle; min duration; cannot cross; handle absent without `onEventResize` and on a 15-minute (15.dp) tile; handle zone does not steal a vertical scroll started on the tile body.
- **Auto-scroll:** drag near the viewport bottom → `verticalScrollState.value` increases and the ghost's `endTime` keeps growing.
- **Keyboard/semantics:** `Alt+↓` on a focused tile → `onEventMove` +15 min; `Alt+→` next day (RTL swapped); `Alt+Shift+↓` → `onEventResize` end +15; column caret + `Enter` → `onEmptySlotLongPress` (time asserted); the `customActions` exist with the localized labels and fire the same callbacks (`performCustomAction`).
- **Compat/regression:** `ScheduleViewTest`, `ScheduleViewPowerTest`, `ScheduleTileRenderingTest` pass unmodified; with no editing callbacks the semantics tree and node count equal the 0.9.2 tree (no handle nodes, no extra focusables); `ScheduleBinaryCompatTest`.

### 3.3 Roborazzi goldens (`androidHostTest`; Robolectric Native graphics; light/dark/RTL like the existing suites)

- **`DualMonthRangePickerSnapshotTest`** with a tablet configuration (`RobolectricDeviceQualifiers.MediumTablet`, or `w1024dp-h768dp-xhdpi` if the constant differs in the pinned Roborazzi): `dualMonthLight`, `dualMonthDark`, `dualMonthRtl` (`LocalLayoutDirection provides Rtl` via `CompositionLocalProvider`, like `MiniMonthSnapshotTest.miniMonthHighlightsRtl`), `dualMonthRangeAcrossMonthsLight` (Jul 28 → Aug 6), `dualMonthHoverPreviewLight` (start set + mouse hover), `dualMonthWithPresetsLight`, `dualMonthInputLight` (side-by-side fields), `dualMonthGuardrailsLight` (disabled dates + min range). A phone-size `Adaptive` shot `singleMonthPhoneLight` proves nothing regressed for the existing `rangePickerLight` (which must stay **unchanged**: the existing snapshot is Pixel5 = narrow, so `Adaptive` resolves single).
- **`ScheduleEditingSnapshotTest`** (Pixel 5 + week view on tablet): `resizeHandlesLight/Dark`, `createGhostLight/Dark`, `moveGhostWeekLight/Dark` (source tile at 0.35 alpha plus ghost in another column plus time chip), `resizeGhostLight`, `ghostOutOfBoundsLight` (error guide), `ghostRtl`, `keyboardCaretLight`. Mid-drag frames use `LocalScheduleDragOverride` (2.6); a separate real-gesture test asserts the same override is produced by the gesture (so the seam cannot drift).
- **Desktop:** Roborazzi stays Android-host (as everywhere in this repo); Desktop parity is covered by the `commonTest` pure tests and, optionally, a small `desktopTest` using `runComposeUiTest` for mouse drag creation and the hover preview (nice-to-have, not a merge gate).
- Re-recorded goldens: none expected; any diff in an existing golden is a bug. Rei signs off the new goldens before merge.

### 3.4 Binary compatibility (CI gate)

- Every PR: `./gradlew updateKotlinAbi` then `checkKotlinAbi` (CI already runs it) on Android, Desktop and KLib dumps; the diff must be **additive only**: new `OrreryDateRangePickerDialog` overload, `DateRangeCalendarLayoutMode` + members, `ScheduleDragKind/ScheduleDragPreview`, new `DaySchedule/WeekSchedule` overloads, `ScheduleStrings/ScheduleDimensions` constructor additions, `ScheduleDefaults.DragGhost`, `ScheduleDefaults.strings/dimensions` overloads; and the old signatures must remain in the dump as hidden (HIDDEN functions stay in the binary dump).
- `DateRangePickerBinaryCompatTest` and `ScheduleBinaryCompatTest` (reflection on the 0.9.2 shapes, 2.1).
- `CHANGELOG [Unreleased]`: one entry for the new `Adaptive` default (behaviour change on wide windows) and one for editing callbacks.

---

## 4. Compatibility Summary

| Area | Change | Compatibility |
|---|---|---|
| `OrreryDateRangePickerDialog` | +`layoutMode` (last, default `Adaptive()`) | HIDDEN bridge of the 0.9.2 signature; **visual change at ≥ 640.dp** (open decision 1) |
| `OrreryDateRangePickerState`, `rememberOrreryDateRangePickerState` | none | unchanged |
| `CalendarKeyboardNavigation` (internal) | +`dualMonth` | internal |
| `DaySchedule` / `WeekSchedule` | +4 callbacks, `snapMinutes`, `hapticsEnabled`, `ghostContent` | the four existing overloads become HIDDEN bridges |
| `ScheduleStrings`, `ScheduleDimensions`, `ScheduleDefaults.strings/dimensions` | appended fields | HIDDEN ctor/function bridges (English fallbacks in the hidden ctor) |
| New public types | `DateRangeCalendarLayoutMode`, `ScheduleDragKind`, `ScheduleDragPreview`, `ScheduleDefaults.DragGhost` | additive; non-data classes where growth is expected |
| `orrery-core` | none | unchanged |
| Strings | +9 `orrery_schedule_*` in 6 locales; none for the picker | resource-prefix rule respected |

## 5. Rollout Order (for Maya)

Three PRs, the first two independent; `updateKotlinAbi` in each:

1. **#88a Schedule editing:** `ScheduleEditing.kt` pure helpers + `ScheduleEditingTest` first commit; then the controller, create/move/resize gestures, handles, ghost, auto-scroll, keyboard and semantics; the HIDDEN-bridge rewrite of the four overloads; strings in 6 locales; `ScheduleEditingSnapshotTest` goldens.
2. **#88b Dual-month picker:** `useItemsPerMonth`, `monthDayAt` extraction (with `DragSelectTest` unchanged), `DualMonthRangeCalendar`, `DateRangeCalendarLayoutMode`, dialog sizing, keyboard `dualMonth`, tests, goldens.
3. **#88c Sample + docs:** `TripsScreen` / `PickersScreen` / `AgendaScreen` editing, README feature table and `docs/guide` page "Wide pickers and editable schedules", CHANGELOG, benchmark-free (no perf gate; Asuka confirms no frame hitching while dragging on a device).

## 6. Open Decisions for Andrii (with recommended defaults)

1. **Default layout mode:** *Recommended:* `Adaptive(640.dp)` (issue AC; call out in CHANGELOG). Alternative: default `SingleMonth` for zero visual change, apps opt in.
2. **No public `SchedulePager`:** *Recommended:* parameters on `DaySchedule`/`WeekSchedule` only (matches the v0.9.0 decision). Alternative: introduce a public `SchedulePager` wrapper (an extra permanent 1.0 API with no behaviour of its own).
3. **Two months only:** *Recommended:* dual, not N-up (three-up/yearly would be a `columns` parameter later; the sealed interface can grow). Alternative: `DateRangeCalendarLayoutMode.MultiMonth(count)` now.
4. **`layoutMode` on the dialog, not the state:** *Recommended:* dialog parameter (depends on the window; keeps the state ABI untouched). Alternative: a `state.layoutMode` property for hosts that embed the picker body without the dialog (no public body composable exists yet; defer).
5. **Default created-event length:** *Recommended:* 60 min for a long-press without drag (internal constant). Alternative: one `snapMinutes` step, or a `ScheduleDimensions`/parameter value.
6. **24:00 representation:** *Recommended:* `endTime = 00:00` meaning next midnight, documented on each callback. Alternative: pass a `LocalDateTime` end, or cap interactive ends at 23:45 (loses the last slot).
7. **Moves across pages:** *Recommended:* not in 1.0 (visible page only; the pager is frozen during a drag). Alternative: edge auto-flip by hovering the left/right 32.dp for 600 ms (needs a pager-aware auto-scroll and a ghost that survives a page recomposition).
8. **Dual card visual details (Rei):** corner radius, divider, title weight, whether the ghost uses a dashed outline (`PathEffect.dashPathEffect`, costlier to snapshot) or solid.
9. **Today button when today is in the right pane:** *Recommended:* hidden (already visible). Alternative: always shown unless today is in the **left** pane.
10. **Immediate per-key commit for keyboard edits:** *Recommended:* yes (stateless schedule; each step is one callback). Alternative: a keyboard "lift mode" (Space to pick up, arrows to move, Enter to drop) with the ghost; heavier, more screen-reader friendly. Flag if Asuka's a11y audit prefers it.
11. **Haptics parameter:** *Recommended:* `hapticsEnabled = true` on the schedule, as on the calendars. Alternative: reuse a global setting if one is added before 1.0.
