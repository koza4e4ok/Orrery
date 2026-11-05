package me.kozakov.orrery.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
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
