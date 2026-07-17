# Foundations & Polish (0.2.0) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship Orrery 0.2.0: segmented in-range style, `animateScrollToDate`, selected-day elevation, three new selection presets, a custom week-number slot, and a decade level in `MonthYearPicker`.

**Architecture:** All changes are additive to existing files. Core (pure Kotlin, kotlinx-datetime) gains three preset factories. Compose gains one state method, two new `DefaultDay` render paths (segmented range fill, shadow layer), one slot parameter, and one picker level. No new dependencies, no new modules.

**Tech Stack:** Kotlin, Jetpack Compose, kotlinx-datetime, Roborazzi + Robolectric for snapshots, kotlin.test (core) / JUnit4 + compose-ui-test (compose).

**Spec:** `docs/superpowers/specs/2026-07-17-foundations-polish-design.md`

## Global Constraints

- Explicit API mode: every public declaration needs the `public` modifier and KDoc, matching surrounding code.
- No new dependencies; no changes to `gradle/libs.versions.toml` except none expected.
- Match existing style; ktlint + detekt must pass: `./gradlew :orrery-core:ktlintCheck :orrery-core:detekt :orrery-compose:ktlintCheck :orrery-compose:detekt`.
- Run module-scoped Gradle tasks only, never a full build.
- Conventional Commits (`feat:`, `fix:`, `docs:`, `chore:`, `release:`); never mention Claude/Anthropic or add co-authors in commit messages.
- Core tests: `./gradlew :orrery-core:test`. Compose tests: `./gradlew :orrery-compose:testDebugUnitTest`. Record new snapshot goldens: `./gradlew :orrery-compose:recordRoborazziDebug`.
- Snapshot tests follow `SnapshotTest.kt` conventions: `@GraphicsMode(NATIVE)`, `@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)`, fixed dates in July 2026, `animateSelection = false`, light + dark (`+night`) variants.
- Fixed reference dates: today = `LocalDate(2026, 7, 15)` (a Wednesday), month = July 2026, `firstDayOfWeek = DayOfWeek.MONDAY`.

## File Structure

| File                                                                                      | Change                                           |
| ----------------------------------------------------------------------------------------- | ------------------------------------------------ |
| `orrery-core/src/main/kotlin/me/kozakov/orrery/core/SelectionPresets.kt`                  | Add `lastNDays`, `thisQuarter`, `workweek`       |
| `orrery-core/src/test/kotlin/me/kozakov/orrery/core/SelectionPresetsTest.kt`              | Tests for the three presets                      |
| `orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/CalendarState.kt`               | Add `animateScrollToDate`                        |
| `orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/AnimateScrollToDateTest.kt`     | New test file                                    |
| `orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/DefaultDay.kt`                  | Segmented range fill + elevation layer           |
| `orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/CalendarDefaults.kt`            | KDoc for `inRangeShape` modes                    |
| `orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/SegmentedRangeSnapshotTest.kt`  | New snapshot test                                |
| `orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/ElevationSnapshotTest.kt`       | New snapshot test                                |
| `orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/Calendars.kt`                   | `weekNumber` slot on Horizontal/VerticalCalendar |
| `orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/WeekNumbersUiTest.kt`           | Custom-slot test                                 |
| `orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/MonthYearPicker.kt`             | Decade level                                     |
| `orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/MonthYearPickerTest.kt`         | Decade navigation tests                          |
| `orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/MonthYearPickerSnapshotTest.kt` | New snapshot test                                |
| `CHANGELOG.md`, `README.md`, `gradle.properties`                                          | Release prep                                     |

---

### Task 1: SelectionPresets — `lastNDays`, `thisQuarter`, `workweek`

**Files:**

- Modify: `orrery-core/src/main/kotlin/me/kozakov/orrery/core/SelectionPresets.kt`
- Test: `orrery-core/src/test/kotlin/me/kozakov/orrery/core/SelectionPresetsTest.kt`

**Interfaces:**

- Consumes: existing `Selection(rangeStart, rangeEnd)`, `CalendarPages.weekStart(date, firstDayOfWeek)`.
- Produces: `SelectionPresets.lastNDays(until: LocalDate, days: Int): Selection`, `SelectionPresets.thisQuarter(today: LocalDate): Selection`, `SelectionPresets.workweek(today: LocalDate): Selection`.

- [ ] **Step 1: Write the failing tests**

Append inside `SelectionPresetsTest` (the class already defines `private val wed15 = LocalDate(2026, 7, 15)`):

