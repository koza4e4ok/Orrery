package me.kozakov.orrery.compose

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

/**
 * A dialog headline on one line: [style] when it fits, otherwise [compactStyle].
 * If even that is too wide (large font scale, narrow window) it wraps instead of
 * cutting the text off. Resets to [style] when [text] changes.
 */
@Composable
internal fun FittingHeadline(
    text: String,
    style: TextStyle,
    compactStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    var compact by remember(text) { mutableStateOf(false) }
    var wrap by remember(text) { mutableStateOf(false) }
    Text(
        text = text,
        modifier = modifier,
        style = if (compact) compactStyle else style,
        maxLines = if (wrap) Int.MAX_VALUE else 1,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = {
            if (it.didOverflowHeight) {
                // Only a layout in compactStyle decides to wrap, not a stale one in style.
                if (!compact) {
                    compact = true
                } else if (it.layoutInput.style.fontSize ==
                    compactStyle.fontSize
                ) {
                    wrap = true
                }
            }
        },
    )
}
