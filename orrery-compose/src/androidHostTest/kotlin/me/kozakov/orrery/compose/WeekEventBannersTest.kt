package me.kozakov.orrery.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.EventSpan
import me.kozakov.orrery.core.SelectionMode
import me.kozakov.orrery.core.packWeekEventBanners
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeekEventBannersTest {
    @get:Rule
    val rule = createComposeRule()

    private fun jul(day: Int) = LocalDate(2026, 7, day)

    private fun event(
        id: String,
        start: LocalDate,
        end: LocalDate = start,
    ) = CalendarEvent<String>(id = id, title = id, span = EventSpan.AllDay(start, end), payload = "payload-$id")

    private val events =
        listOf(
            // Mon-first July 2026 rows: Jun 29-Jul 5, Jul 6-12, 13-19, 20-26, Jul 27-Aug 2.
            event("Summer trip", jul(9), jul(15)),
            event("Conference", jul(7), jul(8)),
            event("Sprint", jul(20), jul(24)),
            event("Offsite", jul(22), jul(23)),
            event("Demo", jul(22)),
            event("Retro", jul(22)),
        )

    @Composable
    private fun julyState(): CalendarState =
        rememberCalendarState(
            startMonth = YearMonth(2026, 7),
            endMonth = YearMonth(2026, 7),
            firstVisibleMonth = YearMonth(2026, 7),
            firstDayOfWeek = DayOfWeek.MONDAY,
        )

    @Test
    fun horizontalCalendarDrawsBannersPerWeekWithOverflow() {
        val clicked = mutableListOf<CalendarEvent<String>>()
        val overflowClicks = mutableListOf<Pair<LocalDate, Int>>()
        rule.setContent {
            HorizontalCalendar(
                state = julyState(),
                weekOverlay = { week ->
                    WeekEventBanners(
                        week = week,
                        events = events,
                        onEventClick = { clicked += it },
                        onOverflowClick = { date, count -> overflowClicks += date to count },
                    )
                },
                dayContent = { day -> Text(day.date.day.toString()) },
            )
        }
        // The trip wraps from the second week row into the third.
        rule.onAllNodesWithText("Summer trip").assertCountEquals(2)
        rule.onNodeWithText("Conference").assertIsDisplayed().performClick()
        assertEquals(listOf("payload-Conference"), clicked.map { it.payload })

        // Sprint, Offsite and Demo fill the three lanes on Wed Jul 22; Retro overflows there.
        rule.onAllNodesWithText("Retro").assertCountEquals(0)
        rule.onNodeWithText("+1").assertIsDisplayed().performClick()
        assertEquals(listOf(jul(22) to 1), overflowClicks)
    }

    @Test
    fun bannersWithoutCallbacksAreNotClickable() {
        val pack = packWeekEventBanners((6..12).map { jul(it) }, events, maxVisibleLanes = 1)
        rule.setContent {
            WeekEventBanners(packResult = pack, overflowLabel = { "+$it more" })
        }
        rule.onNodeWithText("Conference").assertHasNoClickAction()
        // The trip (Jul 9-12 here) shares no column with the conference, so both fit in lane 0.
        rule.onNodeWithText("Summer trip").assertIsDisplayed()
        rule.onAllNodesWithText("more", substring = true).assertCountEquals(0)
    }

    @Test
    fun customOverflowLabelIsShownAndClickable() {
        val pack = packWeekEventBanners((20..26).map { jul(it) }, events, maxVisibleLanes = 1)
        rule.setContent {
            WeekEventBanners(packResult = pack, overflowLabel = { "+$it more" }, onOverflowClick = { _, _ -> })
        }
        // Sprint takes the only lane; Offsite (Jul 22-23), Demo and Retro (Jul 22) overflow.
        rule.onNodeWithText("Sprint").assertIsDisplayed()
        rule.onNodeWithText("+3 more").assertIsDisplayed().assertHasClickAction()
        rule.onNodeWithText("+1 more").assertIsDisplayed()
    }

    @Test
    fun verticalCalendarRendersWeekAndMonthFooters() {
        rule.setContent {
            VerticalCalendar(
                state = julyState(),
                weekFooter = { week -> Text("Week footer ${week.days.first().date}") },
                monthFooter = { month -> Text("Month footer ${month.yearMonth}") },
                dayContent = { day -> Text(day.date.day.toString()) },
            )
        }
        rule.onAllNodesWithText("Week footer", substring = true).assertCountEquals(5)
        rule.onNodeWithText("Week footer 2026-07-06").assertIsDisplayed()
        rule.onNodeWithText("Month footer 2026-07").assertIsDisplayed()
    }

    @Test
    fun weekCalendarRendersOverlayAndFooter() {
        rule.setContent {
            WeekCalendar(
                state =
                    rememberWeekCalendarState(
                        startDate = jul(1),
                        endDate = jul(31),
                        firstVisibleDate = jul(9),
                        firstDayOfWeek = DayOfWeek.MONDAY,
                    ),
                weekOverlay = { week -> WeekEventBanners(week = week, events = events) },
                weekFooter = { week -> Text("Footer ${week.days.first().date}") },
                dayContent = { day -> Text(day.date.day.toString()) },
            )
        }
        rule.onNodeWithText("Summer trip").assertIsDisplayed()
        rule.onNodeWithText("Conference").assertIsDisplayed()
        rule.onNodeWithText("Footer 2026-07-06").assertIsDisplayed()
    }

    @Test
    fun dragSelectionFollowsUnevenRowsWithOverlayAndFooter() {
        lateinit var selection: CalendarSelectionState
        rule.setContent {
            selection = rememberCalendarSelectionState(mode = SelectionMode.Range())
            HorizontalCalendar(
                state = julyState(),
                dragSelection = selection,
                modifier = Modifier.testTag("cal"),
                // Only the second row gets a tall overlay, so rows are no longer evenly sized.
                weekOverlay = { week ->
                    if (week.days.any { it.date == jul(8) }) Spacer(Modifier.fillMaxWidth().height(120.dp))
                },
                weekFooter = { Spacer(Modifier.height(16.dp)) },
                dayContent = { day ->
                    Box(Modifier.testTag("d-${day.date}").padding(12.dp)) { Text(day.date.day.toString()) }
                },
            )
        }
        val calendar = rule.onNodeWithTag("cal").fetchSemanticsNode().boundsInRoot

        fun centerOf(date: LocalDate): Offset =
            rule
                .onNodeWithTag("d-$date")
                .fetchSemanticsNode()
                .boundsInRoot.center - calendar.topLeft

        val start = centerOf(jul(7))
        val end = centerOf(jul(15))
        rule.onNodeWithTag("cal").performTouchInput {
            down(start)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveTo(end)
            up()
        }
        rule.waitForIdle()
        assertEquals(jul(7)..jul(15), selection.selection.range)
    }
}
