package me.kozakov.orrery.core

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

public sealed interface SelectionMode {
    public data object None : SelectionMode

    /**
     * One selected day. [autoAdvance] = true reproduces the original
     * "default" mode where paging months auto-selects a day (see
     * [autoSelectDay]); false reproduces the original "single" mode where
     * selection changes only on click.
     */
    public data class Single(
        val autoAdvance: Boolean = false,
        val autoSelectDay: AutoSelectDay = AutoSelectDay.FirstDayOfMonth,
    ) : SelectionMode

    /**
     * Start/end range. Day counts are inclusive: a range of one day has
     * size 1. Values that are null or <= 0 mean unbounded; if min > max,
     * both are treated as min (original setSelectRange semantics).
     */
    public data class Range(
        val minDays: Int? = null,
        val maxDays: Int? = null,
    ) : SelectionMode {
        public val effectiveMin: Int? = minDays?.takeIf { it > 0 }
        public val effectiveMax: Int? =
            maxDays?.takeIf { it > 0 }?.let { max ->
                val min = minDays?.takeIf { it > 0 }
                if (min != null && min > max) min else max
            }
    }

    /** Toggle set of days, optionally capped at [maxCount]. */
    public data class Multi(
        val maxCount: Int? = null,
    ) : SelectionMode

    /**
     * Tapping any day selects the whole 7-day week containing it, starting
     * at [firstDayOfWeek], as a range ([Selection.rangeStart]..[Selection.rangeEnd]).
     * Pass the same [firstDayOfWeek] as the calendar so the selected week
     * matches the visible row. The week is clamped to the engine bounds and
     * the click is rejected with [SelectionEvent.Intercepted] when any day
     * in it is disabled. Tapping a day inside the selected week clears the
     * selection when [allowClear] is true.
     */
    public data class Week(
        val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
        val allowClear: Boolean = true,
    ) : SelectionMode

    /**
     * Tapping any day selects its whole month (first..last day) as a range,
     * clamped to the engine bounds. The click is rejected with
     * [SelectionEvent.Intercepted] when any day in it is disabled. Tapping a
     * day inside the selected month clears the selection when [allowClear]
     * is true.
     */
    public data class Month(
        val allowClear: Boolean = true,
    ) : SelectionMode
}

/** Policy for which day gets selected when auto-advance paging changes month. */
public enum class AutoSelectDay {
    FirstDayOfMonth,
    LastSelectedDay,
    LastSelectedDayIgnoringCurrent,
}

public data class Selection(
    val single: LocalDate? = null,
    val rangeStart: LocalDate? = null,
    val rangeEnd: LocalDate? = null,
    val multi: Set<LocalDate> = emptySet(),
) {
    public val range: ClosedRange<LocalDate>?
        get() = if (rangeStart != null && rangeEnd != null) rangeStart..rangeEnd else null

    public companion object {
        public val Empty: Selection = Selection()
    }
}

public sealed interface SelectionEvent {
    public val date: LocalDate

    public data class OutOfRange(
        override val date: LocalDate,
    ) : SelectionEvent

    public data class Intercepted(
        override val date: LocalDate,
    ) : SelectionEvent

    public data class RangeTooShort(
        override val date: LocalDate,
        val minDays: Int,
    ) : SelectionEvent

    public data class RangeTooLong(
        override val date: LocalDate,
        val maxDays: Int,
    ) : SelectionEvent

    public data class TooManyDays(
        override val date: LocalDate,
        val maxCount: Int,
    ) : SelectionEvent
}

public data class SelectionResult(
    val selection: Selection,
    val events: List<SelectionEvent>,
)
