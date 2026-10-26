package me.kozakov.orrery.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ScheduleLayoutTest {
    private val day1 = LocalDate(2026, 7, 15)
    private val day2 = LocalDate(2026, 7, 16)

    private fun timed(
        id: String,
        start: String,
        end: String,
        startDate: LocalDate = day1,
        endDate: LocalDate = startDate,
    ): CalendarEvent<Nothing> =
        CalendarEvent(
            id = id,
            title = id,
            span = EventSpan.Timed(start = startDate.at(start), end = endDate.at(end)),
        )

    private fun LocalDate.at(time: String): LocalDateTime {
        val (hour, minute) = time.split(":").map(String::toInt)
        return LocalDateTime(this, LocalTime(hour, minute))
    }

    private fun DayScheduleLayout<*>.byId(id: String): PositionedEvent<*> = timedEvents.single { it.event.id == id }

    @Test
    fun nonOverlappingEventsTakeTheFullWidth() {
        val layout =
            layoutDaySchedule(
                day1,
                listOf(timed("a", "09:00", "10:00"), timed("b", "10:00", "11:00"), timed("c", "13:00", "14:30")),
            )
        assertEquals(listOf("a", "b", "c"), layout.timedEvents.map { it.event.id })
        for (positioned in layout.timedEvents) {
            assertEquals(0, positioned.columnIndex)
            assertEquals(1, positioned.columnCount)
            assertEquals(0f, positioned.leftFraction)
            assertEquals(1f, positioned.widthFraction)
        }
        assertEquals(780, layout.byId("c").startMinute)
        assertEquals(870, layout.byId("c").endMinute)
    }

    @Test
    fun pairwiseOverlapSplitsInHalves() {
        val layout = layoutDaySchedule(day1, listOf(timed("a", "09:00", "10:00"), timed("b", "09:30", "10:30")))
        assertEquals(0, layout.byId("a").columnIndex)
        assertEquals(1, layout.byId("b").columnIndex)
        assertEquals(0.5f, layout.byId("a").widthFraction)
        assertEquals(0.5f, layout.byId("b").leftFraction)
        assertEquals(0.5f, layout.byId("b").widthFraction)
        assertTrue(layout.timedEvents.all { it.columnCount == 2 })
    }

    @Test
    fun threeWayOverlapAssignsThreeColumns() {
        val layout =
            layoutDaySchedule(
                day1,
                listOf(timed("c", "09:30", "10:00"), timed("a", "09:00", "11:00"), timed("b", "09:00", "10:00")),
            )
        assertEquals(listOf("a", "b", "c"), layout.timedEvents.map { it.event.id })
        assertEquals(listOf(0, 1, 2), layout.timedEvents.map { it.columnIndex })
        for (positioned in layout.timedEvents) {
            assertEquals(3, positioned.columnCount)
            assertEquals(1 / 3f, positioned.widthFraction)
        }
        assertEquals(2 / 3f, layout.byId("c").leftFraction)
    }

    @Test
    fun staggeredChainReusesTheFirstColumn() {
        val layout =
            layoutDaySchedule(
                day1,
                listOf(timed("a", "09:00", "10:00"), timed("b", "09:30", "11:00"), timed("c", "10:00", "12:00")),
            )
        assertEquals(0, layout.byId("a").columnIndex)
        assertEquals(1, layout.byId("b").columnIndex)
        assertEquals(0, layout.byId("c").columnIndex)
        assertTrue(layout.timedEvents.all { it.columnCount == 2 })
        assertTrue(layout.timedEvents.all { it.widthFraction == 0.5f })
    }

    @Test
    fun eventExpandsRightAcrossFreeColumns() {
        val layout =
            layoutDaySchedule(
                day1,
                listOf(
                    timed("a", "09:00", "12:00"),
                    timed("b", "09:00", "10:00"),
                    timed("c", "09:00", "10:00"),
                    timed("d", "10:00", "11:00"),
                ),
            )
        assertEquals(1, layout.byId("d").columnIndex)
        assertEquals(3, layout.byId("d").columnCount)
        assertEquals(1 / 3f, layout.byId("d").leftFraction)
        assertEquals(2 / 3f, layout.byId("d").widthFraction)
        assertEquals(1 / 3f, layout.byId("a").widthFraction)
        assertEquals(1 / 3f, layout.byId("b").widthFraction)
    }

    @Test
    fun separateClustersDoNotShareColumnCounts() {
        val layout =
            layoutDaySchedule(
                day1,
                listOf(
                    timed("a", "09:00", "10:00"),
                    timed("b", "09:00", "10:00"),
                    timed("c", "09:00", "10:00"),
                    timed("d", "14:00", "15:00"),
                ),
            )
        assertEquals(3, layout.byId("a").columnCount)
        assertEquals(1, layout.byId("d").columnCount)
        assertEquals(1f, layout.byId("d").widthFraction)
    }

    @Test
    fun overnightEventIsClippedToEachDay() {
        val overnight = timed("night", "22:00", "02:00", startDate = day1, endDate = day2)
        val first = layoutDaySchedule(day1, listOf(overnight)).byId("night")
        assertEquals(1320, first.startMinute)
        assertEquals(1440, first.endMinute)
        assertEquals(1440, first.displayEndMinute)
        val second = layoutDaySchedule(day2, listOf(overnight)).byId("night")
        assertEquals(0, second.startMinute)
        assertEquals(120, second.endMinute)
        assertEquals(day2, second.date)
    }

    @Test
    fun overnightEventEndingAtMidnightIsExcludedFromTheNextDay() {
        val untilMidnight = timed("late", "22:00", "00:00", startDate = day1, endDate = day2)
        assertEquals(1440, layoutDaySchedule(day1, listOf(untilMidnight)).byId("late").endMinute)
        assertTrue(layoutDaySchedule(day2, listOf(untilMidnight)).timedEvents.isEmpty())
    }

    @Test
    fun shortEventsGetTheMinimumVisualHeight() {
        val layout =
            layoutDaySchedule(day1, listOf(timed("zero", "08:00", "08:00"), timed("five", "12:00", "12:05")))
        assertEquals(480, layout.byId("zero").endMinute)
        assertEquals(495, layout.byId("zero").displayEndMinute)
        assertEquals(725, layout.byId("five").endMinute)
        assertEquals(735, layout.byId("five").displayEndMinute)
    }

    @Test
    fun minimumHeightIsCappedAtMidnightAndHonorsCustomMinimum() {
        val layout =
            layoutDaySchedule(
                day1,
                listOf(timed("tail", "23:55", "23:58"), timed("short", "10:00", "10:05")),
                minDurationMinutes = 30,
            )
        assertEquals(1440, layout.byId("tail").displayEndMinute)
        assertEquals(630, layout.byId("short").displayEndMinute)
    }

    @Test
    fun minimumHeightCountsTowardsOverlap() {
        val layout = layoutDaySchedule(day1, listOf(timed("a", "09:00", "09:05"), timed("b", "09:10", "09:30")))
        assertEquals(2, layout.byId("a").columnCount)
        assertEquals(1, layout.byId("b").columnIndex)
    }

    @Test
    fun allDayEventsArePartitionedAndSorted() {
        val layout =
            layoutDaySchedule(
                day2,
                listOf(
                    CalendarEvent<Nothing>("z", "Zeta", EventSpan.AllDay(day2)),
                    CalendarEvent<Nothing>("x", "Offsite", EventSpan.AllDay(day1, day2)),
                    CalendarEvent<Nothing>("y", "Alpha", EventSpan.AllDay(day2)),
                    CalendarEvent<Nothing>("past", "Past", EventSpan.AllDay(day1)),
                    timed("t", "09:00", "10:00", startDate = day2),
                ),
            )
        assertEquals(listOf("x", "y", "z"), layout.allDayEvents.map { it.id })
        assertEquals(listOf("t"), layout.timedEvents.map { it.event.id })
        assertEquals(day2, layout.date)
    }

    @Test
    fun eventsOnOtherDaysAreIgnored() {
        val layout = layoutDaySchedule(day1, listOf(timed("other", "09:00", "10:00", startDate = day2)))
        assertTrue(layout.timedEvents.isEmpty())
        assertTrue(layout.allDayEvents.isEmpty())
    }

    @Test
    fun negativeMinimumDurationIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            layoutDaySchedule(day1, emptyList<CalendarEvent<Nothing>>(), minDurationMinutes = -1)
        }
    }
}
