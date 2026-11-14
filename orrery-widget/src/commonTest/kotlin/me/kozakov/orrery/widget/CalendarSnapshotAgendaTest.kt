package me.kozakov.orrery.widget

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.EventSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalendarSnapshotAgendaTest {
    @Test
    fun allDayEventsComeFirstThenTimedEventsByStartTime() {
        val events = listOf(timed("c", 14, 15), allDay("b", 14), timed("a", 14, 9), allDay("d", 14))
        val days = packAgenda(events, day(14), days = 1, maxRows = 10)
        assertEquals(1, days.size)
        assertEquals(listOf("b", "d", "a", "c"), days.single().rows.map { it.event.id })
        assertEquals(listOf(null, null, LocalTime(9, 0), LocalTime(15, 0)), days.single().rows.map { it.startTime })
    }

    @Test
    fun theRowCapCountsRowsAcrossDays() {
        val events = (1..3).map { allDay("a$it", 14) } + (1..3).map { allDay("b$it", 15) }
        val days = packAgenda(events, day(14), days = 7, maxRows = 4)
        assertEquals(listOf(day(14), day(15)), days.map { it.date })
        assertEquals(listOf(3, 1), days.map { it.rows.size })
        assertEquals(listOf("b1"), days[1].rows.map { it.event.id })
    }

    @Test
    fun emptyAgendasHaveNoDays() {
        assertTrue(packAgenda(emptyList(), day(14), days = 7, maxRows = 4).isEmpty())
        assertTrue(packAgenda(listOf(allDay("x", 10), allDay("y", 21)), day(14), days = 7, maxRows = 4).isEmpty())
        assertTrue(packAgenda(listOf(allDay("x", 14)), day(14), days = 7, maxRows = 0).isEmpty())
    }

    @Test
    fun daysWithoutEventsGetNoHeading() {
        val days = packAgenda(listOf(allDay("x", 16), allDay("y", 14)), day(14), days = 7, maxRows = 4)
        assertEquals(listOf(day(14), day(16)), days.map { it.date })
    }

    @Test
    fun multiDayEventsAreListedOnEveryDayOfTheWindow() {
        val trip = CalendarEvent<Nothing>("trip", "Trip", EventSpan.AllDay(day(12), day(16)))
        val days = packAgenda(listOf(trip), day(14), days = 2, maxRows = 10)
        assertEquals(listOf(day(14), day(15)), days.map { it.date })
        assertTrue(days.all { it.rows.single().startTime == null })
    }

    @Test
    fun timedMultiDayEventsShowTheirStartTimeOnTheFirstDayOnly() {
        val night = timedSpan("night", LocalDateTime(2026, 11, 14, 22, 0), LocalDateTime(2026, 11, 15, 2, 0))
        val early = timed("early", 15, 7)
        val days = packAgenda(listOf(early, night), day(14), days = 2, maxRows = 10)
        assertEquals(LocalTime(22, 0), days[0].rows.single().startTime)
        // On the second day the continuing event has no start time, so it comes first.
        assertEquals(listOf("night", "early"), days[1].rows.map { it.event.id })
        assertEquals(listOf(null, LocalTime(7, 0)), days[1].rows.map { it.startTime })
    }

    @Test
    fun aTimedEventEndingAtMidnightDoesNotCoverTheNextDay() {
        val evening = timedSpan("evening", LocalDateTime(2026, 11, 14, 20, 0), LocalDateTime(2026, 11, 15, 0, 0))
        val days = packAgenda(listOf(evening), day(14), days = 3, maxRows = 10)
        assertEquals(listOf(day(14)), days.map { it.date })
    }

    @Test
    fun equalKeysKeepTheCallerOrder() {
        val days = packAgenda(listOf(allDay("y", 14), allDay("x", 14), allDay("z", 14)), day(14), 1, 10)
        assertEquals(listOf("y", "x", "z"), days.single().rows.map { it.event.id })
    }

    @Test
    fun longAndRtlTitlesAreKeptWholeForThePainterToEllipsise() {
        val long = "Quarterly planning ".repeat(40)
        val rtl = "פגישת צוות שבועית"
        val events =
            listOf(
                CalendarEvent<Nothing>("long", long, EventSpan.AllDay(day(14))),
                CalendarEvent<Nothing>("rtl", rtl, EventSpan.AllDay(day(14))),
            )
        val titles = packAgenda(events, day(14), 1, 10).single().rows.map { it.event.title }
        assertEquals(listOf(long, rtl), titles)
    }

    @Test
    fun fittingDropsRowsThatDoNotFitAndNeverLeavesABareHeading() {
        val days =
            listOf(
                AgendaDay(day(14), (1..3).map { AgendaRow(allDay("a$it", 14), null) }),
                AgendaDay(day(15), (1..2).map { AgendaRow(allDay("b$it", 15), null) }),
            )

        fun fit(height: Float) = fitAgenda(days, height, headingHeight = 10f, rowHeight = 20f).map { it.rows.size }
        assertEquals(listOf(3, 2), fit(1000f))
        assertEquals(listOf(3, 1), fit(105f))
        assertEquals(listOf(3), fit(75f))
        assertEquals(listOf(2), fit(65f))
        assertEquals(emptyList(), fit(25f))
        assertEquals(emptyList(), fitAgenda(emptyList(), 100f, 10f, 20f))
    }

    private fun day(day: Int) = LocalDate(2026, 11, day)

    private fun allDay(
        id: String,
        dayOfMonth: Int,
    ) = CalendarEvent<Nothing>(id, "Event $id", EventSpan.AllDay(day(dayOfMonth)))

    private fun timed(
        id: String,
        dayOfMonth: Int,
        hour: Int,
    ) = timedSpan(id, LocalDateTime(2026, 11, dayOfMonth, hour, 0), LocalDateTime(2026, 11, dayOfMonth, hour + 1, 0))

    private fun timedSpan(
        id: String,
        start: LocalDateTime,
        end: LocalDateTime,
    ) = CalendarEvent<Nothing>(id, "Event $id", EventSpan.Timed(start, end))
}
