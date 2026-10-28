# v0.8.0 UI Focus, Polish, Density & Styles — Design

**Date:** 2026-10-28
**Status:** Approved (v0.8.0 scope including universal CalendarView-inspired :sample showcase)
**Issues:** #61, #62, #63, #64, #65, #66, #67 (Tracking roadmap: #68)
**Owners:** Ritsuko (architecture), Rei (visual design), Maya (implementation), Asuka (QA review)

## Context

With `v0.7.0` published across Android, Desktop JVM, iOS (`iosArm64`, `iosSimulatorArm64`), and Web (`wasmJs`), `v0.8.0` focuses exclusively on UI quality, visual polish, keyboard/D-pad focus navigation, and turnkey style/density presets inside `orrery-compose` and `:sample` (no new Maven modules).

---

## 1. Phase 1: UI Focus & Keyboard Navigation (#61, #62)

### 1.1 Visible Focus Rings & Pointer Hover (#61 — implemented in PR #70)
- `CalendarDayColors` adds:
  - `focusedBorderColor: Color = todayIndicatorColor`
  - `hoverContainerColor: Color = contentColor.copy(alpha = 0.08f)`
- `CalendarDayShapes` adds:
  - `focusedBorderWidth: Dp = 2.dp`
- Binary compatibility with `0.7.0` is preserved via `@Deprecated(level = DeprecationLevel.HIDDEN)` constructors and `CalendarDefaults.dayColors` / `dayShapes` bridges.
- `DefaultDay` observes `MutableInteractionSource.collectIsFocusedAsState()` and `collectIsHoveredAsState()`:
  - Hovering an available, unselected cell draws `hoverContainerColor` inside `dayShape`.
  - Focusing an available cell draws a `focusedBorderWidth` stroke in `focusedBorderColor` in the unclipped overlay.
- Interactive `HeatmapCalendar` tiles (`onDayClick != null`) and `YearCalendar` mini-month cells also render a `2.dp` focus ring when keyboard-focused.

### 1.2 Full Keyboard Navigation & Dialog Initial Focus (#62)

#### Month & Week Grid Shortcuts (`MonthContent`, `HorizontalCalendar`, `VerticalCalendar`, `WeekCalendar`)
| Key | Condition | Action |
|-----|-----------|--------|
| `DirectionLeft` / `DirectionRight` (flipped in RTL) | At row edge (`col == 0` / `col == 6`) | Page to previous/next month (or week in `WeekCalendar`) and land focus on `date ± 1 day` (already supported for month; extend to `WeekCalendar`) |
| `DirectionUp` / `DirectionDown` | At top row (`row == 0`) / bottom row (`row == weeks.lastIndex`) | Page to the target month containing `date ± 7 days` and land focus on `date ± 7 days` |
| `PageUp` / `PageDown` | Any focused day cell | Page 1 month backward/forward (`date.minus/plus(1, DateTimeUnit.MONTH)`, clamped to month length) and land focus on that date |
| `Shift + PageUp` / `Shift + PageDown` | Any focused day cell | Page 1 year backward/forward (`date.minus/plus(1, DateTimeUnit.YEAR)`) and land focus on that date |
| `MoveHome` / `MoveEnd` | Any focused day cell | Move focus to the first (`col = 0`) or last (`col = 6`) day of the current week row |
| `Shift + Direction*` | `selectionState != null` with `SelectionMode.Range` | Move focus to the target day and extend/anchor the active range selection from `rangeStart` (or current day) to the newly focused date |

#### Dialog Initial Focus & Mode Toggles
- **`OrreryDatePickerDialog` / `OrreryDateRangePickerDialog` / `OrreryDateTimePickerDialog`:**
  - When opened in `DatePickerDisplayMode.Picker` (or toggled from `Input` to `Picker`), seed `keyboardNavigation.pendingFocus` with `selectedDate ?: today` (clamped to the visible month) so the first arrow key press starts from the active date.
  - When switched to `DatePickerDisplayMode.Input`, request focus on the primary `DateInputField` (or start-date field in `OrreryDateRangePickerDialog`).
- **`MonthYearPicker` & `OrreryMonthPickerDialog`:**
  - `Escape` / Back when drilled into the month grid or decade grid returns one level up before dismissing.
- **`DaySchedule` / `WeekSchedule` / `Agenda`:**
  - Event blocks/chips when `onEventClick != null` are focusable via Tab/D-pad and draw a `2.dp` focus outline.

---

## 2. Phase 2: UI Polishing for Existing Components (#63, #64, #65)

### 2.1 `DefaultDay` Label Fitting, Decorator Contrast & Range Band Fades (#63 — implemented in PR #69)
- Secondary `info?.label` renders on a single centered line (`maxLines = 1`, `overflow = TextOverflow.Ellipsis`, `2.dp` horizontal padding) so long holiday names never wrap or shift the day number vertically.
- `RangeBand.TowardStart` and `RangeBand.TowardEnd` animate their alpha independently (`200 ms`), matching `RangeBand.Full`.
- `DayDecorators.eventDots` switches to `selectedContentColor` (or custom `selectedColor` overload) when drawn on a selected cell, and `DayDecorators.badge` draws a `1.dp` `selectedOutlineColor` ring on selected days.

