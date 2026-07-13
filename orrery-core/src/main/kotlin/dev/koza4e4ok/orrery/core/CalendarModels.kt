package dev.koza4e4ok.orrery.core

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

public data class CalendarDay(
    val date: LocalDate,
    val position: DayPosition,
)

public data class CalendarWeek(
    val days: List<CalendarDay>,
)

public data class CalendarMonth(
    val yearMonth: YearMonth,
    val weeks: List<CalendarWeek>,
)

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
