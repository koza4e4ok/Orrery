# Schedules and agenda

## Time-grid schedules

`DaySchedule` pages one day per swipe and `WeekSchedule` one week (`daysPerPage`,
default 7) per swipe. Both take the same `CalendarEvent` lookup as `Agenda`, share
a `ScheduleState` (visible date, vertical scroll, zoomable `hourHeight`), and
draw the current-time line only when you pass `currentTime`, so tests and
screenshots stay deterministic:

```kotlin
val events = remember(allEvents) { allEvents.eventsByDate(start..end) }
val state = rememberScheduleState(initialScrollTime = LocalTime(8, 0))

WeekSchedule(
    events = { date -> events[date].orEmpty() },
    state = state,
    currentTime = now, // from your own clock; null hides the line
    onEventClick = { event -> /* open */ },
    onEmptySlotClick = { date, time -> /* create at date/time */ },
)

scope.launch { state.scrollToTime(LocalTime(14, 0), density) }
```

Overlapping timed events split the column into lanes and widen into free lanes
to their right; overnight events are clipped to each day. The same packing is
available without Compose as `layoutDaySchedule(date, events)` in `orrery-core`.
Colors, sizes and every slot (day header, all-day chip, hour label, now line,
event tile) can be replaced through `ScheduleDefaults`.

## Agenda

`Agenda` is a vertically scrolling list of `CalendarEvent`s grouped by date, with
sticky day headers. It takes the same `events` lookup as the schedules, so one map
can feed a month calendar, the agenda and the time grid:

```kotlin
val events = remember(allEvents) { allEvents.eventsByDate(start..end) }
val agendaState = rememberAgendaState(startDate = start, endDate = end) // opens on today

Agenda(
    events = { date -> events[date].orEmpty() },
    state = agendaState,
    onEventClick = { event -> /* open */ },
)

scope.launch { agendaState.animateScrollToDate(date) } // e.g. when a day is tapped above
```

`showEmptyDays = true` also lists days without events. The day header, event row,
empty-day and empty-range content are slots with defaults in `AgendaDefaults`, and
`AgendaDefaults.strings()` localizes the built-in text. `rememberAgendaState`
defaults to a two-year window around today. To mark the same events on a month
calendar, use `DayDecorators.eventDotsForEvents`; to collapse the month into a week
row as the agenda scrolls, host it in `CollapsibleCalendarScaffold`
(see [Calendars](calendars.md)).