```kotlin
    @Test
    fun `lastNDays ends at the given date`() {
        assertEquals(LocalDate(2026, 7, 9)..wed15, SelectionPresets.lastNDays(wed15, 7).range)
    }

    @Test
    fun `lastNDays of one selects a single-day range`() {
        assertEquals(wed15..wed15, SelectionPresets.lastNDays(wed15, 1).range)
    }

    @Test
    fun `lastNDays rejects non-positive counts`() {
        assertFailsWith<IllegalArgumentException> { SelectionPresets.lastNDays(wed15, 0) }
    }

    @Test
    fun `thisQuarter spans the calendar quarter`() {
        assertEquals(
            LocalDate(2026, 7, 1)..LocalDate(2026, 9, 30),
            SelectionPresets.thisQuarter(wed15).range,
        )
    }

    @Test
    fun `thisQuarter handles quarter boundaries`() {
        assertEquals(
            LocalDate(2026, 1, 1)..LocalDate(2026, 3, 31),
            SelectionPresets.thisQuarter(LocalDate(2026, 3, 31)).range,
        )
        assertEquals(
            LocalDate(2026, 10, 1)..LocalDate(2026, 12, 31),
            SelectionPresets.thisQuarter(LocalDate(2026, 10, 1)).range,
        )
    }

    @Test
    fun `workweek is Monday through Friday of the containing week`() {
        assertEquals(
            LocalDate(2026, 7, 13)..LocalDate(2026, 7, 17),
            SelectionPresets.workweek(wed15).range,
        )
        // From a Sunday: the week that started the previous Monday.
        assertEquals(
            LocalDate(2026, 7, 6)..LocalDate(2026, 7, 10),
            SelectionPresets.workweek(LocalDate(2026, 7, 12)).range,
        )
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :orrery-core:test --tests "me.kozakov.orrery.core.SelectionPresetsTest"`
Expected: FAIL — compile error, `lastNDays`/`thisQuarter`/`workweek` unresolved.

- [ ] **Step 3: Implement the presets**

Append inside `object SelectionPresets` in `SelectionPresets.kt`, and add `import kotlinx.datetime.Month` to the imports:

```kotlin
    /** The [days]-day range ending at [until], inclusive; requires [days] >= 1. */
    public fun lastNDays(
        until: LocalDate,
        days: Int,
    ): Selection {
        require(days >= 1) { "days must be >= 1, was $days" }
        return Selection(rangeStart = until.minus(days - 1, DateTimeUnit.DAY), rangeEnd = until)
    }

    /** First..last day of [today]'s calendar quarter (Jan-Mar, Apr-Jun, Jul-Sep, Oct-Dec). */
    public fun thisQuarter(today: LocalDate): Selection {
        val firstMonth = Month(((today.month.number - 1) / 3) * 3 + 1)
        val first = LocalDate(today.year, firstMonth, 1)
        val last = first.plus(3, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
        return Selection(rangeStart = first, rangeEnd = last)
    }

    /** Monday..Friday of the week containing [today]. */
    public fun workweek(today: LocalDate): Selection {
        val monday = CalendarPages.weekStart(today, DayOfWeek.MONDAY)
        return Selection(rangeStart = monday, rangeEnd = monday.plus(4, DateTimeUnit.DAY))
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :orrery-core:test --tests "me.kozakov.orrery.core.SelectionPresetsTest"`
Expected: PASS (all, including the pre-existing ones).

- [ ] **Step 5: Lint and commit**

Run: `./gradlew :orrery-core:ktlintCheck :orrery-core:detekt`
Expected: BUILD SUCCESSFUL

```bash
git add orrery-core/src/main/kotlin/me/kozakov/orrery/core/SelectionPresets.kt orrery-core/src/test/kotlin/me/kozakov/orrery/core/SelectionPresetsTest.kt
git commit -m "feat(core): add lastNDays, thisQuarter and workweek selection presets"
```

---

### Task 2: `CalendarState.animateScrollToDate`

**Files:**

- Modify: `orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/CalendarState.kt` (after `scrollToDate`, ~line 64)
- Create: `orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/AnimateScrollToDateTest.kt`

**Interfaces:**

- Consumes: existing `CalendarState.animateScrollToMonth(month: YearMonth)` and `scrollToDate(date: LocalDate)`.
- Produces: `public suspend fun CalendarState.animateScrollToDate(date: LocalDate)` — coerces out-of-range dates to the month range bounds like every other scroll method (via the private `indexOf`).

