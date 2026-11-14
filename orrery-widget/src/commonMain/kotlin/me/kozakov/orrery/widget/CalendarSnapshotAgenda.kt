package me.kozakov.orrery.widget

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.EventSpan

private val Midnight = LocalTime(0, 0)

/**
 * One event row of the agenda strip. [startTime] is set for a timed event on the day it
 * starts; all-day events and later days of a multi-day event have none.
 */
internal class AgendaRow(
    val event: CalendarEvent<*>,
    val startTime: LocalTime?,
)

/** One day heading of the agenda strip with its event rows (never empty). */
internal class AgendaDay(
    val date: LocalDate,
    val rows: List<AgendaRow>,
)

/**
 * Groups [events] by day for the [days] days from [start], with at most [maxRows] event
 * rows in total. An event is listed on every day of the window it covers; a timed event
 * that ends exactly at midnight does not cover the next day. Within a day, rows without
 * a start time (all-day events and continuing multi-day events) come first, then timed
 * events by start time; equal keys keep the order of [events]. Days without events get
 * no heading, so an empty agenda returns no days.
 */
internal fun packAgenda(
    events: List<CalendarEvent<*>>,
    start: LocalDate,
    days: Int,
    maxRows: Int,
): List<AgendaDay> {
    if (maxRows <= 0) return emptyList()
    val end = start.plus(days - 1, DateTimeUnit.DAY)
    val byDate = HashMap<LocalDate, MutableList<AgendaRow>>()
    for (event in events) {
        val first = maxOf(event.span.startDate, start)
        val last = minOf(lastCoveredDate(event.span), end)
        val timed = event.span as? EventSpan.Timed
        var date = first
        while (date <= last) {
            val startTime = if (timed != null && date == timed.start.date) timed.start.time else null
            byDate.getOrPut(date) { mutableListOf() } += AgendaRow(event, startTime)
            date = date.plus(1, DateTimeUnit.DAY)
        }
    }
    var remaining = maxRows
    val result = mutableListOf<AgendaDay>()
    for (date in byDate.keys.sorted()) {
        if (remaining == 0) break
        val rows =
            byDate
                .getValue(date)
                .sortedWith(compareBy(nullsFirst<LocalTime>(), AgendaRow::startTime))
                .take(remaining)
        remaining -= rows.size
        result += AgendaDay(date, rows)
    }
    return result
}

/** The last date [span] covers; a timed span ending at 00:00 after its start ends the day before. */
private fun lastCoveredDate(span: EventSpan): LocalDate =
    if (span is EventSpan.Timed && span.end.time == Midnight && span.end > span.start) {
        span.end.date.minus(1, DateTimeUnit.DAY)
    } else {
        span.endDate
    }

/**
 * Keeps the leading rows of [days] that fit in [height] px, in order: a day heading takes
 * [headingHeight] and needs at least one row under it, every row takes [rowHeight]. The
 * first row that does not fit ends the strip, so rows are dropped, never clipped.
 */
internal fun fitAgenda(
    days: List<AgendaDay>,
    height: Float,
    headingHeight: Float,
    rowHeight: Float,
): List<AgendaDay> {
    var used = 0f
    var full = false
    val result = mutableListOf<AgendaDay>()
    for (day in days) {
        if (full || used + headingHeight + rowHeight > height) break
        used += headingHeight
        val fitting = minOf(day.rows.size, ((height - used) / rowHeight).toInt())
        used += fitting * rowHeight
        result += AgendaDay(day.date, day.rows.take(fitting))
        full = fitting < day.rows.size
    }
    return result
}
