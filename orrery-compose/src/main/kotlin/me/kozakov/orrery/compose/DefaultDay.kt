package me.kozakov.orrery.compose

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import me.kozakov.orrery.core.CalendarDay
import me.kozakov.orrery.core.DayInfo
import me.kozakov.orrery.core.DayPosition
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Batteries-included day cell: selection fill, in-range band, today
 * indicator, optional secondary label, and stackable [decorators] for
 * custom drawing (event dots, progress, markers, heatmaps).
 *
 * Dates disabled via the selection state's DisabledDates render with the
 * unavailable colors and are not clickable; [enabled] = false forces the
 * same treatment. Shapes come from [shapes].
 *
 * A non-rectangular [CalendarDayShapes.inRangeShape] switches the range
 * band to per-day segmented fills.
 *
 * The container fill, selection fill, today FilledCircle and Behind
 * decorators are clipped to [CalendarDayShapes.dayShape]; the range
 * connector band, the today Ring/Underline and Over decorators (e.g.
 * badges) draw outside that clip so they are never cropped by the cell
 * shape.
 */
@Composable
public fun DefaultDay(
    day: CalendarDay,
    modifier: Modifier = Modifier,
    selectionState: CalendarSelectionState? = null,
    today: LocalDate = currentDate(),
    colors: CalendarDayColors = CalendarDefaults.dayColors(),
    shapes: CalendarDayShapes = CalendarDefaults.dayShapes(),
    info: DayInfo? = null,
    enabled: Boolean = true,
    showOutDates: Boolean = true,
    animateSelection: Boolean = true,
    hapticsEnabled: Boolean = true,
    decorators: List<DayDecorator> = emptyList(),
    onClick: ((CalendarDay) -> Unit)? = null,
) {
    val hidden = !showOutDates && day.position != DayPosition.MonthDate
    if (hidden) {
        Box(modifier.fillMaxWidth().heightIn(min = 48.dp))
        return
    }
    val isToday = day.date == today
    val available = enabled && selectionState?.isDisabled(day.date) != true
    val isSelected = selectionState?.isSelected(day.date) == true
    val isInRange = selectionState?.isInRange(day.date) == true
    val indicator = shapes.todayIndicator
    val bandMode =
        when {
            isInRange -> RangeBand.Full
            selectionState?.isRangeStart(day.date) == true -> RangeBand.TowardEnd
            selectionState?.isRangeEnd(day.date) == true -> RangeBand.TowardStart
            else -> RangeBand.None
        }
    val selectedScale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec =
            if (animateSelection) {
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
            } else {
                snap()
            },
        label = "daySelection",
    )
    val bandAlpha by animateFloatAsState(
        targetValue = if (bandMode != RangeBand.None) 1f else 0f,
        animationSpec = if (animateSelection) tween(durationMillis = 200) else snap(),
        label = "dayRangeBand",
    )
    val description =
        remember(day.date) {
            day.date.toJavaLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
        }
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val contentColor =
        when {
            !available -> colors.unavailableContentColor
            isSelected -> colors.selectedContentColor
            isInRange -> colors.inRangeContentColor
            day.position != DayPosition.MonthDate -> colors.outDateContentColor
            isToday && indicator is TodayIndicator.FilledCircle -> colors.selectedContentColor
            isToday -> colors.todayContentColor
            else -> colors.contentColor
        }
    val hasOverlay =
        (isToday && !isSelected && (indicator is TodayIndicator.Ring || indicator is TodayIndicator.Underline)) ||
            decorators.any { it.layer == DecoratorLayer.Over }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .drawBehind {
                    if (bandAlpha > 0f && shapes.inRangeShape == RectangleShape) {
                        drawRangeBand(
                            bandMode,
                            colors.inRangeContainerColor.copy(alpha = colors.inRangeContainerColor.alpha * bandAlpha),
                        )
                    }
                }.selectable(
                    selected = isSelected,
                    enabled = available,
                    interactionSource = interactionSource,
                    // Ripple is drawn by the shape-clipped layer below, not the full-cell touch target.
                    indication = null,
                    onClick = {
                        if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        selectionState?.click(day.date)
                        onClick?.invoke(day)
                    },
                ).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        // Clipped fill layer: container, unavailable, today fill, selection, Behind decorators.
        Box(
            Modifier
                .matchParentSize()
                .background(colors.containerColor, RectangleShape)
                .clip(shapes.dayShape)
                .then(
                    if (!available) Modifier.background(colors.unavailableContainerColor) else Modifier,
                ).drawBehind {
                    if (bandAlpha > 0f && bandMode == RangeBand.Full && shapes.inRangeShape != RectangleShape) {
                        drawDayShape(
                            shapes.inRangeShape,
                            colors.inRangeContainerColor.copy(alpha = colors.inRangeContainerColor.alpha * bandAlpha),
                        )
                    }
                    if (isToday && !isSelected && indicator is TodayIndicator.FilledCircle) {
                        drawDayShape(shapes.dayShape, colors.todayIndicatorColor)
                    }
                    if (selectedScale > 0f) {
                        scale(selectedScale) {
                            drawDayShape(shapes.selectedShape, colors.selectedContainerColor)
                        }
                    }
                    decorators.forEach { decorator ->
                        if (decorator.layer == DecoratorLayer.Behind) {
                            with(decorator) { draw(day) }
                        }
                    }
                },
        )
        // Ripple layer: a centered square clipped to dayShape so touch feedback
        // matches the drawn selection (circle by default, or any custom shape).
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxSize()
                    .aspectRatio(1f)
                    .clip(shapes.dayShape)
                    .indication(interactionSource, ripple()),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.date.day.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
            val label = info?.label
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) colors.selectedContentColor else colors.labelColor,
                )
            }
        }
        // Unclipped overlay: today Ring/Underline and Over decorators (badges) are
        // never cropped by the cell shape.
        if (hasOverlay) {
            Box(
                Modifier.matchParentSize().drawBehind {
                    if (isToday && !isSelected) {
                        when (indicator) {
                            is TodayIndicator.Ring -> {
                                drawDayShape(
                                    shapes.dayShape,
                                    colors.todayIndicatorColor,
                                    Stroke(width = indicator.width.toPx()),
                                )
                            }

                            TodayIndicator.Underline -> {
                                val lineWidth = size.minDimension * 0.4f
                                val y = size.height - 6.dp.toPx()
                                drawLine(
                                    color = colors.todayIndicatorColor,
                                    start = Offset((size.width - lineWidth) / 2f, y),
                                    end = Offset((size.width + lineWidth) / 2f, y),
                                    strokeWidth = 2.dp.toPx(),
                                    cap = StrokeCap.Round,
                                )
                            }

                            TodayIndicator.FilledCircle, TodayIndicator.None -> {
                                Unit
                            }
                        }
                    }
                    decorators.forEach { decorator ->
                        if (decorator.layer == DecoratorLayer.Over) {
                            with(decorator) { draw(day) }
                        }
                    }
                },
            )
        }
    }
}

