package me.kozakov.orrery.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
class SpanningBarsTest {
    @get:Rule val rule = createComposeRule()

    private val red = Color(0xFFE53935)
    private val blue = Color(0xFF1E88E5)
    private val mon = LocalDate(2026, 7, 6)
    private val tue = LocalDate(2026, 7, 7)

    // A: Mon–Wed (red). B: Tue only (blue). Monday has 1 event, Tuesday 2.
    private val a =
        CalendarEvent<Nothing>(
            id = "a",
            title = "A",
            span = EventSpan.AllDay(mon, LocalDate(2026, 7, 8)),
            colorArgb = 0xFFE53935,
        )
    private val b = CalendarEvent<Nothing>(id = "b", title = "B", span = EventSpan.AllDay(tue), colorArgb = 0xFF1E88E5)

    private fun eventsOn(date: LocalDate): List<CalendarEvent<*>> =
        listOf(a, b).filter { date in it.span.startDate..it.span.endDate }

    private fun render(contentBottom: Dp? = null) {
        rule.setContent {
            MaterialTheme {
                val decorator =
                    DayDecorators.spanningEventBars(::eventsOn, maxBars = 2, firstDayOfWeek = DayOfWeek.MONDAY)
                val bottomPx = contentBottom?.let { with(LocalDensity.current) { it.toPx() } } ?: Float.NaN
                val cell =
                    DayCellDrawState(
                        false,
                        Color.Unspecified,
                        hasLabel = contentBottom != null,
                        contentBottom = bottomPx,
                    )
                Row {
                    for ((tag, date) in listOf("mon" to mon, "tue" to tue)) {
                        Box(
                            Modifier.testTag(tag).size(48.dp).background(Color.White).drawBehind {
                                drawDecorator(decorator, CalendarDay(date, DayPosition.MonthDate), cell)
                            },
                        )
                    }
                }
            }
        }
    }

    private fun pixel(
        tag: String,
        y: Dp,
    ): Color {
        val map = rule.onNodeWithTag(tag).captureToImage().toPixelMap()
        val (px, py) = with(rule.density) { 24.dp.roundToPx() to y.roundToPx() }
        return map[px, py]
    }

    private fun assertNear(
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
    fun multiDayEventKeepsItsLaneAcrossDaysWithDifferentEventCounts() {
        render()
        // 2 lanes used this week; no label → bottom margin 3dp; stack top = 48 - 3 - (4 + 2 + 4) = 35dp.
        assertNear(red, pixel("mon", 37.dp)) // lane 0 on Monday
        assertNear(red, pixel("tue", 37.dp)) // lane 0 on Tuesday, same height
        assertNear(blue, pixel("tue", 43.dp)) // lane 1
        assertNear(Color.White, pixel("mon", 43.dp))
    }

    @Test
    fun lanesThatWouldCoverTheLabelAreNotDrawn() {
        // Content ends at 40dp; with a label the bottom margin is 1dp → 7dp free → 1 lane (4dp) fits.
        render(contentBottom = 40.dp)
        assertNear(red, pixel("tue", 45.dp)) // lane 0 moved to the bottom: 48 - 1 - 4 = 43..47
        assertNear(Color.White, pixel("tue", 39.dp)) // where a 2-lane stack would have started
    }
}
