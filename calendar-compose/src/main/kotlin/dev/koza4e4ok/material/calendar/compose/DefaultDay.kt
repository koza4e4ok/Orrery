package dev.koza4e4ok.material.calendar.compose

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.koza4e4ok.material.calendar.core.CalendarDay
import dev.koza4e4ok.material.calendar.core.DayInfo
import dev.koza4e4ok.material.calendar.core.DayPosition
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Batteries-included day cell: selection fill, in-range band, today
 * indicator, optional secondary label, and stackable [decorators] for
 * custom drawing (event dots, progress, markers, heatmaps).
 *
 * Dates disabled via the selection state's DisabledDates render with the
 * unavailable colors and are not clickable; [enabled] = false forces the
 * same treatment. Shapes come from [shapes]; note that
 * [CalendarDayShapes.dayShape] clips the cell, so a
 * [CalendarDayShapes.selectedShape] wider than it will be clipped.
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
    val inRangeAlpha by animateFloatAsState(
        targetValue = if (isInRange) 1f else 0f,
        animationSpec = if (animateSelection) tween(durationMillis = 200) else snap(),
        label = "dayInRange",
    )
    val description =
        remember(day.date) {
            day.date.toJavaLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
        }
    val haptics = LocalHapticFeedback.current
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
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .background(
                    color =
                        if (inRangeAlpha > 0f) {
                            colors.inRangeContainerColor.copy(alpha = colors.inRangeContainerColor.alpha * inRangeAlpha)
                        } else {
                            colors.containerColor
                        },
                    shape = if (inRangeAlpha > 0f) shapes.inRangeShape else RectangleShape,
                ).clip(shapes.dayShape)
                .then(
                    if (!available) Modifier.background(colors.unavailableContainerColor) else Modifier,
                ).drawBehind {
                    if (isToday && !isSelected) {
                        when (indicator) {
                            TodayIndicator.FilledCircle -> {
                                drawDayShape(shapes.dayShape, colors.todayIndicatorColor)
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

                            is TodayIndicator.Ring, TodayIndicator.None -> {
                                Unit
                            }
                        }
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
                }.then(
                    if (decorators.any { it.layer == DecoratorLayer.Over }) {
                        Modifier.drawWithContent {
                            drawContent()
                            decorators.forEach { decorator ->
                                if (decorator.layer == DecoratorLayer.Over) {
                                    with(decorator) { draw(day) }
                                }
                            }
                        }
                    } else {
                        Modifier
                    },
                ).then(
                    if (isToday && !isSelected && indicator is TodayIndicator.Ring) {
                        Modifier.border(indicator.width, colors.todayIndicatorColor, shapes.dayShape)
                    } else {
                        Modifier
                    },
                ).selectable(
                    selected = isSelected,
                    enabled = available,
                    onClick = {
                        if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        selectionState?.click(day.date)
                        onClick?.invoke(day)
                    },
                ).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
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
    }
}

/**
 * Draws [shape] filled with [color] in a centered square of the cell's
 * min dimension — for the default CircleShape this reproduces the
 * original drawCircle(radius = minDimension / 2) exactly, keeping
 * existing screenshot goldens byte-identical.
 */
internal fun DrawScope.drawDayShape(
    shape: Shape,
    color: Color,
) {
    val side = size.minDimension
    translate((size.width - side) / 2f, (size.height - side) / 2f) {
        drawOutline(shape.createOutline(Size(side, side), layoutDirection, this), color)
    }
}
