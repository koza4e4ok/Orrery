package me.kozakov.orrery.core

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/**
 * How taps on days change the [Selection], as applied by [SelectionEngine].
 *
 * - [None]: taps never change the selection.
 * - [Single]: one selected day.
 * - [Range]: a start and an end day.
 * - [Multi]: a toggled set of days.
 * - [Week]: the whole week containing the tapped day.
 * - [Month]: the whole month containing the tapped day.
 *
 * New subtypes may be added in a minor release: do not rely on an exhaustive `when` over
 * this type across versions; add an `else` branch.
 */
public sealed interface SelectionMode {
    /** Selection is off: taps never change the selection and fire no event. */
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
    public class Single(
        public val autoAdvance: Boolean = false,
        public val autoSelectDay: AutoSelectDay = AutoSelectDay.FirstDayOfMonth,
        public val allowClear: Boolean = false,
    ) : SelectionMode {
        /** Returns a copy of this mode with the given properties replaced. */
        public fun copy(
            autoAdvance: Boolean = this.autoAdvance,
            autoSelectDay: AutoSelectDay = this.autoSelectDay,
            allowClear: Boolean = this.allowClear,
        ): Single =
            Single(
                autoAdvance = autoAdvance,
                autoSelectDay = autoSelectDay,
                allowClear = allowClear,
            )

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Single) return false
            return autoAdvance == other.autoAdvance &&
                autoSelectDay == other.autoSelectDay &&
                allowClear == other.allowClear
        }

        override fun hashCode(): Int {
            var result = autoAdvance.hashCode()
            result = 31 * result + autoSelectDay.hashCode()
            result = 31 * result + allowClear.hashCode()
            return result
        }

        override fun toString(): String =
            "Single(autoAdvance=$autoAdvance, autoSelectDay=$autoSelectDay, allowClear=$allowClear)"
    }

    /**
     * Start/end range. Day counts are inclusive: a range of one day has
     * size 1. Values that are null or <= 0 mean unbounded; if min > max,
     * both are treated as min (original setSelectRange semantics).
     *
     * A tap that would complete a range shorter than [effectiveMin] or longer than
     * [effectiveMax] keeps the start and fires [SelectionEvent.RangeTooShort] or
     * [SelectionEvent.RangeTooLong].
     *
     * @property minDays Minimum range length in days, inclusive; `null` or `<= 0` means no minimum.
     * @property maxDays Maximum range length in days, inclusive; `null` or `<= 0` means no maximum.
     */
    public class Range(
        public val minDays: Int? = null,
        public val maxDays: Int? = null,
    ) : SelectionMode {
        /** [minDays] as the engine applies it: `null` when there is no minimum (`null` or `<= 0`). */
        public val effectiveMin: Int? = minDays?.takeIf { it > 0 }
        /**
         * [maxDays] as the engine applies it: `null` when there is no maximum (`null` or `<= 0`),
         * and raised to [effectiveMin] when the minimum is larger.
         */
        public val effectiveMax: Int? =
            maxDays?.takeIf { it > 0 }?.let { max ->
                val min = minDays?.takeIf { it > 0 }
                if (min != null && min > max) min else max
            }

        /** Returns a copy of this mode with the given properties replaced. */
        public fun copy(
            minDays: Int? = this.minDays,
            maxDays: Int? = this.maxDays,
        ): Range =
            Range(
                minDays = minDays,
                maxDays = maxDays,
            )

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Range) return false
            return minDays == other.minDays && maxDays == other.maxDays
        }

        override fun hashCode(): Int = 31 * minDays.hashCode() + maxDays.hashCode()

        override fun toString(): String = "Range(minDays=$minDays, maxDays=$maxDays)"
    }

    /**
     * Toggle set of days, optionally capped at [maxCount].
     *
     * A tap on a selected day removes it; a tap that would add a day beyond [maxCount] is
     * ignored and fires [SelectionEvent.TooManyDays]. `null` (the default) means no cap.
     */
    public class Multi(
        public val maxCount: Int? = null,
    ) : SelectionMode {
        /** Returns a copy of this mode with the given properties replaced. */
        public fun copy(maxCount: Int? = this.maxCount): Multi = Multi(maxCount = maxCount)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Multi) return false
            return maxCount == other.maxCount
        }

        override fun hashCode(): Int = maxCount.hashCode()

        override fun toString(): String = "Multi(maxCount=$maxCount)"
    }

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
    public class Week(
        public val firstDayOfWeek: DayOfWeek? = null,
        public val allowClear: Boolean = true,
    ) : SelectionMode {
        /** Returns a copy of this mode with the given properties replaced. */
        public fun copy(
            firstDayOfWeek: DayOfWeek? = this.firstDayOfWeek,
            allowClear: Boolean = this.allowClear,
        ): Week =
            Week(
                firstDayOfWeek = firstDayOfWeek,
                allowClear = allowClear,
            )

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Week) return false
            return firstDayOfWeek == other.firstDayOfWeek && allowClear == other.allowClear
        }

        override fun hashCode(): Int = 31 * firstDayOfWeek.hashCode() + allowClear.hashCode()

        override fun toString(): String = "Week(firstDayOfWeek=$firstDayOfWeek, allowClear=$allowClear)"
    }

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
    public class Month(
        public val allowClear: Boolean = true,
    ) : SelectionMode {
        /** Returns a copy of this mode with the given properties replaced. */
        public fun copy(allowClear: Boolean = this.allowClear): Month = Month(allowClear = allowClear)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Month) return false
            return allowClear == other.allowClear
        }

        override fun hashCode(): Int = allowClear.hashCode()

        override fun toString(): String = "Month(allowClear=$allowClear)"
    }
}

