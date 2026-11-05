# Font Scale and 12/24-Hour Clock Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the library's calendar, agenda, banner and picker UI hold up at large system font sizes (1.3× and above), and make agenda and schedule times follow the device's 12/24-hour setting like the pickers already do.

**Architecture:** Every layout that hard-codes a height or width in dp around text that is sized in sp either measures the text or reserves space proportional to it. Times in Agenda rows, schedule tiles and the hour gutter switch from hard-coded "HH:mm" to the existing `PlatformDateFormat.formatTime(hour, minute, is24Hour)`, with an `is24Hour` parameter that defaults to `systemUses24HourClock()` (the same default `rememberOrreryDateTimePickerState` uses). Public signatures only gain parameters; old overloads stay as `@Deprecated(level = HIDDEN)` bridges.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Robolectric 4.17 (`@Config(fontScale = …)`), compose-ui-test, Roborazzi.

**Source:** second library pass of 2026-11-05 (memory file `orrery-ui-audit-2026-11.md`, items A1–A4 plus the picker-headline risk), confirmed on a Pixel 9 Pro emulator at font scale 1.3.

## Global Constraints

- **Branch:** `fix/0.9.3-font-scale`, created from `fix/0.9.2-ui-audit` (PR #111, not yet merged) because both touch `ScheduleDefaults.kt`, `DefaultDay.kt`, `CollapsibleCalendarScaffold.kt` and `CHANGELOG.md`. Open its PR against `fix/0.9.2-ui-audit`; after #111 merges, rebase onto `main` and retarget.
- Conventional Commits. **No `Co-Authored-By` lines and no mention of Claude/Anthropic** in commits or PRs.
- Explicit API mode: every new `public` declaration needs KDoc. No new dependencies.
- Binary compatibility: a changed public signature gets a new overload, and the old one stays as `@Deprecated("Kept for binary compatibility; use the overload with <param>.", level = DeprecationLevel.HIDDEN)`. Then run `./gradlew :orrery-compose:updateKotlinAbi`. The dump diff may only turn old entries `synthetic` and add new ones.
- Test commands (the `*Debug` aliases reject `--tests`):
  - one class: `./gradlew :orrery-compose:testAndroidHostTest --tests "<FQCN>"`
  - all goldens: `./gradlew :orrery-compose:verifyRoborazziAndroidHostTest`
  - record: `./gradlew :orrery-compose:recordRoborazziAndroidHostTest --tests "<FQCN>"` for only the affected snapshot classes. Then `git checkout --` every recorded PNG whose test was passing, since recording also rewrites sub-threshold noise.
- **Known baseline failures:** `DateTimePickerDialogSnapshotTest.timeStepLight`, `DateTimePickerDialogSnapshotTest.stepTransitionMidway` and `CompactHeightDialogSnapshotTest.dateTimePickerTimeStepCompactHeight` fail on `main` by a few anti-aliased pixels. Never record them. "Tests pass" means no other failures.
- Snapshot policy: open every changed PNG's `_compare.png` in `orrery-compose/build/outputs/roborazzi/` and confirm the change is the intended fix before recording.
- Tests that measure text (line counts, bounds against text) need `@GraphicsMode(GraphicsMode.Mode.NATIVE)`. Without it, Robolectric measures text as about 0 width and the test passes without testing anything.
- Font scale in tests: `@Config(fontScale = 1.3f)` on the test method or class (Robolectric applies it to the `Configuration`, so `LocalDensity.current.fontScale` is 1.3).
- After each task: `./gradlew :orrery-compose:ktlintCheck :orrery-compose:detekt`, then append one line under `## [Unreleased]` → `### Fixed` (or `### Added`) in `CHANGELOG.md`.
- Final CI-equivalent check (iOS link tasks can't run on this Mac; they run in CI):
  `./gradlew ktlintCheck detekt checkKotlinAbi :orrery-core:jvmTest :orrery-core:wasmJsTest :orrery-compose:verifyRoborazziAndroidHostTest :orrery-compose:desktopTest :orrery-compose:wasmJsTest :sample:assembleDebug`

## Decision for the user before Task 6

Task 6 changes the default look of Agenda and schedule times in 12-hour locales: "13:30" becomes "1:30 PM", and the hour gutter shows "1:00 PM". This matches the pickers and Android's own calendar. Existing tests keep their 24-hour assertions by pinning the clock to 24-hour in `@Before`, so goldens don't change; new tests cover the 12-hour path. If the user prefers to keep 24-hour as the default, change only the default values of the new `is24Hour` parameters to `true` and drop the 12-hour default test.

## Out of scope

- Week numbers on the remaining `WeekCalendar` overloads, `YearCalendar` and the schedules.
- Dimming month banners drawn over out-of-month cells (not reproduced yet).
- An hour-only format ("1 PM") for the gutter. That needs new `expect`/`actual` code on all platforms; the gutter instead widens to fit the full time.

## File Structure

| File                                                                                                                       | Change                                                       | Task   |
| -------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------ | ------ |
| `orrery-compose/src/commonMain/.../DefaultDay.kt`                                                                          | vertical decorator band around number and label              | 1      |
| `orrery-compose/src/commonMain/.../CollapsibleCalendarScaffold.kt`                                                         | row height measured, `dayHeight` becomes a minimum           | 2      |
| `orrery-compose/src/commonMain/.../WeekEventBanners.kt`                                                                    | lane height at least one text line                           | 3      |
| `orrery-compose/src/commonMain/.../AgendaDefaults.kt`                                                                      | measured time column, title/subtitle line limits, `is24Hour` | 4, 6   |
| `orrery-compose/src/commonMain/.../FittingHeadline.kt`                                                                     | **create**: one-line headline that steps down a style        | 5      |
| `orrery-compose/src/commonMain/.../DatePickerDialog.kt`, `MonthPickerDialog.kt`, `DateRangePickerDialog.kt`                | use `FittingHeadline`                                        | 5      |
| `orrery-compose/src/commonMain/.../ScheduleDefaults.kt`                                                                    | `HourLabel` / `EventTile` `is24Hour`, `hourLabelText`        | 6      |
| `orrery-compose/src/commonMain/.../ScheduleView.kt`                                                                        | `is24Hour` param, gutter width fits labels                   | 6      |
| `orrery-compose/src/androidHostTest/.../FontScaleLayoutTest.kt`                                                            | **create**: tests for Tasks 1–5                              | 1–5    |
| `orrery-compose/src/androidHostTest/.../ClockFormatTest.kt`                                                                | **create**: 12/24-hour tests                                 | 6      |
| `orrery-compose/src/androidHostTest/.../TestClock.kt`                                                                      | **create**: `setSystem24HourClock` helper                    | 6      |
| `AgendaTest.kt`, `AgendaSnapshotTest.kt`, `ScheduleViewTest.kt`, `ScheduleViewPowerTest.kt`, `ScheduleViewSnapshotTest.kt` | pin 24-hour in `@Before`                                     | 6      |
| `orrery-compose/api/**`, `CHANGELOG.md`                                                                                    | ABI dumps, changelog                                         | 6, all |

---

### Task 0: Branch and baseline

- [ ] **Step 1:** `git checkout fix/0.9.2-ui-audit && git pull && git checkout -b fix/0.9.3-font-scale`
- [ ] **Step 2:** `./gradlew :orrery-compose:verifyRoborazziAndroidHostTest`. Expected: only the 3 known baseline failures. If anything else fails, stop and report.
- [ ] **Step 3:** Create `orrery-compose/src/androidHostTest/kotlin/me/kozakov/orrery/compose/FontScaleLayoutTest.kt`; Tasks 1–5 add tests to it:

```kotlin
package me.kozakov.orrery.compose

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Large system text (1.3×) with real font metrics.
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(fontScale = 1.3f)
class FontScaleLayoutTest {
    @get:Rule val rule = createComposeRule()

    private fun SemanticsNodeInteraction.lineCount(): Int {
        val results = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single().lineCount
    }
}
```

No commit yet; the file is committed with Task 1.

---

### Task 1: Day cells keep decorators clear of the label (A1, part 1)

At 1.3× a labelled day cell is exactly as tall as its number plus label. Bottom decorators (dots at 3dp from the bottom, bars at 1dp) then draw on the label, and the selection circle fits the text with no margin. `DefaultDay` gets a vertical band of `DecoratorBand = 6.dp` above and below its number/label column, only when the cell has both a label and decorators. The band is symmetric, so the content stays centred and the cell only grows when content + 12dp exceeds `minCellHeight`.

**Files:**

- Modify: `orrery-compose/src/commonMain/kotlin/me/kozakov/orrery/compose/DefaultDay.kt` (the number/label `Column`, around line 530)
- Test: `FontScaleLayoutTest.kt`

**Interfaces:** Produces `internal val DecoratorBand: Dp = 6.dp` in `DefaultDay.kt`.

- [ ] **Step 1: Failing test.** Imports: `androidx.compose.foundation.layout.width`, `androidx.compose.material3.MaterialTheme`, `androidx.compose.ui.Modifier`, `androidx.compose.ui.graphics.Color`, `androidx.compose.ui.platform.testTag`, `androidx.compose.ui.test.getUnclippedBoundsInRoot`, `androidx.compose.ui.test.onNodeWithTag`, `androidx.compose.ui.test.onNodeWithText`, `androidx.compose.ui.unit.dp`, `kotlinx.datetime.LocalDate`, `me.kozakov.orrery.core.CalendarDay`, `me.kozakov.orrery.core.DayInfo`, `me.kozakov.orrery.core.DayPosition`, `org.junit.Assert.assertTrue`, `org.junit.Test`.

```kotlin
    @Test
    fun labelledDayLeavesRoomForDecoratorsBelowTheLabel() {
        rule.setContent {
            MaterialTheme {
                DefaultDay(
                    day = CalendarDay(LocalDate(2026, 7, 8), DayPosition.MonthDate),
                    modifier = Modifier.width(52.dp).testTag("cell"),
                    today = LocalDate(2026, 7, 15),
                    info = DayInfo(label = "廿四"),
                    decorators = listOf(DayDecorators.eventDots { listOf(Color.Red) }),
                )
            }
        }
        val cell = rule.onNodeWithTag("cell").getUnclippedBoundsInRoot()
        val label = rule.onNodeWithText("廿四").getUnclippedBoundsInRoot()
        assertTrue("label bottom ${label.bottom}, cell bottom ${cell.bottom}", cell.bottom - label.bottom >= 5.9.dp)
    }
```

- [ ] **Step 2: Run it** (`--tests "me.kozakov.orrery.compose.FontScaleLayoutTest"`). Expected: FAIL, with the gap about 0dp.
- [ ] **Step 3: Implement.** Below `OUT_DATE_DECORATOR_ALPHA` add:

```kotlin
/** Space kept above and below a labelled day's text so bottom decorators never draw over the label. */
internal val DecoratorBand = 6.dp
```

Above the number/label `Column`, add:

```kotlin
        val reserveDecoratorBand = label != null && decorators.isNotEmpty()
        val decoratorBandPx = with(LocalDensity.current) { DecoratorBand.toPx() }
```

and change the column's modifier from

```kotlin
            modifier = Modifier.onPlaced { contentBottomPx = it.boundsInParent().bottom },
```

to

```kotlin
            modifier =
                Modifier
                    .then(if (reserveDecoratorBand) Modifier.padding(vertical = DecoratorBand) else Modifier)
                    // onPlaced sees the padded bounds; contentBottom must stay at the label's real bottom.
                    .onPlaced {
                        val band = if (reserveDecoratorBand) decoratorBandPx else 0f
                        contentBottomPx = it.boundsInParent().bottom - band
                    },
```

Import `androidx.compose.ui.platform.LocalDensity` if it's missing.

- [ ] **Step 4: Run the test class.** Expected: PASS. Then run `SpanningBarsTest`, `OutDateDimmingTest` and `DayDecoratorsTest`. Expected: PASS.
- [ ] **Step 5: Screenshots.** Run `verifyRoborazziAndroidHostTest`. Snapshots with labelled _and_ decorated days at 1.0× may grow by a few dp (`DefaultDayPolishSnapshotTest.selectedRangeWithLabelsAndDecorators*`, `DecoratorsSnapshotTest` labelled cases). Confirm in the compare images that the dots/bars now sit below the labels and nothing else moved, then record those classes.
- [ ] **Step 6: Changelog and commit.** "`DefaultDay` keeps a 6dp band above and below a labelled day's text when it has decorators, so dots and bars no longer draw over the label at large font sizes."

```bash
git add orrery-compose CHANGELOG.md
git commit -m "fix(compose): keep decorators clear of day labels at large font sizes"
```

---

### Task 2: The collapsible scaffold uses the real row height (A1, part 2)

`CollapsibleGeometry` sizes the clip box and the collapse offset from `dayHeight` (48dp). Month rows wrap their content, so at large font sizes (or with Task 1's band) they are taller, the last row is clipped, and the Two Weeks and Week anchors show the wrong rows. The scaffold records the real row height from whichever grid is on screen, and `dayHeight` becomes the minimum.

**Files:**

- Modify: `orrery-compose/src/commonMain/kotlin/me/kozakov/orrery/compose/CollapsibleCalendarScaffold.kt` (`CollapsibleCalendarScaffoldImpl`, around lines 450–590; the KDoc of both public overloads)
- Test: `FontScaleLayoutTest.kt`

- [ ] **Step 1: Failing tests.** Extra imports: `androidx.compose.ui.test.assertIsNotDisplayed`, `androidx.compose.ui.test.hasContentDescription`, `androidx.compose.ui.test.onFirst`, `kotlinx.datetime.DayOfWeek`, `kotlinx.datetime.YearMonth`.

```kotlin
    private fun scaffold(anchor: CollapsibleCalendarAnchor) {
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
                            firstVisibleDate = LocalDate(2026, 7, 13),
                            firstDayOfWeek = DayOfWeek.MONDAY,
                        ),
                    state = rememberCollapsibleCalendarState(initialAnchor = anchor, enableTwoWeekStop = true),
                    modifier = Modifier.testTag("scaffold"),
                    dayContent = { day ->
                        DefaultDay(day = day, today = LocalDate(2026, 7, 15), info = DayInfo(label = "初一"))
                    },
                ) {
                    androidx.compose.foundation.layout.Box(Modifier.testTag("below"))
                }
            }
        }
    }

    private fun cell(description: String) =
        rule.onAllNodes(hasContentDescription(description, substring = true)).onFirst()

    @Test
    fun expandedScaffoldShowsTheLastRowUnclipped() {
        scaffold(CollapsibleCalendarAnchor.Month)
        val lastCell = cell("July 31, 2026").getUnclippedBoundsInRoot()
        val below = rule.onNodeWithTag("below").getUnclippedBoundsInRoot()
        assertTrue("last row ends ${lastCell.bottom}, content starts ${below.top}", lastCell.bottom <= below.top + 0.5.dp)
    }

    @Test
    fun twoWeekAnchorShowsExactlyTheAnchorRowAndItsNeighbour() {
        scaffold(CollapsibleCalendarAnchor.TwoWeeks)
        val anchorCell = cell("July 13, 2026").getUnclippedBoundsInRoot()
        val nextCell = cell("July 20, 2026").getUnclippedBoundsInRoot()
        val below = rule.onNodeWithTag("below").getUnclippedBoundsInRoot()
        assertTrue("anchor row top ${anchorCell.top} is hidden", anchorCell.top >= rule.onNodeWithTag("scaffold").getUnclippedBoundsInRoot().top)
        assertTrue("second row ends ${nextCell.bottom} past ${below.top}", nextCell.bottom <= below.top + 0.5.dp)
        cell("July 27, 2026").assertIsNotDisplayed()
    }
```

Keep the `androidx.compose.foundation.layout.Box` reference fully qualified, or import `Box`.

- [ ] **Step 2: Run them.** Expected: at least `expandedScaffoldShowsTheLastRowUnclipped` FAILS (the last row extends past the clip box).
- [ ] **Step 3: Implement.** In `CollapsibleCalendarScaffoldImpl`, replace

```kotlin
    val dayPx = with(density) { dayHeight.toPx() }
    val geometry = remember(dayPx, rows, anchorRow) { CollapsibleGeometry(dayPx, rows, anchorRow) }
```

with

```kotlin
    val minRowPx = with(density) { dayHeight.toPx() }
    // Rows wrap their day cells, so large text or labels make them taller than dayHeight.
    var measuredRowPx by remember { mutableFloatStateOf(0f) }
    val rowPx = maxOf(minRowPx, measuredRowPx)
    val geometry = remember(rowPx, rows, anchorRow) { CollapsibleGeometry(rowPx, rows, anchorRow) }
```

Then add a size listener to each of the two grids, after their existing modifiers:

```kotlin
                        modifier =
                            Modifier
                                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                                .onSizeChanged { if (it.height > 0) measuredRowPx = it.height.toFloat() / rows }
                                .offset { IntOffset(0, -geometry.shiftAt(state.expandFraction).roundToInt()) },
```

```kotlin
                    WeekCalendar(
                        state = weekState,
                        modifier = Modifier.onSizeChanged { if (it.height > 0) measuredRowPx = it.height.toFloat() },
                        showWeekNumbers = weekNumber != null,
                        weekNumber = weekNumber ?: { CalendarDefaults.WeekNumber(it) },
                        weekHeader = null,
                        dayContent = dayContent,
                    )
```

Imports: `androidx.compose.runtime.mutableFloatStateOf`, `androidx.compose.runtime.setValue` (if missing), `androidx.compose.ui.layout.onSizeChanged`. In both public overloads' KDoc, add: "`dayHeight` is the minimum row height; rows taller than it (large text, labels) are measured and used for the collapse geometry."

- [ ] **Step 4: Run the class and the existing scaffold tests:** `FontScaleLayoutTest`, `CollapsibleScaffoldTest`, `CollapsibleWeekNumbersTest`, `CollapsibleGestureScrollSourceTest`, `AnimatedMonthHeightTest`. Expected: PASS. If a test asserted exact 48dp heights, report it before changing it.
- [ ] **Step 5: Screenshots.** Run `verifyRoborazziAndroidHostTest`. `CollapsibleScaffoldSnapshotTest` may change where rows were taller than 48dp (the clipped bottom row now shows). Review and record that class only.
- [ ] **Step 6: Changelog and commit.** "`CollapsibleCalendarScaffold` measures its rows, so a month with tall rows (large text, labels) is no longer clipped and the Two Weeks/Week stops show the right rows; `dayHeight` is now the minimum row height."

```bash
git add orrery-compose CHANGELOG.md
git commit -m "fix(compose): size the collapsible scaffold from measured row height"
```

---

### Task 3: Month banner lanes fit one line of text (A3)

`WeekEventBanners` measures each chip at exactly `laneHeight` (18dp), so at 1.3× the descenders of `labelSmall` text are cut off. The effective lane height becomes the larger of `laneHeight` and one line of `labelSmall` text plus 2dp.

**Files:**

- Modify: `orrery-compose/src/commonMain/kotlin/me/kozakov/orrery/compose/WeekEventBanners.kt` (`WeekEventBannersContent`; the KDoc of both `WeekEventBanners` overloads, `@param laneHeight`)
- Test: `FontScaleLayoutTest.kt`

- [ ] **Step 1: Failing test.** Extra imports: `me.kozakov.orrery.core.CalendarEvent`, `me.kozakov.orrery.core.EventSpan`, `me.kozakov.orrery.core.packWeekEventBanners`.

```kotlin
    @Test
    fun bannerLaneIsAtLeastOneLineOfText() {
        val week = (6..12).map { LocalDate(2026, 7, it) }
        val gym = CalendarEvent<Nothing>(id = "g", title = "Gym", span = EventSpan.AllDay(LocalDate(2026, 7, 8)))
        rule.setContent {
            MaterialTheme {
                WeekEventBanners(
                    packResult = packWeekEventBanners(week, listOf(gym)),
                    modifier = Modifier.testTag("banners"),
                    topInset = 0.dp,
                )
            }
        }
        val lane = rule.onNodeWithTag("banners").getUnclippedBoundsInRoot()
        val text = rule.onNodeWithText("Gym").getUnclippedBoundsInRoot()
        assertTrue("lane ${lane.height} < text ${text.height}", lane.height >= text.height)
    }
```

- [ ] **Step 2: Run it.** Expected: FAIL (lane 18dp, text taller).
- [ ] **Step 3: Implement.** At the top of `WeekEventBannersContent`, before `Layout(`:

```kotlin
    val textMeasurer = rememberTextMeasurer()
    val bannerTextStyle = MaterialTheme.typography.labelSmall
    // One line of banner text plus a little air, so large system text is never cut off.
    val minLanePx =
        remember(textMeasurer, bannerTextStyle) {
            textMeasurer.measure("Ag", bannerTextStyle, maxLines = 1).size.height
        }
```

In the measure block, replace `val laneHeight = metrics.laneHeight.roundToPx()` with

```kotlin
        val laneHeight = maxOf(metrics.laneHeight.roundToPx(), minLanePx + BannerTextVerticalPadding.roundToPx())
```

and add next to `BannerEdgeInset`:

```kotlin
private val BannerTextVerticalPadding = 2.dp
```

Imports: `androidx.compose.ui.text.rememberTextMeasurer`. Update both overloads' `@param laneHeight`: "Minimum height of one banner lane; lanes grow to fit one line of text at large font sizes."

- [ ] **Step 4: Run the test**, then `WeekEventBannersTest` (compose) if present (`ls orrery-compose/src/androidHostTest/kotlin/me/kozakov/orrery/compose | grep -i banner`). Expected: PASS.
- [ ] **Step 5: Screenshots.** At 1.0×, `labelSmall` (11sp, about 16dp line) + 2dp ≤ 18dp, so goldens should not change. If some do, review them before recording.
- [ ] **Step 6: Changelog and commit.** "`WeekEventBanners` lanes grow to fit one line of text, so banner titles are no longer clipped at large font sizes."

```bash
git add orrery-compose CHANGELOG.md
git commit -m "fix(compose): grow month banner lanes to fit their text"
```

---

### Task 4: Agenda rows keep the time on one line (A2)

`AgendaDefaults.EventRow` gives the time a fixed 104dp column with no line limit, so "12:30 – 13:30" wraps at 1.3×; the title and subtitle are unbounded too. The time column becomes the larger of 104dp and the measured width of the widest time label (`"00:00 – 24:00"` for now; Task 6 makes this clock-aware). It stays the same width on every row, so titles stay aligned. Title: at most 2 lines; subtitle: 1 line, both ellipsized.

**Files:**

- Modify: `orrery-compose/src/commonMain/kotlin/me/kozakov/orrery/compose/AgendaDefaults.kt` (`EventRow`)
- Test: `FontScaleLayoutTest.kt`

**Interfaces:** Produces `private fun widestTimeLabel(strings: AgendaStrings): String` in `AgendaDefaults.kt`; Task 6 adds an `is24Hour` parameter to it.

- [ ] **Step 1: Failing test.** Extra import: `kotlinx.datetime.LocalDateTime`.

```kotlin
    @Test
    fun agendaTimeLabelStaysOnOneLine() {
        val lunch =
            CalendarEvent<Nothing>(
                id = "l",
                title = "Lunch with Sam",
                span = EventSpan.Timed(LocalDateTime(2026, 7, 15, 12, 30), LocalDateTime(2026, 7, 15, 13, 30)),
            )
        rule.setContent {
            MaterialTheme { AgendaDefaults.EventRow(lunch, date = LocalDate(2026, 7, 15)) }
        }
        assertEquals(1, rule.onNodeWithText("12:30 – 13:30").lineCount())
    }
```

(Import `org.junit.Assert.assertEquals`.)

- [ ] **Step 2: Run it.** Expected: FAIL (2 lines).
- [ ] **Step 3: Implement.** In `EventRow`, before `Row(`:

```kotlin
        val timeStyle = MaterialTheme.typography.labelLarge
        val textMeasurer = rememberTextMeasurer()
        val density = LocalDensity.current
        // Same width on every row so titles line up; at least 104dp, wider when large text needs it.
        val timeColumnWidth =
            remember(textMeasurer, timeStyle, density, strings) {
                val widest = textMeasurer.measure(widestTimeLabel(strings), timeStyle, maxLines = 1).size.width
                maxOf(104.dp, with(density) { widest.toDp() })
            }
```

Replace the time `Text` and the title/subtitle `Text`s with:

```kotlin
            Text(
                text = timeText,
                modifier = Modifier.width(timeColumnWidth),
                style = timeStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
```

Add below `eventTimeLabel`:

```kotlin
/** The widest label the time column has to fit: an all-day label or a full-day time range. */
private fun widestTimeLabel(strings: AgendaStrings): String =
    listOf(strings.allDayLabel, "00:00 – 24:00").maxBy { it.length }
```

Imports as needed: `androidx.compose.ui.platform.LocalDensity`, `androidx.compose.ui.text.rememberTextMeasurer`, `androidx.compose.ui.text.style.TextOverflow`. Update the `EventRow` KDoc: "The time column is at least 104dp and widens to fit its longest label; the title shows up to two lines and the subtitle one, both ellipsized."

- [ ] **Step 4: Run** `FontScaleLayoutTest`, `AgendaTest`. Expected: PASS.
- [ ] **Step 5: Screenshots.** At 1.0× the column stays 104dp, so `AgendaSnapshotTest` should not change except where a long title now ellipsizes at 2 lines. Review before recording.
- [ ] **Step 6: Changelog and commit.** "`AgendaDefaults.EventRow` widens its time column to fit the time on one line at large font sizes, and limits titles to two lines and subtitles to one."

```bash
git add orrery-compose CHANGELOG.md
git commit -m "fix(compose): keep agenda times on one line at large font sizes"
```

---

### Task 5: Picker headlines stay on one line

The date, month and date-range dialogs show their headline with no line limit, the same pattern the date-time dialog had before #111. A shared internal `FittingHeadline` shows one line in the given style. If the text would wrap, it steps down to a compact style; if that still doesn't fit, it ellipsizes.

**Files:**

- Create: `orrery-compose/src/commonMain/kotlin/me/kozakov/orrery/compose/FittingHeadline.kt`
- Modify: `DatePickerDialog.kt` (headline `Text`, around line 86), `MonthPickerDialog.kt` (around line 124), `DateRangePickerDialog.kt` (around line 105)
- Test: `FontScaleLayoutTest.kt`

**Interfaces:** Produces `@Composable internal fun FittingHeadline(text: String, style: TextStyle, compactStyle: TextStyle, modifier: Modifier = Modifier)`.

- [ ] **Step 1: Failing tests.** Extra imports: `androidx.compose.material3.Text`, `androidx.compose.material3.TextButton`, `com.github.takahirom.roborazzi.RobolectricDeviceQualifiers`. These need a phone-sized screen, so annotate the methods with `@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5, fontScale = 1.3f)`.

```kotlin
    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel5, fontScale = 1.3f)
    fun rangePickerHeadlineStaysOnOneLine() {
        rule.setContent {
            MaterialTheme {
                OrreryDateRangePickerDialog(
                    onDismissRequest = {},
                    confirmButton = { TextButton(onClick = {}) { Text("Save") } },
                    state =
                        rememberOrreryDateRangePickerState(
                            initialStartDate = LocalDate(2026, 9, 28),
                            initialEndDate = LocalDate(2026, 10, 30),
                            initialDisplayedMonth = YearMonth(2026, 9),
                        ),
                    today = LocalDate(2026, 9, 15),
                )
            }
        }
        assertEquals(1, rule.onNodeWithText("Sep 28, 2026 – Oct 30, 2026").lineCount())
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel5, fontScale = 1.3f)
    fun datePickerHeadlineStaysOnOneLine() {
        rule.setContent {
            MaterialTheme {
                OrreryDatePickerDialog(
                    onDismissRequest = {},
                    confirmButton = { TextButton(onClick = {}) { Text("OK") } },
                    state = rememberOrreryDatePickerState(initialDate = LocalDate(2026, 9, 28)),
                    today = LocalDate(2026, 9, 15),
                )
            }
        }
        assertEquals(1, rule.onNodeWithText("Sep 28, 2026").lineCount())
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel5, fontScale = 1.3f)
    fun monthPickerHeadlineStaysOnOneLine() {
        rule.setContent {
            MaterialTheme {
                OrreryMonthPickerDialog(
                    onDismissRequest = {},
                    confirmButton = { TextButton(onClick = {}) { Text("OK") } },
                    state = rememberOrreryMonthPickerState(initialSelectedMonth = YearMonth(2026, 9)),
                    today = LocalDate(2026, 9, 15),
                )
            }
        }
        assertEquals(1, rule.onNodeWithText("September 2026").lineCount())
    }
```

Factory names checked against the code: `rememberOrreryDatePickerState(initialDate)`, `rememberOrreryMonthPickerState(initialSelectedMonth)`, `rememberOrreryDateRangePickerState(initialStartDate, initialEndDate, initialDisplayedMonth)`. The expected headline strings come from `PlatformDateFormat.formatDate(…, Medium)` and `yearMonthTitle`.

- [ ] **Step 2: Run them.** Expected: at least the range headline FAILS (2 lines). Any test that already passes stays as a regression guard; note it in the commit body.
- [ ] **Step 3: Create `FittingHeadline.kt`:**

```kotlin
package me.kozakov.orrery.compose

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

/**
 * A dialog headline on one line: [style] when it fits, otherwise [compactStyle],
 * ellipsized if even that is too wide. Resets to [style] when [text] changes.
 */
@Composable
internal fun FittingHeadline(
    text: String,
    style: TextStyle,
    compactStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    var compact by remember(text) { mutableStateOf(false) }
    Text(
        text = text,
        modifier = modifier,
        style = if (compact) compactStyle else style,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { if (!compact && it.didOverflowHeight) compact = true },
    )
}
```

Replace the three headline `Text`s:

- `DatePickerDialog.kt`: `FittingHeadline(text = date?.let { PlatformDateFormat.formatDate(it, DateFormattingStyle.Medium) } ?: strings.headlinePlaceholder, style = MaterialTheme.typography.headlineLarge, compactStyle = MaterialTheme.typography.headlineSmall)`
- `MonthPickerDialog.kt`: `FittingHeadline(text = state.selectedMonth?.let(PlatformDateFormat::yearMonthTitle) ?: strings.headlinePlaceholder, style = MaterialTheme.typography.headlineLarge, compactStyle = MaterialTheme.typography.headlineSmall)`
- `DateRangePickerDialog.kt`: `FittingHeadline(text = "$start – $end", style = MaterialTheme.typography.headlineSmall, compactStyle = MaterialTheme.typography.titleLarge)`

Remove the now-unused `Text` imports only if nothing else in those files uses them.

- [ ] **Step 4: Run** `FontScaleLayoutTest`, `DatePickerDialogTest`, `MonthPickerDialogTest`, `DateRangePickerDialogTest`. Expected: PASS.
- [ ] **Step 5: Screenshots.** At 1.0× the headlines fit, so no golden should change. If any does, review it.
- [ ] **Step 6: Changelog and commit.** "Date, month and date-range picker headlines stay on one line, stepping down to a smaller type style at large font sizes."

```bash
git add orrery-compose CHANGELOG.md
git commit -m "fix(compose): keep date, month and range picker headlines on one line"
```

---

### Task 6: Agenda and schedule times follow the device's 12/24-hour setting (A4)

Agenda rows, schedule tiles and hour labels hard-code "HH:mm", while the pickers follow the device. Each gets an `is24Hour: Boolean = systemUses24HourClock()` parameter and formats with `PlatformDateFormat.formatTime`. The schedule's time gutter widens to fit its widest hour label. **Confirm the decision at the top of this plan first.**

**Files:**

- Create: `orrery-compose/src/androidHostTest/kotlin/me/kozakov/orrery/compose/TestClock.kt`, `ClockFormatTest.kt`
- Modify: `AgendaDefaults.kt` (`EventRow`, `eventTimeLabel`, `widestTimeLabel`), `ScheduleDefaults.kt` (`HourLabel`, `EventTile`, `minuteLabel`), `ScheduleView.kt` (the four `DaySchedule`/`WeekSchedule` overloads' default slot lambdas, `SchedulePage` gutter width), `AgendaTest.kt`, `AgendaSnapshotTest.kt`, `ScheduleViewTest.kt`, `ScheduleViewPowerTest.kt`, `ScheduleViewSnapshotTest.kt`, `orrery-compose/api/**`

**Interfaces:**

- Consumes `widestTimeLabel` from Task 4.
- Produces:
  - `internal fun eventTimeLabel(span: EventSpan, date: LocalDate?, strings: AgendaStrings, is24Hour: Boolean): String`
  - `internal fun clockLabel(minuteOfDay: Int, is24Hour: Boolean): String`
  - `internal fun setSystem24HourClock(on: Boolean)` (test only)

- [ ] **Step 1: Test helper.** Create `TestClock.kt`:

```kotlin
package me.kozakov.orrery.compose

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider

/** Sets Android's "Use 24-hour format", which DateFormat.is24HourFormat (and so systemUses24HourClock) reads. */
internal fun setSystem24HourClock(on: Boolean) {
    val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
    Settings.System.putString(resolver, Settings.System.TIME_12_24, if (on) "24" else "12")
}
```

- [ ] **Step 2: Pin existing tests to 24-hour.** In `AgendaTest`, `AgendaSnapshotTest`, `ScheduleViewTest`, `ScheduleViewPowerTest` and `ScheduleViewSnapshotTest`, add (importing `org.junit.Before`):

```kotlin
    @Before
    fun use24HourClock() = setSystem24HourClock(true)
```

If a class already has a `@Before`, add the call inside it instead.

- [ ] **Step 3: Failing tests.** Create `ClockFormatTest.kt`:

```kotlin
package me.kozakov.orrery.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.EventSpan
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// en-US with "Use 24-hour format" off: times read like "12:30 PM".
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS")
class ClockFormatTest {
    @get:Rule val rule = createComposeRule()

    private val day = LocalDate(2026, 7, 15)
    private val lunch =
        CalendarEvent<Nothing>(
            id = "l",
            title = "Lunch",
            span = EventSpan.Timed(LocalDateTime(2026, 7, 15, 12, 30), LocalDateTime(2026, 7, 15, 13, 30)),
        )

    @Before
    fun use12HourClock() = setSystem24HourClock(false)

    @Test
    fun agendaRowFollowsTheDeviceClock() {
        rule.setContent { MaterialTheme { AgendaDefaults.EventRow(lunch, date = day) } }
        rule.onNode(hasText("12:30", substring = true) and hasText("PM", substring = true)).assertExists()
    }

    @Test
    fun scheduleTileAndHourLabelsFollowTheDeviceClock() {
        rule.setContent {
            MaterialTheme {
                DaySchedule(
                    events = { if (it == day) listOf(lunch) else emptyList() },
                    modifier = Modifier.fillMaxWidth().height(600.dp),
                    state =
                        rememberScheduleState(
                            firstVisibleDate = day,
                            firstDayOfWeek = DayOfWeek.MONDAY,
                            initialScrollTime = LocalTime(11, 0),
                        ),
                    today = day,
                )
            }
        }
        rule.onNode(hasText("1:00", substring = true) and hasText("PM", substring = true)).assertExists()
        rule.onNode(hasText("12:30", substring = true) and hasText("PM", substring = true)).assertExists()
    }

    @Test
    fun explicit24HourOverridesTheDevice() {
        rule.setContent { MaterialTheme { AgendaDefaults.EventRow(lunch, date = day, is24Hour = true) } }
        rule.onNodeWithText("12:30 – 13:30").assertExists()
    }
}
```

The matchers check "12:30" and "PM" separately because CLDR may put a narrow no-break space between them.

- [ ] **Step 4: Run** `ClockFormatTest`. Expected: compile error (`is24Hour` doesn't exist).
- [ ] **Step 5: Core formatting.** In `AgendaDefaults.kt`, replace `eventTimeLabel` and `formatHhMm` with:

```kotlin
internal fun eventTimeLabel(
    span: EventSpan,
    date: LocalDate?,
    strings: AgendaStrings,
    is24Hour: Boolean,
): String =
    when (span) {
        is EventSpan.AllDay -> strings.allDayLabel
        is EventSpan.Timed -> {
            val startsBefore = if (date != null) span.start.date < date else span.start.date < span.end.date
            val startMinute = if (startsBefore) 0 else span.start.time.hour * 60 + span.start.time.minute
            val endMinute =
                if (date != null && span.end.date > date) MINUTES_PER_DAY else span.end.time.hour * 60 + span.end.time.minute
            clockLabel(startMinute, is24Hour) + " – " + clockLabel(endMinute, is24Hour)
        }
    }

private const val MINUTES_PER_DAY = 24 * 60

/**
 * A minute of the day on the given clock. 24:00 (an event running past midnight)
 * keeps its 24-hour spelling, and becomes 12:00 AM on a 12-hour clock.
 */
internal fun clockLabel(
    minuteOfDay: Int,
    is24Hour: Boolean,
): String =
    if (is24Hour) {
        (minuteOfDay / 60).toString().padStart(2, '0') + ":" + (minuteOfDay % 60).toString().padStart(2, '0')
    } else {
        PlatformDateFormat.formatTime((minuteOfDay / 60) % 24, minuteOfDay % 60, is24Hour = false)
    }
```

The 24-hour branch keeps today's exact "08:00" / "24:00" output, so pinned tests and goldens stay unchanged. Make `widestTimeLabel` clock-aware:

```kotlin
private fun widestTimeLabel(
    strings: AgendaStrings,
    is24Hour: Boolean,
): String = listOf(strings.allDayLabel, clockLabel(0, is24Hour) + " – " + clockLabel(MINUTES_PER_DAY, is24Hour)).maxBy { it.length }
```

In `ScheduleDefaults.kt`, delete `minuteLabel` and use `clockLabel(minute, is24Hour)` at its call site.

- [ ] **Step 6: Public parameters and hidden bridges.**
  - `AgendaDefaults.EventRow`: add `is24Hour: Boolean = systemUses24HourClock()` as the last parameter. Pass it to `eventTimeLabel` and `widestTimeLabel`, and add it to the `remember` keys. Keep the previous signature as a hidden bridge that calls the new one with `is24Hour = systemUses24HourClock()`. KDoc: "@param is24Hour Clock for the time label; defaults to the device setting, like the date-time picker."
  - `ScheduleDefaults.HourLabel(hour, colors, modifier, is24Hour = systemUses24HourClock())`: text becomes `clockLabel(hour * 60, is24Hour)`; add `softWrap = false`. Old signature becomes a hidden bridge.
  - `ScheduleDefaults.EventTile(positioned, colors, dimensions, modifier, onClick, is24Hour = systemUses24HourClock())`: the time line uses `clockLabel`. Old signature becomes a hidden bridge.
  - `DaySchedule`/`WeekSchedule`: their default `hourLabel` and `eventContent` lambdas already call `HourLabel(hour, colors)` / `EventTile(...)` and pick up the new defaults unchanged. Don't add a schedule-level parameter (YAGNI); callers who want a fixed clock pass their own slots.
- [ ] **Step 7: The gutter fits its labels.** In `SchedulePage` (`ScheduleView.kt`), before the header `Column`:

```kotlin
    val is24Hour = systemUses24HourClock()
    val labelStyle = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // Default hour labels can be wider than timeGutterWidth on a 12-hour clock or with large text.
    val gutterWidth =
        remember(is24Hour, labelStyle, density, measurer, dimensions.timeGutterWidth) {
            val widest =
                (0 until 24).maxOf { measurer.measure(clockLabel(it * 60, is24Hour), labelStyle).size.width }
            maxOf(dimensions.timeGutterWidth, with(density) { widest.toDp() } + 8.dp)
        }
```

Replace all three uses of `dimensions.timeGutterWidth` in `ScheduleView.kt` (the header `Spacer`, the `HourGutter` call and the `AllDayRow` label width) with `gutterWidth`. `AllDayRow` takes the width as a new `gutterWidth: Dp` parameter instead of reading `dimensions.timeGutterWidth`. Update the `ScheduleDimensions.timeGutterWidth` KDoc: "Minimum width of the hour gutter; it widens to fit the default hour labels."

- [ ] **Step 8: Run** `ClockFormatTest`, `AgendaTest`, `ScheduleViewTest`, `ScheduleViewPowerTest`, `ScheduleTileRenderingTest`, `FontScaleLayoutTest`. Expected: PASS. If `agendaTimeLabelStaysOnOneLine` (Task 4) now runs on a 12-hour default clock, give it `setSystem24HourClock(true)` at its start, since it asserts the 24-hour string.
- [ ] **Step 9: ABI and screenshots.** Run `./gradlew :orrery-compose:updateKotlinAbi` and check that the diff only adds the three new signatures and marks the old ones `synthetic`. Then `verifyRoborazziAndroidHostTest`: with existing classes pinned to 24-hour, expect **no** golden changes. If any change, the 24-hour path changed output; fix that rather than recording.
- [ ] **Step 10: Changelog and commit.** Under `### Changed`: "Agenda rows, schedule tiles and schedule hour labels follow the device's 12/24-hour setting (new `is24Hour` parameter on `AgendaDefaults.EventRow`, `ScheduleDefaults.HourLabel` and `ScheduleDefaults.EventTile`; previous signatures stay as hidden bridges), and the schedule's hour gutter widens to fit its labels."

```bash
git add orrery-compose CHANGELOG.md
git commit -m "feat(compose): agenda and schedule times follow the device 12/24-hour clock"
```

---

### Task 7: Full verification and on-device check

- [ ] **Step 1:** Run the final CI-equivalent command from Global Constraints. Expected: only the 3 known baseline goldens fail. Paste the result in the hand-off.
- [ ] **Step 2: Emulator** (`Pixel_9_Pro`). `./gradlew :sample:assembleDebug && adb install -r sample/build/outputs/apk/debug/sample-debug.apk`. Use `adb shell input tap` for taps, and wait about 1.5s before each `adb exec-out screencap -p`. **Never send BACK unless a screenshot confirms a dialog or sheet is open.**
  1. `adb shell settings put system font_scale 1.3`, relaunch:
     - **Almanac:** dots sit below the lunar labels; the selected day's circle doesn't cut its label; the last row (Nov 30 / Dec) isn't clipped; Collapse to Week and back shows the right week.
     - **Agenda → Month:** banner text isn't clipped ("Gym").
     - **Agenda list:** "12:30 – 13:30" is on one line.
     - **Pickers:** pick a range across months; the headline is one line.
  2. `adb shell settings put system font_scale 1.0` and `adb shell settings put system time_12_24 12`, relaunch:
     - **Agenda list** reads "12:30 PM – 1:30 PM"; **Day** view gutter shows "1:00 PM" uncut.
  3. Restore: `adb shell settings put system time_12_24 24` (or `delete system time_12_24` if it was unset; check first with `adb shell settings get system time_12_24`).
     Save screenshots to the scratchpad and list their paths.
- [ ] **Step 3: Memory.** In `orrery-ui-audit-2026-11.md`, mark A1–A4 and the headline risk fixed on `fix/0.9.3-font-scale`.
- [ ] **Step 4: Hand-off.** Don't merge, push, tag or bump versions. Report the branch, `git log --oneline fix/0.9.2-ui-audit..`, test output and screenshot paths, then ask how to integrate (superpowers:finishing-a-development-branch).
