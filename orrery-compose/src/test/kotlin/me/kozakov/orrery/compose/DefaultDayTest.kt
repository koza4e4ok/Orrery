package me.kozakov.orrery.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.LocalDate
import me.kozakov.orrery.core.CalendarDay
import me.kozakov.orrery.core.DayInfo
import me.kozakov.orrery.core.DayPosition
import me.kozakov.orrery.core.Selection
import me.kozakov.orrery.core.SelectionMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DefaultDayTest {
    @get:Rule
    val rule = createComposeRule()

    private val day = CalendarDay(LocalDate(2026, 7, 9), DayPosition.MonthDate)

    private fun labelColor(text: String): Color {
        val layouts = mutableListOf<TextLayoutResult>()
        rule
            .onNodeWithText(text, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(layouts)
        return layouts.single().layoutInput.style.color
    }

    @Test
    fun clickSelectsAndSemanticsReportSelection() {
        lateinit var selection: CalendarSelectionState
        rule.setContent {
            selection = rememberCalendarSelectionState(mode = SelectionMode.Single())
            DefaultDay(day = day, selectionState = selection, today = LocalDate(2026, 7, 1))
        }
        rule.onNodeWithContentDescription("July 9, 2026", substring = true).assertIsDisplayed()
        rule.onNodeWithText("9").performClick()
        assertEquals(day.date, selection.selection.single)
        rule.onNodeWithContentDescription("July 9, 2026", substring = true).assertIsSelected()
    }

    @Test
    fun selectionWorksWithAnimationDisabled() {
        lateinit var selection: CalendarSelectionState
        rule.setContent {
            selection = rememberCalendarSelectionState(mode = SelectionMode.Single())
            DefaultDay(
                day = day,
                selectionState = selection,
                today = LocalDate(2026, 7, 1),
                animateSelection = false,
            )
        }
        rule.onNodeWithText("9").performClick()
        assertEquals(day.date, selection.selection.single)
        rule.onNodeWithContentDescription("July 9, 2026", substring = true).assertIsSelected()
    }

    @Test
    fun disabledDayIsNotClickable() {
        rule.setContent {
            DefaultDay(day = day, enabled = false, today = LocalDate(2026, 7, 1))
        }
        rule.onNodeWithContentDescription("July 9, 2026", substring = true).assertIsNotEnabled()
    }

    @Test
    fun labelFromDayInfoIsRendered() {
        rule.setContent {
            DefaultDay(day = day, info = DayInfo(label = "初一"), today = LocalDate(2026, 7, 1))
        }
        rule.onNodeWithText("初一").assertIsDisplayed()
    }

    @Test
    fun labelColorReflectsHolidayCustomColorSelectionAndAvailability() {
        val regularColor = Color(0xFF445566)
        val holidayColor = Color(0xFFD32F2F)
        val selectedColor = Color(0xFFFFFFFF)
        val unavailableColor = Color(0xFF999999)
        val customArgb = 0xFF2E7D32L

        rule.setContent {
            val colors =
                CalendarDefaults.dayColors(
                    selectedContentColor = selectedColor,
                    unavailableContentColor = unavailableColor,
                    labelColor = regularColor,
                    holidayLabelColor = holidayColor,
                )
            val selectedState =
                rememberCalendarSelectionState(
                    mode = SelectionMode.Single(),
                    initialSelection = Selection(single = day.date),
                )
            DefaultDay(
                day = day,
                colors = colors,
                info = DayInfo(label = "regular"),
                today = LocalDate(2026, 7, 1),
            )
            DefaultDay(
                day = day,
                colors = colors,
                info = DayInfo(label = "holiday", isHoliday = true),
                today = LocalDate(2026, 7, 1),
            )
            DefaultDay(
                day = day,
                colors = colors,
                info = DayInfo(label = "custom", isHoliday = true, colorArgb = customArgb),
                today = LocalDate(2026, 7, 1),
            )
            DefaultDay(
                day = day,
                selectionState = selectedState,
                colors = colors,
                info = DayInfo(label = "selected", isHoliday = true, colorArgb = customArgb),
                today = LocalDate(2026, 7, 1),
                animateSelection = false,
            )
            DefaultDay(
                day = day,
                colors = colors,
                info = DayInfo(label = "unavailable", isHoliday = true, colorArgb = customArgb),
                enabled = false,
                today = LocalDate(2026, 7, 1),
            )
        }

        assertEquals(regularColor, labelColor("regular"))
        assertEquals(holidayColor, labelColor("holiday"))
        assertEquals(Color(customArgb), labelColor("custom"))
        assertEquals(selectedColor, labelColor("selected"))
        assertEquals(unavailableColor, labelColor("unavailable"))
    }

    @Test
    fun outDatesRenderEmptyWhenHidden() {
        val outDay = CalendarDay(LocalDate(2026, 8, 2), DayPosition.OutDate)
        rule.setContent {
            DefaultDay(day = outDay, showOutDates = false, today = LocalDate(2026, 7, 1))
        }
        rule.onNodeWithText("2").assertDoesNotExist()
    }
}
