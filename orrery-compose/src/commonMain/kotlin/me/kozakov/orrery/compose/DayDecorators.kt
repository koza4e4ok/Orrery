package me.kozakov.orrery.compose

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import me.kozakov.orrery.core.CalendarDay
import me.kozakov.orrery.core.CalendarEvent

/** Draw layer for a [DayDecorator]: behind or over the day content. */
public enum class DecoratorLayer { Behind, Over }

/**
 * Custom drawing stacked into [DefaultDay]'s cell. Implementations draw
 * with plain DrawScope commands — no extra composables per cell.
 * [layer] picks whether drawing happens behind the day number (the
 * default) or over it (e.g. strikethrough). Within a layer, decorators
 * draw in list order.
 */
public fun interface DayDecorator {
    public val layer: DecoratorLayer
        get() = DecoratorLayer.Behind

    public fun DrawScope.draw(day: CalendarDay)
}

/** What [DefaultDay] knows about the cell when a built-in decorator draws. */
internal class DayCellDrawState(
    val selected: Boolean,
    val selectedContentColor: Color,
    val hasLabel: Boolean,
) {
    internal companion object {
        val Unknown = DayCellDrawState(selected = false, selectedContentColor = Color.Unspecified, hasLabel = false)
    }
}

/**
 * Built-in decorators that adapt to the cell, e.g. recolor on a selected
 * day. Outside [DefaultDay] they draw as for an unselected, unlabeled day.
 */
internal interface CellAwareDayDecorator : DayDecorator {
    fun DrawScope.draw(
        day: CalendarDay,
        cell: DayCellDrawState,
    )

    override fun DrawScope.draw(day: CalendarDay) {
        draw(day, DayCellDrawState.Unknown)
    }
}

internal fun DrawScope.drawDecorator(
    decorator: DayDecorator,
    day: CalendarDay,
    cell: DayCellDrawState,
) {
    if (decorator is CellAwareDayDecorator) {
        with(decorator) { draw(day, cell) }
    } else {
        with(decorator) { draw(day) }
    }
}

/**
 * Prebuilt [DayDecorator]s. All are data-driven via per-date lookups
 * returning null/empty/false for undecorated days. Factories reading
 * Material theme defaults are @Composable — call them in composition
 * (once, outside dayContent, ideally remembered) and pass the resulting
 * list to [DefaultDay].
 */
public object DayDecorators {
    /**
     * Up to [maxDots] colored dots centered near the cell bottom.
     *
     * In [DefaultDay], dots on a selected day draw in the cell's selected
     * content color so they stay visible on the selection fill (pass a
     * selectedColor to the other overload to pick it), and dots on a day
     * with a secondary label sit closer to the bottom edge so they do not
     * touch the label.
     */
    public fun eventDots(
        maxDots: Int = 3,
        dotSize: Dp = 4.dp,
        spacing: Dp = 2.dp,
        colors: (LocalDate) -> List<Color>,
    ): DayDecorator = EventDots(maxDots, dotSize, spacing, selectedColor = null, colors)

    /**
     * [eventDots] whose dots draw in [selectedColor] on a selected day in
     * [DefaultDay].
     */
    public fun eventDots(
        selectedColor: Color,
        maxDots: Int = 3,
        dotSize: Dp = 4.dp,
        spacing: Dp = 2.dp,
        colors: (LocalDate) -> List<Color>,
    ): DayDecorator = EventDots(maxDots, dotSize, spacing, selectedColor, colors)

    private class EventDots(
        private val maxDots: Int,
        private val dotSize: Dp,
        private val spacing: Dp,
        private val selectedColor: Color?,
        private val colors: (LocalDate) -> List<Color>,
    ) : CellAwareDayDecorator {
        override fun DrawScope.draw(
            day: CalendarDay,
            cell: DayCellDrawState,
        ) {
            val dots = colors(day.date).take(maxDots)
            if (dots.isEmpty()) return
            val selectedDotColor =
                if (cell.selected) {
                    selectedColor ?: cell.selectedContentColor.takeIf { it.isSpecified }
                } else {
                    null
                }
            val diameter = dotSize.toPx()
            val gap = spacing.toPx()
            val total = dots.size * diameter + (dots.size - 1) * gap
            val y = size.height - (if (cell.hasLabel) 3.dp else 6.dp).toPx()
            var x = (size.width - total) / 2f + diameter / 2f
            dots.forEach { color ->
                drawCircle(color = selectedDotColor ?: color, radius = diameter / 2f, center = Offset(x, y))
                x += diameter + gap
            }
        }
    }

