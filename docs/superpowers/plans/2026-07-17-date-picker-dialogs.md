# Date Picker Dialogs (0.3.0) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship Orrery 0.3.0: `OrreryDatePickerDialog` (compact) and `OrreryDateRangePickerDialog` (full-screen), M3-slot-shaped, with picker and text-input modes.

**Architecture:** New state holders (`OrreryDatePickerState`, `OrreryDateRangePickerState`) wrap the existing `CalendarState` + `CalendarSelectionState` (both have `internal` constructors — same module, directly constructible). Dialogs assemble existing pieces (`CalendarNavHeader`, `MonthYearPicker`, `HorizontalCalendar`/`VerticalCalendar`, `DefaultDay`) inside `androidx.compose.ui.window.Dialog` chrome. Input mode is a shared internal `DateInputField`. Icons are Canvas-drawn (the `NavChevron` precedent) — no material-icons dependency.

**Tech Stack:** Kotlin, Jetpack Compose (material3 via BOM), kotlinx-datetime + `java.time` formatters, Roborazzi + Robolectric, compose-ui-test.

**Spec:** `docs/superpowers/specs/2026-07-17-date-picker-dialogs-design.md`

## Global Constraints

- Explicit API mode: `public` modifier + KDoc on all public declarations, matching surrounding code.
- No new dependencies — in particular NO material-icons artifacts; draw glyphs with Canvas.
- Work on branch `feature/0.3.0-dialogs`; Conventional Commits; never mention Claude/Anthropic in commits.
- Module-scoped Gradle tasks only.
- Compose tests: `./gradlew :orrery-compose:testDebugUnitTest --tests "<class>"`. Record goldens: `./gradlew :orrery-compose:recordRoborazziDebug --tests "<class>"`. Plain `testDebugUnitTest` does NOT compare goldens — regression checks use `:orrery-compose:verifyRoborazziDebug`.
- Snapshot conventions (`SnapshotTest.kt`): `@GraphicsMode(GraphicsMode.Mode.NATIVE)`, `@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)`, `MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme())`, fixed dates in July 2026, `+night` for dark variants.
- Fixed reference dates: today = `LocalDate(2026, 7, 15)`, month = July 2026. Tests that type dates assume the en-US locale pattern `MM/dd/yyyy` (Robolectric default).
- Dialog content renders in its own window: snapshot/interaction tests target it with `rule.onNode(isDialog())` (import `androidx.compose.ui.test.isDialog`), not `onRoot()`.
- Full CI verification: `./gradlew ktlintCheck detekt checkKotlinAbi :orrery-core:test :orrery-lunar:test :orrery-compose:testDebugUnitTest :orrery-compose:verifyRoborazziDebug :sample:assembleDebug`.

## File Structure

| File                                                                                                                                                                                                                                               | Change                                                        |
| -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------- |
| `orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/DatePickerState.kt`                                                                                                                                                                      | Create — both state holders + `DatePickerDisplayMode`         |
| `orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/DatePickerDefaults.kt`                                                                                                                                                                   | Create — `DatePickerStrings` + `DatePickerDefaults.strings()` |
| `orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/DateInput.kt`                                                                                                                                                                            | Create — internal `DateInputField` + formatters               |
| `orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/PickerGlyphs.kt`                                                                                                                                                                         | Create — internal Canvas keyboard/calendar/close glyphs       |
| `orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/DatePickerDialog.kt`                                                                                                                                                                     | Create — `OrreryDatePickerDialog`                             |
| `orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/DateRangePickerDialog.kt`                                                                                                                                                                | Create — `OrreryDateRangePickerDialog`                        |
| `orrery-compose/src/test/.../DatePickerStateTest.kt`, `DateInputFieldTest.kt`, `DatePickerDialogTest.kt`, `DatePickerDialogSnapshotTest.kt`, `DateRangePickerStateTest.kt`, `DateRangePickerDialogTest.kt`, `DateRangePickerDialogSnapshotTest.kt` | Create                                                        |
| `sample/src/main/kotlin/me/kozakov/orrery/sample/PickersScreen.kt`, `MainActivity.kt`                                                                                                                                                              | Create / modify — demo screen                                 |
| `CHANGELOG.md`, `README.md`, `gradle.properties`                                                                                                                                                                                                   | Release prep                                                  |

---

### Task 0: Branch

- [ ] `git checkout -b feature/0.3.0-dialogs` (from up-to-date `main`).

---

### Task 1: `OrreryDatePickerState`

**Files:**

- Create: `orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/DatePickerState.kt`
- Test: `orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/DatePickerStateTest.kt`

**Interfaces:**

- Consumes: `CalendarState`/`CalendarSelectionState` internal constructors, `Selection`, `SelectionMode.Single`, `DisabledDates`, `firstDayOfWeekFromLocale()`, `currentYearMonth()`, `OutDateStyle`.
- Produces (used by Tasks 3, 4, 6):
  - `public enum class DatePickerDisplayMode { Picker, Input }`
  - `OrreryDatePickerState` with `var selectedDate: LocalDate?` (write-through validated), `var displayMode`, `val displayedMonth: YearMonth`, `val yearRange: IntRange`, `internal val calendar: CalendarState`, `internal val selection: CalendarSelectionState`, `internal val bounds: ClosedRange<LocalDate>`.
  - `@Composable rememberOrreryDatePickerState(initialDate, initialDisplayedMonth, yearRange, initialDisplayMode, disabledDates, firstDayOfWeek)`.

- [ ] **Step 1: Write the failing tests**

Create `DatePickerStateTest.kt`:

