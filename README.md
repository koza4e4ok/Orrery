# MaterialCalendar

[![CI](https://github.com/koza4e4ok/MaterialCalendar/actions/workflows/ci.yml/badge.svg)](https://github.com/koza4e4ok/MaterialCalendar/actions/workflows/ci.yml)

A modern Android calendar library: Kotlin, Jetpack Compose, Material3, kotlinx-datetime.
A full rewrite of [CalendarView](https://github.com/huanghaibin-dev/CalendarView) with
composable slots instead of view subclassing, snapshot state instead of listener
interfaces, and an optional Chinese lunisolar artifact.

## Modules

| Artifact                                           | Contents                                                                   |
| -------------------------------------------------- | -------------------------------------------------------------------------- |
| `dev.koza4e4ok.material.calendar:calendar-core`    | Pure-Kotlin calendar models, grid math and selection engine                |
| `dev.koza4e4ok.material.calendar:calendar-compose` | Jetpack Compose calendar composables with Material3 theming                |
| `dev.koza4e4ok.material.calendar:calendar-lunar`   | Chinese lunisolar calendar, solar terms and festivals as a DayInfoProvider |

```kotlin
dependencies {
    implementation("dev.koza4e4ok.material.calendar:calendar-compose:0.1.0-SNAPSHOT")
    // Optional lunar labels:
    implementation("dev.koza4e4ok.material.calendar:calendar-lunar:0.1.0-SNAPSHOT")
}
```

## Quick start

```kotlin
@Composable
fun CalendarScreen() {
    val today = remember { currentDate() }
    val selection = rememberCalendarSelectionState(mode = SelectionMode.Range(maxDays = 14))
    val lunar = remember { LunarDayInfoProvider() } // optional, from calendar-lunar

    HorizontalCalendar(
        state = rememberCalendarState(),
        showWeekNumbers = true,
        dayContent = { day ->
            DefaultDay(
                day = day,
                selectionState = selection,
                today = today,
                info = lunar.info(day.date),
                decorator = { d ->
                    // DrawScope escape hatch: event dots, heatmaps, progress rings…
                },
            )
        },
    )
}
```

The sample app (`sample/`) demonstrates an agenda screen with a collapsible
month/week calendar, a vertical calendar with sticky headers and drag-to-select,
and the lunar provider.

## Features

- Month (`HorizontalCalendar`), vertical list (`VerticalCalendar`), single-week
  (`WeekCalendar`) and year-overview (`YearCalendar`) composables
- `CollapsibleCalendarScaffold`: month collapses to a week row as the content scrolls
- Selection modes: single (with optional auto-advance), range (min/max), multi,
  plus long-press **drag-to-select**
- First-class unavailable dates: `DisabledDates` (dates, ranges, days of week,
  before/after, predicates) blocks selection and renders disabled automatically
- Per-state day styling: `CalendarDayColors` + `CalendarDayShapes` with
  today-indicator variants (ring, filled, underline)
- Material3 theming — dark mode and dynamic color for free; all colors overridable
- Accessibility semantics, RTL mirroring, locale-driven names and first day of week
- ISO week numbers and sticky month headers
- `calendar-lunar`: Gregorian↔lunar conversion (golden-tested against the original
  for all 73,049 days of 1900–2099), astronomical 24 solar terms, traditional and
  Gregorian festivals, 干支 year names, zh-CN/zh-TW/zh-HK string variants

## Development

```bash
# Full verification (what CI runs):
./gradlew ktlintCheck detekt checkKotlinAbi \
  :calendar-core:test :calendar-lunar:test \
  :calendar-compose:testDebugUnitTest :calendar-compose:verifyRoborazziDebug \
  :sample:assembleDebug

./gradlew :calendar-compose:recordRoborazziDebug  # refresh screenshot goldens
./gradlew updateKotlinAbi                          # refresh ABI dumps after API changes
```

Releases are documented in [docs/RELEASING.md](docs/RELEASING.md).

## License

Apache License 2.0 — see [LICENSE](LICENSE). Ports algorithms and data tables from
[CalendarView](https://github.com/huanghaibin-dev/CalendarView) (Apache 2.0); see [NOTICE](NOTICE).
