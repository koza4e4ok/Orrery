package me.kozakov.orrery.sample

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.EventSpan

/**
 * Applies the Day / Week schedule's drag edits to the sample's in-memory event list. The
 * schedule never changes events itself; it reports the snapped result once per gesture.
 */
internal object ScheduleEdits {
    private const val NEW_EVENT_COLOR = 0xFF00897BL

    /** Adds a "New event" over the dragged-out slot. */
    fun create(
        events: List<CalendarEvent<Nothing>>,
        date: LocalDate,
        start: LocalTime,
        end: LocalTime,
    ): List<CalendarEvent<Nothing>> =
        events +
            CalendarEvent(
                id = "new-$date-$start-${events.size}",
                title = "New event",
                span = timedSpan(date, start, end),
                colorArgb = NEW_EVENT_COLOR,
            )

    /**
     * Moves or resizes [event] to [date] from [start] to [end]. An occurrence of a recurring
     * series is detached: its date is excluded from the series (EXDATE) and it becomes a
     * plain event, so the rest of the series stays where it was.
     */
    fun reschedule(
        events: List<CalendarEvent<Nothing>>,
        event: CalendarEvent<Nothing>,
        date: LocalDate,
        start: LocalTime,
        end: LocalTime,
    ): List<CalendarEvent<Nothing>> {
        val span = timedSpan(date, start, end)
        val instance = event.instance ?: return events.map { if (it.id == event.id) it.copy(span = span) else it }
        return events.map { master ->
            val rule = master.recurrence
            if (master.id == instance.seriesId && rule != null) {
                master.copy(recurrence = rule.copy(exDates = rule.exDates + instance.recurrenceId))
            } else {
                master
            }
        } + event.copy(span = span, instance = null)
    }

    // The schedule reports an end that is not after the start as 00:00 of the next day.
    private fun timedSpan(
        date: LocalDate,
        start: LocalTime,
        end: LocalTime,
    ): EventSpan.Timed {
        val endDateTime =
            if (end > start) {
                LocalDateTime(date, end)
            } else {
                LocalDateTime(date.plus(1, DateTimeUnit.DAY), LocalTime(0, 0))
            }
        return EventSpan.Timed(start = LocalDateTime(date, start), end = endDateTime)
    }
}
