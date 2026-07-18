# Infinite Paging (0.5.0) — Design

**Date:** 2026-07-18
**Status:** Approved
**Sub-project:** 4 of 6 (feature roadmap: foundations → dialogs → heatmap → **paging** → providers → schedule view)

## Context

Every Orrery state holder requires a finite date range; `rememberCalendarState`
defaults to 1971–2055. This sub-project makes `CalendarState` and
`WeekCalendarState` unbounded by default, so calendars scroll indefinitely in
either direction. All changes land in `orrery-compose`; no orrery-core changes.
Ships as **0.5.0**.

Decisions made during brainstorming:

- **Scope: `CalendarState` + `WeekCalendarState` only.** `YearCalendarState` and
  `HeatmapCalendarState` keep explicit ranges (year overviews and contribution
  graphs are inherently bounded views; heatmap summaries are undefined over an
  unbounded range).
- **Nullable bounds, null defaults.** `startMonth`/`endMonth` (and
  `startDate`/`endDate`) become `null`able; null = unbounded on that side;
  defaults change from fixed ranges to null. One-sided openness supported.
- **Mechanism: huge effective window (approach A).** Null bounds map internally
  to kotlinx-datetime's supported extremes. Rejected: epoch-anchored
  `Int.MAX_VALUE` indexing (re-derives all index math for no observable gain)
  and sliding-window `updateRange` (fragile re-anchoring, racy restoration).

Out of scope: unbounded `YearCalendar`/`HeatmapCalendar`, changes to
`MonthYearPicker`'s required finite `range`, orrery-core API changes.

## 1. Public API changes

**Breaking (pre-1.0, called out in changelog):** four properties become
nullable; two required parameters gain null defaults; the default month range
changes from 1971–2055 to unbounded (a strict superset).

```kotlin
// CalendarState
public var startMonth: YearMonth?   // null = unbounded past
    private set
public var endMonth: YearMonth?     // null = unbounded future
    private set
public fun updateRange(startMonth: YearMonth?, endMonth: YearMonth?)
    // require(start <= end) only when both non-null

@Composable
public fun rememberCalendarState(
    startMonth: YearMonth? = null,
    endMonth: YearMonth? = null,
    firstVisibleMonth: YearMonth = currentYearMonth(),
    firstDayOfWeek: DayOfWeek = firstDayOfWeekFromLocale(),
    outDateStyle: OutDateStyle = OutDateStyle.EndOfRow,
): CalendarState

// WeekCalendarState
public var startDate: LocalDate?    // null = unbounded past
    private set
public var endDate: LocalDate?      // null = unbounded future
    private set

@Composable
public fun rememberWeekCalendarState(
    startDate: LocalDate? = null,   // was a required parameter
    endDate: LocalDate? = null,     // was a required parameter
    firstVisibleDate: LocalDate = currentDate(),
    firstDayOfWeek: DayOfWeek = firstDayOfWeekFromLocale(),
): WeekCalendarState
```

`monthCount` / `weekCount` remain non-null `Int`, computed over the effective
window (§2); KDoc states this explicitly.

## 2. Effective window

Private computed properties supply the working bounds:

```kotlin
private val effectiveStartMonth: YearMonth get() = startMonth ?: YearMonth(-19999, 1)
private val effectiveEndMonth: YearMonth get() = endMonth ?: YearMonth(19999, 12)
// WeekCalendarState: LocalDate(-19999, 1, 1) / LocalDate(19999, 12, 31)
```

Every existing computation (`monthCount`, `indexOf`, `firstVisibleMonth`,
`itemsPerMonth` handling, keyboard-navigation edge paging) routes through the
effective bounds and stays structurally unchanged. ~480k month items /
~2.1M week items are plain `Int` indices, far below overflow, and `LazyList`
composes items lazily so item count is free.

## 3. Far-jump scrolling

`animateScrollToMonth` (and `animateScrollToDate` on both states): when the
target is more than 12 months (52 weeks for `WeekCalendarState`) from
`firstVisibleMonth`/`firstVisibleWeek`, first `scrollToItem` to 2 items short
of the target on the near side, then `animateScrollToItem` the rest —
constant-time long jumps with an animated landing. Targets within the
threshold animate as today. `scrollToMonth`/`scrollToDate` are unchanged
(already constant-time). `animateScrollToToday` inherits the fix for free.

## 4. Chrome interplay

- `CalendarNavHeader`: chevron enablement compares against the nullable
  bounds — a null side never disables. (Current code compares
  `state.firstVisibleMonth` to `state.startMonth`/`endMonth`; the comparison
  becomes `bound == null || ...`.)
- `MonthYearPicker`: unchanged; KDoc gains a sentence recommending a finite
  window (e.g. ±100 years around `currentYearMonth()`) when hosting it over an
  unbounded calendar.
- Keyboard/D-pad month-edge paging: works off effective bounds; unreachable in
  practice on open sides.

## 5. Saver and restoration

Both `Saver`s store the nullable bounds as `String?` list entries (listSaver
supports nulls); restore parses null back to null. Mixed one-sided bounds
round-trip. `HeatmapCalendarState`, `YearCalendarState`,
`CollapsibleCalendarState` untouched.

## 6. Testing

House conventions (fixed dates July 2026; module-scoped Gradle; no new
snapshots — no visual change, `verifyRoborazziDebug` guards regressions):

- **`CalendarStateTest` additions** (or new `InfinitePagingTest`):
  - default state is unbounded: `startMonth == null`, `endMonth == null`,
    renders current month in `HorizontalCalendar`
  - `scrollToMonth(YearMonth(2526, 7))` lands exactly (`firstVisibleMonth`),
    and back to `YearMonth(1526, 7)`
  - one-sided: `startMonth = YearMonth(2026, 1), endMonth = null` —
    `scrollToMonth(YearMonth(2020, 1))` clamps to 2026-01
  - `updateRange(null, null)` keeps the visible month
  - restoration round-trips null, non-null, and mixed bounds
- **Far jump:** `animateScrollToMonth` to +100 years completes within the test
  clock and lands exactly; same for `WeekCalendarState.animateScrollToDate`.
- **`WeekCalendarStateTest` additions:** unbounded default renders current
  week; scroll 500 years out and back; one-sided clamp.
- **NavHeader:** chevrons enabled on open sides; disabled exactly at a
  non-null bound.

## 7. Sample app

AgendaScreen already calls `rememberCalendarState()` — it becomes unbounded
with zero diff (that is the demo). TripsScreen keeps its explicit bounds,
demonstrating the bounded form. No sample code changes required; verify by
building.

## 8. Release

0.5.0: CHANGELOG entry under a fresh Unreleased (Added: unbounded paging;
Changed/Breaking: nullable bounds properties, null defaults,
`rememberWeekCalendarState` parameters now optional), README update (Features
bullet + "Calendar state and navigation" subsection notes null bounds and the
far-jump behavior), full CI verification, release commit pair after user
confirmation. No core ABI change (`checkKotlinAbi` unaffected). No
tagging/pushing/publishing by Claude.
