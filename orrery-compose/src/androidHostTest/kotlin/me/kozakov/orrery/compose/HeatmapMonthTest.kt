package me.kozakov.orrery.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeatmapMonthTest {
    @get:Rule val rule = createComposeRule()

    private fun intensity(date: LocalDate): Float? = if (date.day % 5 == 0) null else (date.day % 5) / 4f

    private fun content(
        onDayClick: ((LocalDate) -> Unit)? = null,
        showDayNumbers: Boolean = false,
    ) {
        rule.setContent {
            MaterialTheme {
                HeatmapMonth(
                    yearMonth = YearMonth(2026, 7),
                    intensity = ::intensity,
                    onDayClick = onDayClick,
                    showDayNumbers = showDayNumbers,
                    firstDayOfWeek = DayOfWeek.MONDAY,
                )
            }
        }
    }

    @Test
    fun clickReportsDate() {
        var clicked: LocalDate? = null
        content(onDayClick = { clicked = it })
        rule.onNodeWithContentDescription("Jul 8, 2026, level 4 of 5").performClick()
        rule.runOnIdle { assertEquals(LocalDate(2026, 7, 8), clicked) }
    }

    @Test
    fun outDatesAreNotClickable() {
        content(onDayClick = {})
        rule.onAllNodesWithText("29").assertCountEquals(0)
        rule.onNodeWithContentDescription("Jun 29, 2026, no data").assertDoesNotExist()
    }

    @Test
    fun dayNumbersToggleOn() {
        content(showDayNumbers = true)
        rule.onAllNodesWithText("15").assertCountEquals(1)
    }

    @Test
    fun cellsNotClickableWithoutCallback() {
        content(onDayClick = null)
        rule.onNodeWithContentDescription("Jul 8, 2026, level 4 of 5").assertHasNoClickAction()
    }
}
