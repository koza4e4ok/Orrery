package dev.koza4e4ok.orrery.compose

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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.koza4e4ok.orrery.core.CalendarDay
import kotlinx.datetime.LocalDate

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

/**
 * Prebuilt [DayDecorator]s. All are data-driven via per-date lookups
 * returning null/empty/false for undecorated days. Factories reading
 * Material theme defaults are @Composable — call them in composition
 * (once, outside dayContent, ideally remembered) and pass the resulting
 * list to [DefaultDay].
 */
public object DayDecorators {
    /** Up to [maxDots] colored dots centered near the cell bottom. */
    public fun eventDots(
        maxDots: Int = 3,
        dotSize: Dp = 4.dp,
        spacing: Dp = 2.dp,
        colors: (LocalDate) -> List<Color>,
    ): DayDecorator =
        DayDecorator { day ->
            val dots = colors(day.date).take(maxDots)
            if (dots.isNotEmpty()) {
                val diameter = dotSize.toPx()
                val gap = spacing.toPx()
                val total = dots.size * diameter + (dots.size - 1) * gap
                val y = size.height - 6.dp.toPx()
                var x = (size.width - total) / 2f + diameter / 2f
                dots.forEach { color ->
                    drawCircle(color = color, radius = diameter / 2f, center = Offset(x, y))
                    x += diameter + gap
                }
            }
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
     */
    @Composable
    public fun badge(
        maxCount: Int = 9,
        containerColor: Color = MaterialTheme.colorScheme.error,
        contentColor: Color = MaterialTheme.colorScheme.onError,
        count: (LocalDate) -> Int,
    ): DayDecorator {
        val textMeasurer = rememberTextMeasurer()
        val textStyle = MaterialTheme.typography.labelSmall.copy(color = contentColor)
        return object : DayDecorator {
            override val layer: DecoratorLayer
                get() = DecoratorLayer.Over

            override fun DrawScope.draw(day: CalendarDay) {
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

    @Composable
    private fun defaultHeatmapScale(): (Float) -> Color {
        val primary = MaterialTheme.colorScheme.primary
        return { t -> lerp(Color.Transparent, primary, t) }
    }
}