```kotlin
package me.kozakov.orrery.compose

import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.DisabledDates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatePickerStateTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun selectedDateWritesThroughValidation() {
        lateinit var state: OrreryDatePickerState
        rule.setContent { state = rememberOrreryDatePickerState() }
        rule.runOnIdle { state.selectedDate = LocalDate(2026, 7, 15) }
        rule.runOnIdle { assertEquals(LocalDate(2026, 7, 15), state.selectedDate) }
        rule.runOnIdle { state.selectedDate = null }
        rule.runOnIdle { assertNull(state.selectedDate) }
    }

    @Test
    fun selectedDateRejectsDisabledDates() {
        lateinit var state: OrreryDatePickerState
        rule.setContent {
            state =
                rememberOrreryDatePickerState(
                    disabledDates = DisabledDates { dates(LocalDate(2026, 7, 20)) },
                )
        }
        rule.runOnIdle { state.selectedDate = LocalDate(2026, 7, 20) }
        rule.runOnIdle { assertNull(state.selectedDate) }
    }

    @Test
    fun selectedDateRejectsDatesOutsideYearRange() {
        lateinit var state: OrreryDatePickerState
        rule.setContent { state = rememberOrreryDatePickerState(yearRange = 2026..2026) }
        rule.runOnIdle { state.selectedDate = LocalDate(2027, 1, 1) }
        rule.runOnIdle { assertNull(state.selectedDate) }
    }

    @Test
    fun displayedMonthFollowsInitialDate() {
        lateinit var state: OrreryDatePickerState
        rule.setContent {
            state = rememberOrreryDatePickerState(initialDate = LocalDate(2026, 3, 10))
        }
        rule.runOnIdle { assertEquals(YearMonth(2026, 3), state.displayedMonth) }
    }

    @Test
    fun stateSurvivesRestoration() {
        val restorationTester = StateRestorationTester(rule)
        lateinit var state: OrreryDatePickerState
        restorationTester.setContent { state = rememberOrreryDatePickerState() }
        rule.runOnIdle {
            state.selectedDate = LocalDate(2026, 7, 15)
            state.displayMode = DatePickerDisplayMode.Input
        }
        restorationTester.emulateSavedInstanceStateRestore()
        rule.runOnIdle {
            assertEquals(LocalDate(2026, 7, 15), state.selectedDate)
            assertEquals(DatePickerDisplayMode.Input, state.displayMode)
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :orrery-compose:testDebugUnitTest --tests "me.kozakov.orrery.compose.DatePickerStateTest"`
Expected: FAIL — compile error, `OrreryDatePickerState` unresolved.

- [ ] **Step 3: Implement**

Create `DatePickerState.kt`:

```kotlin
package me.kozakov.orrery.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.DisabledDates
import me.kozakov.orrery.core.OutDateStyle
import me.kozakov.orrery.core.Selection
import me.kozakov.orrery.core.SelectionMode

/** Which face of a picker dialog is showing. */
public enum class DatePickerDisplayMode { Picker, Input }

/**
 * State for [OrreryDatePickerDialog]: the selected date, the display
 * mode, and the visible month. Writes to [selectedDate] are validated
 * like clicks (bounds and disabled dates); invalid writes are ignored.
 */
@Stable
public class OrreryDatePickerState internal constructor(
    initialDate: LocalDate?,
    initialDisplayedMonth: YearMonth,
    public val yearRange: IntRange,
    initialDisplayMode: DatePickerDisplayMode,
    disabledDates: DisabledDates,
    firstDayOfWeek: DayOfWeek,
) {
    internal val bounds: ClosedRange<LocalDate> =
        LocalDate(yearRange.first, 1, 1)..LocalDate(yearRange.last, 12, 31)

    internal val calendar: CalendarState =
        CalendarState(
            startMonth = YearMonth(yearRange.first, 1),
            endMonth = YearMonth(yearRange.last, 12),
            firstVisibleMonth = initialDisplayedMonth,
            firstDayOfWeek = firstDayOfWeek,
            outDateStyle = OutDateStyle.EndOfRow,
        )

    internal val selection: CalendarSelectionState =
        CalendarSelectionState(
            mode = SelectionMode.Single(),
            initialSelection = initialDate?.let { Selection(single = it) } ?: Selection.Empty,
            bounds = bounds,
            disabled = disabledDates,
            onEvent = {},
        )

    public var displayMode: DatePickerDisplayMode by mutableStateOf(initialDisplayMode)

    public var selectedDate: LocalDate?
        get() = selection.selection.single
        set(value) {
            if (value == null) selection.clear() else selection.set(Selection(single = value))
        }

    /** The month at the picker's first visible page. Snapshot-observable. */
    public val displayedMonth: YearMonth
        get() = calendar.firstVisibleMonth

    internal companion object {
        internal fun saver(
            yearRange: IntRange,
            disabledDates: DisabledDates,
            firstDayOfWeek: DayOfWeek,
        ): Saver<OrreryDatePickerState, Any> =
            listSaver(
                save = {
                    listOf(
                        it.selectedDate?.toString() ?: "",
                        it.displayMode.ordinal,
                        it.displayedMonth.toString(),
                    )
                },
                restore = {
                    OrreryDatePickerState(
                        initialDate = (it[0] as String).takeIf(String::isNotEmpty)?.let(LocalDate::parse),
                        initialDisplayedMonth = YearMonth.parse(it[2] as String),
                        yearRange = yearRange,
                        initialDisplayMode = DatePickerDisplayMode.entries[it[1] as Int],
                        disabledDates = disabledDates,
                        firstDayOfWeek = firstDayOfWeek,
                    )
                },
            )
    }
}

/** Remembers saveable [OrreryDatePickerState] for [OrreryDatePickerDialog]. */
@Composable
public fun rememberOrreryDatePickerState(
    initialDate: LocalDate? = null,
    initialDisplayedMonth: YearMonth =
        initialDate?.let { YearMonth(it.year, it.month) } ?: currentYearMonth(),
    yearRange: IntRange = 1971..2055,
    initialDisplayMode: DatePickerDisplayMode = DatePickerDisplayMode.Picker,
    disabledDates: DisabledDates = DisabledDates.None,
    firstDayOfWeek: DayOfWeek = firstDayOfWeekFromLocale(),
): OrreryDatePickerState {
    val saver =
        remember(yearRange, firstDayOfWeek) {
            OrreryDatePickerState.saver(yearRange, disabledDates, firstDayOfWeek)
        }
    val state =
        rememberSaveable(yearRange, firstDayOfWeek, saver = saver) {
            OrreryDatePickerState(
                initialDate = initialDate,
                initialDisplayedMonth = initialDisplayedMonth,
                yearRange = yearRange,
                initialDisplayMode = initialDisplayMode,
                disabledDates = disabledDates,
                firstDayOfWeek = firstDayOfWeek,
            )
        }
    SideEffect { state.selection.disabled = disabledDates }
    return state
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :orrery-compose:testDebugUnitTest --tests "me.kozakov.orrery.compose.DatePickerStateTest"`
Expected: PASS (all 5).

