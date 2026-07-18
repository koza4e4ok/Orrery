# Infinite Paging (0.5.0) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship Orrery 0.5.0: `CalendarState` and `WeekCalendarState` scroll unboundedly by default via nullable bounds and a huge effective window.

**Architecture:** Null bounds map to private effective extremes (`YearMonth(-19999, 1)`..`YearMonth(19999, 12)`; the LocalDate equivalents for weeks). All index math routes through the effective bounds unchanged. `animateScrollTo*` teleports to 2 items short of far targets before animating. `CalendarNavHeader` chevrons treat a null side as never-disabling.

**Tech Stack:** Kotlin, Jetpack Compose, kotlinx-datetime, Robolectric/compose-ui-test.

**Spec:** `docs/superpowers/specs/2026-07-18-infinite-paging-design.md`

## Global Constraints

- Explicit API mode: `public` + KDoc on all public declarations.
- No new dependencies; no orrery-core changes (`checkKotlinAbi` must pass untouched).
- Work on branch `feature/0.5.0-infinite-paging`; Conventional Commits; never mention Claude/Anthropic in commits.
- Module-scoped Gradle tasks only. Compose tests: `./gradlew :orrery-compose:testDebugUnitTest --tests "<class>"`.
- No new snapshots; `:orrery-compose:verifyRoborazziDebug` must pass with zero golden changes.
- Fixed reference dates: July 2026 (`YearMonth(2026, 7)`, today = `LocalDate(2026, 7, 15)`), `DayOfWeek.MONDAY` in tests.
- Nullable-bounds Saver entries use the `DatePickerState` precedent — store `bound?.toString() ?: ""`, restore with `takeIf(String::isNotEmpty)` — NOT null list entries (deviation from spec §5's "String? entries", noted for the reviewer: avoids null-in-bundle edge cases and matches the module's existing saver idiom).
- Full CI verification: `./gradlew ktlintCheck detekt checkKotlinAbi :orrery-core:test :orrery-lunar:test :orrery-compose:testDebugUnitTest :orrery-compose:verifyRoborazziDebug :sample:assembleDebug`.

## File Structure

| File                                                                             | Change                                                            |
| -------------------------------------------------------------------------------- | ----------------------------------------------------------------- |
| `orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/CalendarState.kt`      | Modify — nullable bounds, effective window, far-jump, saver       |
| `orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/NavHeader.kt`          | Modify — null-aware chevron enablement (lines 62, 95)             |
| `orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/WeekCalendarState.kt`  | Modify — same treatment for dates                                 |
| `orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/MonthYearPicker.kt`    | Modify — KDoc sentence only                                       |
| `orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/InfinitePagingTest.kt` | Create — unbounded/one-sided/restoration/far-jump for both states |
| `orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/NavHeaderTest.kt`      | Modify — add open-bounds enablement test                          |
| `CHANGELOG.md`, `README.md`, `gradle.properties`                                 | Release prep                                                      |

---

### Task 0: Branch

- [ ] `git checkout -b feature/0.5.0-infinite-paging` (from up-to-date `main`).

---

### Task 1: `CalendarState` nullable bounds + NavHeader

`NavHeader.kt:62,95` read `state.startMonth`/`state.endMonth` in comparisons, so the nullability change and the chevron fix must land together to compile.

**Files:**

- Modify: `orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/CalendarState.kt`
- Modify: `orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/NavHeader.kt:62,95`
- Create: `orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/InfinitePagingTest.kt` (calendar half)
- Modify: `orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/NavHeaderTest.kt` (one added test)

**Interfaces:**

- Produces (used by Task 2 as the pattern, and by callers):
  - `CalendarState.startMonth: YearMonth?` / `endMonth: YearMonth?` (private set)
  - `updateRange(startMonth: YearMonth?, endMonth: YearMonth?)`
  - `rememberCalendarState(startMonth: YearMonth? = null, endMonth: YearMonth? = null, ...)`
  - `internal const val FAR_JUMP_MONTHS = 12` and approach offset 2 months.

- [ ] **Step 1: Write the failing tests**

Create `InfinitePagingTest.kt` with the calendar-state half (Task 2 appends the week half). Follow `CalendarStateTest.kt`'s idioms — `lateinit state/scope`, content attaches `LazyRow(state = state.listState) { items(state.monthCount) { Box(Modifier.size(80.dp)) { Text("m$it") } } }`:

