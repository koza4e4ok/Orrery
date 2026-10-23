package me.kozakov.orrery.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CalendarEventTest {
    private val jul10 = LocalDate(2026, 7, 10)
    private val jul11 = LocalDate(2026, 7, 11)
    private val jul12 = LocalDate(2026, 7, 12)
    private val jul13 = LocalDate(2026, 7, 13)

    private fun allDay(
        id: String,
        title: String,
        start: LocalDate,
        end: LocalDate = start,
    ): CalendarEvent<Nothing> = CalendarEvent(id = id, title = title, span = EventSpan.AllDay(start, end))

    private fun timed(
        id: String,
        title: String,
        startHour: Int,
        endHour: Int,
        endMinute: Int = 0,
    ): CalendarEvent<Nothing> =
        CalendarEvent(
            id = id,
            title = title,
            span =
                EventSpan.Timed(
                    start = LocalDateTime(2026, 7, 10, startHour, 0),
                    end = LocalDateTime(2026, 7, 10, endHour, endMinute),
                ),
        )

    @Test
    fun `all-day span defaults endDate to startDate and validates bounds`() {
        val single = EventSpan.AllDay(jul10)
        assertEquals(jul10, single.startDate)
        assertEquals(jul10, single.endDate)

        val multi = EventSpan.AllDay(jul10, jul12)
        assertEquals(jul10, multi.startDate)
        assertEquals(jul12, multi.endDate)

        assertFailsWith<IllegalArgumentException> {
            EventSpan.AllDay(startDate = jul12, endDate = jul10)
        }
    }

    @Test
    fun `timed span exposes start and end dates and validates bounds`() {
        val start = LocalDateTime(2026, 7, 10, 22, 0)
        val end = LocalDateTime(2026, 7, 11, 2, 0)
        val span = EventSpan.Timed(start = start, end = end)
        assertEquals(jul10, span.startDate)
        assertEquals(jul11, span.endDate)

        val zeroDuration = EventSpan.Timed(start = start, end = start)
        assertEquals(jul10, zeroDuration.startDate)

        assertFailsWith<IllegalArgumentException> {
            EventSpan.Timed(start = end, end = start)
        }
    }

    @Test
    fun `occursOn checks inclusive date span for all-day and overnight timed events`() {
        val multiAllDay = allDay(id = "all-day", title = "Conference", start = jul10, end = jul12)
        assertFalse(multiAllDay.occursOn(LocalDate(2026, 7, 9)))
        assertTrue(multiAllDay.occursOn(jul10))
        assertTrue(multiAllDay.occursOn(jul11))
        assertTrue(multiAllDay.occursOn(jul12))
        assertFalse(multiAllDay.occursOn(jul13))

        val overnight =
            CalendarEvent(
                id = "overnight",
                title = "Deploy window",
                span =
                    EventSpan.Timed(
                        start = LocalDateTime(2026, 7, 10, 23, 0),
                        end = LocalDateTime(2026, 7, 11, 1, 30),
                    ),
                payload = 42,
            )
        assertFalse(overnight.occursOn(LocalDate(2026, 7, 9)))
        assertTrue(overnight.occursOn(jul10))
        assertTrue(overnight.occursOn(jul11))
        assertFalse(overnight.occursOn(jul12))
        assertEquals(42, overnight.payload)
    }

    @Test
    fun `eventsByDate sorts all-day before timed with canonical tie-breaking`() {
        val timedLate = timed("t-late", "Afternoon sync", 15, 16)
        val timedEarlyLong = timed("t-early-long", "Workshop", 9, 11)
        val timedEarlyShortB = timed("t-early-b", "Standup B", 9, 9, 30)
        val timedEarlyShortA2 = timed("t-early-a-2", "Standup A", 9, 9, 30)
        val timedEarlyShortA1 = timed("t-early-a-1", "Standup A", 9, 9, 30)
        val allDayTodayB = allDay("ad-today-b", "Release B", jul10)
        val allDayTodayA = allDay("ad-today-a", "Release A", jul10)
        val allDayStartedEarlier = allDay("ad-earlier", "Zeta Summit", LocalDate(2026, 7, 9), jul11)

        val indexed =
            listOf(
                timedLate,
                timedEarlyLong,
                allDayTodayB,
                timedEarlyShortB,
                timedEarlyShortA2,
                allDayTodayA,
                timedEarlyShortA1,
                allDayStartedEarlier,
            ).eventsByDate(jul10..jul10)

        assertEquals(
            listOf(
                allDayStartedEarlier,
                allDayTodayA,
                allDayTodayB,
                timedEarlyShortA1,
                timedEarlyShortA2,
                timedEarlyShortB,
                timedEarlyLong,
                timedLate,
            ),
            indexed[jul10],
        )
    }

    @Test
    fun `eventsByDate clips multi-day spans to queried range and handles empty range`() {
        val multi = allDay(id = "multi", title = "Roadshow", start = jul10, end = jul13)
        val outside = allDay(id = "outside", title = "August event", start = LocalDate(2026, 8, 1))

        val clipped = listOf(multi, outside).eventsByDate(jul11..jul12)
        assertEquals(setOf(jul11, jul12), clipped.keys)
        assertEquals(listOf(multi), clipped[jul11])
        assertEquals(listOf(multi), clipped[jul12])

        assertTrue(listOf(multi).eventsByDate(jul13..jul10).isEmpty())
        assertTrue(emptyList<CalendarEvent<Nothing>>().eventsByDate(jul10..jul13).isEmpty())
    }
}