- [ ] **Step 5: Lint and commit**

Run: `./gradlew :orrery-compose:ktlintCheck :orrery-compose:detekt`

```bash
git add orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/DatePickerState.kt orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/DatePickerStateTest.kt
git commit -m "feat(compose): add OrreryDatePickerState"
```

---

### Task 2: Strings, glyphs, and `DateInputField`

**Files:**

- Create: `orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/DatePickerDefaults.kt`
- Create: `orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/PickerGlyphs.kt`
- Create: `orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/DateInput.kt`
- Test: `orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/DateInputFieldTest.kt`

**Interfaces:**

- Consumes: `LocalDate.toJavaLocalDate()` (kotlinx-datetime-jvm interop, already used in `DefaultDay.kt`), `java.time.LocalDate.toKotlinLocalDate()`.
- Produces (used by Tasks 3, 4):
  - `public class DatePickerStrings(...)` + `DatePickerDefaults.strings(...)` — fields exactly as the spec's §5 list.
  - `internal fun DateInputField(value: LocalDate?, onValueChange: (LocalDate?) -> Unit, label: String, bounds: ClosedRange<LocalDate>, isDisabled: (LocalDate) -> Boolean, strings: DatePickerStrings, modifier: Modifier = Modifier, externalError: String? = null)` — `onValueChange` fires with the parsed date on valid input, null when blank or invalid.
  - `internal fun KeyboardGlyph(contentDescription: String?)`, `CalendarGlyph(contentDescription: String?)`, `CloseGlyph(contentDescription: String?)` — 24dp Canvas composables tinted `LocalContentColor.current`.

- [ ] **Step 1: Write the failing tests**

Create `DateInputFieldTest.kt` (Robolectric default locale is en-US → pattern `MM/dd/yyyy`):

```kotlin
package me.kozakov.orrery.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DateInputFieldTest {
    @get:Rule val rule = createComposeRule()

    private var value: LocalDate? = null
    private val strings = DatePickerDefaults.strings()

    private fun content(disabled: (LocalDate) -> Boolean = { false }) {
        rule.setContent {
            MaterialTheme {
                DateInputField(
                    value = null,
                    onValueChange = { value = it },
                    label = "Date",
                    bounds = LocalDate(2026, 1, 1)..LocalDate(2026, 12, 31),
                    isDisabled = disabled,
                    strings = strings,
                )
            }
        }
    }

    @Test
    fun validInputFiresParsedDate() {
        content()
        rule.onNodeWithText("Date").performTextReplacement("07/15/2026")
        rule.runOnIdle { assertEquals(LocalDate(2026, 7, 15), value) }
    }

    @Test
    fun garbageShowsFormatError() {
        content()
        rule.onNodeWithText("Date").performTextReplacement("potato")
        rule.onNodeWithText(strings.invalidFormatError.format("MM/DD/YYYY")).assertExists()
        rule.runOnIdle { assertNull(value) }
    }

    @Test
    fun impossibleDateShowsFormatError() {
        content()
        rule.onNodeWithText("Date").performTextReplacement("02/31/2026")
        rule.onNodeWithText(strings.invalidFormatError.format("MM/DD/YYYY")).assertExists()
        rule.runOnIdle { assertNull(value) }
    }

    @Test
    fun outOfBoundsShowsRangeError() {
        content()
        rule.onNodeWithText("Date").performTextReplacement("07/15/2030")
        rule.onNodeWithText(strings.outOfRangeError).assertExists()
        rule.runOnIdle { assertNull(value) }
    }

    @Test
    fun disabledDateShowsDisabledError() {
        content(disabled = { it == LocalDate(2026, 7, 20) })
        rule.onNodeWithText("Date").performTextReplacement("07/20/2026")
        rule.onNodeWithText(strings.disabledDateError).assertExists()
        rule.runOnIdle { assertNull(value) }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :orrery-compose:testDebugUnitTest --tests "me.kozakov.orrery.compose.DateInputFieldTest"`
Expected: FAIL — compile error, `DateInputField`/`DatePickerDefaults` unresolved.

- [ ] **Step 3: Implement strings**

Create `DatePickerDefaults.kt` — an `@Immutable public class DatePickerStrings` holding exactly these vals, and `public object DatePickerDefaults` with a `strings(...)` factory defaulting every parameter: `title = "Select date"`, `rangeTitle = "Select dates"`, `headlinePlaceholder = "Selected date"`, `rangeStartPlaceholder = "Start date"`, `rangeEndPlaceholder = "End date"`, `inputLabel = "Date"`, `rangeStartInputLabel = "Start date"`, `rangeEndInputLabel = "End date"`, `invalidFormatError = "Date must match %s"`, `outOfRangeError = "Date out of allowed range"`, `disabledDateError = "Date is unavailable"`, `invalidRangeError = "Invalid date range"`, `switchToInputDescription = "Switch to text input"`, `switchToPickerDescription = "Switch to calendar"`, `closeDescription = "Close"`. KDoc: strings are English defaults; callers localize by passing values.

