package me.kozakov.orrery.widget

import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.ExperimentalOrreryApi

/** Largest [CalendarSnapshotRange.Agenda.days]: one leap year. */
internal const val MAX_AGENDA_DAYS = 366

/**
 * What a calendar snapshot draws: one month, one week or an agenda strip.
 *
 * New kinds may be added in any release; do not rely on an exhaustive `when` over
 * this type across versions.
 */
@ExperimentalOrreryApi
public sealed interface CalendarSnapshotRange {
    /** The whole of [month] as a grid. */
    @ExperimentalOrreryApi
    public class Month(
        /** The month to draw. */
        public val month: YearMonth,
    ) : CalendarSnapshotRange {
        override fun equals(other: Any?): Boolean = other is Month && other.month == month

        override fun hashCode(): Int = month.hashCode()

        override fun toString(): String = "Month(month=$month)"
    }

    /** The week that contains [date], starting on the snapshot's first day of the week. */
    @ExperimentalOrreryApi
    public class Week(
        /** Any date inside the week to draw. */
        public val date: LocalDate,
    ) : CalendarSnapshotRange {
        override fun equals(other: Any?): Boolean = other is Week && other.date == date

        override fun hashCode(): Int = date.hashCode()

        override fun toString(): String = "Week(date=$date)"
    }

    /**
     * Events grouped by day from [start] for [days] days, at most [maxRows] event rows.
     * [days] is at most 366, which bounds the work of one widget update.
     *
     * @throws IllegalArgumentException if [days] is not in `1..366` or [maxRows] is negative.
     */
    @ExperimentalOrreryApi
    public class Agenda(
        /** First day of the strip. */
        public val start: LocalDate,
        /** Number of days from [start], 1 to 366. */
        public val days: Int = 7,
        /** Maximum number of event rows; rows that do not fit are dropped. */
        public val maxRows: Int = 4,
    ) : CalendarSnapshotRange {
        init {
            require(days in 1..MAX_AGENDA_DAYS) { "days must be in 1..$MAX_AGENDA_DAYS, was $days" }
            require(maxRows >= 0) { "maxRows must not be negative, was $maxRows" }
        }

        override fun equals(other: Any?): Boolean =
            other is Agenda && other.start == start && other.days == days && other.maxRows == maxRows

        override fun hashCode(): Int = (start.hashCode() * 31 + days) * 31 + maxRows

        override fun toString(): String = "Agenda(start=$start, days=$days, maxRows=$maxRows)"
    }
}
