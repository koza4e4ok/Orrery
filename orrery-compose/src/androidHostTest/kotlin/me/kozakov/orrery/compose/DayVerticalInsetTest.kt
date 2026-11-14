package me.kozakov.orrery.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.LocalDate
import me.kozakov.orrery.core.CalendarDay
import me.kozakov.orrery.core.DayInfo
import me.kozakov.orrery.core.DayPosition
import me.kozakov.orrery.core.Selection
import me.kozakov.orrery.core.SelectionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DayVerticalInsetTest {
    @get:Rule
    val rule = createComposeRule()

    private val date = LocalDate(2026, 7, 9)

    @Composable
    private fun style(inset: Dp): CalendarStyle =
        CalendarStyle(
            colors =
                CalendarDefaults.dayColors(
                    selectedContainerColor = Color.Red,
                    todayIndicatorColor = Color.Transparent,
                ),
            shapes = CalendarDefaults.dayShapes(selectedShape = RectangleShape, verticalInset = inset),
        )

    private fun setSelectedCell(inset: Dp) {
        rule.setContent {
            MaterialTheme {
                val selection =
                    rememberCalendarSelectionState(
                        mode = SelectionMode.Single(),
                        initialSelection = Selection(single = date),
                    )
                Box(Modifier.testTag(CELL).size(48.dp).background(Color.White)) {
                    DefaultDay(
                        day = CalendarDay(date, DayPosition.MonthDate),
                        style = style(inset),
                        modifier = Modifier.size(48.dp),
                        selectionState = selection,
                        today = LocalDate(2026, 7, 1),
                        animateSelection = false,
                    )
                }
            }
        }
    }

    private fun pixel(
        x: Dp,
        y: Dp,
    ): Color {
        val map = rule.onNodeWithTag(CELL).captureToImage().toPixelMap()
        val (px, py) = with(rule.density) { x.roundToPx() to y.roundToPx() }
        return map[px.coerceAtMost(map.width - 1), py.coerceAtMost(map.height - 1)]
    }

    private fun assertColorNear(
        expected: Color,
        actual: Color,
    ) {
        val close =
            abs(expected.red - actual.red) < 0.05f &&
                abs(expected.green - actual.green) < 0.05f &&
                abs(expected.blue - actual.blue) < 0.05f
        assertTrue("expected ~$expected but was $actual", close)
    }

    @Test
    fun defaultInsetLeavesTheTopAndBottomEdgesFree() {
        setSelectedCell(DefaultDayVerticalInset)
        assertColorNear(Color.White, pixel(24.dp, 1.dp))
        assertColorNear(Color.White, pixel(24.dp, 47.dp))
        assertColorNear(Color.Red, pixel(24.dp, 4.dp))
    }

    @Test
    fun zeroInsetRestoresTheEdgeToEdgeLook() {
        setSelectedCell(0.dp)
        assertColorNear(Color.Red, pixel(24.dp, 1.dp))
        assertColorNear(Color.Red, pixel(24.dp, 47.dp))
    }

    @Test
    fun negativeInsetIsTreatedAsZero() {
        setSelectedCell((-8).dp)
        assertColorNear(Color.Red, pixel(24.dp, 1.dp))
    }

    @Test
    fun oversizedInsetDrawsWithoutThrowing() {
        setSelectedCell(100.dp)
        assertColorNear(Color.White, pixel(24.dp, 24.dp))
    }

    @Test
    fun aTapInTheGapStillSelectsTheDay() {
        lateinit var selection: CalendarSelectionState
        rule.setContent {
            MaterialTheme {
                selection = rememberCalendarSelectionState(mode = SelectionMode.Single())
                DefaultDay(
                    day = CalendarDay(date, DayPosition.MonthDate),
                    style = style(8.dp),
                    modifier = Modifier.size(48.dp).testTag(CELL),
                    selectionState = selection,
                    today = LocalDate(2026, 7, 1),
                )
            }
        }
        rule.onNodeWithTag(CELL).performTouchInput { click(Offset(centerX, 2f)) }
        assertEquals(date, selection.selection.single)
    }

    @Test
    fun theInsetNeverChangesTheCellHeight() {
        var inset by mutableStateOf(0.dp)
        rule.setContent {
            MaterialTheme {
                DefaultDay(
                    day = CalendarDay(date, DayPosition.MonthDate),
                    style = style(inset),
                    modifier = Modifier.width(48.dp).testTag(CELL),
                    today = LocalDate(2026, 7, 1),
                    info = DayInfo(label = "Holiday"),
                    decorators = listOf(DayDecorators.eventDots { listOf(Color.Blue) }),
                )
            }
        }
        val heights = mutableMapOf<Dp, Int>()
        for (value in listOf(0.dp, DefaultDayVerticalInset, 6.dp)) {
            inset = value
            rule.waitForIdle()
            heights[value] =
                rule
                    .onNodeWithTag(CELL)
                    .fetchSemanticsNode()
                    .size
                    .height
        }
        assertEquals("heights by inset: $heights", 1, heights.values.toSet().size)
    }

    private companion object {
        const val CELL = "cell"
    }
}
