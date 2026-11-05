package me.kozakov.orrery.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import me.kozakov.orrery.core.CalendarDay
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.DayPosition
import me.kozakov.orrery.core.EventSpan
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AuditPolishTest {
    @get:Rule val rule = createComposeRule()

    private fun pixel(
        tag: String,
        x: Dp,
        y: Dp,
    ): Color {
        val map = rule.onNodeWithTag(tag).captureToImage().toPixelMap()
        val (px, py) = with(rule.density) { x.roundToPx() to y.roundToPx() }
        return map[px.coerceAtMost(map.width - 1), py.coerceAtMost(map.height - 1)]
    }

    private fun assertNear(
        expected: Color,
        actual: Color,
        tolerance: Float = 0.06f,
    ) {
        val close =
            abs(expected.red - actual.red) <= tolerance &&
                abs(expected.green - actual.green) <= tolerance &&
                abs(expected.blue - actual.blue) <= tolerance
        assertTrue("expected ~$expected but was $actual", close)
    }

    @Test
    fun progressRingLeavesRoomForTheCellOutline() {
        rule.setContent {
            MaterialTheme {
                val ring = DayDecorators.progressRing(color = Color.Red, trackColor = Color.Transparent) { 1f }
                Box(
                    Modifier.testTag("cell").size(48.dp).background(Color.White).drawBehind {
                        drawDecorator(
                            ring,
                            CalendarDay(LocalDate(2026, 7, 8), DayPosition.MonthDate),
                            DayCellDrawState.Unknown,
                        )
                    },
                )
            }
        }
        // Stroke 2dp, inset 3dp: the ring spans y = 3..5dp at the top; the outer 0..2dp stays free for the outline.
        assertNear(Color.White, pixel("cell", 24.dp, 1.dp))
        assertNear(Color.Red, pixel("cell", 24.dp, 4.dp))
    }

    @Test
    fun fullCellTurnoverDayIsFilled() {
        var inRange = Color.Unspecified
        rule.setContent {
            MaterialTheme {
                val style = CalendarDefaults.hospitalityStyle(rangeEndpointStyle = RangeEndpointStyle.FullCell)
                inRange = style.colors.inRangeContainerColor
                Box(Modifier.testTag("cell").size(48.dp).background(Color.White)) {
                    DefaultDay(
                        day = CalendarDay(LocalDate(2026, 7, 14), DayPosition.MonthDate),
                        style = style,
                        turnoverDates = setOf(LocalDate(2026, 7, 14)),
                        today = LocalDate(2026, 7, 1),
                    )
                }
            }
        }
        val fill = rule.runOnIdle { inRange }
        val expected = fill.copy(alpha = 1f).compositeOverWhite(fill.alpha)
        // Two corners on opposite sides of the divider, away from the day number.
        assertNear(expected, pixel("cell", 6.dp, 6.dp))
        assertNear(expected, pixel("cell", 42.dp, 42.dp))
    }

    private fun Color.compositeOverWhite(alpha: Float) =
        Color(red * alpha + (1 - alpha), green * alpha + (1 - alpha), blue * alpha + (1 - alpha))

    @Test
    fun shortScheduleCapsAllDayRows() {
        val day = LocalDate(2026, 7, 15)
        val allDay =
            (1..3).map {
                CalendarEvent<Nothing>(
                    id = "a$it",
                    title = "Trip $it",
                    span = EventSpan.AllDay(day),
                )
            }
        rule.setContent {
            MaterialTheme {
                DaySchedule(
                    events = { if (it == day) allDay else emptyList() },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    state = rememberScheduleState(firstVisibleDate = day, firstDayOfWeek = DayOfWeek.MONDAY),
                    today = day,
                    dayHeader = null,
                )
            }
        }
        // 200dp / 4 = 50dp fits 2 rows of 20dp + 2dp gap: one chip and a "+2" overflow.
        rule.onNodeWithText("+2", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Trip 3", useUnmergedTree = true).assertDoesNotExist()
    }
}
