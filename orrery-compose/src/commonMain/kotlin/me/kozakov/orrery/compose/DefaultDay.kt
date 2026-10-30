package me.kozakov.orrery.compose

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.datetime.LocalDate
import me.kozakov.orrery.core.CalendarDay
import me.kozakov.orrery.core.DayInfo
import me.kozakov.orrery.core.DayPosition
import kotlin.math.sqrt

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
 * band to per-day segmented fills. Each half of the connector band fades
 * in and out on its own (when [animateSelection] is true), so range
 * endpoints and in-range days transition smoothly as a range grows,
 * shrinks or is cleared.
 *
 * The secondary [DayInfo.label] stays on one centered line and is
 * ellipsized when it does not fit the cell, so long holiday names never
 * wrap or shift the day number. A positive [selectedElevation] draws
 * a shadow under the selected day's fill.
 *
 * The container fill, selection fill, today FilledCircle and Behind
 * decorators are clipped to [CalendarDayShapes.dayShape]; the range
 * connector band, the today Ring/Underline and Over decorators (e.g.
 * badges) draw outside that clip so they are never cropped by the cell
 * shape.
 *
 * While the cell has keyboard / D-pad focus, a ring of
 * [CalendarDayShapes.focusedBorderWidth] in [CalendarDayColors.focusedBorderColor]
 * outlines [CalendarDayShapes.dayShape] in the unclipped overlay. While a
 * pointer hovers an available, unselected day, [CalendarDayColors.hoverContainerColor]
 * fills the day shape.
 *
 * Besides the date as content description, the cell reports range
 * start / end / in range, today and unavailable as its accessibility
 * state description, using [strings].
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
    selectedElevation: Dp = 0.dp,
    hapticsEnabled: Boolean = true,
    decorators: List<DayDecorator> = emptyList(),
    strings: CalendarDayStrings = CalendarDefaults.dayStrings(),
    onClick: ((CalendarDay) -> Unit)? = null,
) {
    DefaultDayContent(
        day = day,
        modifier = modifier,
        selectionState = selectionState,
        today = today,
        colors = colors,
        shapes = shapes,
        info = info,
        enabled = enabled,
        showOutDates = showOutDates,
        animateSelection = animateSelection,
        selectedElevation = selectedElevation,
        hapticsEnabled = hapticsEnabled,
        decorators = decorators,
        strings = strings,
        onClick = onClick,
        minCellHeight = CalendarDensity.Comfortable.minCellHeight,
        showSecondaryLabel = true,
        cellBorderWidth = 0.dp,
        cellBorderColor = Color.Transparent,
        turnoverDates = emptySet(),
        dayClick = null,
    )
}

/**
 * [DefaultDay] rendered with a [CalendarStyle]: [CalendarStyle.colors],
 * [CalendarStyle.shapes] and [CalendarStyle.selectedElevation] replace
 * the separate parameters of the other overload.
 *
 * The cell is at least [CalendarDensity.minCellHeight] of
 * [CalendarStyle.density] tall, and the secondary [DayInfo.label] is
 * hidden when [CalendarDensity.showSecondaryLabel] is false. A positive
 * [CalendarStyle.cellBorderWidth] outlines [CalendarDayShapes.dayShape]
 * in [CalendarStyle.cellBorderColor], drawn unclipped under the today
 * marker, Over decorators and focus ring.
 */
@Composable
public fun DefaultDay(
    day: CalendarDay,
    style: CalendarStyle,
    modifier: Modifier = Modifier,
    selectionState: CalendarSelectionState? = null,
    today: LocalDate = currentDate(),
    info: DayInfo? = null,
    enabled: Boolean = true,
    showOutDates: Boolean = true,
    animateSelection: Boolean = true,
    hapticsEnabled: Boolean = true,
    decorators: List<DayDecorator> = emptyList(),
    strings: CalendarDayStrings = CalendarDefaults.dayStrings(),
    onClick: ((CalendarDay) -> Unit)? = null,
) {
    DefaultDayContent(
        day = day,
        modifier = modifier,
        selectionState = selectionState,
        today = today,
        colors = style.colors,
        shapes = style.shapes,
        info = info,
        enabled = enabled,
        showOutDates = showOutDates,
        animateSelection = animateSelection,
        selectedElevation = style.selectedElevation,
        hapticsEnabled = hapticsEnabled,
        decorators = decorators,
        strings = strings,
        onClick = onClick,
        minCellHeight = style.density.minCellHeight,
        showSecondaryLabel = style.density.showSecondaryLabel,
        cellBorderWidth = style.cellBorderWidth,
        cellBorderColor = style.cellBorderColor,
        turnoverDates = emptySet(),
        dayClick = null,
    )
}