- [ ] **Step 1: Write the failing test**

Create `AnimateScrollToDateTest.kt`:

```kotlin
package me.kozakov.orrery.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnimateScrollToDateTest {
    @get:Rule val rule = createComposeRule()

    private lateinit var state: CalendarState
    private lateinit var scope: CoroutineScope

    private fun content(firstVisibleMonth: YearMonth) {
        rule.setContent {
            MaterialTheme {
                state =
                    rememberCalendarState(
                        startMonth = YearMonth(2026, 1),
                        endMonth = YearMonth(2026, 12),
                        firstVisibleMonth = firstVisibleMonth,
                    )
                scope = rememberCoroutineScope()
                HorizontalCalendar(state = state) { day -> DefaultDay(day = day) }
            }
        }
    }

    @Test
    fun animateScrollToDateLandsOnTheDateMonth() {
        content(firstVisibleMonth = YearMonth(2026, 1))
        rule.runOnIdle { scope.launch { state.animateScrollToDate(LocalDate(2026, 11, 5)) } }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(YearMonth(2026, 11), state.firstVisibleMonth) }
    }

    @Test
    fun animateScrollToDateCoercesOutOfRangeDates() {
        content(firstVisibleMonth = YearMonth(2026, 6))
        rule.runOnIdle { scope.launch { state.animateScrollToDate(LocalDate(2030, 3, 1)) } }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(YearMonth(2026, 12), state.firstVisibleMonth) }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :orrery-compose:testDebugUnitTest --tests "me.kozakov.orrery.compose.AnimateScrollToDateTest"`
Expected: FAIL — compile error, `animateScrollToDate` unresolved on `CalendarState`.

- [ ] **Step 3: Implement**

In `CalendarState.kt`, directly after `scrollToDate`:

```kotlin
    /** Animated counterpart of [scrollToDate]. */
    public suspend fun animateScrollToDate(date: LocalDate) {
        animateScrollToMonth(YearMonth(date.year, date.month))
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :orrery-compose:testDebugUnitTest --tests "me.kozakov.orrery.compose.AnimateScrollToDateTest"`
Expected: PASS

- [ ] **Step 5: Lint and commit**

Run: `./gradlew :orrery-compose:ktlintCheck :orrery-compose:detekt`
Expected: BUILD SUCCESSFUL

```bash
git add orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/CalendarState.kt orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/AnimateScrollToDateTest.kt
git commit -m "feat(compose): add CalendarState.animateScrollToDate"
```

---

### Task 3: Segmented in-range style (`inRangeShape`)

**Files:**

- Modify: `orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/DefaultDay.kt` (outer `drawBehind` ~line 141, inner `drawBehind` ~line 167)
- Modify: `orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/CalendarDefaults.kt` (KDoc only, ~line 41)
- Create: `orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/SegmentedRangeSnapshotTest.kt`

**Interfaces:**

- Consumes: `CalendarDayShapes.inRangeShape`, `drawDayShape(shape, color)`, existing `bandMode`/`bandAlpha` locals and `RangeBand` enum in `DefaultDay.kt`.
- Produces: no API change — behavior only. `inRangeShape == RectangleShape` (default) keeps the connector band; any other shape draws a centered per-day fill for in-range days, and endpoints draw no half-band.

Snapshot tests can't fail before goldens exist, so this task is implement → record → verify (not strict TDD).

- [ ] **Step 1: Gate the connector band on the rectangle default**

In `DefaultDay.kt`, the outer `Box`'s `drawBehind` currently reads:

```kotlin
.drawBehind {
    if (bandAlpha > 0f) {
        drawRangeBand(
            bandMode,
            colors.inRangeContainerColor.copy(alpha = colors.inRangeContainerColor.alpha * bandAlpha),
        )
    }
}
```

Change the condition to:

```kotlin
.drawBehind {
    if (bandAlpha > 0f && shapes.inRangeShape == RectangleShape) {
        drawRangeBand(
            bandMode,
            colors.inRangeContainerColor.copy(alpha = colors.inRangeContainerColor.alpha * bandAlpha),
        )
    }
}
```

- [ ] **Step 2: Draw the segmented fill in the clipped layer**

In the inner clipped `Box`'s `drawBehind`, add the segmented fill as the FIRST draw (before the today `FilledCircle` block), so selection and decorators render above it:

```kotlin
.drawBehind {
    if (bandAlpha > 0f && bandMode == RangeBand.Full && shapes.inRangeShape != RectangleShape) {
        drawDayShape(
            shapes.inRangeShape,
            colors.inRangeContainerColor.copy(alpha = colors.inRangeContainerColor.alpha * bandAlpha),
        )
    }
    if (isToday && !isSelected && indicator is TodayIndicator.FilledCircle) {
```

Note `bandMode == RangeBand.Full` restricts the fill to interior in-range days; `TowardEnd`/`TowardStart` endpoints keep only their `selectedShape` fill.

- [ ] **Step 3: Document the two modes**

Replace the `CalendarDayShapes` KDoc in `CalendarDefaults.kt`:

```kotlin
/**
 * Shapes and today-marker style used by [DefaultDay].
 *
 * [inRangeShape] picks the in-range rendering mode: [RectangleShape]
 * (the default) draws a continuous connector band across the range;
 * any other shape draws a separate centered fill per in-range day
 * (segmented style), with the range endpoints keeping [selectedShape].
 */
```

Also mention the mode split on the `inRangeShape` line of the `DefaultDay` class KDoc in `DefaultDay.kt` (the paragraph describing the range band), appending: "A non-rectangular [CalendarDayShapes.inRangeShape] switches the band to per-day segmented fills."

- [ ] **Step 4: Add the snapshot test**

Create `SegmentedRangeSnapshotTest.kt`:

```kotlin
package me.kozakov.orrery.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.Selection
import me.kozakov.orrery.core.SelectionMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class SegmentedRangeSnapshotTest {
    @get:Rule val rule = createComposeRule()

    private fun content() {
        rule.setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                Surface {
                    val selection =
                        rememberCalendarSelectionState(
                            mode = SelectionMode.Range(),
                            initialSelection =
                                Selection(
                                    rangeStart = LocalDate(2026, 7, 8),
                                    rangeEnd = LocalDate(2026, 7, 12),
                                ),
                        )
                    HorizontalCalendar(
                        state =
                            rememberCalendarState(
                                startMonth = YearMonth(2026, 7),
                                endMonth = YearMonth(2026, 7),
                                firstVisibleMonth = YearMonth(2026, 7),
                                firstDayOfWeek = DayOfWeek.MONDAY,
                            ),
                        dayContent = { day ->
                            DefaultDay(
                                day = day,
                                selectionState = selection,
                                today = LocalDate(2026, 7, 15),
                                shapes = CalendarDefaults.dayShapes(inRangeShape = CircleShape),
                                animateSelection = false,
                            )
                        },
                    )
                }
            }
        }
    }

    @Test
    fun segmentedRangeLight() {
        content()
        rule.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "+night")
    fun segmentedRangeDark() {
        content()
        rule.onRoot().captureRoboImage()
    }
}
```

- [ ] **Step 5: Record goldens and inspect**

Run: `./gradlew :orrery-compose:recordRoborazziDebug --tests "me.kozakov.orrery.compose.SegmentedRangeSnapshotTest"`
Expected: BUILD SUCCESSFUL; two new PNGs under `orrery-compose/src/test/snapshots/`. Open them (`Read` the PNGs) and confirm: days 9–11 show separate circles in `primaryContainer`; days 8 and 12 show only the solid `primary` selection circle; no rectangular band anywhere.

- [ ] **Step 6: Verify existing snapshots are unchanged**

Run: `./gradlew :orrery-compose:testDebugUnitTest`
Expected: PASS — in particular `SnapshotTest` (default `RectangleShape` band) must not change. If it fails with an image diff, Step 1's gating broke the default path; fix before continuing.

- [ ] **Step 7: Lint and commit**

Run: `./gradlew :orrery-compose:ktlintCheck :orrery-compose:detekt`
Expected: BUILD SUCCESSFUL

```bash
git add orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/DefaultDay.kt orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/CalendarDefaults.kt orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/SegmentedRangeSnapshotTest.kt orrery-compose/src/test/snapshots
git commit -m "feat(compose): honor inRangeShape with a segmented in-range style"
```

---

### Task 4: Selected-day elevation

**Files:**

- Modify: `orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/DefaultDay.kt`
- Create: `orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/ElevationSnapshotTest.kt`

**Interfaces:**

- Consumes: `shapes.selectedShape`, the centered-square layer pattern already used by the ripple layer in `DefaultDay.kt`.
- Produces: new `DefaultDay` parameter `selectedElevation: Dp = 0.dp`, inserted directly after `animateSelection`.