    /** Up to [maxDots] distinct event colors for each day from [eventsByDate]. */
    @Composable
    public fun eventDotsForEvents(
        eventsByDate: (LocalDate) -> List<CalendarEvent<*>>,
        defaultColor: Color = MaterialTheme.colorScheme.primary,
        maxDots: Int = 3,
        dotSize: Dp = 4.dp,
        spacing: Dp = 2.dp,
    ): DayDecorator =
        eventDots(
            maxDots = maxDots,
            dotSize = dotSize,
            spacing = spacing,
        ) { date ->
            eventsByDate(date).map { event -> event.colorArgb?.let(::Color) ?: defaultColor }.distinct()
        }

    /** 0f..1f progress arc around the cell; null draws nothing. */
    @Composable
    public fun progressRing(
        strokeWidth: Dp = 2.dp,
        color: Color = MaterialTheme.colorScheme.primary,
        trackColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        progress: (LocalDate) -> Float?,
    ): DayDecorator =
        DayDecorator { day ->
            val value = progress(day.date)
            if (value != null) {
                val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
                val side = size.minDimension - strokeWidth.toPx()
                val topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f)
                val arcSize = Size(side, side)
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke,
                )
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * value.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke,
                )
            }
        }

    /** 0f..1f linear bar near the cell bottom; null draws nothing. */
    @Composable
    public fun progressBar(
        thickness: Dp = 3.dp,
        color: Color = MaterialTheme.colorScheme.primary,
        trackColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        progress: (LocalDate) -> Float?,
    ): DayDecorator =
        DayDecorator { day ->
            val value = progress(day.date)
            if (value != null) {
                val width = size.minDimension * 0.6f
                val startX = (size.width - width) / 2f
                val y = size.height - 5.dp.toPx()
                val strokePx = thickness.toPx()
                drawLine(trackColor, Offset(startX, y), Offset(startX + width, y), strokePx, StrokeCap.Round)
                drawLine(
                    color,
                    Offset(startX, y),
                    Offset(startX + width * value.coerceIn(0f, 1f), y),
                    strokePx,
                    StrokeCap.Round,
                )
            }
        }

    /** Line through the day number for matching dates. Draws over the content. */
    @Composable
    public fun strikethrough(
        color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        thickness: Dp = 1.dp,
        dates: (LocalDate) -> Boolean,
    ): DayDecorator =
        object : DayDecorator {
            override val layer: DecoratorLayer
                get() = DecoratorLayer.Over

            override fun DrawScope.draw(day: CalendarDay) {
                if (dates(day.date)) {
                    val width = size.minDimension * 0.4f
                    val y = size.height / 2f
                    drawLine(
                        color,
                        Offset((size.width - width) / 2f, y),
                        Offset((size.width + width) / 2f, y),
                        thickness.toPx(),
                        StrokeCap.Round,
                    )
                }
            }
        }

    /** Short line under the day number for matching dates. */
    @Composable
    public fun underline(
        color: Color = MaterialTheme.colorScheme.primary,
        thickness: Dp = 2.dp,
        dates: (LocalDate) -> Boolean,
    ): DayDecorator =
        DayDecorator { day ->
            if (dates(day.date)) {
                val width = size.minDimension * 0.4f
                val y = size.height - 6.dp.toPx()
                drawLine(
                    color,
                    Offset((size.width - width) / 2f, y),
                    Offset((size.width + width) / 2f, y),
                    thickness.toPx(),
                    StrokeCap.Round,
                )
            }
        }

    /** GitHub-style intensity fill (0f..1f) of [shape]; null draws nothing. */
    @Composable
    public fun heatmap(
        shape: Shape = CircleShape,
        colorScale: (Float) -> Color = defaultHeatmapScale(),
        intensity: (LocalDate) -> Float?,
    ): DayDecorator =
        DayDecorator { day ->
            val value = intensity(day.date)
            if (value != null) {
                drawDayShape(shape, colorScale(value.coerceIn(0f, 1f)))
            }
        }

    /**
     * Count bubble at the cell's top-end corner (top-start in RTL). Counts
     * <= 0 draw nothing; counts above [maxCount] render as "[maxCount]+".
     * Draws over the day content.
     *
     * In [DefaultDay], the bubble on a selected day gets a 1 dp outline in
     * [selectedOutlineColor] (null uses the cell's selected content color)
     * that separates it from the selection fill it overlaps.
     */
    @Composable
    public fun badge(
        maxCount: Int = 9,
        containerColor: Color = MaterialTheme.colorScheme.error,
        contentColor: Color = MaterialTheme.colorScheme.onError,
        selectedOutlineColor: Color? = null,
        count: (LocalDate) -> Int,
    ): DayDecorator {
        val textMeasurer = rememberTextMeasurer()
        val textStyle = MaterialTheme.typography.labelSmall.copy(color = contentColor)
        return object : CellAwareDayDecorator {
            override val layer: DecoratorLayer
                get() = DecoratorLayer.Over

            override fun DrawScope.draw(
                day: CalendarDay,
                cell: DayCellDrawState,
            ) {
                val value = count(day.date)
                if (value <= 0) return
                val text = if (value > maxCount) "$maxCount+" else value.toString()
                val layout = textMeasurer.measure(text, textStyle)
                val bubbleHeight = layout.size.height + 2.dp.toPx()
                val bubbleWidth = maxOf(bubbleHeight, layout.size.width + 8.dp.toPx())
                val inset = 2.dp.toPx()
                val left =
                    if (layoutDirection == LayoutDirection.Rtl) {
                        inset
                    } else {
                        size.width - bubbleWidth - inset
                    }
                drawRoundRect(
                    color = containerColor,
                    topLeft = Offset(left, inset),
                    size = Size(bubbleWidth, bubbleHeight),
                    cornerRadius = CornerRadius(bubbleHeight / 2f),
                )
                val outline = selectedOutlineColor ?: cell.selectedContentColor
                if (cell.selected && outline.isSpecified) {
                    val outlineWidth = 1.dp.toPx()
                    drawRoundRect(
                        color = outline,
                        topLeft = Offset(left - outlineWidth / 2f, inset - outlineWidth / 2f),
                        size = Size(bubbleWidth + outlineWidth, bubbleHeight + outlineWidth),
                        cornerRadius = CornerRadius((bubbleHeight + outlineWidth) / 2f),
                        style = Stroke(width = outlineWidth),
                    )
                }
                drawText(
                    textLayoutResult = layout,
                    topLeft =
                        Offset(
                            left + (bubbleWidth - layout.size.width) / 2f,
                            inset + (bubbleHeight - layout.size.height) / 2f,
                        ),
                )
            }
        }
    }

    @Deprecated(
        "Kept for binary compatibility; use the overload with selectedOutlineColor.",
        level = DeprecationLevel.HIDDEN,
    )
    @Composable
    public fun badge(
        maxCount: Int = 9,
        containerColor: Color = MaterialTheme.colorScheme.error,
        contentColor: Color = MaterialTheme.colorScheme.onError,
        count: (LocalDate) -> Int,
    ): DayDecorator =
        badge(
            maxCount = maxCount,
            containerColor = containerColor,
            contentColor = contentColor,
            selectedOutlineColor = null,
            count = count,
        )

    @Composable
    private fun defaultHeatmapScale(): (Float) -> Color {
        val primary = MaterialTheme.colorScheme.primary
        return { t -> lerp(Color.Transparent, primary, t) }
    }
}
