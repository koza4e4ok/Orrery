@file:OptIn(ExperimentalOrreryApi::class)

package me.kozakov.orrery.widget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import me.kozakov.orrery.ExperimentalOrreryApi

/** Contrast every day number inside a selected range reaches against the band: WCAG AA for normal text. */
internal const val MIN_RANGE_TEXT_CONTRAST = 4.5f

/** Halvings of the mix between a colour and white or black; 16 is finer than 8-bit colour. */
private const val CONTRAST_SEARCH_STEPS = 16

/** WCAG 2 contrast ratio of two opaque colours, from 1 to 21. */
internal fun contrastRatio(
    a: Color,
    b: Color,
): Float {
    val first = a.luminance()
    val second = b.luminance()
    return (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
}

/**
 * The colour a day number inside the range band is drawn in: [color] (the day, holiday or
 * priority colour) as is when it reaches [MIN_RANGE_TEXT_CONTRAST] against the band over
 * the container, otherwise [readableOn] the band, so a strong band (dynamic colours) or a
 * red priority day on a dark band stays legible.
 */
internal fun CalendarSnapshotColors.rangeDayColor(color: Color): Color =
    readableOn(color, rangeBand.compositeOver(container), MIN_RANGE_TEXT_CONTRAST)

/**
 * [color] mixed toward white or black, whichever contrasts more with [background], just
 * far enough to reach [minContrast] against it, so a red stays reddish; [color] itself when
 * it already does. Every opaque background reaches at least 4.58:1 with white or black,
 * so any [minContrast] up to that is always met.
 */
internal fun readableOn(
    color: Color,
    background: Color,
    minContrast: Float,
): Color {
    val opaque = color.compositeOver(background)
    if (contrastRatio(opaque, background) >= minContrast) return opaque
    val backgroundLuminance = background.luminance()
    val toWhite = contrastRatio(Color.White, background) >= contrastRatio(Color.Black, background)
    val target = if (toWhite) Color.White else Color.Black

    // Past the background's luminance on the target's side, contrast only grows toward the target.
    fun reaches(candidate: Color): Boolean {
        val luminance = candidate.luminance()
        val onTargetSide = if (toWhite) luminance > backgroundLuminance else luminance < backgroundLuminance
        return onTargetSide && contrastRatio(candidate, background) >= minContrast
    }

    var low = 0f
    var high = 1f
    repeat(CONTRAST_SEARCH_STEPS) {
        val middle = (low + high) / 2f
        if (reaches(lerp(opaque, target, middle))) high = middle else low = middle
    }
    return lerp(opaque, target, high)
}
