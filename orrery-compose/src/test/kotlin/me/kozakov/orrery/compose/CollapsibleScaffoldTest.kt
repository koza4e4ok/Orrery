package me.kozakov.orrery.compose

import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CollapsibleScaffoldTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun collapseSwapsMonthForWeekAndBack() {
        lateinit var collapsible: CollapsibleCalendarState
        lateinit var scope: CoroutineScope
        rule.setContent {
            scope = rememberCoroutineScope()
            collapsible = rememberCollapsibleCalendarState()
            CollapsibleCalendarScaffold(
                calendarState =
                    rememberCalendarState(
                        startMonth = YearMonth(2026, 7),
                        endMonth = YearMonth(2026, 7),
                        firstVisibleMonth = YearMonth(2026, 7),
                        firstDayOfWeek = DayOfWeek.MONDAY,
                    ),
                weekState =
                    rememberWeekCalendarState(
                        startDate = LocalDate(2026, 7, 1),
                        endDate = LocalDate(2026, 7, 31),
                        firstVisibleDate = LocalDate(2026, 7, 9),
                        firstDayOfWeek = DayOfWeek.MONDAY,
                    ),
                state = collapsible,
                dayContent = { day -> Text(day.date.toString(), Modifier.testTag("d-${day.date}")) },
            ) { Text("content") }
        }
        assertTrue(collapsible.isExpanded)
        rule.onNodeWithTag("d-2026-07-27").assertIsDisplayed() // last-row day, visible only expanded
        rule.onNodeWithText("content").assertIsDisplayed()

        scope.launch { collapsible.collapse() }
        rule.waitForIdle()
        assertFalse(collapsible.isExpanded)
        rule.onNodeWithTag("d-2026-07-06").assertIsDisplayed() // week row of Jul 9
        rule.onNodeWithTag("d-2026-07-27").assertDoesNotExist() // month replaced by week

        scope.launch { collapsible.expand() }
        rule.waitForIdle()
        assertTrue(collapsible.isExpanded)
        rule.onNodeWithTag("d-2026-07-27").assertIsDisplayed()
    }
}
