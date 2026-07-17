# Orrery

[![Maven Central](https://img.shields.io/maven-central/v/me.kozakov.orrery/orrery-compose?label=Maven%20Central)](https://central.sonatype.com/artifact/me.kozakov.orrery/orrery-compose)
[![CI](https://github.com/koza4e4ok/Orrery/actions/workflows/ci.yml/badge.svg)](https://github.com/koza4e4ok/Orrery/actions/workflows/ci.yml)
[![API](https://img.shields.io/badge/API-23%2B-brightgreen.svg)](https://developer.android.com/studio/releases/platforms)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

**Orrery is an Android calendar library for Jetpack Compose** — a fully
composable month/week/year calendar with Material 3 theming, flexible selection, and an
optional Chinese lunisolar module. It's distributed as Maven Central artifacts you add to
your app's Gradle dependencies (see [Installation](#installation)); there's no view
subclassing or XML.

It's a full rewrite of [CalendarView](https://github.com/huanghaibin-dev/CalendarView)
with composable slots instead of view subclassing, snapshot state instead of listener
interfaces, and an optional Chinese lunisolar artifact.

**Requirements:** Android `minSdk 23` · Jetpack Compose (Material 3) · Kotlin · JDK 17.
Built on [`kotlinx-datetime`](https://github.com/Kotlin/kotlinx-datetime).

## Installation

The library ships three artifacts on **Maven Central** under the group
`me.kozakov.orrery`:

| Artifact                           | Contents                                                                   |
| ---------------------------------- | -------------------------------------------------------------------------- |
| `me.kozakov.orrery:orrery-core`    | Pure-Kotlin calendar models, grid math and selection engine                |
| `me.kozakov.orrery:orrery-compose` | Jetpack Compose calendar composables with Material3 theming                |
| `me.kozakov.orrery:orrery-lunar`   | Chinese lunisolar calendar, solar terms and festivals as a DayInfoProvider |

`orrery-compose` already depends on `orrery-core`, so most apps only need the two
lines below. Make sure Maven Central is in your repositories (usually in
`settings.gradle.kts` under `dependencyResolutionManagement`):

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("me.kozakov.orrery:orrery-compose:0.1.0")
    // Optional Chinese lunisolar labels:
    implementation("me.kozakov.orrery:orrery-lunar:0.1.0")
}
```

Using a [version catalog](https://docs.gradle.org/current/userguide/version_catalogs.html)
(`gradle/libs.versions.toml`)?

```toml
[versions]
orrery = "0.1.0"

[libraries]
orrery-compose = { module = "me.kozakov.orrery:orrery-compose", version.ref = "orrery" }
orrery-lunar   = { module = "me.kozakov.orrery:orrery-lunar",   version.ref = "orrery" }
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(libs.orrery.compose)
    implementation(libs.orrery.lunar) // optional
}
```

> **Snapshots:** development builds are published as `0.1.0-SNAPSHOT` to the Central
> snapshots repository. To use them, add
> `maven("https://central.sonatype.com/repository/maven-snapshots/")` to your repositories
> and depend on the `-SNAPSHOT` version.

## Quick start

```kotlin
@Composable
fun CalendarScreen() {
    val today = remember { currentDate() }
    val selection = rememberCalendarSelectionState(mode = SelectionMode.Range(maxDays = 14))
    val lunar = remember { LunarDayInfoProvider() } // optional, from orrery-lunar

    HorizontalCalendar(
        state = rememberCalendarState(),
        showWeekNumbers = true,
        dayContent = { day ->
            DefaultDay(
                day = day,
                selectionState = selection,
                today = today,
                info = lunar.info(day.date),
                decorators = listOf(
                    DayDecorators.eventDots { date -> eventColors[date].orEmpty() },
                    DayDecorators.progressRing { date -> habits[date] }, // 0f..1f
                ),
            )
        },
    )
}
```

The sample app (`sample/`) is four product-style screens: an Agenda with a
collapsible calendar and named events, a Trips booking flow with drag-select,
nightly prices and blackout dates, a Habits tracker with streaks and animated
progress rings, and a Chinese Almanac backed by `orrery-lunar`.

### Animating decorator data

Animate in composition, read the animated value inside the decorator lookup —
`rememberAnimatedDayValues` packages the idiom:

```kotlin
val animated = rememberAnimatedDayValues(habitProgress) // Map<LocalDate, Float>
DefaultDay(
    day = day,
    decorators = listOf(DayDecorators.progressRing(progress = animated)),
)
// when habitProgress[date] changes 0.4 -> 0.8, the ring eases over 300 ms
```

### Date picker dialogs

M3-shaped dialog wrappers — `OrreryDatePickerDialog` (compact, single date) and
`OrreryDateRangePickerDialog` (full-screen, drag-select range) — with the same
`onDismissRequest`/`confirmButton`/`dismissButton` slot API as
`androidx.compose.material3.DatePickerDialog`, plus a locale-aware text-input mode:

```kotlin
@Composable
fun PickDateButton() {
    var showDialog by remember { mutableStateOf(false) }
    val state = rememberOrreryDatePickerState()

    Button(onClick = { showDialog = true }) { Text("Pick date") }

    if (showDialog) {
        OrreryDatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = { showDialog = false },
                    enabled = state.selectedDate != null,
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } },
            state = state,
        )
    }
}
```

Read `state.selectedDate` (or `selectedStartDate`/`selectedEndDate` on the range state) in
your confirm button. Both dialogs localize entirely through `DatePickerStrings` — override
any message via `DatePickerDefaults.strings(...)`.

## Features

- Month (`HorizontalCalendar`), vertical list (`VerticalCalendar`), single-week
  (`WeekCalendar`) and year-overview (`YearCalendar`) composables
- `CollapsibleCalendarScaffold`: month collapses to a week row as the content scrolls
- Selection modes: single (with optional auto-advance), range (min/max), multi,
  plus long-press **drag-to-select**
- `OrreryDatePickerDialog` / `OrreryDateRangePickerDialog`: Material 3-shaped dialogs
  (slot-based confirm/dismiss buttons) with calendar and locale-aware text-input modes
- First-class unavailable dates: `DisabledDates` (dates, ranges, days of week,
  before/after, predicates) blocks selection and renders disabled automatically
- Per-state day styling: `CalendarDayColors` + `CalendarDayShapes` with
  today-indicator variants (ring, filled, underline), segmented or banded
  in-range fills, and optional selected-day elevation
- Stackable day decorators: event dots, progress rings/bars, strikethrough,
  underline, and a GitHub-style heatmap — plus a raw `DayDecorator` DrawScope
  escape hatch
- `CalendarNavHeader` prev/next navigation with animated title and
  `MonthYearPicker` decade/year/month jump picker
- Scroll-to-today, selection haptics, range-fill animation, and
  keyboard/D-pad navigation with month-edge paging
- Flow observation (`visibleMonths`, `selectionChanges`), selection presets
  with a validated programmatic setter, count badges, and animated
  decorator values via `rememberAnimatedDayValues`
- Material3 theming — dark mode and dynamic color for free; all colors overridable
- Accessibility semantics, RTL mirroring, locale-driven names and first day of week
- Week numbers (ISO by default, custom `weekNumber` slot) and sticky month headers
- `orrery-lunar`: Gregorian↔lunar conversion (golden-tested against the original
  for all 73,049 days of 1900–2099), astronomical 24 solar terms, traditional and
  Gregorian festivals, 干支 year names, zh-CN/zh-TW/zh-HK string variants

## Development

```bash
# Full verification (what CI runs):
./gradlew ktlintCheck detekt checkKotlinAbi \
  :orrery-core:test :orrery-lunar:test \
  :orrery-compose:testDebugUnitTest :orrery-compose:verifyRoborazziDebug \
  :sample:assembleDebug

./gradlew :orrery-compose:recordRoborazziDebug  # refresh screenshot goldens
./gradlew updateKotlinAbi                          # refresh ABI dumps after API changes
```

Releases are documented in [docs/RELEASING.md](docs/RELEASING.md).

## License

Apache License 2.0 — see [LICENSE](LICENSE). Ports algorithms and data tables from
[CalendarView](https://github.com/huanghaibin-dev/CalendarView) (Apache 2.0); see [NOTICE](NOTICE).
