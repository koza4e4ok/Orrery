package me.kozakov.orrery.compose

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp

/** How [OrreryDateRangePickerDialog] arranges its calendar. */
public sealed interface DateRangeCalendarLayoutMode {
    /** One vertically scrolling column of months in a full-screen dialog. */
    public data object SingleMonth : DateRangeCalendarLayoutMode

    /**
     * Two consecutive months side by side in a centered card, regardless of
     * the available width. On a narrow window the day cells shrink to fit.
     */
    public data object DualMonth : DateRangeCalendarLayoutMode

    /**
     * [DualMonth] when the dialog's window is at least [minDualMonthWidth]
     * wide, else [SingleMonth]. The layout follows the window as it is
     * resized or rotated.
     */
    @Immutable
    public class Adaptive(
        public val minDualMonthWidth: Dp = DatePickerDefaults.MinDualMonthWidth,
    ) : DateRangeCalendarLayoutMode {
        override fun equals(other: Any?): Boolean =
            this === other || (other is Adaptive && minDualMonthWidth == other.minDualMonthWidth)

        override fun hashCode(): Int = minDualMonthWidth.hashCode()

        override fun toString(): String = "Adaptive(minDualMonthWidth=$minDualMonthWidth)"
    }
}
