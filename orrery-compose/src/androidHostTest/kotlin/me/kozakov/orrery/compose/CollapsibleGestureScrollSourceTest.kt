package me.kozakov.orrery.compose

import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
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

@RunWith(AndroidJUnit4::class)
class CollapsibleGestureScrollSourceTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var collapsible: CollapsibleCalendarState
    private lateinit var calendarState: CalendarState
    private lateinit var weekState: WeekCalendarState
    private lateinit var scope: CoroutineScope

    private fun setScaffold(
        enableTwoWeekStop: Boolean = true,
        enableDirectDrag: Boolean = true,
        withHandle: Boolean = false,
        firstVisibleDate: LocalDate = LocalDate(2026, 7, 9),
    ) {
        rule.setContent {
            scope = rememberCoroutineScope()
            collapsible =
                rememberCollapsibleCalendarState(
                    initialAnchor = CollapsibleCalendarAnchor.Month,
                    enableTwoWeekStop = enableTwoWeekStop,
                )
            calendarState =
                rememberCalendarState(
                    startMonth = YearMonth(2026, 6),
                    endMonth = YearMonth(2026, 9),
                    firstVisibleMonth = YearMonth(2026, 7),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                )
            weekState =
                rememberWeekCalendarState(
                    startDate = LocalDate(2026, 6, 1),
                    endDate = LocalDate(2026, 9, 30),
                    firstVisibleDate = firstVisibleDate,
                    firstDayOfWeek = DayOfWeek.MONDAY,
                )
            CollapsibleCalendarScaffold(
                calendarState = calendarState,
                weekState = weekState,
                state = collapsible,
                enableDirectDrag = enableDirectDrag,
                dragHandle =
                    if (withHandle) {
                        { CalendarDefaults.CollapsibleDragHandle(Modifier.testTag("handle")) }
                    } else {
                        null
                    },
                // Rows match the scaffold's dayHeight (48.dp), as with DefaultDay's Comfortable density.
                dayContent = { day -> Text(day.date.toString(), Modifier.height(48.dp).testTag("d-${day.date}")) },
            ) { Text("content") } // not scrollable: no nested scroll reaches the scaffold
        }
        rule.waitForIdle()
    }

    /** Fast vertical swipe of [dy] dp (negative = up) starting on the node tagged [tag]. */
    private fun fling(
        tag: String,
        dy: Int,
    ) {
        rule.onNodeWithTag(tag).performTouchInput {
            swipe(center, center + Offset(0f, dy.dp.toPx()), durationMillis = 100)
        }
        rule.waitForIdle()
    }

    @Test
    fun directDragSnapsThroughMonthTwoWeeksAndWeek() {
        setScaffold()
        assertEquals(CollapsibleCalendarAnchor.Month, collapsible.currentAnchor)

        fling("d-2026-07-08", dy = -60)
        assertEquals(CollapsibleCalendarAnchor.TwoWeeks, collapsible.currentAnchor)
        assertEquals(CollapsibleCalendarAnchor.TwoWeeks.expandFraction, collapsible.expandFraction)
        rule.onNodeWithTag("d-2026-07-08").assertIsDisplayed() // anchor week row stays in view
        rule.onNodeWithTag("d-2026-07-15").assertIsDisplayed() // plus the next row

        fling("d-2026-07-08", dy = -40)
        assertEquals(CollapsibleCalendarAnchor.Week, collapsible.currentAnchor)
        assertEquals(0f, collapsible.expandFraction)
        rule.onNodeWithTag("d-2026-07-27").assertDoesNotExist() // week strip replaced the month

        fling("d-2026-07-08", dy = 40)
        assertEquals(CollapsibleCalendarAnchor.TwoWeeks, collapsible.currentAnchor)

        fling("d-2026-07-08", dy = 60)
        assertEquals(CollapsibleCalendarAnchor.Month, collapsible.currentAnchor)
        assertEquals(1f, collapsible.expandFraction)
        rule.onNodeWithTag("d-2026-07-27").assertIsDisplayed()
    }

    @Test
    fun slowShortDragFollowsTheFingerThenSettlesOnTheNearestStop() {
        setScaffold()
        rule.onNodeWithTag("d-2026-07-08").performTouchInput {
            down(center)
            repeat(10) {
                advanceEventTime(16)
                moveBy(Offset(0f, -4.dp.toPx()))
            }
        }
        rule.waitForIdle()
        assertTrue(collapsible.expandFraction < 1f) // follows the finger
        rule.onNodeWithTag("d-2026-07-08").performTouchInput {
            advanceEventTime(300) // stop moving so the release carries no velocity
            up()
        }
        rule.waitForIdle()
        assertEquals(CollapsibleCalendarAnchor.Month, collapsible.currentAnchor)
        assertEquals(1f, collapsible.expandFraction)
    }

    @Test
    fun dragHandleCollapsesAndExpandsWithoutTwoWeekStop() {
        setScaffold(enableTwoWeekStop = false, withHandle = true)
        fling("handle", dy = -60)
        assertEquals(CollapsibleCalendarAnchor.Week, collapsible.currentAnchor)
        assertEquals(0f, collapsible.expandFraction)
        fling("handle", dy = 60)
        assertEquals(CollapsibleCalendarAnchor.Month, collapsible.currentAnchor)
        assertEquals(1f, collapsible.expandFraction)
    }

    @Test
    fun disabledDirectDragIgnoresVerticalSwipes() {
        setScaffold(enableDirectDrag = false)
        fling("d-2026-07-08", dy = -60)
        assertEquals(1f, collapsible.expandFraction)
    }

    @Test
    fun pagingTheCollapsedWeekStripMovesTheMonthAlong() {
        setScaffold(firstVisibleDate = LocalDate(2026, 7, 29))
        scope.launch { collapsible.collapse() }
        rule.waitForIdle()
        assertEquals(0f, collapsible.expandFraction)

        rule.onNodeWithTag("d-2026-07-29").performTouchInput {
            swipe(center, center + Offset(-150.dp.toPx(), 0f), durationMillis = 300)
        }
        rule.waitForIdle()
        val firstWeek = weekState.firstVisibleWeek
        val weekStart = firstWeek.days.first().date
        assertTrue("week strip paged forward, at $weekStart", weekStart >= LocalDate(2026, 8, 3))
        assertEquals(YearMonth(2026, 8), calendarState.firstVisibleMonth)
        assertEquals(ScrollSource.UserDrag, weekState.lastScrollSource)
        assertEquals(ScrollSource.Programmatic, calendarState.lastScrollSource)

        scope.launch { collapsible.expand() }
        rule.waitForIdle()
        rule.onNodeWithTag("d-2026-08-20").assertIsDisplayed()
    }

    @Test
    fun calendarStateReportsProgrammaticThenUserDrag() {
        val changes = mutableListOf<VisibleMonthChange>()
        rule.setContent {
            scope = rememberCoroutineScope()
            calendarState =
                rememberCalendarState(
                    startMonth = YearMonth(2026, 7),
                    endMonth = YearMonth(2026, 9),
                    firstVisibleMonth = YearMonth(2026, 7),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                )
            LaunchedEffect(calendarState) { calendarState.visibleMonthChanges().collect { changes.add(it) } }
            HorizontalCalendar(
                state = calendarState,
                modifier = Modifier.testTag("calendar"),
                dayContent = { day -> Text(day.date.day.toString()) },
            )
        }
        rule.waitForIdle()
        assertEquals(ScrollSource.Initial, calendarState.lastScrollSource)

        rule.runOnIdle { scope.launch { calendarState.scrollToMonth(YearMonth(2026, 8)) } }
        rule.waitForIdle()
        assertEquals(ScrollSource.Programmatic, calendarState.lastScrollSource)

        rule.onNodeWithTag("calendar").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(YearMonth(2026, 9), calendarState.firstVisibleMonth)
        assertEquals(ScrollSource.UserDrag, calendarState.lastScrollSource)
        assertEquals(
            listOf(
                VisibleMonthChange(YearMonth(2026, 7), ScrollSource.Initial),
                VisibleMonthChange(YearMonth(2026, 8), ScrollSource.Programmatic),
                VisibleMonthChange(YearMonth(2026, 9), ScrollSource.UserDrag),
            ),
            changes,
        )
    }

    @Test
    fun weekCalendarStateReportsProgrammaticThenUserDrag() {
        val changes = mutableListOf<VisibleWeekChange>()
        rule.setContent {
            scope = rememberCoroutineScope()
            weekState =
                rememberWeekCalendarState(
                    startDate = LocalDate(2026, 7, 6),
                    endDate = LocalDate(2026, 7, 26),
                    firstVisibleDate = LocalDate(2026, 7, 6),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                )
            LaunchedEffect(weekState) { weekState.visibleWeekChanges().collect { changes.add(it) } }
            WeekCalendar(
                state = weekState,
                modifier = Modifier.width(350.dp).testTag("week"),
                dayContent = { day -> Text(day.date.day.toString()) },
            )
        }
        rule.waitForIdle()
        assertEquals(ScrollSource.Initial, weekState.lastScrollSource)

        rule.runOnIdle { scope.launch { weekState.scrollToDate(LocalDate(2026, 7, 15)) } }
        rule.waitForIdle()
        assertEquals(ScrollSource.Programmatic, weekState.lastScrollSource)

        rule.onNodeWithTag("week").performTouchInput {
            swipe(center, center + Offset(-150.dp.toPx(), 0f), durationMillis = 300)
        }
        rule.waitForIdle()
        assertEquals(ScrollSource.UserDrag, weekState.lastScrollSource)
        assertEquals(
            listOf(
                LocalDate(2026, 7, 6) to ScrollSource.Initial,
                LocalDate(2026, 7, 13) to ScrollSource.Programmatic,
                LocalDate(2026, 7, 20) to ScrollSource.UserDrag,
            ),
            changes.map { change ->
                val firstDay = change.week.days.first()
                firstDay.date to change.source
            },
        )
    }

    @Test
    fun yearCalendarStateReportsProgrammaticThenUserDrag() {
        lateinit var yearState: YearCalendarState
        rule.setContent {
            scope = rememberCoroutineScope()
            yearState = rememberYearCalendarState(startYear = 2025, endYear = 2027, firstVisibleYear = 2025)
            YearCalendar(state = yearState, modifier = Modifier.testTag("year"), onMonthClick = {})
        }
        rule.waitForIdle()
        assertEquals(ScrollSource.Initial, yearState.lastScrollSource)

        rule.runOnIdle { scope.launch { yearState.animateScrollToYear(2026) } }
        rule.waitForIdle()
        assertEquals(2026, yearState.firstVisibleYear)
        assertEquals(ScrollSource.Programmatic, yearState.lastScrollSource)

        rule.onNodeWithTag("year").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(2027, yearState.firstVisibleYear)
        assertEquals(ScrollSource.UserDrag, yearState.lastScrollSource)
    }
}
