package me.kozakov.orrery.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
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
import me.kozakov.orrery.core.CalendarDay
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.DayPosition
import me.kozakov.orrery.core.EventSpan
import me.kozakov.orrery.core.Selection
import me.kozakov.orrery.core.SelectionMode
import me.kozakov.orrery.core.eventsByDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DayDecoratorsTest {
    @get:Rule
    val rule = createComposeRule()

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
        tolerance: Float = 0.05f,
    ) {
        val close =
            abs(expected.red - actual.red) <= tolerance &&
                abs(expected.green - actual.green) <= tolerance &&
                abs(expected.blue - actual.blue) <= tolerance
        assertTrue("expected ~$expected but was $actual", close)
    }

    @Test
    fun strikethroughAndOverlayDecoratorsDrawOverEverythingElseBehind() {
        lateinit var layers: List<DecoratorLayer>
        rule.setContent {
            MaterialTheme {
                val selection = rememberCalendarSelectionState(mode = SelectionMode.Range())
                layers =
                    listOf(
                        DayDecorators.eventDots { emptyList() },
                        DayDecorators.stackedBars { emptyList() },
                        DayDecorators.progressRing { null },
                        DayDecorators.progressBar { null },
                        DayDecorators.underline { false },
                        DayDecorators.heatmap { null },
                        DayDecorators.spanningEventBars(eventsByDate = { emptyList() }),
                        DayDecorators.rangePreview(selectionState = selection, targetDate = null),
                        DayDecorators.strikethrough { false },
                    ).map { it.layer }
            }
        }
        assertEquals(
            listOf(
                DecoratorLayer.Behind,
                DecoratorLayer.Behind,
                DecoratorLayer.Behind,
                DecoratorLayer.Behind,
                DecoratorLayer.Behind,
                DecoratorLayer.Behind,
                DecoratorLayer.Over,
                DecoratorLayer.Over,
                DecoratorLayer.Over,
            ),
            layers,
        )
    }

    @Test
    fun emptyDataDrawsWithoutCrashing() {
        rule.setContent {
            MaterialTheme {
                val selection = rememberCalendarSelectionState(mode = SelectionMode.Range())
                DefaultDay(
                    day = CalendarDay(LocalDate(2026, 7, 6), DayPosition.MonthDate),
                    decorators =
                        listOf(
                            DayDecorators.eventDots { emptyList() },
                            DayDecorators.stackedBars { emptyList() },
                            DayDecorators.spanningEventBars(eventsByDate = { emptyList() }),
                            DayDecorators.rangePreview(selectionState = selection, targetDate = null),
                            DayDecorators.progressRing { null },
                            DayDecorators.progressBar { null },
                            DayDecorators.strikethrough { false },
                            DayDecorators.underline { false },
                            DayDecorators.heatmap { null },
                        ),
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun spanningEventBarsExtendFlushAcrossAdjacentDaysAndInsetAtEndpoints() {
        val d1 = LocalDate(2026, 7, 6)
        val d2 = LocalDate(2026, 7, 7)
        val d3 = LocalDate(2026, 7, 8)
        val events =
            listOf(
                CalendarEvent<Nothing>(
                    id = "trip",
                    title = "Conference",
                    span = EventSpan.AllDay(startDate = d1, endDate = d3),
                    colorArgb = 0xFFFF0000L,
                ),
            ).eventsByDate(d1..d3)

        rule.setContent {
            MaterialTheme {
                val decorator =
                    DayDecorators.spanningEventBars(
                        eventsByDate = { events[it].orEmpty() },
                        maxBars = 1,
                        barHeight = 4.dp,
                    )
                Row(Modifier.background(Color.White)) {
                    listOf(d1 to "d1", d2 to "d2", d3 to "d3").forEach { (date, tag) ->
                        DefaultDay(
                            day = CalendarDay(date, DayPosition.MonthDate),
                            modifier = Modifier.size(48.dp).testTag(tag),
                            today = LocalDate(2026, 7, 1),
                            animateSelection = false,
                            decorators = listOf(decorator),
                        )
                    }
                }
            }
        }

        // Bar sits at y = 41..45dp (48 - 3 - 4 = 41, center y = 43dp).
        // Day 1: starts inset (x=1dp is white), center and right edge (x=47dp) are red.
        assertColorNear(Color.White, pixel("d1", 1.dp, 43.dp))
        assertColorNear(Color.Red, pixel("d1", 24.dp, 43.dp))
        assertColorNear(Color.Red, pixel("d1", 47.dp, 43.dp))

        // Day 2: continues from d1 and to d3 -> both left (x=1dp) and right (x=47dp) edges are red.
        assertColorNear(Color.Red, pixel("d2", 1.dp, 43.dp))
        assertColorNear(Color.Red, pixel("d2", 24.dp, 43.dp))
        assertColorNear(Color.Red, pixel("d2", 47.dp, 43.dp))

        // Day 3: continues from d2 (x=1dp is red), ends inset (x=47dp is white).
        assertColorNear(Color.Red, pixel("d3", 1.dp, 43.dp))
        assertColorNear(Color.Red, pixel("d3", 24.dp, 43.dp))
        assertColorNear(Color.White, pixel("d3", 47.dp, 43.dp))
    }

    @Test
    fun spanningEventBarsSortByStartDateAndIdForConsistentTracks() {
        val d1 = LocalDate(2026, 7, 6)
        val d2 = LocalDate(2026, 7, 7)
        val earlier =
            CalendarEvent<Nothing>(
                id = "b",
                title = "Earlier",
                span = EventSpan.AllDay(startDate = d1, endDate = d2),
                colorArgb = 0xFFFF0000L, // Red -> top track
            )
        val later =
            CalendarEvent<Nothing>(
                id = "a",
                title = "Later",
                span = EventSpan.AllDay(startDate = d2, endDate = d2),
                colorArgb = 0xFF0000FFL, // Blue -> second track
            )
        rule.setContent {
            MaterialTheme {
                val decorator =
                    DayDecorators.spanningEventBars(
                        // Deliberately pass in reverse order; decorator must sort by (startDate, id).
                        eventsByDate = { date -> if (date == d2) listOf(later, earlier) else listOf(earlier) },
                        maxBars = 2,
                        barHeight = 4.dp,
                        barSpacing = 2.dp,
                    )
                Box(Modifier.background(Color.White)) {
                    DefaultDay(
                        day = CalendarDay(d2, DayPosition.MonthDate),
                        modifier = Modifier.size(48.dp).testTag("cell"),
                        today = LocalDate(2026, 7, 1),
                        animateSelection = false,
                        decorators = listOf(decorator),
                    )
                }
            }
        }
        // Total height = 2*4 + 2 = 10dp, bottomMargin = 3dp -> topY = 35dp..39dp (Red), second = 41dp..45dp (Blue).
        assertColorNear(Color.Red, pixel("cell", 24.dp, 37.dp))
        assertColorNear(Color.Blue, pixel("cell", 24.dp, 43.dp))
    }

    @Test
    fun stackedBarsDrawCenteredBarsAndAdaptOnSelectedDay() {
        val date = LocalDate(2026, 7, 10)
        rule.setContent {
            MaterialTheme {
                val selectedState =
                    rememberCalendarSelectionState(
                        mode = SelectionMode.Single(),
                        initialSelection = Selection(single = date),
                    )
                val barDecorator =
                    DayDecorators.stackedBars(
                        maxBars = 2,
                        barWidth = 16.dp,
                        barHeight = 4.dp,
                        spacing = 2.dp,
                    ) { listOf(Color.Red, Color.Green) }
                Row(Modifier.background(Color.White)) {
                    DefaultDay(
                        day = CalendarDay(date, DayPosition.MonthDate),
                        modifier = Modifier.size(48.dp).testTag("unselected"),
                        today = LocalDate(2026, 7, 1),
                        colors =
                            CalendarDefaults.dayColors(
                                selectedContainerColor = Color.Blue,
                                selectedContentColor = Color.White,
                            ),
                        animateSelection = false,
                        decorators = listOf(barDecorator),
                    )
                    DefaultDay(
                        day = CalendarDay(date, DayPosition.MonthDate),
                        modifier = Modifier.size(48.dp).testTag("selected"),
                        selectionState = selectedState,
                        today = LocalDate(2026, 7, 1),
                        colors =
                            CalendarDefaults.dayColors(
                                selectedContainerColor = Color.Blue,
                                selectedContentColor = Color.White,
                            ),
                        animateSelection = false,
                        decorators = listOf(barDecorator),
                    )
                }
            }
        }
        // Total height = 2*4 + 2 = 10dp, bottomMargin = 4dp -> topY = 34..38dp (Red), 40..44dp (Green).
        assertColorNear(Color.Red, pixel("unselected", 24.dp, 36.dp))
        assertColorNear(Color.Green, pixel("unselected", 24.dp, 42.dp))
        // Outside barWidth (x = 8dp) is background White.
        assertColorNear(Color.White, pixel("unselected", 8.dp, 36.dp))
        // On selected day, bars switch to selectedContentColor (White).
        assertColorNear(Color.White, pixel("selected", 24.dp, 36.dp))
        assertColorNear(Color.White, pixel("selected", 24.dp, 42.dp))
    }

    @Test
    fun rangePreviewDrawsBandOnlyWhenRangeStartIsOpenAndTargetDiffers() {
        val d1 = LocalDate(2026, 7, 6)
        val d2 = LocalDate(2026, 7, 7)
        val d3 = LocalDate(2026, 7, 8)
        val d4 = LocalDate(2026, 7, 9)
        rule.setContent {
            MaterialTheme {
                val openSelection =
                    rememberCalendarSelectionState(
                        mode = SelectionMode.Range(),
                        initialSelection = Selection(rangeStart = d1),
                    )
                val preview =
                    DayDecorators.rangePreview(
                        selectionState = openSelection,
                        targetDate = d3,
                        color = Color(red = 0f, green = 1f, blue = 0f, alpha = 0.5f),
                    )
                Row(Modifier.background(Color.White)) {
                    listOf(d1 to "d1", d2 to "d2", d3 to "d3", d4 to "d4").forEach { (date, tag) ->
                        DefaultDay(
                            day = CalendarDay(date, DayPosition.MonthDate),
                            modifier = Modifier.size(48.dp).testTag(tag),
                            selectionState = openSelection,
                            today = LocalDate(2026, 7, 1),
                            colors =
                                CalendarDefaults.dayColors(
                                    selectedContainerColor = Color.Blue,
                                    selectedContentColor = Color.White,
                                ),
                            animateSelection = false,
                            decorators = listOf(preview),
                        )
                    }
                }
            }
        }
        // d1 (start): right half has translucent green over white -> red < 0.6, green > 0.9
        val p1Right = pixel("d1", 44.dp, 24.dp)
        assertTrue("expected preview on d1 right half, was $p1Right", p1Right.green > 0.9f && p1Right.red < 0.7f)
        // d1 left edge (outside circle and left half) is White.
        assertColorNear(Color.White, pixel("d1", 2.dp, 24.dp))

        // d2 (middle): both left and right edges have preview band.
        val p2Left = pixel("d2", 2.dp, 24.dp)
        val p2Right = pixel("d2", 46.dp, 24.dp)
        assertTrue("expected preview on d2 left, was $p2Left", p2Left.green > 0.9f && p2Left.red < 0.7f)
        assertTrue("expected preview on d2 right, was $p2Right", p2Right.green > 0.9f && p2Right.red < 0.7f)

        // d3 (target end): left edge has preview band, right edge (x=46dp) is White.
        val p3Left = pixel("d3", 2.dp, 24.dp)
        assertTrue("expected preview on d3 left, was $p3Left", p3Left.green > 0.9f && p3Left.red < 0.7f)
        assertColorNear(Color.White, pixel("d3", 46.dp, 24.dp))

        // d4 (outside): completely untouched White.
        assertColorNear(Color.White, pixel("d4", 2.dp, 24.dp))
    }
}