- [ ] **Step 4: Implement glyphs**

Create `PickerGlyphs.kt`, all `internal @Composable`, each a `Canvas(modifier.size(24.dp).semantics { contentDescription?.let { this.contentDescription = it } })` drawing with `LocalContentColor.current` and 2dp stroke, `StrokeCap.Round`:

- `CloseGlyph`: two crossed lines between (7,7)–(17,17) and (17,7)–(7,17) (dp→px via `dp.toPx()`).
- `KeyboardGlyph`: rounded-rect outline 3..21 × 7..17 (corner 2dp), three 1.2dp-radius filled dots along y=11 at x=7/12/17, and a space-bar line (8,14.5)–(16,14.5).
- `CalendarGlyph`: rounded-rect outline 4..20 × 6..19, header line (4,10)–(20,10), two hanger ticks (8,4.5)–(8,7.5) and (16,4.5)–(16,7.5).

Follow `NavChevron` in `NavHeader.kt` for the Canvas/semantics idiom.

- [ ] **Step 5: Implement `DateInputField`**

Create `DateInput.kt`:

```kotlin
package me.kozakov.orrery.compose

import android.text.format.DateFormat
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Locale

/** Locale-ordered date pattern with 4-digit years (e.g. MM/dd/yyyy for en-US). */
internal fun localizedDatePattern(locale: Locale = Locale.getDefault()): String =
    DateFormat.getBestDateTimePattern(locale, "yMMdd")

/** Strict formatter for [localizedDatePattern]; y->u is required for STRICT resolution. */
internal fun strictDateFormatter(
    pattern: String,
    locale: Locale = Locale.getDefault(),
): DateTimeFormatter =
    DateTimeFormatter.ofPattern(pattern.replace('y', 'u'), locale).withResolverStyle(ResolverStyle.STRICT)

/**
 * One date text field: strict locale-pattern parsing with error
 * supporting-text. [onValueChange] receives the parsed date, or null
 * while the text is blank or invalid. [externalError] overlays
 * cross-field validation from the range dialog.
 */
@Composable
internal fun DateInputField(
    value: LocalDate?,
    onValueChange: (LocalDate?) -> Unit,
    label: String,
    bounds: ClosedRange<LocalDate>,
    isDisabled: (LocalDate) -> Boolean,
    strings: DatePickerStrings,
    modifier: Modifier = Modifier,
    externalError: String? = null,
) {
    val locale = Locale.getDefault()
    val pattern = remember(locale) { localizedDatePattern(locale) }
    val formatter = remember(pattern) { strictDateFormatter(pattern, locale) }
    fun parse(text: String): LocalDate? =
        try {
            java.time.LocalDate.parse(text, formatter).toKotlinLocalDate()
        } catch (_: DateTimeParseException) {
            null
        }

    var text by rememberSaveable { mutableStateOf(value?.toJavaLocalDate()?.format(formatter) ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    // Sync external writes (e.g. mode toggle after picking) without clobbering typing.
    LaunchedEffect(value) {
        if (value != parse(text)) {
            text = value?.toJavaLocalDate()?.format(formatter) ?: ""
            error = null
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            text = new
            if (new.isBlank()) {
                error = null
                onValueChange(null)
                return@OutlinedTextField
            }
            val parsed = parse(new)
            error =
                when {
                    parsed == null -> strings.invalidFormatError.format(pattern.uppercase(locale))
                    parsed !in bounds -> strings.outOfRangeError
                    isDisabled(parsed) -> strings.disabledDateError
                    else -> null
                }
            onValueChange(if (error == null) parsed else null)
        },
        label = { Text(label) },
        placeholder = { Text(pattern.uppercase(locale)) },
        isError = error != null || externalError != null,
        supportingText = { (error ?: externalError)?.let { Text(it) } },
        singleLine = true,
        modifier = modifier,
    )
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :orrery-compose:testDebugUnitTest --tests "me.kozakov.orrery.compose.DateInputFieldTest"`
Expected: PASS (all 5).

- [ ] **Step 7: Lint and commit**

Run: `./gradlew :orrery-compose:ktlintCheck :orrery-compose:detekt`

```bash
git add orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/DatePickerDefaults.kt orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/PickerGlyphs.kt orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/DateInput.kt orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/DateInputFieldTest.kt
git commit -m "feat(compose): add picker strings, glyphs and date input field"
```

---

### Task 3: `OrreryDatePickerDialog`

**Files:**

- Create: `orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/DatePickerDialog.kt`
- Test: `orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/DatePickerDialogTest.kt`
- Test: `orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/DatePickerDialogSnapshotTest.kt`

**Interfaces:**

- Consumes: Task 1 state (`state.calendar`, `state.selection`, `state.bounds`, `selectedDate`, `displayMode`, `yearRange`), Task 2 `DateInputField`/glyphs/strings, existing `CalendarNavHeader`, `MonthYearPicker`, `HorizontalCalendar`, `DefaultDay`.
- Produces: `public fun OrreryDatePickerDialog(onDismissRequest, confirmButton, state = rememberOrreryDatePickerState(), modifier, dismissButton = null, colors, shapes, today = currentDate(), strings)`.

- [ ] **Step 1: Write the failing interaction tests**

Create `DatePickerDialogTest.kt`:

