package me.kozakov.orrery.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.requestFocus
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

/** Golden images for keyboard focus rings and pointer hover fills. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class FocusSnapshotTest {
    @get:Rule val rule = createComposeRule()

    private lateinit var inputModeManager: InputModeManager

    /** Clickable cells are focusable only in keyboard mode, as on Desktop / Android TV. */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun SemanticsNodeInteraction.keyboardFocus() {
        rule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        requestFocus()
        rule.waitForIdle()
    }

    private fun setThemedContent(content: @Composable () -> Unit) {
        rule.setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                inputModeManager = LocalInputModeManager.current
                Surface { content() }
            }
        }
    }

    @Suppress("TestFunctionName")
    @Composable
    private fun July() {
        val selection =
            rememberCalendarSelectionState(
                mode = SelectionMode.Single(),
                initialSelection = Selection(single = LocalDate(2026, 7, 8)),
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
                    modifier = Modifier.testTag("day-${day.date}"),
                    selectionState = selection,
                    today = LocalDate(2026, 7, 15),
                    animateSelection = false,
                )
            },
        )
    }

    private fun focus(date: String) {
        rule.onNodeWithTag("day-$date").keyboardFocus()
    }

    @Test
    fun dayFocusedLight() {
        setThemedContent { July() }
        focus("2026-07-21")
        rule.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "+night")
    fun dayFocusedDark() {
        setThemedContent { July() }
        focus("2026-07-21")
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun dayFocusedSelected() {
        setThemedContent { July() }
        focus("2026-07-08")
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun dayFocusedToday() {
        setThemedContent { July() }
        focus("2026-07-15")
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun dayHoveredLight() {
        setThemedContent { July() }
        rule.onNodeWithTag("day-2026-07-21").performMouseInput { enter(center) }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "+night")
    fun dayHoveredDark() {
        setThemedContent { July() }
        rule.onNodeWithTag("day-2026-07-21").performMouseInput { enter(center) }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun heatmapCellFocused() {
        setThemedContent {
            HeatmapCalendar(
                intensity = { date -> if (date.day % 5 == 0) null else (date.day % 5) / 4f },
                state =
                    rememberHeatmapCalendarState(
                        startDate = LocalDate(2026, 5, 1),
                        endDate = LocalDate(2026, 7, 31),
                        firstDayOfWeek = DayOfWeek.MONDAY,
                    ),
                onDayClick = {},
            )
        }
        rule.onNodeWithContentDescription("Jul 8, 2026", substring = true).keyboardFocus()
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun miniMonthFocused() {
        setThemedContent {
            YearCalendar(
                state = rememberYearCalendarState(startYear = 2026, endYear = 2026, firstVisibleYear = 2026),
                onMonthClick = {},
                monthContent = { CalendarDefaults.MiniMonth(it, today = LocalDate(2026, 7, 15)) },
            )
        }
        rule.onNodeWithText("Mar").keyboardFocus()
        rule.onRoot().captureRoboImage()
    }
}
