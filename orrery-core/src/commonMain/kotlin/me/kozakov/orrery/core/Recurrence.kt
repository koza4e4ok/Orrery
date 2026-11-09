package me.kozakov.orrery.core

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/**
 * Recurrence frequency of a [RecurrenceRule] (RFC 5545 `FREQ`).
 *
 * Orrery events are date-granular, so the sub-daily frequencies (`HOURLY`, `MINUTELY`,
 * `SECONDLY`) are not supported. More entries may be added in a later release: use an
 * `else` branch when matching exhaustively.
 */
public enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

/**
 * A weekday with an optional ordinal, as in the RFC 5545 `BYDAY` part: `MO`, `2MO`, `-1FR`.
 *
 * @property dayOfWeek The weekday.
 * @property ordinal `null` for "every [dayOfWeek]" in the period; otherwise the n-th
 *   occurrence (negative values count from the end). Valid values are `-53..-1` and `1..53`;
 *   [RecurrenceRule] narrows the range further by frequency.
 */
public data class ByDay(
    public val dayOfWeek: DayOfWeek,
    public val ordinal: Int? = null,
) {
    init {
        require(ordinal == null || (ordinal != 0 && ordinal in -53..53)) {
            "ByDay ordinal must be in -53..-1 or 1..53, was $ordinal"
        }
    }
}

/**
 * How month-end dates that do not exist (`31 April`, `30 February`, `29 February` in a common
 * year) are treated during expansion.
 *
 * This is not part of RFC 5545 and is never stored in a [RecurrenceRule] or serialised by
 * [RecurrenceRule.toRfc5545String]: pass it to [RecurrenceRule.occurrences],
 * [RecurrenceRule.asSequence], [CalendarEvent.occurrencesIn] or [expandRecurring].
 */
public enum class InvalidDatePolicy {
    /**
     * RFC 5545 section 3.3.10 behaviour (default): invalid dates are skipped and do not
     * consume `COUNT`. `DTSTART=2026-01-31;FREQ=MONTHLY;COUNT=3` gives Jan 31, Mar 31, May 31.
     * Use `BYMONTHDAY=-1` ([RecurrenceRule.monthlyOnLastDay]) for "last day of every month".
     */
    SkipInvalid,

    /**
     * Non-standard convenience: a day number larger than the month length is replaced by the
     * month's last day, and the clamped date consumes `COUNT`.
     * `DTSTART=2026-01-31;FREQ=MONTHLY;COUNT=3` gives Jan 31, Feb 28, Mar 31. It has no effect
     * on `BYDAY` ordinal rules.
     */
    ClampToMonthEnd,
}

/**
 * Identifies an expanded occurrence of a recurring [CalendarEvent].
 *
 * @property seriesId [CalendarEvent.id] of the series master.
 * @property recurrenceId Start date of this occurrence (RFC 5545 `RECURRENCE-ID`, date form).
 */
public class RecurrenceInstance(
    public val seriesId: String,
    public val recurrenceId: LocalDate,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RecurrenceInstance) return false
        return seriesId == other.seriesId && recurrenceId == other.recurrenceId
    }

    override fun hashCode(): Int = 31 * seriesId.hashCode() + recurrenceId.hashCode()

    override fun toString(): String = "RecurrenceInstance(seriesId=$seriesId, recurrenceId=$recurrenceId)"
}
