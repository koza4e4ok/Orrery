package me.kozakov.orrery.compose

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How dense a [DefaultDay] cell is laid out when rendered with a
 * [CalendarStyle].
 *
 * [minCellHeight] is the minimum height of each day cell and
 * [showSecondaryLabel] whether the secondary [me.kozakov.orrery.core.DayInfo.label]
 * (lunar date, holiday name, ...) is drawn under the day number.
 */
public enum class CalendarDensity(
    public val minCellHeight: Dp,
    public val showSecondaryLabel: Boolean,
) {
    /** 36.dp min cell height with secondary labels hidden, for sidebars and widgets. */
    Compact(minCellHeight = 36.dp, showSecondaryLabel = false),

    /** 48.dp min cell height (the plain [DefaultDay] default) with secondary labels shown. */
    Comfortable(minCellHeight = 48.dp, showSecondaryLabel = true),

    /** 56.dp min cell height with secondary labels shown, for tablet and desktop grids. */
    Expanded(minCellHeight = 56.dp, showSecondaryLabel = true),
}

/**
 * How [DefaultDay] draws the start and end of a completed multi-day range,
 * set through [CalendarDayShapes.rangeEndpointStyle].
 *
 * The split styles model hotel and rental stays, where guests check in in
 * the afternoon (PM) and check out in the morning (AM): the range start
 * fills only the half of the cell facing the next day and the range end
 * only the half facing the previous day, both in
 * [CalendarDayColors.inRangeContainerColor] so the stay reads as one
 * continuous band. On a turnover day (passed to the [DefaultDay] overload
 * with `turnoverDates`), one stay checks out in the morning and the next
 * checks in in the afternoon, so both halves are filled with a 2.dp gap
 * between them. Halves follow the layout direction: in RTL the range start
 * fills the left half.
 */
public enum class RangeEndpointStyle {
    /** Range endpoints are filled with [CalendarDayShapes.selectedShape] (the default). */
    FullCell,

    /** The range start fills the right (PM) half and the range end the left (AM) half. */
    SplitHorizontal,

    /** The range start fills the bottom-right triangle and the range end the top-left triangle. */
    SplitDiagonal,
}

/**
 * A coordinated look for [DefaultDay]: [colors], [shapes] and [density]
 * plus an optional cell border and selected-day elevation.
 *
 * A positive [cellBorderWidth] outlines every visible day's
 * [CalendarDayShapes.dayShape] in [cellBorderColor]; a positive
 * [selectedElevation] draws a shadow under the selected day's fill.
 *
 * Build one with [CalendarDefaults.style] or a curated preset such as
 * [CalendarDefaults.roundedSquareStyle], [CalendarDefaults.segmentedRangeStyle],
 * [CalendarDefaults.borderedCardStyle] or [CalendarDefaults.hospitalityStyle],
 * and pass it to the [DefaultDay] overload that takes a `style`.
 */
@Immutable
public class CalendarStyle(
    public val colors: CalendarDayColors,
    public val shapes: CalendarDayShapes,
    public val density: CalendarDensity = CalendarDensity.Comfortable,
    public val cellBorderWidth: Dp = 0.dp,
    public val cellBorderColor: Color = Color.Transparent,
    public val selectedElevation: Dp = 0.dp,
)
