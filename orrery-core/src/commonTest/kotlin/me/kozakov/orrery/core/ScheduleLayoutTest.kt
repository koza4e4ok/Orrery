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

    // minHour / maxHour visible window

    @Test
    fun visibleWindowClipsAndExcludesEventsOutsideTheHours() {
        val layout =
            layoutDaySchedule(
                day1,
                listOf(
                    timed("early", "06:00", "07:30"),
                    timed("edgeStart", "07:00", "08:00"),
                    timed("crossStart", "07:30", "09:00"),
                    timed("inside", "12:00", "13:00"),
                    timed("crossEnd", "17:30", "19:00"),
                    timed("atEnd", "18:00", "19:00"),
                    timed("late", "20:00", "21:00"),
                ),
                minHour = 8,
                maxHour = 18,
            )
        assertEquals(listOf("crossStart", "inside", "crossEnd"), layout.timedEvents.map { it.event.id })
        assertEquals(480, layout.byId("crossStart").startMinute)
        assertEquals(540, layout.byId("crossStart").endMinute)
        assertEquals(720, layout.byId("inside").startMinute)
        assertEquals(1050, layout.byId("crossEnd").startMinute)
        assertEquals(1080, layout.byId("crossEnd").endMinute)
        assertEquals(1080, layout.byId("crossEnd").displayEndMinute)
    }

    @Test
    fun visibleWindowCapsMinimumDurationAtMaxHour() {
        val layout =
            layoutDaySchedule(
                day1,
                listOf(timed("zero", "08:00", "08:00"), timed("tail", "17:55", "18:00")),
                minDurationMinutes = 30,
                minHour = 8,
                maxHour = 18,
            )
        assertEquals(480, layout.byId("zero").startMinute)
        assertEquals(510, layout.byId("zero").displayEndMinute)
        assertEquals(1075, layout.byId("tail").startMinute)
        assertEquals(1080, layout.byId("tail").displayEndMinute)
    }

    @Test
    fun visibleWindowClipsOvernightEvents() {
        val overnight = timed("night", "22:00", "09:00", startDate = day1, endDate = day2)
        val first = layoutDaySchedule(day1, listOf(overnight), minHour = 6, maxHour = 23).byId("night")
        assertEquals(1320, first.startMinute)
        assertEquals(1380, first.endMinute)
        val second = layoutDaySchedule(day2, listOf(overnight), minHour = 6, maxHour = 23).byId("night")
        assertEquals(360, second.startMinute)
        assertEquals(540, second.endMinute)
        assertTrue(layoutDaySchedule(day2, listOf(overnight), minHour = 10, maxHour = 23).timedEvents.isEmpty())
    }

    @Test
    fun defaultWindowMatchesTheThreeArgumentOverload() {
        val events =
            listOf(
                timed("a", "00:00", "01:00"),
                timed("b", "00:30", "02:00"),
                timed("c", "23:00", "23:59"),
                timed("d", "23:50", "00:00", startDate = day1, endDate = day2),
            )
        assertEquals(
            layoutDaySchedule(day1, events, 15),
            layoutDaySchedule(day1, events, 15, minHour = 0, maxHour = 24, overlapMode = EventOverlapMode.SideBySide),
        )
    }

    @Test
    fun invalidHourBoundsAreRejected() {
        val none = emptyList<CalendarEvent<Nothing>>()
        assertFailsWith<IllegalArgumentException> { layoutDaySchedule(day1, none, minHour = -1) }
        assertFailsWith<IllegalArgumentException> { layoutDaySchedule(day1, none, maxHour = 25) }
        assertFailsWith<IllegalArgumentException> { layoutDaySchedule(day1, none, minHour = 10, maxHour = 10) }
        assertFailsWith<IllegalArgumentException> { layoutDaySchedule(day1, none, minHour = 24) }
    }

    // EventOverlapMode.Stacked

    @Test
    fun stackedModeIndentsEachLaneAndExtendsToTheRightEdge() {
        val layout =
            layoutDaySchedule(
                day1,
                listOf(
                    timed("a", "09:00", "11:00"),
                    timed("b", "09:30", "10:30"),
                    timed("c", "10:00", "10:45"),
                    timed("solo", "13:00", "14:00"),
                ),
                overlapMode = EventOverlapMode.Stacked(),
            )
        assertEquals(0, layout.byId("a").columnIndex)
        assertEquals(1, layout.byId("b").columnIndex)
        assertEquals(2, layout.byId("c").columnIndex)
        assertEquals(3, layout.byId("a").columnCount)
        assertEquals(0f, layout.byId("a").leftFraction)
        assertEquals(1f, layout.byId("a").widthFraction)
        assertEquals(0.15f, layout.byId("b").leftFraction, 1e-6f)
        assertEquals(0.85f, layout.byId("b").widthFraction, 1e-6f)
        assertEquals(0.3f, layout.byId("c").leftFraction, 1e-6f)
        assertEquals(0.7f, layout.byId("c").widthFraction, 1e-6f)
        assertEquals(0f, layout.byId("solo").leftFraction)
        assertEquals(1f, layout.byId("solo").widthFraction)
    }

    @Test
    fun stackedModeCapsTheIndentAtThreeQuarters() {
        val events = (0 until 6).map { timed("e$it", "09:0$it", "12:00") }
        val layout = layoutDaySchedule(day1, events, overlapMode = EventOverlapMode.Stacked(laneIndentFraction = 0.2f))
        assertEquals(0.6f, layout.byId("e3").leftFraction, 1e-6f)
        assertEquals(0.75f, layout.byId("e4").leftFraction, 1e-6f)
        assertEquals(0.75f, layout.byId("e5").leftFraction, 1e-6f)
        assertEquals(0.25f, layout.byId("e5").widthFraction, 1e-6f)
    }

    @Test
    fun stackedModeReusesFreedLanes() {
        val layout =
            layoutDaySchedule(
                day1,
                listOf(timed("a", "09:00", "10:00"), timed("b", "09:30", "11:00"), timed("c", "10:00", "10:30")),
                overlapMode = EventOverlapMode.Stacked(0.1f),
            )
        assertEquals(0, layout.byId("c").columnIndex)
        assertEquals(0f, layout.byId("c").leftFraction)
        assertEquals(0.1f, layout.byId("b").leftFraction, 1e-6f)
    }

    @Test
    fun stackedIndentMustBeAFraction() {
        assertFailsWith<IllegalArgumentException> { EventOverlapMode.Stacked(-0.1f) }
        assertFailsWith<IllegalArgumentException> { EventOverlapMode.Stacked(1.5f) }
    }

    // packAllDayBanners

    private fun allDay(
        id: String,
        start: LocalDate,
        end: LocalDate = start,
    ): CalendarEvent<Nothing> = CalendarEvent(id = id, title = id, span = EventSpan.AllDay(start, end))

    private val week = (13..19).map { LocalDate(2026, 7, it) }

    @Test
    fun bannersSpanTheCoveredColumnsAndPackIntoLanes() {
        val banners =
            packAllDayBanners(
                week,
                listOf(
                    allDay("single", LocalDate(2026, 7, 15)),
                    allDay("offsite", LocalDate(2026, 7, 15), LocalDate(2026, 7, 16)),
                    allDay("trip", LocalDate(2026, 7, 14), LocalDate(2026, 7, 17)),
                    allDay("later", LocalDate(2026, 7, 18), LocalDate(2026, 7, 19)),
                    timed("meeting", "09:00", "10:00"),
                ),
            )
        assertEquals(listOf("trip", "offsite", "single", "later"), banners.map { it.event.id })
        val byId = banners.associateBy { it.event.id }
        assertEquals(AllDayBanner(byId.getValue("trip").event, 1, 4, 0, false, false), byId["trip"])
        assertEquals(2 to 3, byId.getValue("offsite").let { it.startColumn to it.endColumnInclusive })
        assertEquals(1, byId.getValue("offsite").laneIndex)
        assertEquals(2, byId.getValue("single").laneIndex)
        assertEquals(0, byId.getValue("later").laneIndex)
        assertEquals(5 to 6, byId.getValue("later").let { it.startColumn to it.endColumnInclusive })
    }

    @Test
    fun bannersClipAtThePageEdges() {
        val banners =
            packAllDayBanners(
                week,
                listOf(
                    allDay("before", LocalDate(2026, 7, 10), LocalDate(2026, 7, 14)),
                    allDay("after", LocalDate(2026, 7, 18), LocalDate(2026, 7, 25)),
                    allDay("whole", LocalDate(2026, 7, 1), LocalDate(2026, 7, 31)),
                    allDay("outside", LocalDate(2026, 7, 20), LocalDate(2026, 7, 21)),
                ),
            ).associateBy { it.event.id }
        assertEquals(setOf("before", "after", "whole"), banners.keys)
        with(banners.getValue("before")) {
            assertEquals(0 to 1, startColumn to endColumnInclusive)
            assertTrue(clippedStart)
            assertEquals(false, clippedEnd)
        }
        with(banners.getValue("after")) {
            assertEquals(5 to 6, startColumn to endColumnInclusive)
            assertEquals(false, clippedStart)
            assertTrue(clippedEnd)
        }
        with(banners.getValue("whole")) {
            assertEquals(0 to 6, startColumn to endColumnInclusive)
            assertTrue(clippedStart)
            assertTrue(clippedEnd)
            assertEquals(0, laneIndex)
        }
    }

    @Test
    fun bannersDeduplicateRepeatedIdsAndHandleEmptyInput() {
        val offsite = allDay("offsite", LocalDate(2026, 7, 15), LocalDate(2026, 7, 16))
        val banners = packAllDayBanners(week, listOf(offsite, offsite, offsite))
        assertEquals(1, banners.size)
        assertTrue(packAllDayBanners(emptyList(), listOf(offsite)).isEmpty())
        assertTrue(packAllDayBanners(week, emptyList<CalendarEvent<Nothing>>()).isEmpty())
    }
}
