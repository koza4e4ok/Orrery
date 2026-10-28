package me.kozakov.orrery.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp

/** Where [focusRing] draws its stroke relative to the node's bounds. */
internal enum class FocusRingPlacement {
    /** Just outside the bounds, so small filled tiles keep their color visible. */
    Outside,

    /** Just inside the bounds, so the ring is never clipped by a parent. */
    Inside,
}

/**
 * Draws a [width] ring in [color] following [shape] over the content
 * while [focused]. Apply it before any clip so the ring is not cropped.
 */
internal fun Modifier.focusRing(
    focused: Boolean,
    color: Color,
    width: Dp,
    shape: Shape,
    placement: FocusRingPlacement,
): Modifier =
    drawWithContent {
        drawContent()
        if (focused) {
            val stroke = width.toPx()
            // The stroke is centered on the outline: grow or shrink the outline by
            // one stroke width so the ring lies entirely outside or inside the bounds.
            val delta = if (placement == FocusRingPlacement.Outside) stroke else -stroke
            val outlineSize = Size(size.width + delta, size.height + delta)
            translate(-delta / 2f, -delta / 2f) {
                drawOutline(
                    shape.createOutline(outlineSize, layoutDirection, this),
                    color,
                    style = Stroke(width = stroke),
                )
            }
        }
    }
