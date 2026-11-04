# Orrery guides

Everything in these guides ships in `orrery-compose` unless marked otherwise. All dates are
[`kotlinx-datetime`](https://github.com/Kotlin/kotlinx-datetime) types (`LocalDate`,
`YearMonth`, `DayOfWeek`).

| Guide | Covers |
| --- | --- |
| [Calendars and navigation](calendars.md) | `HorizontalCalendar`, `VerticalCalendar`, `WeekCalendar`, `YearCalendar`, `CollapsibleCalendarScaffold`, calendar state, scrolling, `CalendarNavHeader`, `MonthYearPicker`, Flow observation, utilities |
| [Selection and disabled dates](selection.md) | Single, range, multi, week and month selection, drag-to-select, presets, booking guardrails, `DisabledDates` |
| [Day cells, decorators and heatmaps](day-cells.md) | `DefaultDay`, colors and shapes, event dots, progress rings, badges, custom `DayDecorator`s, `HeatmapCalendar`, `HeatmapMonth` |
| [Schedules and agenda](schedules.md) | `DaySchedule`, `WeekSchedule`, overlapping events, `Agenda` |
| [Date picker dialogs](pickers.md) | Date, date range (with presets), date-time and month pickers |
| [Calendar systems and holidays](calendar-systems.md) | `DayInfoProvider`, Chinese lunar calendar, Hijri, Persian, public holidays |
| [Using orrery-core without Compose](core-without-compose.md) | Grid math and the selection engine for View-based UIs or servers |

Not covered here yet: the 0.8.0 and 0.9.0 additions (two-week and work-week modes,
`WeekEventBanners`, hospitality range endpoints, `EventOverlapMode`, schedule hour
windows and `CalendarStyle` presets). See the [API reference](https://koza4e4ok.github.io/Orrery/)
and the [CHANGELOG](../../CHANGELOG.md) for those.

Coming from CalendarView? See the [migration guide](../migration/from-calendarview.md).