### 2.2 `CalendarNavHeader` "Today" Action & Richer `YearCalendar` `MiniMonth` (#64)
- **`CalendarNavHeader`:**
  - Add optional `showTodayButton: Boolean = false`, `today: YearMonth = currentYearMonth()`, and `todayLabel: String = orreryString(OrreryString.Today)` (localized in EN, DE, ES, UK, ZH-CN, JA; hidden binary bridge for the 6-parameter overload).
  - When `showTodayButton && state.firstVisibleMonth != today`, show a compact `TextButton` / `AssistChip` next to the navigation chevrons that smoothly animates back to `today`.
- **`CalendarDefaults.MiniMonth` (`YearCalendar`):**
  - Add optional `selection: Selection? = null` and `dayInfo: ((LocalDate) -> DayInfo?)? = null` (with hidden binary bridge for existing callers).
  - Render `today` with a filled primary circle and `onPrimary` text; render selected dates/ranges with a subtle `primaryContainer` pill; color holiday day numbers (`dayInfo(date)?.isHoliday == true`) with `MaterialTheme.colorScheme.primary`.

### 2.3 Responsive Picker Dialogs & Animated Date/Time Step Transition (#65)
- **Compact-height responsiveness:** wrap picker dialog calendar/time content in a flexible height container with vertical scroll fallback when viewport height is under `480.dp` (e.g., landscape phones), keeping the confirm/dismiss button row pinned at the bottom.
- **`OrreryDateTimePickerDialog` step animation & active pill:**
  - Wrap the `DateTimePickerStep.Date` / `Time` body switch in `AnimatedContent` (horizontal slide + fade).
  - Highlight the active date or time chip in the headline with a subtle `secondaryContainer` pill so the user can see which step is active and tap to switch.
- **`DaySchedule` / `WeekSchedule` event text contrast:** automatically pick dark/light text on custom `event.colorArgb` backgrounds based on relative luminance when the caller does not override content color.

---

## 3. Phase 3: New UI Styles & Features (#66, #67)

### 3.1 `CalendarDensity` & Curated `CalendarStyle` Presets (#66)

```kotlin
public enum class CalendarDensity {
    /** 36.dp min cell height, compact header padding, secondary labels hidden by default. */
    Compact,
    /** 48.dp min cell height (current default), secondary labels shown. */
    Comfortable,
    /** 56.dp min cell height, roomy padding for tablet/desktop views with secondary labels + dots. */
    Expanded,
}

@Immutable
public class CalendarStyle(
    public val colors: CalendarDayColors,
    public val shapes: CalendarDayShapes,
    public val density: CalendarDensity = CalendarDensity.Comfortable,
    public val cellBorderWidth: Dp = 0.dp,
    public val cellBorderColor: Color = Color.Transparent,
    public val selectedElevation: Dp = 0.dp,
)
```

- **Preset factories on `CalendarDefaults`:**
  - `CalendarDefaults.style(density = Comfortable)` — default Material 3 circle selection + continuous pill range band.
  - `CalendarDefaults.roundedSquareStyle(cornerRadius = 10.dp, density = Comfortable)` — rounded-square day/selection shapes with continuous range band.
  - `CalendarDefaults.segmentedRangeStyle(cornerRadius = 8.dp, density = Comfortable)` — per-day rounded-square segmented fills for in-range days (`inRangeShape = RoundedCornerShape(8.dp)`).
  - `CalendarDefaults.borderedCardStyle(cornerRadius = 8.dp, density = Expanded)` — subtle `1.dp` `outlineVariant` cell border and `2.dp` selected elevation for dashboard/desktop grids.
- **Ergonomic `DefaultDay` overload:** add an overload accepting `style: CalendarStyle` (forwarding `colors`, `shapes`, `density`, `selectedElevation`, and optional cell border).

### 3.2 Multi-Day Spanning Event Bars, Range Preview & `:sample` Studio (#67)

- **`DayDecorators.spanningEventBars`:**
  ```kotlin
  @Composable
  public fun spanningEventBars(
      eventsByDate: (LocalDate) -> List<CalendarEvent<*>>,
      maxBars: Int = 2,
      barHeight: Dp = 4.dp,
      barSpacing: Dp = 2.dp,
      defaultColor: Color = MaterialTheme.colorScheme.primary,
  ): DayDecorator
  ```
  - For each day cell, inspects multi-day (or all-day) events active on `day.date`.
  - If an event continues from `day.date.minus(1, DAY)` (and `col > 0`), the bar extends flush to the left cell edge (`x = 0`); otherwise it starts inset with a rounded start cap.
  - If an event continues to `day.date.plus(1, DAY)` (and `col < 6`), the bar extends flush to the right cell edge (`x = size.width`); otherwise it ends inset with a rounded end cap.
  - Assigns stable vertical slot indices by sorting events by `(startDate, id)` so a multi-day event stays on the same horizontal track across days.
