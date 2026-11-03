package me.kozakov.orrery.compose

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Velocity-aware settles and the spring/tween micro-animations of #101. */
@RunWith(AndroidJUnit4::class)
class SmoothAnimationsTest {
    @get:Rule
    val rule = createComposeRule()

    private fun scope(): CoroutineScope {
        lateinit var scope: CoroutineScope
        rule.setContent { scope = rememberCoroutineScope() }
        rule.waitForIdle()
        return scope
    }

    @Test
    fun collapsibleAnimateToCarriesReleaseVelocity() {
        val still = CollapsibleCalendarState(initialFraction = 0f, enableTwoWeekStop = false)
        val flung = CollapsibleCalendarState(initialFraction = 0f, enableTwoWeekStop = false)
        val scope = scope()
        rule.mainClock.autoAdvance = false
        rule.runOnIdle {
            scope.launch { still.animateTo(1f) }
            scope.launch { flung.animateTo(1f, initialVelocity = 8f) }
        }
        rule.mainClock.advanceTimeBy(64)
        assertTrue(
            "a flung settle should lead a still one (flung=${flung.expandFraction}, still=${still.expandFraction})",
            flung.expandFraction > still.expandFraction,
        )
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertEquals(1f, still.expandFraction)
        assertEquals(1f, flung.expandFraction)
    }

    @Test
    fun collapsibleAnimateToStaysInRangeWithHugeVelocity() {
        val state = CollapsibleCalendarState(initialFraction = 0.5f, enableTwoWeekStop = false)
        val scope = scope()
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { scope.launch { state.animateTo(1f, initialVelocity = 50f) } }
        repeat(60) {
            rule.mainClock.advanceTimeByFrame()
            assertTrue("fraction ${state.expandFraction} left 0..1", state.expandFraction in 0f..1f)
        }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertEquals(1f, state.expandFraction)
    }

    @Test
    fun collapsibleGeometryConvertsPixelVelocityPerSegment() {
        // 100px rows, 6 rows: Week..TwoWeeks spans 100..200px, TwoWeeks..Month 200..600px.
        val geometry = CollapsibleGeometry(dayPx = 100f, rows = 6, anchorRow = 2)
        val twoWeeks = CollapsibleCalendarAnchor.TwoWeeks.expandFraction
        assertEquals(1_000f * twoWeeks / 100f, geometry.fractionVelocity(0.1f, 1_000f), 1e-4f)
        assertEquals(1_000f * (1f - twoWeeks) / 400f, geometry.fractionVelocity(0.8f, 1_000f), 1e-4f)
        assertEquals(-1_000f * (1f - twoWeeks) / 400f, geometry.fractionVelocity(0.8f, -1_000f), 1e-4f)
        // A one-row month has no travel, so no velocity.
        assertEquals(0f, CollapsibleGeometry(dayPx = 100f, rows = 1, anchorRow = 0).fractionVelocity(0.5f, 1_000f))
    }

