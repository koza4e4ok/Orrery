package me.kozakov.orrery.compose.internal

import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import me.kozakov.orrery.InternalOrreryApi

private const val DAYS_PER_WEEK = 7

/** "0".."31", shared by every month so drawing allocates no day strings. */
private val DayLabels: Array<String> = Array(32) { it.toString() }

/**
 * Everything a measured month text layout depends on.
 *
 * Internal Orrery API: public only for sibling `orrery-*` modules.
 */
@InternalOrreryApi
public data class MonthTextKey(
    /** Style of the day numbers; the bold variant is derived from it. */
    public val dayStyle: TextStyle,
    /** Style of titles. */
    public val titleStyle: TextStyle,
    /** Density (including font scale) the layouts are measured for. */
    public val density: Density,
    /** Layout direction the layouts are measured for. */
    public val layoutDirection: LayoutDirection,
    /** Resolver used to load the fonts. */
    public val fontFamilyResolver: FontFamily.Resolver,
)

/**
 * Text layouts for month grids: one regular and one bold layout per day number and
 * one per title, measured once (without colour, which is applied when drawing) and
 * reused. MiniMonth shares one cache across a whole YearCalendar; the snapshot
 * renderer creates one per render call. Not thread-safe.
 *
 * Internal Orrery API: public only for sibling `orrery-*` modules.
 */
@InternalOrreryApi
public class MonthTextCache(
    private val measurer: TextMeasurer,
) {
    /** Text layouts measured so far; stays bounded however many years scroll by. */
    public var measureCount: Int = 0
        private set

    private var latest: Texts? = null

    /** The layouts for [key]; a different style or environment starts a fresh set. */
    public fun texts(key: MonthTextKey): Texts = latest?.takeIf { it.key == key } ?: Texts(key).also { latest = it }

    /** The layouts measured for one [key]. */
    @InternalOrreryApi
    public inner class Texts(
        /** The style and environment these layouts are measured for. */
        public val key: MonthTextKey,
    ) {
        private val regular = arrayOfNulls<TextLayoutResult>(DayLabels.size)
        private val bold = arrayOfNulls<TextLayoutResult>(DayLabels.size)
        private val titles = HashMap<String, TextLayoutResult>()
        private val boldStyle = key.dayStyle.merge(TextStyle(fontWeight = FontWeight.Bold))

        /** The layout of day number [day] (0..31), bold when [isBold]. */
        public fun day(
            day: Int,
            isBold: Boolean,
        ): TextLayoutResult {
            val layouts = if (isBold) bold else regular
            return layouts[day]
                ?: measure(DayLabels[day], if (isBold) boldStyle else key.dayStyle).also { layouts[day] = it }
        }

        /** The layout of [text] in the title style. */
        public fun title(text: String): TextLayoutResult = titles.getOrPut(text) { measure(text, key.titleStyle) }

        /** Height of one week row: the measured day-number line (font-scale aware). */
        public val rowHeight: Int
            get() = maxOf(day(1, isBold = false).size.height, day(1, isBold = true).size.height)

        /** Width that fits seven day numbers side by side. */
        public val minGridWidth: Int
            get() = DAYS_PER_WEEK * maxOf(day(31, isBold = false).size.width, day(31, isBold = true).size.width)

        private fun measure(
            text: String,
            style: TextStyle,
        ): TextLayoutResult {
            measureCount++
            return measurer.measure(
                text = text,
                style = style,
                softWrap = false,
                maxLines = 1,
                layoutDirection = key.layoutDirection,
                density = key.density,
                fontFamilyResolver = key.fontFamilyResolver,
            )
        }
    }
}