```kotlin
package me.kozakov.orrery.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatePickerDialogTest {
    @get:Rule val rule = createComposeRule()

    private lateinit var state: OrreryDatePickerState
    private val strings = DatePickerDefaults.strings()

    private fun content() {
        rule.setContent {
            MaterialTheme {
                state =
                    rememberOrreryDatePickerState(
                        initialDisplayedMonth = YearMonth(2026, 7),
                        yearRange = 2026..2026,
                    )
                OrreryDatePickerDialog(
                    onDismissRequest = {},
                    confirmButton = { TextButton(onClick = {}) { Text("OK") } },
                    dismissButton = { TextButton(onClick = {}) { Text("Cancel") } },
                    state = state,
                    today = LocalDate(2026, 7, 15),
                )
            }
        }
    }

    @Test
    fun clickingADayUpdatesSelectedDate() {
        content()
        rule.onNodeWithText("10").performClick()
        rule.runOnIdle { assertEquals(LocalDate(2026, 7, 10), state.selectedDate) }
    }

    @Test
    fun buttonSlotsRender() {
        content()
        rule.onNodeWithText("OK").assertExists()
        rule.onNodeWithText("Cancel").assertExists()
    }

    @Test
    fun typedDateSurvivesToggleBackToPicker() {
        content()
        rule.onNodeWithContentDescription(strings.switchToInputDescription).performClick()
        rule.onNodeWithText(strings.inputLabel).performTextReplacement("03/10/2026")
        rule.runOnIdle { assertEquals(LocalDate(2026, 3, 10), state.selectedDate) }
        rule.onNodeWithContentDescription(strings.switchToPickerDescription).performClick()
        rule.runOnIdle { assertEquals(YearMonth(2026, 3), state.displayedMonth) }
    }

    @Test
    fun titleClickOpensMonthYearPicker() {
        content()
        // NavHeaderTitle renders the month as plain text ("July 2026").
        rule.onNodeWithText("July 2026").performClick()
        rule.onNodeWithText("2026 – 2026").assertExists()
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :orrery-compose:testDebugUnitTest --tests "me.kozakov.orrery.compose.DatePickerDialogTest"`
Expected: FAIL — compile error, `OrreryDatePickerDialog` unresolved.

- [ ] **Step 3: Implement the dialog**

Create `DatePickerDialog.kt`:

```kotlin
package me.kozakov.orrery.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toJavaLocalDate
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Material-3-shaped date picker dialog over Orrery's calendar. Selection
 * lives in [state]; read state.selectedDate in your confirm button. The
 * headline toggles between calendar picking and locale-formatted text
 * input. Strings localize via [strings].
 */
@Composable
public fun OrreryDatePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    state: OrreryDatePickerState = rememberOrreryDatePickerState(),
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    colors: CalendarDayColors = CalendarDefaults.dayColors(),
    shapes: CalendarDayShapes = CalendarDefaults.dayShapes(),
    today: LocalDate = currentDate(),
    strings: DatePickerStrings = DatePickerDefaults.strings(),
) {
    val headlineFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp, modifier = modifier) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    strings.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        state.selectedDate?.toJavaLocalDate()?.format(headlineFormatter)
                            ?: strings.headlinePlaceholder,
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        state.displayMode =
                            if (state.displayMode == DatePickerDisplayMode.Picker) {
                                DatePickerDisplayMode.Input
                            } else {
                                DatePickerDisplayMode.Picker
                            }
                    }) {
                        if (state.displayMode == DatePickerDisplayMode.Picker) {
                            KeyboardGlyph(contentDescription = strings.switchToInputDescription)
                        } else {
                            CalendarGlyph(contentDescription = strings.switchToPickerDescription)
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                AnimatedContent(
                    targetState = state.displayMode,
                    transitionSpec = { fadeIn().togetherWith(fadeOut()) },
                    label = "datePickerMode",
                ) { mode ->
                    when (mode) {
                        DatePickerDisplayMode.Picker -> PickerPane(state, colors, shapes, today)
                        DatePickerDisplayMode.Input ->
                            DateInputField(
                                value = state.selectedDate,
                                onValueChange = { state.selectedDate = it },
                                label = strings.inputLabel,
                                bounds = state.bounds,
                                isDisabled = state.selection::isDisabled,
                                strings = strings,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}

@Composable
private fun PickerPane(
    state: OrreryDatePickerState,
    colors: CalendarDayColors,
    shapes: CalendarDayShapes,
    today: LocalDate,
) {
    val scope = rememberCoroutineScope()
    var showMonthPicker by rememberSaveable { mutableStateOf(false) }
    Column {
        CalendarNavHeader(
            state = state.calendar,
            onTitleClick = { showMonthPicker = !showMonthPicker },
        )
        if (showMonthPicker) {
            MonthYearPicker(
                current = state.displayedMonth,
                range = YearMonth(state.yearRange.first, 1)..YearMonth(state.yearRange.last, 12),
                onSelect = { month ->
                    showMonthPicker = false
                    scope.launch { state.calendar.scrollToMonth(month) }
                },
                modifier = Modifier.height(320.dp),
            )
        } else {
            HorizontalCalendar(state = state.calendar) { day ->
                DefaultDay(
                    day = day,
                    selectionState = state.selection,
                    today = today,
                    colors = colors,
                    shapes = shapes,
                )
            }
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :orrery-compose:testDebugUnitTest --tests "me.kozakov.orrery.compose.DatePickerDialogTest"`
Expected: PASS (all 4).

- [ ] **Step 5: Snapshot tests**

Create `DatePickerDialogSnapshotTest.kt` — standard snapshot scaffolding (see Global Constraints), content = `OrreryDatePickerDialog` with `state = rememberOrreryDatePickerState(initialDate = LocalDate(2026, 7, 8), yearRange = 2026..2026)`, `today = LocalDate(2026, 7, 15)`, OK/Cancel `TextButton` slots. Three tests, each capturing `rule.onNode(isDialog()).captureRoboImage()`:

