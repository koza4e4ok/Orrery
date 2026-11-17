package me.kozakov.orrery.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/** Where a day cell falls relative to the month being rendered. */
public enum class DayPosition {
    /** Belongs to the previous month, fills leading cells of the first row. */
    InDate,

    /** Belongs to the rendered month. */
    MonthDate,

    /** Belongs to the next month, fills trailing cells. */
    OutDate,
}

public class CalendarDay(
    public val date: LocalDate,
    public val position: DayPosition,
) {
    /** Returns a copy of this day with the given properties replaced. */
    public fun copy(
        date: LocalDate = this.date,
        position: DayPosition = this.position,
    ): CalendarDay =
        CalendarDay(
            date = date,
            position = position,
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CalendarDay) return false
        return date == other.date && position == other.position
    }

    override fun hashCode(): Int = 31 * date.hashCode() + position.hashCode()

    override fun toString(): String = "CalendarDay(date=$date, position=$position)"
}

public class CalendarWeek(
    public val days: List<CalendarDay>,
) {
    /** Returns a copy of this week with the given properties replaced. */
    public fun copy(days: List<CalendarDay> = this.days): CalendarWeek = CalendarWeek(days = days)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CalendarWeek) return false
        return days == other.days
    }

    override fun hashCode(): Int = days.hashCode()

    override fun toString(): String = "CalendarWeek(days=$days)"
}

public class CalendarMonth(
    public val yearMonth: YearMonth,
    public val weeks: List<CalendarWeek>,
) {
    /** Returns a copy of this month with the given properties replaced. */
    public fun copy(
        yearMonth: YearMonth = this.yearMonth,
        weeks: List<CalendarWeek> = this.weeks,
    ): CalendarMonth =
        CalendarMonth(
            yearMonth = yearMonth,
            weeks = weeks,
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CalendarMonth) return false
        return yearMonth == other.yearMonth && weeks == other.weeks
    }

    override fun hashCode(): Int = 31 * yearMonth.hashCode() + weeks.hashCode()

    override fun toString(): String = "CalendarMonth(yearMonth=$yearMonth, weeks=$weeks)"
}

/**
 * Replaces the original library's `month_view_show_mode`.
 * [EndOfRow] = mode_all, [None] = mode_only_current (grid identical to
 * EndOfRow; renderers leave in/out cells empty), [EndOfGrid] = mode_fix (6 rows).
 */
public enum class OutDateStyle {
    EndOfRow,
    None,
    EndOfGrid,
}
