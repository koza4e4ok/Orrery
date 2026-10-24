package me.kozakov.orrery.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.CalendarDay
import me.kozakov.orrery.core.DayPosition
import me.kozakov.orrery.core.DisabledDates
import me.kozakov.orrery.core.SelectionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UnavailableDayUiTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun disabledDayIsNotClickableAndAvailableDayIs() {
        lateinit var selection: CalendarSelectionState
        rule.setContent {
            selection =
                rememberCalendarSelectionState(
                    mode = SelectionMode.Single(),
                    disabled = DisabledDates { daysOfWeek(DayOfWeek.SATURDAY) },
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
        rule.onNodeWithTag("day-2026-07-11").assertIsNotEnabled()
        rule.onNodeWithTag("day-2026-07-11").performClick()
        rule.runOnIdle { assertNull(selection.selection.single) }
        rule.onNodeWithTag("day-2026-07-06").assertIsEnabled()
        rule.onNodeWithTag("day-2026-07-06").performClick()
        rule.runOnIdle { assertEquals(LocalDate(2026, 7, 6), selection.selection.single) }
    }

    @Test
    fun enabledFalseForcesTheDisabledTreatment() {
        rule.setContent {
            DefaultDay(
                day = CalendarDay(LocalDate(2026, 7, 6), DayPosition.MonthDate),
                modifier = Modifier.testTag("day"),
                enabled = false,
            )
        }
        rule.onNodeWithTag("day").assertIsNotEnabled()
    }
}
