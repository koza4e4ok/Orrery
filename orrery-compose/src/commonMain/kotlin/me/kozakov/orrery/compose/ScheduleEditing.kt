package me.kozakov.orrery.compose

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.EventSpan
import kotlin.math.floor

/** What an in-flight [DaySchedule] / [WeekSchedule] edit is doing. */
public enum class ScheduleDragKind {
    /** A new time range is being dragged out on empty space. */
    Create,

    /** An event is being moved, keeping its duration. */
    Move,

    /** The start edge of an event is being dragged. */
    ResizeStart,

    /** The end edge of an event is being dragged. */
    ResizeEnd,
}

/**
 * What the ghost tile shows while a schedule gesture or keyboard edit is in flight.
 *
 * @property kind The edit in progress.
 * @property event The event being moved or resized; null for [ScheduleDragKind.Create].
 * @property date The day column the ghost is over.
 * @property startTime Snapped start of the previewed range.
 * @property endTime Snapped end of the previewed range. An [endTime] that is not
 *   after [startTime] means 00:00 of the following day.
 */
public class ScheduleDragPreview<out T>(
    public val kind: ScheduleDragKind,
    public val event: CalendarEvent<T>?,
    public val date: LocalDate,
    public val startTime: LocalTime,
    public val endTime: LocalTime,
) {
    override fun equals(other: Any?): Boolean =
        other is ScheduleDragPreview<*> &&
            kind == other.kind &&
            event == other.event &&
            date == other.date &&
            startTime == other.startTime &&
            endTime == other.endTime

    override fun hashCode(): Int {
        var result = kind.hashCode()
        result = 31 * result + (event?.hashCode() ?: 0)
        result = 31 * result + date.hashCode()
        result = 31 * result + startTime.hashCode()
        result = 31 * result + endTime.hashCode()
        return result
    }

    override fun toString(): String =
        "ScheduleDragPreview(kind=$kind, event=${event?.id}, date=$date, startTime=$startTime, endTime=$endTime)"
}

/** A `start..end` range in minutes from midnight; [end] may be 1440. */
internal class MinuteRange(
    val start: Int,
    val end: Int,
) {
    val duration: Int get() = end - start

    override fun equals(other: Any?): Boolean = other is MinuteRange && start == other.start && end == other.end

    override fun hashCode(): Int = 31 * start + end

    override fun toString(): String = "MinuteRange($start..$end)"
}

internal const val EDIT_MINUTES_PER_DAY: Int = 24 * 60

/** Length of an event created by a long press that is released without dragging. */
internal const val DEFAULT_CREATE_DURATION_MINUTES: Int = 60

/** Whether [snapMinutes] divides the hour, so the snapping grid stays aligned with the hour lines. */
internal fun isValidSnapMinutes(snapMinutes: Int): Boolean = snapMinutes in 1..60 && 60 % snapMinutes == 0

/** The unsnapped minute at [yPx] in the time grid, clamped to the visible [window]. */
internal fun minuteAt(
    yPx: Float,
    hourPx: Float,
    window: HourWindow,
): Int = (window.startMinute + floor(yPx / hourPx * 60f).toInt()).coerceIn(window.startMinute, window.endMinute)

internal fun snapNearest(
    minute: Int,
    snap: Int,
): Int = (minute + snap / 2) / snap * snap

internal fun snapFloor(
    minute: Int,
    snap: Int,
): Int = minute / snap * snap

internal fun snapCeil(
    minute: Int,
    snap: Int,
): Int = (minute + snap - 1) / snap * snap

/**
 * Create: the earlier of [anchorMinute] and [pointerMinute] floors to the grid, the
 * later one rounds up; the range is at least one [snap] step and stays in [min]..[max].
 */
internal fun createRange(
    anchorMinute: Int,
    pointerMinute: Int,
    snap: Int,
    min: Int,
    max: Int,
): MinuteRange {
    var start = snapFloor(minOf(anchorMinute, pointerMinute), snap).coerceIn(min, max)
    var end = snapCeil(maxOf(anchorMinute, pointerMinute), snap).coerceIn(min, max)
    if (end - start < snap) {
        end = start + snap
        if (end > max) {
            end = max
            start = (max - snap).coerceAtLeast(min)
        }
    }
    return MinuteRange(start, end)
}

