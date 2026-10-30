package me.kozakov.orrery.core

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WeekEventBannersTest {
    // Monday 2026-07-06 .. Sunday 2026-07-12.
    private val week = (6..12).map { LocalDate(2026, 7, it) }

    private fun allDay(
        id: String,
        start: LocalDate,
        end: LocalDate = start,
        title: String = id,
    ): CalendarEvent<Nothing> = CalendarEvent(id = id, title = title, span = EventSpan.AllDay(start, end))

    private fun jul(day: Int) = LocalDate(2026, 7, day)

    @Test
    fun emptyWeekProducesEmptyResult() {
        val result = packWeekEventBanners(emptyList(), listOf(allDay("a", jul(6))))
        assertEquals(emptyList(), result.banners)
        assertEquals(emptyList(), result.overflowCountByColumn)
    }

    @Test
    fun eventsOutsideTheWeekAreIgnored() {
        val result =
            packWeekEventBanners(
                week,
                listOf(allDay("before", jul(1), jul(5)), allDay("after", jul(13), jul(20))),
            )
        assertEquals(emptyList(), result.banners)
        assertEquals(List(7) { 0 }, result.overflowCountByColumn)
        assertEquals(week, result.weekDates)
    }

    @Test
    fun singleEventMapsToColumnsWithoutContinuation() {
        val banner = packWeekEventBanners(week, listOf(allDay("a", jul(7), jul(9)))).banners.single()
        assertEquals(1, banner.startColumn)
        assertEquals(3, banner.endColumnInclusive)
        assertEquals(3, banner.columnSpan)
        assertEquals(0, banner.laneIndex)
        assertFalse(banner.continuesFromPreviousWeek)
        assertFalse(banner.continuesIntoNextWeek)
    }

    @Test
    fun multiWeekEventWrapsAcrossWeekRows() {
        // Thu 2026-07-09 .. Tue 2026-07-21 covers three Monday-first weeks.
        val event = allDay("trip", jul(9), jul(21))
        val month = monthGrid(YearMonth(2026, 7), DayOfWeek.MONDAY, OutDateStyle.EndOfRow)
        val perWeek =
            month.weeks.map { calendarWeek ->
                packWeekEventBanners(calendarWeek.days.map { it.date }, listOf(event)).banners.singleOrNull()
            }
        val covered = perWeek.filterNotNull()
        assertEquals(3, covered.size)

        val (first, middle, last) = covered
        assertEquals(3 to 6, first.startColumn to first.endColumnInclusive)
        assertFalse(first.continuesFromPreviousWeek)
        assertTrue(first.continuesIntoNextWeek)

        assertEquals(0 to 6, middle.startColumn to middle.endColumnInclusive)
        assertTrue(middle.continuesFromPreviousWeek)
        assertTrue(middle.continuesIntoNextWeek)

        assertEquals(0 to 1, last.startColumn to last.endColumnInclusive)
        assertTrue(last.continuesFromPreviousWeek)
        assertFalse(last.continuesIntoNextWeek)
    }

    @Test
    fun packingOrderIsStartThenLongerSpanThenTitleThenId() {
        val events =
            listOf(
                allDay("z-short", jul(8), jul(8), title = "Short"),
                allDay("long", jul(8), jul(11), title = "Long"),
                allDay("early", jul(6), jul(7), title = "Early"),
                allDay("b", jul(8), jul(8), title = "Short"),
                allDay("alpha", jul(8), jul(8), title = "Alpha"),
            )
        val banners = packWeekEventBanners(week, events, maxVisibleLanes = 10).banners
        assertEquals(listOf("early", "long", "alpha", "b", "z-short"), banners.map { it.event.id })
        // "early" ends on column 1, so "long" (from column 2) reuses lane 0.
        assertEquals(listOf(0, 0, 1, 2, 3), banners.map { it.laneIndex })
    }

    @Test
    fun greedyLanesReuseTheLowestFreeLane() {
        val events =
            listOf(
                allDay("a", jul(6), jul(8)),
                allDay("b", jul(7), jul(10)),
                allDay("c", jul(9), jul(12)),
                allDay("d", jul(11), jul(12)),
            )
        val lanes = packWeekEventBanners(week, events).banners.associate { it.event.id to it.laneIndex }
        assertEquals(mapOf("a" to 0, "b" to 1, "c" to 0, "d" to 1), lanes)
    }

    @Test
    fun eventsThatDoNotFitCountAsOverflowPerCoveredColumn() {
        val events =
            listOf(
                allDay("a", jul(6), jul(12)),
                allDay("b", jul(6), jul(9)),
                allDay("c", jul(7), jul(8)),
                allDay("d", jul(8), jul(10)),
                allDay("e", jul(10), jul(10)),
            )
        val result = packWeekEventBanners(week, events, maxVisibleLanes = 2)
        assertEquals(listOf("a", "b", "e"), result.banners.map { it.event.id })
        assertEquals(listOf(0, 1, 1), result.banners.map { it.laneIndex })
        // c covers columns 1..2, d covers columns 2..4.
        assertEquals(listOf(0, 1, 2, 1, 1, 0, 0), result.overflowCountByColumn)
    }

    @Test
    fun zeroVisibleLanesMovesEverythingToOverflow() {
        val result = packWeekEventBanners(week, listOf(allDay("a", jul(5), jul(7))), maxVisibleLanes = 0)
        assertEquals(emptyList(), result.banners)
        assertEquals(listOf(1, 1, 0, 0, 0, 0, 0), result.overflowCountByColumn)
    }

    @Test
    fun timedEventsArePackedByTheDatesTheyTouch() {
        val overnight =
            CalendarEvent<Nothing>(
                id = "night",
                title = "Night shift",
                span = EventSpan.Timed(LocalDateTime(2026, 7, 12, 22, 0), LocalDateTime(2026, 7, 13, 6, 0)),
            )
        val banner = packWeekEventBanners(week, listOf(overnight)).banners.single()
        assertEquals(6 to 6, banner.startColumn to banner.endColumnInclusive)
        assertTrue(banner.continuesIntoNextWeek)
    }

    @Test
    fun weekDatesMaySkipDays() {
        val workWeek = (6..10).map { jul(it) }
        val events = listOf(allDay("weekend", jul(11), jul(12)), allDay("long", jul(4), jul(14)))
        val result = packWeekEventBanners(workWeek, events)
        val banner = result.banners.single()
        assertEquals("long", banner.event.id)
        assertEquals(0 to 4, banner.startColumn to banner.endColumnInclusive)
        assertTrue(banner.continuesFromPreviousWeek)
        assertTrue(banner.continuesIntoNextWeek)
    }

    @Test
    fun rejectsInvalidArguments() {
        assertFailsWith<IllegalArgumentException> {
            packWeekEventBanners(week, emptyList<CalendarEvent<Nothing>>(), maxVisibleLanes = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            packWeekEventBanners(week.reversed(), emptyList<CalendarEvent<Nothing>>())
        }
    }
}
