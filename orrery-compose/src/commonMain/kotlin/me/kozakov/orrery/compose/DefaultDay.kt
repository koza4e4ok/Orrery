package me.kozakov.orrery.compose

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
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
 * Available, unselected in-month days outside a range whose day of week is
 * in [CalendarDayColors.weekendDays] draw their number in
 * [CalendarDayColors.weekendContentColor] (today keeps its own color). Under
 * `LayoutDirection.Rtl` the range band halves and split endpoint halves
 * are mirrored so they still face the previous and next days.
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
 * fills them like the range with a thin diagonal divider. Pass an empty set
 * when there are none.
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
        Box(modifier.fillMaxWidth().defaultMinSize(minHeight = minCellHeight))
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
    val isTurnover = day.date in turnoverDates
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
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var contentBottomPx by remember { mutableFloatStateOf(Float.NaN) }
    val hoverAlpha by animateFloatAsState(
        targetValue = if (showHover) 1f else 0f,
        animationSpec = if (animateSelection) tween(durationMillis = 150) else snap(),
        label = "DefaultDayHoverAlpha",
    )
    val targetContentColor =
        when {
            !available -> colors.unavailableContentColor
            splitEndpoint || isTurnover -> colors.inRangeContentColor
            isSelected -> colors.selectedContentColor
            isInRange -> colors.inRangeContentColor
            day.position != DayPosition.MonthDate -> colors.outDateContentColor
            isToday && indicator is TodayIndicator.FilledCircle -> colors.selectedContentColor
            isToday -> colors.todayContentColor
            day.date.dayOfWeek in colors.weekendDays -> colors.weekendContentColor
            else -> colors.contentColor
        }
    val contentColor by animateColorAsState(
        targetValue = targetContentColor,
        animationSpec = if (animateSelection) tween(durationMillis = 150) else snap(),
        label = "DefaultDayContentColor",
    )
    val hasOverlay =
        (isToday && !isSelected && (indicator is TodayIndicator.Ring || indicator is TodayIndicator.Underline)) ||
            decorators.any { it.layer == DecoratorLayer.Over } ||
            showFocusRing ||
            showCellBorder
    // Negative insets are treated as zero; drawing clamps oversized ones to half the cell height.
    val dayInset = shapes.verticalInset.coerceAtLeast(0.dp)
    val surface = MaterialTheme.colorScheme.surface
    val turnoverDividerColor = if (colors.containerColor.alpha > 0f) colors.containerColor else surface
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = minCellHeight)
                // The full cell is the touch target and keeps its measured height; only what is
                // drawn is inset by dayInset, so the shapes of adjacent weeks never touch.
                .selectable(
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
                }.drawBehind {
                    val insetPx = minOf(dayInset.toPx(), size.height / 2f)
                    inset(left = 0f, top = insetPx, right = 0f, bottom = insetPx) {
                        if (shapes.inRangeShape == RectangleShape) {
                            drawRangeBand(bandStartAlpha, bandEndAlpha, colors.inRangeContainerColor, isRtl)
                        }
                        if (split) {
                            drawSplitEndpoint(
                                style = endpointStyle,
                                amAlpha = amHalfAlpha,
                                pmAlpha = pmHalfAlpha,
                                gap = if (isTurnover) TurnoverGap.toPx() else 0f,
                                color = colors.inRangeContainerColor,
                                isRtl = isRtl,
                            )
                        } else if (isTurnover) {
                            // Full-cell turnover: filled like the range, with a thin divider
                            // where a split cell would part.
                            drawRect(colors.inRangeContainerColor)
                            val start = if (isRtl) Offset(size.width, size.height) else Offset(0f, size.height)
                            val end = if (isRtl) Offset(0f, 0f) else Offset(size.width, 0f)
                            drawLine(turnoverDividerColor, start, end, TurnoverGap.toPx())
                        }
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        // Shadow layer: same centered-square geometry as the ripple layer,
        // drawn first so the selection fill covers the shape's interior.
        if (elevation > 0.dp) {
            Box(Modifier.matchParentSize().padding(vertical = dayInset), contentAlignment = Alignment.Center) {
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
                .padding(vertical = dayInset)
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
                    if (hoverAlpha > 0f) {
                        val hoverColor = colors.hoverContainerColor
                        drawDayShape(shapes.dayShape, hoverColor.copy(alpha = hoverColor.alpha * hoverAlpha))
                    }
                    if (selectedScale > 0f) {
                        scale(selectedScale) {
                            drawDayShape(shapes.selectedShape, colors.selectedContainerColor)
                        }
                    }
                    val cellState =
                        DayCellDrawState(
                            selectedScale >= 0.5f,
                            colors.selectedContentColor,
                            label != null,
                            contentBottom = contentBottomPx,
                        )
                    drawDecorators(decorators, DecoratorLayer.Behind, day, cellState)
                },
        )
        // Ripple layer: a centered square clipped to dayShape so touch feedback
        // matches the drawn selection (circle by default, or any custom shape).
        Box(Modifier.matchParentSize().padding(vertical = dayInset), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxSize()
                    .aspectRatio(1f)
                    .clip(shapes.dayShape)
                    .indication(pressInteractions, ripple()),
            )
        }
        val reserveDecoratorBand = label != null && decorators.isNotEmpty()
        val decoratorBandPx = with(LocalDensity.current) { DecoratorBand.toPx() }
        val dayInsetPx = with(LocalDensity.current) { dayInset.toPx() }
        Column(
            // Built-in stacked decorators read contentBottom to stay below the number and label.
            modifier =
                Modifier
                    .then(if (reserveDecoratorBand) Modifier.padding(vertical = DecoratorBand) else Modifier)
                    // onPlaced sees the padded bounds; contentBottom must stay at the label's real bottom.
                    .onPlaced {
                        val band = if (reserveDecoratorBand) decoratorBandPx else 0f
                        // Decorators draw in the inset area, so measure from its top.
                        contentBottomPx = it.boundsInParent().bottom - band - dayInsetPx
                    },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = day.date.day.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
            if (label != null && info != null) {
                val resolvedLabelColor =
                    resolveDayLabelColor(
                        colors = colors,
                        available = available,
                        onRangeEndpoint = splitEndpoint || isTurnover,
                        isSelected = isSelected,
                        isOutDate = day.position != DayPosition.MonthDate,
                        colorArgb = info.colorArgb,
                        isHoliday = info.isHoliday,
                    )
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
                Modifier.matchParentSize().padding(vertical = dayInset).drawBehind {
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
                    val cellState =
                        DayCellDrawState(
                            selectedScale >= 0.5f,
                            colors.selectedContentColor,
                            label != null,
                            contentBottom = contentBottomPx,
                        )
                    drawDecorators(decorators, DecoratorLayer.Over, day, cellState)
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
    isRtl: Boolean,
) {
    if (startAlpha <= 0f && endAlpha <= 0f) return
    val height = size.minDimension
    val top = (size.height - height) / 2f

    fun half(
        bounds: Pair<Float, Float>,
        alpha: Float,
    ) {
        if (alpha > 0f) {
            drawRect(
                color = color.copy(alpha = color.alpha * alpha),
                topLeft = Offset(bounds.first, top),
                size = Size(bounds.second - bounds.first, height),
            )
        }
    }

    if (startAlpha == endAlpha) {
        half(0f to size.width, startAlpha)
    } else {
        half(rangeBandHalfBounds(size.width, towardPrevious = true, isRtl = isRtl), startAlpha)
        half(rangeBandHalfBounds(size.width, towardPrevious = false, isRtl = isRtl), endAlpha)
    }
}

/**
 * Left and right x of the range band half facing the previous day
 * ([towardPrevious]) or the next day in a cell [width] wide: in LTR the
 * previous day is on the left, so a range start fills the right half and a
 * range end the left half; RTL mirrors both.
 */
internal fun rangeBandHalfBounds(
    width: Float,
    towardPrevious: Boolean,
    isRtl: Boolean,
): Pair<Float, Float> {
    val center = width / 2f
    return if (towardPrevious != isRtl) 0f to center else center to width
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
    isRtl: Boolean,
) {
    if (amAlpha <= 0f && pmAlpha <= 0f) return
    val h = size.minDimension
    val top = (size.height - h) / 2f
    val (amPoints, pmPoints) = splitEndpointPolygons(style, size.width, h, gap, isRtl)

    fun fill(
        alpha: Float,
        points: List<Offset>,
    ) {
        if (alpha <= 0f || points.isEmpty()) return
        val path =
            Path().apply {
                moveTo(points[0].x, top + points[0].y)
                for (i in 1 until points.size) lineTo(points[i].x, top + points[i].y)
                close()
            }
        drawPath(path, color.copy(alpha = color.alpha * alpha.coerceIn(0f, 1f)))
    }

    fill(amAlpha, amPoints)
    fill(pmAlpha, pmPoints)
}

/**
 * Vertices of the check-out (AM) and check-in (PM) halves of a split range
 * endpoint in a [w] × [h] strip, with [gap] between them. In LTR the AM half
 * faces the previous day on the left: [RangeEndpointStyle.SplitHorizontal]
 * puts it in the left half, [RangeEndpointStyle.SplitDiagonal] in the
 * top-left triangle (PM bottom-right). RTL mirrors every x (`x' = w - x`),
 * so AM sits on the right / top-right and PM on the left / bottom-left.
 * [RangeEndpointStyle.FullCell] has no halves (two empty lists).
 */
internal fun splitEndpointPolygons(
    style: RangeEndpointStyle,
    w: Float,
    h: Float,
    gap: Float,
    isRtl: Boolean,
): Pair<List<Offset>, List<Offset>> {
    val (am, pm) =
        when (style) {
            RangeEndpointStyle.FullCell -> {
                emptyList<Offset>() to emptyList()
            }

            RangeEndpointStyle.SplitHorizontal -> {
                val center = w / 2f
                val left = center - gap / 2f
                val right = center + gap / 2f
                listOf(Offset(0f, 0f), Offset(left, 0f), Offset(left, h), Offset(0f, h)) to
                    listOf(Offset(right, 0f), Offset(w, 0f), Offset(w, h), Offset(right, h))
            }

            RangeEndpointStyle.SplitDiagonal -> {
                // The diagonal is x / w + y / h = 1; moving it by d (perpendicular)
                // shifts the right-hand side by d * sqrt(1 / w² + 1 / h²).
                val shift = (gap / 2f) * sqrt(1f / (w * w) + 1f / (h * h))
                val amEdge = 1f - shift
                listOf(Offset(0f, 0f), Offset(amEdge * w, 0f), Offset(0f, amEdge * h)) to
                    listOf(Offset(w, shift * h), Offset(w, h), Offset(shift * w, h))
            }
        }
    if (!isRtl) return am to pm
    return am.map { Offset(w - it.x, it.y) } to pm.map { Offset(w - it.x, it.y) }
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

/** Secondary label color: the out-date dimming wins over the label's own color, like the day number. */
internal fun resolveDayLabelColor(
    colors: CalendarDayColors,
    available: Boolean,
    onRangeEndpoint: Boolean,
    isSelected: Boolean,
    isOutDate: Boolean,
    colorArgb: Long?,
    isHoliday: Boolean,
): Color =
    when {
        !available -> colors.unavailableContentColor
        onRangeEndpoint -> colors.inRangeContentColor
        isSelected -> colors.selectedContentColor
        isOutDate -> colors.outDateContentColor
        colorArgb != null -> Color(colorArgb)
        isHoliday -> colors.holidayLabelColor
        else -> colors.labelColor
    }

/** Material's disabled-content alpha, applied to every decorator on an out-of-month day. */
internal const val OUT_DATE_DECORATOR_ALPHA = 0.38f

/** Space kept above and below a labelled day's text so bottom decorators never draw over the label. */
internal val DecoratorBand = 6.dp

/** Draws [decorators] of [layer], through a translucent layer on out-of-month days. */
internal fun DrawScope.drawDecorators(
    decorators: List<DayDecorator>,
    layer: DecoratorLayer,
    day: CalendarDay,
    cell: DayCellDrawState,
) {
    val chosen = decorators.filter { it.layer == layer }
    if (chosen.isEmpty()) return
    if (day.position == DayPosition.MonthDate) {
        chosen.forEach { drawDecorator(it, day, cell) }
        return
    }
    val canvas = drawContext.canvas
    canvas.saveLayer(size.toRect(), Paint().apply { alpha = OUT_DATE_DECORATOR_ALPHA })
    chosen.forEach { drawDecorator(it, day, cell) }
    canvas.restore()
}