- [ ] **Step 1: Add the parameter**

In the `DefaultDay` signature, after `animateSelection: Boolean = true,`:

```kotlin
    animateSelection: Boolean = true,
    selectedElevation: Dp = 0.dp,
    hapticsEnabled: Boolean = true,
```

Add to the `DefaultDay` KDoc: "A positive [selectedElevation] draws a shadow under the selected day's fill."

New imports in `DefaultDay.kt`:

```kotlin
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
```

- [ ] **Step 2: Animate the elevation**

After the `bandAlpha` declaration (~line 108):

```kotlin
    val elevation by animateDpAsState(
        targetValue = if (isSelected) selectedElevation else 0.dp,
        animationSpec = if (animateSelection) spring(stiffness = Spring.StiffnessMediumLow) else snap(),
        label = "dayElevation",
    )
```

- [ ] **Step 3: Add the shadow layer**

Inside the outer `Box`, as the FIRST child — before the clipped fill `Box` — so the shadow renders under the selection fill (a shadow drawn after it would darken the fill itself):

```kotlin
        // Shadow layer: same centered-square geometry as the ripple layer,
        // drawn first so the selection fill covers the shape's interior.
        if (elevation > 0.dp) {
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .aspectRatio(1f)
                        .shadow(elevation, shapes.selectedShape, clip = false),
                )
            }
        }
```

- [ ] **Step 4: Add the snapshot test**

