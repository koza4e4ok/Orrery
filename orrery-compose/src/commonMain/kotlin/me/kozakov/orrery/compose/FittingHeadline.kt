package me.kozakov.orrery.compose

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

/**
 * A dialog headline on one line: [style] when it fits, otherwise [compactStyle].
 * If even that is too wide (large font scale, narrow window) it wraps instead of
 * cutting the text off. Resets to [style] when [text], the available width or the
 * font scale changes.
 */
@Composable
internal fun FittingHeadline(
    text: String,
    style: TextStyle,
    compactStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    // The parent's width, not the text's: it does not change with the fit.
    var availableWidth by remember { mutableIntStateOf(-1) }
    val fontScale = LocalDensity.current.fontScale
    var fit by remember(text, availableWidth, fontScale) { mutableStateOf(HeadlineFit.Full) }
    // A layout measured in an earlier stage (one pass can report several) must not skip one.
    val measured = fit
    Text(
        text = text,
        modifier = modifier,
        style = if (fit == HeadlineFit.Full) style else compactStyle,
        maxLines = if (fit == HeadlineFit.Stacked) Int.MAX_VALUE else 1,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = {
            val width = it.layoutInput.constraints.maxWidth
            if (width != availableWidth) {
                availableWidth = width
            } else if (it.didOverflowHeight && fit == measured) {
                fit = measured.next()
            }
        },
    )
}

/** How a headline fits: its own style, a compact one, or the compact one wrapping over lines. */
internal enum class HeadlineFit {
    Full,
    Compact,
    Stacked,
    ;

    fun next(): HeadlineFit = entries[minOf(ordinal + 1, entries.lastIndex)]
}
