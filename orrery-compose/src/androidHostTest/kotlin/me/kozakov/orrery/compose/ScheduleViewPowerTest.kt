package me.kozakov.orrery.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.EventOverlapMode
import me.kozakov.orrery.core.EventSpan
import me.kozakov.orrery.core.eventsByDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.roundToInt

/** #85: visible hour window, overlap modes, spanning all-day banners and scroll helpers. */
@RunWith(AndroidJUnit4::class)
class ScheduleViewPowerTest {
    @get:Rule val rule = createComposeRule()

    private val jul15 = LocalDate(2026, 7, 15)
    private val jul17 = LocalDate(2026, 7, 17)

    private fun timed(
        id: String,
        day: Int,
        start: LocalTime,
        end: LocalTime,
    ): CalendarEvent<Nothing> =
        CalendarEvent(
            id = id,
            title = id,
            span =
                EventSpan.Timed(
                    start = LocalDateTime(LocalDate(2026, 7, day), start),
                    end = LocalDateTime(LocalDate(2026, 7, day), end),
                ),
        )

    private fun allDay(
        id: String,
        startDay: Int,
        endDay: Int = startDay,
    ): CalendarEvent<Nothing> =
        CalendarEvent(
            id = id,
            title = id,
            span = EventSpan.AllDay(LocalDate(2026, 7, startDay), LocalDate(2026, 7, endDay)),
        )

    private val events =
        listOf(
            allDay("Offsite", 15, 16),
            allDay("Release", 17),
            timed("Early", 15, LocalTime(6, 0), LocalTime(7, 0)),
            timed("Standup", 15, LocalTime(9, 0), LocalTime(9, 30)),
            timed("Review", 15, LocalTime(9, 15), LocalTime(10, 30)),
            timed("Late", 15, LocalTime(19, 0), LocalTime(20, 0)),
        ).eventsByDate(LocalDate(2026, 7, 1)..LocalDate(2026, 7, 31))

    private lateinit var state: ScheduleState
    private lateinit var scope: CoroutineScope

    private fun daySchedule(
        minHour: Int = 0,
        maxHour: Int = 24,
        overlapMode: EventOverlapMode = EventOverlapMode.SideBySide,
        initialScrollTime: LocalTime = LocalTime(8, 0),
        events: Map<LocalDate, List<CalendarEvent<Nothing>>> = this.events,
        onEmptySlotClick: ((LocalDate, LocalTime) -> Unit)? = null,
    ) {
        rule.setContent {
            scope = rememberCoroutineScope()
            state =
                rememberScheduleState(
                    firstVisibleDate = jul15,
                    firstDayOfWeek = DayOfWeek.MONDAY,
                    initialScrollTime = initialScrollTime,
                    minHour = minHour,
                    maxHour = maxHour,
                )
            MaterialTheme {
                DaySchedule(
                    events = { date -> events[date].orEmpty() },
                    modifier = Modifier.testTag("schedule").fillMaxWidth().height(400.dp),
                    state = state,
                    today = jul15,
                    overlapMode = overlapMode,
                    onEmptySlotClick = onEmptySlotClick,
                    dayHeader = null,
                    eventContent = { positioned -> Box(Modifier.fillMaxSize().testTag("tile-" + positioned.event.id)) },
                )
            }
        }
    }

    private fun weekSchedule(
        events: Map<LocalDate, List<CalendarEvent<Nothing>>> = this.events,
        maxVisibleAllDayRows: Int = 3,
        zoomGesturesEnabled: Boolean = true,
    ) {
        rule.setContent {
            scope = rememberCoroutineScope()
            state = rememberScheduleState(firstVisibleDate = jul15, firstDayOfWeek = DayOfWeek.MONDAY)
            MaterialTheme {
                WeekSchedule(
                    events = { date -> events[date].orEmpty() },
                    modifier = Modifier.testTag("schedule").fillMaxWidth().height(400.dp),
                    state = state,
                    today = jul15,
                    zoomGesturesEnabled = zoomGesturesEnabled,
                    overlapMode = EventOverlapMode.SideBySide,
                    dimensions = ScheduleDefaults.dimensions(maxVisibleAllDayRows = maxVisibleAllDayRows),
                    allDayEventContent = { event -> Box(Modifier.fillMaxSize().testTag("banner-" + event.id)) },
                )
            }
        }
    }

