# v1.0.0 Phase 1: RFC 5545 Recurrence Engine and Reactive Viewport Loading — Design

**Date:** 2026-11-09
**Status:** Draft for review (Andrii); implementation by Maya, QA by Asuka
**Issues:** #86, #87 (tracking roadmap: #91)
**Base:** main at v0.9.2 (be5b1d5)
**Owners:** Ritsuko (architecture), Maya (implementation), Asuka (QA review)

## Context

Orrery has a minimal event model (`CalendarEvent`, `EventSpan`, `eventsByDate`, ADR 0003) but no recurrence, no way for host apps to learn which dates are on screen (needed for lazy DB/network loading), and a `YearCalendar` whose mini-months compose about 45 nodes each. Phase 1 closes all three before the 1.0.0 API lock (#89).

Ground rules (same as earlier specs):

- Pure date and recurrence math lives in `orrery-core` (package `me.kozakov.orrery.core`), uses only `kotlinx-datetime` (no `java.time`, no `String.format`, no regex-only-on-JVM APIs) and is unit-tested without UI. Flows, state and drawing live in `orrery-compose`. No new Maven modules.
- Binary compatibility with 0.9.2 is kept: new parameters are appended with defaults and the previous signature stays as a `@Deprecated(level = HIDDEN)` bridge (pattern of `DayInfo` and `DayInfoBinaryCompatTest`). Every implementation PR runs `apiDump`/`apiCheck` and updates `orrery-core/api/orrery-core.api`, `orrery-core/api/orrery-core.klib.api` and the three `orrery-compose/api/*` dumps.
- Iterate dates via epoch days (the "Bolt" pattern in `MonthGrid.kt`) instead of `LocalDate.plus(1, DAY)`.
- Gradle runs only in CI (no JDK on the agent host); the PRs must show green CI.

### Existing solutions check

- **RFC 5545 engines:** ical4j (JVM only, heavy, `java.time`), `kotlin-rrule`/`rfc5545-kt`-style ports (small, unmaintained or JVM-bound, return `Instant`/`ZonedDateTime`). None is KMP + `kotlinx-datetime` + date-granular with the strict skip-invalid contract below, and a new dependency on the core module (which is dependency-free apart from `kotlinx-datetime`) is not worth a ~600-line algorithm we can verify against `python-dateutil` fixtures. **Decision: implement in-tree**, use dateutil as the conformance oracle in tests (same approach as the holidays fixtures).
- **Viewport flows:** kizitonwose/Calendar exposes `firstVisibleMonth` only; Orrery already has `visibleMonths()`/`visibleWeeks()`. Nothing to adopt; the new flows are thin layers on the existing `listState`.
- **Canvas mini-months:** no library offers this; Compose `TextMeasurer` + `drawText` is the supported primitive.

**Deliberately NOT building in Phase 1:**

- `RDATE`, `EXRULE`, `BYYEARDAY`, `BYWEEKNO`, `BYEASTER`, `BYHOUR/BYMINUTE/BYSECOND`, `FREQ=HOURLY/MINUTELY/SECONDLY`. The parser rejects them with a clear error (section 1.5). Orrery events are date-granular.
- Time zones. Timed events are floating `LocalDateTime` (like the rest of the model); there is no DST handling because there is no zone.
- Editing recurring series ("this and following", per-instance overrides / `RECURRENCE-ID` exceptions). Callers supply overrides as separate non-recurring events and an `exDate`.
- iCalendar file (`.ics`) parsing. Only the `RRULE` value and an `EXDATE` value list.
- A new `VerticalYearCalendar` composable (open decision 12).
- Per-day semantics nodes in the Canvas mini-month (open decision 11).
- Debouncing inside the viewport flows (callers use `.debounce()`).

---

## 1. #86 RFC 5545 Recurrence Engine (`orrery-core`)

### 1.1 Contract summary (the points that must not regress)

1. **Strict RFC 5545 §3.3.10 "skip invalid" is the default and only RFC behaviour.** Invalid generated dates (`31 April`, `30 February`, `29 February` in a non-leap year) are *skipped* and never consume `COUNT`. `DTSTART=2026-01-31; FREQ=MONTHLY;COUNT=3` yields `2026-01-31, 2026-03-31, 2026-05-31`. "Last day of every month" is spelled `BYMONTHDAY=-1`.
2. **Window queries clip the true series.** `COUNT` is always counted from `DTSTART`; the query window never resets it.
3. **EXDATE subtracts after generation.** RRULE generates the candidate set bounded by `COUNT`/`UNTIL`; `exDates` then remove entries. No refill.
4. **Multi-day events keep their duration** and a window query returns every occurrence whose span overlaps the window.
5. **DTSTART is always the first instance and counts toward COUNT** (RFC 5545 §3.3.10, COUNT paragraph), even when it does not match the rule's pattern.

### 1.2 Model

```kotlin
package me.kozakov.orrery.core

public enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

/** A weekday with an optional ordinal: MO, 2MO, -1FR. ordinal == null means "every". */
public data class ByDay(
    public val dayOfWeek: DayOfWeek,
    public val ordinal: Int? = null,   // null or in -53..-1 / 1..53; 0 is rejected
)

public class RecurrenceRule(
    public val frequency: Frequency,
    public val interval: Int = 1,                       // >= 1
    public val byDay: Set<ByDay> = emptySet(),
    public val byMonthDay: Set<Int> = emptySet(),       // 1..31 and -31..-1
    public val byMonth: Set<Int> = emptySet(),          // 1..12
    public val bySetPos: Set<Int> = emptySet(),         // -366..-1, 1..366
    public val count: Int? = null,                      // >= 1
    public val until: LocalDate? = null,                // inclusive, on the occurrence start date
    public val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    public val exDates: Set<LocalDate> = emptySet(),
) {
    // init validation: see 1.3. Hand-written equals/hashCode/toString (set-based) and:
    public fun copy(frequency: Frequency = this.frequency, /* all fields */): RecurrenceRule

    public fun toRfc5545String(includePrefix: Boolean = true): String   // "RRULE:FREQ=MONTHLY;BYMONTHDAY=-1"
    public fun exDatesToRfc5545String(): String?                        // "EXDATE;VALUE=DATE:20260331" or null

    public fun occurrences(
        dtStart: LocalDate,
        range: ClosedRange<LocalDate>,
        invalidDatePolicy: InvalidDatePolicy = InvalidDatePolicy.SkipInvalid,
    ): List<LocalDate>                                                   // sorted, exDates already removed
    public fun asSequence(
        dtStart: LocalDate,
        invalidDatePolicy: InvalidDatePolicy = InvalidDatePolicy.SkipInvalid,
    ): Sequence<LocalDate>                                               // lazy, finite iff count/until set, exDates removed

    public companion object {
        public fun parse(rrule: String, exDates: Set<LocalDate> = emptySet()): RecurrenceRule
        public fun parseOrNull(rrule: String, exDates: Set<LocalDate> = emptySet()): RecurrenceRule?
        public fun parseExDates(exDate: String): Set<LocalDate>        // "EXDATE;VALUE=DATE:2026...,2026..." or bare "2026...,2026..."
    }
}

/** Not part of RFC 5545. See 1.6. */
public enum class InvalidDatePolicy { SkipInvalid, ClampToMonthEnd }
```

**Why a plain class and not a `data class`.** The 1.0 API lock (#89) makes every `data class` `componentN/copy/constructor` permanent; a rule will grow `BYYEARDAY`/`BYWEEKNO` later. A hand-written `copy` with defaults plus a builder keeps that additive (open decision 3). `ByDay` is tiny and final, so it stays a `data class`. `Frequency` is an enum; adding `HOURLY` later is source-compatible for callers that use `else` branches and is called out in KDoc.

### 1.3 Validation (constructor `require`, messages name the offending value)

| Rule | Check |
|---|---|
| interval | `>= 1` |
| count / until | `count == null || count >= 1`; **not both** (RFC: "MUST NOT occur in the same RRULE") |
| byMonth | each in `1..12` |
| byMonthDay | each in `-31..-1` or `1..31`; **illegal for WEEKLY** (RFC table) |
| byDay ordinals | ordinal only for `MONTHLY`/`YEARLY`; `MONTHLY` ordinals in `±1..5`; `YEARLY` ordinals in `±1..53`; ordinals and `byMonthDay` together are allowed (they intersect) |
| bySetPos | each in `±1..366`; requires at least one other `BY*` part (RFC) |
| FREQ=DAILY | `byDay` must not have ordinals |

An invalid rule never exists. Everything in section 1.4 can assume a valid rule.

### 1.4 Expansion algorithm (internal `RecurrenceExpansion.kt`)

Inputs: valid `rule`, `dtStart: LocalDate`, query window `[from, to]`, `policy`.

**Period model.** The series is a sequence of *periods* of size `interval` × FREQ unit, starting with the period that contains `dtStart`:

- DAILY: period = one day, `dtStart + k*interval` days.
- WEEKLY: period = a `weekStart`-aligned week; period `k` is the week of `dtStart + 7*k*interval`. (`weekStart` matters for `interval > 1` with `BYDAY`: RFC example `DTSTART=1997-08-05; FREQ=WEEKLY;INTERVAL=2;COUNT=4;BYDAY=TU,SU` gives Aug 5, 10, 19, 24 with `WKST=MO` and Aug 5, 17, 19, 31 with `WKST=SU`; both are test fixtures.)
- MONTHLY: period = calendar month `dtStart.month + k*interval`.
- YEARLY: period = calendar year `dtStart.year + k*interval`.

**Per period** (RFC 5545 §3.3.10 expand/limit table, with the dateutil defaults for the omitted finer parts):

1. Build the candidate set of dates in the period:
   - DAILY: the one date; limited by `byMonth`, `byMonthDay`, `byDay`.
   - WEEKLY: `byDay` expands (default: `dtStart.dayOfWeek`); limited by `byMonth`.
   - MONTHLY: `byMonthDay` expands (default: `dtStart.day` when `byDay` is empty); `byDay` expands (ordinal = n-th such weekday of the month) or, when `byMonthDay` is also present, limits; limited by `byMonth`.
   - YEARLY: `byMonth` expands (default: `dtStart.month` when no `byMonth`/`byMonthDay`/`byDay` is given); `byMonthDay` expands (default: `dtStart.day` when only `byMonth` is given); `byDay` expands, ordinals relative to the month when `byMonth` is present and to the year otherwise.
2. **Invalid dates simply do not exist** (`31` in a 30-day month, `29 Feb` in a common year): they are never produced and therefore never counted (strict policy; clamp policy in 1.6).
3. Sort ascending, de-duplicate.
4. Apply `bySetPos` to that sorted per-period set (1-based, negative from the end). SETPOS is evaluated **before** dropping dates ahead of `dtStart`.
5. Drop dates `< dtStart`. Insert `dtStart` itself as the first instance if the rule did not generate it (contract point 5).
6. Walk dates in order: stop when `date > until` or the running count (from `dtStart`) reaches `count`; the running count is incremented for every generated date, **before** `exDates` are applied.
7. Emit the date iff it is not in `exDates` and lies in `[from, to]`.

**Windowing and cost.**

- `count != null`: the series must be walked from `dtStart` (the count prefix matters); cost ≤ `count` dates plus empty periods. Stop as soon as the period start exceeds `to`.
- `count == null`: fast-forward. Jump straight to the first period that can contain `from` using integer period arithmetic (days/weeks via epoch days, months/years via `year*12+month`). Nothing before `from` can influence later dates because `BYSETPOS` is evaluated inside a period.
- **Fruitless rules** (`FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=30`): the walk ends after `1000` consecutive empty periods (a Feb-29 yearly rule with `interval=7` needs ≤ 28 years, so the bound is comfortably above every legal gap). The result is simply the dates found so far; no exception.
- Epoch-day arithmetic throughout; no `LocalDate.plus` in the hot loop.

### 1.5 Parser and formatter

`RecurrenceRule.parse(rruleString, exDates)` accepts the value with or without the `RRULE:` prefix, surrounding whitespace and lower-case keys. Strict otherwise:

- Unknown or unsupported parts (`BYYEARDAY`, `BYWEEKNO`, `BYHOUR`, `RDATE`…), `FREQ=HOURLY` and below, duplicate parts, empty values and out-of-range numbers throw `IllegalArgumentException` with a message naming the part (`"Unsupported RRULE part BYYEARDAY"`). `parseOrNull` returns `null` for the same inputs.
- `BYDAY` tokens: `(+|-)?<1..2 digits>?(MO|TU|WE|TH|FR|SA|SU)`.
- `UNTIL` accepts `yyyyMMdd`, `yyyyMMdd'T'HHmmss` and `yyyyMMdd'T'HHmmss'Z'`. **Only the date part is kept** (the model is date-granular; open decision 5).
- A rule that violates 1.3 throws from the constructor path with the same exception type.

**Canonical formatting** (`toRfc5545String()`, deterministic so round trips are string-stable): `FREQ`; `INTERVAL` (omitted when 1); `COUNT` or `UNTIL` (`yyyyMMdd`); `BYMONTH`; `BYMONTHDAY`; `BYDAY`; `BYSETPOS`; `WKST` (omitted when `MO`, the RFC default). Sets are emitted in ascending numeric / Monday-first order. Round-trip law (property-tested): `parse(r.toRfc5545String(), r.exDates) == r` and `parse(s).toRfc5545String() == canonical(s)`.

`exDatesToRfc5545String()` returns `EXDATE;VALUE=DATE:20260331,20260430` (ascending) or `null` when empty; `parseExDates` is its inverse and also accepts the bare comma list.

### 1.6 Month-end behaviour: strict by default, clamp is an explicit opt-in

Strict skip-invalid is the RFC meaning and the only thing `toRfc5545String()` can express. For apps that really want "the 31st, or the last day of shorter months" we ship, in order of preference:

1. **`BYMONTHDAY=-1` / `RecurrenceRule.monthlyOnLastDay()`** — RFC-conformant "last day of every month" (Jan 31, Feb 28, Mar 31, Apr 30…).
2. **`InvalidDatePolicy.ClampToMonthEnd`** — non-standard convenience, only as a parameter of `occurrences/asSequence/occurrencesIn/expandRecurring`; it is **not** stored in the rule and never serialised, so a rule cannot silently change meaning when exported. Semantics: each month period generates its dates from the *original* rule day(s) and a day larger than the month length is replaced by the month's last day (de-duplicated within the period); the clamped date **does** consume `COUNT`. `DTSTART=2026-01-31; MONTHLY;COUNT=3` gives `Jan 31, Feb 28, Mar 31` (per-month clamp from day 31, not chained `plusMonths(1)` drift, which would give `Jan 31, Feb 28, Mar 28`). For YEARLY it applies to `29 Feb` (clamped to 28 Feb in common years). It has no effect on `BYDAY` ordinal rules.

Default everywhere: `InvalidDatePolicy.SkipInvalid`.

### 1.7 Builder / DSL

```kotlin
public fun recurrenceRule(
    frequency: Frequency,
    block: RecurrenceRuleBuilder.() -> Unit = {},
): RecurrenceRule

public class RecurrenceRuleBuilder {
    public var interval: Int
    public var count: Int?
    public var until: LocalDate?
    public var weekStart: DayOfWeek
    public fun byDay(vararg days: DayOfWeek)                     // every such weekday
    public fun nth(ordinal: Int, day: DayOfWeek)                 // 2MO, -1FR
    public fun last(day: DayOfWeek)                              // == nth(-1, day)
    public fun byMonthDay(vararg days: Int)
    public fun byMonth(vararg months: Int)
    public fun bySetPos(vararg positions: Int)
    public fun exDate(vararg dates: LocalDate)
}

// Companion factories (all return RecurrenceRule):
RecurrenceRule.daily(interval: Int = 1)
RecurrenceRule.weekly(vararg days: DayOfWeek, interval: Int = 1)
RecurrenceRule.monthlyOnDay(day: Int)                 // BYMONTHDAY=day  (strict; skips short months)
RecurrenceRule.monthlyOnLastDay()                     // BYMONTHDAY=-1
RecurrenceRule.monthlyOnNth(ordinal: Int, day: DayOfWeek)
RecurrenceRule.yearly()
```

Example: `recurrenceRule(Frequency.MONTHLY) { nth(2, DayOfWeek.MONDAY); count = 12; exDate(LocalDate(2026, 5, 11)) }`. The builder validates in `build` via the constructor.

### 1.8 CalendarEvent integration

Current `CalendarEvent<out T>(id, title, span, subtitle, colorArgb, payload)` is a `data class` with a published 6-arg constructor, `copy` and `copy$default`. We append two fields:

```kotlin
public data class CalendarEvent<out T>(
    public val id: String,
    public val title: String,
    public val span: EventSpan,
    public val subtitle: String? = null,
    public val colorArgb: Long? = null,
    public val payload: T? = null,
    public val recurrence: RecurrenceRule? = null,       // set on a series master; null on occurrences
    public val instance: RecurrenceInstance? = null,     // set on expanded occurrences only
) {
    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    public constructor(id: String, title: String, span: EventSpan, subtitle: String? = null,
        colorArgb: Long? = null, payload: T? = null) : this(id, title, span, subtitle, colorArgb, payload, null, null)

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    public fun copy(id: String = this.id, title: String = this.title, span: EventSpan = this.span,
        subtitle: String? = this.subtitle, colorArgb: Long? = this.colorArgb, payload: @UnsafeVariance T? = this.payload,
    ): CalendarEvent<T> = CalendarEvent(id, title, span, subtitle, colorArgb, payload, recurrence, instance)

    public fun occurrencesIn(
        range: ClosedRange<LocalDate>,
        invalidDatePolicy: InvalidDatePolicy = InvalidDatePolicy.SkipInvalid,
    ): List<CalendarEvent<T>>
}

public class RecurrenceInstance(
    public val seriesId: String,          // master.id
    public val recurrenceId: LocalDate,   // start date of this occurrence (RFC 5545 RECURRENCE-ID, date form)
)  // equals/hashCode/toString hand-written (same ABI rationale as RecurrenceRule)

public fun <T> Iterable<CalendarEvent<T>>.expandRecurring(
    range: ClosedRange<LocalDate>,
    invalidDatePolicy: InvalidDatePolicy = InvalidDatePolicy.SkipInvalid,
): List<CalendarEvent<T>>
```

The issue text asks for `List<CalendarEvent>.expandRecurring`; `Iterable` is the same call site and also what `eventsByDate` takes. Binary-compat test (jvmTest `CalendarEventBinaryCompatTest`, modelled on `DayInfoBinaryCompatTest`) must reflectively call the 6-arg constructor, `copy(6)` and `copy$default` of the old shape and the default-marker constructor. Equality of `CalendarEvent` now includes `recurrence` and `instance`; no existing event has either, so behaviour is unchanged.

**`occurrencesIn(range)` semantics:**

1. `recurrence == null`: returns `listOf(this)` when `span` overlaps `range`, else empty (so `expandRecurring` is a uniform overlap filter).
2. Otherwise `dtStart = span.startDate`, `durationDays = span.endDate - span.startDate` (all-day and timed alike).
3. Ask the rule for start dates in `[range.start - durationDays, range.endInclusive]` (the widened window catches an occurrence that began before the window but still overlaps it). `COUNT` is from `dtStart` (section 1.4), `exDates` are matched against the occurrence **start** date and remove the whole occurrence.
4. Build each occurrence by shifting the span: all-day `AllDay(d, d + durationDays)`; timed `Timed(LocalDateTime(d, start.time), LocalDateTime(d + durationDays, end.time))`. Keep only occurrences whose `[occStart .. occEnd]` overlaps `range`.
5. Occurrence fields: `id = "<master.id>@<yyyy-MM-dd of occurrence start>"` (unique, stable; used as the lazy-list key and comparator tiebreak), `title/subtitle/colorArgb/payload` copied, `recurrence = null`, `instance = RecurrenceInstance(master.id, occurrenceStartDate)`. The first occurrence is also re-id'd for uniformity (callers find the master via `instance.seriesId`).
6. Result is chronological. `UNTIL` is compared with the occurrence start date.

**`expandRecurring`:** flat-maps `occurrencesIn` over the receiver in input order (no global re-sort; each master's occurrences are chronological in place). Already expanded occurrences (`recurrence == null`) pass through the overlap filter, so the call is idempotent.

**Downstream wiring.** `eventsByDate(range)` calls `expandRecurring(range)` first (open decision 4) so `Agenda`, `DaySchedule/WeekSchedule`, `WeekEventBanners` and `DayDecorators.eventDotsForEvents` need no change. Callers that bypass `eventsByDate` call `expandRecurring` themselves.

### 1.9 Tests (`orrery-core` commonTest unless noted)

Fixtures named after the behaviour they protect:

- **strictSkipInvalid:** `DTSTART 2026-01-31, MONTHLY;COUNT=3` → `01-31, 03-31, 05-31`. `BYMONTHDAY=-1;COUNT=3` → `01-31, 02-28, 03-31`. `MONTHLY;BYMONTHDAY=30` skips February without consuming count.
- **windowClipsSeries:** same rule, window = March 2026 only → exactly `[03-31]`; window = April → `[]`; window = May → `[05-31]`; `COUNT` does not restart.
- **exDateNoRefill:** add `exDates = {2026-03-31}`: full window → `01-31, 05-31` (no fourth occurrence); March window → empty.
- **clampPolicy:** `ClampToMonthEnd`, `COUNT=3` → `01-31, 02-28, 03-31`; leap year `2028-02` → `02-29`.
- **leapDay:** `DTSTART 2024-02-29, YEARLY;COUNT=3` → `2024, 2028, 2032`; with `interval=1` common years are skipped, not clamped.
- **nthWeekday:** `2MO`, `-1FR`, `5MO` (months without a fifth Monday skipped), yearly `20MO`, `BYMONTH=11;BYDAY=4TH` (US Thanksgiving).
- **setPos:** `MONTHLY;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=-1` (last weekday), `BYSETPOS=1,-1`.
- **weekStart:** the two RFC `WKST=MO`/`WKST=SU` biweekly examples above.
- **dtStartCounts:** `FREQ=WEEKLY;BYDAY=TU` with a Monday `DTSTART` includes the Monday as instance 1.
- **until:** inclusive on the start date; date-time `UNTIL` keeps the date part.
- **multiDay:** weekly Fri→Mon event, window `Sat..Sun` returns the occurrence that began on the previous Friday; window ending Thursday excludes the next one; timed overnight event keeps end time-of-day; `exDate` on the Friday start removes the whole occurrence.
- **expandRecurring:** idempotent, mixed recurring and plain, input order, plain event outside range dropped, occurrence ids unique, `instance` set.
- **parse/format:** every example above, canonical ordering, `RRULE:` prefix and lower-case, every rejection message, round-trip property over a generated corpus, `EXDATE` list round trip.
- **fastForward:** `DAILY` from year 2000 queried in 2031 runs in a bounded step count (assert via an internal iteration counter), `COUNT=1000` walk stops at the count, fruitless rule terminates.
- **jvmTest `RecurrenceOracleTest`:** generated by `tools/golden/rrule_fixtures.py` (python-dateutil, version pinned, same layout as `holidays_fixtures.py`) comparing ~200 rules × windows; any intentional divergence (clamp, always-include-DTSTART) is listed in `golden/rrule-exceptions.csv` with its RFC citation.
- **jvmTest `CalendarEventBinaryCompatTest`** (1.8).

---

## 2. #87 Viewport Date-Range Flows (`orrery-compose`)

### 2.1 Semantics shared by all containers

- Each state gets a snapshot-observable property `visibleDateRange: ClosedRange<LocalDate>` — the exact dates that can be painted for the pages currently intersecting the viewport — and an extension `visibleDateRangeFlow(prefetch…)` in `Observation.kt` built as `snapshotFlow { … }.distinctUntilChanged()`, like `visibleMonths()`. Cold flow, collect outside composition (`LaunchedEffect`). The first emission is immediate (never empty, even before first layout).
- Prefetch arguments default to `1` (issue AC), must be `>= 0` (`require`); `0` means "exact". Prefetch widens by whole months/weeks on each side and is **clamped** to the configured bounds (`startMonth/endMonth`, `startDate/endDate`; unbounded sides are not clamped) and never makes the range smaller than the exact one.
- A page counts as visible when any pixel of it is in the viewport (`LazyListState.layoutInfo.visibleItemsInfo`). While a horizontal swipe is in flight the range therefore already covers both months, which is what a prefetching loader wants and avoids a blank frame. The range is thus monotone while paging and shrinks only when the old page has left.
- Ranges are contiguous and ignore `visibleDaysOfWeek` (hidden weekdays are still in the range; loaders over-fetch by at most two days a week, which is cheaper than being wrong).
- No debouncing and no coalescing across pages; `distinctUntilChanged` is the only filter.

### 2.2 `CalendarState` (month pagers; `HorizontalCalendar` and `VerticalCalendar`)

```kotlin
// CalendarState (new members)
public val visibleDateRange: ClosedRange<LocalDate>          // exact, snapshot-observable

// Observation.kt
public fun CalendarState.visibleDateRangeFlow(prefetchMonths: Int = 1): Flow<ClosedRange<LocalDate>>
```

- Visible months = `visibleItemsInfo.map { it.index / itemsPerMonth }` (min..max), so sticky-header mode (`itemsPerMonth = 2`) and `VerticalCalendar` (many months on screen) work. With an empty `visibleItemsInfo` fall back to `firstVisibleMonth`.
- **Exact range** = first date of the grid of the first visible month through the last date of the grid of the last visible month: for `OutDateStyle.EndOfRow` the in/out dates of the partial first/last weeks are included (they are painted), for `EndOfGrid` the 6-row grid, for `None` only the month's own days. Computed arithmetically with `CalendarPages.weekStart` (no `monthGrid` allocation).
- **Prefetched range** = `[min(exactStart, firstDay(firstMonth - n)), max(exactEnd, lastDay(lastMonth + n))]` clamped to `startMonth.firstDay..endMonth.lastDay`.
- Example (horizontal, `firstDayOfWeek = MONDAY`, EndOfRow, Feb 2026 visible, `prefetchMonths = 1`): exact `2026-01-26..2026-03-01`, prefetched `2026-01-01..2026-03-31`.

### 2.3 `WeekCalendarState`

```kotlin
public val visibleDateRange: ClosedRange<LocalDate>
public fun WeekCalendarState.visibleDateRangeFlow(prefetchWeeks: Int = 1): Flow<ClosedRange<LocalDate>>
```

- Pages = `visibleItemsInfo` indices; a page's dates are computed arithmetically from the start of week `CalendarPages.weekAt(effectiveStartDate, index, firstDayOfWeek, visibleWeeksCount)` for `7 * visibleWeeksCount` days (an internal accessor exposes `effectiveStartDate`; `weeksAt` is not used because it filters hidden days).
- Prefetch widens by `7 * prefetchWeeks` days on each side (a "week" is a row, independent of `visibleWeeksCount`), clamped to the week containing `startDate` / `endDate` when set.

### 2.4 `CollapsibleCalendarState`

The brief's signature (`prefetchMonths, prefetchWeeks` only) cannot be implemented: `CollapsibleCalendarState` holds just `expandFraction` and has no reference to the two pagers that hold the dates. The flow therefore takes them explicitly (open decision 9):

```kotlin
public fun CollapsibleCalendarState.visibleDateRangeFlow(
    calendarState: CalendarState,
    weekState: WeekCalendarState,
    prefetchMonths: Int = 1,
    prefetchWeeks: Int = 1,
): Flow<ClosedRange<LocalDate>>
```

Selection by `expandFraction` (matching `CollapsibleGeometry`, whose TwoWeeks fraction is `0.35` whether or not `enableTwoWeekStop` is on), always choosing the **superset** during a drag so rows that are partly visible are never unloaded:

| expandFraction | Visible rows | Range |
|---|---|---|
| `== 0` (Week stop) | the anchor week | `weekState` week row ± `prefetchWeeks` weeks |
| `0 < f <= 0.35` (TwoWeeks) | anchor week plus its neighbour (the next row, or the previous row when the anchor is the month grid's last row, mirroring `twoWeekTopRow`) | those two rows ± `prefetchWeeks` weeks |
| `f > 0.35` (Month) | whole month grid | `calendarState.visibleDateRange` expanded by `prefetchMonths` (2.2) |

The flow reads `expandFraction`, `calendarState` and `weekState` inside one `snapshotFlow` so a settle animation emits at most twice (Month → TwoWeeks → Week) and a user drag emits only when a threshold is crossed. Ranges can shrink on collapse; KDoc tells loaders to keep their cache rather than evict on shrink.

### 2.5 Tests (`orrery-compose` androidHostTest, style of `ObservationTest`)

- `CalendarState`: initial emission equals the exact+prefetch example above; `scrollToMonth` emits once; duplicate scroll emits nothing; prefetch `0` equals `visibleDateRange`; clamped at `startMonth/endMonth`; `OutDateStyle.None/EndOfGrid`; `VerticalCalendar` with 2–3 months visible; `stickyMonthHeaders` (items per month 2); mid-swipe covers both months.
- `WeekCalendarState`: `visibleWeeksCount = 1, 2`, hidden weekdays do not change the range, bounds clamping, `prefetchWeeks = 0`.
- `CollapsibleCalendarState`: `collapse()` → Week range, `animateTo(TwoWeeks)` → two rows (including last-row anchor), `expand()` → month range; intermediate fraction (via the internal `snapTo`) 0.2 → two-row range, 0.5 → month; month paging while expanded and week paging while collapsed re-emit; `require(prefetch >= 0)`.
- All three: `prefetch < 0` throws `IllegalArgumentException`.

---

## 3. #87 YearCalendar / MiniMonth Frame-Rate (`orrery-compose`)

### 3.1 Where the cost is

Per `CalendarDefaults.MiniMonth` today: a `Column` + title `Text` + one `Row` per week (4–6) + one `Text` **with a `drawBehind` lambda** per grid cell (28–31 in-month cells; out-cells are empty `Text("")` too) ≈ 40–48 nodes, plus `monthGrid()` and 28–31 `date.day.toString()` allocations and `dayInfo`/selection evaluation per recomposition. `YearCalendar` adds a `Box` with `focusRing`, `clip` and `clickable` per month and rebuilds `(1..12).chunked(columns)` in every item composition. A year page is therefore roughly 500–600 nodes composed and measured when a page scrolls in.

### 3.2 Immutable per-month model (cached)

```kotlin
internal class MiniMonthModel(            // pure; may live in orrery-core as internal-to-compose-only helper, kept in orrery-compose
    val yearMonth: YearMonth,
    val firstDayOfWeek: DayOfWeek,
    val leadingBlanks: Int,               // 0..6
    val dayCount: Int,                    // 28..31
    val rowCount: Int,                    // 4..6
)
```

- Everything derived from `(yearMonth, firstDayOfWeek)` only; day labels come from one shared `Array<String>(32)` (no per-frame `toString`).
- **Cache:** `MiniMonthModelCache`, a bounded LRU (capacity 48 = four years) provided by `YearCalendar` through a `CompositionLocal` (a standalone `MiniMonth` creates its own with `remember`). No global mutable state (KMP-safe, no locks); it is read only on the UI thread during composition. The model is small, so the win here is mostly allocation-free reuse and a stable identity; the real win is 3.3.
- Per-recomposition inputs are folded into one immutable value so unchanged months skip redraw:
  `MiniMonthDayState(todayDay: Int, selectedMask: Int, rangeFirstDay: Int, rangeLastDay: Int, holidayMask: Int)` — bit `d-1` for day `d`. It is computed once per `(yearMonth, today, selection, dayInfo)` change in `remember` (keys include `dayInfo`; callers should hoist it) and has structural `equals`. `dayInfo` is still invoked only for the month's own dates, in composition, exactly 28–31 times (existing `MiniMonthTest` keeps its assertion).

### 3.3 Single-Canvas drawing path

`CalendarDefaults.MiniMonth` keeps both public overloads unchanged (hidden 4-param bridge stays) and becomes one layout node:

- `Layout/Spacer + Modifier.drawWithCache` (or `Canvas`): width from constraints, height = padding + title line + `rowCount × rowHeight` where `rowHeight` is the measured height of the day-number text style (9.sp scaled by font scale), so large-text accessibility settings keep working (cf. `FontScaleLayoutTest`).
- Text via one shared `MiniMonthTextCache` (provided with the model cache): `TextMeasurer` (`rememberTextMeasurer(cacheSize)`) plus lazily built `TextLayoutResult` arrays `regular[1..31]` and `bold[1..31]` for the 9.sp style, keyed by `(density, fontFamilyResolver, layoutDirection)`, plus the title layout per month. Layouts are measured **without** colour and drawn with `drawText(textLayoutResult, color = …, topLeft = …)`, so changing selection or theme never re-measures. The 62 layouts are shared by all 12 months of all years.
- Pill/today backgrounds reuse the existing geometry (`drawMiniDayBackground` is rewritten to take a cell rect instead of `size/center`) so the Roborazzi goldens stay within tolerance: `MiniMonthSnapshotTest` (light/dark/RTL/close-up) is re-recorded only if sub-pixel differences appear, and Rei signs off the diff. RTL mirrors columns and swaps the rounded ends exactly as the `Row` + `LayoutDirection` code does today; `FocusSnapshotTest.miniMonthFocused` is unaffected (the ring belongs to `YearCalendar`'s cell).
- Result: ≈ 2 layout nodes per month (Canvas + the existing cell `Box`) instead of ≈ 45; a year page about 25 nodes instead of ~550.

### 3.4 Accessibility and input (preserved)

- Clicks, ripple, focus ring, hover and keyboard focus stay on `YearCalendar`'s per-month `Box` (`clickable(role = Role.Button)`), untouched.
- Today the clickable cell merges the descendants into one TalkBack button that reads the month name and 28–31 numbers. The Canvas node replaces that with `semantics { contentDescription = "<month name> <year>" (via the same `PlatformDateFormat` helper `MonthTitle` uses); stateDescription = "<Today> · N selected" }` built from existing strings (`orrery_day_state_today` and a plural-free "N" suffix, no new locale strings) — shorter and more useful than reading every digit. Per-day virtual nodes are intentionally not added (open decision 11).
- `MiniMonthTest.rendersEveryDayNumberWithHighlights` asserted `Text` nodes; it is replaced by (a) a pixel test in the style of `FocusHoverTest.markerPixels` (today circle, pill, holiday colour present at the right cell centres) and (b) a semantics test for the description.

### 3.5 `YearCalendar` cheap wins (same PR)

- Hoist `(1..12).chunked(columns)` with `remember(columns)`.
- `items(count, key, contentType = { YearPageType })`.
- Provide the two caches above once per `YearCalendar` via `CompositionLocalProvider`.
- `monthContent` stays the extension point; custom month content (e.g. the tests' `Text(ym.toString())`) is unaffected.

### 3.6 Verification

- **Composition-count (CI, deterministic):** an unmerged-semantics-tree node count test: one year of default `YearCalendar` has ≤ 40 nodes (was ≈ 12 × 30+); `MiniMonthModelCache` unit tests (hit/miss/eviction, same instance for same key); `TextMeasurer` layout count test with an injected measuring counter: scrolling through 20 years measures 62 + 12 title layouts once, not per month; `dayInfo` call count unchanged.
- **Macrobenchmark (not a CI gate):** a `macrobenchmark` scaffold in `:sample` scenarios (fling YearCalendar 10 years; fling `VerticalCalendar`; collapse/expand scaffold) recording `FrameTimingMetric` before/after. Asuka runs it on a device or emulator and pastes the numbers into the implementation PR; the target is "frame overrun p90 below 8 ms on a Pixel-5-class profile" and no regression vs baseline. Baseline profile (`baseline-prof.txt`) is regenerated for the new draw path.

---

## 4. Compatibility Summary

| Area | Change | Compatibility |
|---|---|---|
| `CalendarEvent` | +`recurrence`, +`instance`, +`occurrencesIn` | HIDDEN 6-arg ctor and `copy` bridges, jvm binary-compat test |
| `eventsByDate` | expands recurring events first | no change for events without `recurrence` |
| New core API | `RecurrenceRule`, `ByDay`, `Frequency`, `InvalidDatePolicy`, `RecurrenceInstance`, `recurrenceRule {}`, `expandRecurring` | additive; classes non-data where growth is expected |
| `CalendarState`, `WeekCalendarState` | +`visibleDateRange` | additive property |
| `ObservationKt` | +3 `visibleDateRangeFlow` extensions | additive |
| `CalendarDefaults.MiniMonth` | internals only | both overloads unchanged |
| `YearCalendar` | internals only | signature unchanged |
| Strings | none | no new `orrery_*` resources |

## 5. Rollout Order (for Maya)

Two independent tracks; run in parallel, one PR each, `apiDump` in every PR:

1. **#86a** core model, validation, builder, parser/formatter + tests (model and parser land first; `occurrences` is not exposed until the engine PR, so no stub that throws ships).
2. **#86b** expansion engine (strict + clamp), dateutil oracle fixtures, `CalendarEvent` integration, `expandRecurring`, `eventsByDate` expansion, binary-compat test; `:sample` Agenda gets one weekly and one "last day of month" recurring event.
3. **#87a** `visibleDateRange` + the three flows + tests; `:sample` shows the range in a debug chip.
4. **#87b** `MiniMonth` Canvas path, caches, `YearCalendar` wins, tests, re-recorded goldens if needed, benchmark scaffold.
5. README feature table, `docs/guide` page "Recurring events and lazy loading", CHANGELOG `[Unreleased]`.

## 6. Open Decisions for Andrii (with recommended defaults)

1. **DTSTART when it does not match the rule** (e.g. Monday start with `BYDAY=TU`): *Recommended:* RFC §3.3.10 — it is always instance 1 and counts toward `COUNT`. Alternative: dateutil behaviour (dropped); listed in the oracle exceptions file.
2. **Month-end clamping:** *Recommended:* strict skip-invalid default; `BYMONTHDAY=-1` / `monthlyOnLastDay()` for last day; `InvalidDatePolicy.ClampToMonthEnd` only as an explicit, non-serialised parameter. Alternative: omit clamp entirely (smaller API).
3. **`RecurrenceRule` as plain class vs `data class`:** *Recommended:* plain class + builder + hand-written `copy` (keeps `BYYEARDAY` etc. additive after the 1.0 lock).
4. **`eventsByDate` auto-expands recurring events:** *Recommended:* yes (one-line integration for every consumer; no behaviour change without `recurrence`). Alternative: callers always call `expandRecurring` explicitly.
5. **`UNTIL` granularity:** *Recommended:* date only (date part of a date-time `UNTIL` kept, inclusive on the start date), matching `until: LocalDate?` in the issue. Known deviation: a timed series with `UNTIL=...T090000Z` still includes that day's later occurrence.
6. **Unsupported parts:** *Recommended:* reject with explicit exceptions in Phase 1 (`BYYEARDAY`, `BYWEEKNO`, time `BY*`, `RDATE`, `EXRULE`, sub-daily `FREQ`); `BYYEARDAY`/`BYWEEKNO` are the first post-1.0 additions.
7. **Occurrence ids:** *Recommended:* `"<seriesId>@<yyyy-MM-dd>"` plus `RecurrenceInstance(seriesId, recurrenceId)` for lookup. Alternative: keep the master id and rely on `instance` (breaks unique lazy keys).
8. **Prefetch default:** *Recommended:* `1` on all flows (issue AC) with whole-month/week widening and bound clamping; `0` is "exact".
9. **Collapsible flow signature:** *Recommended:* explicit `calendarState` and `weekState` parameters (the state cannot reach them); an alternative is a `rememberCollapsibleVisibleDateRange(...)` helper added later.
10. **Range semantics during swipes:** *Recommended:* any-pixel-visible pages (earlier prefetch, shrinks after the old page leaves). Alternative: settled page only (`firstVisibleMonth`), simpler but causes blank frames while loading.
11. **Mini-month accessibility:** *Recommended:* month-level semantics only (description + state), same single-button model as today. Per-day virtual nodes are a follow-up if TalkBack users ask.
12. **`VerticalYearCalendar` composable:** the issue title mentions it, but Orrery has only the horizontal `YearCalendar` and the AC target the mini-months. *Recommended:* not in Phase 1; the Canvas `MiniMonth` benefits any vertical year list callers build with `LazyColumn`. Revisit after Phase 3. Likewise a `YearCalendarState.visibleDateRangeFlow` (trivial: Jan 1–Dec 31 of `firstVisibleYear` ± years) is deferred.
13. **Benchmarks:** *Recommended:* deterministic CI tests are the gate; the macrobenchmark scaffold ships but is run manually by Asuka.
14. **Time zones:** *Recommended:* floating local date/time only in 1.0; zone support would be an additive `TimeZone?` on the series later.
15. **Runaway rules:** *Recommended:* silent stop after 1000 consecutive empty periods (no exception), documented in KDoc.