Create `ElevationSnapshotTest.kt` (same test scaffolding as `SegmentedRangeSnapshotTest` — repeat it, don't reference it):

```kotlin
package me.kozakov.orrery.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.Selection
import me.kozakov.orrery.core.SelectionMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class ElevationSnapshotTest {
    @get:Rule val rule = createComposeRule()

    private fun content() {
        rule.setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                Surface {
                    val selection =
                        rememberCalendarSelectionState(
                            mode = SelectionMode.Single(),
                            initialSelection = Selection(single = LocalDate(2026, 7, 15)),
                        )
                    HorizontalCalendar(
                        state =
                            rememberCalendarState(
                                startMonth = YearMonth(2026, 7),
                                endMonth = YearMonth(2026, 7),
                                firstVisibleMonth = YearMonth(2026, 7),
                                firstDayOfWeek = DayOfWeek.MONDAY,
                            ),
                        dayContent = { day ->
                            DefaultDay(
                                day = day,
                                selectionState = selection,
                                today = LocalDate(2026, 7, 15),
                                animateSelection = false,
                                selectedElevation = 4.dp,
                            )
                        },
                    )
                }
            }
        }
    }

    @Test
    fun elevatedSelectionLight() {
        content()
        rule.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "+night")
    fun elevatedSelectionDark() {
        content()
        rule.onRoot().captureRoboImage()
    }
}
```

- [ ] **Step 5: Record goldens and inspect**

Run: `./gradlew :orrery-compose:recordRoborazziDebug --tests "me.kozakov.orrery.compose.ElevationSnapshotTest"`
Expected: two new PNGs. Confirm a soft shadow rings the selected day's circle and nothing else changed.

- [ ] **Step 6: Verify the default is unchanged, lint, commit**

Run: `./gradlew :orrery-compose:testDebugUnitTest :orrery-compose:ktlintCheck :orrery-compose:detekt`
Expected: PASS — existing snapshots identical (default `0.dp` adds no layer).

```bash
git add orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/DefaultDay.kt orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/ElevationSnapshotTest.kt orrery-compose/src/test/snapshots
git commit -m "feat(compose): add selectedElevation to DefaultDay"
```

---

### Task 5: Custom week-number slot

**Files:**

- Modify: `orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/Calendars.kt` (`HorizontalCalendar` ~line 47 and ~line 137; `VerticalCalendar` ~line 160 and ~line 170)
- Modify: `orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/WeekNumbersUiTest.kt`

**Interfaces:**

- Consumes: existing `MonthContent(weekNumber: (@Composable (CalendarWeek) -> Unit)?)` plumbing — already slot-shaped internally.
- Produces: public parameter `weekNumber: @Composable (CalendarWeek) -> Unit = { CalendarDefaults.WeekNumber(it) }` on both `HorizontalCalendar` and `VerticalCalendar`, placed directly after `showWeekNumbers`. `showWeekNumbers` still gates visibility.

- [ ] **Step 1: Write the failing test**

Add to `WeekNumbersUiTest.kt` (match the file's existing rule/imports; the test needs `androidx.compose.material3.Text` and `me.kozakov.orrery.core.isoWeekNumber`):

```kotlin
    @Test
    fun customWeekNumberSlotReplacesTheDefault() {
        rule.setContent {
            MaterialTheme {
                HorizontalCalendar(
                    state =
                        rememberCalendarState(
                            startMonth = YearMonth(2026, 7),
                            endMonth = YearMonth(2026, 7),
                            firstVisibleMonth = YearMonth(2026, 7),
                            firstDayOfWeek = DayOfWeek.MONDAY,
                        ),
                    showWeekNumbers = true,
                    weekNumber = { week ->
                        Text("W" + week.days.first().date.isoWeekNumber())
                    },
                ) { day -> DefaultDay(day = day) }
            }
        }
        // July 13 2026 falls in ISO week 29; the default slot would render "29", not "W29".
        rule.onNodeWithText("W29").assertExists()
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :orrery-compose:testDebugUnitTest --tests "me.kozakov.orrery.compose.WeekNumbersUiTest"`
Expected: FAIL — compile error, no `weekNumber` parameter.

- [ ] **Step 3: Add the slot to HorizontalCalendar**

In the signature, after `showWeekNumbers: Boolean = false,`:

```kotlin
    showWeekNumbers: Boolean = false,
    weekNumber: @Composable (CalendarWeek) -> Unit = { CalendarDefaults.WeekNumber(it) },
```

In the body, replace the `weekNumber =` argument to `MonthContent`:

```kotlin
                        weekNumber = if (showWeekNumbers) weekNumber else null,
```

(deleting the old `if (showWeekNumbers) { { CalendarDefaults.WeekNumber(it) } } else { null }` block).

- [ ] **Step 4: Add the slot to VerticalCalendar**

Same signature addition after `showWeekNumbers`. Replace the local:

```kotlin
    val weekNumberSlot: (@Composable (CalendarWeek) -> Unit)? =
        if (showWeekNumbers) weekNumber else null
```

and pass `weekNumber = weekNumberSlot` at both `MonthContent` call sites (sticky and plain branches).

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :orrery-compose:testDebugUnitTest --tests "me.kozakov.orrery.compose.WeekNumbersUiTest"`
Expected: PASS (new test and the pre-existing ones — default behavior unchanged).

- [ ] **Step 6: Lint and commit**

Run: `./gradlew :orrery-compose:ktlintCheck :orrery-compose:detekt`
Expected: BUILD SUCCESSFUL

```bash
git add orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/Calendars.kt orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/WeekNumbersUiTest.kt
git commit -m "feat(compose): add weekNumber slot to month calendars"
```

---

### Task 6: Decade level in MonthYearPicker

**Files:**

- Modify: `orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/MonthYearPicker.kt`
- Modify: `orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/MonthYearPickerTest.kt`
- Create: `orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/MonthYearPickerSnapshotTest.kt`

**Interfaces:**

- Consumes: existing `MonthYearPicker(current, range, onSelect, modifier)` public signature — unchanged.
- Produces: new interaction only. The year grid gains a title button labeled `"${range.start.year} – ${range.endInclusive.year}"` (en dash, spaces); tapping it shows a decade grid with cells labeled `"1990s"` etc.; tapping a decade returns to the year grid scrolled to that decade.

- [ ] **Step 1: Write the failing tests**

Add to `MonthYearPickerTest` (default `content()` range is `2020..2030`):

```kotlin
    @Test
    fun yearGridTitleOpensTheDecadeGrid() {
        content()
        rule.onNodeWithText("2020 – 2030").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("2020s").assertExists()
        rule.onNodeWithText("2030s").assertExists()
    }

    @Test
    fun pickingADecadeJumpsTheYearGrid() {
        content(range = YearMonth(1971, 1)..YearMonth(2055, 12))
        rule.onNodeWithText("1971 – 2055").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("1990s").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("1991").assertExists()
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :orrery-compose:testDebugUnitTest --tests "me.kozakov.orrery.compose.MonthYearPickerTest"`
Expected: FAIL — no node with text "2020 – 2030".

- [ ] **Step 3: Implement the decade level**

Rewrite `MonthYearPicker.kt`'s composables (public signature unchanged). The Years↔Decades switch nests inside the existing `pickedYear` `AnimatedContent`, so the Months exit animation keeps its `year` snapshot:

```kotlin
/**
 * Month/year jump picker: a year grid drilling into a 3x4 month grid;
 * months outside [range] are disabled. Tapping the year grid's title
 * zooms out to a decade grid for fast far jumps. Selecting a month
 * fires [onSelect] — the host decides what to do (typically scroll a
 * calendar and dismiss). Host it in a dialog, dropdown, or swap it with
 * the calendar in-place.
 */
@Composable
public fun MonthYearPicker(
    current: YearMonth,
    range: ClosedRange<YearMonth>,
    onSelect: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickedYear by rememberSaveable { mutableStateOf<Int?>(null) }
    var showingDecades by rememberSaveable { mutableStateOf(false) }
    var focusYear by rememberSaveable { mutableStateOf(current.year) }
    AnimatedContent(
        targetState = pickedYear,
        modifier = modifier,
        transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.92f)).togetherWith(fadeOut()) },
        label = "monthYearPicker",
    ) { year ->
        if (year == null) {
            AnimatedContent(
                targetState = showingDecades,
                transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.92f)).togetherWith(fadeOut()) },
                label = "yearDecade",
            ) { decades ->
                if (decades) {
                    DecadeGrid(
                        currentYear = current.year,
                        range = range,
                        onDecadeClick = { decadeStart ->
                            focusYear =
                                decadeStart.coerceIn(range.start.year, range.endInclusive.year)
                            showingDecades = false
                        },
                    )
                } else {
                    YearGrid(
                        currentYear = current.year,
                        focusYear = focusYear,
                        range = range,
                        onTitleClick = { showingDecades = true },
                        onYearClick = { pickedYear = it },
                    )
                }
            }
        } else {
            MonthGrid(
                year = year,
                current = current,
                range = range,
                onBack = { pickedYear = null },
                onMonthClick = { month -> onSelect(YearMonth(year, month)) },
            )
        }
    }
}
```

`YearGrid` gains the title and a `focusYear` scroll target (replacing `currentYear` as the scroll anchor; `currentYear` still drives the highlight):

```kotlin
@Composable
private fun YearGrid(
    currentYear: Int,
    focusYear: Int,
    range: ClosedRange<YearMonth>,
    onTitleClick: () -> Unit,
    onYearClick: (Int) -> Unit,
) {
    val years = (range.start.year..range.endInclusive.year).toList()
    val initialIndex =
        (focusYear - range.start.year).coerceIn(0, years.lastIndex).let { it - it % 3 }
    Column {
        TextButton(onClick = onTitleClick) {
            Text(
                "${range.start.year} – ${range.endInclusive.year}",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = rememberLazyGridState(initialFirstVisibleItemIndex = initialIndex),
        ) {
            items(years.size) { index ->
                val year = years[index]
                TextButton(onClick = { onYearClick(year) }) {
                    Text(
                        year.toString(),
                        color = if (year == currentYear) MaterialTheme.colorScheme.primary else Color.Unspecified,
                        fontWeight = if (year == currentYear) FontWeight.Bold else null,
                    )
                }
            }
        }
    }
}
```

New `DecadeGrid` (every listed decade intersects [range] by construction, so no disabled state):

```kotlin
@Composable
private fun DecadeGrid(
    currentYear: Int,
    range: ClosedRange<YearMonth>,
    onDecadeClick: (Int) -> Unit,
) {
    val decades = (range.start.year / 10..range.endInclusive.year / 10).map { it * 10 }
    LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxWidth()) {
        items(decades.size) { index ->
            val decade = decades[index]
            TextButton(onClick = { onDecadeClick(decade) }) {
                Text(
                    "${decade}s",
                    fontWeight = if (currentYear in decade until decade + 10) FontWeight.Bold else null,
                )
            }
        }
    }
}
```

`MonthGrid` is unchanged. Update `MonthYearPicker`'s call in `YearGrid` docs if the KDoc mentions "two-level" anywhere else (grep for "two-level" — `NavHeader.kt` or README may reference it; fix stale copy in KDoc only, leave README for Task 7).

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :orrery-compose:testDebugUnitTest --tests "me.kozakov.orrery.compose.MonthYearPickerTest"`
Expected: PASS — the two new tests plus the three pre-existing ones (`drillingDownSelectsAYearMonth`, `monthsOutsideTheRangeAreDisabled`, `yearHeaderNavigatesBackToTheYearGrid`).

