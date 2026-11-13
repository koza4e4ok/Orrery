package me.kozakov.orrery.compose

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp

/** How [OrreryDateRangePickerDialog] arranges its calendar. */
public sealed interface DateRangeCalendarLayoutMode {
    /** The 0.9 layout: one vertically scrolling column of months. */
    public data object SingleMonth : DateRangeCalendarLayoutMode

    /** Two consecutive months side by side regardless of the available width. */
    public data object DualMonth : DateRangeCalendarLayoutMode

    /**
     * [DualMonth] when the dialog window's available width is at least
     * [minDualMonthWidth], else [SingleMonth]. Resolved live, so rotating or
     * resizing the window switches the layout while keeping the selection
     * and the visible month.
     */
    @Immutable
    public class Adaptive(
        public val minDualMonthWidth: Dp = DatePickerDefaults.MinDualMonthWidth,
    ) : DateRangeCalendarLayoutMode {
        override fun equals(other: Any?): Boolean = other is Adaptive && other.minDualMonthWidth == minDualMonthWidth

        override fun hashCode(): Int = minDualMonthWidth.hashCode()

        override fun toString(): String = "Adaptive(minDualMonthWidth=$minDualMonthWidth)"
    }
}

/** Whether [this] mode shows two months for a dialog window [availableWidth] wide. */
internal fun DateRangeCalendarLayoutMode.isDualMonth(availableWidth: Dp): Boolean =
    when (this) {
        DateRangeCalendarLayoutMode.SingleMonth -> false
        DateRangeCalendarLayoutMode.DualMonth -> true
        is DateRangeCalendarLayoutMode.Adaptive -> availableWidth >= minDualMonthWidth
    }
