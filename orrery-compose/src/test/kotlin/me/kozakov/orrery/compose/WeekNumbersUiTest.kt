package me.kozakov.orrery.compose

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.isoWeekNumber
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeekNumbersUiTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun weekNumberColumnRendersIsoNumbers() {
        rule.setContent {
            HorizontalCalendar(
                state =
                    rememberCalendarState(
                        startMonth = YearMonth(2026, 7),
                        endMonth = YearMonth(2026, 7),
                        firstVisibleMonth = YearMonth(2026, 7),
                        firstDayOfWeek = DayOfWeek.MONDAY,
                    ),
                showWeekNumbers = true,
                dayContent = { day -> Text("day${day.date.day}") }, // avoid text collision with week numbers
            )
        }
        // July 2026 rows start at ISO weeks 27..31
        rule.onNodeWithText("27").assertIsDisplayed()
        rule.onNodeWithText("31").assertIsDisplayed()
    }

    @Test
    fun customWeekNumberSlotReplacesTheDefault() {
        rule.setContent {
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
                    Text(
                        "W" +
                            week.days
                                .first()
                                .date
                                .isoWeekNumber(),
                    )
                },
                dayContent = { day -> Text("day${day.date.day}") },
            )
        }
        // The default slot would render "29", not "W29".
        rule.onNodeWithText("W29").assertIsDisplayed()
    }
}