```kotlin
package me.kozakov.orrery.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InfinitePagingTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var state: CalendarState
    private lateinit var scope: CoroutineScope

    private fun calendarContent(
        startMonth: YearMonth? = null,
        endMonth: YearMonth? = null,
    ) {
        rule.setContent {
            scope = rememberCoroutineScope()
            state =
                rememberCalendarState(
                    startMonth = startMonth,
                    endMonth = endMonth,
                    firstVisibleMonth = YearMonth(2026, 7),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                )
            LazyRow(state = state.listState) {
                items(state.monthCount) { Box(Modifier.size(80.dp)) { Text("m$it") } }
            }
        }
    }

    @Test
    fun defaultStateIsUnbounded() {
        calendarContent()
        rule.runOnIdle {
            assertNull(state.startMonth)
            assertNull(state.endMonth)
            assertEquals(YearMonth(2026, 7), state.firstVisibleMonth)
        }
    }

    @Test
    fun scrollsCenturiesOutAndBack() {
        calendarContent()
        rule.waitForIdle()
        scope.launch { state.scrollToMonth(YearMonth(2526, 7)) }
        rule.runOnIdle { assertEquals(YearMonth(2526, 7), state.firstVisibleMonth) }
        scope.launch { state.scrollToMonth(YearMonth(1526, 7)) }
        rule.runOnIdle { assertEquals(YearMonth(1526, 7), state.firstVisibleMonth) }
    }

    @Test
    fun oneSidedStartClampsScroll() {
        calendarContent(startMonth = YearMonth(2026, 1))
        rule.waitForIdle()
        scope.launch { state.scrollToMonth(YearMonth(2020, 1)) }
        rule.runOnIdle { assertEquals(YearMonth(2026, 1), state.firstVisibleMonth) }
    }

    @Test
    fun updateRangeToUnboundedKeepsVisibleMonth() {
        calendarContent(startMonth = YearMonth(2026, 1), endMonth = YearMonth(2026, 12))
        rule.runOnIdle { state.updateRange(null, null) }
        rule.runOnIdle { assertEquals(YearMonth(2026, 7), state.firstVisibleMonth) }
    }

    @Test
    fun farAnimatedJumpLandsExactly() {
        calendarContent()
        rule.waitForIdle()
        scope.launch { state.animateScrollToMonth(YearMonth(2126, 7)) }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(YearMonth(2126, 7), state.firstVisibleMonth) }
    }

    @Test
    fun mixedBoundsSurviveRestoration() {
        val restorationTester = StateRestorationTester(rule)
        restorationTester.setContent {
            state =
                rememberCalendarState(
                    startMonth = YearMonth(2026, 1),
                    endMonth = null,
                    firstVisibleMonth = YearMonth(2026, 7),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                )
            LazyRow(state = state.listState) {
                items(state.monthCount) { Box(Modifier.size(80.dp)) { Text("m$it") } }
            }
        }
        restorationTester.emulateSavedInstanceStateRestore()
        rule.runOnIdle {
            assertEquals(YearMonth(2026, 1), state.startMonth)
            assertNull(state.endMonth)
            assertEquals(YearMonth(2026, 7), state.firstVisibleMonth)
        }
    }
}
```

