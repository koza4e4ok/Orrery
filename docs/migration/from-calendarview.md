# Migrating from CalendarView

Orrery started as a rewrite of [huanghaibin-dev/CalendarView](https://github.com/huanghaibin-dev/CalendarView)
for Jetpack Compose and Compose Multiplatform. The ideas carry over, but the shape
of the API changes: instead of subclassing views and registering listeners, you pass
composable slots and read snapshot state.

Orrery is Compose-only. If your app still uses Views, you can host Orrery in a
`ComposeView`, or use [`orrery-core`](../guide/core-without-compose.md) for the grid
math and selection rules.

## API mapping

| CalendarView | Orrery |
| --- | --- |
| Subclass `MonthView` / `WeekView` / `RangeMonthView` / `MultiMonthView` and draw in `onDrawSelected`, `onDrawScheme`, `onDrawText` | The `dayContent` slot takes any composable. `DefaultDay` covers selection, range bands and today, and `DayDecorators` (or your own `DayDecorator` drawing in `DrawScope`) add marks. See [Day cells](../guide/day-cells.md). |
| `setSchemeDate` / `addSchemeDate` | A `DayInfoProvider` for the label under the day number, and `DayDecorators` (event dots, badges, progress rings) for marks, both keyed by `LocalDate` |
| `setSelectSingleMode` / `setSelectRangeMode` / `setSelectMultiMode` | `SelectionMode.Single` / `SelectionMode.Range` / `SelectionMode.Multi` |
| `setSelectRange(minRange, maxRange)` | `SelectionMode.Range(minDays, maxDays)` |
| `setMaxMultiSelectSize` | `SelectionMode.Multi(maxCount)` |
| `month_view_auto_select_day` | `SelectionMode.Single(autoAdvance = true, autoSelectDay = …)` |
| `setOnCalendarInterceptListener` (`onCalendarIntercept`) | `DisabledDates`: dates, ranges, days of the week, before/after and predicates. Disabled days render as unavailable and also block any range that would span them. |
| `onSelectOutOfRange` / `onSelectOutOfSize` | The `onEvent` callback of `rememberCalendarSelectionState`: `OutOfRange`, `Intercepted`, `RangeTooShort`, `RangeTooLong`, `TooManyDays` |
| `OnCalendarSelectListener` / `OnCalendarRangeSelectListener` / `OnCalendarMultiSelectListener` | Read `CalendarSelectionState.selection`, or collect `selectionChanges()` |
| `OnMonthChangeListener` | Collect `CalendarState.visibleMonths()` |
| `setRange(minYear, …, maxYear, …)` | `rememberCalendarState(startMonth, endMonth)`, or leave the bounds out to scroll without limits |
| `scrollToCalendar(year, month, day)` / `scrollToCurrent()` | `state.scrollToDate(date)` / `state.animateScrollToToday()` |
| `setWeekStarWithMon()` / `setWeekStarWithSun()` / `setWeekStarWithSat()` | `rememberCalendarState(firstDayOfWeek = …)`. The default follows the locale. |
| `CalendarLayout` with `calendar_content_view_id` | `CollapsibleCalendarScaffold`: the month collapses to a week row as its content scrolls |
| `YearView` and the year selection layout | `YearCalendar`, and `MonthYearPicker` for jumping to a month |
| Lunar text (`Calendar.getLunar()`), solar terms and festivals | The optional `orrery-lunar` module: `LunarDayInfoProvider`, `toLunarDate()`, `solarTermsFor()`. Its lunar date conversion is golden-tested against CalendarView's for every day of 1900–2099. See [Calendar systems](../guide/calendar-systems.md). |

## Example

A range picker with marked holidays, written the CalendarView way with
`setSelectRangeMode`, `setSelectRange`, `onCalendarIntercept`, `setSchemeDate` and
an `OnCalendarRangeSelectListener`, becomes:

```kotlin
@Composable
fun StayCalendar(
    holidays: Map<LocalDate, String>,
    fullyBooked: Set<LocalDate>,
    onRangeSelected: (ClosedRange<LocalDate>) -> Unit,
    onBlocked: (SelectionEvent) -> Unit,
) {
    val labels = remember(holidays) {
        object : DayInfoProvider {
            override fun info(date: LocalDate): DayInfo? =
                holidays[date]?.let { DayInfo(label = it, isHoliday = true) }
        }
    }
    val selection = rememberCalendarSelectionState(
        mode = SelectionMode.Range(minDays = 2, maxDays = 14),
        disabled = DisabledDates {
            before(currentDate())
            dates(fullyBooked)
        },
        onEvent = onBlocked, // e.g. Intercepted(date), RangeTooLong
    )
    LaunchedEffect(selection) {
        selection.selectionChanges().collect { it.range?.let(onRangeSelected) }
    }

    HorizontalCalendar(
        state = rememberCalendarState(firstDayOfWeek = DayOfWeek.MONDAY),
        dragSelection = selection,
    ) { day ->
        DefaultDay(day, selectionState = selection, info = labels.info(day.date))
    }
}
```
