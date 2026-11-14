package me.kozakov.orrery.compose.internal

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.LayoutDirection
import me.kozakov.orrery.InternalOrreryApi

/**
 * Draws one selection pill segment in [cell]: a band [radius] × 2 tall centred
 * vertically. A rounded end is inset to the today circle (centre ± [radius]) so a lone
 * date reads as a circle; a square end runs to the cell edge so adjacent segments join.
 * [roundStart] and [roundEnd] are logical: they mirror in RTL.
 *
 * Internal Orrery API: public only for sibling `orrery-*` modules.
 */
@InternalOrreryApi
public fun DrawScope.drawSelectionPill(
    color: Color,
    cell: Rect,
    radius: Float,
    roundStart: Boolean,
    roundEnd: Boolean,
) {
    val rtl = layoutDirection == LayoutDirection.Rtl
    val roundLeft = if (rtl) roundEnd else roundStart
    val roundRight = if (rtl) roundStart else roundEnd
    val centerX = cell.center.x
    val top = cell.center.y - radius
    val left = if (roundLeft) centerX - radius else cell.left
    val right = if (roundRight) centerX + radius else cell.right
    drawRoundRect(color, Offset(left, top), Size(right - left, radius * 2), CornerRadius(radius))
    if (!roundLeft) drawRect(color, Offset(cell.left, top), Size(centerX - cell.left, radius * 2))
    if (!roundRight) drawRect(color, Offset(centerX, top), Size(cell.right - centerX, radius * 2))
}

/**
 * Draws the today mark: a filled circle of [radius] at [center].
 *
 * Internal Orrery API: public only for sibling `orrery-*` modules.
 */
@InternalOrreryApi
public fun DrawScope.drawTodayMark(
    color: Color,
    radius: Float,
    center: Offset,
) {
    drawCircle(color, radius, center)
}

/**
 * Draws one dot per entry of [colors] in a row centred horizontally in this scope at
 * [centerY], [diameter] px wide with [spacing] px between dots. [color], when set,
 * replaces every dot's colour (for example on a selected day). Callers cap the count;
 * translate the scope to draw into a cell.
 *
 * Internal Orrery API: public only for sibling `orrery-*` modules.
 */
@InternalOrreryApi
public fun DrawScope.drawEventDots(
    colors: List<Color>,
    centerY: Float,
    diameter: Float,
    spacing: Float,
    color: Color? = null,
) {
    if (colors.isEmpty()) return
    val total = colors.size * diameter + (colors.size - 1) * spacing
    var x = (size.width - total) / 2f + diameter / 2f
    colors.forEach { dotColor ->
        drawCircle(color = color ?: dotColor, radius = diameter / 2f, center = Offset(x, centerY))
        x += diameter + spacing
    }
}