Add to `NavHeaderTest.kt` (follow its existing `content(...)` helper — pass an unbounded state; check the helper's signature and adapt mechanically):

```kotlin
    @Test
    fun buttonsStayEnabledWithOpenBounds() {
        content(startMonth = null, endMonth = null)
        rule.onNodeWithContentDescription("Previous month").assertIsEnabled()
        rule.onNodeWithContentDescription("Next month").assertIsEnabled()
    }
```

(If `content` hard-codes non-null bounds, extend it with nullable defaulted parameters preserving current call sites.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :orrery-compose:testDebugUnitTest --tests "me.kozakov.orrery.compose.InfinitePagingTest"`
Expected: FAIL — compile error (`rememberCalendarState` has no nullable overload / type mismatch YearMonth? vs YearMonth).

- [ ] **Step 3: Implement `CalendarState` changes**

In `CalendarState.kt`:

```kotlin
/** Far animated jumps teleport near the target first (months). */
internal const val FAR_JUMP_MONTHS: Int = 12

private val UNBOUNDED_START_MONTH = YearMonth(-19999, 1)
private val UNBOUNDED_END_MONTH = YearMonth(19999, 12)
```

Property and constructor changes (constructor params become `YearMonth?`):

```kotlin
    /** Inclusive range start; null scrolls unboundedly into the past. */
    public var startMonth: YearMonth? by mutableStateOf(startMonth)
        private set

    /** Inclusive range end; null scrolls unboundedly into the future. */
    public var endMonth: YearMonth? by mutableStateOf(endMonth)
        private set

    private val effectiveStartMonth: YearMonth
        get() = startMonth ?: UNBOUNDED_START_MONTH

    private val effectiveEndMonth: YearMonth
        get() = endMonth ?: UNBOUNDED_END_MONTH
```

Route all math through the effective bounds (`monthCount`, `firstVisibleMonth`, `indexOf` — replace every `startMonth`/`endMonth` read inside computations with the effective properties). `monthCount` KDoc: "Months in the effective range; unbounded sides count to the supported date extremes." `updateRange`:

```kotlin
    /** Changes the month range (null = unbounded side), keeping the visible month if still in range. */
    public fun updateRange(
        startMonth: YearMonth?,
        endMonth: YearMonth?,
    ) {
        if (startMonth != null && endMonth != null) {
            require(startMonth <= endMonth) { "startMonth must be <= endMonth" }
        }
        val visible = firstVisibleMonth
        this.startMonth = startMonth
        this.endMonth = endMonth
        listState.requestScrollToItem(indexOf(visible))
    }
```

Far-jump in `animateScrollToMonth`:

```kotlin
    public suspend fun animateScrollToMonth(month: YearMonth) {
        val target = indexOf(month)
        val current = listState.firstVisibleItemIndex
        if (kotlin.math.abs(target - current) > FAR_JUMP_MONTHS * itemsPerMonth) {
            val approach =
                if (target > current) target - 2 * itemsPerMonth else target + 2 * itemsPerMonth
            listState.scrollToItem(approach.coerceIn(0, (monthCount - 1) * itemsPerMonth))
        }
        listState.animateScrollToItem(target)
    }
```

Saver (empty-string markers, `DatePickerState` precedent):

```kotlin
                save = {
                    listOf(
                        it.startMonth?.toString() ?: "",
                        it.endMonth?.toString() ?: "",
                        it.firstVisibleMonth.toString(),
                        it.firstDayOfWeek.ordinal,
                        it.outDateStyle.ordinal,
                    )
                },
                restore = {
                    CalendarState(
                        startMonth = (it[0] as String).takeIf(String::isNotEmpty)?.let(YearMonth::parse),
                        endMonth = (it[1] as String).takeIf(String::isNotEmpty)?.let(YearMonth::parse),
                        firstVisibleMonth = YearMonth.parse(it[2] as String),
                        firstDayOfWeek = DayOfWeek.entries[it[3] as Int],
                        outDateStyle = OutDateStyle.entries[it[4] as Int],
                    )
                },
```

`rememberCalendarState` signature: `startMonth: YearMonth? = null, endMonth: YearMonth? = null` (rest unchanged; update its KDoc to describe null sides).

- [ ] **Step 4: Fix `NavHeader` enablement**

Replace the two comparisons (current lines 62 and 95):

```kotlin
// before each IconButton, or inline:
val start = state.startMonth
val end = state.endMonth
// line 62 equivalent:
enabled = start == null || visibleMonth > start,
// line 95 equivalent:
enabled = end == null || visibleMonth < end,
```

- [ ] **Step 5: Sweep remaining compile fallout**

Run: `./gradlew :orrery-compose:compileDebugKotlin`
Then `grep -rn "\.startMonth\|\.endMonth" orrery-compose/src/main sample/src/main` — NavHeader and CalendarState internals should be the only main-source readers; fix any other site the compiler flags with the same null-aware pattern and list each in the commit body. `DatePickerState.kt` passes non-null args into the constructor — no change needed (non-null into nullable is fine).

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :orrery-compose:testDebugUnitTest --tests "me.kozakov.orrery.compose.InfinitePagingTest" --tests "me.kozakov.orrery.compose.NavHeaderTest" --tests "me.kozakov.orrery.compose.CalendarStateTest" --tests "me.kozakov.orrery.compose.KeyboardNavigationTest"`
Expected: PASS (new tests plus all existing state/nav tests unchanged).

- [ ] **Step 7: Lint and commit**

Run: `./gradlew :orrery-compose:ktlintCheck :orrery-compose:detekt`

```bash
git add orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/CalendarState.kt orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/NavHeader.kt orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/InfinitePagingTest.kt orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/NavHeaderTest.kt
git commit -m "feat(compose): unbounded CalendarState via nullable bounds"
```

---

### Task 2: `WeekCalendarState` nullable bounds

**Files:**

- Modify: `orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/WeekCalendarState.kt`
- Modify: `orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/InfinitePagingTest.kt` (append week half)

**Interfaces:**

- Consumes: Task 1's pattern; `CalendarPages.weekIndex/weekCount/weekStart`.
- Produces: `WeekCalendarState.startDate: LocalDate?` / `endDate: LocalDate?` (private set); `rememberWeekCalendarState(startDate: LocalDate? = null, endDate: LocalDate? = null, ...)`; `internal const val FAR_JUMP_WEEKS = 52`.

- [ ] **Step 1: Write the failing tests**

Append to `InfinitePagingTest.kt` (own `weekContent` helper attaching `LazyRow(state = weekState.listState) { items(weekState.weekCount) { Box(Modifier.size(80.dp)) { Text("w$it") } } }`, `lateinit weekState: WeekCalendarState`):

```kotlin
    @Test
    fun weekDefaultStateIsUnbounded() {
        weekContent()
        rule.runOnIdle {
            assertNull(weekState.startDate)
            assertNull(weekState.endDate)
        }
    }

    @Test
    fun weekScrollsCenturiesOutAndBack() {
        weekContent()
        rule.waitForIdle()
        scope.launch { weekState.scrollToDate(LocalDate(2526, 7, 15)) }
        rule.runOnIdle {
            assertEquals(
                CalendarPages.weekStart(LocalDate(2526, 7, 15), DayOfWeek.MONDAY),
                weekState.firstVisibleWeek.days.first().date,
            )
        }
    }

    @Test
    fun weekOneSidedStartClamps() {
        weekContent(startDate = LocalDate(2026, 7, 1))
        rule.waitForIdle()
        scope.launch { weekState.scrollToDate(LocalDate(2020, 1, 1)) }
        rule.runOnIdle {
            assertEquals(
                CalendarPages.weekStart(LocalDate(2026, 7, 1), DayOfWeek.MONDAY),
                weekState.firstVisibleWeek.days.first().date,
            )
        }
    }

    @Test
    fun weekFarAnimatedJumpLandsExactly() {
        weekContent()
        rule.waitForIdle()
        scope.launch { weekState.animateScrollToDate(LocalDate(2126, 7, 15)) }
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(
                CalendarPages.weekStart(LocalDate(2126, 7, 15), DayOfWeek.MONDAY),
                weekState.firstVisibleWeek.days.first().date,
            )
        }
    }
```

(`weekContent(startDate: LocalDate? = null, endDate: LocalDate? = null)` passes `firstVisibleDate = LocalDate(2026, 7, 15)`, `firstDayOfWeek = DayOfWeek.MONDAY`. Import `me.kozakov.orrery.core.CalendarPages` and `kotlinx.datetime.LocalDate`.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :orrery-compose:testDebugUnitTest --tests "me.kozakov.orrery.compose.InfinitePagingTest"`
Expected: FAIL — compile error (`rememberWeekCalendarState` requires startDate/endDate; nullable mismatch).

- [ ] **Step 3: Implement**

Mirror Task 1 exactly in `WeekCalendarState.kt`:

```kotlin
internal const val FAR_JUMP_WEEKS: Int = 52

private val UNBOUNDED_START_DATE = LocalDate(-19999, 1, 1)
private val UNBOUNDED_END_DATE = LocalDate(19999, 12, 31)
```

- `startDate`/`endDate` become `LocalDate?` `mutableStateOf` with private set; KDoc "null scrolls unboundedly".
- `private val effectiveStartDate get() = startDate ?: UNBOUNDED_START_DATE` (and end); `weekCount`, `weekAt`, `indexOf` route through them.
- `animateScrollToDate` gains the far-jump (threshold `FAR_JUMP_WEEKS`, approach 2 items; weeks have no `itemsPerMonth` multiplier).
- Saver: empty-string markers for both dates, same shape as Task 1.
- `rememberWeekCalendarState(startDate: LocalDate? = null, endDate: LocalDate? = null, firstVisibleDate: LocalDate = currentDate(), firstDayOfWeek: DayOfWeek = firstDayOfWeekFromLocale())`.

Check callers: `CollapsibleCalendarScaffold`, `AgendaScreen`, and `HeatmapCalendar` do not read `startDate`/`endDate` off `WeekCalendarState` (grep `weekState\.\|\.startDate\|\.endDate` across `orrery-compose/src/main` and `sample/src/main`; `HeatmapCalendarState` has its own non-null fields — do not touch). Fix any flagged site null-aware and list it in the commit body.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :orrery-compose:testDebugUnitTest --tests "me.kozakov.orrery.compose.InfinitePagingTest" --tests "me.kozakov.orrery.compose.CalendarStateTest" --tests "me.kozakov.orrery.compose.KeyboardNavigationTest"`
Expected: PASS.

- [ ] **Step 5: MonthYearPicker KDoc**

Add one sentence to `MonthYearPicker`'s KDoc: "For calendars with unbounded state, pass a finite window here — e.g. a hundred years around [currentYearMonth]."

- [ ] **Step 6: Lint and commit**

Run: `./gradlew :orrery-compose:ktlintCheck :orrery-compose:detekt`

```bash
git add orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/WeekCalendarState.kt orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/MonthYearPicker.kt orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/InfinitePagingTest.kt
git commit -m "feat(compose): unbounded WeekCalendarState via nullable bounds"
```

---

### Task 3: Docs, changelog, release prep

**Files:**

- Modify: `CHANGELOG.md`, `README.md`

- [ ] **Step 1: CHANGELOG** — new `## [0.5.0] - <today's date>` under a fresh `## [Unreleased]`:

```markdown
### Added

- Unbounded (infinite) scrolling for `CalendarState` and `WeekCalendarState`:
  bounds are now nullable and default to null (open); one-sided ranges
  supported. Far `animateScrollTo*` jumps teleport near the target before
  animating.

### Changed

- **Breaking:** `CalendarState.startMonth`/`endMonth` and
  `WeekCalendarState.startDate`/`endDate` are now nullable;
  `rememberWeekCalendarState`'s `startDate`/`endDate` parameters are now
  optional. The default calendar range changes from 1971–2055 to unbounded.
```

- [ ] **Step 2: README** — in "Calendar state and navigation": change the `rememberCalendarState` snippet comment to note bounds are optional (`// omit bounds for unbounded scrolling; null = open side`), add one sentence after the snippet about far jumps teleporting, and update the `WeekCalendar` snippet in "Calendars" to `rememberWeekCalendarState()` (no args). Add a Features bullet: "Unbounded (infinite) month/week scrolling with one-sided ranges". Keep existing tone.

- [ ] **Step 3: Full CI verification**

Run: `./gradlew ktlintCheck detekt checkKotlinAbi :orrery-core:test :orrery-lunar:test :orrery-compose:testDebugUnitTest :orrery-compose:verifyRoborazziDebug :sample:assembleDebug`
Expected: BUILD SUCCESSFUL, zero golden changes, ABI dump untouched.

- [ ] **Step 4: Commit docs**

```bash
git add CHANGELOG.md README.md
git commit -m "docs: changelog and README for 0.5.0"
```

- [ ] **Step 5: Finish branch + release commits (pause for user)**

STOP: hand back for branch integration and confirm before the release pair on main:

```bash
# gradle.properties: VERSION_NAME=0.5.0
git add gradle.properties && git commit -m "release: 0.5.0"
# gradle.properties: VERSION_NAME=0.5.1-SNAPSHOT
git add gradle.properties && git commit -m "chore: prepare next development version 0.5.1-SNAPSHOT"
```

No tagging, pushing, or publishing — the user handles those.
