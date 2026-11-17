package me.kozakov.orrery.compose

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DayOfWeek

internal val ALL_DAYS_OF_WEEK_SET: Set<DayOfWeek> = DayOfWeek.entries.toSet()

/**
 * How week rows in [HorizontalCalendar] and [VerticalCalendar] size their
 * vertical height.
 */
public sealed interface CalendarRowHeight {
    /** Each row wraps its day cells (the default). */
    public data object WrapContent : CalendarRowHeight

    /** Each week row has an exact height of [height]. */
    public class Fixed(
        public val height: Dp,
    ) : CalendarRowHeight {
        /** Returns a copy of this row height with the given properties replaced. */
        public fun copy(height: Dp = this.height): Fixed = Fixed(height = height)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Fixed) return false
            return height == other.height
        }

        override fun hashCode(): Int = height.hashCode()

        override fun toString(): String = "Fixed(height=$height)"
    }

    /**
     * Week rows expand to share the available viewport height, with each row
     * at least [minRowHeight] tall.
     *
     * In [HorizontalCalendar], when bounded by a finite max height (such as
     * `Modifier.fillMaxSize()` or `Modifier.height(...)`), each month page
     * fills that height and divides it evenly across its week rows. When the
     * height constraint is unbounded (for example inside a vertical scroll),
     * rows fall back to [minRowHeight].
     *
     * In [VerticalCalendar], each row uses the larger of [minRowHeight] and
     * one-sixth of the viewport height.
     */
    public class FillViewport(
        public val minRowHeight: Dp = 40.dp,
    ) : CalendarRowHeight {
        /** Returns a copy of this row height with the given properties replaced. */
        public fun copy(minRowHeight: Dp = this.minRowHeight): FillViewport = FillViewport(minRowHeight = minRowHeight)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is FillViewport) return false
            return minRowHeight == other.minRowHeight
        }

        override fun hashCode(): Int = minRowHeight.hashCode()

        override fun toString(): String = "FillViewport(minRowHeight=$minRowHeight)"
    }
}

internal fun Set<DayOfWeek>.toBitMask(): Int {
    var mask = 0
    for (day in this) {
        mask = mask or (1 shl day.ordinal)
    }
    return mask
}

internal fun dayOfWeekSetFromBitMask(mask: Int): Set<DayOfWeek> {
    val set =
        buildSet {
            for (day in DayOfWeek.entries) {
                if ((mask and (1 shl day.ordinal)) != 0) {
                    add(day)
                }
            }
        }
    return set.ifEmpty { ALL_DAYS_OF_WEEK_SET }
}