- [ ] **Step 5: Add the decade-grid snapshot**

Create `MonthYearPickerSnapshotTest.kt`:

```kotlin
package me.kozakov.orrery.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.datetime.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class MonthYearPickerSnapshotTest {
    @get:Rule val rule = createComposeRule()

    private fun content() {
        rule.setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                Surface {
                    MonthYearPicker(
                        current = YearMonth(2026, 7),
                        range = YearMonth(1971, 1)..YearMonth(2055, 12),
                        onSelect = {},
                    )
                }
            }
        }
    }

    @Test
    fun decadeGridLight() {
        content()
        rule.onNodeWithText("1971 – 2055").performClick()
        rule.waitForIdle()
        rule.onRoot().captureRoboImage()
    }
}
```

Run: `./gradlew :orrery-compose:recordRoborazziDebug --tests "me.kozakov.orrery.compose.MonthYearPickerSnapshotTest"`
Expected: one new PNG showing the decade grid ("1970s" … "2050s", "2020s" bold). Inspect it.

- [ ] **Step 6: Full module test, lint, commit**

Run: `./gradlew :orrery-compose:testDebugUnitTest :orrery-compose:ktlintCheck :orrery-compose:detekt`
Expected: PASS

```bash
git add orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/MonthYearPicker.kt orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/MonthYearPickerTest.kt orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/MonthYearPickerSnapshotTest.kt orrery-compose/src/test/snapshots
git commit -m "feat(compose): add decade level to MonthYearPicker"
```

