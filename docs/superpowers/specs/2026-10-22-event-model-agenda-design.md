# Event Model & Agenda List (0.7.0) — Design

**Date:** 2026-10-22
**Status:** Proposed (awaiting Andrii sign-off)
**Issue:** #22 (Co-designed with #21 time-grid schedule view; tracking roadmap: #26)
**Owners:** Ritsuko (design), Maya (implementation), Asuka (review)

## Context

Currently, `sample/AgendaScreen.kt` defines an ad-hoc `internal data class Event(val time: String, val title: String, val color: Color)` and builds a manual `LazyColumn` below `CollapsibleCalendarScaffold`. Every consumer building an event-driven calendar must reinvent:

1. A temporal event representation supporting both timed and all-day (single- or multi-day) spans.
2. Fast date-indexed lookup tables feeding `DayDecorators.eventDots`, `DayDecorators.badge`, an agenda list, and the hourly schedule view (#21).
3. A ready-made `Agenda` list composable that synchronizes selection and scroll position with `HorizontalCalendar`, `WeekCalendar`, or `CollapsibleCalendarScaffold`.

This specification—co-designed with the Time-Grid Schedule View (#21)—introduces a minimal, optional, UI-agnostic event model in `orrery-core` and a customizable `Agenda` composable in `orrery-compose`.

**Deliberate non-goals (what we are NOT building):**

- No iCalendar (`RFC 5545` / `RRULE`) recurrence expansion engine or persistence layer; callers expand recurring events in their domain/repository layer for the queried date range.
- No timezone conversion policy inside `CalendarEvent`; events operate in the calendar's display timezone via `kotlinx-datetime` `LocalDate` and `LocalDateTime`.
- No Compose `Color` dependency in `orrery-core`; colors are carried as optional ARGB `Long?` hints or resolved in `orrery-compose`.

---

## 1. Core Event Model (`orrery-core`)

New file `orrery-core/src/main/kotlin/me/kozakov/orrery/core/CalendarEvent.kt`:

```kotlin
package me.kozakov.orrery.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * Temporal extent of a [CalendarEvent].
 *
 * Making the span a sealed hierarchy prevents illegal combinations such as an
 * all-day event with a start time or a timed event missing one endpoint.
 */
public sealed interface EventSpan {
    public val startDate: LocalDate
    public val endDate: LocalDate

    /**
     * All-day or multi-day banner event spanning [startDate]..[endDate] inclusive.
     */
    public data class AllDay(
        override val startDate: LocalDate,
        override val endDate: LocalDate = startDate,
    ) : EventSpan {
        init {
            require(endDate >= startDate) {
                "endDate ($endDate) must not precede startDate ($startDate)"
            }
        }
    }

    /**
     * Timed event spanning [start]..[end] (supports same-day and overnight spans).
     */
    public data class Timed(
        public val start: LocalDateTime,
        public val end: LocalDateTime,
    ) : EventSpan {
        init {
            require(end >= start) {
                "end ($end) must not precede start ($start)"
            }
        }

        override val startDate: LocalDate get() = start.date
        override val endDate: LocalDate get() = end.date
    }
}

/**
 * Minimal, optional calendar event model shared across [DayDecorators],
 * `Agenda`, and the time-grid schedule views (#21).
 *
 * @param T Optional caller-owned domain payload for click handling or custom slots.
 * @property id Stable identifier used as a Compose lazy-list/grid key and tie-breaker.
 * @property title Primary display title.
 * @property span Temporal extent ([EventSpan.AllDay] or [EventSpan.Timed]).
 * @property subtitle Optional secondary text (location, room, summary).
 * @property colorArgb Optional 0xAARRGGBB color hint (UI-agnostic in `orrery-core`).
 * @property payload Optional domain object attached by the consumer.
 */
public data class CalendarEvent<out T>(
    public val id: String,
    public val title: String,
    public val span: EventSpan,
    public val subtitle: String? = null,
    public val colorArgb: Long? = null,
    public val payload: T? = null,
) {
    /** True if this event covers any part of [date]. */
    public fun occursOn(date: LocalDate): Boolean =
        date >= span.startDate && date <= span.endDate
}
```

### Indexing & Query Helpers (`orrery-core`)

```kotlin
/**
 * Groups events by each [LocalDate] they cover within [range], sorted in
 * canonical display order per day:
 * 1. [EventSpan.AllDay] events (ordered by [EventSpan.startDate], then [CalendarEvent.title], then [CalendarEvent.id])
 * 2. [EventSpan.Timed] events (ordered by [EventSpan.Timed.start], then [EventSpan.Timed.end], then [CalendarEvent.title], then [CalendarEvent.id])
 *
 * Iterates via epoch days (matching Orrery's existing Bolt optimization) to avoid
 * `DateTimeUnit.DAY` allocation overhead.
 */
public fun <T> Iterable<CalendarEvent<T>>.eventsByDate(
    range: ClosedRange<LocalDate>,
): Map<LocalDate, List<CalendarEvent<T>>>
```

---

## 2. Architecture & Data Flow Across Components

```text
                    Iterable<CalendarEvent<T>>
                                │
                    eventsByDate(visibleRange)
                                │
        ┌───────────────────────┼───────────────────────┐
        ▼                       ▼                       ▼
  DayDecorators            Agenda (#22)       DaySchedule / WeekSchedule (#21)
  ├─ eventDots(events)     ├─ AllDay rows     ├─ AllDay banner row
  └─ badge(events)         └─ Timed rows      └─ 24h time grid (ScheduleLayout)
```

### Decorator Convenience Overloads (`orrery-compose`)

In `orrery-compose/.../DayDecorators.kt`, add optional convenience overloads that accept a date-indexed event map:

```kotlin
/** Up to [maxDots] distinct event colors for each day from [eventsByDate]. */
@Composable
public fun DayDecorators.eventDotsForEvents(
    eventsByDate: (LocalDate) -> List<CalendarEvent<*>>,
    defaultColor: Color = MaterialTheme.colorScheme.primary,
    maxDots: Int = 3,
    dotSize: Dp = 4.dp,
    spacing: Dp = 2.dp,
): DayDecorator
```

---

## 3. `AgendaState` & `Agenda` Composable (`orrery-compose`)

New file `orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/Agenda.kt`:

```kotlin
@Stable
public class AgendaState internal constructor(
    public val startDate: LocalDate,
    public val endDate: LocalDate,
    initialVisibleDate: LocalDate,
) {
    internal val listState: LazyListState

    /** First date currently visible at the top of the agenda viewport (snapshot-observable). */
    public val firstVisibleDate: LocalDate

    public suspend fun scrollToDate(date: LocalDate)
    public suspend fun animateScrollToDate(date: LocalDate)

    public companion object {
        public val Saver: Saver<AgendaState, Any>
    }
}

@Composable
public fun rememberAgendaState(
    startDate: LocalDate = currentDate().minus(365, DateTimeUnit.DAY),
    endDate: LocalDate = currentDate().plus(365, DateTimeUnit.DAY),
    initialVisibleDate: LocalDate = currentDate(),
): AgendaState
```

### `Agenda` Composable Signature

```kotlin
@Composable
public fun <T> Agenda(
    events: (LocalDate) -> List<CalendarEvent<T>>,
    state: AgendaState = rememberAgendaState(),
    modifier: Modifier = Modifier,
    today: LocalDate = currentDate(),
    showEmptyDays: Boolean = false,
    stickyDayHeaders: Boolean = true,
    onEventClick: ((CalendarEvent<T>) -> Unit)? = null,
    dayHeader: @Composable (LocalDate, Boolean) -> Unit = { date, isToday ->
        AgendaDefaults.DayHeader(date = date, isToday = isToday, strings = strings)
    },
    eventContent: @Composable (CalendarEvent<T>) -> Unit = { event ->
        AgendaDefaults.EventRow(event = event, onClick = onEventClick?.let { cb -> { cb(event) } }, strings = strings)
    },
    emptyDayContent: @Composable (LocalDate) -> Unit = {
        AgendaDefaults.EmptyDay(strings = strings)
    },
    emptyRangeContent: @Composable () -> Unit = {
        AgendaDefaults.EmptyRange(strings = strings)
    },
    strings: AgendaStrings = AgendaDefaults.strings(),
)
```

### Behavior & Layout Details

- **Display modes (`showEmptyDays`):**
  - `false` (default): Only dates within `state.startDate..state.endDate` that have `events(date).isNotEmpty()` (plus `initialVisibleDate`/`today` if scrolled to) are emitted into the `LazyColumn`. If no dates in the range have events, `emptyRangeContent` is rendered.
  - `true`: Every date in `state.startDate..state.endDate` is emitted; days without events render `emptyDayContent(date)`.
- **Scroll-to-date mapping:** `AgendaState` maintains an internal index mapping from `LocalDate` to `LazyColumn` item index (accounting for sticky header items + event items). When `scrollToDate(date)` is called in `showEmptyDays = false` mode for a date with no events, it scrolls to the nearest date `>= date` with events.
- **Calendar synchronization:** Consumers pairing `Agenda` inside `CollapsibleCalendarScaffold` can observe `selectionState.selection.single` in a `LaunchedEffect` to call `agendaState.animateScrollToDate(selectedDate)`, or observe `agendaState.firstVisibleDate` while `agendaState.listState.isScrollInProgress` to update the calendar selection.
- **Default `EventRow` (`AgendaDefaults.EventRow`):**
  - Leading vertical color bar (`4.dp` wide, `RoundedCornerShape(2.dp)`, using `event.colorArgb?.let(::Color) ?: MaterialTheme.colorScheme.primary`).
  - Time label column: `strings.allDayLabel` ("All day") for `EventSpan.AllDay`, or localized `HH:mm – HH:mm` for `EventSpan.Timed`.
  - Title (`bodyLarge`) and optional `subtitle` (`bodyMedium`, `onSurfaceVariant`).
  - Full-row `Modifier.clickable(role = Role.Button)` when `onEventClick != null`.

---

## 4. Defaults & Strings (`AgendaDefaults.kt`)

```kotlin
@Immutable
public class AgendaStrings(
    public val todayPrefix: String,
    public val allDayLabel: String,
    public val noEventsLabel: String,
)

public object AgendaDefaults {
    public fun strings(
        todayPrefix: String = "TODAY",
        allDayLabel: String = "All day",
        noEventsLabel: String = "No events",
    ): AgendaStrings
}
```

---

## 5. Alternatives Considered (MAGI Consensus)

1. **Where `CalendarEvent` lives (`orrery-core` vs `orrery-compose`):**
   - *Melchior (Technical):* Keeping `CalendarEvent` and `eventsByDate` in `orrery-core` allows pure-Kotlin unit testing and reuse by non-Compose view models and schedule layout algorithms (#21).
   - *Balthasar (Operational):* Avoiding `androidx.compose.ui.graphics.Color` in `orrery-core` preserves `orrery-core`'s zero-UI-dependency footprint for KMP (#20). `colorArgb: Long?` bridges cleanly via `Color(argb)` in `orrery-compose`.
   - *Casper (Product):* Adding `payload: T? = null` (with `CalendarEvent<Nothing>` usable when no payload is needed) gives callers type-safe access to their domain model on click without wrapper boilerplate.
2. **Flat nullable `startTime: LocalTime?` vs sealed `EventSpan`:**
   - *Decision:* Sealed `EventSpan` (`AllDay` vs `Timed`). Flat nullable fields allow ambiguous states (e.g., `isAllDay = true` with `startTime != null`, or multi-day timed events where `LocalTime` alone loses the date boundary). `EventSpan` models single-day all-day, multi-day all-day, single-day timed, and overnight timed events without ambiguity.

---

## 6. Testing & Sample App Plan

- **Core unit tests (`CalendarEventTest`):**
  - `EventSpan.AllDay` and `EventSpan.Timed` invariant validation (`end < start` throws `IllegalArgumentException`).
  - `occursOn(date)` for single-day, multi-day all-day, and overnight timed events.
  - `eventsByDate(range)` ordering (AllDay before Timed, chronological tie-breaking) and boundary clipping.
- **Compose interaction tests (`AgendaTest`):**
  - Renders day headers, all-day rows, and timed rows in order.
  - `showEmptyDays = false` vs `true` behavior and `emptyRangeContent` fallback.
  - `onEventClick` callback fires with the clicked `CalendarEvent<T>`.
  - `scrollToDate` / `animateScrollToDate` updates `firstVisibleDate`; state survives `StateRestorationTester`.
- **Roborazzi snapshot tests (`AgendaSnapshotTest`):**
  - `agendaLight`, `agendaDark`, `agendaEmpty` with deterministic July 2026 fixture events.
- **Sample app (`sample/AgendaScreen.kt`):**
  - Refactor `SampleData` and `AgendaScreen` to use `CalendarEvent` + `Agenda` + `DayDecorators.eventDotsForEvents`.
- **Verification:**
  - `./gradlew ktlintCheck detekt updateKotlinAbi checkKotlinAbi :orrery-core:test :orrery-compose:testDebugUnitTest :orrery-compose:verifyRoborazziDebug :sample:assembleDebug`.

---

## 7. Open Decisions for Sign-Off

1. **Multi-day timed events in `Agenda`:** When an `EventSpan.Timed` spans midnight (e.g., 22:00 Jul 15 → 02:00 Jul 16), should `AgendaDefaults.EventRow` format the second day's time as `00:00 – 02:00` (clamped to that date) or `Jul 15, 22:00 – 02:00`? *(Recommendation: clamp display times to the day's `00:00..24:00` window with a subtle continuation arrow `→ 02:00`, matching Google Calendar and the #21 time-grid clipping behavior.)*
