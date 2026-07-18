# HeatmapCalendar (0.4.0) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship Orrery 0.4.0: `HeatmapCalendar` (GitHub-style week-column contribution graph) and `HeatmapMonth` (static month-grid heat tiles), with streak math in orrery-core.

**Architecture:** `HeatmapCalendarState` mirrors `WeekCalendarState` (a `LazyListState` wrapper indexed by `CalendarPages` week math). `HeatmapCalendar` is a `LazyRow` of week columns — each item is a Column of a month-label line plus 7 tile cells — flanked by a pinned weekday-label column and a summary/legend footer. `HeatmapMonth` is a static grid over core's `monthGrid()`. Streaks come from a new pure core function `heatmapSummary()`.

**Tech Stack:** Kotlin, Jetpack Compose (material3 via BOM), kotlinx-datetime, Roborazzi + Robolectric, compose-ui-test.

**Spec:** `docs/superpowers/specs/2026-07-18-heatmap-calendar-design.md`

## Global Constraints

- Explicit API mode: `public` modifier + KDoc on all public declarations, matching surrounding code.
- No new dependencies.
- Work on branch `feature/0.4.0-heatmap`; Conventional Commits; never mention Claude/Anthropic in commits.
- Module-scoped Gradle tasks only.
- Core tests: `./gradlew :orrery-core:test --tests "<class>"`. Compose tests: `./gradlew :orrery-compose:testDebugUnitTest --tests "<class>"`. Record goldens: `:orrery-compose:recordRoborazziDebug --tests "<class>"`; regression check is `:orrery-compose:verifyRoborazziDebug` (plain testDebugUnitTest does NOT compare goldens).
- orrery-core public API changes require `./gradlew updateKotlinAbi` and committing the dump.
- Snapshot conventions (`SnapshotTest.kt`): `@GraphicsMode(GraphicsMode.Mode.NATIVE)`, `@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)`, `MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme())` inside `Surface`, `+night` for dark variants, capture `rule.onRoot().captureRoboImage()` (no dialogs in this release).
- Fixed reference dates: heatmap range `LocalDate(2025, 7, 16)..LocalDate(2026, 7, 15)`; `firstDayOfWeek = DayOfWeek.MONDAY` in tests (locale-independent assertions); en-US locale for label text.
- A11y level bucketing is fixed at 5 levels (`min(5, floor(clamped * 5) + 1)`) regardless of a custom `colorScale` — an internal constant, documented in KDoc.
- Full CI verification: `./gradlew ktlintCheck detekt checkKotlinAbi :orrery-core:test :orrery-lunar:test :orrery-compose:testDebugUnitTest :orrery-compose:verifyRoborazziDebug :sample:assembleDebug`.

## File Structure

| File                                                                                                                                                                   | Change                                              |
| ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------- |
| `orrery-core/src/main/kotlin/me/kozakov/orrery/core/HeatmapSummary.kt`                                                                                                 | Create — `HeatmapSummary` + `heatmapSummary()`      |
| `orrery-core/src/test/kotlin/me/kozakov/orrery/core/HeatmapSummaryTest.kt`                                                                                             | Create                                              |
| `orrery-core/api/orrery-core.api`                                                                                                                                      | Regenerate (`updateKotlinAbi`)                      |
| `orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/HeatmapDefaults.kt`                                                                                          | Create — `HeatmapStrings`, `HeatmapDefaults`        |
| `orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/HeatmapCalendar.kt`                                                                                          | Create — `HeatmapCalendarState` + `HeatmapCalendar` |
| `orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/HeatmapMonth.kt`                                                                                             | Create — `HeatmapMonth`                             |
| `orrery-compose/src/test/.../HeatmapDefaultsTest.kt`, `HeatmapCalendarTest.kt`, `HeatmapCalendarSnapshotTest.kt`, `HeatmapMonthTest.kt`, `HeatmapMonthSnapshotTest.kt` | Create                                              |
| `sample/src/main/kotlin/me/kozakov/orrery/sample/HabitsScreen.kt`                                                                                                      | Modify — "Year in review" + month heat cards        |
| `CHANGELOG.md`, `README.md`, `gradle.properties`                                                                                                                       | Release prep                                        |

---

### Task 0: Branch

- [ ] `git checkout -b feature/0.4.0-heatmap` (from up-to-date `main`).

---

### Task 1: `heatmapSummary` in orrery-core

**Files:**

- Create: `orrery-core/src/main/kotlin/me/kozakov/orrery/core/HeatmapSummary.kt`
- Test: `orrery-core/src/test/kotlin/me/kozakov/orrery/core/HeatmapSummaryTest.kt`
- Regenerate: `orrery-core/api/orrery-core.api`

**Interfaces:**

- Consumes: kotlinx-datetime `LocalDate`, `DateTimeUnit`, `plus`.
- Produces (used by Task 3): `public data class HeatmapSummary(activeDays: Int, currentStreak: Int, longestStreak: Int)`; `public fun heatmapSummary(range: ClosedRange<LocalDate>, isActive: (LocalDate) -> Boolean): HeatmapSummary`.

- [ ] **Step 1: Write the failing tests**

