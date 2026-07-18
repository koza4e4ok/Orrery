package me.kozakov.orrery.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeatmapCalendarTest {
    @get:Rule val rule = createComposeRule()

    private val start = LocalDate(2025, 7, 16)
    private val end = LocalDate(2026, 7, 15)
    private val strings = HeatmapDefaults.strings()

    private fun intensity(date: LocalDate): Float? = if (date.day % 5 == 0) null else (date.day % 5) / 4f

    private lateinit var state: HeatmapCalendarState

    private fun content(
        onDayClick: ((LocalDate) -> Unit)? = null,
        showMonthLabels: Boolean = true,
        showWeekdayLabels: Boolean = true,
        showLegend: Boolean = true,
    ) {
        rule.setContent {
            MaterialTheme {
                state =
                    rememberHeatmapCalendarState(
                        startDate = start,
                        endDate = end,
                        firstDayOfWeek = DayOfWeek.MONDAY,
                    )
                HeatmapCalendar(
                    intensity = ::intensity,
                    state = state,
                    onDayClick = onDayClick,
                    showMonthLabels = showMonthLabels,
                    showWeekdayLabels = showWeekdayLabels,
                    showLegend = showLegend,
                )
            }
        }
    }

    @Test
    fun startsScrolledToRangeEnd() {
        content()
        rule.runOnIdle {
            val lastVisible =
                state.listState.layoutInfo.visibleItemsInfo
                    .last()
                    .index
            assertEquals(state.weekCount - 1, lastVisible)
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
    fun emptyCellDescribesNoData() {
        content(onDayClick = {})
        rule.onNodeWithContentDescription("Jul 10, 2026, no data").assertExists()
    }

    @Test
    fun cellsNotClickableWithoutCallback() {
        content(onDayClick = null)
        rule.onNodeWithContentDescription("Jul 8, 2026, level 4 of 5").assertHasNoClickAction()
    }

    @Test
    fun cellsClickableWithCallback() {
        content(onDayClick = {})
        rule.onNodeWithContentDescription("Jul 8, 2026, level 4 of 5").assertHasClickAction()
    }

    @Test
    fun chromeTogglesOff() {
        content(showMonthLabels = false, showWeekdayLabels = false, showLegend = false)
        rule.onAllNodesWithText("Jul").assertCountEquals(0)
        rule.onAllNodesWithText("Mon").assertCountEquals(0)
        rule.onAllNodesWithText(strings.legendLess).assertCountEquals(0)
    }

    @Test
    fun legendAndSummaryRender() {
        content()
        rule.onNodeWithText(strings.legendLess).assertExists()
        rule.onNodeWithText(strings.legendMore).assertExists()
        rule.onAllNodesWithText("active days", substring = true).assertCountEquals(1)
    }

    @Test
    fun scrollToDateMovesWindow() {
        content()
        rule.waitForIdle()
        runBlocking { state.scrollToDate(start) }
        rule.runOnIdle { assertEquals(state.weekStartAt(0), state.firstVisibleWeekStart) }
    }

    @Test
    fun stateSurvivesRestoration() {
        val restorationTester = StateRestorationTester(rule)
        restorationTester.setContent {
            MaterialTheme {
                state =
                    rememberHeatmapCalendarState(
                        startDate = start,
                        endDate = end,
                        firstDayOfWeek = DayOfWeek.MONDAY,
                    )
                HeatmapCalendar(intensity = ::intensity, state = state)
            }
        }
        rule.waitForIdle()
        runBlocking { state.scrollToDate(LocalDate(2026, 1, 15)) }
        val expected = rule.runOnIdle { state.firstVisibleWeekStart }
        restorationTester.emulateSavedInstanceStateRestore()
        rule.runOnIdle { assertEquals(expected, state.firstVisibleWeekStart) }
    }
}