- `pickerLight` — default mode.
- `pickerDark` — `@Config(qualifiers = "+night")`.
- `inputLight` — `initialDisplayMode = DatePickerDisplayMode.Input` on the state.

Run: `./gradlew :orrery-compose:recordRoborazziDebug --tests "me.kozakov.orrery.compose.DatePickerDialogSnapshotTest"`
Then Read the three PNGs and confirm: dialog chrome (title, headline "Jul 8, 2026", toggle glyph, buttons); July 2026 grid with 8 selected and 15 ringed; input mode shows the text field with "07/08/2026".

- [ ] **Step 6: Verify, lint, commit**

Run: `./gradlew :orrery-compose:verifyRoborazziDebug :orrery-compose:ktlintCheck :orrery-compose:detekt`
Expected: PASS — no existing golden changed.

```bash
git add orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/DatePickerDialog.kt orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/DatePickerDialogTest.kt orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/DatePickerDialogSnapshotTest.kt orrery-compose/src/test/snapshots
git commit -m "feat(compose): add OrreryDatePickerDialog"
```

---

### Task 4: `OrreryDateRangePickerState`

**Files:**

- Create in: `orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/DatePickerState.kt` (append)
- Test: `orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/DateRangePickerStateTest.kt`

**Interfaces:**

- Consumes: same building blocks as Task 1, `SelectionMode.Range(minDays, maxDays)`.
- Produces (used by Task 5): `OrreryDateRangePickerState` with `val selectedStartDate: LocalDate?`, `val selectedEndDate: LocalDate?`, `fun setSelection(start: LocalDate?, end: LocalDate?)`, `var displayMode`, `val displayedMonth`, `val yearRange`, `internal calendar/selection/bounds`, plus `@Composable rememberOrreryDateRangePickerState(initialStartDate = null, initialEndDate = null, initialDisplayedMonth, yearRange = 1971..2055, initialDisplayMode = Picker, minDays: Int? = null, maxDays: Int? = null, disabledDates = DisabledDates.None, firstDayOfWeek = firstDayOfWeekFromLocale())`.

- [ ] **Step 1: Write the failing tests**

Create `DateRangePickerStateTest.kt` mirroring `DatePickerStateTest`'s structure with these cases:

```kotlin
    @Test
    fun setSelectionStoresAValidRange() {
        // setContent { state = rememberOrreryDateRangePickerState() }
        rule.runOnIdle { state.setSelection(LocalDate(2026, 7, 8), LocalDate(2026, 7, 12)) }
        rule.runOnIdle {
            assertEquals(LocalDate(2026, 7, 8), state.selectedStartDate)
            assertEquals(LocalDate(2026, 7, 12), state.selectedEndDate)
        }
    }

    @Test
    fun setSelectionRejectsTooShortRanges() {
        // state = rememberOrreryDateRangePickerState(minDays = 3)
        // SelectionEngine.replace rejects wholesale, keeping the current
        // (empty) selection - so both endpoints stay null.
        rule.runOnIdle { state.setSelection(LocalDate(2026, 7, 8), LocalDate(2026, 7, 9)) }
        rule.runOnIdle {
            assertNull(state.selectedStartDate)
            assertNull(state.selectedEndDate)
        }
    }

    @Test
    fun setSelectionWithNullStartClears() {
        // seed a valid range first, then setSelection(null, null)
        rule.runOnIdle { state.setSelection(LocalDate(2026, 7, 8), LocalDate(2026, 7, 12)) }
        rule.runOnIdle { state.setSelection(null, null) }
        rule.runOnIdle { assertNull(state.selectedStartDate) }
    }

    @Test
    fun stateSurvivesRestoration() {
        // seed range + Input mode, emulateSavedInstanceStateRestore, assert both dates + mode
    }
```

