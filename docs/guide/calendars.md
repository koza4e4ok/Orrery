# Calendars and navigation

## Calendars

Five composables cover the common layouts. Each takes a state holder and a
`dayContent` slot; `DefaultDay` is the batteries-included cell, but the slot
accepts any composable.

| Composable                    | Layout                                                |
| ----------------------------- | ----------------------------------------------------- |
| `HorizontalCalendar`          | One month per page, swipe horizontally                |
| `VerticalCalendar`            | Continuous month list, optional sticky headers        |
| `WeekCalendar`                | Single week strip, one week per page                  |
| `YearCalendar`                | Year overview with a tappable mini-month grid         |
| `CollapsibleCalendarScaffold` | Month that collapses to a week row as content scrolls |

```kotlin
HorizontalCalendar(state = rememberCalendarState()) { day -> DefaultDay(day) }

VerticalCalendar(state = state, stickyMonthHeaders = true) { day -> DefaultDay(day) }

WeekCalendar(state = rememberWeekCalendarState()) { day -> DefaultDay(day) }

YearCalendar(onMonthClick = { month -> /* navigate */ })
```

`CollapsibleCalendarScaffold` pairs a month state with a week state and collapses
between them as its `content` scrolls (the Agenda screen in the sample):

```kotlin
CollapsibleCalendarScaffold(
    calendarState = rememberCalendarState(),
    weekState = rememberWeekCalendarState(startDate = start, endDate = end),
    dayContent = { day -> DefaultDay(day, selectionState = selection) },
) {
    LazyColumn { /* scrolling this collapses the month to a week row */ }
}
```

Every calendar handles keyboard/D-pad navigation (`keyboardNavigation`, on by
default): arrow keys move by a day or a week row, `Home` / `End` jump to the row's
ends, and `PageUp` / `PageDown` move a month (`Shift` for a year). Moving past the
first or last row pages `HorizontalCalendar` and multi-row `WeekCalendar`
(`visibleWeeksCount` 1..4) and scrolls `VerticalCalendar` to the target month.
`HorizontalCalendar` animates height between 4/5/6-week months, and `WeekCalendar`
springs its height when `visibleWeeksCount` changes (`animateHeight = false` turns
it off).

`CollapsibleCalendarScaffold` keeps the month grid opaque while it collapses, so the
active week row stays in place without blinking, and a released drag or fling settles
on a spring that carries its velocity.

Under `LayoutDirection.Rtl` the grids, range bands, check-in / check-out halves and
event banners mirror, and `Left` / `Right` follow the mirrored grid (`Left` moves to
the next day).

## Calendar state and navigation

`rememberCalendarState` pins the month range, the first visible month, the first
day of the week (locale-derived by default), and the `OutDateStyle` (how
leading/trailing cells fill). The state exposes scrolling as suspend functions
and the visible month as snapshot state:

```kotlin
val state = rememberCalendarState(
    startMonth = YearMonth(2020, 1),        // omit bounds for unbounded scrolling
    endMonth = YearMonth(2030, 12),         // null = open on that side
    outDateStyle = OutDateStyle.EndOfGrid,  // always six rows
)
val scope = rememberCoroutineScope()

state.firstVisibleMonth                       // snapshot-observable
scope.launch { state.animateScrollToToday() } // also: scrollToMonth, (animate)ScrollToDate
state.updateRange(newStart, newEnd)           // grow/shrink the range in place
```

For your own toolbar arrows or shortcuts, the step helpers move one page and clamp
to the state's bounds:

```kotlin
scope.launch { state.animateScrollToNextMonth() }         // also: PreviousMonth, scrollTo* without animation
scope.launch { weekState.animateScrollToPreviousWeek() }  // one page of visibleWeeksCount weeks
```

Bounds are optional — the default state scrolls indefinitely in both
directions, and one-sided ranges (fixed past, open future) work too. Far
animated jumps teleport near the target before animating the landing, so
`animateScrollToMonth` five centuries away completes instantly.

`CalendarNavHeader` gives you prev/next chevrons around an animated title;
`MonthYearPicker` is a month/year/decade jump grid you can host in a dialog,
dropdown, or in place of the month grid:

```kotlin
CalendarNavHeader(
    state = state,
    onTitleClick = { showJumpPicker = true },
    expanded = showJumpPicker, // rotates the title's dropdown chevron
)

if (showJumpPicker) {
    MonthYearPicker(
        current = state.firstVisibleMonth,
        range = YearMonth(2020, 1)..YearMonth(2030, 12),
        onSelect = { month ->
            showJumpPicker = false
            scope.launch { state.scrollToMonth(month) }
        },
    )
}
```

Observe scrolling and selection as Flows — the sample's Agenda screen loads
events for whichever month scrolls into view:

```kotlin
LaunchedEffect(state) {
    state.visibleMonths().collect { month -> loadEvents(month) }
}
// also: WeekCalendarState.visibleWeeks(), CalendarSelectionState.selectionChanges()
```

## Utilities

- `currentDate()` / `currentYearMonth()` — today in the system time zone
- `firstDayOfWeekFromLocale()` — locale-preferred first day of the week
- `DayOfWeek.displayName(narrow)`, `Month.displayName(short)`,
  `YearMonth.displayName()` — localized names
- `LocalDate.isoWeekNumber()` (`orrery-core`) — ISO-8601 week of year