    @Test
    fun scheduleAnimateHourHeightToSpringsAndClamps() {
        lateinit var state: ScheduleState
        lateinit var scope: CoroutineScope
        rule.setContent {
            scope = rememberCoroutineScope()
            state = rememberScheduleState(firstVisibleDate = LocalDate(2026, 7, 15), initialHourHeight = 60.dp)
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { scope.launch { state.animateHourHeightTo(120.dp) } }
        rule.mainClock.advanceTimeBy(64)
        val mid = state.hourHeight
        assertTrue("hourHeight should be mid-spring but was $mid", mid > 60.dp && mid < 120.dp)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertEquals(120.dp, state.hourHeight)

        rule.runOnIdle { scope.launch { state.animateHourHeightTo(500.dp, animationSpec = tween(100)) } }
        rule.waitForIdle()
        assertEquals(state.maxHourHeight, state.hourHeight)

        rule.runOnIdle { scope.launch { state.animateHourHeightTo(1.dp, animationSpec = tween(100)) } }
        rule.waitForIdle()
        assertEquals(state.minHourHeight, state.hourHeight)
    }

    /** Heights of the week grid at one week, 64 ms after switching to two, and settled at two. */
    private fun weekCalendarHeights(animateHeight: Boolean): Triple<Dp, Dp, Dp> {
        var weeks by mutableIntStateOf(1)
        rule.setContent {
            MaterialTheme {
                WeekCalendar(
                    state =
                        rememberWeekCalendarState(
                            startDate = LocalDate(2026, 7, 1),
                            endDate = LocalDate(2026, 8, 31),
                            firstVisibleDate = LocalDate(2026, 7, 6),
                            firstDayOfWeek = DayOfWeek.MONDAY,
                        ),
                    modifier = Modifier.testTag("week"),
                    visibleWeeksCount = weeks,
                    animateHeight = animateHeight,
                    weekHeader = null,
                    dayContent = { Box(Modifier.fillMaxWidth().height(40.dp)) },
                )
            }
        }
        rule.waitForIdle()
        val one = rule.onNodeWithTag("week").getUnclippedBoundsInRoot().height
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { weeks = 2 }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(64)
        val mid = rule.onNodeWithTag("week").getUnclippedBoundsInRoot().height
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        val two = rule.onNodeWithTag("week").getUnclippedBoundsInRoot().height
        return Triple(one, mid, two)
    }

    @Test
    fun weekCalendarSpringsHeightWhenWeekCountChanges() {
        val (one, mid, two) = weekCalendarHeights(animateHeight = true)
        assertTrue("two rows ($two) should be taller than one ($one)", two > one)
        assertTrue("height should be mid-spring but was $mid (from $one to $two)", mid > one && mid < two)
    }

    @Test
    fun weekCalendarSnapsHeightWithoutAnimateHeight() {
        val (one, mid, two) = weekCalendarHeights(animateHeight = false)
        assertTrue("two rows ($two) should be taller than one ($one)", two > one)
        assertTrue("height should snap to $two but was $mid", abs((mid - two).value) < 0.5f)
    }

    private fun dropdownRotation(): Float =
        rule
            .onNode(SemanticsMatcher.keyIsDefined(DropdownRotationKey), useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[DropdownRotationKey]

    @Test
    fun navHeaderDropdownRotatesWithExpanded() {
        var expanded by mutableStateOf(false)
        rule.setContent {
            MaterialTheme {
                CalendarNavHeader(
                    state =
                        rememberCalendarState(
                            startMonth = YearMonth(2026, 7),
                            endMonth = YearMonth(2026, 9),
                            firstVisibleMonth = YearMonth(2026, 8),
                            firstDayOfWeek = DayOfWeek.MONDAY,
                        ),
                    onTitleClick = { expanded = !expanded },
                    expanded = expanded,
                )
            }
        }
        rule.waitForIdle()
        assertEquals(0f, dropdownRotation())

        rule.mainClock.autoAdvance = false
        rule.runOnIdle { expanded = true }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(100)
        val mid = dropdownRotation()
        assertTrue("triangle should be mid-rotation but was $mid", mid > 0f && mid < 180f)
        rule.mainClock.advanceTimeBy(300)
        assertEquals(180f, dropdownRotation())

        rule.mainClock.autoAdvance = true
        rule.runOnIdle { expanded = false }
        rule.waitForIdle()
        assertEquals(0f, dropdownRotation())
    }

    @Test
    fun navHeaderWithoutTitleClickHasNoDropdown() {
        rule.setContent {
            MaterialTheme {
                CalendarNavHeader(
                    state =
                        rememberCalendarState(
                            startMonth = YearMonth(2026, 7),
                            endMonth = YearMonth(2026, 9),
                            firstVisibleMonth = YearMonth(2026, 8),
                            firstDayOfWeek = DayOfWeek.MONDAY,
                        ),
                    expanded = true,
                )
            }
        }
        rule
            .onNode(SemanticsMatcher.keyIsDefined(DropdownRotationKey), useUnmergedTree = true)
            .assertDoesNotExist()
    }
}
