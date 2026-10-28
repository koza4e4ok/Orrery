package me.kozakov.orrery.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.LocalDate
import me.kozakov.orrery.core.CalendarDay
import me.kozakov.orrery.core.DayInfo
import me.kozakov.orrery.core.DayPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CalendarStyleTest {
    @get:Rule
    val rule = createComposeRule()

    private val day = CalendarDay(LocalDate(2026, 10, 14), DayPosition.MonthDate)
    private val today = LocalDate(2026, 10, 1)

    @Test
    fun densityHelpers() {
        assertEquals(36.dp, CalendarDensity.Compact.minCellHeight)
        assertEquals(48.dp, CalendarDensity.Comfortable.minCellHeight)
        assertEquals(56.dp, CalendarDensity.Expanded.minCellHeight)
        assertFalse(CalendarDensity.Compact.showSecondaryLabel)
        assertTrue(CalendarDensity.Comfortable.showSecondaryLabel)
        assertTrue(CalendarDensity.Expanded.showSecondaryLabel)
    }

    @Test
    fun presetsBundleShapesDensityAndBorders() {
        lateinit var default: CalendarStyle
        lateinit var roundedSquare: CalendarStyle
        lateinit var segmented: CalendarStyle
        lateinit var bordered: CalendarStyle
        val scheme = lightColorScheme()
        rule.setContent {
            MaterialTheme(colorScheme = scheme) {
                default = CalendarDefaults.style()
                roundedSquare = CalendarDefaults.roundedSquareStyle()
                segmented = CalendarDefaults.segmentedRangeStyle()
                bordered = CalendarDefaults.borderedCardStyle()
            }
        }
        rule.waitForIdle()

        assertEquals(CalendarDensity.Comfortable, default.density)
        assertEquals(CircleShape, default.shapes.dayShape)
        assertEquals(CircleShape, default.shapes.selectedShape)
        assertEquals(RectangleShape, default.shapes.inRangeShape)
        assertEquals(0.dp, default.cellBorderWidth)
        assertEquals(Color.Transparent, default.cellBorderColor)
        assertEquals(0.dp, default.selectedElevation)

        assertEquals(CalendarDensity.Comfortable, roundedSquare.density)
        assertEquals(RoundedCornerShape(10.dp), roundedSquare.shapes.dayShape)
        assertEquals(RoundedCornerShape(10.dp), roundedSquare.shapes.selectedShape)
        assertEquals(RectangleShape, roundedSquare.shapes.inRangeShape)

        assertEquals(CalendarDensity.Comfortable, segmented.density)
        assertEquals(RoundedCornerShape(8.dp), segmented.shapes.dayShape)
        assertEquals(RoundedCornerShape(8.dp), segmented.shapes.selectedShape)
        assertEquals(RoundedCornerShape(8.dp), segmented.shapes.inRangeShape)

        assertEquals(CalendarDensity.Expanded, bordered.density)
        assertEquals(RoundedCornerShape(8.dp), bordered.shapes.dayShape)
        assertEquals(1.dp, bordered.cellBorderWidth)
        assertEquals(scheme.outlineVariant, bordered.cellBorderColor)
        assertEquals(2.dp, bordered.selectedElevation)
    }

    @Test
    fun densitySetsMinCellHeight() {
        rule.setContent {
            Column {
                CalendarDensity.entries.forEach { density ->
                    DefaultDay(
                        day = day,
                        style = CalendarDefaults.style(density = density),
                        modifier = Modifier.width(48.dp).testTag("cell-$density"),
                        today = today,
                    )
                }
            }
        }
        CalendarDensity.entries.forEach { density ->
            rule.onNodeWithTag("cell-$density").assertHeightIsEqualTo(density.minCellHeight)
        }
    }

    @Test
    fun legacyOverloadKeepsComfortableHeight() {
        rule.setContent {
            DefaultDay(day = day, modifier = Modifier.width(48.dp).testTag(CELL), today = today)
        }
        rule.onNodeWithTag(CELL).assertHeightIsEqualTo(48.dp)
    }

    @Test
    fun compactHidesSecondaryLabel() {
        rule.setContent {
            DefaultDay(
                day = day,
                style = CalendarDefaults.style(density = CalendarDensity.Compact),
                info = DayInfo(label = LABEL),
                today = today,
            )
        }
        rule.onNodeWithText("14", useUnmergedTree = true).assertExists()
        rule.onNodeWithText(LABEL, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun comfortableShowsSecondaryLabel() {
        rule.setContent {
            DefaultDay(
                day = day,
                style = CalendarDefaults.style(density = CalendarDensity.Comfortable),
                info = DayInfo(label = LABEL),
                today = today,
            )
        }
        rule.onNodeWithText(LABEL, useUnmergedTree = true).assertExists()
    }

    @Test
    fun cellBorderOutlinesDayShape() {
        rule.setContent {
            Box(Modifier.background(Color.White)) {
                DefaultDay(
                    day = day,
                    style =
                        CalendarDefaults.style(
                            shapes = CalendarDefaults.dayShapes(dayShape = RectangleShape),
                            cellBorderWidth = 4.dp,
                            cellBorderColor = Color.Red,
                        ),
                    modifier = Modifier.size(48.dp).testTag(CELL),
                    today = today,
                )
            }
        }
        assertColorNear(Color.Red, pixel(0.dp, 24.dp))
        assertColorNear(Color.White, pixel(8.dp, 24.dp))
    }

    @Test
    fun noBorderByDefault() {
        rule.setContent {
            Box(Modifier.background(Color.White)) {
                DefaultDay(
                    day = day,
                    style = CalendarDefaults.style(shapes = CalendarDefaults.dayShapes(dayShape = RectangleShape)),
                    modifier = Modifier.size(48.dp).testTag(CELL),
                    today = today,
                )
            }
        }
        assertColorNear(Color.White, pixel(0.dp, 24.dp))
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

    private companion object {
        const val CELL = "cell"
        const val LABEL = "Holiday"
    }
}
