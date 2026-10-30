package me.kozakov.orrery.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.EventSpan
import me.kozakov.orrery.core.eventsByDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class ScheduleViewTest {
    @get:Rule val rule = createComposeRule()

    private val jul15 = LocalDate(2026, 7, 15)
    private val jul16 = LocalDate(2026, 7, 16)
    private val jul17 = LocalDate(2026, 7, 17)
    private val jul20 = LocalDate(2026, 7, 20)
    private val jul22 = LocalDate(2026, 7, 22)

    private fun timed(
        id: String,
        title: String,
        start: LocalDateTime,
        end: LocalDateTime,
    ): CalendarEvent<String> = CalendarEvent(id = id, title = title, span = EventSpan.Timed(start, end), payload = id)

    private val events =
        listOf(
            CalendarEvent(
                id = "offsite",
                title = "Offsite",
                span = EventSpan.AllDay(jul15, jul16),
                payload = "offsite",
            ),
            timed("standup", "Standup", LocalDateTime(2026, 7, 15, 9, 0), LocalDateTime(2026, 7, 15, 9, 30)),
            timed("review", "Design review", LocalDateTime(2026, 7, 15, 9, 15), LocalDateTime(2026, 7, 15, 10, 30)),
            timed("retro", "Retro", LocalDateTime(2026, 7, 16, 14, 0), LocalDateTime(2026, 7, 16, 15, 0)),
        ).eventsByDate(LocalDate(2026, 7, 1)..LocalDate(2026, 7, 31))

    private lateinit var state: ScheduleState
    private lateinit var scope: CoroutineScope

    private fun daySchedule(
        firstVisibleDate: LocalDate = jul15,
        initialScrollTime: LocalTime = LocalTime(8, 0),
        showHeader: Boolean = true,
        showHalfHourLines: Boolean = false,
        onEventClick: ((CalendarEvent<String>) -> Unit)? = null,
        onEmptySlotClick: ((LocalDate, LocalTime) -> Unit)? = null,
    ) {
        rule.setContent {
            scope = rememberCoroutineScope()
            state =
                rememberScheduleState(
                    firstVisibleDate = firstVisibleDate,
                    firstDayOfWeek = DayOfWeek.MONDAY,
                    initialScrollTime = initialScrollTime,
                )
            MaterialTheme {
                if (showHeader) {
                    DaySchedule(
                        events = { date -> events[date].orEmpty() },
                        modifier = Modifier.testTag("schedule").fillMaxWidth().height(400.dp),
                        state = state,
                        today = jul15,
                        showHalfHourLines = showHalfHourLines,
                        onEventClick = onEventClick,
                        onEmptySlotClick = onEmptySlotClick,
                    )
                } else {
                    DaySchedule(
                        events = { date -> events[date].orEmpty() },
                        modifier = Modifier.testTag("schedule").fillMaxWidth().height(400.dp),
                        state = state,
                        today = jul15,
                        showHalfHourLines = showHalfHourLines,
                        onEventClick = onEventClick,
                        onEmptySlotClick = onEmptySlotClick,
                        dayHeader = null,
                    )
                }
            }
        }
    }

    private fun weekSchedule() {
        rule.setContent {
            scope = rememberCoroutineScope()
            state = rememberScheduleState(firstVisibleDate = jul15, firstDayOfWeek = DayOfWeek.MONDAY)
            MaterialTheme {
                WeekSchedule(
                    events = { date -> events[date].orEmpty() },
                    modifier = Modifier.testTag("schedule").fillMaxWidth().height(400.dp),
                    state = state,
                    today = jul15,
                )
            }
        }
    }

    @Test
    fun rendersHourLabelsHeaderAllDayChipsAndTimedTiles() {
        daySchedule()
        for (hour in 0..23) {
            rule.onNodeWithText(hour.toString().padStart(2, '0') + ":00").assertExists()
        }
        rule.onNodeWithText("15").assertIsDisplayed()
        rule.onNodeWithText("All day").assertIsDisplayed()
        rule.onNodeWithText("Offsite").assertIsDisplayed()
        rule.onNodeWithText("Standup").assertIsDisplayed()
        rule.onNodeWithText("Design review").assertIsDisplayed()
        rule.onNodeWithText("09:15 – 10:30").assertExists()
        rule.onAllNodesWithText("Retro").assertCountEquals(0)
    }

    @Test
    fun onEventClickReportsTimedAndAllDayEvents() {
        var clicked: CalendarEvent<String>? = null
        daySchedule(onEventClick = { clicked = it })
        rule.onNodeWithText("Standup").performClick()
        rule.runOnIdle { assertEquals("standup", clicked?.payload) }
        rule.onNodeWithText("Offsite").performClick()
        rule.runOnIdle { assertEquals("offsite", clicked?.id) }
    }

    @Test
    fun onEmptySlotClickReportsDateAndHour() {
        var slot: Pair<LocalDate, LocalTime>? = null
        daySchedule(
            firstVisibleDate = jul17,
            initialScrollTime = LocalTime(10, 0),
            showHeader = false,
            onEmptySlotClick = { date, time -> slot = date to time },
        )
        rule.onNodeWithTag("schedule").performTouchInput {
            click(Offset(156.dp.toPx(), 45.dp.toPx()))
        }
        rule.runOnIdle { assertEquals(jul17 to LocalTime(10, 0), slot) }
    }

    @Test
    fun onEmptySlotClickSnapsToHalfHoursWithHalfHourLines() {
        var slot: Pair<LocalDate, LocalTime>? = null
        daySchedule(
            firstVisibleDate = jul17,
            initialScrollTime = LocalTime(10, 0),
            showHeader = false,
            showHalfHourLines = true,
            onEmptySlotClick = { date, time -> slot = date to time },
        )
        rule.onNodeWithTag("schedule").performTouchInput {
            click(Offset(156.dp.toPx(), 45.dp.toPx()))
        }
        rule.runOnIdle { assertEquals(jul17 to LocalTime(10, 30), slot) }
    }

    @Test
    fun initialScrollTimeAndScrollToTimePositionTheGrid() {
        daySchedule()
        val hourPx = with(rule.density) { 60.dp.toPx() }
        rule.runOnIdle { assertEquals((hourPx * 8).roundToInt(), state.verticalScrollState.value) }
        scope.launch { state.scrollToTime(LocalTime(14, 0), rule.density) }
        rule.waitForIdle()
        assertEquals((hourPx * 14).roundToInt(), state.verticalScrollState.value)
    }

    @Test
    fun nowIndicatorOnlyRendersForCurrentTimeOnVisibleToday() {
        var currentTime: LocalTime? by mutableStateOf(null)
        var today by mutableStateOf(jul15)
        rule.setContent {
            MaterialTheme {
                DaySchedule(
                    events = { date -> events[date].orEmpty() },
                    modifier = Modifier.fillMaxWidth().height(400.dp),
                    state = rememberScheduleState(firstVisibleDate = jul15),
                    today = today,
                    currentTime = currentTime,
                )
            }
        }
        rule.onNodeWithContentDescription("Now").assertDoesNotExist()
        currentTime = LocalTime(10, 15)
        rule.onNodeWithContentDescription("Now").assertIsDisplayed()
        today = jul20
        rule.onNodeWithContentDescription("Now").assertDoesNotExist()
    }

    @Test
    fun swipeMovesOneDay() {
        daySchedule()
        rule.onNodeWithTag("schedule").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(jul16, state.firstVisibleDate)
        rule.onNodeWithText("Retro").assertExists()
    }

    @Test
    fun scrollToDateShowsThatDay() {
        daySchedule()
        scope.launch { state.scrollToDate(jul16) }
        rule.waitForIdle()
        assertEquals(jul16, state.firstVisibleDate)
        rule.onNodeWithText("16").assertIsDisplayed()
        rule.onNodeWithText("Retro").assertExists()
    }

    @Test
    fun weekScheduleShowsTheWeekWithASpanningAllDayBanner() {
        weekSchedule()
        for (day in 13..19) {
            rule.onNodeWithText(day.toString()).assertIsDisplayed()
        }
        rule.onAllNodesWithText("Offsite").assertCountEquals(1)
        rule.onNodeWithText("Standup").assertExists()
        rule.onNodeWithText("Retro").assertExists()
    }

    @Test
    fun weekScheduleScrollToDateKeepsTheRequestedDate() {
        weekSchedule()
        scope.launch { state.scrollToDate(jul22) }
        rule.waitForIdle()
        assertEquals(jul22, state.firstVisibleDate)
        for (day in 20..26) {
            rule.onNodeWithText(day.toString()).assertIsDisplayed()
        }
    }

    @Test
    fun weekScheduleSwipeMovesAWholeWeek() {
        weekSchedule()
        rule.onNodeWithTag("schedule").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(jul20, state.firstVisibleDate)
        rule.onNodeWithText("26").assertIsDisplayed()
    }

    @Test
    fun hourHeightIsClampedToTheZoomRange() {
        daySchedule()
        rule.runOnIdle {
            state.hourHeight = 500.dp
            assertEquals(160.dp, state.hourHeight)
            state.hourHeight = 1.dp
            assertEquals(36.dp, state.hourHeight)
        }
    }

    @Test
    fun pinchOutZoomsIn() {
        daySchedule()
        rule.onNodeWithTag("schedule").performTouchInput {
            pinch(
                start0 = center - Offset(0f, 20.dp.toPx()),
                end0 = center - Offset(0f, 120.dp.toPx()),
                start1 = center + Offset(0f, 20.dp.toPx()),
                end1 = center + Offset(0f, 120.dp.toPx()),
            )
        }
        rule.runOnIdle { assertTrue("hourHeight = ${state.hourHeight}", state.hourHeight > 60.dp) }
    }

    @Test
    fun saverRestoresDateHourHeightAndScrollOffset() {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            scope = rememberCoroutineScope()
            state = rememberScheduleState(firstVisibleDate = jul15, firstDayOfWeek = DayOfWeek.MONDAY)
            MaterialTheme {
                DaySchedule(
                    events = { date -> events[date].orEmpty() },
                    modifier = Modifier.fillMaxWidth().height(400.dp),
                    state = state,
                    today = jul15,
                )
            }
        }
        rule.runOnIdle { state.hourHeight = 90.dp }
        scope.launch {
            state.scrollToDate(jul16)
            state.verticalScrollState.scrollTo(500)
        }
        rule.waitForIdle()
        tester.emulateSavedInstanceStateRestore()
        rule.runOnIdle {
            assertEquals(jul16, state.firstVisibleDate)
            assertEquals(90.dp, state.hourHeight)
            assertEquals(500, state.verticalScrollState.value)
        }
    }
}
