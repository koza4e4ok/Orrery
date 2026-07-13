package me.kozakov.orrery.compose

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.LocalDate
import me.kozakov.orrery.core.CalendarDay
import me.kozakov.orrery.core.DayInfo
import me.kozakov.orrery.core.DayPosition
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
    fun outDatesRenderEmptyWhenHidden() {
        val outDay = CalendarDay(LocalDate(2026, 8, 2), DayPosition.OutDate)
        rule.setContent {
            DefaultDay(day = outDay, showOutDates = false, today = LocalDate(2026, 7, 1))
        }
        rule.onNodeWithText("2").assertDoesNotExist()
    }
}