/** Which portion of the in-range connector band a cell fills. */
private enum class RangeBand { None, Full, TowardEnd, TowardStart }

/**
 * Draws the range connector band as a vertically centered bar whose
 * height matches the endpoint circle diameter, so a completed range
 * reads as one continuous pill capped by the endpoint fills. [mode]
 * picks the horizontal extent; [TowardEnd]/[TowardStart] fill the half
 * that faces the range interior (flipped in RTL).
 */
private fun DrawScope.drawRangeBand(
    mode: RangeBand,
    color: Color,
) {
    if (mode == RangeBand.None) return
    val height = size.minDimension
    val top = (size.height - height) / 2f
    val center = size.width / 2f
    val ltr = layoutDirection == LayoutDirection.Ltr
    val (left, right) =
        when (mode) {
            RangeBand.Full -> 0f to size.width
            RangeBand.TowardEnd -> if (ltr) center to size.width else 0f to center
            RangeBand.TowardStart -> if (ltr) 0f to center else center to size.width
            RangeBand.None -> return
        }
    drawRect(color = color, topLeft = Offset(left, top), size = Size(right - left, height))
}

/**
 * Draws [shape] with [color] in a centered square of the cell's min
 * dimension — for the default CircleShape this reproduces the original
 * drawCircle(radius = minDimension / 2) exactly. [style] defaults to
 * [Fill]; pass a [Stroke] for outlined markers such as the today ring,
 * which stays perfectly round regardless of the cell's aspect ratio.
 */
internal fun DrawScope.drawDayShape(
    shape: Shape,
    color: Color,
    style: DrawStyle = Fill,
) {
    val side = size.minDimension
    translate((size.width - side) / 2f, (size.height - side) / 2f) {
        drawOutline(shape.createOutline(Size(side, side), layoutDirection, this), color, style = style)
    }
}