/**
 * [DefaultDay] rendered with a [CalendarStyle] for hospitality / booking
 * calendars, such as [CalendarDefaults.hospitalityStyle].
 *
 * [turnoverDates] are the days where one stay checks out in the morning
 * and the next checks in in the afternoon. With a split
 * [CalendarDayShapes.rangeEndpointStyle] ([RangeEndpointStyle.SplitHorizontal]
 * or [RangeEndpointStyle.SplitDiagonal]) these cells fill both the AM and
 * the PM half with a 2.dp gap between them; [RangeEndpointStyle.FullCell]
 * ignores them. Pass an empty set when there are none.
 *
 * Taps on [CalendarDay]s outside the rendered month (InDate / OutDate)
 * follow [outDateClickPolicy] through [CalendarSelectionState.onDayClick]:
 * [OutDateClickPolicy.Ignore] does nothing, [OutDateClickPolicy.SelectOnly]
 * selects the date, and [OutDateClickPolicy.SelectAndScrollToMonth] also
 * animates [calendarState] to the date's month (it only selects when
 * [calendarState] is null). [onClick] runs only for taps that were not
 * ignored. All other parameters behave like the other `style` overload.
 */
@Composable
public fun DefaultDay(
    day: CalendarDay,
    style: CalendarStyle,
    turnoverDates: Set<LocalDate>,
    modifier: Modifier = Modifier,
    selectionState: CalendarSelectionState? = null,
    calendarState: CalendarState? = null,
    outDateClickPolicy: OutDateClickPolicy = OutDateClickPolicy.SelectOnly,
    today: LocalDate = currentDate(),
    info: DayInfo? = null,
    enabled: Boolean = true,
    showOutDates: Boolean = true,
    animateSelection: Boolean = true,
    hapticsEnabled: Boolean = true,
    decorators: List<DayDecorator> = emptyList(),
    strings: CalendarDayStrings = CalendarDefaults.dayStrings(),
    onClick: ((CalendarDay) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    DefaultDayContent(
        day = day,
        modifier = modifier,
        selectionState = selectionState,
        today = today,
        colors = style.colors,
        shapes = style.shapes,
        info = info,
        enabled = enabled,
        showOutDates = showOutDates,
        animateSelection = animateSelection,
        selectedElevation = style.selectedElevation,
        hapticsEnabled = hapticsEnabled,
        decorators = decorators,
        strings = strings,
        onClick = onClick,
        minCellHeight = style.density.minCellHeight,
        showSecondaryLabel = style.density.showSecondaryLabel,
        cellBorderWidth = style.cellBorderWidth,
        cellBorderColor = style.cellBorderColor,
        turnoverDates = turnoverDates,
        dayClick = { clicked ->
            if (selectionState == null) {
                clicked.position == DayPosition.MonthDate ||
                    outDateClickPolicy != OutDateClickPolicy.Ignore
            } else {
                selectionState.handleDayClick(clicked, calendarState, outDateClickPolicy, scope)
            }
        },
    )
}

/**
 * Shared body of the [DefaultDay] overloads. [dayClick] replaces the plain
 * [CalendarSelectionState.click] when set and returns whether the tap was
 * handled; [onClick] and haptics run only for handled taps.
 */
@Composable
private fun DefaultDayContent(
    day: CalendarDay,
    modifier: Modifier,
    selectionState: CalendarSelectionState?,
    today: LocalDate,
    colors: CalendarDayColors,
    shapes: CalendarDayShapes,
    info: DayInfo?,
    enabled: Boolean,
    showOutDates: Boolean,
    animateSelection: Boolean,
    selectedElevation: Dp,
    hapticsEnabled: Boolean,
    decorators: List<DayDecorator>,
    strings: CalendarDayStrings,
    onClick: ((CalendarDay) -> Unit)?,
    minCellHeight: Dp,
    showSecondaryLabel: Boolean,
    cellBorderWidth: Dp,
    cellBorderColor: Color,
    turnoverDates: Set<LocalDate>,
    dayClick: ((CalendarDay) -> Boolean)?,
) {
    val hidden = !showOutDates && day.position != DayPosition.MonthDate
    if (hidden) {
        Box(modifier.fillMaxWidth().heightIn(min = minCellHeight))
        return
    }
    val isToday = day.date == today
    val available = enabled && selectionState?.isDisabled(day.date) != true
    val isSelected = selectionState?.isSelected(day.date) == true
    val isInRange = selectionState?.isInRange(day.date) == true
    val isRangeStart = selectionState?.isRangeStart(day.date) == true
    val isRangeEnd = selectionState?.isRangeEnd(day.date) == true
    val endpointStyle = shapes.rangeEndpointStyle
    val split = endpointStyle != RangeEndpointStyle.FullCell
    val isTurnover = split && day.date in turnoverDates
    // Split styles draw range endpoints as half-cell fills instead of selectedShape.
    val splitEndpoint = split && (isRangeStart || isRangeEnd)
    val showSelectedShape = isSelected && !splitEndpoint
    val indicator = shapes.todayIndicator
    val label = info?.label?.takeIf { showSecondaryLabel }
    val showCellBorder = cellBorderWidth > 0.dp
    val selectedScale by animateFloatAsState(
        targetValue = if (showSelectedShape) 1f else 0f,
        animationSpec =
            if (animateSelection) {
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
            } else {
                snap()
            },
        label = "daySelection",
    )
    // The band's start half (toward the previous day) and end half animate
    // separately: Full fills both, a range start only the end half and a
    // range end only the start half, so switching modes or clearing the
    // range fades each half instead of snapping.
    val bandStartAlpha by animateFloatAsState(
        targetValue = if (isInRange || (isRangeEnd && !split)) 1f else 0f,
        animationSpec = if (animateSelection) tween(durationMillis = 200) else snap(),
        label = "dayRangeBandStart",
    )
    val bandEndAlpha by animateFloatAsState(
        targetValue = if (isInRange || (isRangeStart && !split)) 1f else 0f,
        animationSpec = if (animateSelection) tween(durationMillis = 200) else snap(),
        label = "dayRangeBandEnd",
    )
    // Split endpoints: the AM half (check-out, toward the previous day) and
    // the PM half (check-in, toward the next day) fade like the band halves.
    val amHalfAlpha by animateFloatAsState(
        targetValue = if (split && (isRangeEnd || isTurnover)) 1f else 0f,
        animationSpec = if (animateSelection) tween(durationMillis = 200) else snap(),
        label = "dayCheckOutHalf",
    )
    val pmHalfAlpha by animateFloatAsState(
        targetValue = if (split && (isRangeStart || isTurnover)) 1f else 0f,
        animationSpec = if (animateSelection) tween(durationMillis = 200) else snap(),
        label = "dayCheckInHalf",
    )
    val elevation by animateDpAsState(
        targetValue = if (showSelectedShape) selectedElevation else 0.dp,
        animationSpec = if (animateSelection) spring(stiffness = Spring.StiffnessMediumLow) else snap(),
        label = "dayElevation",
    )
    val description =
        remember(day.date) {
            PlatformDateFormat.formatDate(day.date, DateFormattingStyle.Long)
        }
    val dayState =
        listOfNotNull(
            strings.rangeStart.takeIf { isRangeStart },
            strings.rangeEnd.takeIf { isRangeEnd },
            strings.inRange.takeIf { isInRange },
            strings.today.takeIf { isToday },
            strings.unavailable.takeIf { !available },
        ).joinToString(", ")
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    // The ripple shows presses only; hover and focus have their own fill and ring.
    val pressInteractions = remember(interactionSource) { PressOnlyInteractionSource(interactionSource) }
    val showFocusRing = isFocused && available
    val showHover = isHovered && available && !isSelected
    val contentColor =
        when {
            !available -> colors.unavailableContentColor
            splitEndpoint || isTurnover -> colors.inRangeContentColor
            isSelected -> colors.selectedContentColor
            isInRange -> colors.inRangeContentColor
            day.position != DayPosition.MonthDate -> colors.outDateContentColor
            isToday && indicator is TodayIndicator.FilledCircle -> colors.selectedContentColor
            isToday -> colors.todayContentColor
            else -> colors.contentColor
        }
    val hasOverlay =
        (isToday && !isSelected && (indicator is TodayIndicator.Ring || indicator is TodayIndicator.Underline)) ||
            decorators.any { it.layer == DecoratorLayer.Over } ||
            showFocusRing ||
            showCellBorder
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = minCellHeight)
                .drawBehind {
                    if (shapes.inRangeShape == RectangleShape) {
                        drawRangeBand(bandStartAlpha, bandEndAlpha, colors.inRangeContainerColor)
                    }
                    if (split) {
                        drawSplitEndpoint(
                            style = endpointStyle,
                            amAlpha = amHalfAlpha,
                            pmAlpha = pmHalfAlpha,
                            gap = if (isTurnover) TurnoverGap.toPx() else 0f,
                            color = colors.inRangeContainerColor,
                        )
                    }
                }.selectable(
                    selected = isSelected,
                    enabled = available,
                    interactionSource = interactionSource,
                    // Ripple is drawn by the shape-clipped layer below, not the full-cell touch target.
                    role = Role.Button,
                    indication = null,
                    onClick = {
                        val handled =
                            if (dayClick != null) {
                                dayClick(day)
                            } else {
                                selectionState?.click(day.date)
                                true
                            }
                        if (handled) {
                            if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onClick?.invoke(day)
                        }
                    },
                ).semantics {
                    contentDescription = description
                    if (dayState.isNotEmpty()) stateDescription = dayState
                },
        contentAlignment = Alignment.Center,
    ) {
        // Shadow layer: same centered-square geometry as the ripple layer,
        // drawn first so the selection fill covers the shape's interior.
        if (elevation > 0.dp) {
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .aspectRatio(1f)
                        .shadow(elevation, shapes.selectedShape, clip = false),
                )
            }
        }
        // Clipped fill layer: container, unavailable, today fill, selection, Behind decorators.
        Box(
            Modifier
                .matchParentSize()
                .background(colors.containerColor, RectangleShape)
                .clip(shapes.dayShape)
                .then(
                    if (!available) Modifier.background(colors.unavailableContainerColor) else Modifier,
                ).drawBehind {
                    val segmentAlpha = minOf(bandStartAlpha, bandEndAlpha)
                    if (segmentAlpha > 0f && shapes.inRangeShape != RectangleShape) {
                        val bandColor = colors.inRangeContainerColor
                        drawDayShape(shapes.inRangeShape, bandColor.copy(alpha = bandColor.alpha * segmentAlpha))
                    }
                    if (isToday && !isSelected && indicator is TodayIndicator.FilledCircle) {
                        drawDayShape(shapes.dayShape, colors.todayIndicatorColor)
                    }
                    if (showHover) {
                        drawDayShape(shapes.dayShape, colors.hoverContainerColor)
                    }
                    if (selectedScale > 0f) {
                        scale(selectedScale) {
                            drawDayShape(shapes.selectedShape, colors.selectedContainerColor)
                        }
                    }
                    val cellState = DayCellDrawState(selectedScale >= 0.5f, colors.selectedContentColor, label != null)
                    decorators.forEach { decorator ->
                        if (decorator.layer == DecoratorLayer.Behind) {
                            drawDecorator(decorator, day, cellState)
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
                    .indication(pressInteractions, ripple()),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.date.day.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
            if (label != null && info != null) {
                val colorArgb = info.colorArgb
                val resolvedLabelColor =
                    when {
                        !available -> colors.unavailableContentColor
                        splitEndpoint || isTurnover -> colors.inRangeContentColor
                        isSelected -> colors.selectedContentColor
                        colorArgb != null -> Color(colorArgb)
                        info.isHoliday -> colors.holidayLabelColor
                        else -> colors.labelColor
                    }
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = resolvedLabelColor,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
            }
        }
        // Unclipped overlay: today Ring/Underline, Over decorators (badges) and the
        // focus ring are never cropped by the cell shape.
        if (hasOverlay) {
            Box(
                Modifier.matchParentSize().drawBehind {
                    if (showCellBorder) {
                        drawDayShape(shapes.dayShape, cellBorderColor, Stroke(width = cellBorderWidth.toPx()))
                    }
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
                    val cellState = DayCellDrawState(selectedScale >= 0.5f, colors.selectedContentColor, label != null)
                    decorators.forEach { decorator ->
                        if (decorator.layer == DecoratorLayer.Over) {
                            drawDecorator(decorator, day, cellState)
                        }
                    }
                    if (showFocusRing) {
                        val focusStrokePx = shapes.focusedBorderWidth.toPx()
                        if (selectedScale >= 0.5f) {
                            drawDayShape(
                                shapes.dayShape,
                                colors.selectedContentColor,
                                Stroke(width = focusStrokePx + 2.dp.toPx()),
                            )
                        }
                        drawDayShape(
                            shapes.dayShape,
                            colors.focusedBorderColor,
                            Stroke(width = focusStrokePx),
                        )
                    }
                },
            )
        }
    }
}

/** Forwards only [PressInteraction]s from [source]. */
private class PressOnlyInteractionSource(
    source: InteractionSource,
) : InteractionSource {
    override val interactions: Flow<Interaction> = source.interactions.filter { it is PressInteraction }
}

/**
 * Draws the range connector band as a vertically centered bar whose
 * height matches the endpoint circle diameter, so a completed range
 * reads as one continuous pill capped by the endpoint fills. The half
 * facing the previous day uses [startAlpha] and the half facing the next
 * day [endAlpha] (sides flipped in RTL); equal alphas draw one rect so
 * no seam shows at the center while both halves fade together.
 */
private fun DrawScope.drawRangeBand(
    startAlpha: Float,
    endAlpha: Float,
    color: Color,
) {
    if (startAlpha <= 0f && endAlpha <= 0f) return
    val height = size.minDimension
    val top = (size.height - height) / 2f
    val center = size.width / 2f
    val ltr = layoutDirection == LayoutDirection.Ltr

    fun half(
        left: Float,
        right: Float,
        alpha: Float,
    ) {
        if (alpha > 0f) {
            drawRect(
                color = color.copy(alpha = color.alpha * alpha),
                topLeft = Offset(left, top),
                size = Size(right - left, height),
            )
        }
    }

    when {
        startAlpha == endAlpha -> {
            half(0f, size.width, startAlpha)
        }

        ltr -> {
            half(0f, center, startAlpha)
            half(center, size.width, endAlpha)
        }

        else -> {
            half(center, size.width, startAlpha)
            half(0f, center, endAlpha)
        }
    }
}

/** Gap between the check-out (AM) and check-in (PM) halves of a turnover day. */
private val TurnoverGap = 2.dp

/**
 * Draws the split range endpoint halves in the same vertically centered
 * strip as the range band: [amAlpha] fills the check-out half facing the
 * previous day and [pmAlpha] the check-in half facing the next day (left
 * and right in LTR, mirrored in RTL). [RangeEndpointStyle.SplitHorizontal]
 * splits at the vertical center line, [RangeEndpointStyle.SplitDiagonal]
 * along the bottom-left to top-right diagonal (top-left triangle = AM,
 * bottom-right = PM). A positive [gap] keeps that much space between the
 * halves, measured perpendicular to the split line.
 */
private fun DrawScope.drawSplitEndpoint(
    style: RangeEndpointStyle,
    amAlpha: Float,
    pmAlpha: Float,
    gap: Float,
    color: Color,
) {
    if (amAlpha <= 0f && pmAlpha <= 0f) return
    val w = size.width
    val h = size.minDimension
    val top = (size.height - h) / 2f
    val ltr = layoutDirection == LayoutDirection.Ltr

    // x in LTR terms (0 = edge toward the previous day), mirrored in RTL.
    fun x(value: Float): Float = if (ltr) value else w - value

    fun fill(
        alpha: Float,
        vararg points: Offset,
    ) {
        if (alpha <= 0f) return
        val path =
            Path().apply {
                moveTo(x(points[0].x), top + points[0].y)
                for (i in 1 until points.size) lineTo(x(points[i].x), top + points[i].y)
                close()
            }
        drawPath(path, color.copy(alpha = color.alpha * alpha.coerceIn(0f, 1f)))
    }

    when (style) {
        RangeEndpointStyle.FullCell -> {
            Unit
        }

        RangeEndpointStyle.SplitHorizontal -> {
            val half = gap / 2f
            val center = w / 2f
            fill(amAlpha, Offset(0f, 0f), Offset(center - half, 0f), Offset(center - half, h), Offset(0f, h))
            fill(pmAlpha, Offset(center + half, 0f), Offset(w, 0f), Offset(w, h), Offset(center + half, h))
        }

        RangeEndpointStyle.SplitDiagonal -> {
            // The diagonal is x / w + y / h = 1; moving it by d (perpendicular)
            // shifts the right-hand side by d * sqrt(1 / w² + 1 / h²).
            val shift = (gap / 2f) * sqrt(1f / (w * w) + 1f / (h * h))
            val am = 1f - shift
            val pm = shift
            fill(amAlpha, Offset(0f, 0f), Offset(am * w, 0f), Offset(0f, am * h))
            fill(pmAlpha, Offset(w, pm * h), Offset(w, h), Offset(pm * w, h))
        }
    }
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
