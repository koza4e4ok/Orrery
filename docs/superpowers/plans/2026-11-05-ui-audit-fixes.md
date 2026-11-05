# UI Audit Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the 11 library UI bugs (plus one minor) found by the 2026-11-05 manual `:sample` run on a Pixel 9 Pro emulator, without breaking binary compatibility.

**Architecture:** Each bug is fixed at its root in `orrery-core` or `orrery-compose`, each in its own task with a failing test first. Public signatures grow only through new overloads; the old overload stays as a `@Deprecated(level = HIDDEN)` bridge (repo precedent: `DayDecorators.badge`, `WeekCalendar`). Where the public slot API can't carry new info (all-day banner edges), an internal `CompositionLocal` is used instead of an API change.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform (Android host tests via Robolectric + compose-ui-test, Roborazzi), kotlinx-datetime.

**Source of findings:** the memory file `orrery-ui-audit-2026-11.md` and the emulator screenshots from that session.

## Global Constraints

- Branch `fix/0.9.2-ui-audit` off `main`. Conventional Commits. **No `Co-Authored-By` lines, and no mention of Claude/Anthropic** in commits or PRs (the user's CLAUDE.md overrides harness defaults).
- Explicit API mode: every new `public` declaration needs KDoc.
- No new dependencies.
- Binary compatibility: changing a public signature means adding a new overload and keeping the old one as `@Deprecated("Kept for binary compatibility; use the overload with <param>.", level = DeprecationLevel.HIDDEN)`. Then run `./gradlew :orrery-compose:updateKotlinAbi` (or `:orrery-core:`) and commit `*/api/` changes. `checkKotlinAbi` must pass, and the dump diff may only **add** entries.
- Run module-scoped Gradle tasks only:
  - core: `./gradlew :orrery-core:jvmTest --tests "<FQCN>"`
  - compose: `./gradlew :orrery-compose:testAndroidHostTest --tests "<FQCN>"`
  - screenshots: `./gradlew :orrery-compose:verifyRoborazziDebug` (plain `testDebugUnitTest` does **not** compare goldens). The `*Debug` names are aliases that reject `--tests`; for one class use `./gradlew :orrery-compose:verifyRoborazziAndroidHostTest --tests "<FQCN>"`.
- **Known baseline failures (user decision, 2026-11-05):** on untouched `main`, `DateTimePickerDialogSnapshotTest.timeStepLight`, `DateTimePickerDialogSnapshotTest.stepTransitionMidway` and `CompactHeightDialogSnapshotTest.dateTimePickerTimeStepCompactHeight` fail by a few anti-aliased pixels around the clock dial's selected "9". Leave those 3 goldens untouched. "Screenshots pass" means no failures other than these 3.
- Snapshot policy: a task may change goldens **only** in the snapshots its bug affects. If `verifyRoborazziDebug` fails, run `./gradlew :orrery-compose:recordRoborazziDebug`, list the changed PNGs with `git status --porcelain '*.png'`, **open every changed PNG** and confirm the change is the intended fix. Then restore the 3 known-baseline goldens with `git checkout -- 'orrery-compose/src/androidHostTest/snapshots/*TimeStep*' 'orrery-compose/src/androidHostTest/snapshots/*stepTransitionMidway*' 'orrery-compose/src/androidHostTest/snapshots/*timeStepLight*'`, unless the task's own fix legitimately changed them. Record with `:orrery-compose:recordRoborazziAndroidHostTest --tests "<FQCN>"` for just the affected classes, and `git checkout --` any recorded PNG whose test was passing (recording rewrites sub-threshold noise too). Commit goldens in the same commit as the fix.
- Pixel tests need `@GraphicsMode(GraphicsMode.Mode.NATIVE)` on the class (see `DayDecoratorsTest`).
- Fixed test dates: July 2026, today = `LocalDate(2026, 7, 15)`, `DayOfWeek.MONDAY`, unless a task says otherwise.
- Each task appends one line under `## [Unreleased]` → `### Fixed` (or `### Added`) in `CHANGELOG.md`. Create the subsection the first time.
- Full CI-equivalent check, used in the last task:
  `./gradlew ktlintCheck detekt checkKotlinAbi :orrery-core:allTests :orrery-lunar:allTests :orrery-calendars:allTests :orrery-holidays:allTests :orrery-compose:testDebugUnitTest :orrery-compose:verifyRoborazziDebug :orrery-compose:desktopTest :orrery-compose:wasmJsTest :sample:assembleDebug`

## Out of scope (recorded, not fixed here)

These are minor or sample-only, so they wait for a later pass:

- Habit progress ring and today ring drawn at the same radius.
- `CollapsibleCalendarScaffold` uses a fixed `dayHeight` instead of measuring the cells.
- The `FullCell` endpoint style never shows turnover days.
- The schedule all-day area has no maximum height.
- The year picker marks the current year with bold text only.
- All sample-only bugs, except the two sample call sites that Tasks 7 and 12 must update.

## File Structure

| File                                                                                            | Change                                                                          | Task       |
| ----------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------- | ---------- |
| `orrery-core/src/commonMain/kotlin/me/kozakov/orrery/core/WeekEventBanners.kt`                  | packing order: all-day, then start time, then title                             | 1          |
| `orrery-core/src/commonTest/kotlin/me/kozakov/orrery/core/WeekEventBannersTest.kt`              | ordering tests                                                                  | 1          |
| `orrery-compose/src/commonMain/kotlin/me/kozakov/orrery/compose/ScheduleDefaults.kt`            | opaque tiles, title line fitting, banner-edge shape                             | 2, 3, 4    |
| `orrery-compose/src/commonMain/kotlin/me/kozakov/orrery/compose/ScheduleView.kt`                | banner edges local, flush insets, hour label clamp                              | 4, 5       |
| `orrery-compose/src/androidHostTest/.../ScheduleTileRenderingTest.kt`                           | **create**: tile pixel and line tests                                           | 2, 3, 4    |
| `orrery-compose/src/androidHostTest/.../ScheduleViewTest.kt`                                    | hour-label test                                                                 | 5          |
| `orrery-compose/src/commonMain/kotlin/me/kozakov/orrery/compose/DefaultDay.kt`                  | out-date label colour, decorator dimming, content bottom                        | 6, 7       |
| `orrery-compose/src/commonMain/kotlin/me/kozakov/orrery/compose/DayDecorators.kt`               | `DayCellDrawState.contentBottom`, week-packed spanning bars, label-aware stacks | 7          |
| `orrery-compose/src/androidHostTest/.../OutDateDimmingTest.kt`                                  | **create**                                                                      | 6          |
| `orrery-compose/src/androidHostTest/.../SpanningBarsTest.kt`                                    | **create**                                                                      | 7          |
| `orrery-compose/src/commonMain/kotlin/me/kozakov/orrery/compose/HeatmapCalendar.kt`             | fixed-width label box                                                           | 8          |
| `orrery-compose/src/commonMain/kotlin/me/kozakov/orrery/compose/DateRangePickerDialog.kt`       | hide out-dates                                                                  | 9          |
| `orrery-compose/src/commonMain/kotlin/me/kozakov/orrery/compose/MonthYearPicker.kt`             | short-name fallback                                                             | 10         |
| `orrery-compose/src/commonMain/kotlin/me/kozakov/orrery/compose/DateTimePickerDialog.kt`        | one-line headline                                                               | 11         |
| `orrery-compose/src/commonMain/kotlin/me/kozakov/orrery/compose/Calendars.kt`                   | `WeekCalendar` week numbers                                                     | 12         |
| `orrery-compose/src/commonMain/kotlin/me/kozakov/orrery/compose/CollapsibleCalendarScaffold.kt` | `showWeekNumbers`                                                               | 12         |
| `sample/src/main/kotlin/me/kozakov/orrery/sample/AlmanacScreen.kt`                              | pass `firstDayOfWeek` / `showWeekNumbers`                                       | 7, 12      |
| `orrery-compose/api/**`, `CHANGELOG.md`                                                         | ABI dumps, changelog                                                            | 7, 12, all |

---

### Task 0: Branch

- [ ] **Step 1:** `git checkout main && git checkout -b fix/0.9.2-ui-audit`
- [ ] **Step 2:** Confirm a clean baseline: `./gradlew :orrery-core:jvmTest :orrery-compose:testDebugUnitTest :orrery-compose:verifyRoborazziDebug`. Expected: BUILD SUCCESSFUL. If it fails, stop and report; don't fix unrelated failures.

---

### Task 1: Month banners order same-day events by start time (bug 2)

The audit found that on Sat Nov 7, the 12:30 "Lunch with Sam" was hidden behind "+1" while the 16:00 and 19:30 events were shown. `PackingOrder` sorts by title after span length.

**Files:**

- Modify: `orrery-core/src/commonMain/kotlin/me/kozakov/orrery/core/WeekEventBanners.kt` (KDoc of `packWeekEventBanners`, `PackingOrder` at the bottom)
- Test: `orrery-core/src/commonTest/kotlin/me/kozakov/orrery/core/WeekEventBannersTest.kt`

**Interfaces:** Produces the packing order _start date ↑, span days ↓, all-day before timed, timed start time ↑, title, id_. Task 7 relies on this order through `packWeekEventBanners`.

- [ ] **Step 1: Write failing tests** (add them to `WeekEventBannersTest`)

```kotlin
    private fun timed(
        id: String,
        title: String,
        day: Int,
        hour: Int,
    ): CalendarEvent<Nothing> =
        CalendarEvent(
            id = id,
            title = title,
            span = EventSpan.Timed(LocalDateTime(2026, 7, day, hour, 0), LocalDateTime(2026, 7, day, hour + 1, 0)),
        )

    @Test
    fun timedEventsOnTheSameDayPackInStartTimeOrder() {
        val result =
            packWeekEventBanners(
                week,
                listOf(
                    timed("book", "Book club", 11, 19),
                    timed("one", "1:1 with manager", 11, 16),
                    timed("lunch", "Lunch with Sam", 11, 12),
                    allDay("summit", jul(10), jul(12), title = "Summit"),
                ),
                maxVisibleLanes = 3,
            )
        assertEquals(listOf("summit", "lunch", "one"), result.banners.sortedBy { it.laneIndex }.map { it.event.id })
        assertEquals(1, result.overflowCountByColumn[5])
    }

    @Test
    fun allDayEventsPackBeforeTimedEventsStartingTheSameDay() {
        val result = packWeekEventBanners(week, listOf(timed("a-timed", "A", 8, 9), allDay("z-all-day", jul(8), title = "Z")))
        assertEquals(listOf("z-all-day", "a-timed"), result.banners.map { it.event.id })
    }
```

- [ ] **Step 2: Run them and confirm they fail**
      Run: `./gradlew :orrery-core:jvmTest --tests "me.kozakov.orrery.core.WeekEventBannersTest"`
      Expected: both new tests FAIL (order follows titles: `1:1…`/`Book…` before `Lunch…`, and `A` before `Z`).

- [ ] **Step 3: Implement.** Replace `PackingOrder` with:

```kotlin
private val PackingOrder: Comparator<PackCandidate<*>> =
    compareBy<PackCandidate<*>> { it.event.span.startDate }
        .thenByDescending { it.spanDays() }
        // All-day events (null) sort before timed ones, which go in start-time order.
        .thenBy { (it.event.span as? EventSpan.Timed)?.start?.time }
        .thenBy { it.event.title }
        .thenBy { it.event.id }
```

In the `packWeekEventBanners` KDoc, replace the sentence that starts "Events are placed greedily, in this order:" with:
"Events are placed greedily, in this order: earlier start date first, then longer span (in days) first, then [EventSpan.AllDay] before [EventSpan.Timed], then earlier start time, then [CalendarEvent.title], then [CalendarEvent.id]."

- [ ] **Step 4: Run the tests again.** Same command as Step 2. Expected: PASS, including all existing tests (the existing title-order test only uses all-day events).
- [ ] **Step 5: Confirm the core ABI hasn't changed.** `./gradlew :orrery-core:checkKotlinAbi` → PASS, because only a private member changed.
- [ ] **Step 6: Changelog and commit.** Under `### Fixed` add: "Month event banners (`packWeekEventBanners`) order same-day timed events by start time instead of title, with all-day events first, so the earliest events stay visible when a day overflows."

```bash
git add orrery-core CHANGELOG.md
git commit -m "fix(core): pack same-day banners by start time, all-day first"
```

---

### Task 2: Schedule tiles are opaque, so gridlines stop striking through text (bug 1)

The default `EventTile` and `AllDayEventChip` fill is the event colour at 18% alpha (28% in dark mode), so hour gridlines show through. Only stacked tiles were composited onto the surface.

**Files:**

- Modify: `orrery-compose/src/commonMain/kotlin/me/kozakov/orrery/compose/ScheduleDefaults.kt` (`EventTile`, `AllDayEventChip`)
- Create: `orrery-compose/src/androidHostTest/kotlin/me/kozakov/orrery/compose/ScheduleTileRenderingTest.kt`

**Interfaces:** Produces the test helpers `positioned(title)`, `pixel(...)` and `assertColorNear(...)` in `ScheduleTileRenderingTest`. Tasks 3 and 4 add tests to the same class.

- [ ] **Step 1: Create the test file**

```kotlin
package me.kozakov.orrery.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.EventSpan
import me.kozakov.orrery.core.PositionedEvent
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScheduleTileRenderingTest {
    @get:Rule val rule = createComposeRule()

    private val day = LocalDate(2026, 7, 15)

    private fun positioned(title: String): PositionedEvent<Nothing> =
        PositionedEvent(
            event =
                CalendarEvent(
                    id = title,
                    title = title,
                    span = EventSpan.Timed(LocalDateTime(2026, 7, 15, 12, 30), LocalDateTime(2026, 7, 15, 13, 30)),
                    colorArgb = 0xFFE53935,
                ),
            date = day,
            startMinute = 750,
            endMinute = 810,
            displayEndMinute = 810,
            columnIndex = 0,
            columnCount = 1,
            leftFraction = 0f,
            widthFraction = 1f,
        )

    private fun pixel(
        tag: String,
        x: Dp,
        y: Dp,
    ): Color {
        val map = rule.onNodeWithTag(tag).captureToImage().toPixelMap()
        val (px, py) = with(rule.density) { x.roundToPx() to y.roundToPx() }
        return map[px.coerceAtMost(map.width - 1), py.coerceAtMost(map.height - 1)]
    }

    private fun assertColorNear(
        expected: Color,
        actual: Color,
        tolerance: Float = 0.03f,
    ) {
        val close =
            abs(expected.red - actual.red) <= tolerance &&
                abs(expected.green - actual.green) <= tolerance &&
                abs(expected.blue - actual.blue) <= tolerance
        assertTrue("expected ~$expected but was $actual", close)
    }

    @Test
    fun timedTileHidesGridLinesBehindIt() {
        rule.setContent {
            MaterialTheme {
                Box(
                    Modifier
                        .testTag("host")
                        .size(120.dp, 60.dp)
                        .background(MaterialTheme.colorScheme.surface)
                        .drawBehind {
                            drawLine(Color.Black, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx())
                        },
                ) {
                    ScheduleDefaults.EventTile(positioned("Lunch"), ScheduleDefaults.colors())
                }
            }
        }
        // x = 100dp is right of the text, so both samples hit the bare tile fill.
        assertColorNear(pixel("host", 100.dp, 45.dp), pixel("host", 100.dp, 30.dp))
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**
      Run: `./gradlew :orrery-compose:testAndroidHostTest --tests "me.kozakov.orrery.compose.ScheduleTileRenderingTest"`
      Expected: FAIL. The pixel on the line is near-black and the pixel off the line is pink.

- [ ] **Step 3: Implement** in `ScheduleDefaults.kt`:
  - In `EventTile`, replace
    `val containerColor = if (isStacked) palette.container.compositeOver(surface) else palette.container`
    with
    `val containerColor = palette.container.compositeOver(surface)`
    Keep `isStacked`; the border still uses it.
  - In `AllDayEventChip`, replace `.background(palette.container)` with `.background(palette.container.compositeOver(MaterialTheme.colorScheme.surface))`.
  - Update the `EventTile` KDoc first sentence to: "Default timed event tile, filled opaque (the event tint composited over the surface) so grid lines never show through: a leading accent stripe, …" and keep the rest.

- [ ] **Step 4: Run the test again** (Step 2 command). Expected: PASS.
- [ ] **Step 5: Screenshots.** Run `./gradlew :orrery-compose:verifyRoborazziDebug`. The `ScheduleViewSnapshotTest` goldens are expected to change, and only those. Record, open each changed PNG, and confirm the tiles now hide the lines and nothing else moved.
- [ ] **Step 6: Changelog and commit.** "Schedule event tiles and all-day chips are filled opaque, so hour grid lines no longer cross their text."

```bash
git add orrery-compose CHANGELOG.md
git commit -m "fix(compose): draw schedule event tiles opaque over the grid"
```

---

### Task 3: Tile titles fit the tile (bug 11)

The audit found "Team stan…" with its second line cut off by a 30-minute tile, and "Morni/ng run" broken mid-word in a narrow week column. The title always uses `maxLines = 2`, whatever the tile size.

**Files:**

- Modify: `ScheduleDefaults.kt` (`EventTile` body; new private helper below `FittingColumn`)
- Test: `ScheduleTileRenderingTest.kt`

**Interfaces:** Consumes `positioned(title)` from Task 2.

- [ ] **Step 1: Add failing tests.** Add these imports: `androidx.compose.ui.semantics.SemanticsActions`, `androidx.compose.ui.test.onNodeWithText`, `androidx.compose.ui.test.performSemanticsAction`, `androidx.compose.ui.text.TextLayoutResult`, `org.junit.Assert.assertEquals`.

```kotlin
    private fun tile(
        title: String,
        width: Dp,
        height: Dp,
    ) {
        rule.setContent {
            MaterialTheme {
                Box(Modifier.size(width, height)) {
                    ScheduleDefaults.EventTile(positioned(title), ScheduleDefaults.colors())
                }
            }
        }
    }

    private fun titleLineCount(title: String): Int {
        val results = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(title).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single().lineCount
    }

    @Test
    fun shortTileShowsOnlyTheTitleLinesThatFit() {
        tile("Team standup", width = 64.dp, height = 28.dp)
        assertEquals(1, titleLineCount("Team standup"))
    }

    @Test
    fun tallTileStillWrapsTheTitleOverTwoLines() {
        tile("Team standup", width = 64.dp, height = 120.dp)
        assertEquals(2, titleLineCount("Team standup"))
    }

    @Test
    fun narrowTileNeverBreaksAWordMidway() {
        tile("Morning run", width = 44.dp, height = 120.dp)
        assertEquals(1, titleLineCount("Morning run"))
    }
```

- [ ] **Step 2: Run the class and confirm** `shortTile…` and `narrowTile…` FAIL (2 lines) and `tallTile…` passes.

- [ ] **Step 3: Implement.** In `EventTile`, replace `FittingColumn(Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) { … }` with:

```kotlin
            BoxWithConstraints(Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                val titleStyle = MaterialTheme.typography.labelMedium
                val titleMaxLines = rememberTitleMaxLines(event.title, titleStyle, maxWidth, maxHeight)
                FittingColumn(Modifier) {
                    Text(
                        text = event.title,
                        style = titleStyle,
                        color = palette.content,
                        maxLines = titleMaxLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // … the time-range Text and the subtitle Text stay exactly as they are …
                }
            }
```

Add this below `FittingColumn` (imports: `androidx.compose.foundation.layout.BoxWithConstraints`, `androidx.compose.ui.platform.LocalDensity`, `androidx.compose.ui.text.TextStyle`, `androidx.compose.ui.text.rememberTextMeasurer`, `androidx.compose.ui.unit.Dp`):

```kotlin
private const val MAX_TITLE_LINES = 2

/**
 * Title lines for a tile: at most [MAX_TITLE_LINES], no more than whole lines
 * fit in [maxHeight], and one ellipsized line when the first word is wider
 * than [maxWidth], so narrow week columns never break a word mid-way.
 */
@Composable
private fun rememberTitleMaxLines(
    title: String,
    style: TextStyle,
    maxWidth: Dp,
    maxHeight: Dp,
): Int {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(title, style, maxWidth, maxHeight, density, measurer) {
        with(density) {
            val lineHeightPx = measurer.measure("A", style).size.height
            val byHeight =
                if (maxHeight == Dp.Infinity || lineHeightPx == 0) MAX_TITLE_LINES else (maxHeight.toPx() / lineHeightPx).toInt()
            // With two lines at most, only a too-wide first word gets split across lines;
            // a later one wraps to the last line and is ellipsized there.
            val firstWord = title.trimStart().substringBefore(' ')
            val wordWidthPx = measurer.measure(firstWord, style, softWrap = false, maxLines = 1).size.width
            val byWidth = if (maxWidth != Dp.Infinity && wordWidthPx > maxWidth.toPx()) 1 else MAX_TITLE_LINES
            minOf(byHeight, byWidth).coerceIn(1, MAX_TITLE_LINES)
        }
    }
}
```

> **Execution note:** the first draft checked the *longest* word, which turned "Sprint / plan…" into "Spri…". It now checks the *first* word, covered by the test `aLongLaterWordStillLetsTheTitleUseTwoLines`.

- [ ] **Step 4: Run the class.** Expected: all PASS.
- [ ] **Step 5: Screenshots.** Run `verifyRoborazziDebug`. Only schedule snapshots with short or narrow tiles may change. Record and review as in the Global Constraints.
- [ ] **Step 6: Changelog and commit.** "Schedule tile titles show only the lines that fit the tile and fall back to one ellipsized line instead of breaking a word mid-way."

```bash
git add orrery-compose CHANGELOG.md
git commit -m "fix(compose): fit schedule tile titles to the tile's height and width"
```

---

### Task 4: All-day banners show when an event continues past the visible days (bug 5)

`AllDayBanner` already has `clippedStart`/`clippedEnd`, but `AllDayRow` ignores them. Every chip gets rounded ends and a 1dp inset on both sides, while the month view squares off continuing ends. The public `allDayEventContent: (CalendarEvent<T>) -> Unit` slot can't carry this, so an internal `CompositionLocal` provides it to the default chip without an API change.

**Files:**

- Modify: `ScheduleView.kt` (add the local next to `AllDayRow`; provide it, plus flush insets, inside `AllDayRow`)
- Modify: `ScheduleDefaults.kt` (`AllDayEventChip` shape)
- Test: `ScheduleTileRenderingTest.kt`

**Interfaces:** Produces `internal data class AllDayBannerEdges(val clippedStart: Boolean, val clippedEnd: Boolean)` and `internal val LocalAllDayBannerEdges: ProvidableCompositionLocal<AllDayBannerEdges>` in `ScheduleView.kt`.

- [ ] **Step 1: Write a failing test** (imports: `androidx.compose.runtime.CompositionLocalProvider`, `me.kozakov.orrery.core.CalendarEvent`, `me.kozakov.orrery.core.EventSpan`):

```kotlin
    @Test
    fun allDayChipSquaresOffTheEndThatContinues() {
        val summit = CalendarEvent<Nothing>(id = "s", title = "Summit", span = EventSpan.AllDay(day), colorArgb = 0xFF1E88E5)
        rule.setContent {
            MaterialTheme {
                Box(Modifier.testTag("host").size(120.dp, 24.dp).background(Color.Black)) {
                    CompositionLocalProvider(LocalAllDayBannerEdges provides AllDayBannerEdges(clippedStart = false, clippedEnd = true)) {
                        ScheduleDefaults.AllDayEventChip(summit, ScheduleDefaults.colors())
                    }
                }
            }
        }
        val chip = pixel("host", 100.dp, 12.dp)
        assertColorNear(chip, pixel("host", 119.5.dp, 0.5.dp)) // end corner: square
        assertColorNear(Color.Black, pixel("host", 0.5.dp, 0.5.dp)) // start corner: rounded, background shows
    }
```

- [ ] **Step 2: Run it and confirm it fails** (compile error: `LocalAllDayBannerEdges` is unresolved).

- [ ] **Step 3: Implement.**
      In `ScheduleView.kt`, above `AllDayRow` (imports: `androidx.compose.runtime.CompositionLocalProvider`, `androidx.compose.runtime.staticCompositionLocalOf`):

```kotlin
/** Which ends of the all-day banner being drawn continue past the visible days. */
internal data class AllDayBannerEdges(
    val clippedStart: Boolean,
    val clippedEnd: Boolean,
)

/** Read by [ScheduleDefaults.AllDayEventChip] to square off continuing ends. */
internal val LocalAllDayBannerEdges = staticCompositionLocalOf { AllDayBannerEdges(clippedStart = false, clippedEnd = false) }
```

In `AllDayRow`, wrap the banner content:

```kotlin
                        Box(propagateMinConstraints = true) {
                            CompositionLocalProvider(
                                LocalAllDayBannerEdges provides AllDayBannerEdges(banner.clippedStart, banner.clippedEnd),
                            ) {
                                allDayEventContent(banner.event)
                            }
                        }
```

In the same `Layout` measure block, give inset only to unclipped sides (`placeRelative` mirrors start/end for RTL):

```kotlin
                    if (index < visible.size) {
                        val banner = visible[index]
                        val span = banner.endColumnInclusive - banner.startColumn + 1
                        val startInset = if (banner.clippedStart) 0 else inset
                        val endInset = if (banner.clippedEnd) 0 else inset
                        val bannerWidth = (columnWidth * span).roundToInt() - startInset - endInset
                        measurable.measure(Constraints.fixed(bannerWidth.coerceAtLeast(0), rowHeight))
                    }
```

and in placement:

```kotlin
                        val banner = visible[index]
                        val startInset = if (banner.clippedStart) 0 else inset
                        val x = (columnWidth * banner.startColumn).roundToInt() + startInset
```

In `ScheduleDefaults.AllDayEventChip`, replace `val shape = RoundedCornerShape(dimensions.tileCornerRadius)` with:

```kotlin
        val edges = LocalAllDayBannerEdges.current
        val radius = dimensions.tileCornerRadius
        val startRadius = if (edges.clippedStart) 0.dp else radius
        val endRadius = if (edges.clippedEnd) 0.dp else radius
        val shape =
            RoundedCornerShape(
                topStart = startRadius,
                bottomStart = startRadius,
                topEnd = endRadius,
                bottomEnd = endRadius,
            )
```

Add to the `AllDayEventChip` KDoc: "Inside a schedule's all-day row, an end whose event continues past the visible days is drawn square and flush with the column edge."

- [ ] **Step 4: Run the class.** Expected: PASS.
- [ ] **Step 5: Screenshots.** Run `verifyRoborazziDebug`. Schedule snapshots with multi-day all-day events may change, at the continuing ends only. Record and review.
- [ ] **Step 6: Changelog and commit.** "Schedule all-day chips draw an end square and flush when the event continues before or after the visible days, matching month-view banners."

```bash
git add orrery-compose CHANGELOG.md
git commit -m "fix(compose): square off continuing ends of schedule all-day chips"
```

---

### Task 5: The top hour label isn't half-cut (minor)

The initial scroll lands exactly on an hour line, and labels are centred on their line. `HourGutter` keeps labels inside the _content_ but not inside the _viewport_, so "08:00" is half hidden under the header.

**Files:**

- Modify: `ScheduleView.kt` (`HourGutter` signature and placement; its call in `SchedulePage`)
- Test: `orrery-compose/src/androidHostTest/kotlin/me/kozakov/orrery/compose/ScheduleViewTest.kt`

- [ ] **Step 1: Write a failing test** (import `androidx.compose.ui.test.getUnclippedBoundsInRoot`):

```kotlin
    @Test
    fun hourLabelAtTheViewportTopIsNotCutOff() {
        daySchedule(initialScrollTime = LocalTime(8, 0))
        val label8 = rule.onNodeWithText("08:00").getUnclippedBoundsInRoot()
        val label9 = rule.onNodeWithText("09:00").getUnclippedBoundsInRoot()
        val hourHeight = rule.runOnIdle { state.hourHeight }
        val gridTop = label9.top + (label9.bottom - label9.top) / 2 - hourHeight
        assertTrue("08:00 top ${label8.top} is above the grid top $gridTop", label8.top >= gridTop - 0.5.dp)
    }
```

- [ ] **Step 2: Run it and confirm it fails**
      Run: `./gradlew :orrery-compose:testAndroidHostTest --tests "me.kozakov.orrery.compose.ScheduleViewTest"`. Expected: the new test FAILs (the label sits half a label height above the grid top).

- [ ] **Step 3: Implement.** Change `HourGutter` to take the scroll state (import `androidx.compose.foundation.ScrollState`):

```kotlin
@Composable
private fun HourGutter(
    hourHeight: Dp,
    window: HourWindow,
    width: Dp,
    scrollState: ScrollState,
    hourLabel: @Composable (Int) -> Unit,
) {
```

In its placement block:

```kotlin
        layout(constraints.maxWidth, constraints.maxHeight) {
            val viewportTop = scrollState.value
            placeables.forEachIndexed { row, placeable ->
                // Center each label on its hour line, kept inside the gutter at both ends.
                val maxY = (constraints.maxHeight - placeable.height).coerceAtLeast(0)
                var y = (row * hourPx - placeable.height / 2f).roundToInt().coerceIn(0, maxY)
                // A label cut by the viewport's top edge slides down to sit fully inside it.
                if (y < viewportTop && y + placeable.height > viewportTop) y = viewportTop.coerceAtMost(maxY)
                placeable.placeRelative(0, y)
            }
        }
```

Update the call in `SchedulePage`: `HourGutter(hourHeight, window, dimensions.timeGutterWidth, state.verticalScrollState, slots.hourLabel)`.

- [ ] **Step 4: Run the class.** Expected: all PASS.
- [ ] **Step 5: Screenshots.** Run `verifyRoborazziDebug`. Only schedule snapshots whose top hour line is at the viewport edge may change. Record and review.
- [ ] **Step 6: Changelog and commit.** "The schedule's top hour label slides fully into view instead of being half hidden under the header."

```bash
git add orrery-compose CHANGELOG.md
git commit -m "fix(compose): keep the top schedule hour label fully visible"
```

---

### Task 6: Out-of-month days dim their label and decorators, not just the number (bug 4)

**Files:**

- Modify: `DefaultDay.kt` (extract the label colour into a function; dim decorators on out-dates in both decorator draw sites)
- Create: `orrery-compose/src/androidHostTest/kotlin/me/kozakov/orrery/compose/OutDateDimmingTest.kt`

**Interfaces:**

- Produces `internal fun resolveDayLabelColor(colors: CalendarDayColors, available: Boolean, onRangeEndpoint: Boolean, isSelected: Boolean, isOutDate: Boolean, colorArgb: Long?, isHoliday: Boolean): Color`
- Produces `internal const val OUT_DATE_DECORATOR_ALPHA = 0.38f` and `internal fun DrawScope.drawDecorators(decorators: List<DayDecorator>, layer: DecoratorLayer, day: CalendarDay, cell: DayCellDrawState)` in `DefaultDay.kt`. Task 7 passes a richer `cell` through this function.

- [ ] **Step 1: Write failing tests**

```kotlin
package me.kozakov.orrery.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.LocalDate
import me.kozakov.orrery.core.CalendarDay
import me.kozakov.orrery.core.DayPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OutDateDimmingTest {
    @get:Rule val rule = createComposeRule()

    private val red = Color(0xFFE53935)

    private fun colors(): CalendarDayColors {
        lateinit var colors: CalendarDayColors
        rule.setContent { MaterialTheme { colors = CalendarDefaults.dayColors() } }
        return rule.runOnIdle { colors }
    }

    @Test
    fun outDateLabelUsesTheOutDateColorOverItsOwnColor() {
        val colors = colors()
        val color =
            resolveDayLabelColor(
                colors,
                available = true,
                onRangeEndpoint = false,
                isSelected = false,
                isOutDate = true,
                colorArgb = 0xFFE53935,
                isHoliday = true,
            )
        assertEquals(colors.outDateContentColor, color)
    }

    @Test
    fun selectedOutDateLabelStillUsesTheSelectedColor() {
        val colors = colors()
        val color =
            resolveDayLabelColor(
                colors,
                available = true,
                onRangeEndpoint = false,
                isSelected = true,
                isOutDate = true,
                colorArgb = null,
                isHoliday = false,
            )
        assertEquals(colors.selectedContentColor, color)
    }

    private fun dotGreen(position: DayPosition): Float {
        rule.setContent {
            MaterialTheme {
                Box(Modifier.testTag("cell").size(48.dp).background(Color.White)) {
                    DefaultDay(
                        day = CalendarDay(LocalDate(2026, 8, 1), position),
                        today = LocalDate(2026, 7, 15),
                        decorators = listOf(DayDecorators.eventDots(maxDots = 1) { listOf(red) }),
                    )
                }
            }
        }
        val map = rule.onNodeWithTag("cell").captureToImage().toPixelMap()
        val (x, y) = with(rule.density) { 24.dp.roundToPx() to (48.dp - 6.dp).roundToPx() }
        return map[x, y].green
    }

    @Test
    fun outDateDecoratorsAreDimmed() {
        // 0xE53935 at 38% over white has green ≈ 0.70; at full strength ≈ 0.22.
        assertTrue(dotGreen(DayPosition.OutDate) > 0.6f)
    }

    @Test
    fun inMonthDecoratorsKeepFullStrength() {
        assertTrue(dotGreen(DayPosition.MonthDate) < 0.35f)
    }
}
```

Note: each test calls `setContent` once. If `colors()` followed by a second `setContent` throws in some test, split that test so it calls `setContent` once.

- [ ] **Step 2: Run the class and confirm it fails.**
      `./gradlew :orrery-compose:testAndroidHostTest --tests "me.kozakov.orrery.compose.OutDateDimmingTest"` → compile error (`resolveDayLabelColor` is unresolved). After adding a stub that returns the old logic, `outDateLabel…` and `outDateDecoratorsAreDimmed` FAIL.

- [ ] **Step 3: Implement** in `DefaultDay.kt`:

```kotlin
/** Secondary label color: the out-date dimming wins over the label's own color, like the day number. */
internal fun resolveDayLabelColor(
    colors: CalendarDayColors,
    available: Boolean,
    onRangeEndpoint: Boolean,
    isSelected: Boolean,
    isOutDate: Boolean,
    colorArgb: Long?,
    isHoliday: Boolean,
): Color =
    when {
        !available -> colors.unavailableContentColor
        onRangeEndpoint -> colors.inRangeContentColor
        isSelected -> colors.selectedContentColor
        isOutDate -> colors.outDateContentColor
        colorArgb != null -> Color(colorArgb)
        isHoliday -> colors.holidayLabelColor
        else -> colors.labelColor
    }

/** Material's disabled-content alpha, applied to every decorator on an out-of-month day. */
internal const val OUT_DATE_DECORATOR_ALPHA = 0.38f

/** Draws [decorators] of [layer], through a translucent layer on out-of-month days. */
internal fun DrawScope.drawDecorators(
    decorators: List<DayDecorator>,
    layer: DecoratorLayer,
    day: CalendarDay,
    cell: DayCellDrawState,
) {
    val chosen = decorators.filter { it.layer == layer }
    if (chosen.isEmpty()) return
    if (day.position == DayPosition.MonthDate) {
        chosen.forEach { drawDecorator(it, day, cell) }
        return
    }
    val canvas = drawContext.canvas
    canvas.saveLayer(size.toRect(), Paint().apply { alpha = OUT_DATE_DECORATOR_ALPHA })
    chosen.forEach { drawDecorator(it, day, cell) }
    canvas.restore()
}
```

(Imports: `androidx.compose.ui.geometry.toRect`, `androidx.compose.ui.graphics.Paint`.)

Replace the inline label `when` with:

```kotlin
                val resolvedLabelColor =
                    resolveDayLabelColor(
                        colors = colors,
                        available = available,
                        onRangeEndpoint = splitEndpoint || isTurnover,
                        isSelected = isSelected,
                        isOutDate = day.position != DayPosition.MonthDate,
                        colorArgb = info.colorArgb,
                        isHoliday = info.isHoliday,
                    )
```

and drop the now-unused `val colorArgb = info.colorArgb`.

Replace both `decorators.forEach { decorator -> if (decorator.layer == DecoratorLayer.X) { drawDecorator(decorator, day, cellState) } }` blocks with `drawDecorators(decorators, DecoratorLayer.Behind, day, cellState)` and `drawDecorators(decorators, DecoratorLayer.Over, day, cellState)` respectively.

- [ ] **Step 4: Run the class.** Expected: PASS. Then run all compose unit tests, `./gradlew :orrery-compose:testDebugUnitTest`. Any failing existing test that asserts full-strength decorators on an out-date must be **reported to the user** before you change it, since it shows an intentional behavior change.
- [ ] **Step 5: Screenshots.** Run `verifyRoborazziDebug`. Snapshots that show out-dates with labels or decorators will change. Record, open each changed PNG, and confirm only out-of-month cells got lighter.
- [ ] **Step 6: Changelog and commit.** Under `### Fixed`: "`DefaultDay` dims an out-of-month day's secondary label and all its decorators (dots, badges, bars, rings) like its number; decorators draw at 38% opacity there."

```bash
git add orrery-compose CHANGELOG.md
git commit -m "fix(compose): dim labels and decorators on out-of-month days"
```

---

### Task 7: Spanning event bars keep one lane per event across the week and stay below the label (bug 6)

`SpanningEventBars` stacks each cell's own events bottom-up, so a multi-day event jumps lanes when a neighbouring day has a different event count. The stack also grows into the secondary label. The fix packs lanes per week row with core's `packWeekEventBanners`, which is consistent across the row. That needs the grid's first day of week, so it becomes a new parameter, with the old overload kept as a hidden bridge. `DefaultDay` reports where its number and label end, so `spanningEventBars` and `stackedBars` draw only the lanes that fit below.

**Files:**

- Modify: `DayDecorators.kt` (`DayCellDrawState`, `SpanningEventBars`, `spanningEventBars` factory and its hidden bridge, `StackedBars`)
- Modify: `DefaultDay.kt` (measure the content bottom and pass it in `DayCellDrawState`)
- Modify: `sample/src/main/kotlin/me/kozakov/orrery/sample/AlmanacScreen.kt` (the `spanningEventBars` call near line 570)
- Modify: `orrery-compose/api/**` (regenerated)
- Create: `orrery-compose/src/androidHostTest/kotlin/me/kozakov/orrery/compose/SpanningBarsTest.kt`

**Interfaces:**

- Consumes `drawDecorators(...)` from Task 6 and the packing order from Task 1.
- Produces `internal class DayCellDrawState(selected: Boolean, selectedContentColor: Color, hasLabel: Boolean, contentBottom: Float = Float.NaN)`, where `contentBottom` is in px from the cell top and `NaN` means unknown.
- Produces `DayDecorators.spanningEventBars(eventsByDate, maxBars = 2, barHeight = 4.dp, barSpacing = 2.dp, defaultColor = MaterialTheme.colorScheme.primary, firstDayOfWeek: DayOfWeek = PlatformDateFormat.firstDayOfWeek())`.

- [ ] **Step 1: Write failing tests**

```kotlin
package me.kozakov.orrery.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import me.kozakov.orrery.core.CalendarDay
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.DayPosition
import me.kozakov.orrery.core.EventSpan
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SpanningBarsTest {
    @get:Rule val rule = createComposeRule()

    private val red = Color(0xFFE53935)
    private val blue = Color(0xFF1E88E5)
    private val mon = LocalDate(2026, 7, 6)
    private val tue = LocalDate(2026, 7, 7)

    // A: Mon–Wed (red). B: Tue only (blue). Monday has 1 event, Tuesday 2.
    private val a = CalendarEvent<Nothing>(id = "a", title = "A", span = EventSpan.AllDay(mon, LocalDate(2026, 7, 8)), colorArgb = 0xFFE53935)
    private val b = CalendarEvent<Nothing>(id = "b", title = "B", span = EventSpan.AllDay(tue), colorArgb = 0xFF1E88E5)

    private fun eventsOn(date: LocalDate) = listOf(a, b).filter { date in it.span.startDate..it.span.endDate }

    private fun render(contentBottom: Dp? = null) {
        rule.setContent {
            MaterialTheme {
                val decorator = DayDecorators.spanningEventBars(::eventsOn, maxBars = 2, firstDayOfWeek = DayOfWeek.MONDAY)
                val bottomPx = contentBottom?.let { with(LocalDensity.current) { it.toPx() } } ?: Float.NaN
                val cell = DayCellDrawState(false, Color.Unspecified, hasLabel = contentBottom != null, contentBottom = bottomPx)
                Row {
                    for ((tag, date) in listOf("mon" to mon, "tue" to tue)) {
                        Box(
                            Modifier.testTag(tag).size(48.dp).background(Color.White).drawBehind {
                                drawDecorator(decorator, CalendarDay(date, DayPosition.MonthDate), cell)
                            },
                        )
                    }
                }
            }
        }
    }

    private fun pixel(tag: String, y: Dp): Color {
        val map = rule.onNodeWithTag(tag).captureToImage().toPixelMap()
        val (px, py) = with(rule.density) { 24.dp.roundToPx() to y.roundToPx() }
        return map[px, py]
    }

    private fun assertNear(expected: Color, actual: Color) {
        val close = abs(expected.red - actual.red) < 0.05f && abs(expected.green - actual.green) < 0.05f && abs(expected.blue - actual.blue) < 0.05f
        assertTrue("expected ~$expected but was $actual", close)
    }

    @Test
    fun multiDayEventKeepsItsLaneAcrossDaysWithDifferentEventCounts() {
        render()
        // 2 lanes used this week; no label → bottom margin 3dp; stack top = 48 - 3 - (4 + 2 + 4) = 35dp.
        assertNear(red, pixel("mon", 37.dp)) // lane 0 on Monday
        assertNear(red, pixel("tue", 37.dp)) // lane 0 on Tuesday, same height
        assertNear(blue, pixel("tue", 43.dp)) // lane 1
        assertNear(Color.White, pixel("mon", 43.dp))
    }

    @Test
    fun lanesThatWouldCoverTheLabelAreNotDrawn() {
        // Content ends at 40dp; with a label the bottom margin is 1dp → 7dp free → 1 lane (4dp) fits.
        render(contentBottom = 40.dp)
        assertNear(red, pixel("tue", 45.dp)) // lane 0 moved to the bottom: 48 - 1 - 4 = 43..47
        assertNear(Color.White, pixel("tue", 39.dp)) // where a 2-lane stack would have started
    }
}
```

- [ ] **Step 2: Run it and confirm it fails** (compile errors: no `contentBottom`, no `firstDayOfWeek` parameter).
      `./gradlew :orrery-compose:testAndroidHostTest --tests "me.kozakov.orrery.compose.SpanningBarsTest"`

- [ ] **Step 3: Implement `DayCellDrawState.contentBottom`.**

```kotlin
internal class DayCellDrawState(
    val selected: Boolean,
    val selectedContentColor: Color,
    val hasLabel: Boolean,
    /** Bottom of the day number and label in px from the cell top; NaN when unknown. */
    val contentBottom: Float = Float.NaN,
) {
```

In `DefaultDay.kt`, add `var contentBottomPx by remember { mutableFloatStateOf(Float.NaN) }` near the other per-cell state. Give the number/label `Column` the modifier `Modifier.onPlaced { contentBottomPx = it.boundsInParent().bottom }` (imports: `androidx.compose.ui.layout.onPlaced`, `androidx.compose.ui.layout.boundsInParent`, `androidx.compose.runtime.mutableFloatStateOf`). Pass it to both `DayCellDrawState(...)` constructions as `contentBottom = contentBottomPx`. The draw layers are `matchParentSize` siblings of that `Column`, so the parent coordinates match.

- [ ] **Step 4: Implement week-packed lanes.** Replace `SpanningEventBars.draw` with the version below. Keep `drawSpanningBar` as is. Add `firstDayOfWeek: DayOfWeek` to the class constructor. Imports: `kotlinx.datetime.DayOfWeek`, `kotlinx.datetime.isoDayNumber`, `kotlinx.datetime.DateTimeUnit`, `kotlinx.datetime.minus`, `kotlinx.datetime.plus`, `me.kozakov.orrery.core.packWeekEventBanners`.

```kotlin
        override fun DrawScope.draw(
            day: CalendarDay,
            cell: DayCellDrawState,
        ) {
            if (maxBars <= 0) return
            // Lanes are packed for the whole week row, so an event sits at the same height in every cell it spans.
            val column = (day.date.dayOfWeek.isoDayNumber - firstDayOfWeek.isoDayNumber + 7) % 7
            val weekStart = day.date.minus(column, DateTimeUnit.DAY)
            val weekDates = List(7) { weekStart.plus(it, DateTimeUnit.DAY) }
            val weekEvents = weekDates.flatMap(eventsByDate).distinctBy { it.id }
            val banners = packWeekEventBanners(weekDates, weekEvents, maxVisibleLanes = maxBars).banners
            val here = banners.filter { column in it.startColumn..it.endColumnInclusive }
            if (here.isEmpty()) return

            val heightPx = barHeight.toPx()
            val spacingPx = barSpacing.toPx()
            val bottomMargin = (if (cell.hasLabel) 1.dp else 3.dp).toPx()
            val usedLanes = banners.maxOf { it.laneIndex } + 1
            val fittingLanes =
                if (cell.contentBottom.isNaN()) {
                    usedLanes
                } else {
                    ((size.height - bottomMargin - cell.contentBottom + spacingPx) / (heightPx + spacingPx)).toInt().coerceAtLeast(1)
                }
            val lanes = minOf(usedLanes, fittingLanes)
            val stackTop = size.height - bottomMargin - (lanes * heightPx + (lanes - 1) * spacingPx)
            val insetPx = 4.dp.toPx()
            val radius = CornerRadius(heightPx / 2f, heightPx / 2f)
            val isLtr = layoutDirection == LayoutDirection.Ltr
            val selectedBarColor = if (cell.selected) cell.selectedContentColor.takeIf { it.isSpecified } else null
            val path = Path()
            for (banner in here) {
                if (banner.laneIndex >= lanes) continue
                val event = banner.event
                val continuesFromPrev = event.span.startDate < day.date
                val continuesToNext = event.span.endDate > day.date
                drawSpanningBar(
                    path = path,
                    topY = stackTop + banner.laneIndex * (heightPx + spacingPx),
                    heightPx = heightPx,
                    insetPx = insetPx,
                    radius = radius,
                    leftFlush = if (isLtr) continuesFromPrev else continuesToNext,
                    rightFlush = if (isLtr) continuesToNext else continuesFromPrev,
                    color = selectedBarColor ?: event.colorArgb?.let(::Color) ?: defaultColor,
                )
            }
        }
```

- [ ] **Step 5: New factory overload and hidden bridge.** Add `firstDayOfWeek: DayOfWeek = PlatformDateFormat.firstDayOfWeek()` as the last parameter of `spanningEventBars` and pass it to `SpanningEventBars`. Replace the KDoc's last sentence ("Events are sorted by (startDate, id) …") with: "Lanes are packed per week row (starting on [firstDayOfWeek], which must match the calendar's) in [packWeekEventBanners] order, so a multi-day event keeps one height across the days it spans. In [DefaultDay], lanes that would overlap the day number or secondary label are not drawn." Then add the bridge:

```kotlin
    @Deprecated(
        "Kept for binary compatibility; use the overload with firstDayOfWeek.",
        level = DeprecationLevel.HIDDEN,
    )
    @Composable
    public fun spanningEventBars(
        eventsByDate: (LocalDate) -> List<CalendarEvent<*>>,
        maxBars: Int = 2,
        barHeight: Dp = 4.dp,
        barSpacing: Dp = 2.dp,
        defaultColor: Color = MaterialTheme.colorScheme.primary,
    ): DayDecorator =
        spanningEventBars(
            eventsByDate = eventsByDate,
            maxBars = maxBars,
            barHeight = barHeight,
            barSpacing = barSpacing,
            defaultColor = defaultColor,
            firstDayOfWeek = PlatformDateFormat.firstDayOfWeek(),
        )
```

- [ ] **Step 6: Make `StackedBars` label-aware.** In `StackedBars.draw`, after `val gapPx = spacing.toPx()` and `val bottomMargin = …`, replace `val bars = colors(day.date).take(maxBars)` (move it below these) with:

```kotlin
            val fitting =
                if (cell.contentBottom.isNaN()) {
                    maxBars
                } else {
                    ((size.height - bottomMargin - cell.contentBottom + gapPx) / (heightPx + gapPx)).toInt().coerceAtLeast(1)
                }
            val bars = colors(day.date).take(minOf(maxBars, fitting))
            if (bars.isEmpty()) return
```

Add to the `stackedBars` KDoc: "In [DefaultDay], bars that would overlap the day number or secondary label are not drawn."

- [ ] **Step 7: Update the sample call.** In `AlmanacScreen.kt` (around line 570), add `firstDayOfWeek = calendarState.firstDayOfWeek` to `DayDecorators.spanningEventBars(...)`. If `calendarState` isn't in scope there, pass the screen's first-day value the same way the month calendar gets it.

> **Execution note:** the lane/bar limit applies only when the cell has a secondary label (`cell.hasLabel`). Applying it to every cell dropped `stackedBars`' third bar on unlabelled days, which only sat in the number's descent area and was never part of the bug.

- [ ] **Step 8: Run the tests.** Run `SpanningBarsTest` (PASS) and then all of `./gradlew :orrery-compose:testDebugUnitTest`. Existing `DayDecoratorsTest` cases for spanning bars that assumed per-cell stacking may need new expected y positions. Update them only if the new position matches the week-packed rule, and say so in the commit body.
- [ ] **Step 9: ABI.** Run `./gradlew :orrery-compose:updateKotlinAbi` and check that `git diff orrery-compose/api` only **adds** the new `spanningEventBars` signature; the old one remains. Then run `./gradlew :orrery-compose:checkKotlinAbi :sample:assembleDebug`. Expected: PASS.
- [ ] **Step 10: Screenshots.** Run `verifyRoborazziDebug`. Decorator snapshots using spanning or stacked bars may change. Record, open each changed PNG, and confirm bars stay level along a week and below labels.
- [ ] **Step 11: Changelog and commit.** Under `### Fixed`: "`DayDecorators.spanningEventBars` keeps each multi-day event on one lane across its week row (new `firstDayOfWeek` parameter), and it and `stackedBars` no longer draw over the day's secondary label."

```bash
git add orrery-compose sample CHANGELOG.md
git commit -m "fix(compose): week-consistent spanning bar lanes that stay below labels"
```

---

### Task 8: Heatmap month labels no longer widen their week column (bug 3)

The label `Box` in `WeekColumn` has no width, so "Sep" (wider than a 12dp cell) stretches its column. That creates uneven gaps that look like month separators but sit one column off.

**Files:**

- Modify: `HeatmapCalendar.kt` (`WeekColumn`, label `Box`)
- Test: `orrery-compose/src/androidHostTest/kotlin/me/kozakov/orrery/compose/HeatmapCalendarTest.kt`

- [ ] **Step 1: Write a failing test**

```kotlin
    @Test
    fun monthLabelsDoNotWidenTheirWeekColumns() {
        content()
        rule.runOnIdle {
            val widths = state.listState.layoutInfo.visibleItemsInfo.map { it.size }.distinct()
            assertEquals("week columns have different widths: $widths", 1, widths.size)
        }
    }
```

> **Execution note:** `HeatmapCalendarTest` runs without native graphics, so labels measure as zero width and the test passed before the fix. The test lives in a new `HeatmapLabelLayoutTest` with `@GraphicsMode(NATIVE)`; it reproduced widths `[14, 21, 25, 22, 19]` before the fix.

- [ ] **Step 2: Run it and confirm it fails.** `./gradlew :orrery-compose:testAndroidHostTest --tests "me.kozakov.orrery.compose.HeatmapCalendarTest"` → FAIL (two widths).

- [ ] **Step 3: Implement.** Replace the label `Box` (imports: `androidx.compose.foundation.layout.size`, `androidx.compose.foundation.layout.wrapContentWidth`):

```kotlin
        if (showMonthLabels) {
            // Fixed to the cell width; the label overflows to the end without widening the column.
            Box(Modifier.size(width = cellSize, height = labelHeight)) {
                monthLabelFor(state, index)?.let { label ->
                    Text(
                        text = label,
                        modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
```

- [ ] **Step 4: Run the class.** Expected: all PASS, including the existing month-label tests.
- [ ] **Step 5: Screenshots.** Run `verifyRoborazziDebug`. `HeatmapCalendarSnapshotTest` goldens will change. Record and confirm even column spacing, with labels starting at their week column.
- [ ] **Step 6: Changelog and commit.** "`HeatmapCalendar` month labels no longer widen their week column, which caused uneven, off-by-one gaps between months."

```bash
git add orrery-compose CHANGELOG.md
git commit -m "fix(compose): keep heatmap week columns equal width under month labels"
```

---

### Task 9: The range picker dialog doesn't repeat the weeks at month boundaries (bug 7)

The vertical month list in `OrreryDateRangePickerDialog` calls `DefaultDay` with the default `showOutDates = true`. The boundary week therefore appears under both months, and a selected range is drawn twice.

**Files:**

- Modify: `DateRangePickerDialog.kt` (~line 156, the `DefaultDay(...)` call)
- Test: `orrery-compose/src/androidHostTest/kotlin/me/kozakov/orrery/compose/DateRangePickerDialogTest.kt`

- [ ] **Step 1: Write a failing test.** Add a tall-screen test so July and August are both composed (imports: `androidx.compose.ui.test.hasContentDescription`, `androidx.compose.ui.test.onAllNodes`, `androidx.compose.ui.test.assertCountEquals`, `org.robolectric.annotation.Config`):

```kotlin
    @Test
    @Config(qualifiers = "w411dp-h2400dp")
    fun boundaryWeekDaysAppearOnlyUnderTheirOwnMonth() {
        content()
        rule.onAllNodes(hasContentDescription("August 1, 2026", substring = true)).assertCountEquals(1)
    }
```

- [ ] **Step 2: Run it and confirm it fails.** Expected: count 2 (July's out-date row plus August).
- [ ] **Step 3: Implement.** Add `showOutDates = false,` to the `DefaultDay(...)` call inside the `VerticalCalendar` in `DateRangePickerDialog.kt`.
- [ ] **Step 4: Run the whole `DateRangePickerDialogTest` class.** Expected: PASS. Existing tests click `onFirst()` of "8"/"12", which are in-month days, so they're unaffected.
- [ ] **Step 5: Screenshots.** Run `verifyRoborazziDebug`. `DateRangePickerDialogSnapshotTest` goldens will change (boundary rows blank). Record and review.
- [ ] **Step 6: Changelog and commit.** "`OrreryDateRangePickerDialog` hides out-of-month days, so boundary weeks (and selected ranges) are no longer drawn under two months."

```bash
git add orrery-compose CHANGELOG.md
git commit -m "fix(compose): hide out-dates in the range picker's month list"
```

---

### Task 10: Month names don't break mid-word in `MonthYearPicker` (bug 8)

**Files:**

- Modify: `MonthYearPicker.kt` (`MonthGrid`)
- Test: `orrery-compose/src/androidHostTest/kotlin/me/kozakov/orrery/compose/MonthYearPickerTest.kt`

- [ ] **Step 1: Write a failing test** (imports: `androidx.compose.foundation.layout.width`, `androidx.compose.ui.Modifier`, `androidx.compose.ui.unit.dp`, `androidx.compose.ui.test.onNodeWithContentDescription`):

```kotlin
    @Test
    fun narrowMonthGridFallsBackToShortMonthNames() {
        rule.setContent {
            MaterialTheme {
                MonthYearPicker(
                    current = YearMonth(2026, 7),
                    range = YearMonth(2020, 1)..YearMonth(2030, 12),
                    onSelect = { selected = it },
                    modifier = Modifier.width(240.dp),
                )
            }
        }
        rule.onNodeWithText("2026").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Sep").assertExists()
        rule.onNodeWithText("September").assertDoesNotExist()
        rule.onNodeWithContentDescription("September").assertExists()
    }
```

> **Execution note:** this test lives in a new `MonthYearPickerLayoutTest` with `@GraphicsMode(NATIVE)` for real text metrics (see Task 8).

- [ ] **Step 2: Run it and confirm it fails** (only "September" exists, wrapped).
- [ ] **Step 3: Implement.** In `MonthGrid`, before `LazyVerticalGrid`: `var shortNames by remember { mutableStateOf(false) }`. Replace the month `Text` with the code below (imports: `androidx.compose.ui.semantics.contentDescription`; `semantics` is already imported):

```kotlin
                    val fullName = PlatformDateFormat.monthName(month, short = false)
                    Text(
                        PlatformDateFormat.monthName(month, short = shortNames),
                        // Short names keep the full name for accessibility services.
                        modifier = if (shortNames) Modifier.semantics { contentDescription = fullName } else Modifier,
                        fontWeight = if (yearMonth == current) FontWeight.Bold else null,
                        maxLines = 1,
                        // Any full name that would wrap switches the whole grid to short names.
                        onTextLayout = { if (it.didOverflowHeight) shortNames = true },
                    )
```

- [ ] **Step 4: Run the whole `MonthYearPickerTest` class.** Expected: PASS. Existing tests use full names at the default Robolectric width; if one fails because names went short, give that test's `content()` an explicit `Modifier.width(360.dp)` and say so in the commit body.
- [ ] **Step 5: Screenshots.** Run `verifyRoborazziDebug`. `MonthYearPickerSnapshotTest` and `MonthPickerDialogSnapshotTest` should be **unchanged** at their widths. If they change, open them: short names are acceptable only where full names wrapped.
- [ ] **Step 6: Changelog and commit.** "`MonthYearPicker` switches the month grid to short names (with full-name accessibility labels) instead of breaking a name mid-word when the grid is narrow."

```bash
git add orrery-compose CHANGELOG.md
git commit -m "fix(compose): fall back to short month names in a narrow month grid"
```

---

### Task 11: The date-time dialog headline stays on one line (bug 9)

**Files:**

- Modify: `DateTimePickerDialog.kt` (the headline `Row`, around lines 268–285, and `HeadlineStepPill`)
- Test: `orrery-compose/src/androidHostTest/kotlin/me/kozakov/orrery/compose/DateTimePickerDialogTest.kt`

- [ ] **Step 1: Write failing tests** (imports: `androidx.compose.ui.semantics.SemanticsActions`, `androidx.compose.ui.test.performSemanticsAction`, `androidx.compose.ui.text.TextLayoutResult`, `org.junit.Assert.assertFalse`):

```kotlin
    private fun assertOneUnclippedLine(text: String) {
        val results = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val layout = results.single()
        assertEquals("'$text' wrapped", 1, layout.lineCount)
        assertFalse("'$text' was cut off", layout.isLineEllipsized(0))
    }

    @Test
    fun headlineDateAndTimeStayOnOneLine() {
        content(initialDateTime = LocalDateTime(2026, 7, 28, 21, 45), is24Hour = false)
        assertOneUnclippedLine("Jul 28, 2026")
    }

    @Test
    fun headlinePlaceholderStaysOnOneLine() {
        content()
        assertOneUnclippedLine("Selected date")
    }
```

If the placeholder string isn't "Selected date" in this locale, use `DatePickerDefaults.strings().headlinePlaceholder` captured in `content()`.

> **Execution note:** the tests live in a new `DateTimePickerHeadlineTest` (native graphics, Pixel 5). Under Robolectric's metrics only the placeholder case reproduced; the date case is kept as a regression guard.

- [ ] **Step 2: Run them and confirm they fail** (`lineCount` 2).
- [ ] **Step 3: Implement.** Add parameters to `HeadlineStepPill`: `style: TextStyle` and `onOverflow: () -> Unit` (import `androidx.compose.ui.text.TextStyle`). Change its `Text`:

```kotlin
    Text(
        text,
        style = style,
        color = content,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { if (it.didOverflowHeight) onOverflow() },
        modifier = /* unchanged */,
    )
```

In the dialog, before the headline `Row`: `var compactHeadline by remember { mutableStateOf(false) }` and `val headlineStyle = if (compactHeadline) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall`. Pass `style = headlineStyle, onOverflow = { compactHeadline = true }` to **both** pills, so date and time shrink together. Update the comment above the inner `Row` to: "The pills share one weighted row; if the date would wrap, both drop to titleLarge so it fits on one line."

- [ ] **Step 4: Run the whole `DateTimePickerDialogTest` class.** Expected: PASS. Existing tests find "09:30" and similar by text, which is unchanged.
- [ ] **Step 5: Screenshots.** Run `verifyRoborazziDebug`. `DateTimePickerDialogSnapshotTest` goldens may change, but only where the headline used to wrap. Record and review.
- [ ] **Step 6: Changelog and commit.** "The date-time picker headline stays on one line, stepping down to a smaller type style when the date and time don't fit side by side."

```bash
git add orrery-compose CHANGELOG.md
git commit -m "fix(compose): keep the date-time picker headline on one line"
```

---

### Task 12: Week numbers in `WeekCalendar` and `CollapsibleCalendarScaffold` (bug 10)

`HorizontalCalendar` supports `showWeekNumbers`, but `WeekCalendar` and `CollapsibleCalendarScaffold` don't. The Almanac sample added a header spacer without numbers, so the weekday labels drifted off their columns.

**Files:**

- Modify: `Calendars.kt` (`WeekCalendarContent` gets a `weekNumber` slot; the multi-week `WeekCalendar` overload (the one with `animateHeight`) gets `showWeekNumbers`/`weekNumber`; the old overload becomes a hidden bridge)
- Modify: `CollapsibleCalendarScaffold.kt` (the full overload with `enableDirectDrag` gets `showWeekNumbers`/`weekNumber`, plus a hidden bridge; `CollapsibleCalendarScaffoldImpl` passes them on)
- Modify: `sample/.../AlmanacScreen.kt` (~line 358: pass `showWeekNumbers = showWeekNumbers`)
- Modify: `orrery-compose/api/**`
- Test: `orrery-compose/src/androidHostTest/kotlin/me/kozakov/orrery/compose/CollapsibleScaffoldTest.kt`

**Interfaces:**

- Produces `WeekCalendar(state, modifier, userScrollEnabled, keyboardNavigation, visibleWeeksCount, visibleDaysOfWeek, animateHeight, showWeekNumbers: Boolean = false, weekNumber: @Composable (CalendarWeek) -> Unit = { CalendarDefaults.WeekNumber(it) }, weekHeader = { CalendarDefaults.WeekHeader(it, leadingSpacer = showWeekNumbers) }, dayContent)`
- Produces `CollapsibleCalendarScaffold(calendarState, weekState, modifier, state, dayHeight, gesturesEnabled, enableDirectDrag, dragHandle, showWeekNumbers: Boolean = false, weekNumber: @Composable (CalendarWeek) -> Unit = { CalendarDefaults.WeekNumber(it) }, weekHeader = { CalendarDefaults.WeekHeader(it, leadingSpacer = showWeekNumbers) }, dayContent, content)`

- [ ] **Step 1: Write failing tests** in `CollapsibleScaffoldTest`. The helper below uses the same July 2026, Monday-first states the existing tests build inline (import `androidx.compose.ui.test.getUnclippedBoundsInRoot`, `androidx.compose.ui.test.hasContentDescription`, `androidx.compose.ui.test.onAllNodes`, `androidx.compose.ui.test.onFirst`):

```kotlin
    private fun weekNumberContent(expanded: Boolean) {
        rule.setContent {
            MaterialTheme {
                CollapsibleCalendarScaffold(
                    calendarState =
                        rememberCalendarState(
                            startMonth = YearMonth(2026, 7),
                            endMonth = YearMonth(2026, 7),
                            firstVisibleMonth = YearMonth(2026, 7),
                            firstDayOfWeek = DayOfWeek.MONDAY,
                        ),
                    weekState =
                        rememberWeekCalendarState(
                            startDate = LocalDate(2026, 7, 1),
                            endDate = LocalDate(2026, 7, 31),
                            firstVisibleDate = LocalDate(2026, 7, 6),
                            firstDayOfWeek = DayOfWeek.MONDAY,
                        ),
                    state = rememberCollapsibleCalendarState(initialExpanded = expanded),
                    showWeekNumbers = true,
                    dayContent = { day -> DefaultDay(day = day, today = LocalDate(2026, 7, 15)) },
                ) {}
            }
        }
    }

    private fun assertMondayHeaderAlignedWithMondayCells() {
        val header = rule.onNodeWithText("Mon").getUnclippedBoundsInRoot()
        val cell = rule.onAllNodes(hasContentDescription("July 6, 2026", substring = true)).onFirst().getUnclippedBoundsInRoot()
        val headerCenter = (header.left + header.right) / 2
        val cellCenter = (cell.left + cell.right) / 2
        assertTrue("Mon header at $headerCenter, Monday cell at $cellCenter", (headerCenter - cellCenter).value.let { kotlin.math.abs(it) } < 1f)
    }

    @Test
    fun expandedScaffoldWithWeekNumbersKeepsHeaderAligned() {
        weekNumberContent(expanded = true)
        assertMondayHeaderAlignedWithMondayCells()
    }

    @Test
    fun collapsedScaffoldWithWeekNumbersKeepsHeaderAligned() {
        weekNumberContent(expanded = false)
        assertMondayHeaderAlignedWithMondayCells()
    }
```

If `CalendarDefaults.WeekHeader` renders a narrow name ("M") under the test locale, use the text it actually renders, as found in the existing header tests.

- [ ] **Step 2: Run them and confirm they fail** (compile error: no `showWeekNumbers` parameter).

- [ ] **Step 3: `WeekCalendarContent` week-number slot.** Add the parameter `weekNumber: (@Composable (CalendarWeek) -> Unit)?` after `slots`. In the week row, put the same leading box `MonthContent` uses before `WeekDays(...)`:

```kotlin
                        Row(Modifier.fillMaxWidth()) {
                            if (weekNumber != null) {
                                Box(Modifier.width(WeekNumberColumnWidth), contentAlignment = Alignment.Center) {
                                    weekNumber(week)
                                }
                            }
                            val colCount = week.days.size
                            // … unchanged …
```

Pass `weekNumber = null` from every existing `WeekCalendarContent` caller.

- [ ] **Step 4: `WeekCalendar` overload and bridge.** Copy the current multi-week overload (the one with `animateHeight`) unchanged, mark the copy `@Deprecated("Kept for binary compatibility; use the overload with showWeekNumbers.", level = DeprecationLevel.HIDDEN)`, and keep its body (`weekNumber = null`). In the live overload, add after `animateHeight`:

```kotlin
    showWeekNumbers: Boolean = false,
    weekNumber: @Composable (CalendarWeek) -> Unit = { CalendarDefaults.WeekNumber(it) },
    weekHeader: (@Composable ColumnScope.(List<DayOfWeek>) -> Unit)? = {
        CalendarDefaults.WeekHeader(it, leadingSpacer = showWeekNumbers)
    },
```

and pass `weekNumber = if (showWeekNumbers) weekNumber else null`. Add KDoc lines: "@param showWeekNumbers Shows a leading week-number column, as in [HorizontalCalendar]." and "@param weekNumber Content of that column for each week."

- [ ] **Step 5: Scaffold overload and bridge.** Do the same for the full `CollapsibleCalendarScaffold` overload (the one with `enableDirectDrag`): copy it as a hidden bridge (`"…use the overload with showWeekNumbers."`) and add `showWeekNumbers` and `weekNumber` after `dragHandle`, with the `weekHeader` default using `leadingSpacer = showWeekNumbers`. Thread both through `CollapsibleCalendarScaffoldImpl` (new params). Inside it, call `HorizontalCalendar(…, showWeekNumbers = showWeekNumbers, weekNumber = weekNumber, …)` and the multi-week `WeekCalendar(state = weekState, showWeekNumbers = showWeekNumbers, weekNumber = weekNumber, weekHeader = null, dayContent = dayContent)`. The hidden bridge and the short overload call the impl with `showWeekNumbers = false, weekNumber = { CalendarDefaults.WeekNumber(it) }`. Add the same two `@param` KDoc lines to the live scaffold overload.

- [ ] **Step 6: Sample.** In `AlmanacScreen.kt`'s `CollapsibleCalendarScaffold(...)` call, add `showWeekNumbers = showWeekNumbers,`. Keep its custom `weekHeader` (now correct).

- [ ] **Step 7: Run the tests.** Run `CollapsibleScaffoldTest` and `WeekCalendarTest` (PASS), then all of `:orrery-compose:testDebugUnitTest`.
- [ ] **Step 8: ABI.** Run `./gradlew :orrery-compose:updateKotlinAbi` and confirm the dump diff only adds the two new signatures. Then run `./gradlew :orrery-compose:checkKotlinAbi :sample:assembleDebug`. Expected: PASS.
- [ ] **Step 9: Screenshots.** Run `verifyRoborazziDebug`. Expected: **no** golden changes, since the defaults are unchanged.
- [ ] **Step 10: Changelog and commit.** Under `### Added`: "`WeekCalendar` and `CollapsibleCalendarScaffold` take `showWeekNumbers` / `weekNumber` like `HorizontalCalendar`; the previous signatures stay as hidden binary-compatible bridges." Under `### Fixed`: "Almanac sample: weekday header stays aligned when week numbers are on."

```bash
git add orrery-compose sample CHANGELOG.md
git commit -m "feat(compose): week numbers in WeekCalendar and CollapsibleCalendarScaffold"
```

---

### Task 13: Full verification and on-device check

- [ ] **Step 1: Full CI-equivalent run** (command in Global Constraints). Expected: BUILD SUCCESSFUL. Paste the tail of the output in the hand-off.
- [ ] **Step 2: Emulator re-check.** Run `./gradlew :sample:assembleDebug`, install, and launch on the `Pixel_9_Pro` AVD (`adb install -r sample/build/outputs/apk/debug/sample-debug.apk`). Use `adb shell input tap` rather than mobile-mcp taps, and wait about 1.5s before every `adb exec-out screencap -p`. Confirm each bug on the screen where it was found:
  1. Agenda → Day, go to Sat Nov 7: no gridline through "Lunch with Sam"; all-day "Architecture Summit" is square on both ends; "08:00" is fully visible.
  2. Agenda → Month: Sat 7 shows Summit, Lunch, 1:1 (Book club is the hidden "+1").
  3. Agenda → Week, week of Nov 15: "Team standup" shows one clean line; "Morning run" is not split mid-word.
  4. Habits → Year in review: even column spacing.
  5. Almanac (M3 Default): Oct 26–31 and Dec 1–6 labels and dots are dimmed. Then Stacked & Spanning: "Release Freeze" stays level from Mon 16 to Wed 18, and no bar crosses a lunar label. Then Style Studio → Week Numbers: the header lines up and numbers show, both expanded and after "Collapse to Week".
  6. Almanac → "November 2026 ▾" → pick a year: no month name breaks mid-word.
  7. Pickers → Pick range: Dec 1–5 appears only under December. Pick date and time: the headline is one line before and after picking a date.
     Save before/after screenshots to the scratchpad and list their paths in the hand-off.
- [ ] **Step 3: Memory.** Update the project memory file `orrery-ui-audit-2026-11.md`: mark library bugs 1–11 and the hour-label minor as fixed on branch `fix/0.9.2-ui-audit`, keeping the out-of-scope and sample-only lists open.
- [ ] **Step 4: Hand-off.** Don't merge, tag, push, or bump the version. Report the branch, the commit list (`git log --oneline main..`), the test output, and the screenshot paths, then ask the user how to integrate (superpowers:finishing-a-development-branch).
