package me.kozakov.orrery.core

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RecurringCalendarEventTest {
    private fun d(iso: String): LocalDate = LocalDate.parse(iso)

    private fun allDay(
        id: String,
        start: String,
        end: String = start,
        recurrence: RecurrenceRule? = null,
    ): CalendarEvent<String> =
        CalendarEvent(
            id = id,
            title = id.uppercase(),
            span = EventSpan.AllDay(d(start), d(end)),
            subtitle = "room",
            colorArgb = 0xFF0000FF,
            payload = "payload-$id",
            recurrence = recurrence,
        )

    // Friday 2026-01-02 to Monday 2026-01-05, every week.
    private val weekend = allDay("weekend", "2026-01-02", "2026-01-05", RecurrenceRule.weekly())

    @Test
    fun `Alox1d fixtures hold through CalendarEvent occurrencesIn`() {
        val master = allDay("rent", "2026-01-31", recurrence = RecurrenceRule.parse("FREQ=MONTHLY;COUNT=3"))
        val year = d("2026-01-01")..d("2026-12-31")
        assertEquals(
            listOf("rent@2026-01-31", "rent@2026-03-31", "rent@2026-05-31"),
            master.occurrencesIn(year).map { it.id },
        )
        assertEquals(listOf("rent@2026-03-31"), master.occurrencesIn(d("2026-03-01")..d("2026-03-31")).map { it.id })
        val withExDate = master.copy(recurrence = master.recurrence?.copy(exDates = setOf(d("2026-03-31"))))
        assertEquals(listOf("rent@2026-01-31", "rent@2026-05-31"), withExDate.occurrencesIn(year).map { it.id })
        val lastDay = master.copy(recurrence = RecurrenceRule.monthlyOnLastDay().copy(count = 3))
        assertEquals(
            listOf(d("2026-01-31"), d("2026-02-28"), d("2026-03-31")),
            lastDay.occurrencesIn(year).map { it.span.startDate },
        )
    }

    @Test
    fun `multiDay - an occurrence that began before the window still overlaps it`() {
        val occurrences = weekend.occurrencesIn(d("2026-01-10")..d("2026-01-11"))
        assertEquals(1, occurrences.size)
        val occurrence = occurrences.single()
        assertEquals(EventSpan.AllDay(d("2026-01-09"), d("2026-01-12")), occurrence.span)
        assertEquals("weekend@2026-01-09", occurrence.id)
        assertEquals(RecurrenceInstance("weekend", d("2026-01-09")), occurrence.instance)
        assertNull(occurrence.recurrence)
        assertEquals("WEEKEND", occurrence.title)
        assertEquals("room", occurrence.subtitle)
        assertEquals(0xFF0000FF, occurrence.colorArgb)
        assertEquals("payload-weekend", occurrence.payload)
    }

    @Test
    fun `multiDay - a window between occurrences returns nothing`() {
        // Tuesday..Thursday: the first occurrence ended Monday and the next starts Friday.
        assertEquals(emptyList(), weekend.occurrencesIn(d("2026-01-06")..d("2026-01-08")))
        assertEquals(
            listOf("weekend@2026-01-02", "weekend@2026-01-09"),
            weekend.occurrencesIn(d("2026-01-05")..d("2026-01-09")).map { it.id },
        )
    }

    @Test
    fun `multiDay - an EXDATE on the start removes the whole occurrence`() {
        val skipped = weekend.copy(recurrence = RecurrenceRule.weekly().copy(exDates = setOf(d("2026-01-09"))))
        assertEquals(emptyList(), skipped.occurrencesIn(d("2026-01-10")..d("2026-01-11")))
        assertEquals(
            listOf("weekend@2026-01-16"),
            skipped.occurrencesIn(d("2026-01-10")..d("2026-01-16")).map { it.id },
        )
    }

    @Test
    fun `multiDay - a timed overnight event keeps its start and end time of day`() {
        val event =
            CalendarEvent<Nothing>(
                id = "night",
                title = "Night shift",
                span = EventSpan.Timed(LocalDateTime(2026, 1, 2, 22, 0), LocalDateTime(2026, 1, 3, 2, 30)),
                recurrence = RecurrenceRule.parse("FREQ=DAILY;COUNT=3"),
            )
        val spans = event.occurrencesIn(d("2026-01-04")..d("2026-01-04")).map { it.span }
        assertEquals(
            listOf(
                EventSpan.Timed(LocalDateTime(2026, 1, 3, 22, 0), LocalDateTime(2026, 1, 4, 2, 30)),
                EventSpan.Timed(LocalDateTime(2026, 1, 4, 22, 0), LocalDateTime(2026, 1, 5, 2, 30)),
            ),
            spans,
        )
        assertEquals(emptyList(), event.occurrencesIn(d("2026-01-06")..d("2026-01-31")))
    }

    @Test
    fun `a plain event is returned as is when it overlaps the range`() {
        val plain = allDay("plain", "2026-01-05", "2026-01-07")
        assertSame(plain, plain.occurrencesIn(d("2026-01-07")..d("2026-01-09")).single())
        assertEquals(emptyList(), plain.occurrencesIn(d("2026-01-08")..d("2026-01-09")))
        assertEquals(emptyList(), weekend.occurrencesIn(d("2026-01-09")..d("2026-01-01")))
    }

    @Test
    fun `expandRecurring keeps input order drops plain events outside and is idempotent`() {
        val before = allDay("before", "2026-01-01")
        val inside = allDay("inside", "2026-01-12")
        val outside = allDay("outside", "2026-03-01")
        val range = d("2026-01-10")..d("2026-01-25")
        val expanded = listOf(inside, weekend, outside, before).expandRecurring(range)
        assertEquals(
            listOf("inside", "weekend@2026-01-09", "weekend@2026-01-16", "weekend@2026-01-23"),
            expanded.map { it.id },
        )
        assertEquals(expanded, expanded.expandRecurring(range))
        assertEquals(expanded.size, expanded.map { it.id }.toSet().size)
        assertTrue(expanded.drop(1).all { it.instance?.seriesId == "weekend" && it.recurrence == null })
        assertNull(expanded.first().instance)
    }

    @Test
    fun `eventsByDate expands recurring events automatically`() {
        val standup =
            CalendarEvent<Nothing>(
                id = "standup",
                title = "Standup",
                span = EventSpan.Timed(LocalDateTime(2026, 1, 5, 9, 0), LocalDateTime(2026, 1, 5, 9, 15)),
                recurrence = RecurrenceRule.weekly(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            )
        val byDate = listOf(standup).eventsByDate(d("2026-01-01")..d("2026-01-15"))
        assertEquals(listOf(d("2026-01-05"), d("2026-01-08"), d("2026-01-12"), d("2026-01-15")), byDate.keys.toList())
        assertEquals("standup@2026-01-08", byDate.getValue(d("2026-01-08")).single().id)
        val days = listOf(weekend).eventsByDate(d("2026-01-10")..d("2026-01-12"))
        val ids = days.values.flatten().map { it.id }
        assertEquals(listOf("weekend@2026-01-09"), ids.distinct())
        assertEquals(3, days.size)
    }

    @Test
    fun `recurrence and instance take part in equality`() {
        val plain = allDay("a", "2026-01-01")
        assertNotEquals(plain, plain.copy(recurrence = RecurrenceRule.daily()))
        assertNotEquals(plain, plain.copy(instance = RecurrenceInstance("a", d("2026-01-01"))))
        assertEquals(
            plain.copy(recurrence = RecurrenceRule.daily()),
            plain.copy(recurrence = RecurrenceRule.parse("FREQ=DAILY")),
        )
    }
}
