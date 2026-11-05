package me.kozakov.orrery.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

// Native graphics so month labels are measured with real font metrics.
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HeatmapLabelLayoutTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun aLabelOnTheLastColumnStaysInsideTheGrid() {
        // Monday-first, the last week column (Jul 27 – Aug 2) holds Aug 1 and so carries "Aug".
        rule.setContent {
            MaterialTheme {
                HeatmapCalendar(
                    intensity = { 0.5f },
                    state =
                        rememberHeatmapCalendarState(
                            startDate = LocalDate(2025, 8, 2),
                            endDate = LocalDate(2026, 8, 1),
                            firstDayOfWeek = DayOfWeek.MONDAY,
                        ),
                    modifier = Modifier.testTag("heatmap"),
                    showLegend = false,
                    summary = null,
                )
            }
        }
        val label = rule.onNodeWithText("Aug").getUnclippedBoundsInRoot()
        val grid = rule.onNodeWithTag("heatmap").getUnclippedBoundsInRoot()
        assertTrue("Aug label ends at ${label.right}, grid at ${grid.right}", label.right <= grid.right + 0.5.dp)
    }

    @Test
    fun monthLabelsDoNotWidenTheirWeekColumns() {
        lateinit var state: HeatmapCalendarState
        rule.setContent {
            MaterialTheme {
                state =
                    rememberHeatmapCalendarState(
                        startDate = LocalDate(2025, 7, 16),
                        endDate = LocalDate(2026, 7, 15),
                        firstDayOfWeek = DayOfWeek.MONDAY,
                    )
                HeatmapCalendar(intensity = { 0.5f }, state = state)
            }
        }
        rule.runOnIdle {
            val widths =
                state.listState.layoutInfo.visibleItemsInfo
                    .map { it.size }
                    .distinct()
            assertEquals("week columns have different widths: $widths", 1, widths.size)
        }
    }
}