- **`DayDecorators.rangePreview`:**
  ```kotlin
  @Composable
  public fun rangePreview(
      selectionState: CalendarSelectionState,
      hoveredOrFocusedDate: LocalDate?,
      color: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
  ): DayDecorator
  ```
  - When `selectionState.selection` has a `rangeStart` with `rangeEnd == null` and `hoveredOrFocusedDate != null`, draws a translucent preview connector band between `minOf(rangeStart, hoveredOrFocusedDate)` and `maxOf(rangeStart, hoveredOrFocusedDate)`.
- **`:sample` UI Style & Focus Studio:**
  - Add a **Studio** control bar / bottom sheet in `:sample` allowing live switching between `CalendarStyle` presets (`MaterialCircle`, `RoundedSquare`, `SegmentedRange`, `BorderedCard`), `CalendarDensity` (`Compact`, `Comfortable`, `Expanded`), `TodayIndicator` (`Ring`, `FilledCircle`, `Underline`), and spanning event bars vs. dots.

---

## 4. Open Decisions for Andrii (with recommended defaults)

1. **Focused + Selected ring contrast (#61 follow-up):** When a day cell is both selected (filled with `primary`) and keyboard-focused, a `primary` focus ring blends with the fill.
   - *Recommended default:* Default `focusedBorderColor` on selected cells to `onPrimary` (or draw a `1.dp` inner/outer contrasting halo) so focused-and-selected cells are unmistakable.
2. **`CalendarStyle` preset set (#66):** Ship the four presets above (`style`/MaterialCircle, `roundedSquareStyle`, `segmentedRangeStyle`, `borderedCardStyle`) plus `CalendarDensity` (`Compact`, `Comfortable`, `Expanded`)?
   - *Recommended default:* Yes, ship those four presets and three densities in `orrery-compose`.
3. **Multi-day spanning bars placement (#67):** Implement `DayDecorators.spanningEventBars` as a composable `DayDecorator` (zero layout overhead, works in `HorizontalCalendar`, `VerticalCalendar`, and `WeekCalendar`)?
   - *Recommended default:* Yes, implement as `DayDecorators.spanningEventBars`.


---

## 5. Universal Modern `:sample` App Redesign (Inspired by `CalendarView` Assets — #67)

Based on the `huanghaibin-dev/CalendarView` showcase assets (`color_expand.png`, `custom_expand.png`, `meizu_expand.png`, `progress_expand.png`, `solar_expand.png`, `full_calendar.png`, `index_expand.png`, `range.png`, `year_view.png`, `main_en_func.png`, `main_en_list.png`):

1. **Universal Hero Date Header Bar:**
   - Displays the selected date in bold display typography (e.g. `Oct 28`), with a two-line subtitle next to it showing the year (`2026`) and the active almanac/holiday label (Lunar, Hijri, Persian, or US/GB/DE/UA public holiday).
   - Trailing action icons:
     - **Today calendar badge button** showing today's day-of-month number inside a calendar frame icon (one tap scrolls any active view back to `today`).
     - **Year Overview button** toggling an in-place `YearCalendar` 12-month grid with highlighted today, selected dates, and holidays; tapping any month drills smoothly into that month.
     - **Style & Dynamic Controls button** opening the interactive theme/function modal bottom sheet.
2. **Curated Visual Theme Presets (live-switchable in `:sample`):**
   - **Material 3 Default:** clean circle selection and continuous range pill.
   - **Meizu / Almanac Badges (`custom_expand` / `meizu_expand`):** soft drop shadow on the selected circle, top-right corner badge (`DayDecorators.badge` for holiday/workday/note tags), and bottom event dot.
   - **Colorful Glow (`color_expand`):** per-day colored circle fills with white day + subtitle text.
   - **Solar / Emerald Progress (`solar_expand` / `progress_expand`):** full-bleed emerald header or light surface with circular `DayDecorators.progressRing` arcs around active dates.
   - **Stacked Bars / Index (`full_calendar` / `index_expand`):** `DayDecorators.stackedBars` / `spanningEventBars` below the secondary label.
   - **Rounded Square & Bordered Card Grid:** showcasing the new `CalendarDefaults.roundedSquareStyle` and `borderedCardStyle` presets.
3. **Dynamic Controls Sheet (`main_en_list` / `main_en_func`):**
   - **Expand / Collapse** month ↔ week (`CollapsibleCalendarScaffold`), **Scroll to Previous / Next / Current**, **First day of week** (`Mon` / `Sun` / `Sat`), **Out-dates mode** (`All`, `Only current`, `Fixed 6 weeks`), **Week numbers**, **Selection mode** (`Single`, `Multiple`, `Range`, `Week`, `Month`), **Density** (`Compact`, `Comfortable`, `Expanded`), and **Almanac & Holiday country** (`Lunar`, `Hijri`, `Persian` + `None`/`US`/`GB`/`DE`/`UA`).
