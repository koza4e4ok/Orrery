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
     *
     * [allowClear] = true lets a tap on the already-selected day clear the
     * selection back to [Selection.Empty]. With false (the default, and the
     * behavior of every release before it existed) the selected day stays
     * selected, so the selection never becomes empty through taps: use it
     * for pickers and booking flows that always need a date.
     */
    public data class Single(
        val autoAdvance: Boolean = false,
        val autoSelectDay: AutoSelectDay = AutoSelectDay.FirstDayOfMonth,
        val allowClear: Boolean = false,
    ) : SelectionMode {
        @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
        public constructor(
            autoAdvance: Boolean = false,
            autoSelectDay: AutoSelectDay = AutoSelectDay.FirstDayOfMonth,
        ) : this(autoAdvance, autoSelectDay, false)

        @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
        public fun copy(
            autoAdvance: Boolean = this.autoAdvance,
            autoSelectDay: AutoSelectDay = this.autoSelectDay,
        ): Single = Single(autoAdvance, autoSelectDay, allowClear)
    }

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
     * The week is clamped to the engine bounds and the click is rejected
     * with [SelectionEvent.Intercepted] when any day in it is disabled.
     * Tapping a day inside the selected week clears the selection when
     * [allowClear] is true.
     *
     * [firstDayOfWeek] = null (the default) follows the calendar's default:
     * `rememberCalendarSelectionState` in orrery-compose resolves it to the
     * locale's first day of week, the same default `rememberCalendarState`
     * uses, so the selected week matches the visible row. A bare
     * [SelectionEngine] resolves null to [DayOfWeek.MONDAY]. Pass an explicit
     * value when the calendar overrides its own `firstDayOfWeek`.
     *
     * Programmatic writes ([SelectionEngine.replace], `CalendarSelectionState.set`
     * and `initialSelection`) are snapped to the whole week containing
     * [Selection.rangeStart] (clamped to the bounds), then validated like a tap;
     * [Selection.rangeEnd] of the proposal is ignored.
     */
    public data class Week(
        val firstDayOfWeek: DayOfWeek? = null,
        val allowClear: Boolean = true,
    ) : SelectionMode

    /**
     * Tapping any day selects its whole month (first..last day) as a range,
     * clamped to the engine bounds. The click is rejected with
     * [SelectionEvent.Intercepted] when any day in it is disabled. Tapping a
     * day inside the selected month clears the selection when [allowClear]
     * is true.
     *
     * Programmatic writes ([SelectionEngine.replace], `CalendarSelectionState.set`
     * and `initialSelection`) are snapped to the whole month containing
     * [Selection.rangeStart] (clamped to the bounds), then validated like a tap;
     * [Selection.rangeEnd] of the proposal is ignored.
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
