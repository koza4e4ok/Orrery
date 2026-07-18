package me.kozakov.orrery.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavHeaderTest {
    @get:Rule
    val rule = createComposeRule()

    private fun content(
        onTitleClick: (() -> Unit)? = null,
        firstVisible: YearMonth = YearMonth(2026, 8),
        startMonth: YearMonth? = YearMonth(2026, 7),
        endMonth: YearMonth? = YearMonth(2026, 9),
    ): () -> CalendarState {
        lateinit var state: CalendarState
        rule.setContent {
            MaterialTheme {
                state =
                    rememberCalendarState(
                        startMonth = startMonth,
                        endMonth = endMonth,
                        firstVisibleMonth = firstVisible,
                        firstDayOfWeek = DayOfWeek.MONDAY,
                    )
                Column {
                    CalendarNavHeader(
                        state = state,
                        onTitleClick = onTitleClick,
                        title = { month -> Text(month.toString(), Modifier.testTag("title")) },
                    )
                    HorizontalCalendar(
                        state = state,
                        dayContent = { day -> DefaultDay(day = day, animateSelection = false) },
                    )
                }
            }
        }
        return { state }
    }

    @Test
    fun nextAndPreviousButtonsPageTheCalendar() {
        val state = content()
        rule.onNodeWithContentDescription("Next month").performClick()
        rule.waitForIdle()
        assertEquals(YearMonth(2026, 9), state().firstVisibleMonth)
        rule.onNodeWithContentDescription("Previous month").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Previous month").performClick()
        rule.waitForIdle()
        assertEquals(YearMonth(2026, 7), state().firstVisibleMonth)
    }

    @Test
    fun buttonsDisableAtRangeBounds() {
        content(firstVisible = YearMonth(2026, 7))
        rule.onNodeWithContentDescription("Previous month").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Next month").assertIsEnabled()
    }

    @Test
    fun buttonsStayEnabledWithOpenBounds() {
        content(startMonth = null, endMonth = null)
        rule.onNodeWithContentDescription("Previous month").assertIsEnabled()
        rule.onNodeWithContentDescription("Next month").assertIsEnabled()
    }

    @Test
    fun titleClickInvokesCallback() {
        var clicked = false
        content(onTitleClick = { clicked = true })
        // the clickable title Box merges its children's semantics
        rule.onNodeWithTag("title", useUnmergedTree = true).performClick()
        rule.runOnIdle { assertTrue(clicked) }
    }
}
