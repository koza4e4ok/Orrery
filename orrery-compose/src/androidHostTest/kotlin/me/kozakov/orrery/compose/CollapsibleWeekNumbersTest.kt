package me.kozakov.orrery.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class CollapsibleWeekNumbersTest {
    @get:Rule val rule = createComposeRule()

    private fun content(expanded: Boolean) {
        rule.setContent {
            MaterialTheme {
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
                            firstVisibleDate = LocalDate(2026, 7, 6),
                            firstDayOfWeek = DayOfWeek.MONDAY,
                        ),
                    state = rememberCollapsibleCalendarState(initialExpanded = expanded),
                    showWeekNumbers = true,
                    dayContent = { day -> DefaultDay(day = day, today = LocalDate(2026, 7, 15)) },
                ) {}
            }
        }
    }

    private fun assertMondayHeaderAlignedWithMondayCells() {
        val header = rule.onNodeWithText("Mon").getUnclippedBoundsInRoot()
        val cell =
            rule
                .onAllNodes(hasContentDescription("July 6, 2026", substring = true))
                .onFirst()
                .getUnclippedBoundsInRoot()
        val headerCenter = (header.left + header.right) / 2
        val cellCenter = (cell.left + cell.right) / 2
        assertTrue(
            "Mon header at $headerCenter, Monday cell at $cellCenter",
            abs((headerCenter - cellCenter).value) < 1f,
        )
    }

    @Test
    fun expandedScaffoldWithWeekNumbersKeepsHeaderAligned() {
        content(expanded = true)
        assertMondayHeaderAlignedWithMondayCells()
    }

    @Test
    fun collapsedScaffoldWithWeekNumbersKeepsHeaderAligned() {
        content(expanded = false)
        assertMondayHeaderAlignedWithMondayCells()
    }
}
