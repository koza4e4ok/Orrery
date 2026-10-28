package me.kozakov.orrery.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
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
class DefaultDayPolishTest {
    @get:Rule
    val rule = createComposeRule()

    private val day = CalendarDay(LocalDate(2026, 10, 14), DayPosition.MonthDate)
    private val today = LocalDate(2026, 10, 1)
    private val longLabel = "Day of Defenders and Defendresses of Ukraine"

    private fun textLayout(text: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        val node = rule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts[0]
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
    fun longLabelStaysOnOneEllipsizedLine() {
        rule.setContent {
            DefaultDay(
                day = day,
                modifier = Modifier.width(48.dp),
                info = DayInfo(label = longLabel, isHoliday = true),
                today = today,
            )
        }
        val layout = textLayout(longLabel)
        assertEquals(1, layout.lineCount)
        assertTrue(layout.isLineEllipsized(0))
    }

    @Test
    fun longLabelDoesNotShiftDayNumber() {
        rule.setContent {
            Row {
                DefaultDay(
                    day = CalendarDay(LocalDate(2026, 10, 13), DayPosition.MonthDate),
                    modifier = Modifier.width(48.dp),
                    info = DayInfo(label = "Tue"),
                    today = today,
                )
                DefaultDay(
                    day = day,
                    modifier = Modifier.width(48.dp),
                    info = DayInfo(label = longLabel, isHoliday = true),
                    today = today,
                )
            }
        }
        val shortTop = rule.onNodeWithText("13", useUnmergedTree = true).getBoundsInRoot().top
        val longTop = rule.onNodeWithText("14", useUnmergedTree = true).getBoundsInRoot().top
        assertEquals(shortTop, longTop)
    }

    @Test
    fun eventDotsUseSelectedContentColorOnSelectedDay() {
        rule.setContent {
            val selection =
                rememberCalendarSelectionState(
                    mode = SelectionMode.Single(),
                    initialSelection = Selection(single = day.date),
                )
            Box(Modifier.background(Color.White)) {
                DefaultDay(
                    day = day,
                    modifier = Modifier.size(48.dp).testTag(CELL),
                    selectionState = selection,
                    colors = testColors(),
                    animateSelection = false,
                    decorators = listOf(DayDecorators.eventDots(maxDots = 1, dotSize = 6.dp) { listOf(Color.Red) }),
                    today = today,
                )
            }
        }
        assertColorNear(Color.White, pixel(24.dp, 42.dp))
    }

    @Test
    fun eventDotsKeepTheirColorOnUnselectedDay() {
        rule.setContent {
            Box(Modifier.background(Color.White)) {
                DefaultDay(
                    day = day,
                    modifier = Modifier.size(48.dp).testTag(CELL),
                    colors = testColors(),
                    decorators = listOf(DayDecorators.eventDots(maxDots = 1, dotSize = 6.dp) { listOf(Color.Red) }),
                    today = today,
                )
            }
        }
        assertColorNear(Color.Red, pixel(24.dp, 42.dp))
    }

    @Test
    fun eventDotsHonorExplicitSelectedColor() {
        rule.setContent {
            val selection =
                rememberCalendarSelectionState(
                    mode = SelectionMode.Single(),
                    initialSelection = Selection(single = day.date),
                )
            Box(Modifier.background(Color.White)) {
                DefaultDay(
                    day = day,
                    modifier = Modifier.size(48.dp).testTag(CELL),
                    selectionState = selection,
                    colors = testColors(),
                    animateSelection = false,
                    decorators =
                        listOf(
                            DayDecorators.eventDots(selectedColor = Color.Yellow, maxDots = 1, dotSize = 6.dp) {
                                listOf(Color.Red)
                            },
                        ),
                    today = today,
                )
            }
        }
        assertColorNear(Color.Yellow, pixel(24.dp, 42.dp))
    }

    @Test
    fun eventDotsMoveBelowSecondaryLabel() {
        rule.setContent {
            Box(Modifier.background(Color.White)) {
                DefaultDay(
                    day = day,
                    modifier = Modifier.size(48.dp).testTag(CELL),
                    colors = testColors(),
                    info = DayInfo(label = longLabel, isHoliday = true),
                    decorators = listOf(DayDecorators.eventDots(maxDots = 1, dotSize = 4.dp) { listOf(Color.Red) }),
                    today = today,
                )
            }
        }
        assertColorNear(Color.Red, pixel(24.dp, 45.dp))
    }

    @Test
    fun rangeEndConnectorFadesOutWhenRangeIsCleared() {
        lateinit var selection: CalendarSelectionState
        rule.mainClock.autoAdvance = false
        rule.setContent {
            selection =
                rememberCalendarSelectionState(
                    mode = SelectionMode.Range(),
                    initialSelection = Selection(rangeStart = LocalDate(2026, 10, 10), rangeEnd = day.date),
                )
            Box(Modifier.background(Color.White)) {
                DefaultDay(
                    day = day,
                    modifier = Modifier.size(width = 96.dp, height = 48.dp).testTag(CELL),
                    selectionState = selection,
                    colors = testColors(),
                    today = today,
                )
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
        // Range end: only the half toward the range start is filled.
        assertColorNear(Color.Green, pixel(4.dp, 24.dp))
        assertColorNear(Color.White, pixel(92.dp, 24.dp))

        changeSelection { selection.clear() }
        val mid = pixel(4.dp, 24.dp)
        assertTrue("band should be mid-fade but was $mid", mid.red in 0.05f..0.95f)

        rule.mainClock.advanceTimeBy(1_000)
        assertColorNear(Color.White, pixel(4.dp, 24.dp))
    }

    @Test
    fun rangeStartConnectorFadesInWhenRangeCompletes() {
        lateinit var selection: CalendarSelectionState
        rule.mainClock.autoAdvance = false
        rule.setContent {
            selection =
                rememberCalendarSelectionState(
                    mode = SelectionMode.Range(),
                    initialSelection = Selection(rangeStart = day.date),
                )
            Box(Modifier.background(Color.White)) {
                DefaultDay(
                    day = day,
                    modifier = Modifier.size(width = 96.dp, height = 48.dp).testTag(CELL),
                    selectionState = selection,
                    colors = testColors(),
                    today = today,
                )
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
        assertColorNear(Color.White, pixel(92.dp, 24.dp))

        changeSelection { selection.set(Selection(rangeStart = day.date, rangeEnd = LocalDate(2026, 10, 18))) }
        val mid = pixel(92.dp, 24.dp)
        assertTrue("band should be mid-fade but was $mid", mid.red in 0.05f..0.95f)

        rule.mainClock.advanceTimeBy(1_000)
        // Range start: only the half toward the range end is filled.
        assertColorNear(Color.Green, pixel(92.dp, 24.dp))
        assertColorNear(Color.White, pixel(4.dp, 24.dp))
    }

    /** Applies [change], then advances the paused clock to roughly mid-way through the 200 ms band fade. */
    private fun changeSelection(change: () -> Unit) {
        rule.runOnIdle {
            change()
            Snapshot.sendApplyNotifications()
        }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(48)
    }

    @Composable
    private fun testColors(): CalendarDayColors =
        CalendarDefaults.dayColors(
            selectedContainerColor = Color.Blue,
            selectedContentColor = Color.White,
            inRangeContainerColor = Color.Green,
        )

    private companion object {
        const val CELL = "cell"
    }
}
