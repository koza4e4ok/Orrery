package me.kozakov.orrery.compose

import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import me.kozakov.orrery.core.CalendarDay
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.DayPosition
import me.kozakov.orrery.core.EventSpan
import me.kozakov.orrery.core.eventsByDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AgendaTest {
    @get:Rule val rule = createComposeRule()

    private val jul15 = LocalDate(2026, 7, 15)
    private val jul16 = LocalDate(2026, 7, 16)
    private val jul17 = LocalDate(2026, 7, 17)
    private val jul18 = LocalDate(2026, 7, 18)

    private val sampleEvents =
        listOf(
            CalendarEvent(
                id = "all-day-1",
                title = "Company Summit",
                span = EventSpan.AllDay(jul15),
                subtitle = "Main Hall",
                colorArgb = 0xFF64B5F6,
                payload = "summit-payload",
            ),
            CalendarEvent(
                id = "timed-1",
                title = "Morning Standup",
                span =
                    EventSpan.Timed(
                        start = LocalDateTime(2026, 7, 15, 9, 0),
                        end = LocalDateTime(2026, 7, 15, 9, 30),
                    ),
                payload = "standup-payload",
            ),
            CalendarEvent(
                id = "overnight-1",
                title = "Night Maintenance",
                span =
                    EventSpan.Timed(
                        start = LocalDateTime(2026, 7, 15, 22, 0),
                        end = LocalDateTime(2026, 7, 16, 2, 0),
                    ),
                payload = "overnight-payload",
            ),
            CalendarEvent(
                id = "timed-2",
                title = "Sprint Retro",
                span =
                    EventSpan.Timed(
                        start = LocalDateTime(2026, 7, 18, 14, 0),
                        end = LocalDateTime(2026, 7, 18, 15, 0),
                    ),
                payload = "retro-payload",
            ),
        )

    @Test
    fun rendersDayHeadersAllDayTimedAndClampedOvernightRows() {
        val indexed = sampleEvents.eventsByDate(jul15..jul18)
        rule.setContent {
            MaterialTheme {
                Agenda(
                    events = { date -> indexed[date].orEmpty() },
                    state =
                        rememberAgendaState(
                            startDate = jul15,
                            endDate = jul18,
                            initialVisibleDate = jul15,
                        ),
                    today = jul15,
                )
            }
        }

        rule.onNodeWithText("TODAY · ${jul15.formatDayHeader()}").assertIsDisplayed()
        rule.onNodeWithText(jul16.formatDayHeader().uppercase()).assertIsDisplayed()
        rule.onNodeWithText("All day").assertIsDisplayed()
        rule.onNodeWithText("Company Summit").assertIsDisplayed()
        rule.onNodeWithText("Main Hall").assertIsDisplayed()
        rule.onNodeWithText("09:00 – 09:30").assertIsDisplayed()
        rule.onNodeWithText("Morning Standup").assertIsDisplayed()
        // Overnight event shows 22:00 – 24:00 on day 1 and 00:00 – 02:00 on day 2.
        rule.onNodeWithText("22:00 – 24:00").assertIsDisplayed()
        rule.onNodeWithText("00:00 – 02:00").assertIsDisplayed()
        // Empty jul17 is omitted when showEmptyDays = false.
        rule.onAllNodesWithText(jul17.formatDayHeader().uppercase()).assertCountEquals(0)
    }

    @Test
    fun showEmptyDaysTrueRendersEmptyDayPlaceholders() {
        val indexed = sampleEvents.eventsByDate(jul15..jul17)
        rule.setContent {
            MaterialTheme {
                Agenda(
                    events = { date -> indexed[date].orEmpty() },
                    state =
                        rememberAgendaState(
                            startDate = jul15,
                            endDate = jul17,
                            initialVisibleDate = jul15,
                        ),
                    today = jul15,
                    showEmptyDays = true,
                )
            }
        }

        rule.onNodeWithText(jul17.formatDayHeader().uppercase()).assertIsDisplayed()
        rule.onNodeWithText("No events").assertIsDisplayed()
    }

    @Test
    fun emptyRangeRendersEmptyRangeContentWhenShowEmptyDaysFalse() {
        rule.setContent {
            MaterialTheme {
                Agenda<Nothing>(
                    events = { emptyList() },
                    state =
                        rememberAgendaState(
                            startDate = jul15,
                            endDate = jul18,
                            initialVisibleDate = jul15,
                        ),
                    today = jul15,
                    emptyRangeContent = { Text("Nothing scheduled in this range") },
                )
            }
        }

        rule.onNodeWithText("Nothing scheduled in this range").assertIsDisplayed()
    }

    @Test
    fun onEventClickFiresWithClickedCalendarEvent() {
        val indexed = sampleEvents.eventsByDate(jul15..jul18)
        var clicked: CalendarEvent<String>? = null
        rule.setContent {
            MaterialTheme {
                Agenda(
                    events = { date -> indexed[date].orEmpty() },
                    state =
                        rememberAgendaState(
                            startDate = jul15,
                            endDate = jul18,
                            initialVisibleDate = jul15,
                        ),
                    today = jul15,
                    onEventClick = { clicked = it },
                )
            }
        }

        rule.onNodeWithText("Morning Standup").performClick()
        rule.runOnIdle {
            assertEquals("timed-1", clicked?.id)
            assertEquals("standup-payload", clicked?.payload)
        }
    }

    @Test
    fun scrollToDateAndAnimateScrollToDateUpdateFirstVisibleDate() {
        val start = LocalDate(2026, 7, 1)
        val end = LocalDate(2026, 7, 31)
        val manyEvents =
            (1..31 step 2).map { day ->
                val date = LocalDate(2026, 7, day)
                CalendarEvent<Nothing>(
                    id = "ev-$day",
                    title = "Event $day",
                    span = EventSpan.AllDay(date),
                )
            }
        val indexed = manyEvents.eventsByDate(start..end)
        lateinit var state: AgendaState
        lateinit var scope: CoroutineScope

        rule.setContent {
            MaterialTheme {
                scope = rememberCoroutineScope()
                state =
                    rememberAgendaState(
                        startDate = start,
                        endDate = end,
                        initialVisibleDate = start,
                    )
                Agenda(
                    events = { date -> indexed[date].orEmpty() },
                    state = state,
                    modifier = Modifier.height(160.dp),
                    today = jul15,
                )
            }
        }

        rule.runOnIdle { assertEquals(start, state.firstVisibleDate) }

        // Jul 14 has no events; scrolling to Jul 14 in showEmptyDays=false mode jumps to Jul 15.
        scope.launch { state.scrollToDate(LocalDate(2026, 7, 14)) }
        rule.waitForIdle()
        assertEquals(jul15, state.firstVisibleDate)

        scope.launch { state.animateScrollToDate(LocalDate(2026, 7, 25)) }
        rule.waitForIdle()
        assertEquals(LocalDate(2026, 7, 25), state.firstVisibleDate)
    }

    @Test
    fun stateSurvivesRestoration() {
        val restorationTester = StateRestorationTester(rule)
        val indexed = sampleEvents.eventsByDate(jul15..jul18)
        lateinit var state: AgendaState
        lateinit var scope: CoroutineScope

        restorationTester.setContent {
            MaterialTheme {
                scope = rememberCoroutineScope()
                state =
                    rememberAgendaState(
                        startDate = jul15,
                        endDate = jul18,
                        initialVisibleDate = jul15,
                    )
                Agenda(
                    events = { date -> indexed[date].orEmpty() },
                    state = state,
                    today = jul15,
                )
            }
        }

        scope.launch { state.scrollToDate(jul18) }
        rule.waitForIdle()
        assertEquals(jul18, state.firstVisibleDate)

        restorationTester.emulateSavedInstanceStateRestore()
        rule.runOnIdle {
            assertEquals(jul15, state.startDate)
            assertEquals(jul18, state.endDate)
            assertEquals(jul18, state.firstVisibleDate)
        }
    }

    @Test
    fun eventDotsForEventsDrawsOnDefaultDay() {
        val indexed = sampleEvents.eventsByDate(jul15..jul18)
        lateinit var decorator: DayDecorator
        rule.setContent {
            MaterialTheme {
                decorator = DayDecorators.eventDotsForEvents(eventsByDate = { indexed[it].orEmpty() })
                DefaultDay(
                    day = CalendarDay(jul15, DayPosition.MonthDate),
                    today = jul15,
                    decorators = listOf(decorator),
                )
            }
        }
        rule.runOnIdle {
            assertEquals(DecoratorLayer.Behind, decorator.layer)
        }
    }
}