/** Create without a drag: [DEFAULT_CREATE_DURATION_MINUTES] from the floored [anchorMinute], cut at [max]. */
internal fun defaultCreateRange(
    anchorMinute: Int,
    snap: Int,
    min: Int,
    max: Int,
): MinuteRange {
    val start = snapFloor(anchorMinute, snap).coerceAtMost(max - snap).coerceAtLeast(min)
    return MinuteRange(start, minOf(start + DEFAULT_CREATE_DURATION_MINUTES, max))
}

/**
 * Move: the start snaps to the nearest grid step, the duration is kept, and the
 * range is shifted (never shrunk) to stay inside [min]..[max]. A zero
 * [deltaMinutes] returns the range unchanged, even off the grid.
 */
@Suppress("LongParameterList")
internal fun moveRange(
    start: Int,
    end: Int,
    deltaMinutes: Int,
    snap: Int,
    min: Int,
    max: Int,
): MinuteRange {
    if (deltaMinutes == 0) return MinuteRange(start, end)
    val duration = end - start
    val latestStart = (max - duration).coerceAtLeast(min)
    val newStart = snapNearest((start + deltaMinutes).coerceAtLeast(0), snap).coerceIn(min, latestStart)
    return MinuteRange(newStart, newStart + duration)
}

/** Resize from the top: the start snaps and stays in [min]..(end - [minDuration]). */
@Suppress("LongParameterList")
internal fun resizeStart(
    start: Int,
    end: Int,
    pointerMinute: Int,
    snap: Int,
    minDuration: Int,
    min: Int,
): MinuteRange {
    val latest = (end - minDuration).coerceAtLeast(min)
    val newStart = snapNearest(pointerMinute.coerceAtLeast(0), snap).coerceIn(min, latest)
    return MinuteRange(newStart, end)
}

/** Resize from the bottom: the end snaps and stays in (start + [minDuration])..[max]. */
@Suppress("LongParameterList")
internal fun resizeEnd(
    start: Int,
    end: Int,
    pointerMinute: Int,
    snap: Int,
    minDuration: Int,
    max: Int,
): MinuteRange {
    val earliest = (start + minDuration).coerceAtMost(max)
    val newEnd = snapNearest(pointerMinute.coerceAtLeast(0), snap).coerceIn(earliest, max)
    return MinuteRange(start, newEnd)
}

/** Minutes from midnight as a [LocalTime]; 1440 (the end of the day) becomes 00:00. */
internal fun minuteToLocalTime(minute: Int): LocalTime = LocalTime((minute / 60) % 24, minute % 60)

/** Start and end minute of a previewed range; an end that is not after the start is the end of the day. */
internal fun previewMinutes(preview: ScheduleDragPreview<*>): MinuteRange {
    val start = preview.startTime.hour * 60 + preview.startTime.minute
    val end = preview.endTime.hour * 60 + preview.endTime.minute
    return MinuteRange(start, if (end <= start) EDIT_MINUTES_PER_DAY else end)
}

/**
 * The real start and end minute of an event that can be moved or resized:
 * a timed event within one day (an end at 00:00 of the next day counts as 24:00).
 * Null for all-day and multi-day events.
 */
internal fun editableMinutes(event: CalendarEvent<*>): MinuteRange? {
    val span = event.span as? EventSpan.Timed ?: return null
    val start = span.start.hour * 60 + span.start.minute
    val endOfDay = span.end.hour == 0 && span.end.minute == 0 && span.end.second == 0 && span.end.nanosecond == 0
    val end =
        when {
            span.end.date == span.start.date -> span.end.hour * 60 + span.end.minute
            endOfDay && span.end.date == span.start.date.plus(1, DateTimeUnit.DAY) -> EDIT_MINUTES_PER_DAY
            else -> return null
        }
    return if (end < start) null else MinuteRange(start, end)
}

/** Fills the `%1$s`, `%2$s` placeholders of a raw string template. */
internal fun fillTemplate(
    template: String,
    vararg args: String,
): String {
    var out = template
    args.forEachIndexed { index, arg -> out = out.replace("%${index + 1}\$s", arg) }
    return out
}
