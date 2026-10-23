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
        val timed = EventSpan.Timed(start = start, end = end)
        assertEquals(jul10, timed.startDate)
        assertEquals(jul11, timed.endDate)

        val zeroDuration = EventSpan.Timed(start = start, end = start)
        assertEquals(jul10, zeroDuration.startDate)

        assertFailsWith<IllegalArgumentException> {
            EventSpan.Timed(start = end, end = start)
        }
    }

    @Test
    fun `occursOn checks inclusive date span for all-day and overnight timed events`() {
        val multiAllDay =
            CalendarEvent<Nothing>(
                id = "all-day",
                title = "Conference",
                span = EventSpan.AllDay(jul10, jul12),
            )
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
        val timedLate =
            CalendarEvent<Nothing>(
                id = "t-late",
                title = "Afternoon sync",
                span =
                    EventSpan.Timed(
                        start = LocalDateTime(2026, 7, 10, 15, 0),
                        end = LocalDateTime(2026, 7, 10, 16, 0),
                    ),
            )
        val timedEarlyLong =
            CalendarEvent<Nothing>(
                id = "t-early-long",
                title = "Workshop",
                span =
                    EventSpan.Timed(
                        start = LocalDateTime(2026, 7, 10, 9, 0),
                        end = LocalDateTime(2026, 7, 10, 11, 0),
                    ),
            )
        val timedEarlyShortB =
            CalendarEvent<Nothing>(
                id = "t-early-b",
                title = "Standup B",
                span =
                    EventSpan.Timed(
                        start = LocalDateTime(2026, 7, 10, 9, 0),
                        end = LocalDateTime(2026, 7, 10, 9, 30),
                    ),
            )
        val timedEarlyShortA2 =
            CalendarEvent<Nothing>(
                id = "t-early-a-2",
                title = "Standup A",
                span =
                    EventSpan.Timed(
                        start = LocalDateTime(2026, 7, 10, 9, 0),
                        end = LocalDateTime(2026, 7, 10, 9, 30),
                    ),
            )
        val timedEarlyShortA1 =
            CalendarEvent<Nothing>(
                id = "t-early-a-1",
                title = "Standup A",
                span =
                    EventSpan.Timed(
                        start = LocalDateTime(2026, 7, 10, 9, 0),
                        end = LocalDateTime(2026, 7, 10, 9, 30),
                    ),
            )
        val allDayTodayB =
            CalendarEvent<Nothing>(
                id = "ad-today-b",
                title = "Release B",
                span = EventSpan.AllDay(jul10),
            )
        val allDayTodayA =
            CalendarEvent<Nothing>(
                id = "ad-today-a",
                title = "Release A",
                span = EventSpan.AllDay(jul10),
            )
        val allDayStartedEarlier =
            CalendarEvent<Nothing>(
                id = "ad-earlier",
                title = "Zeta Summit",
                span = EventSpan.AllDay(LocalDate(2026, 7, 9), jul11),
            )

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
        val multi =
            CalendarEvent<Nothing>(
                id = "multi",
                title = "Roadshow",
                span = EventSpan.AllDay(jul10, jul13),
            )
        val outside =
            CalendarEvent<Nothing>(
                id = "outside",
                title = "August event",
                span = EventSpan.AllDay(LocalDate(2026, 8, 1)),
            )

        val clipped = listOf(multi, outside).eventsByDate(jul11..jul12)
        assertEquals(setOf(jul11, jul12), clipped.keys)
        assertEquals(listOf(multi), clipped[jul11])
        assertEquals(listOf(multi), clipped[jul12])

        assertTrue(listOf(multi).eventsByDate(jul13..jul10).isEmpty())
        assertTrue(emptyList<CalendarEvent<Nothing>>().eventsByDate(jul10..jul13).isEmpty())
    }
}
