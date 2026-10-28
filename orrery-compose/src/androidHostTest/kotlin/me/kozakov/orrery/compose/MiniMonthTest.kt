package me.kozakov.orrery.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.DayInfo
import me.kozakov.orrery.core.Selection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MiniMonthTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun dayInfoIsQueriedOnlyForDatesOfTheMonth() {
        val queried = mutableSetOf<LocalDate>()
        rule.setContent {
            MaterialTheme {
                CalendarDefaults.MiniMonth(
                    YearMonth(2026, 2),
                    today = LocalDate(2026, 2, 10),
                    selection = Selection(rangeStart = LocalDate(2026, 1, 28), rangeEnd = LocalDate(2026, 2, 3)),
                    dayInfo = { date ->
                        queried += date
                        DayInfo(label = null, isHoliday = date.day == 14)
                    },
                )
            }
        }
        rule.waitForIdle()
        assertEquals(28, queried.size)
        assertTrue(queried.all { it.year == 2026 && it.month.ordinal == 1 })
    }

    @Test
    fun rendersEveryDayNumberWithHighlights() {
        rule.setContent {
            MaterialTheme {
                CalendarDefaults.MiniMonth(
                    YearMonth(2026, 7),
                    today = LocalDate(2026, 7, 15),
                    selection = Selection(single = LocalDate(2026, 7, 20)),
                    dayInfo = { null },
                )
            }
        }
        rule.onNodeWithText("15").assertIsDisplayed()
        rule.onNodeWithText("20").assertIsDisplayed()
        rule.onNodeWithText("31").assertIsDisplayed()
        assertEquals(1, rule.onAllNodesWithText("1").fetchSemanticsNodes().size)
    }
}