    private fun bounds(tag: String): DpRect = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun assertDpEquals(
        expected: Dp,
        actual: Dp,
        tolerance: Dp = 1.dp,
    ) {
        assertTrue("expected $expected but was $actual", abs((expected - actual).value) <= tolerance.value)
    }

    @Test
    fun boundedHoursOnlyLabelTheVisibleHours() {
        daySchedule(minHour = 8, maxHour = 18)
        for (hour in 8..17) {
            rule.onNodeWithText(hour.toString().padStart(2, '0') + ":00").assertExists()
        }
        rule.onNodeWithText("07:00").assertDoesNotExist()
        rule.onNodeWithText("18:00").assertDoesNotExist()
        rule.onNodeWithText("00:00").assertDoesNotExist()
    }

    @Test
    fun boundedHoursDropEventsOutsideTheWindow() {
        daySchedule(minHour = 8, maxHour = 18)
        rule.onNodeWithTag("tile-Early").assertDoesNotExist()
        rule.onNodeWithTag("tile-Late").assertDoesNotExist()
        rule.onNodeWithTag("tile-Standup").assertExists()
        // The 09:00 tile starts on the 09:00 hour line (the label is centered on it).
        val label = rule.onNodeWithText("09:00").getUnclippedBoundsInRoot()
        assertDpEquals((label.top + label.bottom) / 2, bounds("tile-Standup").top)
        // The grid is only 10 hours (08:00..18:00) tall.
        rule.runOnIdle {
            val gridPx = with(rule.density) { (60.dp * 10).toPx() }.roundToInt()
            assertEquals(gridPx - state.verticalScrollState.viewportSize, state.verticalScrollState.maxValue)
        }
    }

    @Test
    fun boundedHoursClampTheInitialScrollAndOffsetEmptySlotTaps() {
        var slot: Pair<LocalDate, LocalTime>? = null
        daySchedule(
            minHour = 8,
            maxHour = 18,
            initialScrollTime = LocalTime(6, 0),
            events = emptyMap(),
            onEmptySlotClick = { date, time -> slot = date to time },
        )
        rule.runOnIdle { assertEquals(0, state.verticalScrollState.value) }
        rule.onNodeWithTag("schedule").performTouchInput { click(Offset(156.dp.toPx(), 45.dp.toPx())) }
        rule.runOnIdle { assertEquals(jul15 to LocalTime(8, 0), slot) }
    }

    @Test
    fun scrollToHourIsRelativeToMinHourAndClamped() {
        daySchedule(minHour = 8, maxHour = 18)
        val hourPx = with(rule.density) { 60.dp.toPx() }
        scope.launch { state.scrollToHour(10, minute = 30, density = rule.density) }
        rule.waitForIdle()
        assertEquals((hourPx * 2.5f).roundToInt(), state.verticalScrollState.value)
        scope.launch { state.scrollToHour(6, density = rule.density) }
        rule.waitForIdle()
        assertEquals(0, state.verticalScrollState.value)
        scope.launch { state.animateScrollToHour(9, density = rule.density) }
        rule.waitForIdle()
        assertEquals(hourPx.roundToInt(), state.verticalScrollState.value)
    }

    @Test
    fun scrollToTimeUsesTheVisibleWindow() {
        daySchedule(minHour = 8, maxHour = 18)
        val hourPx = with(rule.density) { 60.dp.toPx() }
        scope.launch { state.scrollToTime(LocalTime(11, 0), rule.density) }
        rule.waitForIdle()
        assertEquals((hourPx * 3).roundToInt(), state.verticalScrollState.value)
    }