Create `HeatmapSummaryTest.kt` (match the assertion style of the module's existing tests — see `SelectionEngineReplaceTest.kt` imports; the bodies below use kotlin.test):

```kotlin
package me.kozakov.orrery.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class HeatmapSummaryTest {
    private val jul1 = LocalDate(2026, 7, 1)
    private val jul10 = LocalDate(2026, 7, 10)

    @Test
    fun emptyRangeIsAllZero() {
        val summary = heatmapSummary(jul10..jul1) { true } // start > end
        assertEquals(HeatmapSummary(0, 0, 0), summary)
    }

    @Test
    fun noActiveDays() {
        val summary = heatmapSummary(jul1..jul10) { false }
        assertEquals(HeatmapSummary(0, 0, 0), summary)
    }

    @Test
    fun allActiveDays() {
        val summary = heatmapSummary(jul1..jul10) { true }
        assertEquals(HeatmapSummary(10, 10, 10), summary)
    }

    @Test
    fun gapSplitsStreaks() {
        // active: 1..4 (4 days), 6..7 (2 days); 8..10 inactive
        val active = setOf(1, 2, 3, 4, 6, 7).map { LocalDate(2026, 7, it) }.toSet()
        val summary = heatmapSummary(jul1..jul10) { it in active }
        assertEquals(HeatmapSummary(activeDays = 6, currentStreak = 0, longestStreak = 4), summary)
    }

    @Test
    fun currentStreakEndsAtRangeEnd() {
        // active: 8, 9, 10 — streak of 3 touching endInclusive
        val active = setOf(8, 9, 10).map { LocalDate(2026, 7, it) }.toSet()
        val summary = heatmapSummary(jul1..jul10) { it in active }
        assertEquals(HeatmapSummary(activeDays = 3, currentStreak = 3, longestStreak = 3), summary)
    }

    @Test
    fun singleDayRange() {
        assertEquals(HeatmapSummary(1, 1, 1), heatmapSummary(jul1..jul1) { true })
        assertEquals(HeatmapSummary(0, 0, 0), heatmapSummary(jul1..jul1) { false })
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :orrery-core:test --tests "me.kozakov.orrery.core.HeatmapSummaryTest"`
Expected: FAIL — compile error, `heatmapSummary` unresolved.

- [ ] **Step 3: Implement**

Create `HeatmapSummary.kt`:

```kotlin
package me.kozakov.orrery.core

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Aggregates for a heatmap range: how many days were active, the streak
 * ending at the range end, and the longest streak anywhere in the range.
 */
public data class HeatmapSummary(
    public val activeDays: Int,
    public val currentStreak: Int,
    public val longestStreak: Int,
)

/**
 * Single-pass scan of [range]. [currentStreak] counts consecutive active
 * days ending exactly at `range.endInclusive` (zero when that day is
 * inactive). An empty range (start after end) yields all zeros.
 */
public fun heatmapSummary(
    range: ClosedRange<LocalDate>,
    isActive: (LocalDate) -> Boolean,
): HeatmapSummary {
    var activeDays = 0
    var longest = 0
    var run = 0
    var date = range.start
    while (date <= range.endInclusive) {
        if (isActive(date)) {
            activeDays++
            run++
            if (run > longest) longest = run
        } else {
            run = 0
        }
        date = date.plus(1, DateTimeUnit.DAY)
    }
    return HeatmapSummary(activeDays = activeDays, currentStreak = run, longestStreak = longest)
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :orrery-core:test --tests "me.kozakov.orrery.core.HeatmapSummaryTest"`
Expected: PASS (all 6).

- [ ] **Step 5: Update the ABI dump, lint, commit**

Run: `./gradlew updateKotlinAbi` then `./gradlew :orrery-core:ktlintCheck :orrery-core:detekt`

```bash
git add orrery-core/src/main/kotlin/me/kozakov/orrery/core/HeatmapSummary.kt orrery-core/src/test/kotlin/me/kozakov/orrery/core/HeatmapSummaryTest.kt orrery-core/api/orrery-core.api
git commit -m "feat(core): add heatmapSummary streak aggregation"
```

---

### Task 2: `HeatmapStrings` + `HeatmapDefaults`

**Files:**

- Create: `orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/HeatmapDefaults.kt`
- Test: `orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/HeatmapDefaultsTest.kt`

**Interfaces:**

- Consumes: Task 1's `HeatmapSummary`; `MaterialTheme.colorScheme`; `androidx.compose.ui.graphics.lerp`.
- Produces (used by Tasks 3, 4):
  - `@Immutable public class HeatmapStrings(legendLess, legendMore, dayDescription, dayDescriptionEmpty, summaryActiveDays, summaryStreak: String)`
  - `public object HeatmapDefaults` with `@Composable colorScale(levels: Int = 5): (Float) -> Color`, `@Composable emptyCellColor(): Color`, `strings(...): HeatmapStrings` (defaults: legendLess = "Less", legendMore = "More", dayDescription = "%1$s, level %2$d of %3$d", dayDescriptionEmpty = "%1$s, no data", summaryActiveDays = "%d active days", summaryStreak = "%d-day streak"), `@Composable Summary(summary: HeatmapSummary, strings: HeatmapStrings = strings())`.
  - `internal const val HeatmapLevels = 5` (a11y bucketing) and `internal fun heatLevel(value: Float): Int = min(HeatmapLevels, (value.coerceIn(0f, 1f) * HeatmapLevels).toInt() + 1)`.

- [ ] **Step 1: Write the failing tests**

Create `HeatmapDefaultsTest.kt`:

```kotlin
package me.kozakov.orrery.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.kozakov.orrery.core.HeatmapSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeatmapDefaultsTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun colorScaleQuantizesIntoLevels() {
        lateinit var scale: (Float) -> Color
        rule.setContent { MaterialTheme { scale = HeatmapDefaults.colorScale(levels = 5) } }
        rule.runOnIdle {
            assertEquals(scale(0.0f), scale(0.19f))    // same bucket
            assertNotEquals(scale(0.19f), scale(0.21f)) // bucket boundary at 0.2
            assertEquals(scale(1.0f), scale(0.81f))
            assertEquals(scale(1.0f), scale(2f))        // clamped
        }
    }

    @Test
    fun heatLevelBuckets() {
        assertEquals(1, heatLevel(0f))
        assertEquals(3, heatLevel(0.45f))
        assertEquals(5, heatLevel(1f))
        assertEquals(5, heatLevel(9f))
        assertEquals(1, heatLevel(-1f))
    }

    @Test
    fun summaryRendersCounters() {
        rule.setContent {
            MaterialTheme {
                HeatmapDefaults.Summary(HeatmapSummary(activeDays = 42, currentStreak = 7, longestStreak = 9))
            }
        }
        rule.onNodeWithText("42 active days · 7-day streak").assertExists()
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :orrery-compose:testDebugUnitTest --tests "me.kozakov.orrery.compose.HeatmapDefaultsTest"`
Expected: FAIL — compile error, `HeatmapDefaults` unresolved.

- [ ] **Step 3: Implement**

Create `HeatmapDefaults.kt`:

```kotlin
package me.kozakov.orrery.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import me.kozakov.orrery.core.HeatmapSummary
import kotlin.math.min

/** Number of a11y/legend buckets; fixed regardless of a custom color scale. */
internal const val HeatmapLevels: Int = 5

/** 1-based bucket of a clamped 0..1 intensity: 0f -> 1, 1f -> [HeatmapLevels]. */
internal fun heatLevel(value: Float): Int =
    min(HeatmapLevels, (value.coerceIn(0f, 1f) * HeatmapLevels).toInt() + 1)

/**
 * Localizable strings for the heatmap composables. Defaults are English;
 * callers localize by passing their own values. Templates use
 * `String.format` ordering: date, level, max level.
 */
@Immutable
public class HeatmapStrings(
    public val legendLess: String,
    public val legendMore: String,
    public val dayDescription: String,
    public val dayDescriptionEmpty: String,
    public val summaryActiveDays: String,
    public val summaryStreak: String,
)

/** Defaults for [HeatmapCalendar] and [HeatmapMonth]. */
public object HeatmapDefaults {
    /**
     * Quantized color scale: 0..1 split into [levels] buckets, each lerped
     * from `surfaceVariant` toward `primary`. Values are clamped.
     */
    @Composable
    public fun colorScale(levels: Int = HeatmapLevels): (Float) -> Color {
        val from = MaterialTheme.colorScheme.surfaceVariant
        val to = MaterialTheme.colorScheme.primary
        return remember(levels, from, to) {
            { value ->
                val clamped = value.coerceIn(0f, 1f)
                val bucket = min(levels - 1, (clamped * levels).toInt())
                lerp(from, to, (bucket + 1).toFloat() / levels)
            }
        }
    }

    /** Cell color when a day has no data. */
    @Composable
    public fun emptyCellColor(): Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

    /** English-default strings; override any parameter to localize. */
    public fun strings(
        legendLess: String = "Less",
        legendMore: String = "More",
        dayDescription: String = "%1${'$'}s, level %2${'$'}d of %3${'$'}d",
        dayDescriptionEmpty: String = "%1${'$'}s, no data",
        summaryActiveDays: String = "%d active days",
        summaryStreak: String = "%d-day streak",
    ): HeatmapStrings =
        HeatmapStrings(
            legendLess = legendLess,
            legendMore = legendMore,
            dayDescription = dayDescription,
            dayDescriptionEmpty = dayDescriptionEmpty,
            summaryActiveDays = summaryActiveDays,
            summaryStreak = summaryStreak,
        )

    /** Default summary line: "N active days · M-day streak". */
    @Composable
    public fun Summary(
        summary: HeatmapSummary,
        strings: HeatmapStrings = strings(),
    ) {
        Text(
            text =
                strings.summaryActiveDays.format(summary.activeDays) +
                    " · " +
                    strings.summaryStreak.format(summary.currentStreak),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :orrery-compose:testDebugUnitTest --tests "me.kozakov.orrery.compose.HeatmapDefaultsTest"`
Expected: PASS (all 3).

- [ ] **Step 5: Lint and commit**

Run: `./gradlew :orrery-compose:ktlintCheck :orrery-compose:detekt`

```bash
git add orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/HeatmapDefaults.kt orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/HeatmapDefaultsTest.kt
git commit -m "feat(compose): add heatmap strings, color scale and summary defaults"
```

---

### Task 3: `HeatmapCalendarState` + `HeatmapCalendar`

**Files:**

- Create: `orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/HeatmapCalendar.kt`
- Test: `orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/HeatmapCalendarTest.kt`

**Interfaces:**

- Consumes: `CalendarPages.weekStart/weekIndex/weekCount` (core), Task 1 `heatmapSummary`/`HeatmapSummary`, Task 2 `HeatmapDefaults`/`HeatmapStrings`/`heatLevel`/`HeatmapLevels`, `firstDayOfWeekFromLocale()`, `currentDate()`, `DayOfWeek.displayName(narrow = true)`.
- Produces (used by Tasks 5, 6):
  - `@Stable public class HeatmapCalendarState` with `startDate`, `endDate`, `firstDayOfWeek`, `weekCount`, `firstVisibleWeekStart: LocalDate`, suspend `scrollToDate`/`animateScrollToDate`, `companion Saver`, `internal listState`, `internal fun weekStartAt(index: Int): LocalDate`.
  - `@Composable public fun rememberHeatmapCalendarState(startDate = currentDate().minus(364, DateTimeUnit.DAY), endDate = currentDate(), firstVisibleDate = endDate, firstDayOfWeek = firstDayOfWeekFromLocale())`.
  - `@Composable public fun HeatmapCalendar(intensity, state, modifier, onDayClick, colorScale, emptyCellColor, cellSize = 12.dp, cellSpacing = 2.dp, cellShape = RoundedCornerShape(2.dp), showMonthLabels = true, showWeekdayLabels = true, showLegend = true, summary = { HeatmapDefaults.Summary(it) }, strings = HeatmapDefaults.strings())`.

- [ ] **Step 1: Write the failing tests**

Create `HeatmapCalendarTest.kt`. Shared scaffolding: range `LocalDate(2025, 7, 16)..LocalDate(2026, 7, 15)`, `firstDayOfWeek = DayOfWeek.MONDAY`, intensity `{ date -> if (date.day % 5 == 0) null else (date.day % 5) / 4f }`. Cell descriptions use `DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)` (en-US → "Jul 8, 2026"). July 8, 2026: `8 % 5 = 3` → intensity 0.75f → level `min(5, (0.75*5).toInt()+1) = 4`.

```kotlin
package me.kozakov.orrery.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeatmapCalendarTest {
    @get:Rule val rule = createComposeRule()

    private val start = LocalDate(2025, 7, 16)
    private val end = LocalDate(2026, 7, 15)
    private val strings = HeatmapDefaults.strings()

    private fun intensity(date: LocalDate): Float? =
        if (date.day % 5 == 0) null else (date.day % 5) / 4f

    private lateinit var state: HeatmapCalendarState

    private fun content(
        onDayClick: ((LocalDate) -> Unit)? = null,
        showMonthLabels: Boolean = true,
        showWeekdayLabels: Boolean = true,
        showLegend: Boolean = true,
    ) {
        rule.setContent {
            MaterialTheme {
                state =
                    rememberHeatmapCalendarState(
                        startDate = start,
                        endDate = end,
                        firstDayOfWeek = DayOfWeek.MONDAY,
                    )
                HeatmapCalendar(
                    intensity = ::intensity,
                    state = state,
                    onDayClick = onDayClick,
                    showMonthLabels = showMonthLabels,
                    showWeekdayLabels = showWeekdayLabels,
                    showLegend = showLegend,
                )
            }
        }
    }

    @Test
    fun startsScrolledToRangeEnd() {
        content()
        rule.runOnIdle {
            // last visible week is the one containing endDate; first visible follows suit
            assertEquals(state.weekCount - 1, state.listState.firstVisibleItemIndex + state.listState.layoutInfo.visibleItemsInfo.size - 1)
        }
    }

    @Test
    fun clickReportsDate() {
        var clicked: LocalDate? = null
        content(onDayClick = { clicked = it })
        rule.onNodeWithContentDescription("Jul 8, 2026, level 4 of 5").performClick()
        rule.runOnIdle { assertEquals(LocalDate(2026, 7, 8), clicked) }
    }

    @Test
    fun emptyCellDescribesNoData() {
        content(onDayClick = {})
        // Jul 10, 2026 -> 10 % 5 == 0 -> null intensity
        rule.onNodeWithContentDescription("Jul 10, 2026, no data").assertExists()
    }

    @Test
    fun cellsNotClickableWithoutCallback() {
        content(onDayClick = null)
        rule.onNodeWithContentDescription("Jul 8, 2026, level 4 of 5").assertHasNoClickAction()
    }

    @Test
    fun cellsClickableWithCallback() {
        content(onDayClick = {})
        rule.onNodeWithContentDescription("Jul 8, 2026, level 4 of 5").assertHasClickAction()
    }

    @Test
    fun chromeTogglesOff() {
        content(showMonthLabels = false, showWeekdayLabels = false, showLegend = false)
        rule.onAllNodesWithText("Jul").assertCountEquals(0)
        rule.onAllNodesWithText("Mon").assertCountEquals(0)
        rule.onAllNodesWithText(strings.legendLess).assertCountEquals(0)
    }

    @Test
    fun legendAndSummaryRender() {
        content()
        rule.onNodeWithText(strings.legendLess).assertExists()
        rule.onNodeWithText(strings.legendMore).assertExists()
        // summary slot default renders "N active days · M-day streak"
        rule.onAllNodesWithText("active days", substring = true).assertCountEquals(1)
    }

    @Test
    fun scrollToDateMovesWindow() {
        content()
        rule.waitForIdle()
        kotlinx.coroutines.runBlocking { state.scrollToDate(start) }
        rule.runOnIdle { assertEquals(state.weekStartAt(0), state.firstVisibleWeekStart) }
    }

    @Test
    fun stateSurvivesRestoration() {
        val restorationTester = StateRestorationTester(rule)
        restorationTester.setContent {
            MaterialTheme {
                state =
                    rememberHeatmapCalendarState(
                        startDate = start,
                        endDate = end,
                        firstDayOfWeek = DayOfWeek.MONDAY,
                    )
                HeatmapCalendar(intensity = ::intensity, state = state)
            }
        }
        kotlinx.coroutines.runBlocking { state.scrollToDate(LocalDate(2026, 1, 15)) }
        val expected = rule.runOnIdle { state.firstVisibleWeekStart }
        restorationTester.emulateSavedInstanceStateRestore()
        rule.runOnIdle { assertEquals(expected, state.firstVisibleWeekStart) }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :orrery-compose:testDebugUnitTest --tests "me.kozakov.orrery.compose.HeatmapCalendarTest"`
Expected: FAIL — compile error, `HeatmapCalendar` unresolved.

- [ ] **Step 3: Implement**

Create `HeatmapCalendar.kt`. State first — mirror `WeekCalendarState` exactly (same file layout, saver shape, index coercion):

```kotlin
package me.kozakov.orrery.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaLocalDate
import me.kozakov.orrery.core.CalendarPages
import me.kozakov.orrery.core.HeatmapSummary
import me.kozakov.orrery.core.heatmapSummary
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** State holder for [HeatmapCalendar]. */
@Stable
public class HeatmapCalendarState internal constructor(
    startDate: LocalDate,
    endDate: LocalDate,
    firstVisibleDate: LocalDate,
    firstDayOfWeek: DayOfWeek,
) {
    public var startDate: LocalDate by mutableStateOf(startDate)
        private set
    public var endDate: LocalDate by mutableStateOf(endDate)
        private set
    public var firstDayOfWeek: DayOfWeek by mutableStateOf(firstDayOfWeek)
        private set

    internal val listState: LazyListState =
        LazyListState(firstVisibleItemIndex = indexOf(firstVisibleDate))

    public val weekCount: Int
        get() = CalendarPages.weekCount(startDate, endDate, firstDayOfWeek)

    /** Start-of-week date of the first visible column. Snapshot-observable. */
    public val firstVisibleWeekStart: LocalDate
        get() = weekStartAt(listState.firstVisibleItemIndex)

    internal fun weekStartAt(index: Int): LocalDate =
        CalendarPages.weekStart(startDate, firstDayOfWeek).plus(index * 7, DateTimeUnit.DAY)

    public suspend fun scrollToDate(date: LocalDate) {
        listState.scrollToItem(indexOf(date))
    }

    public suspend fun animateScrollToDate(date: LocalDate) {
        listState.animateScrollToItem(indexOf(date))
    }

    private fun indexOf(date: LocalDate): Int =
        CalendarPages.weekIndex(startDate, date, firstDayOfWeek).coerceIn(0, weekCount - 1)

    public companion object {
        public val Saver: Saver<HeatmapCalendarState, Any> =
            listSaver(
                save = {
                    listOf(
                        it.startDate.toString(),
                        it.endDate.toString(),
                        it.firstVisibleWeekStart.toString(),
                        it.firstDayOfWeek.ordinal,
                    )
                },
                restore = {
                    HeatmapCalendarState(
                        startDate = LocalDate.parse(it[0] as String),
                        endDate = LocalDate.parse(it[1] as String),
                        firstVisibleDate = LocalDate.parse(it[2] as String),
                        firstDayOfWeek = DayOfWeek.entries[it[3] as Int],
                    )
                },
            )
    }
}

/**
 * Remembers saveable [HeatmapCalendarState]. Defaults to the trailing 365
 * days ending today, scrolled to the range end (most recent data first).
 */
@Composable
public fun rememberHeatmapCalendarState(
    startDate: LocalDate = currentDate().minus(364, DateTimeUnit.DAY),
    endDate: LocalDate = currentDate(),
    firstVisibleDate: LocalDate = endDate,
    firstDayOfWeek: DayOfWeek = firstDayOfWeekFromLocale(),
): HeatmapCalendarState =
    rememberSaveable(saver = HeatmapCalendarState.Saver) {
        HeatmapCalendarState(startDate, endDate, firstVisibleDate, firstDayOfWeek)
    }
```

Then the composable, in the same file:

```kotlin
/**
 * GitHub-style contribution graph: one column per week, seven tile rows,
 * horizontally scrollable across the state's date range. [intensity]
 * returns a 0..1 heat value per day, or null for no data. Cells are
 * clickable only when [onDayClick] is set. Accessibility descriptions
 * bucket intensity into five levels regardless of [colorScale].
 */
@Composable
public fun HeatmapCalendar(
    intensity: (LocalDate) -> Float?,
    state: HeatmapCalendarState = rememberHeatmapCalendarState(),
    modifier: Modifier = Modifier,
    onDayClick: ((LocalDate) -> Unit)? = null,
    colorScale: (Float) -> Color = HeatmapDefaults.colorScale(),
    emptyCellColor: Color = HeatmapDefaults.emptyCellColor(),
    cellSize: Dp = 12.dp,
    cellSpacing: Dp = 2.dp,
    cellShape: Shape = RoundedCornerShape(2.dp),
    showMonthLabels: Boolean = true,
    showWeekdayLabels: Boolean = true,
    showLegend: Boolean = true,
    summary: (@Composable (HeatmapSummary) -> Unit)? = { HeatmapDefaults.Summary(it) },
    strings: HeatmapStrings = HeatmapDefaults.strings(),
) {
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val labelHeight = if (showMonthLabels) 16.dp else 0.dp
    Column(modifier) {
        Row {
            if (showWeekdayLabels) {
                WeekdayLabelColumn(
                    firstDayOfWeek = state.firstDayOfWeek,
                    cellSize = cellSize,
                    cellSpacing = cellSpacing,
                    topOffset = labelHeight,
                )
            }
            LazyRow(state = state.listState) {
                items(count = state.weekCount) { index ->
                    WeekColumn(
                        state = state,
                        index = index,
                        intensity = intensity,
                        onDayClick = onDayClick,
                        colorScale = colorScale,
                        emptyCellColor = emptyCellColor,
                        cellSize = cellSize,
                        cellSpacing = cellSpacing,
                        cellShape = cellShape,
                        showMonthLabels = showMonthLabels,
                        labelHeight = labelHeight,
                        dateFormatter = dateFormatter,
                        strings = strings,
                    )
                }
            }
        }
        if (showLegend || summary != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (summary != null) {
                    val computed =
                        heatmapSummary(state.startDate..state.endDate) { (intensity(it) ?: 0f) > 0f }
                    summary(computed)
                }
                Spacer(Modifier.weight(1f))
                if (showLegend) {
                    Legend(colorScale, emptyCellColor, cellSize, cellSpacing, cellShape, strings)
                }
            }
        }
    }
}
```

Private helpers (same file): `WeekColumn` renders the optional month-label line then 7 cells; `monthLabelFor(state, index)` returns the short month name (`Month.displayName(short = true)`) when this week contains the 1st of a month AND the column of the previous month's 1st is at least 3 columns back (`CalendarPages.weekIndex` both sides), else null; `HeatCell` is:

```kotlin
@Composable
private fun HeatCell(
    date: LocalDate,
    inRange: Boolean,
    intensity: (LocalDate) -> Float?,
    onDayClick: ((LocalDate) -> Unit)?,
    colorScale: (Float) -> Color,
    emptyCellColor: Color,
    cellSize: Dp,
    cellShape: Shape,
    dateFormatter: DateTimeFormatter,
    strings: HeatmapStrings,
) {
    if (!inRange) {
        Spacer(Modifier.size(cellSize))
        return
    }
    val value = intensity(date)
    val description =
        if (value == null) {
            strings.dayDescriptionEmpty.format(date.toJavaLocalDate().format(dateFormatter))
        } else {
            strings.dayDescription.format(
                date.toJavaLocalDate().format(dateFormatter),
                heatLevel(value),
                HeatmapLevels,
            )
        }
    Box(
        Modifier
            .size(cellSize)
            .clip(cellShape)
            .background(if (value == null) emptyCellColor else colorScale(value))
            .then(
                if (onDayClick != null) Modifier.clickable { onDayClick(date) } else Modifier,
            ).semantics { contentDescription = description },
    )
}
```

`WeekdayLabelColumn` shows short weekday names via `displayName()` ("Mon", "Wed", "Fri" in en-US) for weekday rows 0, 2, 4 from `firstDayOfWeek`, blank spacer rows otherwise, each row `cellSize` tall with `cellSpacing` gaps and `labelHeight` top offset (the spec's "narrow" wording is relaxed to short names so the chrome-toggle test can assert on "Mon" unambiguously). `Legend` renders `strings.legendLess`, six swatch boxes (`emptyCellColor` first, then `colorScale` at 0f, 0.25f, 0.5f, 0.75f, 1f — each `cellSize` square, `cellShape`-clipped, `cellSpacing` apart), then `strings.legendMore`, in `labelSmall` typography.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :orrery-compose:testDebugUnitTest --tests "me.kozakov.orrery.compose.HeatmapCalendarTest"`
Expected: PASS (all 9). If `startsScrolledToRangeEnd`'s visible-window arithmetic proves flaky against LazyRow's partial-item accounting, assert instead that `state.firstVisibleWeekStart > start.plus(300, DateTimeUnit.DAY)` — the intent is "opens near the end, not the start"; note the substitution in the report.

- [ ] **Step 5: Lint and commit**

Run: `./gradlew :orrery-compose:ktlintCheck :orrery-compose:detekt`

```bash
git add orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/HeatmapCalendar.kt orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/HeatmapCalendarTest.kt
git commit -m "feat(compose): add HeatmapCalendar contribution graph"
```

---

### Task 4: `HeatmapCalendar` snapshots

**Files:**

- Test: `orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/HeatmapCalendarSnapshotTest.kt`

**Interfaces:**

- Consumes: Task 3's `HeatmapCalendar` + `rememberHeatmapCalendarState`, Task 2 defaults.

- [ ] **Step 1: Write the snapshot tests**

Standard scaffolding (Global Constraints). Content composable: `MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) { Surface { HeatmapCalendar(...) } }` with state `rememberHeatmapCalendarState(startDate = LocalDate(2025, 7, 16), endDate = LocalDate(2026, 7, 15), firstDayOfWeek = DayOfWeek.MONDAY)`, intensity `{ date -> if (date.day % 5 == 0) null else (date.day % 5) / 4f }`, `onDayClick = {}`. Three tests capturing `rule.onRoot().captureRoboImage()`:

- `graphLight` — defaults (labels, legend, summary all on).
- `graphDark` — `@Config(qualifiers = "+night")`.
- `graphNoChrome` — `showMonthLabels = false, showWeekdayLabels = false, showLegend = false, summary = null`.

- [ ] **Step 2: Record and visually verify**

Run: `./gradlew :orrery-compose:recordRoborazziDebug --tests "me.kozakov.orrery.compose.HeatmapCalendarSnapshotTest"`
Read the three PNGs and confirm: tile grid scrolled to July 2026 at the right edge; month labels along the top ("Jun", "Jul" visible); Mon/Wed/Fri down the left; "Less □…■ More" legend bottom-right; summary text bottom-left; no-chrome variant shows tiles only; dark variant uses dark scheme. Fix rendering issues before proceeding.

- [ ] **Step 3: Verify, lint, commit**

Run: `./gradlew :orrery-compose:verifyRoborazziDebug :orrery-compose:ktlintCheck :orrery-compose:detekt`
Expected: PASS — no existing golden changed, three new PNGs.

```bash
git add orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/HeatmapCalendarSnapshotTest.kt orrery-compose/src/test/snapshots
git commit -m "test(compose): add HeatmapCalendar snapshot goldens"
```

---

### Task 5: `HeatmapMonth`

**Files:**

- Create: `orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/HeatmapMonth.kt`
- Test: `orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/HeatmapMonthTest.kt`
- Test: `orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/HeatmapMonthSnapshotTest.kt`

**Interfaces:**

- Consumes: core `monthGrid`, `OutDateStyle.EndOfRow`, `DayPosition`, `daysOfWeek`; Task 2 defaults + `heatLevel`/`HeatmapLevels`; `CalendarDefaults.WeekHeader`.
- Produces (used by Task 6): `@Composable public fun HeatmapMonth(yearMonth, intensity, modifier, onDayClick = null, colorScale = HeatmapDefaults.colorScale(), emptyCellColor = HeatmapDefaults.emptyCellColor(), cellShape = RoundedCornerShape(4.dp), showDayNumbers = false, showWeekdayHeader = true, firstDayOfWeek = firstDayOfWeekFromLocale())`.

- [ ] **Step 1: Write the failing tests**

`HeatmapMonthTest.kt`, `yearMonth = YearMonth(2026, 7)`, MONDAY, same `% 5` intensity:

```kotlin
package me.kozakov.orrery.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeatmapMonthTest {
    @get:Rule val rule = createComposeRule()

    private fun intensity(date: LocalDate): Float? =
        if (date.day % 5 == 0) null else (date.day % 5) / 4f

    private fun content(
        onDayClick: ((LocalDate) -> Unit)? = null,
        showDayNumbers: Boolean = false,
    ) {
        rule.setContent {
            MaterialTheme {
                HeatmapMonth(
                    yearMonth = YearMonth(2026, 7),
                    intensity = ::intensity,
                    onDayClick = onDayClick,
                    showDayNumbers = showDayNumbers,
                    firstDayOfWeek = DayOfWeek.MONDAY,
                )
            }
        }
    }

    @Test
    fun clickReportsDate() {
        var clicked: LocalDate? = null
        content(onDayClick = { clicked = it })
        rule.onNodeWithContentDescription("Jul 8, 2026, level 4 of 5").performClick()
        rule.runOnIdle { assertEquals(LocalDate(2026, 7, 8), clicked) }
    }

    @Test
    fun outDatesAreNotClickable() {
        content(onDayClick = {})
        // July 2026 starts on Wednesday; Mon Jun 29 + Tue Jun 30 are in-dates (blank)
        rule.onAllNodesWithText("29").assertCountEquals(0) // numbers off by default
        rule.onNodeWithContentDescription("Jun 29, 2026, no data").assertDoesNotExist()
    }

    @Test
    fun dayNumbersToggleOn() {
        content(showDayNumbers = true)
        rule.onAllNodesWithText("15").assertCountEquals(1)
    }

    @Test
    fun cellsNotClickableWithoutCallback() {
        content(onDayClick = null)
        rule.onNodeWithContentDescription("Jul 8, 2026, level 4 of 5").assertHasNoClickAction()
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :orrery-compose:testDebugUnitTest --tests "me.kozakov.orrery.compose.HeatmapMonthTest"`
Expected: FAIL — compile error.

- [ ] **Step 3: Implement**

Create `HeatmapMonth.kt`:

```kotlin
package me.kozakov.orrery.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toJavaLocalDate
import me.kozakov.orrery.core.DayPosition
import me.kozakov.orrery.core.OutDateStyle
import me.kozakov.orrery.core.daysOfWeek
import me.kozakov.orrery.core.monthGrid
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Static month grid of heat tiles over [monthGrid]. Cells divide the
 * available width equally; leading/trailing out-dates render blank.
 * Accessibility descriptions bucket intensity into five levels.
 */
@Composable
public fun HeatmapMonth(
    yearMonth: YearMonth,
    intensity: (LocalDate) -> Float?,
    modifier: Modifier = Modifier,
    onDayClick: ((LocalDate) -> Unit)? = null,
    colorScale: (Float) -> Color = HeatmapDefaults.colorScale(),
    emptyCellColor: Color = HeatmapDefaults.emptyCellColor(),
    cellShape: Shape = RoundedCornerShape(4.dp),
    showDayNumbers: Boolean = false,
    showWeekdayHeader: Boolean = true,
    firstDayOfWeek: DayOfWeek = firstDayOfWeekFromLocale(),
    strings: HeatmapStrings = HeatmapDefaults.strings(),
) {
    val month = remember(yearMonth, firstDayOfWeek) {
        monthGrid(yearMonth, firstDayOfWeek, OutDateStyle.EndOfRow)
    }
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    Column(modifier) {
        if (showWeekdayHeader) {
            CalendarDefaults.WeekHeader(daysOfWeek = daysOfWeek(firstDayOfWeek))
        }
        month.weeks.forEach { week ->
            Row {
                week.days.forEach { day ->
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(1.dp)) {
                        if (day.position == DayPosition.MonthDate) {
                            MonthHeatCell(
                                date = day.date,
                                intensity = intensity,
                                onDayClick = onDayClick,
                                colorScale = colorScale,
                                emptyCellColor = emptyCellColor,
                                cellShape = cellShape,
                                showDayNumber = showDayNumbers,
                                dateFormatter = dateFormatter,
                                strings = strings,
                            )
                        }
                    }
                }
            }
        }
    }
}
```

`MonthHeatCell` (private, same file): fills its box (`Modifier.matchParentSize()` inside the outer Box, or a Box with `fillMaxSize()`), clipped to `cellShape`, background `emptyCellColor`/`colorScale(value)`, conditional `clickable`, `semantics { contentDescription = ... }` with the same description logic as Task 3's `HeatCell` (extract nothing — repeat the ~8 lines; the two cells differ in sizing and number overlay). When `showDayNumber`, overlay `Text("${date.day}", style = MaterialTheme.typography.labelSmall, color = if (background.luminance() > 0.5f) Color.Black else Color.White)` centered.

Note: the spec signature omits `strings`; it is added here (defaulted) because the a11y templates live in `HeatmapStrings` — record this as a deviation in the task report.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :orrery-compose:testDebugUnitTest --tests "me.kozakov.orrery.compose.HeatmapMonthTest"`
Expected: PASS (all 4).

- [ ] **Step 5: Snapshot tests**

`HeatmapMonthSnapshotTest.kt` — same scaffolding; content `HeatmapMonth(yearMonth = YearMonth(2026, 7), intensity = ..., showDayNumbers = true, firstDayOfWeek = DayOfWeek.MONDAY)` with the `% 5` intensity. Tests `monthLight` and `monthDark` (`+night`), capturing `rule.onRoot().captureRoboImage()`.

Run: `./gradlew :orrery-compose:recordRoborazziDebug --tests "me.kozakov.orrery.compose.HeatmapMonthSnapshotTest"`
Read both PNGs: weekday header, 5 rows of tiles, day numbers legible on heat backgrounds, blanks before Wednesday July 1.

- [ ] **Step 6: Verify, lint, commit**

Run: `./gradlew :orrery-compose:verifyRoborazziDebug :orrery-compose:ktlintCheck :orrery-compose:detekt`
Expected: PASS.

```bash
git add orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/HeatmapMonth.kt orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/HeatmapMonthTest.kt orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/HeatmapMonthSnapshotTest.kt orrery-compose/src/test/snapshots
git commit -m "feat(compose): add HeatmapMonth grid"
```

---

### Task 6: Sample "Year in review" cards

**Files:**

- Modify: `sample/src/main/kotlin/me/kozakov/orrery/sample/HabitsScreen.kt`

**Interfaces:**

- Consumes: `HeatmapCalendar`, `rememberHeatmapCalendarState`, `HeatmapMonth`, existing `SampleData.habits` completion data.

- [ ] **Step 1: Implement**

At the bottom of `HabitsScreen`'s scrolling Column (after the existing "All habits" calendar), add two cards following the screen's existing Card idioms:

- **"Year in review"**: `HeatmapCalendar` with `rememberHeatmapCalendarState()` defaults (trailing year), `intensity` = fraction of habits completed that day from the existing sample data (reuse the combined-habit map the heatmap decorator already feeds; days before the sample data's window return null), `onDayClick` = set the screen's selected date state (add a `rememberSaveable` selected-date string shown under the card, mirroring PickersScreen's result-text idiom).
- **"This month"**: `HeatmapMonth(yearMonth = month, intensity = same lookup, showDayNumbers = true)`.

- [ ] **Step 2: Build, commit**

Run: `./gradlew :sample:assembleDebug` (expected BUILD SUCCESSFUL) and `./gradlew :sample:ktlintCheck`

```bash
git add sample/src/main/kotlin/me/kozakov/orrery/sample/HabitsScreen.kt
git commit -m "feat(sample): add year-in-review heatmap cards to Habits"
```

---

### Task 7: Docs, changelog, release prep

**Files:**

- Modify: `CHANGELOG.md`, `README.md`

- [ ] **Step 1: CHANGELOG** — new `## [0.4.0] - <today's date>` under a fresh `## [Unreleased]`:

```markdown
### Added

- `HeatmapCalendar`: GitHub-style contribution graph — week columns over an
  arbitrary date range with month labels, weekday labels, legend, and a
  summary slot, backed by `rememberHeatmapCalendarState`.
- `HeatmapMonth`: static month grid of heat tiles for dashboards.
- `heatmapSummary()` (orrery-core): pure active-days/streak aggregation.
- `HeatmapStrings` / `HeatmapDefaults`: quantized color scale, legend and
  accessibility strings — all overridable for localization.
```

- [ ] **Step 2: README** — add a Features bullet ("`HeatmapCalendar` contribution graph + `HeatmapMonth` heat tiles with streak summaries") and a `### Heatmaps` subsection under Usage (after Decorators) with a short `HeatmapCalendar` snippet (state + intensity + onDayClick) and one `HeatmapMonth` line; keep the existing tone.

- [ ] **Step 3: Full CI verification**

Run: `./gradlew ktlintCheck detekt checkKotlinAbi :orrery-core:test :orrery-lunar:test :orrery-compose:testDebugUnitTest :orrery-compose:verifyRoborazziDebug :sample:assembleDebug`
Expected: BUILD SUCCESSFUL (Task 1 already updated the core ABI dump).

- [ ] **Step 4: Commit docs**

```bash
git add CHANGELOG.md README.md
git commit -m "docs: changelog and README for 0.4.0"
```

- [ ] **Step 5: Finish branch + release commits (pause for user)**

STOP: hand back for branch integration (merge per the finishing-a-development-branch skill) and confirm before the release pair on main:

```bash
# gradle.properties: VERSION_NAME=0.4.0
git add gradle.properties && git commit -m "release: 0.4.0"
# gradle.properties: VERSION_NAME=0.4.1-SNAPSHOT
git add gradle.properties && git commit -m "chore: prepare next development version 0.4.1-SNAPSHOT"
```

No tagging, pushing, or publishing — the user handles those.