/**
 * Policy for which day gets selected when auto-advance paging changes month
 * ([SelectionMode.Single.autoAdvance]); [autoSelectDate] applies it.
 *
 * New entries may be added in a minor release: do not rely on an exhaustive `when` over
 * this type across versions; add an `else` branch.
 */
public enum class AutoSelectDay {
    /** Today when the new month is the current month, otherwise its first day. */
    FirstDayOfMonth,

    /**
     * Today when the new month is the current month; otherwise the previously selected
     * day of month (clamped to the month's length), or the first day when nothing was selected.
     */
    LastSelectedDay,

    /**
     * The previously selected day of month (clamped to the month's length), even in the
     * current month; the first day when nothing was selected.
     */
    LastSelectedDayIgnoringCurrent,
}

/**
 * The selected days. Which fields are used depends on the [SelectionMode]: [single] in
 * [SelectionMode.Single]; [rangeStart] and [rangeEnd] in [SelectionMode.Range],
 * [SelectionMode.Week] and [SelectionMode.Month]; [multi] in [SelectionMode.Multi]. The
 * other fields stay at their defaults.
 *
 * @property single The selected day, or null when none is selected.
 * @property rangeStart First day of the range, or null when no range is started.
 * @property rangeEnd Last day of the range (inclusive), or null while only the start is
 *   picked.
 * @property multi The selected days of a multi selection.
 */
public class Selection(
    public val single: LocalDate? = null,
    public val rangeStart: LocalDate? = null,
    public val rangeEnd: LocalDate? = null,
    public val multi: Set<LocalDate> = emptySet(),
) {
    /** [rangeStart]..[rangeEnd] when both are set, otherwise null. */
    public val range: ClosedRange<LocalDate>?
        get() = if (rangeStart != null && rangeEnd != null) rangeStart..rangeEnd else null

    /** Returns a copy of this selection with the given properties replaced. */
    public fun copy(
        single: LocalDate? = this.single,
        rangeStart: LocalDate? = this.rangeStart,
        rangeEnd: LocalDate? = this.rangeEnd,
        multi: Set<LocalDate> = this.multi,
    ): Selection =
        Selection(
            single = single,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            multi = multi,
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Selection) return false
        return single == other.single &&
            rangeStart == other.rangeStart &&
            rangeEnd == other.rangeEnd &&
            multi == other.multi
    }

    override fun hashCode(): Int {
        var result = single.hashCode()
        result = 31 * result + rangeStart.hashCode()
        result = 31 * result + rangeEnd.hashCode()
        result = 31 * result + multi.hashCode()
        return result
    }

    override fun toString(): String =
        "Selection(single=$single, rangeStart=$rangeStart, rangeEnd=$rangeEnd, multi=$multi)"

    /** Holds [Empty]. */
    public companion object {
        /** Nothing selected. */
        public val Empty: Selection = Selection()
    }
}

/**
 * A rejected or restricted selection change, reported in [SelectionResult.events] by
 * [SelectionEngine.click] and [SelectionEngine.replace].
 *
 * New subtypes may be added in a minor release: do not rely on an exhaustive `when` over
 * this type across versions; add an `else` branch.
 */
public sealed interface SelectionEvent {
    /** The day that caused the event. */
    public val date: LocalDate

    /** [date] is outside the selectable bounds; the selection is unchanged. */
    public data class OutOfRange(
        override val date: LocalDate,
    ) : SelectionEvent

    /**
     * [date] is disabled (a tapped day, a disabled day inside a range, week or month, or a
     * disabled day of a replaced selection).
     */
    public data class Intercepted(
        override val date: LocalDate,
    ) : SelectionEvent

    /**
     * A range ending at [date] would be shorter than [minDays].
     *
     * @property minDays The minimum range length in days, inclusive, that was violated.
     */
    public data class RangeTooShort(
        override val date: LocalDate,
        val minDays: Int,
    ) : SelectionEvent

    /**
     * A range ending at [date] would be longer than [maxDays].
     *
     * @property maxDays The maximum range length in days, inclusive, that was violated.
     */
    public data class RangeTooLong(
        override val date: LocalDate,
        val maxDays: Int,
    ) : SelectionEvent

    /**
     * The selection would hold more than [maxCount] days in [SelectionMode.Multi]. [date] is
     * the tapped day, or the latest day of a replaced selection.
     *
     * @property maxCount The [SelectionMode.Multi.maxCount] that was reached.
     */
    public data class TooManyDays(
        override val date: LocalDate,
        val maxCount: Int,
    ) : SelectionEvent
}

/**
 * Outcome of [SelectionEngine.click] or [SelectionEngine.replace].
 *
 * @property selection The selection after the call. A rejected change usually keeps the
 *   previous selection; some range guardrails restart the range at the tapped date instead
 *   (see [SelectionEngine]).
 * @property events What was rejected or restricted, in order; empty when the change applied
 *   cleanly.
 */
public class SelectionResult(
    public val selection: Selection,
    public val events: List<SelectionEvent>,
) {
    /** Returns a copy of this result with the given properties replaced. */
    public fun copy(
        selection: Selection = this.selection,
        events: List<SelectionEvent> = this.events,
    ): SelectionResult =
        SelectionResult(
            selection = selection,
            events = events,
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SelectionResult) return false
        return selection == other.selection && events == other.events
    }

    override fun hashCode(): Int = 31 * selection.hashCode() + events.hashCode()

    override fun toString(): String = "SelectionResult(selection=$selection, events=$events)"
}
