package me.kozakov.orrery.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Layout resolution and synchronized paging of the dual-month range picker (#88b). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = DUAL_WIDE_WINDOW)
class DualMonthLayoutTest {
    @get:Rule val rule = createComposeRule()

    private lateinit var state: OrreryDateRangePickerState
    private val bothTitles = "July 2026 – August 2026"

    private fun content(
        layoutMode: DateRangeCalendarLayoutMode = DateRangeCalendarLayoutMode.Adaptive(),
        displayedMonth: YearMonth = YearMonth(2026, 7),
        yearRange: IntRange = 2026..2027,
    ) {
        rule.setContent {
            MaterialTheme {
                state =
                    rememberOrreryDateRangePickerState(
                        initialDisplayedMonth = displayedMonth,
                        yearRange = yearRange,
                    )
                OrreryDateRangePickerDialog(
                    onDismissRequest = {},
                    confirmButton = { TextButton(onClick = {}) { Text("Save") } },
                    state = state,
                    today = LocalDate(2026, 7, 15),
                    layoutMode = layoutMode,
                )
            }
        }
    }

    private fun assertDual(titles: String = bothTitles) {
        rule.onNodeWithContentDescription(titles).assertIsDisplayed()
    }

    private fun assertSingle() {
        rule.onAllNodes(hasContentDescription(" – ", substring = true)).assertCountEquals(0)
    }

    @Test
    fun adaptiveShowsTwoMonthsOnAWideWindow() {
        content()
        assertDual()
        rule.onNodeWithContentDescription("July 2026").assertIsDisplayed()
        rule.onNodeWithContentDescription("August 2026").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = DUAL_PHONE_WINDOW)
    fun adaptiveKeepsTheSingleColumnOnAPhone() {
        content()
        assertSingle()
    }

    @Test
    @Config(qualifiers = DUAL_PHONE_WINDOW)
    fun forcedDualMonthShowsTwoMonthsOnAPhone() {
        content(layoutMode = DateRangeCalendarLayoutMode.DualMonth)
        assertDual()
    }

    @Test
    fun forcedSingleMonthKeepsTheColumnOnAWideWindow() {
        content(layoutMode = DateRangeCalendarLayoutMode.SingleMonth)
        assertSingle()
    }

    @Test
    fun adaptiveThresholdIsConfigurable() {
        content(layoutMode = DateRangeCalendarLayoutMode.Adaptive(minDualMonthWidth = 1000.dp))
        assertSingle()
    }

    @Test
    fun chevronsShiftBothMonthsByOne() {
        content()
        rule.onNodeWithContentDescription("Next month").performClick()
        rule.waitForIdle()
        assertDual("August 2026 – September 2026")
        rule.runOnIdle { assertEquals(YearMonth(2026, 8), state.displayedMonth) }
        rule.onNodeWithContentDescription("Previous month").performClick()
        rule.waitForIdle()
        assertDual()
        rule.runOnIdle { assertEquals(YearMonth(2026, 7), state.displayedMonth) }
    }

    @Test
    fun previousIsDisabledAtTheFirstMonth() {
        content(displayedMonth = YearMonth(2026, 1), yearRange = 2026..2026)
        rule.onNodeWithContentDescription("Previous month").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Next month").assertIsEnabled()
    }

    @Test
    fun nextIsDisabledOnceTheRightPaneShowsTheLastMonth() {
        content(displayedMonth = YearMonth(2026, 11), yearRange = 2026..2026)
        assertDual("November 2026 – December 2026")
        rule.onNodeWithContentDescription("Next month").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Previous month").assertIsEnabled()
    }
}

private const val DUAL_WIDE_WINDOW = "w900dp-h800dp"
private const val DUAL_PHONE_WINDOW = "w411dp-h900dp"
