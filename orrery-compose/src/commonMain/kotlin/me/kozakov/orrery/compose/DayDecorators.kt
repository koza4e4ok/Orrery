package me.kozakov.orrery.compose

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
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
@Suppress("TooManyFunctions")
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

    /**
     * Up to [maxBars] horizontal event bars near the bottom of the cell.
     *
     * If an event continues from the previous calendar day, the bar extends
     * flush to the start edge (x = 0 in LTR); otherwise it starts inset
     * with a rounded cap. If it continues to the next calendar day, it
     * extends flush to the end edge (x = size.width in LTR); otherwise it
     * ends inset with a rounded cap. Events are sorted by (startDate, id)
     * so multi-day events stay aligned on the same horizontal track across
     * adjacent days.
     */
    @Composable
    public fun spanningEventBars(
        eventsByDate: (LocalDate) -> List<CalendarEvent<*>>,
        maxBars: Int = 2,
        barHeight: Dp = 4.dp,
        barSpacing: Dp = 2.dp,
        defaultColor: Color = MaterialTheme.colorScheme.primary,
    ): DayDecorator =
        SpanningEventBars(
            eventsByDate = eventsByDate,
            maxBars = maxBars,
            barHeight = barHeight,
            barSpacing = barSpacing,
            defaultColor = defaultColor,
        )

    private class SpanningEventBars(
        private val eventsByDate: (LocalDate) -> List<CalendarEvent<*>>,
        private val maxBars: Int,
        private val barHeight: Dp,
        private val barSpacing: Dp,
        private val defaultColor: Color,
    ) : CellAwareDayDecorator {
        override val layer: DecoratorLayer
            get() = DecoratorLayer.Over

        override fun DrawScope.draw(
            day: CalendarDay,
            cell: DayCellDrawState,
        ) {
            if (maxBars <= 0) return
            val active =
                eventsByDate(day.date)
                    .sortedWith(compareBy<CalendarEvent<*>> { it.span.startDate }.thenBy { it.id })
                    .take(maxBars)
            if (active.isEmpty()) return

            val prevDate = day.date.minus(1, DateTimeUnit.DAY)
            val nextDate = day.date.plus(1, DateTimeUnit.DAY)
            val prevIds = eventsByDate(prevDate).mapTo(hashSetOf()) { it.id }
            val nextIds = eventsByDate(nextDate).mapTo(hashSetOf()) { it.id }

            val heightPx = barHeight.toPx()
            val spacingPx = barSpacing.toPx()
            val insetPx = 4.dp.toPx()
            val radius = CornerRadius(heightPx / 2f, heightPx / 2f)
            val totalHeight = active.size * heightPx + (active.size - 1) * spacingPx
            val bottomMargin = (if (cell.hasLabel) 1.dp else 3.dp).toPx()
            var topY = size.height - bottomMargin - totalHeight
            val isLtr = layoutDirection == LayoutDirection.Ltr
            val selectedBarColor =
                if (cell.selected) {
                    cell.selectedContentColor.takeIf { it.isSpecified }
                } else {
                    null
                }

            val path = Path()
            active.forEach { event ->
                val continuesFromPrev = event.span.startDate <= prevDate && event.id in prevIds
                val continuesToNext = event.span.endDate >= nextDate && event.id in nextIds
                val color = selectedBarColor ?: event.colorArgb?.let(::Color) ?: defaultColor
                drawSpanningBar(
                    path = path,
                    topY = topY,
                    heightPx = heightPx,
                    insetPx = insetPx,
                    radius = radius,
                    leftFlush = if (isLtr) continuesFromPrev else continuesToNext,
                    rightFlush = if (isLtr) continuesToNext else continuesFromPrev,
                    color = color,
                )
                topY += heightPx + spacingPx
            }
        }

        private fun DrawScope.drawSpanningBar(
            path: Path,
            topY: Float,
            heightPx: Float,
            insetPx: Float,
            radius: CornerRadius,
            leftFlush: Boolean,
            rightFlush: Boolean,
            color: Color,
        ) {
            val leftX = if (leftFlush) 0f else insetPx
            val rightX = if (rightFlush) size.width else (size.width - insetPx).coerceAtLeast(leftX)
            if (rightX <= leftX) return
            val leftRadius = if (leftFlush) CornerRadius.Zero else radius
            val rightRadius = if (rightFlush) CornerRadius.Zero else radius
            path.reset()
            path.addRoundRect(
                RoundRect(
                    left = leftX,
                    top = topY,
                    right = rightX,
                    bottom = topY + heightPx,
                    topLeftCornerRadius = leftRadius,
                    bottomLeftCornerRadius = leftRadius,
                    topRightCornerRadius = rightRadius,
                    bottomRightCornerRadius = rightRadius,
                ),
            )
            drawPath(path = path, color = color)
        }
    }

    /**
     * Up to [maxBars] short horizontal stacked color bars centered near the
     * bottom of the cell.
     *
     * In [DefaultDay], bars on a selected day draw in the cell's selected
     * content color so they stay visible on the selection fill.
     */
    public fun stackedBars(
        maxBars: Int = 3,
        barWidth: Dp = 14.dp,
        barHeight: Dp = 2.5.dp,
        spacing: Dp = 2.dp,
        colors: (LocalDate) -> List<Color>,
    ): DayDecorator =
        StackedBars(
            maxBars = maxBars,
            barWidth = barWidth,
            barHeight = barHeight,
            spacing = spacing,
            colors = colors,
        )

    private class StackedBars(
        private val maxBars: Int,
        private val barWidth: Dp,
        private val barHeight: Dp,
        private val spacing: Dp,
        private val colors: (LocalDate) -> List<Color>,
    ) : CellAwareDayDecorator {
        override fun DrawScope.draw(
            day: CalendarDay,
            cell: DayCellDrawState,
        ) {
            if (maxBars <= 0) return
            val bars = colors(day.date).take(maxBars)
            if (bars.isEmpty()) return
            val selectedBarColor =
                if (cell.selected) {
                    cell.selectedContentColor.takeIf { it.isSpecified }
                } else {
                    null
                }
            val widthPx = barWidth.toPx().coerceAtMost(size.width)
            val heightPx = barHeight.toPx()
            val gapPx = spacing.toPx()
            val totalHeight = bars.size * heightPx + (bars.size - 1) * gapPx
            val bottomMargin = (if (cell.hasLabel) 1.5.dp else 4.dp).toPx()
            val leftX = (size.width - widthPx) / 2f
            var topY = size.height - bottomMargin - totalHeight
            val corner = CornerRadius(heightPx / 2f, heightPx / 2f)
            bars.forEach { color ->
                drawRoundRect(
                    color = selectedBarColor ?: color,
                    topLeft = Offset(leftX, topY),
                    size = Size(widthPx, heightPx),
                    cornerRadius = corner,
                )
                topY += heightPx + gapPx
            }
        }
    }

    /**
     * Translucent range preview connector band when [selectionState]'s
     * selection has an open rangeStart (rangeEnd == null) and
     * [targetDate] is non-null and distinct from rangeStart.
     *
     * Draws a connector band between minOf(rangeStart, targetDate) and
     * maxOf(rangeStart, targetDate), capped with a circular preview fill
     * on [targetDate].
     */
    @Composable
    public fun rangePreview(
        selectionState: CalendarSelectionState,
        targetDate: LocalDate?,
        color: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
    ): DayDecorator =
        object : DayDecorator {
            override val layer: DecoratorLayer
                get() = DecoratorLayer.Over

            override fun DrawScope.draw(day: CalendarDay) {
                val sel = selectionState.selection
                val anchor = sel.rangeStart ?: return
                if (sel.rangeEnd != null) return
                val target = targetDate ?: return
                if (target == anchor) return

                val start = minOf(anchor, target)
                val end = maxOf(anchor, target)
                val date = day.date
                if (date < start || date > end) return

                val isStart = date == start
                val isEnd = date == end
                val height = size.minDimension
                val top = (size.height - height) / 2f
                val center = size.width / 2f
                val isLtr = layoutDirection == LayoutDirection.Ltr

                val leftHalf = if (isLtr) !isStart else !isEnd
                val rightHalf = if (isLtr) !isEnd else !isStart

                when {
                    leftHalf && rightHalf -> {
                        drawRect(
                            color = color,
                            topLeft = Offset(0f, top),
                            size = Size(size.width, height),
                        )
                    }

                    leftHalf -> {
                        drawRect(
                            color = color,
                            topLeft = Offset(0f, top),
                            size = Size(center, height),
                        )
                    }

                    rightHalf -> {
                        drawRect(
                            color = color,
                            topLeft = Offset(center, top),
                            size = Size(size.width - center, height),
                        )
                    }
                }

                if (date == target) {
                    drawCircle(
                        color = color,
                        radius = height / 2f,
                        center = Offset(center, size.height / 2f),
                    )
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
