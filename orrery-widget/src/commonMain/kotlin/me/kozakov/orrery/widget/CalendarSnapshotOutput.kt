package me.kozakov.orrery.widget

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import me.kozakov.orrery.ExperimentalOrreryApi

/** Largest accepted [CalendarSnapshotOutput.widthPx] and [CalendarSnapshotOutput.heightPx]. */
internal const val MAX_SNAPSHOT_SIDE_PX: Int = 4096

/** Largest accepted pixel count, about 16 MB at ARGB_8888. */
internal const val MAX_SNAPSHOT_AREA_PX: Int = 4_194_304

/**
 * Throws [IllegalArgumentException], naming the values, unless both sides are in
 * `1..MAX_SNAPSHOT_SIDE_PX` and the area is at most [MAX_SNAPSHOT_AREA_PX].
 */
internal fun checkSnapshotSize(
    widthPx: Int,
    heightPx: Int,
) {
    require(widthPx in 1..MAX_SNAPSHOT_SIDE_PX && heightPx in 1..MAX_SNAPSHOT_SIDE_PX) {
        "widthPx and heightPx must be in 1..$MAX_SNAPSHOT_SIDE_PX, were $widthPx x $heightPx"
    }
    require(widthPx.toLong() * heightPx <= MAX_SNAPSHOT_AREA_PX) {
        "widthPx x heightPx must be at most $MAX_SNAPSHOT_AREA_PX pixels, was $widthPx x $heightPx = " +
            "${widthPx.toLong() * heightPx}"
    }
}

/**
 * The destination of a snapshot: its pixel size and the environment text is measured in.
 *
 * Grouped in one class so that new destination fields stay additive.
 *
 * @throws IllegalArgumentException if [widthPx] or [heightPx] is outside `1..4096`, or
 *   `widthPx × heightPx` is above 4 194 304 pixels (about 16 MB at ARGB_8888).
 */
@ExperimentalOrreryApi
public class CalendarSnapshotOutput(
    /** Width of the bitmap in pixels, `1..4096`. */
    public val widthPx: Int,
    /** Height of the bitmap in pixels, `1..4096`. */
    public val heightPx: Int,
    /** Converts dp and sp to pixels; its [Density.fontScale] scales all text. */
    public val density: Density,
    /** Loads the fonts of the snapshot's text. */
    public val fontFamilyResolver: FontFamily.Resolver,
    /** Direction of the grid columns, the selection ends and the text. */
    public val layoutDirection: LayoutDirection = LayoutDirection.Ltr,
) {
    init {
        checkSnapshotSize(widthPx, heightPx)
    }

    /** Returns a copy of this output with the given fields replaced. */
    public fun copy(
        widthPx: Int = this.widthPx,
        heightPx: Int = this.heightPx,
        density: Density = this.density,
        fontFamilyResolver: FontFamily.Resolver = this.fontFamilyResolver,
        layoutDirection: LayoutDirection = this.layoutDirection,
    ): CalendarSnapshotOutput = CalendarSnapshotOutput(widthPx, heightPx, density, fontFamilyResolver, layoutDirection)

    override fun equals(other: Any?): Boolean =
        other is CalendarSnapshotOutput &&
            widthPx == other.widthPx &&
            heightPx == other.heightPx &&
            density == other.density &&
            fontFamilyResolver == other.fontFamilyResolver &&
            layoutDirection == other.layoutDirection

    override fun hashCode(): Int {
        var result = widthPx
        result = 31 * result + heightPx
        result = 31 * result + density.hashCode()
        result = 31 * result + fontFamilyResolver.hashCode()
        result = 31 * result + layoutDirection.hashCode()
        return result
    }

    override fun toString(): String =
        "CalendarSnapshotOutput(widthPx=$widthPx, heightPx=$heightPx, density=$density, " +
            "fontFamilyResolver=$fontFamilyResolver, layoutDirection=$layoutDirection)"
}
