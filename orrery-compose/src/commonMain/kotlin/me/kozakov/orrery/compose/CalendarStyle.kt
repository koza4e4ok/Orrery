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
 * A coordinated look for [DefaultDay]: [colors], [shapes] and [density]
 * plus an optional cell border and selected-day elevation.
 *
 * A positive [cellBorderWidth] outlines every visible day's
 * [CalendarDayShapes.dayShape] in [cellBorderColor]; a positive
 * [selectedElevation] draws a shadow under the selected day's fill.
 *
 * Build one with [CalendarDefaults.style] or a curated preset such as
 * [CalendarDefaults.roundedSquareStyle], [CalendarDefaults.segmentedRangeStyle]
 * or [CalendarDefaults.borderedCardStyle], and pass it to the [DefaultDay]
 * overload that takes a `style`.
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