Write them fully (the comments above show the arrangement; each test declares `lateinit var state` and its own `setContent` like Task 1's tests).

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :orrery-compose:testDebugUnitTest --tests "me.kozakov.orrery.compose.DateRangePickerStateTest"`
Expected: FAIL — compile error.

- [ ] **Step 3: Implement**

Append to `DatePickerState.kt`: `OrreryDateRangePickerState` — same shape as `OrreryDatePickerState` with `SelectionMode.Range(minDays, maxDays)`, accessors:

```kotlin
    public val selectedStartDate: LocalDate?
        get() = selection.selection.rangeStart

    public val selectedEndDate: LocalDate?
        get() = selection.selection.rangeEnd

    /** Replaces the range through selection-engine validation; null [start] clears. */
    public fun setSelection(
        start: LocalDate?,
        end: LocalDate?,
    ) {
        if (start == null) selection.clear() else selection.set(Selection(rangeStart = start, rangeEnd = end))
    }
```

Saver persists `[rangeStart, rangeEnd, displayMode.ordinal, displayedMonth]`; factory `rememberOrreryDateRangePickerState` mirrors Task 1's (saver keyed on `yearRange`, `firstDayOfWeek`, plus `minDays`/`maxDays`).

- [ ] **Step 4: Run tests, lint, commit**

Run: `./gradlew :orrery-compose:testDebugUnitTest --tests "me.kozakov.orrery.compose.DateRangePickerStateTest"` then `:orrery-compose:ktlintCheck :orrery-compose:detekt`
Expected: PASS.

```bash
git add orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/DatePickerState.kt orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/DateRangePickerStateTest.kt
git commit -m "feat(compose): add OrreryDateRangePickerState"
```

---

### Task 5: `OrreryDateRangePickerDialog`

**Files:**

- Create: `orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/DateRangePickerDialog.kt`
- Test: `orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/DateRangePickerDialogTest.kt`
- Test: `orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/DateRangePickerDialogSnapshotTest.kt`

**Interfaces:**

- Consumes: Task 4 state, Task 2 `DateInputField`/`CloseGlyph`/glyphs/strings, existing `VerticalCalendar`, `DefaultDay`.
- Produces: `public fun OrreryDateRangePickerDialog(onDismissRequest, confirmButton, state = rememberOrreryDateRangePickerState(), modifier, colors, shapes, today = currentDate(), strings)` — full-screen, no `dismissButton` slot (the ✕ dismisses).

- [ ] **Step 1: Write the failing interaction tests**

`DateRangePickerDialogTest.kt`, state built with `initialDisplayedMonth = YearMonth(2026, 7)`, `yearRange = 2026..2026`, `today = LocalDate(2026, 7, 15)`, confirm slot `TextButton { Text("Save") }`:

- `clickingTwoDaysSelectsARange` — `onAllNodesWithText("8").onFirst().performClick()`, then `onAllNodesWithText("12").onFirst().performClick()`; assert `selectedStartDate`/`selectedEndDate` (day text repeats across the 12 stacked months — always disambiguate with `onAllNodes...onFirst()`).
- `closeGlyphFiresOnDismissRequest` — `onNodeWithContentDescription(strings.closeDescription).performClick()`; assert a `dismissed` flag set by the callback.
- `inputModeCrossValidatesTheRange` — toggle to input; type `07/12/2026` into `strings.rangeStartInputLabel` and `07/08/2026` into `strings.rangeEndInputLabel` (end before start); assert `onNodeWithText(strings.invalidRangeError).assertExists()` and `selectedEndDate == null`.
- `validInputPairAppliesToState` — type `07/08/2026` / `07/12/2026`; assert both state dates.

Write each fully following Task 3's test idioms.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :orrery-compose:testDebugUnitTest --tests "me.kozakov.orrery.compose.DateRangePickerDialogTest"`
Expected: FAIL — compile error.

- [ ] **Step 3: Implement the dialog**

Create `DateRangePickerDialog.kt`. Skeleton:

```kotlin
@Composable
public fun OrreryDateRangePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    state: OrreryDateRangePickerState = rememberOrreryDateRangePickerState(),
    modifier: Modifier = Modifier,
    colors: CalendarDayColors = CalendarDefaults.dayColors(),
    shapes: CalendarDayShapes = CalendarDefaults.dayShapes(),
    today: LocalDate = currentDate(),
    strings: DatePickerStrings = DatePickerDefaults.strings(),
) {
    val headlineFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = modifier.fillMaxSize()) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    IconButton(onClick = onDismissRequest) { CloseGlyph(contentDescription = strings.closeDescription) }
                    Text(strings.rangeTitle, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    confirmButton()
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 24.dp)) {
                    val start = state.selectedStartDate?.toJavaLocalDate()?.format(headlineFormatter) ?: strings.rangeStartPlaceholder
                    val end = state.selectedEndDate?.toJavaLocalDate()?.format(headlineFormatter) ?: strings.rangeEndPlaceholder
                    Text("$start – $end", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { /* toggle displayMode, as in the single dialog */ }) { /* Keyboard/Calendar glyph */ }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                AnimatedContent(targetState = state.displayMode, transitionSpec = { fadeIn().togetherWith(fadeOut()) }, label = "rangePickerMode") { mode ->
                    when (mode) {
                        DatePickerDisplayMode.Picker ->
                            VerticalCalendar(
                                state = state.calendar,
                                stickyMonthHeaders = true,
                                dragSelection = state.selection,
                            ) { day ->
                                DefaultDay(day = day, selectionState = state.selection, today = today, colors = colors, shapes = shapes)
                            }
                        DatePickerDisplayMode.Input -> RangeInputPane(state, strings)
                    }
                }
            }
        }
    }
}
```

`RangeInputPane` (private): two `DateInputField`s bound to local `var start`/`var end` (`rememberSaveable`, initialized from the state), applying on every change:

```kotlin
@Composable
private fun RangeInputPane(
    state: OrreryDateRangePickerState,
    strings: DatePickerStrings,
) {
    var start by rememberSaveable { mutableStateOf(state.selectedStartDate) }
    var end by rememberSaveable { mutableStateOf(state.selectedEndDate) }
    fun apply() {
        state.setSelection(start, end)
    }
    // On rejection the engine keeps the PREVIOUS selection, so "did the
    // proposed pair apply?" is a comparison, not a null check.
    val rangeError =
        if (start != null && end != null &&
            (state.selectedStartDate != start || state.selectedEndDate != end)
        ) {
            strings.invalidRangeError
        } else {
            null
        }
    Column(Modifier.padding(horizontal = 24.dp)) {
        DateInputField(
            value = start,
            onValueChange = { start = it; apply() },
            label = strings.rangeStartInputLabel,
            bounds = state.bounds,
            isDisabled = state.selection::isDisabled,
            strings = strings,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
        DateInputField(
            value = end,
            onValueChange = { end = it; apply() },
            label = strings.rangeEndInputLabel,
            bounds = state.bounds,
            isDisabled = state.selection::isDisabled,
            strings = strings,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            externalError = rangeError,
        )
    }
}
```

Note the cross-field rule rides on engine validation: `apply()` proposes the pair; if the engine rejects (end < start, min/max days, intercepted disabled date), `state.selectedEndDate` stays null and `rangeError` shows on the end field. `rememberSaveable` with a `LocalDate?` needs a saver — store as `String` (`mutableStateOf(state.selectedStartDate?.toString() ?: "")` + parse) or use `rememberSaveable(stateSaver = ...)`; pick the string form for simplicity and adjust the code accordingly.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :orrery-compose:testDebugUnitTest --tests "me.kozakov.orrery.compose.DateRangePickerDialogTest"`
Expected: PASS (all 4).

- [ ] **Step 5: Snapshot tests**

`DateRangePickerDialogSnapshotTest.kt` — state seeded `initialStartDate = LocalDate(2026, 7, 8)`, `initialEndDate = LocalDate(2026, 7, 12)`, `initialDisplayedMonth = YearMonth(2026, 7)`, `yearRange = 2026..2026`, `today = LocalDate(2026, 7, 15)`. Tests `rangePickerLight`, `rangePickerDark` (`+night`), `rangeInputLight` (Input mode), capturing `rule.onNode(isDialog()).captureRoboImage()`.

Run: `./gradlew :orrery-compose:recordRoborazziDebug --tests "me.kozakov.orrery.compose.DateRangePickerDialogSnapshotTest"`
Read the PNGs: full-screen surface, ✕ + "Select dates" + Save top bar, "Jul 8, 2026 – Jul 12, 2026" headline, range band across 8–12, sticky July header; input variant shows both fields populated.

- [ ] **Step 6: Verify, lint, commit**

Run: `./gradlew :orrery-compose:verifyRoborazziDebug :orrery-compose:ktlintCheck :orrery-compose:detekt`
Expected: PASS.

```bash
git add orrery-compose/src/main/kotlin/me/kozakov/orrery/compose/DateRangePickerDialog.kt orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/DateRangePickerDialogTest.kt orrery-compose/src/test/kotlin/me/kozakov/orrery/compose/DateRangePickerDialogSnapshotTest.kt orrery-compose/src/test/snapshots
git commit -m "feat(compose): add OrreryDateRangePickerDialog"
```

---

### Task 6: Sample "Pickers" screen

**Files:**

- Create: `sample/src/main/kotlin/me/kozakov/orrery/sample/PickersScreen.kt`
- Modify: `sample/src/main/kotlin/me/kozakov/orrery/sample/MainActivity.kt` (`Destination` enum ~line 46, `when(screen)` ~line 88)

**Interfaces:**

- Consumes: both dialogs and state holders from Tasks 3/5.
- Produces: `@Composable fun PickersScreen()`.

- [ ] **Step 1: Implement the screen**

`PickersScreen.kt`: a Column with two demo cards — "Single date" (button opens `OrreryDatePickerDialog`; confirm stores `state.selectedDate` into a `rememberSaveable` result shown as text; confirm disabled while `selectedDate == null`) and "Date range" (same pattern with `OrreryDateRangePickerDialog`, Save button in the top bar slot). Use plain `Button` + `Text`, matching the sample's existing screen style (look at `TripsScreen.kt` for card/padding idioms).

- [ ] **Step 2: Wire navigation**

In `MainActivity.kt`: add `Pickers("Pickers", Icons.Filled.Edit)` to `Destination`; change the `when` to `0 -> AgendaScreen(); 1 -> TripsScreen(); 2 -> HabitsScreen(); 3 -> AlmanacScreen(); else -> PickersScreen()`.

- [ ] **Step 3: Build, commit**

Run: `./gradlew :sample:assembleDebug`
Expected: BUILD SUCCESSFUL.

```bash
git add sample/src/main/kotlin/me/kozakov/orrery/sample/PickersScreen.kt sample/src/main/kotlin/me/kozakov/orrery/sample/MainActivity.kt
git commit -m "feat(sample): add Pickers demo screen"
```

---

### Task 7: Docs, changelog, release prep

**Files:**

- Modify: `CHANGELOG.md`, `README.md`, `gradle.properties`

- [ ] **Step 1: CHANGELOG** — new `## [0.3.0] - <today>` section under a fresh `## [Unreleased]`:

```markdown
### Added

- `OrreryDatePickerDialog`: Material-3-shaped single-date picker dialog
  (slot-based confirm/dismiss buttons) with calendar and locale-aware
  text-input modes, backed by `rememberOrreryDatePickerState`.
- `OrreryDateRangePickerDialog`: full-screen range picker with drag
  selection, sticky month headers, and dual text-input fields, backed by
  `rememberOrreryDateRangePickerState`.
- `DatePickerStrings` / `DatePickerDefaults.strings()`: every built-in
  string is an overridable parameter (English defaults).
```

- [ ] **Step 2: README** — add a "Date picker dialogs" subsection with a short `OrreryDatePickerDialog` usage snippet (the M3-slot form from the spec §2) and a feature-list bullet; keep existing tone.

- [ ] **Step 3: Full CI verification**

Run: `./gradlew ktlintCheck detekt checkKotlinAbi :orrery-core:test :orrery-lunar:test :orrery-compose:testDebugUnitTest :orrery-compose:verifyRoborazziDebug :sample:assembleDebug`
Expected: BUILD SUCCESSFUL. (Core is untouched, so `checkKotlinAbi` should pass without `updateKotlinAbi`; if it flags anything, run `./gradlew updateKotlinAbi` and include the dump.)

- [ ] **Step 4: Commit docs**

```bash
git add CHANGELOG.md README.md
git commit -m "docs: changelog and README for 0.3.0"
```

- [ ] **Step 5: Finish branch + release commits (pause for user)**

STOP: hand back for branch integration (merge/PR per the finishing-a-development-branch skill) and confirm before the release pair on main:

```bash
# gradle.properties: VERSION_NAME=0.3.0
git add gradle.properties && git commit -m "release: 0.3.0"
# gradle.properties: VERSION_NAME=0.3.1-SNAPSHOT
git add gradle.properties && git commit -m "chore: prepare next development version 0.3.1-SNAPSHOT"
```

No tagging, pushing, or publishing — the user handles those.