    @Test
    fun scrollToCurrentTimeKeepsAQuarterOfTheViewportAbove() {
        daySchedule()
        val hourPx = with(rule.density) { 60.dp.toPx() }
        scope.launch { state.scrollToCurrentTime(LocalTime(14, 0), rule.density) }
        rule.waitForIdle()
        val viewport = state.verticalScrollState.viewportSize
        assertTrue(viewport > 0)
        assertEquals((hourPx * 14).roundToInt() - (viewport * 0.25f).roundToInt(), state.verticalScrollState.value)
        scope.launch { state.scrollToCurrentTime(LocalTime(14, 0), rule.density, verticalBiasFraction = 0f) }
        rule.waitForIdle()
        assertEquals((hourPx * 14).roundToInt(), state.verticalScrollState.value)
    }

    @Test
    fun sideBySideSplitsOverlappingTiles() {
        daySchedule()
        val standup = bounds("tile-Standup")
        val review = bounds("tile-Review")
        assertDpEquals(standup.width, review.width)
        assertTrue("review should start right of standup", review.left >= standup.right)
    }

    @Test
    fun stackedCascadesOverlappingTilesToTheRightEdge() {
        daySchedule(overlapMode = EventOverlapMode.Stacked(laneIndentFraction = 0.2f))
        val standup = bounds("tile-Standup")
        val review = bounds("tile-Review")
        // Both reach the column's right edge; the second lane is indented by 20% of the column.
        assertDpEquals(standup.right, review.right)
        val column = standup.width + 2.dp
        assertDpEquals(standup.left + column * 0.2f, review.left)
        assertTrue("review overlaps standup", review.left < standup.right)
    }

    @Test
    fun weekScheduleSpansMultiDayAllDayBannersAcrossColumns() {
        weekSchedule()
        val offsite = bounds("banner-Offsite")
        val release = bounds("banner-Release")
        // Offsite (Wed-Thu) spans two columns; Release (Fri) one. Each banner is inset by 1.dp per side.
        assertDpEquals(release.width * 2 + 2.dp, offsite.width)
        assertDpEquals(offsite.top, release.top)
        assertDpEquals(offsite.right + 2.dp, release.left)
    }

    @Test
    fun overlappingBannersTakeSeparateLanesAndOverflowCountsPerColumn() {
        val crowded =
            listOf(
                allDay("Trip", 14, 17),
                allDay("Offsite", 15, 16),
                allDay("Workshop", 16),
                allDay("Holiday", 13),
            ).eventsByDate(LocalDate(2026, 7, 1)..LocalDate(2026, 7, 31))
        weekSchedule(events = crowded, maxVisibleAllDayRows = 2)
        // Trip and Holiday share lane 0; Offsite takes lane 1, which is replaced by the "+N" row.
        assertDpEquals(bounds("banner-Trip").top, bounds("banner-Holiday").top)
        rule.onNodeWithTag("banner-Offsite").assertDoesNotExist()
        rule.onNodeWithTag("banner-Workshop").assertDoesNotExist()
        rule.onNodeWithText("+1").assertExists()
        rule.onNodeWithText("+2").assertExists()
    }

    @Test
    fun pinchIsIgnoredWhenZoomGesturesAreDisabled() {
        weekSchedule(zoomGesturesEnabled = false)
        rule.onNodeWithTag("schedule").performTouchInput {
            pinch(
                start0 = center - Offset(0f, 20.dp.toPx()),
                end0 = center - Offset(0f, 120.dp.toPx()),
                start1 = center + Offset(0f, 20.dp.toPx()),
                end1 = center + Offset(0f, 120.dp.toPx()),
            )
        }
        rule.runOnIdle { assertEquals(60.dp, state.hourHeight) }
    }

    @Test
    fun saverRestoresTheHourWindow() {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            state = rememberScheduleState(firstVisibleDate = jul17, minHour = 7, maxHour = 19)
        }
        tester.emulateSavedInstanceStateRestore()
        rule.runOnIdle {
            assertEquals(7, state.minHour)
            assertEquals(19, state.maxHour)
            assertEquals(jul17, state.firstVisibleDate)
        }
    }
}