---

### Task 7: Docs, changelog, release prep

**Files:**

- Modify: `CHANGELOG.md`, `README.md`, `gradle.properties`

**Interfaces:**

- Consumes: everything shipped in Tasks 1–6 plus the earlier ripple fix (`fix(compose): clip day-cell ripple to the day shape`).
- Produces: release-ready main branch at `VERSION_NAME=0.2.0`.

- [ ] **Step 1: Update CHANGELOG.md**

Under `## [Unreleased]`, add (then retitle that section to `## [0.2.0] - <today's date>` and re-add an empty `## [Unreleased]` above it):

```markdown
### Added

- `SelectionPresets.lastNDays`, `SelectionPresets.thisQuarter`, and
  `SelectionPresets.workweek`.
- `CalendarState.animateScrollToDate(date)`, the animated counterpart of
  `scrollToDate`.
- Segmented in-range style: a non-rectangular `CalendarDayShapes.inRangeShape`
  now draws a centered per-day fill instead of the connector band (the
  parameter previously had no effect).
- `DefaultDay(selectedElevation = ...)`: draws a shadow under the selected
  day's fill.
- `weekNumber` slot on `HorizontalCalendar` and `VerticalCalendar` for custom
  week-number content; `showWeekNumbers` still gates visibility.
- `MonthYearPicker`: decade level — tap the year grid's title to jump by
  decade.

### Fixed

- Day-cell ripple is clipped to `CalendarDayShapes.dayShape` instead of
  covering the whole rectangular cell.
```

- [ ] **Step 2: Update README.md**

Read `README.md`; in its feature list / customization docs, update the `MonthYearPicker` description from "two-level" to mention the decade level, and add mentions of the `weekNumber` slot, `selectedElevation`, segmented `inRangeShape`, and the new presets where the README documents their surrounding features. Keep the README's existing tone and bullet style; don't restructure sections.

- [ ] **Step 3: Verify everything**

Run: `./gradlew :orrery-core:test :orrery-compose:testDebugUnitTest :orrery-compose:ktlintCheck :orrery-compose:detekt :orrery-core:ktlintCheck :orrery-core:detekt`
Expected: BUILD SUCCESSFUL, zero failures.

- [ ] **Step 4: Commit docs**

```bash
git add CHANGELOG.md README.md
git commit -m "docs: changelog and README for 0.2.0"
```

- [ ] **Step 5: Release commits (pause for user confirmation)**

STOP and confirm with the user before this step — it creates release-versioned commits. On approval, mirror the 0.1.0 release pattern:

```bash
# 1) gradle.properties: VERSION_NAME=0.2.0
git add gradle.properties && git commit -m "release: 0.2.0"
# 2) gradle.properties: VERSION_NAME=0.2.1-SNAPSHOT
git add gradle.properties && git commit -m "chore: prepare next development version 0.2.1-SNAPSHOT"
```

Do not tag, push, or publish — the user handles Maven Central publishing.
