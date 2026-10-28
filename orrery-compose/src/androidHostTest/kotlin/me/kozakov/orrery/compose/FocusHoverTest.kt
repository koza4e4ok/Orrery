package me.kozakov.orrery.compose

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.CalendarDay
import me.kozakov.orrery.core.DayPosition
import me.kozakov.orrery.core.Selection
import me.kozakov.orrery.core.SelectionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Focus ring and hover fill rendering. Each test paints the indicator in
 * a marker color no other part of the cell uses and counts marker pixels
 * on the root before and after the interaction.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FocusHoverTest {
    @get:Rule
    val rule = createComposeRule()

    private val day = CalendarDay(LocalDate(2026, 7, 9), DayPosition.MonthDate)
    private val marker = Color(0xFFFF0000)

    private fun SemanticsNodeInteraction.markerPixels(): Int {
        val pixels = captureToImage().toPixelMap()
        var count = 0
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) {
                val c = pixels[x, y]
                if (c.red > 0.8f && c.green < 0.25f && c.blue < 0.25f) count++
            }
        }
        return count
    }

    private fun dayContent(
        colors: @Composable () -> CalendarDayColors,
        selected: Boolean = false,
        enabled: Boolean = true,
    ) {
        rule.setContent {
            MaterialTheme {
                val selection =
                    rememberCalendarSelectionState(
                        mode = SelectionMode.Single(),
                        initialSelection = if (selected) Selection(single = day.date) else Selection(),
                    )
                DefaultDay(
                    day = day,
                    modifier = Modifier.size(48.dp).testTag("day"),
                    selectionState = selection,
                    today = LocalDate(2026, 7, 1),
                    colors = colors(),
                    enabled = enabled,
                    animateSelection = false,
                )
            }
        }
    }

    @Test
    fun dayColorsDefaultFocusToTodayIndicatorAndHoverToTintedContent() {
        lateinit var colors: CalendarDayColors
        rule.setContent {
            MaterialTheme {
                colors = CalendarDefaults.dayColors(todayIndicatorColor = Color.Blue, contentColor = Color.Black)
            }
        }
        rule.runOnIdle {
            assertEquals(Color.Blue, colors.focusedBorderColor)
            assertEquals(Color.Black.copy(alpha = 0.08f), colors.hoverContainerColor)
        }
    }

    @Test
    fun dayShapesDefaultFocusedBorderWidthIsTwoDp() {
        assertEquals(2.dp, CalendarDefaults.dayShapes().focusedBorderWidth)
        assertEquals(3.dp, CalendarDefaults.dayShapes(focusedBorderWidth = 3.dp).focusedBorderWidth)
    }

    @Test
    fun focusedDayDrawsFocusRing() {
        dayContent({ CalendarDefaults.dayColors(focusedBorderColor = marker) })
        assertEquals(0, rule.onRoot().markerPixels())
        rule.onNodeWithTag("day").requestFocus()
        rule.waitForIdle()
        assertTrue(rule.onRoot().markerPixels() > 0)
    }

    @Test
    fun focusedSelectedDayStillDrawsFocusRing() {
        dayContent({ CalendarDefaults.dayColors(focusedBorderColor = marker) }, selected = true)
        rule.onNodeWithTag("day").requestFocus()
        rule.waitForIdle()
        assertTrue(rule.onRoot().markerPixels() > 0)
    }

    @Test
    fun hoveredDayDrawsHoverFill() {
        dayContent({ CalendarDefaults.dayColors(hoverContainerColor = marker) })
        assertEquals(0, rule.onRoot().markerPixels())
        rule.onNodeWithTag("day").performMouseInput { enter(center) }
        rule.waitForIdle()
        assertTrue(rule.onRoot().markerPixels() > 0)
        rule.onNodeWithTag("day").performMouseInput { exit() }
        rule.waitForIdle()
        assertEquals(0, rule.onRoot().markerPixels())
    }

    @Test
    fun hoveredSelectedDayKeepsSelectionFill() {
        dayContent({ CalendarDefaults.dayColors(hoverContainerColor = marker) }, selected = true)
        rule.onNodeWithTag("day").performMouseInput { enter(center) }
        rule.waitForIdle()
        assertEquals(0, rule.onRoot().markerPixels())
    }

    @Test
    fun unavailableDayShowsNoHoverFill() {
        dayContent({ CalendarDefaults.dayColors(hoverContainerColor = marker) }, enabled = false)
        rule.onNodeWithTag("day").performMouseInput { enter(center) }
        rule.waitForIdle()
        assertEquals(0, rule.onRoot().markerPixels())
    }

    @Test
    fun focusedHeatmapCellDrawsFocusRing() {
        rule.setContent {
            MaterialTheme(colorScheme = lightColorScheme(onSurface = marker)) {
                HeatmapCalendar(
                    intensity = { 0.5f },
                    state =
                        rememberHeatmapCalendarState(
                            startDate = LocalDate(2026, 7, 1),
                            endDate = LocalDate(2026, 7, 31),
                            firstDayOfWeek = DayOfWeek.MONDAY,
                        ),
                    onDayClick = {},
                    showMonthLabels = false,
                    showWeekdayLabels = false,
                    showLegend = false,
                    summary = null,
                )
            }
        }
        assertEquals(0, rule.onRoot().markerPixels())
        rule.onNodeWithContentDescription("Jul 8, 2026", substring = true).requestFocus()
        rule.waitForIdle()
        assertTrue(rule.onRoot().markerPixels() > 0)
    }

    @Test
    fun focusedMiniMonthDrawsFocusRing() {
        rule.setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = marker)) {
                YearCalendar(
                    state = rememberYearCalendarState(startYear = 2026, endYear = 2026, firstVisibleYear = 2026),
                    onMonthClick = {},
                    monthContent = { ym -> Text(ym.toString()) },
                )
            }
        }
        assertEquals(0, rule.onRoot().markerPixels())
        // clickable month cells merge descendants, so the merged node is the focus target
        rule.onNodeWithText("2026-03").requestFocus()
        rule.waitForIdle()
        assertTrue(rule.onRoot().markerPixels() > 0)
    }

    @Test
    fun twelveColorConstructorDefaultsFocusAndHoverColors() {
        val colors =
            CalendarDayColors(
                Color.Transparent,
                Color.Black,
                Color.Black,
                Color.Blue,
                Color.Blue,
                Color.White,
                Color.Gray,
                Color.Black,
                Color.Gray,
                Color.Transparent,
                Color.Gray,
                Color.Gray,
            )
        assertEquals(Color.Blue, colors.focusedBorderColor)
        assertEquals(Color.Black.copy(alpha = 0.08f), colors.hoverContainerColor)
        assertEquals(Color.Gray, colors.holidayLabelColor)
    }
}
