# Day cells, decorators and heatmaps

## Day cells and theming

`DefaultDay` renders selection fill, in-range band, today indicator, a secondary
label (`info`), and stacked decorators. Colors and shapes come from two
immutable holders with Material 3 defaults:

```kotlin
DefaultDay(
    day = day,
    selectionState = selection,
    info = DayInfo(label = "$99"),        // small label under the day number
    showOutDates = false,                 // hide leading/trailing month days
    selectedElevation = 2.dp,
    colors = CalendarDefaults.dayColors(
        selectedContainerColor = MaterialTheme.colorScheme.tertiary,
    ),
    shapes = CalendarDefaults.dayShapes(
        dayShape = RoundedCornerShape(12.dp),
        inRangeShape = RoundedCornerShape(12.dp), // non-rect = segmented per-day fill
        todayIndicator = TodayIndicator.FilledCircle, // or Ring(width), Underline, None
    ),
    onClick = { day -> /* optional, in addition to selection */ },
)
```

`CalendarDayColors` covers every state (today, selected, in-range, out-date,
unavailable, label, weekend); anything you don't override follows the Material theme, so
dark mode and dynamic color work for free. With `animateSelection = true` (the
default) the day number's color and the hover fill fade over 150 ms.

To tint weekends, set `weekendContentColor` on the day colors and on the week
header. `weekendDays` defaults to Saturday and Sunday; the weekend color defaults
to the regular one, so nothing changes until you set it. Selected, in-range,
out-date, unavailable and today cells keep their own colors:

```kotlin
val weekend = setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)
val colors = CalendarDefaults.dayColors(
    weekendContentColor = MaterialTheme.colorScheme.tertiary,
    weekendDays = weekend,
)

HorizontalCalendar(
    state = state,
    weekHeader = { days ->
        CalendarDefaults.WeekHeader(
            days,
            weekendDays = weekend,
            weekendContentColor = MaterialTheme.colorScheme.tertiary,
        )
    },
) { day -> DefaultDay(day, colors = colors) }
```
 The week header, week-number cell,
and month title are also slots — the defaults are `CalendarDefaults.WeekHeader`,
`CalendarDefaults.WeekNumber` (ISO week), and `CalendarDefaults.MonthTitle`.

## Decorators

Decorators draw data onto day cells without per-cell composables — they are
plain `DrawScope` lambdas keyed by date, stackable in one list:

```kotlin
val decorators = listOf(
    DayDecorators.eventDots { date -> eventColors[date].orEmpty() },
    DayDecorators.progressRing { date -> habitProgress[date] },   // 0f..1f
    DayDecorators.progressBar { date -> downloadProgress[date] },
    DayDecorators.badge { date -> unreadCount(date) },            // count bubble
    DayDecorators.heatmap { date -> activity[date] },             // GitHub-style
    DayDecorators.strikethrough { date -> date in cancelled },
    DayDecorators.underline { date -> date in deadlines },
)

DefaultDay(day = day, decorators = decorators)
```

Most factories are `@Composable` (they read theme colors) — call them once
outside `dayContent` and remember the list, not inside each cell. For anything
else, implement `DayDecorator` directly and draw with the full `DrawScope`;
`layer` picks `Behind` or `Over` the day number.

To animate decorator data, animate in composition and read the animated value in
the lookup — `rememberAnimatedDayValues` packages the idiom:

```kotlin
val animated = rememberAnimatedDayValues(habitProgress) // Map<LocalDate, Float>
DefaultDay(
    day = day,
    decorators = listOf(DayDecorators.progressRing(progress = animated)),
)
// when habitProgress[date] changes 0.4 -> 0.8, the ring eases over 300 ms
```

## Heatmaps

`HeatmapCalendar` is the GitHub-style contribution graph: one column per week
over any date range, scrolled to the most recent data, with month labels,
weekday labels, a legend, and a summary slot ("N active days · M-day streak",
computed by orrery-core's pure `heatmapSummary()`). `HeatmapMonth` is a static
month grid of the same tiles for dashboards. Both take the decorator-style
`intensity` lookup — null means no data — and an optional tap callback:

```kotlin
HeatmapCalendar(
    intensity = { date -> activity[date] },          // 0f..1f, null = empty cell
    state = rememberHeatmapCalendarState(),          // trailing 365 days by default
    onDayClick = { date -> showDetail(date) },
    strings = HeatmapDefaults.strings(legendLess = "Weniger", legendMore = "Mehr"),
)

HeatmapMonth(yearMonth = YearMonth(2026, 7), intensity = { activity[it] }, showDayNumbers = true)
```

`HeatmapMonth` has no legend by default; pass `showLegend = true` for the same
"Less … More" row that `HeatmapCalendar` draws, built from `strings.legendLess` /
`legendMore`, the `colorScale` and the `emptyCellColor`.

The default color scale quantizes into `HeatmapDefaults.Levels` (five) buckets
from `surfaceVariant` to `primary`. Level 1 starts 35% of the way to `primary`,
so a day with a little activity stands apart from an empty one, and the top level
is `primary` itself. Pass any `colorScale: (Float) -> Color` (a continuous lerp
works too). Cell accessibility descriptions bucket intensity into the same five
spoken levels; `HeatmapDefaults.levelOf(value)` returns that level, so a custom
scale or a tooltip can use the exact boundaries the cells announce.

Neighbouring levels are close in color: with the baseline Material 3 schemes,
adjacent levels differ by less than 3:1, and level 1 against an empty cell is
about 1.9:1 (light) and 3:1 (dark). Color alone does not make the heatmap
accessible; the spoken level in `dayDescription` does, so keep it meaningful.

For data where more is worse (errors, pain, load), or for a second heatmap on the
same screen, ramp toward another color and say what the level means:

```kotlin
HeatmapCalendar(
    intensity = { date -> errorRate[date] },
    colorScale = HeatmapDefaults.colorScale(to = MaterialTheme.colorScheme.tertiary),
    strings = HeatmapDefaults.strings(
        dayDescription = "%1$s, level %2$d of %3$d, higher means more errors",
    ),
)
```
