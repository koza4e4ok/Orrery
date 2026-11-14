package me.kozakov.orrery.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kozakov.orrery.ExperimentalOrreryApi

/**
 * Sizes of a calendar snapshot. Text sizes are scaled by the output's font scale.
 *
 * Day numbers shrink with the grid cells, down to [minTextScale] times [dayTextSize].
 * When even that does not fit, the title row is dropped first, then the weekday row, so
 * text never overlaps; on the smallest outputs only the day marks remain.
 *
 * @throws IllegalArgumentException if a text size is not in sp, or [minTextScale] is not in `(0, 1]`.
 */
@ExperimentalOrreryApi
public class CalendarSnapshotStyle(
    /** Corner radius of the container background. */
    public val cornerRadius: Dp = 12.dp,
    /** Padding between the container edge and the content. */
    public val contentPadding: Dp = 8.dp,
    /** Text size of the title row. */
    public val titleTextSize: TextUnit = 14.sp,
    /** Text size of the day numbers and agenda event rows. */
    public val dayTextSize: TextUnit = 12.sp,
    /** Text size of the weekday row and agenda day headings. */
    public val weekdayTextSize: TextUnit = 10.sp,
    /** Smallest factor text shrinks to with the cell size before rows are dropped instead. */
    public val minTextScale: Float = 0.6f,
) {
    init {
        require(titleTextSize.isSp && dayTextSize.isSp && weekdayTextSize.isSp) {
            "text sizes must be in sp, were $titleTextSize, $dayTextSize, $weekdayTextSize"
        }
        require(minTextScale > 0f && minTextScale <= 1f) { "minTextScale must be in (0, 1], was $minTextScale" }
    }

    /** Returns a copy of this style with the given fields replaced. */
    public fun copy(
        cornerRadius: Dp = this.cornerRadius,
        contentPadding: Dp = this.contentPadding,
        titleTextSize: TextUnit = this.titleTextSize,
        dayTextSize: TextUnit = this.dayTextSize,
        weekdayTextSize: TextUnit = this.weekdayTextSize,
        minTextScale: Float = this.minTextScale,
    ): CalendarSnapshotStyle =
        CalendarSnapshotStyle(cornerRadius, contentPadding, titleTextSize, dayTextSize, weekdayTextSize, minTextScale)

    override fun equals(other: Any?): Boolean =
        other is CalendarSnapshotStyle &&
            cornerRadius == other.cornerRadius &&
            contentPadding == other.contentPadding &&
            titleTextSize == other.titleTextSize &&
            dayTextSize == other.dayTextSize &&
            weekdayTextSize == other.weekdayTextSize &&
            minTextScale == other.minTextScale

    override fun hashCode(): Int {
        var result = cornerRadius.hashCode()
        result = 31 * result + contentPadding.hashCode()
        result = 31 * result + titleTextSize.hashCode()
        result = 31 * result + dayTextSize.hashCode()
        result = 31 * result + weekdayTextSize.hashCode()
        result = 31 * result + minTextScale.hashCode()
        return result
    }

    override fun toString(): String =
        "CalendarSnapshotStyle(cornerRadius=$cornerRadius, contentPadding=$contentPadding, " +
            "titleTextSize=$titleTextSize, dayTextSize=$dayTextSize, weekdayTextSize=$weekdayTextSize, " +
            "minTextScale=$minTextScale)"
}
