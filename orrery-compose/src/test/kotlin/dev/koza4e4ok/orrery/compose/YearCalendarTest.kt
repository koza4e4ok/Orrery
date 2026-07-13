package dev.koza4e4ok.orrery.compose

import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class YearCalendarTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun yearPageRendersTwelveMonthsAndClickReportsMonth() {
        var clicked: YearMonth? = null
        rule.setContent {
            YearCalendar(
                state = rememberYearCalendarState(startYear = 2026, endYear = 2027, firstVisibleYear = 2026),
                onMonthClick = { clicked = it },
                monthContent = { ym -> Text(ym.toString(), Modifier.testTag("m-$ym")) },
            )
        }
        // clickable month cells merge descendants; query the unmerged tree
        rule.onNodeWithTag("m-2026-01", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("m-2026-12", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("m-2026-07", useUnmergedTree = true).performClick()
        assertEquals(YearMonth(2026, 7), clicked)
    }

    @Test
    fun scrollToYearMovesPages() {
        lateinit var state: YearCalendarState
        lateinit var scope: CoroutineScope
        rule.setContent {
            scope = rememberCoroutineScope()
            state = rememberYearCalendarState(startYear = 2026, endYear = 2030, firstVisibleYear = 2026)
            YearCalendar(state = state, onMonthClick = {}, monthContent = { ym -> Text(ym.toString()) })
        }
        scope.launch { state.scrollToYear(2028) }
        rule.waitForIdle()
        assertEquals(2028, state.firstVisibleYear)
        rule.onNodeWithText("2028-05", useUnmergedTree = true).assertIsDisplayed()
    }
}
