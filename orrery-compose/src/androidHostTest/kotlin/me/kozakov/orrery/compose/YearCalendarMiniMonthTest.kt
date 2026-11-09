package me.kozakov.orrery.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The default [CalendarDefaults.MiniMonth] inside [YearCalendar]: one canvas node per month. */
@RunWith(AndroidJUnit4::class)
class YearCalendarMiniMonthTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate(2026, 7, 15)

    private fun setYear(onMonthClick: (YearMonth) -> Unit = {}) {
        rule.setContent {
            MaterialTheme {
                YearCalendar(
                    state = rememberYearCalendarState(startYear = 2026, endYear = 2026, firstVisibleYear = 2026),
                    onMonthClick = onMonthClick,
                    monthContent = { CalendarDefaults.MiniMonth(it, today = today) },
                )
            }
        }
    }

    private fun SemanticsNode.countNodes(): Int = 1 + children.sumOf { it.countNodes() }

    @Test
    fun yearPageHasOneSemanticsNodePerMonthInsteadOfOnePerDay() {
        setYear()
        val nodes = rule.onRoot(useUnmergedTree = true).fetchSemanticsNode().countNodes()
        // Root + list + 12 clickable cells + 12 mini-month canvases; the old layout had ~40 Text nodes per month.
        assertTrue("year page has $nodes semantics nodes", nodes <= 40)
        assertEquals(
            12,
            rule
                .onAllNodesWithContentDescription("2026", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .size,
        )
        assertEquals(0, rule.onAllNodesWithText("15", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun monthCellIsAButtonDescribedByItsMonthAndClickReportsIt() {
        var clicked: YearMonth? = null
        setYear { clicked = it }
        rule
            .onNodeWithContentDescription("July 2026")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Today"))
            .performClick()
        assertEquals(YearMonth(2026, 7), clicked)
        rule
            .onNodeWithContentDescription("March 2026")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun monthCellTakesKeyboardFocus() {
        lateinit var inputModeManager: InputModeManager
        rule.setContent {
            MaterialTheme {
                inputModeManager = LocalInputModeManager.current
                YearCalendar(
                    state = rememberYearCalendarState(startYear = 2026, endYear = 2026, firstVisibleYear = 2026),
                    onMonthClick = {},
                    monthContent = { CalendarDefaults.MiniMonth(it, today = today) },
                )
            }
        }
        rule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        rule.onNodeWithContentDescription("May 2026").requestFocus()
        rule.onNodeWithContentDescription("May 2026").assertIsFocused()
    }

    @Test
    fun scrollingTwentyYearsMeasuresEachTextLayoutOnce() {
        lateinit var caches: MiniMonthCaches
        lateinit var state: YearCalendarState
        lateinit var scope: CoroutineScope
        rule.setContent {
            MaterialTheme {
                val measurer = rememberTextMeasurer(cacheSize = 0)
                caches = remember(measurer) { MiniMonthCaches(measurer) }
                state = rememberYearCalendarState(startYear = 2026, endYear = 2045, firstVisibleYear = 2026)
                scope = rememberCoroutineScope()
                CompositionLocalProvider(LocalMiniMonthCaches provides caches) {
                    YearCalendar(
                        state = state,
                        onMonthClick = {},
                        monthContent = { CalendarDefaults.MiniMonth(it, today = today) },
                    )
                }
            }
        }
        for (year in 2027..2045) {
            rule.runOnIdle { scope.launch { state.scrollToYear(year) } }
            rule.waitForIdle()
        }
        assertEquals(2045, state.firstVisibleYear)
        // 31 regular + 31 bold day numbers (bold only for today) + 12 month titles, whatever the year count.
        val measured = caches.texts.measureCount
        assertTrue("measured $measured layouts", measured <= 31 + 31 + 12)
        assertTrue(caches.models.size <= MINI_MONTH_MODEL_CACHE_CAPACITY)
        assertTrue(caches.models.misses >= 20 * 12)
    }
}
