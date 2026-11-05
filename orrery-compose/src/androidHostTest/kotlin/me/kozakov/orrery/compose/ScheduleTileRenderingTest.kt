package me.kozakov.orrery.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.EventSpan
import me.kozakov.orrery.core.PositionedEvent
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScheduleTileRenderingTest {
    @get:Rule val rule = createComposeRule()

    private val day = LocalDate(2026, 7, 15)

    private fun positioned(title: String): PositionedEvent<Nothing> =
        PositionedEvent(
            event =
                CalendarEvent(
                    id = title,
                    title = title,
                    span = EventSpan.Timed(LocalDateTime(2026, 7, 15, 12, 30), LocalDateTime(2026, 7, 15, 13, 30)),
                    colorArgb = 0xFFE53935,
                ),
            date = day,
            startMinute = 750,
            endMinute = 810,
            displayEndMinute = 810,
            columnIndex = 0,
            columnCount = 1,
            leftFraction = 0f,
            widthFraction = 1f,
        )

    private fun pixel(
        tag: String,
        x: Dp,
        y: Dp,
    ): Color {
        val map = rule.onNodeWithTag(tag).captureToImage().toPixelMap()
        val (px, py) = with(rule.density) { x.roundToPx() to y.roundToPx() }
        return map[px.coerceAtMost(map.width - 1), py.coerceAtMost(map.height - 1)]
    }

    private fun assertColorNear(
        expected: Color,
        actual: Color,
        tolerance: Float = 0.03f,
    ) {
        val close =
            abs(expected.red - actual.red) <= tolerance &&
                abs(expected.green - actual.green) <= tolerance &&
                abs(expected.blue - actual.blue) <= tolerance
        assertTrue("expected ~$expected but was $actual", close)
    }

    @Test
    fun timedTileHidesGridLinesBehindIt() {
        rule.setContent {
            MaterialTheme {
                Box(
                    Modifier
                        .testTag("host")
                        .size(120.dp, 60.dp)
                        .background(MaterialTheme.colorScheme.surface)
                        .drawBehind {
                            drawLine(
                                Color.Black,
                                Offset(0f, size.height / 2),
                                Offset(size.width, size.height / 2),
                                2.dp.toPx(),
                            )
                        },
                ) {
                    ScheduleDefaults.EventTile(positioned("Lunch"), ScheduleDefaults.colors())
                }
            }
        }
        // x = 100dp is right of the text, so both samples hit the bare tile fill.
        assertColorNear(pixel("host", 100.dp, 45.dp), pixel("host", 100.dp, 30.dp))
    }
}
