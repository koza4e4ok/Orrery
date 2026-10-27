# Week/Month Selection Mode and Surface Extensions (#25)

**Issue:** [#25 — Extras: Glance widget, Wear OS month, week/month selection](https://github.com/koza4e4ok/Orrery/issues/25)  
**Date:** 2026-10-27  
**Status:** Proposed (Phase 4 Backlog)

---

## 1. Goals & Prioritization

With Kotlin/Compose Multiplatform (#20), event agendas (#22), and day/week schedule views (#21) merged in 0.6.0, Issue #25 covers three surface and selection extensions:

1. **Whole-week and whole-month selection modes (`orrery-core` + `orrery-compose`)** — **Priority 1 (Multiplatform):** Works across all KMP/CMP targets (Android, Desktop JVM, iOS, Web/Wasm), requires zero new dependencies, and builds directly on `SelectionEngine` and `CalendarSelectionState`.
2. **Compact Wear OS month composable (`orrery-wear`, optional Android artifact)** — **Priority 2:** Round/square watch-screen month view reusing `orrery-core` `monthGrid` and `DayInfoProvider`.
3. **Home-screen Glance widget (`orrery-glance`, optional Android artifact)** — **Priority 3:** RemoteViews/Glance month widget snapshot backed by `orrery-core`.

---

## 2. Whole-Week & Whole-Month Selection (`orrery-core` & `orrery-compose`)

### 2.1 `SelectionMode` additions in `orrery-core`

Extend `SelectionMode` (or provide dedicated selection helpers in `SelectionEngine`) so tapping any enabled day in a week or month selects the entire contiguous period:

```kotlin
public enum class SelectionMode {
    None,
    Single,
    Multiple,
    Range,
    /** Tapping any day selects the full 7-day week (bounded by firstDayOfWeek) as a Range. */
    Week,
    /** Tapping any day selects the full YearMonth (firstDay..lastDay) as a Range. */
    Month,
}
```

- **Representation:** `SelectionMode.Week` and `SelectionMode.Month` produce a `CalendarSelection.Range(start, end)` so existing range rendering (continuous rounded range band, `isRangeStart`, `isRangeEnd`, `isInRange`, TalkBack range announcements) works automatically without duplicating UI state types.
- **Disabled dates & bounds:**
  - In `SelectionMode.Week`, `SelectionEngine.onDayClick(date, firstDayOfWeek)` computes the 7-day week `weekStart..weekEnd` containing `date`, clamped to `minDate..maxDate`. If `disabled.containsAnyInRange(weekStart, weekEnd)` is true (or `date` itself is disabled), the click is rejected.
  - In `SelectionMode.Month`, `SelectionEngine.onDayClick(date)` computes `YearMonth.from(date).let { it.firstDay..it.lastDay }`, clamped to `minDate..maxDate`, rejecting if any date in the range is disabled.
- **Toggling:** Tapping any day inside the already-selected week/month clears the selection when `allowClear = true`.

### 2.2 Compose integration (`orrery-compose`)

- `rememberCalendarSelectionState(mode = SelectionMode.Week, firstDayOfWeek = ...)` passes `firstDayOfWeek` into `SelectionEngine` so week boundaries match the visible `HorizontalCalendar` / `VerticalCalendar` / `WeekCalendar` grid.
- Drag-selection is disabled when `mode == SelectionMode.Week || mode == SelectionMode.Month` (single tap selects the full row/month).

---

## 3. Wear OS & Glance Scoping (Android-only Companion Modules)

Because `orrery-compose` is now a Compose Multiplatform library targeting Android, Desktop, iOS, and Web (`wasmJs`), Android-specific Jetpack dependencies (`androidx.wear.compose:*` and `androidx.glance:*`) **must not** be added to `orrery-compose`.

- **`orrery-wear` (future optional module):**
  - Depends on `orrery-core` and standard Compose foundation/runtime for Android (`minSdk = 26`).
  - Provides `WearMonthCalendar(month: YearMonth, ...)` optimized for circular and rectangular watch viewports (`TimeText`-safe top inset, rotary scroll between months, compact 24dp day cells).
- **`orrery-glance` (future optional module):**
  - Depends on `orrery-core` and `androidx.glance:glance-appwidget`.
  - Renders a static 6-row month grid via Glance `Row`/`Column`/`Text` primitives (since Glance uses `RemoteViews` rather than `ui.DrawScope`), highlighting today and deep-linking tapped dates via `ActionParameters`.

---

## 4. Rollout Plan

1. **PR 1 (v0.7.0):** `SelectionMode.Week` and `SelectionMode.Month` in `orrery-core` + `orrery-compose` with unit tests, Compose UI tests, and Roborazzi snapshots.
2. **PR 2+ (optional Android surface modules):** Evaluate demand before introducing extra Maven artifacts (`orrery-wear`, `orrery-glance`).
