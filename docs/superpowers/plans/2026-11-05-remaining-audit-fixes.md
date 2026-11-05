# Remaining Audit Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close every open item from the 2026-11-05 UI audit: five small library issues and the sample-app problems.

**Architecture:** Library fixes stay inside `orrery-compose` with no public API changes; each gets a failing test first. Sample fixes are confined to `sample/` and are verified on the emulator, since the sample has no tests.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Robolectric/compose-ui-test, Roborazzi; Android sample app.

**Source:** the project memory file `orrery-ui-audit-2026-11.md` ("Minor library", "Sample-only" and "Risks" lists).

## Decisions (made with the user on 2026-11-05)

- Full-cell turnover days: full in-range fill with a thin diagonal divider in the cell's container color.
- Habit ring overlap: inset `progressRing` inside the cell's outline (library change).
- Week numbers on the remaining views: **skipped** (backlog).
- The 3 date-time "time step" baseline goldens: **left as-is**.

## Global Constraints

- **Branch:** `fix/0.9.4-audit-polish`, created from `fix/0.9.3-font-scale` (PR #112). Its PR targets `fix/0.9.3-font-scale`; after #111 and #112 merge, rebase onto `main` and retarget.
- Conventional Commits. **No `Co-Authored-By` lines and no mention of Claude/Anthropic** in commits or PRs.
- No public API changes and no ABI dump changes: `./gradlew :orrery-compose:checkKotlinAbi` must pass untouched.
- Test commands: `./gradlew :orrery-compose:testAndroidHostTest --tests "<FQCN>"`; goldens `./gradlew :orrery-compose:verifyRoborazziAndroidHostTest`; record with `./gradlew :orrery-compose:recordRoborazziAndroidHostTest --tests "<FQCN>"` for affected classes only. Then `git checkout --` any recorded PNG whose test was passing.
- **Known baseline failures:** `DateTimePickerDialogSnapshotTest.timeStepLight`, `.stepTransitionMidway`, `CompactHeightDialogSnapshotTest.dateTimePickerTimeStepCompactHeight`. Never record them.
- Open every changed golden's `_compare.png` before recording it.
- Pixel tests need `@GraphicsMode(GraphicsMode.Mode.NATIVE)`.
- After each task: `./gradlew :orrery-compose:ktlintFormat :orrery-compose:ktlintCheck :orrery-compose:detekt` (library) or `./gradlew :sample:ktlintCheck :sample:detekt :sample:assembleDebug` (sample), and one CHANGELOG line under `## [Unreleased]` → `### Fixed` for library tasks. Sample tasks need no CHANGELOG entry.
- Emulator: `Pixel_9_Pro`. Use `adb shell input tap`, wait about 1.5s before `adb exec-out screencap -p`, and **never send BACK unless a screenshot confirms a dialog or sheet is open.**

## File Structure

| File                                                        | Change                                                  | Task |
| ----------------------------------------------------------- | ------------------------------------------------------- | ---- |
| `orrery-compose/.../DayDecorators.kt`                       | `progressRing` inset                                    | 1    |
| `orrery-compose/.../DefaultDay.kt`                          | Full-cell turnover fill and divider; KDoc               | 2    |
| `orrery-compose/.../ScheduleView.kt`                        | all-day rows capped by available height                 | 3    |
| `orrery-compose/.../MonthYearPicker.kt`                     | current-year outline in the year grid                   | 4    |
| `orrery-compose/.../WeekEventBanners.kt`                    | dim banners lying wholly on out-of-month days           | 5    |
| `orrery-compose/src/androidHostTest/.../AuditPolishTest.kt` | **create**: tests for Tasks 1–5                         | 1–5  |
| `sample/.../MainActivity.kt`                                | `enableEdgeToEdge()`                                    | 6    |
| `sample/.../AgendaScreen.kt`                                | working-hours label, rotation, compact landscape header | 7    |
| `sample/.../AlmanacScreen.kt`                               | Colorful Glow dots, theme name                          | 8    |
| `sample/.../PickersScreen.kt`                               | text-style Cancel/OK                                    | 8    |
| `sample/.../TripsScreen.kt`                                 | Month mode blackouts                                    | 9    |
| `sample/.../SampleData.kt`                                  | weekly overlapping events                               | 9    |

---

### Task 0: Branch and baseline

- [ ] `git checkout fix/0.9.3-font-scale && git pull && git checkout -b fix/0.9.4-audit-polish`
- [ ] `./gradlew :orrery-compose:verifyRoborazziAndroidHostTest`. Expected: only the 3 known failures.
- [ ] Create `orrery-compose/src/androidHostTest/kotlin/me/kozakov/orrery/compose/AuditPolishTest.kt`:

```kotlin
package me.kozakov.orrery.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
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
import me.kozakov.orrery.core.CalendarDay
import me.kozakov.orrery.core.DayPosition
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AuditPolishTest {
    @get:Rule val rule = createComposeRule()

    private fun pixel(
        tag: String,
        x: Dp,
        y: Dp,
    ): Color {
        val map = rule.onNodeWithTag(tag).captureToImage().toPixelMap()
        val (px, py) = with(rule.density) { x.roundToPx() to y.roundToPx() }
        return map[px.coerceAtMost(map.width - 1), py.coerceAtMost(map.height - 1)]
    }

    private fun assertNear(
        expected: Color,
        actual: Color,
        tolerance: Float = 0.06f,
    ) {
        val close =
            abs(expected.red - actual.red) <= tolerance &&
                abs(expected.green - actual.green) <= tolerance &&
                abs(expected.blue - actual.blue) <= tolerance
        assertTrue("expected ~$expected but was $actual", close)
    }
}
```

No commit yet; the file is committed with Task 1.

---

### Task 1: The habit progress ring sits inside the today ring

`progressRing` draws at the cell square's outer edge (radius = minDimension/2 − stroke/2), the same place as the `TodayIndicator.Ring` outline, so today looks like a smudged double ring. The ring moves in by `ProgressRingInset = 3.dp`.

**Files:** Modify `orrery-compose/src/commonMain/kotlin/me/kozakov/orrery/compose/DayDecorators.kt` (`progressRing`, around line 477). Test: `AuditPolishTest.kt`.

- [ ] **Step 1: Failing test**

```kotlin
    @Test
    fun progressRingLeavesRoomForTheCellOutline() {
        rule.setContent {
            MaterialTheme {
                val ring = DayDecorators.progressRing(color = Color.Red, trackColor = Color.Transparent) { 1f }
                Box(
                    Modifier.testTag("cell").size(48.dp).background(Color.White).drawBehind {
                        drawDecorator(ring, CalendarDay(LocalDate(2026, 7, 8), DayPosition.MonthDate), DayCellDrawState.Unknown)
                    },
                )
            }
        }
        // Stroke 2dp, inset 3dp: the ring spans y = 3..5dp at the top; the outer 0..2dp stays free for the outline.
        assertNear(Color.White, pixel("cell", 24.dp, 1.dp))
        assertNear(Color.Red, pixel("cell", 24.dp, 4.dp))
    }
```

- [ ] **Step 2:** Run `--tests "me.kozakov.orrery.compose.AuditPolishTest"`. Expected: FAIL (red at y = 1dp).
- [ ] **Step 3: Implement.** In `progressRing`, replace

```kotlin
                val side = size.minDimension - strokeWidth.toPx()
```

with

```kotlin
                // Inset so the ring never overlaps the cell's today/selection outline.
                val side = size.minDimension - 2 * ProgressRingInset.toPx() - strokeWidth.toPx()
```

Add near `DAYS_PER_WEEK` at the top of the file:

```kotlin
private val ProgressRingInset = 3.dp
```

Extend the `progressRing` KDoc with: "The ring sits 3dp inside the cell so it stays clear of the today or selection outline."

- [ ] **Step 4:** Run the class. Expected: PASS. Then run `verifyRoborazziAndroidHostTest`. `DecoratorsSnapshotTest.progressRing` (and any Habits-like snapshot) changes: confirm the ring is a bit smaller and distinct from the today ring, then record.
- [ ] **Step 5:** CHANGELOG: "`DayDecorators.progressRing` sits 3dp inside the cell, so it no longer overlaps the today ring." Commit: `fix(compose): inset the progress ring inside the today outline`.

---

### Task 2: Full-cell style shows turnover days

`DefaultDay` sets `isTurnover = split && …`, so with `RangeEndpointStyle.FullCell` turnover dates look like ordinary days. In Full-cell mode a turnover day now gets a full in-range fill with a thin diagonal divider in the cell's container color, from the bottom-start to the top-end corner (the same line the diagonal split uses), and in-range content color.

**Files:** Modify `orrery-compose/src/commonMain/kotlin/me/kozakov/orrery/compose/DefaultDay.kt` (line about 330 `isTurnover`, the `drawBehind` around line 435, the content-color `when` around line 408, and the KDoc around line 221). Test: `AuditPolishTest.kt`.

- [ ] **Step 1: Failing test.** Extra imports: `androidx.compose.foundation.layout.width`.

```kotlin
    @Test
    fun fullCellTurnoverDayIsFilled() {
        lateinit var inRange: Color
        rule.setContent {
            MaterialTheme {
                val style = CalendarDefaults.hospitalityStyle(rangeEndpointStyle = RangeEndpointStyle.FullCell)
                inRange = style.colors.inRangeContainerColor
                Box(Modifier.testTag("cell").size(48.dp).background(Color.White)) {
                    DefaultDay(
                        day = CalendarDay(LocalDate(2026, 7, 14), DayPosition.MonthDate),
                        style = style,
                        turnoverDates = setOf(LocalDate(2026, 7, 14)),
                        today = LocalDate(2026, 7, 1),
                    )
                }
            }
        }
        val fill = rule.runOnIdle { inRange }.let { Color(it.red, it.green, it.blue) }
        // Two corners on opposite sides of the divider, away from the day number.
        assertNear(fill.compositeOverWhite(inRange.alpha), pixel("cell", 6.dp, 6.dp))
        assertNear(fill.compositeOverWhite(inRange.alpha), pixel("cell", 42.dp, 42.dp))
    }

    private fun Color.compositeOverWhite(alpha: Float) =
        Color(red * alpha + (1 - alpha), green * alpha + (1 - alpha), blue * alpha + (1 - alpha))
```

- [ ] **Step 2:** Run it. Expected: FAIL (white corners).
- [ ] **Step 3: Implement.**
  - Replace `val isTurnover = split && day.date in turnoverDates` with `val isTurnover = day.date in turnoverDates`.
  - Leave the split-half alpha animations (`if (split && (isRangeEnd || isTurnover))` and the PM line) unchanged; they still gate on `split`.
  - In `drawBehind`, after the `if (split) { … }` block, add:

```kotlin
                    if (!split && isTurnover) {
                        // Full-cell turnover: filled like the range, with a thin divider where a split cell would part.
                        drawRect(colors.inRangeContainerColor)
                        val start = if (isRtl) Offset(size.width, size.height) else Offset(0f, size.height)
                        val end = if (isRtl) Offset(0f, 0f) else Offset(size.width, 0f)
                        drawLine(colors.containerColor, start, end, TurnoverGap.toPx())
                    }
```

`colors.containerColor` is transparent in the default styles, so read the divider color before the `Box` and use `dividerColor` in the `drawLine` above instead of `colors.containerColor`:

```kotlin
    val surface = MaterialTheme.colorScheme.surface
    val dividerColor = if (colors.containerColor.alpha > 0f) colors.containerColor else surface
```

- The content color `when` already maps `isTurnover` to `inRangeContentColor`; it now also applies in Full-cell mode.
- KDoc: replace "[RangeEndpointStyle.FullCell] ignores them." with "[RangeEndpointStyle.FullCell] fills them like the range with a thin diagonal divider."
- [ ] **Step 4:** Run the class plus `HospitalitySelectionTest` and `DefaultDayTest`. Expected: PASS. Run `verifyRoborazziAndroidHostTest`. `HospitalityRangeSnapshotTest` Full-cell turnover snapshots (if any) change; review and record.
- [ ] **Step 5:** CHANGELOG: "`DefaultDay` shows turnover days in the Full-cell range style, filled with a thin diagonal divider." Commit: `fix(compose): show turnover days in the full-cell range style`.

---

### Task 3: Schedule all-day rows leave room for the time grid

`AllDayRow` shows up to `dimensions.maxVisibleAllDayRows` (3) lanes whatever the page height, so in a short window (landscape) the all-day area can take the whole page. Rows are now capped to about a quarter of the page height, never fewer than 2 (one lane plus the "+N" row).

**Files:** Modify `orrery-compose/src/commonMain/kotlin/me/kozakov/orrery/compose/ScheduleView.kt` (`SchedulePage`, `AllDayRow`). Test: `AuditPolishTest.kt`.

- [ ] **Step 1: Failing test.** Extra imports: `androidx.compose.foundation.layout.fillMaxWidth`, `androidx.compose.foundation.layout.height`, `androidx.compose.ui.test.onNodeWithText`, `kotlinx.datetime.DayOfWeek`, `me.kozakov.orrery.core.CalendarEvent`, `me.kozakov.orrery.core.EventSpan`.

```kotlin
    @Test
    fun shortScheduleCapsAllDayRows() {
        val day = LocalDate(2026, 7, 15)
        val allDay = (1..3).map { CalendarEvent<Nothing>(id = "a$it", title = "Trip $it", span = EventSpan.AllDay(day)) }
        rule.setContent {
            MaterialTheme {
                DaySchedule(
                    events = { if (it == day) allDay else emptyList() },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    state = rememberScheduleState(firstVisibleDate = day, firstDayOfWeek = DayOfWeek.MONDAY),
                    today = day,
                    dayHeader = null,
                )
            }
        }
        // 200dp × 1/4 = 50dp fits 2 rows of 20dp + 2dp gap: one chip and a "+2" overflow.
        rule.onNodeWithText("+2", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Trip 3", useUnmergedTree = true).assertDoesNotExist()
    }
```

- [ ] **Step 2:** Run it. Expected: FAIL (all three chips show, no "+2").
- [ ] **Step 3: Implement.** Add a `maxRows: Int` parameter to `AllDayRow` right after `gutterWidth`, and in its body replace `val maxRows = dimensions.maxVisibleAllDayRows.coerceAtLeast(1)` with `val maxRows = maxRows.coerceAtLeast(1)` (rename the parameter to `maxRowsLimit` and use `val maxRows = maxRowsLimit.coerceAtLeast(1)` to avoid shadowing). In `SchedulePage`, wrap the top-level `Column(Modifier.fillMaxSize()) { … }` in `BoxWithConstraints(Modifier.fillMaxSize()) { … }` and compute, before the inner column:

```kotlin
        // The all-day area takes at most about a quarter of the page, so short windows keep a time grid.
        val rowStep = dimensions.allDayChipHeight + 2.dp
        val rowsByHeight = if (maxHeight == Dp.Infinity) Int.MAX_VALUE else ((maxHeight / 4 + 2.dp) / rowStep).toInt()
        val allDayRows = minOf(dimensions.maxVisibleAllDayRows, rowsByHeight.coerceAtLeast(2))
```

Pass `allDayRows` to `AllDayRow(…)`. Imports: `androidx.compose.foundation.layout.BoxWithConstraints`. Update the `maxVisibleAllDayRows` KDoc (in `ScheduleDefaults.kt`): "Most all-day rows (including the "+N" row); fewer in short windows, where the all-day area takes at most about a quarter of the height."

- [ ] **Step 4:** Run the class, `ScheduleViewTest` and `ScheduleViewPowerTest`. Expected: PASS. Goldens use tall pages, so they should not change; check with `verifyRoborazziAndroidHostTest`.
- [ ] **Step 5:** CHANGELOG: "Schedule all-day rows take at most about a quarter of a short page, so landscape and split-screen windows keep a visible time grid." Commit: `fix(compose): cap schedule all-day rows by page height`.

---

### Task 4: The year grid outlines the current year

In `MonthYearPicker`'s year grid the current year is only bold and primary-coloured, while the month grid outlines the current month. The year grid gets the same 1dp primary outline.

**Files:** Modify `orrery-compose/src/commonMain/kotlin/me/kozakov/orrery/compose/MonthYearPicker.kt` (year grid `TextButton`, around line 214). Test: `AuditPolishTest.kt`.

- [ ] **Step 1: Failing test.** Extra imports: `androidx.compose.ui.test.hasText`, `kotlinx.datetime.YearMonth`. The outline is visual, so check it with pixels: the button's left edge at mid-height is primary.

```kotlin
    @Test
    fun yearGridOutlinesTheCurrentYear() {
        lateinit var primary: Color
        rule.setContent {
            MaterialTheme {
                primary = MaterialTheme.colorScheme.primary
                MonthYearPicker(
                    current = YearMonth(2026, 7),
                    range = YearMonth(2024, 1)..YearMonth(2028, 12),
                    onSelect = {},
                    modifier = Modifier.testTag("picker"),
                )
            }
        }
        val year = rule.onNode(hasText("2026"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val picker = rule.onNodeWithTag("picker").fetchSemanticsNode().boundsInRoot
        val map = rule.onNodeWithTag("picker").captureToImage().toPixelMap()
        // Scan left from the year text to the first non-background pixel: the outline.
        val y = (year.center.y - picker.top).toInt()
        val background = map[1, y]
        val edge = (year.left - picker.left).toInt() downTo 0
        val outline = edge.map { map[it, y] }.firstOrNull { it != background }
        assertTrue("no outline left of 2026", outline != null)
        assertNear(rule.runOnIdle { primary }, outline!!, tolerance = 0.15f)
    }
```


- [ ] **Step 2:** Run it. Expected: FAIL ("no outline left of 2026", or the first non-background pixel isn't primary).
- [ ] **Step 3: Implement.** Give the year `TextButton` the same border the month grid uses:

```kotlin
                TextButton(
                    onClick = { onYearClick(year) },
                    border = if (year == currentYear) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                ) {
```

`BorderStroke` and `dp` are already imported in this file (the month grid uses them).

- [ ] **Step 4:** Run the class and `MonthYearPickerTest`. Expected: PASS. `MonthYearPickerSnapshotTest` (year grid) changes: review and record.
- [ ] **Step 5:** CHANGELOG: "`MonthYearPicker` outlines the current year in the year grid, like the current month." Commit: `fix(compose): outline the current year in the year grid`.

---

### Task 5: Banners lying wholly on out-of-month days are dimmed

`DefaultDay` dims out-of-month days (#111), but a `WeekEventBanners` banner covering only out-of-month columns stays full strength. The `week: CalendarWeek` overload knows each column's `DayPosition`, so a banner whose columns are all out-of-month draws at `OUT_DATE_DECORATOR_ALPHA` (0.38). Banners partly in the month stay full strength.

**Files:** Modify `orrery-compose/src/commonMain/kotlin/me/kozakov/orrery/compose/WeekEventBanners.kt` (both overloads, `WeekEventBannersContent`, `BannerChip`). Test: `AuditPolishTest.kt`.

- [ ] **Step 1: Failing test.** Extra imports: `androidx.compose.foundation.layout.fillMaxWidth`, `me.kozakov.orrery.core.CalendarWeek`, `me.kozakov.orrery.core.CalendarEvent`, `me.kozakov.orrery.core.EventSpan`.

```kotlin
    @Test
    fun bannerOnlyOnOutOfMonthDaysIsDimmed() {
        // Mon Jul 27 – Sun Aug 2, July grid: Aug 1–2 are out-of-month.
        val week =
            CalendarWeek(
                (27..31).map { CalendarDay(LocalDate(2026, 7, it), DayPosition.MonthDate) } +
                    (1..2).map { CalendarDay(LocalDate(2026, 8, it), DayPosition.OutDate) },
            )
        val trip =
            CalendarEvent<Nothing>(
                id = "t",
                title = "",
                span = EventSpan.AllDay(LocalDate(2026, 8, 1), LocalDate(2026, 8, 2)),
                colorArgb = 0xFFE53935,
            )
        rule.setContent {
            MaterialTheme {
                Box(Modifier.testTag("row").size(350.dp, 40.dp).background(Color.White)) {
                    WeekEventBanners(week = week, events = listOf(trip), modifier = Modifier.fillMaxWidth(), topInset = 0.dp)
                }
            }
        }
        // Column 5 of 7 starts at 250dp; sample the middle of the lane. Full red has green ≈ 0.22; at 38% over white ≈ 0.70.
        val green = pixel("row", 300.dp, 9.dp).green
        assertTrue("banner green $green", green > 0.6f)
    }
```

- [ ] **Step 2:** Run it. Expected: FAIL (green about 0.22).
- [ ] **Step 3: Implement.**
  - `WeekEventBannersContent` gets a `dimmedColumns: Set<Int>` parameter. The `packResult` overload passes `emptySet()`. The `week` overload passes:

```kotlin
        dimmedColumns =
            remember(week) {
                week.days.indices.filter { week.days[it].position != DayPosition.MonthDate }.toSet()
            },
```

- When composing chips: `banners.forEach { banner -> BannerChip(banner, onEventClick, dimmed = (banner.startColumn..banner.endColumnInclusive).all { it in dimmedColumns }) }`.
- `BannerChip` gets `dimmed: Boolean` and prefixes its modifier chain with `Modifier.then(if (dimmed) Modifier.alpha(OUT_DATE_DECORATOR_ALPHA) else Modifier)`. Import `androidx.compose.ui.draw.alpha` and `me.kozakov.orrery.core.DayPosition`.
- KDoc of the `week` overload: add "A banner lying only on out-of-month days of [week] is dimmed like those days."
- [ ] **Step 4:** Run the class and any compose banner tests. Expected: PASS. Goldens: check with `verifyRoborazziAndroidHostTest`; review and record if a banner snapshot shows out-of-month days.
- [ ] **Step 5:** CHANGELOG: "`WeekEventBanners` dims a banner that lies only on out-of-month days, matching those days." Commit: `fix(compose): dim banners on out-of-month days`.

---

### Task 6: Sample status bar icons are readable in light mode

The activity doesn't opt into edge-to-edge, so on Android 15+ the status bar icons stay white on the light sample background.

**Files:** Modify `sample/src/main/kotlin/me/kozakov/orrery/sample/MainActivity.kt`.

- [ ] **Step 1:** In `onCreate`, call `enableEdgeToEdge()` before `super.onCreate` (import `androidx.activity.enableEdgeToEdge`):

```kotlin
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { SampleApp() }
    }
```

`Scaffold` already pads content by the system bars, and `NavigationBar` handles the bottom inset.

- [ ] **Step 2:** `./gradlew :sample:assembleDebug`, install, launch in light mode (`adb shell cmd uimode night no`). Confirm on a screenshot that the status bar icons are dark and nothing draws under the status bar. Check dark mode too.
- [ ] **Step 3:** Commit: `fix(sample): readable status bar icons with edge-to-edge`.

---

### Task 7: Agenda screen: working-hours label, rotation, landscape

**Files:** Modify `sample/src/main/kotlin/me/kozakov/orrery/sample/AgendaScreen.kt`.

- [ ] **Step 1: Working-hours label.** Remove the `Text("Working hours …")` from the horizontally scrolling chip row and place it as its own line right below that row:

```kotlin
            Text(
                "Working hours $WORK_DAY_START_HOUR:00–$WORK_DAY_END_HOUR:00",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
```

It shows only in Day and Week modes, as before (keep it inside the same `if` that shows the chip row).

- [ ] **Step 2: Rotation keeps the paged position.** Replace

```kotlin
    LaunchedEffect(selectedDay) {
        scheduleState.scrollToDate(selectedDay)
        agendaState.animateScrollToDate(selectedDay)
    }
```

with

```kotlin
    // Sync the views to a newly selected day, but not again after rotation, which would undo paging.
    var syncedDay by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedDay) {
        if (syncedDay == selectedDay.toString()) return@LaunchedEffect
        syncedDay = selectedDay.toString()
        scheduleState.scrollToDate(selectedDay)
        agendaState.animateScrollToDate(selectedDay)
    }
```

(`String` keeps the saver trivial; add `mutableStateOf`/`rememberSaveable`/`getValue`/`setValue` imports if missing.)

- [ ] **Step 3: Landscape keeps a time grid.** Wrap the screen's root `Column(Modifier.fillMaxSize())` in `BoxWithConstraints(Modifier.fillMaxSize())`, and show `SampleHeroHeader(…)` only when `maxHeight >= 480.dp`, so short windows drop the big date header. Import `androidx.compose.foundation.layout.BoxWithConstraints`.
- [ ] **Step 4:** `./gradlew :sample:ktlintCheck :sample:detekt :sample:assembleDebug`, install, and on the emulator:
  1. Agenda → Day: "Working hours 7:00–20:00" is fully visible under the chips.
  2. Week mode, page forward two weeks, rotate (`adb shell settings put system accelerometer_rotation 0; adb shell settings put system user_rotation 1`): the same week stays, and the time grid is visible in landscape.
  3. Rotate back (`user_rotation 0`, `accelerometer_rotation 1`).
- [ ] **Step 5:** Commit: `fix(sample): agenda working-hours label, rotation and landscape layout`.

---

> **Execution note (Task 7):** the screen height comes from `LocalConfiguration.screenHeightDp` instead of `BoxWithConstraints` (simpler in the Android-only sample). Hiding only the hero header wasn't enough on the emulator: in landscape the time grid was still 0px. Compact height also drops the working-hours line, plus the month header in Day/Week modes.

### Task 8: Almanac and Pickers polish

**Files:** Modify `sample/src/main/kotlin/me/kozakov/orrery/sample/AlmanacScreen.kt`, `sample/src/main/kotlin/me/kozakov/orrery/sample/PickersScreen.kt`.

- [ ] **Step 1: Colorful Glow dots.** In `rememberShowcaseDecorators`, before the `when`, read `val accentDot = MaterialTheme.colorScheme.primary`, and replace

```kotlin
                DayDecorators.eventDots(selectedColor = Color.White, maxDots = 2) { date ->
                    if (date.day % 4 == 0) listOf(Color.White) else emptyList()
                },
```

with

```kotlin
                // White only on the glowing (every third) days; the theme accent elsewhere, so plain cells show it.
                DayDecorators.eventDots(selectedColor = Color.White, maxDots = 2) { date ->
                    when {
                        date.day % 4 != 0 -> emptyList()
                        date.day % 3 == 0 -> listOf(Color.White)
                        else -> listOf(accentDot)
                    }
                },
```

- [ ] **Step 2: Theme name.** Add a top-level helper next to `ShowcaseThemePreset`:

```kotlin
/** The name a preset shows everywhere; Rounded & Card shows its active sub-mode. */
private fun ShowcaseThemePreset.displayName(cardGridSubMode: Boolean): String =
    if (this == ShowcaseThemePreset.RoundedAndCard) {
        if (cardGridSubMode) "Card Grid" else "Rounded Square"
    } else {
        label
    }
```

Use it for the hero chip label (replace the inline `if (cardGridSubMode) "Card Grid" else "Rounded Square"` near line 480). Add a `themeName: String` parameter to `ShowcaseDetailList`, use it in `Text("Theme: $themeName · Customize")`, and pass `themePreset.displayName(cardGridSubMode)` at the call site (around line 381).

- [ ] **Step 3: Pickers buttons.** In `PickersScreen.kt`'s date-time dialog (around lines 179 and 184), change `confirmButton = { Button(…) }` and `dismissButton = { Button(…) }` to `TextButton`, matching the other three dialogs; and the month dialog's `Button(` confirm (around line 219) to `TextButton(`. Keep the `Button` import; the four "Pick …" launch buttons still use it.
- [ ] **Step 4:** Build, install, and on the emulator:
  - **Almanac → Colorful Glow:** dots appear on plain cells.
  - **Rounded & Card:** tapping the chip twice switches between "Rounded Square" and "Card Grid", and the "Theme: …" text follows. In Card Grid, the last row isn't clipped (this confirms #112's measured rows fixed the old Card Grid clipping; if it's still clipped, stop and report).
  - **Pickers → Pick date and time:** Cancel and OK are text buttons.
- [ ] **Step 5:** Commit: `fix(sample): almanac glow dots, consistent theme names, text dialog buttons`.

---

### Task 9: Trips month mode and overlapping demo events

**Files:** Modify `sample/src/main/kotlin/me/kozakov/orrery/sample/TripsScreen.kt`, `sample/src/main/kotlin/me/kozakov/orrery/sample/SampleData.kt`.

- [ ] **Step 1: Month stays aren't always rejected.** The blackout ranges (days +12..+14 and +33..+36) fall in the current and next month, so Month mode can only pick months two or more ahead. In `TripsScreen`, replace `val disabled = remember(today) { SampleData.blackoutDates(today) }` and pass a mode-dependent value:

```kotlin
    val blackouts = remember(today) { SampleData.blackoutDates(today) }
    val pastOnly = remember(today) { DisabledDates { before(today) } }
```

and in `rememberCalendarSelectionState(…)` use `disabled = if (stayMode == StayMode.Month) pastOnly else blackouts`, with a one-line comment: "Whole-month stays ignore the short blackouts so the demo can book next month." Rename the other uses of `disabled` in the file to `blackouts` (or whichever is passed to the calendar cells), and check with `grep -n "disabled" sample/src/main/kotlin/me/kozakov/orrery/sample/TripsScreen.kt`. Import `me.kozakov.orrery.core.DisabledDates` if missing.

- [ ] **Step 2: Overlapping events to demo Stacked overlap.** In `SampleData.eventsFor`, inside the `for (epoch in startEpoch..endEpoch)` loop and after the random events for that date, add every Wednesday:

```kotlin
                    // Every Wednesday a review overlaps the standup, so the schedule's overlap modes have something to show.
                    if (date.dayOfWeek == DayOfWeek.WEDNESDAY) {
                        add(timedEvent("$date-standup", date, LocalTime(9, 0), LocalTime(9, 30), "Team standup", WORK_COLOR))
                        add(timedEvent("$date-review", date, LocalTime(9, 15), LocalTime(10, 15), "Code review", WORK_COLOR))
                    }
```

and add to `SampleData`:

```kotlin
    private fun timedEvent(
        id: String,
        date: LocalDate,
        start: LocalTime,
        end: LocalTime,
        title: String,
        colorArgb: Long,
    ) = CalendarEvent<Nothing>(
        id = id,
        title = title,
        span = EventSpan.Timed(LocalDateTime(date, start), LocalDateTime(date, end)),
        colorArgb = colorArgb,
    )
```

`WORK_COLOR` is a `Long` (`0xFF1E88E5L`).

- [ ] **Step 3:** Build, install, and on the emulator:
  - **Trips → Month:** next month can be selected.
  - **Agenda → Week**, a Wednesday: "Team standup" and "Code review" sit side by side; with "Stacked overlap" on, they stack.
- [ ] **Step 4:** Commit: `fix(sample): bookable month stays and overlapping demo events`.

---

### Task 10: Verification and hand-off

- [ ] **Step 1:** `./gradlew ktlintCheck detekt checkKotlinAbi :orrery-core:jvmTest :orrery-compose:verifyRoborazziAndroidHostTest :orrery-compose:desktopTest :orrery-compose:wasmJsTest :sample:assembleDebug`. Expected: only the 3 known goldens fail; `checkKotlinAbi` passes with no dump changes.
- [ ] **Step 2: Emulator pass.** Habits (today ring and progress ring distinct), Trips with the Full-cell style (turnover days on Nov 25 and Dec 2 show the divider), Almanac jump dialog (current year outlined), Agenda → Month (banners on next-month days in the last row are dimmed), Agenda → Day in landscape (time grid visible). Save screenshots to the scratchpad. Restore orientation and light mode afterwards.
- [ ] **Step 3:** Update `orrery-ui-audit-2026-11.md`: mark every item fixed on `fix/0.9.4-audit-polish`; keep only the skipped week-number item and the 3 baseline goldens as open.
- [ ] **Step 4:** Don't merge, push, tag or bump versions. Report and offer integration options (superpowers:finishing-a-development-branch).
