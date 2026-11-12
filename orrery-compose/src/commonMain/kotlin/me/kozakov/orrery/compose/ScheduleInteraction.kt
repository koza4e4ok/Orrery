@file:Suppress("TooManyFunctions")

package me.kozakov.orrery.compose

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.DayScheduleLayout
import me.kozakov.orrery.core.PositionedEvent
import kotlin.math.abs
import kotlin.math.roundToInt

/** Editing callbacks and options of one schedule. With every callback null the schedule is read-only. */
@Suppress("LongParameterList")
internal class ScheduleEditConfig<T>(
    val onEmptySlotLongPress: ((LocalDate, LocalTime) -> Unit)?,
    val onSlotCreate: ((LocalDate, LocalTime, LocalTime) -> Unit)?,
    val onEventMove: ((CalendarEvent<T>, LocalDate, LocalTime, LocalTime) -> Unit)?,
    val onEventResize: ((CalendarEvent<T>, LocalTime, LocalTime) -> Unit)?,
    val snapMinutes: Int,
    val hapticsEnabled: Boolean,
    val minEventDurationMinutes: Int,
    val eventsClickable: Boolean,
) {
    val createEnabled: Boolean get() = onEmptySlotLongPress != null || onSlotCreate != null
    val enabled: Boolean get() = createEnabled || onEventMove != null || onEventResize != null
    val minResizeDuration: Int get() = maxOf(snapMinutes, minEventDurationMinutes)
}

/**
 * Transient state of the edit in flight, shared by the pages of one schedule.
 * Not part of [ScheduleState]: there is nothing to save or restore.
 */
@Stable
internal class ScheduleDragController<T> {
    /** The ghost to draw: a pointer drag, or the keyboard caret of a focused day column. */
    var active: ScheduleDragPreview<T>? by mutableStateOf(null)

    /** True while a pointer owns [active]; paging is frozen meanwhile. */
    var pointerDrag: Boolean by mutableStateOf(false)

    /** The pointer left the time grid; the ghost stays clamped. */
    var outOfBounds: Boolean by mutableStateOf(false)

    /** Auto-scroll speed in px/s while the pointer is near the top (negative) or bottom edge. */
    var edgeSpeed: Float by mutableFloatStateOf(0f)

    /** Set by Escape; the running drag stops without a callback. */
    var cancelled: Boolean = false

    /** Re-reads the pointer position after the grid scrolled under it. */
    var onAutoScrolled: (() -> Unit)? = null

    private var caretDate: LocalDate? = null
    private var caret: MinuteRange? = null

    fun cancelPointerDrag(): Boolean {
        if (!pointerDrag) return false
        cancelled = true
        clear()
        return true
    }

    fun clear() {
        active = null
        pointerDrag = false
        outOfBounds = false
        edgeSpeed = 0f
        onAutoScrolled = null
    }

    fun caretFor(date: LocalDate): MinuteRange? = caret?.takeIf { caretDate == date && !pointerDrag && active != null }

    fun showCaret(
        date: LocalDate,
        range: MinuteRange,
    ) {
        if (pointerDrag) return
        caretDate = date
        caret = range
        active =
            ScheduleDragPreview(
                kind = ScheduleDragKind.Create,
                event = null,
                date = date,
                startTime = minuteToLocalTime(range.start),
                endTime = minuteToLocalTime(range.end),
            )
    }

    fun hideCaret(date: LocalDate) {
        if (caretDate != date) return
        caretDate = null
        caret = null
        if (!pointerDrag) active = null
    }

    /** Alpha of an event's own tile: dimmed while it is moved, hidden while its ghost is resized. */
    fun sourceAlpha(
        event: CalendarEvent<*>,
        override: ScheduleDragPreview<*>?,
    ): Float {
        val preview = override ?: active?.takeIf { pointerDrag } ?: return 1f
        if (preview.event != event) return 1f
        return if (preview.kind == ScheduleDragKind.Move) MOVE_SOURCE_ALPHA else 0f
    }
}

private const val MOVE_SOURCE_ALPHA = 0.35f

/** Test seam: a fixed ghost for snapshot tests, so mid-drag frames need no held pointer. */
internal val LocalScheduleDragOverride = compositionLocalOf<ScheduleDragPreview<*>?> { null }

/** True when the ghost is near the grid top, so [ScheduleDefaults.DragGhost] puts its time chip below. */
internal val LocalGhostChipBelow = staticCompositionLocalOf { false }

/** What the gesture handler needs to know about the page it sits on. */
@Suppress("LongParameterList")
internal class EditGrid<T>(
    val layouts: List<DayScheduleLayout<T>>,
    val hourHeight: Dp,
    val window: HourWindow,
    val dimensions: ScheduleDimensions,
    val config: ScheduleEditConfig<T>,
    val isRtl: Boolean,
    val scrollState: ScrollState,
)

/** Per-column editing context handed to the day columns. */
@Suppress("LongParameterList")
internal class ColumnEditing<T>(
    val config: ScheduleEditConfig<T>,
    val controller: ScheduleDragController<T>,
    val pageDates: List<LocalDate>,
    val colors: ScheduleColors,
    val dimensions: ScheduleDimensions,
    val strings: ScheduleStrings,
    val scrollState: ScrollState,
)

internal const val HANDLE_START: Int = 1
internal const val HANDLE_END: Int = 2

private val MinHeightForEndHandle = 20.dp
private val MinHeightForBothHandles = 36.dp
private val HandleInset = 3.dp
private val HandleTouchHalfWidth = 24.dp
private val AutoScrollEdge = 32.dp
private val AutoScrollMaxSpeed = 240.dp
private val GhostChipFlipDistance = 24.dp

/**
 * Which resize handles a tile of [heightPx] shows, as [HANDLE_START] / [HANDLE_END] bits:
 * both from 36.dp, only the end handle from 20.dp, none below, and never on a
 * side that is cut by the visible hours.
 */
internal fun Density.resizeHandleSides(
    heightPx: Float,
    positioned: PositionedEvent<*>,
    real: MinuteRange?,
): Int {
    if (real == null) return 0
    var sides = 0
    if (heightPx >= MinHeightForEndHandle.toPx() && positioned.endMinute == real.end) {
        sides = sides or HANDLE_END
    }
    if (heightPx >= MinHeightForBothHandles.toPx() && positioned.startMinute == real.start) {
        sides = sides or HANDLE_START
    }
    return sides
}

internal fun DrawScope.drawResizeHandles(
    sides: Int,
    color: Color,
    dimensions: ScheduleDimensions,
) {
    val inset = HandleInset.toPx()
    val width = minOf(dimensions.resizeHandleWidth.toPx(), size.width - 2 * inset)
    val thickness = dimensions.resizeHandleThickness.toPx()
    if (sides == 0 || width <= 0f) return
    val x = (size.width - width) / 2f
    val radius = CornerRadius(thickness / 2f)
    if (sides and HANDLE_START != 0) {
        drawRoundRect(color, Offset(x, inset), Size(width, thickness), radius)
    }
    if (sides and HANDLE_END != 0) {
        drawRoundRect(color, Offset(x, size.height - inset - thickness), Size(width, thickness), radius)
    }
}

private class GridHit<T>(
    val column: Int,
    val tile: PositionedEvent<T>?,
    val handle: ScheduleDragKind?,
)

private class TileRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    fun contains(
        x: Float,
        y: Float,
    ): Boolean = x in left..left + width && y in top..top + height
}

/** A tile's rectangle in its column, [TileRect.left] measured from the column's start edge. */
private fun <T> Density.tileRect(
    grid: EditGrid<T>,
    positioned: PositionedEvent<T>,
    columnWidth: Float,
): TileRect {
    val hourPx = grid.hourHeight.toPx()
    return TileRect(
        left = columnWidth * positioned.leftFraction,
        top = (positioned.startMinute - grid.window.startMinute) / 60f * hourPx,
        width = columnWidth * positioned.widthFraction - grid.dimensions.tileHorizontalGap.toPx(),
        height =
            (positioned.displayEndMinute - positioned.startMinute) / 60f * hourPx -
                grid.dimensions.tileVerticalGap.toPx(),
    )
}

private fun <T> columnAt(
    grid: EditGrid<T>,
    x: Float,
    width: Int,
): Int {
    val count = grid.layouts.size
    val visual = (x / (width.toFloat() / count)).toInt().coerceIn(0, count - 1)
    return if (grid.isRtl) count - 1 - visual else visual
}

/** Finds the column, the top-most tile and the resize handle zone under [position]. */
private fun <T> Density.hitTest(
    grid: EditGrid<T>,
    position: Offset,
    width: Int,
): GridHit<T> {
    val count = grid.layouts.size
    val columnWidth = width.toFloat() / count
    val column = columnAt(grid, position.x, width)
    val visual = if (grid.isRtl) count - 1 - column else column
    val inColumn = position.x - visual * columnWidth
    val fromStart = if (grid.isRtl) columnWidth - inColumn else inColumn
    var tile: PositionedEvent<T>? = null
    var rect: TileRect? = null
    for (positioned in grid.layouts[column].timedEvents) {
        val candidate = tileRect(grid, positioned, columnWidth)
        val inside = candidate.contains(fromStart, position.y)
        val current = tile
        if (inside && (current == null || positioned.columnIndex >= current.columnIndex)) {
            tile = positioned
            rect = candidate
        }
    }
    val hitTile = tile
    val hitRect = rect
    if (hitTile == null || hitRect == null) return GridHit(column, null, null)
    val handle =
        if (grid.config.onEventResize == null) {
            null
        } else {
            handleAt(grid, hitTile, hitRect, fromStart, position.y)
        }
    return GridHit(column, hitTile, handle)
}

private fun <T> Density.handleAt(
    grid: EditGrid<T>,
    tile: PositionedEvent<T>,
    rect: TileRect,
    fromStart: Float,
    y: Float,
): ScheduleDragKind? {
    val sides = resizeHandleSides(rect.height, tile, editableMinutes(tile.event))
    if (sides == 0 || abs(fromStart - (rect.left + rect.width / 2f)) > HandleTouchHalfWidth.toPx()) return null
    // The zones never cover more than the outer thirds, so the middle of a tile still moves it.
    val zone = minOf(grid.dimensions.resizeHandleTouchSize.toPx(), rect.height / 3f)
    return when {
        sides and HANDLE_START != 0 && y <= rect.top + zone -> ScheduleDragKind.ResizeStart
        sides and HANDLE_END != 0 && y >= rect.top + rect.height - zone -> ScheduleDragKind.ResizeEnd
        else -> null
    }
}

private class DragSession<T>(
    val kind: ScheduleDragKind,
    val tile: PositionedEvent<T>?,
    val original: MinuteRange,
    val column: Int,
    val downY: Float,
)

private class DragResult<T>(
    val preview: ScheduleDragPreview<T>,
    val moved: Boolean,
)

private fun <T> sessionFor(
    grid: EditGrid<T>,
    hit: GridHit<T>,
    downY: Float,
): DragSession<T>? {
    val tile = hit.tile
    if (tile == null) {
        if (!grid.config.createEnabled) return null
        return DragSession(ScheduleDragKind.Create, null, MinuteRange(0, 0), hit.column, downY)
    }
    val real = editableMinutes(tile.event) ?: return null
    val kind =
        when {
            hit.handle != null -> hit.handle
            grid.config.onEventMove != null -> ScheduleDragKind.Move
            else -> return null
        }
    return DragSession(kind, tile, real, hit.column, downY)
}

private fun <T> Density.previewAt(
    grid: EditGrid<T>,
    session: DragSession<T>,
    position: Offset,
    width: Int,
): ScheduleDragPreview<T> {
    val hourPx = grid.hourHeight.toPx()
    val min = grid.window.startMinute
    val max = grid.window.endMinute
    val snap = grid.config.snapMinutes
    val delta = ((position.y - session.downY) / hourPx * 60f).roundToInt()
    val original = session.original
    val minDuration = grid.config.minResizeDuration
    val range =
        when (session.kind) {
            ScheduleDragKind.Create ->
                createRange(
                    anchorMinute = minuteAt(session.downY, hourPx, grid.window),
                    pointerMinute = minuteAt(position.y, hourPx, grid.window),
                    snap = snap,
                    min = min,
                    max = max,
                )
            ScheduleDragKind.Move -> moveRange(original.start, original.end, delta, snap, min, max)
            ScheduleDragKind.ResizeStart ->
                if (delta == 0) {
                    original
                } else {
                    resizeStart(original.start, original.end, original.start + delta, snap, minDuration, min)
                }
            ScheduleDragKind.ResizeEnd ->
                if (delta == 0) {
                    original
                } else {
                    resizeEnd(original.start, original.end, original.end + delta, snap, minDuration, max)
                }
        }
    val column = if (session.kind == ScheduleDragKind.Move) columnAt(grid, position.x, width) else session.column
    return ScheduleDragPreview(
        kind = session.kind,
        event = session.tile?.event,
        date = grid.layouts[column].date,
        startTime = minuteToLocalTime(range.start),
        endTime = minuteToLocalTime(range.end),
    )
}

private fun <T> Density.unmovedCreatePreview(
    grid: EditGrid<T>,
    session: DragSession<T>,
): ScheduleDragPreview<T> {
    val anchor = minuteAt(session.downY, grid.hourHeight.toPx(), grid.window)
    val range = defaultCreateRange(anchor, grid.config.snapMinutes, grid.window.startMinute, grid.window.endMinute)
    return ScheduleDragPreview(
        kind = ScheduleDragKind.Create,
        event = null,
        date = grid.layouts[session.column].date,
        startTime = minuteToLocalTime(range.start),
        endTime = minuteToLocalTime(range.end),
    )
}

private fun Density.edgeScrollSpeed(
    viewportY: Float,
    viewportSize: Int,
): Float {
    val edge = AutoScrollEdge.toPx()
    val max = AutoScrollMaxSpeed.toPx()
    return when {
        viewportSize <= 0 -> 0f
        viewportY < edge -> -max * ((edge - viewportY) / edge).coerceIn(0f, 1f)
        viewportY > viewportSize - edge -> max * ((viewportY - (viewportSize - edge)) / edge).coerceIn(0f, 1f)
        else -> 0f
    }
}

/**
 * Create, move and resize gestures of one schedule page, attached above the day
 * columns so a drag can cross them. Touch starts after a long press (a scroll or
 * page swipe that starts first wins); mouse and stylus start once the pointer
 * passes the touch slop; resize handles start on the vertical touch slop for
 * every pointer, so a horizontal swipe from a handle still pages days.
 * Once a drag runs, every change is consumed in the initial pass, so neither
 * the tiles nor the scroll containers react.
 */
internal fun <T> Modifier.scheduleEditGestures(
    grid: State<EditGrid<T>>,
    controller: ScheduleDragController<T>,
    haptics: HapticFeedback,
): Modifier =
    pointerInput(controller) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val current = grid.value
            val session =
                sessionFor(current, hitTest(current, down.position, size.width), down.position.y)
                    ?: return@awaitEachGesture
            val config = current.config
            val precise = down.type == PointerType.Mouse || down.type == PointerType.Stylus
            val longPressOnly = session.kind == ScheduleDragKind.Create && config.onSlotCreate == null
            val isResize =
                session.kind == ScheduleDragKind.ResizeStart || session.kind == ScheduleDragKind.ResizeEnd
            val bySlop = isResize || (precise && !longPressOnly)
            val start =
                if (isResize) {
                    // Vertical slop only, so a horizontal swipe that starts on a handle still pages days.
                    awaitVerticalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                } else if (bySlop) {
                    awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                } else {
                    awaitLongPressOrCancellation(down.id)
                }
            if (start == null) return@awaitEachGesture
            if (!bySlop && config.hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            if (longPressOnly) {
                val preview = unmovedCreatePreview(current, session)
                config.onEmptySlotLongPress?.invoke(preview.date, preview.startTime)
                consumeUntilUp(down.id)
                return@awaitEachGesture
            }
            val result =
                try {
                    drag(current, session, controller, start, bySlop, haptics)
                } finally {
                    controller.clear()
                }
            if (result != null) commit(config, session, result)
        }
    }

@Suppress("LongParameterList", "CyclomaticComplexMethod", "ReturnCount")
private suspend fun <T> AwaitPointerEventScope.drag(
    grid: EditGrid<T>,
    session: DragSession<T>,
    controller: ScheduleDragController<T>,
    start: PointerInputChange,
    startedMoving: Boolean,
    haptics: HapticFeedback,
): DragResult<T>? {
    val scroll = grid.scrollState
    val slop = viewConfiguration.touchSlop
    var moved = startedMoving
    var x = start.position.x
    var viewportY = start.position.y - scroll.value

    fun update() {
        val position = Offset(x, viewportY + scroll.value)
        val preview =
            if (session.kind == ScheduleDragKind.Create && !moved) {
                unmovedCreatePreview(grid, session)
            } else {
                previewAt(grid, session, position, size.width)
            }
        if (preview != controller.active) {
            if (controller.active != null && grid.config.hapticsEnabled) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            }
            controller.active = preview
        }
        controller.outOfBounds = position.y < 0f || position.y > size.height
        controller.edgeSpeed = edgeScrollSpeed(viewportY, scroll.viewportSize)
    }

    controller.cancelled = false
    controller.pointerDrag = true
    controller.onAutoScrolled = { update() }
    update()
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == start.id }
        if (controller.cancelled) {
            if (change != null && change.pressed) {
                change.consume()
                consumeUntilUp(start.id)
            }
            return null
        }
        // A second finger (pinch-to-zoom) or a lost pointer drops the edit without a callback.
        if (change == null || event.changes.count { it.pressed } > 1) return null
        change.consume()
        if (!change.pressed) {
            val preview = controller.active ?: return null
            return DragResult(preview, moved)
        }
        if (!moved && (change.position - start.position).getDistance() > slop) moved = true
        x = change.position.x
        viewportY = change.position.y - scroll.value
        update()
    }
}

private suspend fun AwaitPointerEventScope.consumeUntilUp(id: PointerId) {
    do {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == id }
        change?.consume()
    } while (change != null && change.pressed)
}

/** Fires the one callback of a finished gesture; a drop in place fires nothing. */
private fun <T> commit(
    config: ScheduleEditConfig<T>,
    session: DragSession<T>,
    result: DragResult<T>,
) {
    val preview = result.preview
    val tile = session.tile
    val sameTimes =
        preview.startTime == minuteToLocalTime(session.original.start) &&
            preview.endTime == minuteToLocalTime(session.original.end)
    when (session.kind) {
        ScheduleDragKind.Create -> {
            val longPress = config.onEmptySlotLongPress
            if (!result.moved && longPress != null) {
                longPress(preview.date, preview.startTime)
            } else {
                config.onSlotCreate?.invoke(preview.date, preview.startTime, preview.endTime)
            }
        }
        ScheduleDragKind.Move ->
            if (tile != null && (!sameTimes || preview.date != tile.date)) {
                config.onEventMove?.invoke(tile.event, preview.date, preview.startTime, preview.endTime)
            }
        ScheduleDragKind.ResizeStart, ScheduleDragKind.ResizeEnd ->
            if (tile != null && !sameTimes) {
                config.onEventResize?.invoke(tile.event, preview.startTime, preview.endTime)
            }
    }
}

/** Escape drops a running pointer drag without a callback (needs focus inside the schedule). */
internal fun Modifier.cancelDragOnEscape(controller: ScheduleDragController<*>): Modifier =
    onPreviewKeyEvent { it.type == KeyEventType.KeyDown && it.key == Key.Escape && controller.cancelPointerDrag() }

/** Scrolls the time grid while a drag holds the pointer within 32.dp of the viewport's top or bottom edge. */
@Composable
internal fun ScheduleAutoScroll(
    controller: ScheduleDragController<*>,
    scrollState: ScrollState,
) {
    LaunchedEffect(controller, scrollState) {
        snapshotFlow {
            val speed = controller.edgeSpeed
            val canScroll = if (speed < 0f) scrollState.value > 0 else scrollState.value < scrollState.maxValue
            speed != 0f && canScroll
        }.collectLatest { scrolling ->
            if (scrolling) {
                var last = -1L
                while (true) {
                    withFrameNanos { now ->
                        if (last >= 0L) {
                            scrollState.dispatchRawDelta(controller.edgeSpeed * (now - last) / NANOS_PER_SECOND)
                            controller.onAutoScrolled?.invoke()
                        }
                        last = now
                    }
                }
            }
        }
    }
}

private const val NANOS_PER_SECOND = 1_000_000_000f

/** Draws the ghost of the edit in flight over the page's day columns, plus the snap guide line. */
@Composable
internal fun <T> ScheduleGhostLayer(
    controller: ScheduleDragController<T>,
    dates: List<LocalDate>,
    hourHeight: Dp,
    window: HourWindow,
    dimensions: ScheduleDimensions,
    ghostContent: @Composable BoxScope.(ScheduleDragPreview<T>) -> Unit,
) {
    @Suppress("UNCHECKED_CAST")
    val override = LocalScheduleDragOverride.current as ScheduleDragPreview<T>?
    val preview = override ?: controller.active ?: return
    val column = dates.indexOf(preview.date)
    if (column < 0) return
    val range = previewMinutes(preview)
    val chipBelow = hourHeight * ((range.start - window.startMinute) / 60f) < GhostChipFlipDistance
    val guideColor = if (controller.outOfBounds) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Layout(
        content = {
            Box(propagateMinConstraints = true) {
                CompositionLocalProvider(LocalGhostChipBelow provides chipBelow) {
                    ghostContent(preview)
                }
            }
            Box(Modifier.background(guideColor))
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val hourPx = hourHeight.toPx()
        val columnWidth = constraints.maxWidth.toFloat() / dates.size
        val width = (columnWidth - dimensions.tileHorizontalGap.toPx()).roundToInt().coerceAtLeast(0)
        val top = ((range.start - window.startMinute) / 60f * hourPx).roundToInt()
        val height = (range.duration / 60f * hourPx - dimensions.tileVerticalGap.toPx()).roundToInt().coerceAtLeast(0)
        val ghost = measurables[0].measure(Constraints.fixed(width, height))
        val guide =
            measurables[1].measure(
                Constraints.fixed(columnWidth.roundToInt().coerceAtLeast(0), 1.dp.roundToPx().coerceAtLeast(1)),
            )
        layout(constraints.maxWidth, constraints.maxHeight) {
            val x = (columnWidth * column).roundToInt()
            ghost.placeRelative(x, top)
            guide.placeRelative(x, top)
        }
    }
}

/**
 * Wraps a timed tile's content with its editing affordances: dimming while it
 * is dragged, resize handle pills, Alt+arrow shortcuts and accessibility
 * custom actions. Tiles that cannot be edited are emitted unchanged.
 */
@Composable
internal fun <T> EditableTile(
    positioned: PositionedEvent<T>,
    editing: ColumnEditing<T>,
    window: HourWindow,
    content: @Composable BoxScope.() -> Unit,
) {
    val event = positioned.event
    val real = remember(event) { editableMinutes(event) }
    val canMove = real != null && editing.config.onEventMove != null
    val canResize = real != null && editing.config.onEventResize != null
    if (real == null || (!canMove && !canResize)) {
        Box(propagateMinConstraints = true, content = content)
        return
    }
    val controller = editing.controller
    val override = LocalScheduleDragOverride.current
    val layoutDirection = LocalLayoutDirection.current
    val handleColor = eventPalette(event, editing.colors).content.copy(alpha = 0.8f)
    val ringColor = MaterialTheme.colorScheme.onSurface
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    // A tile with a click handler is already focusable; otherwise the wrapper takes focus for the shortcuts.
    val selfFocusable = !editing.config.eventsClickable
    val focusModifier =
        if (selfFocusable) {
            Modifier
                .eventFocusRing(isFocused, RoundedCornerShape(editing.dimensions.tileCornerRadius), ringColor)
                .focusable(interactionSource = interactionSource)
        } else {
            Modifier
        }
    val actions = tileActions(positioned, real, editing, window, canMove, canResize)
    Box(
        modifier =
            Modifier
                .graphicsLayer { alpha = controller.sourceAlpha(event, override) }
                .onPreviewKeyEvent { onTileKey(it, positioned, real, editing, window, layoutDirection) }
                .then(focusModifier)
                .semantics(mergeDescendants = selfFocusable) { customActions = actions }
                .drawWithContent {
                    drawContent()
                    if (canResize) {
                        val sides = resizeHandleSides(size.height, positioned, real)
                        drawResizeHandles(sides, handleColor, editing.dimensions)
                    }
                },
        propagateMinConstraints = true,
        content = content,
    )
}

@Suppress("LongParameterList")
private fun <T> tileActions(
    positioned: PositionedEvent<T>,
    real: MinuteRange,
    editing: ColumnEditing<T>,
    window: HourWindow,
    canMove: Boolean,
    canResize: Boolean,
): List<CustomAccessibilityAction> {
    val snap = editing.config.snapMinutes
    val strings = editing.strings
    return buildList {
        if (canMove) {
            add(CustomAccessibilityAction(strings.moveEarlier) { moveBy(positioned, real, editing, window, -snap, 0) })
            add(CustomAccessibilityAction(strings.moveLater) { moveBy(positioned, real, editing, window, snap, 0) })
            if (editing.pageDates.size > 1) {
                add(
                    CustomAccessibilityAction(strings.movePreviousDay) {
                        moveBy(positioned, real, editing, window, 0, -1)
                    },
                )
                add(CustomAccessibilityAction(strings.moveNextDay) { moveBy(positioned, real, editing, window, 0, 1) })
            }
        }
        if (canResize) {
            add(CustomAccessibilityAction(strings.extendEnd) { resizeBy(positioned, real, editing, window, 0, snap) })
            add(CustomAccessibilityAction(strings.shortenEnd) { resizeBy(positioned, real, editing, window, 0, -snap) })
        }
    }
}

/**
 * Alt+Up/Down moves by one snap step, Alt+Left/Right to the neighbouring day of
 * the page, Alt+Shift+Up/Down moves the end edge and Alt+Ctrl+Up/Down the start edge.
 */
@Suppress("LongParameterList")
private fun <T> onTileKey(
    event: KeyEvent,
    positioned: PositionedEvent<T>,
    real: MinuteRange,
    editing: ColumnEditing<T>,
    window: HourWindow,
    layoutDirection: LayoutDirection,
): Boolean {
    if (event.type != KeyEventType.KeyDown || !event.isAltPressed) return false
    val snap = editing.config.snapMinutes
    val vertical =
        when (event.key) {
            Key.DirectionUp -> -snap
            Key.DirectionDown -> snap
            else -> 0
        }
    return when {
        vertical != 0 && event.isShiftPressed -> resizeBy(positioned, real, editing, window, 0, vertical)
        vertical != 0 && event.isCtrlPressed -> resizeBy(positioned, real, editing, window, vertical, 0)
        vertical != 0 -> moveBy(positioned, real, editing, window, vertical, 0)
        else -> {
            val dayOffset = horizontalArrowDayOffset(event.key, layoutDirection) ?: return false
            moveBy(positioned, real, editing, window, 0, dayOffset)
        }
    }
}

/** One committed keyboard or accessibility move; returns whether moving is enabled. */
@Suppress("LongParameterList")
private fun <T> moveBy(
    positioned: PositionedEvent<T>,
    real: MinuteRange,
    editing: ColumnEditing<T>,
    window: HourWindow,
    deltaMinutes: Int,
    dayOffset: Int,
): Boolean {
    val callback = editing.config.onEventMove ?: return false
    val dates = editing.pageDates
    val index = dates.indexOf(positioned.date).coerceAtLeast(0)
    val date = dates[(index + dayOffset).coerceIn(0, dates.lastIndex)]
    val range =
        moveRange(real.start, real.end, deltaMinutes, editing.config.snapMinutes, window.startMinute, window.endMinute)
    if (date != positioned.date || range != real) {
        callback(positioned.event, date, minuteToLocalTime(range.start), minuteToLocalTime(range.end))
    }
    return true
}

/** One committed keyboard or accessibility resize of the start or the end edge. */
@Suppress("LongParameterList")
private fun <T> resizeBy(
    positioned: PositionedEvent<T>,
    real: MinuteRange,
    editing: ColumnEditing<T>,
    window: HourWindow,
    startDelta: Int,
    endDelta: Int,
): Boolean {
    val callback = editing.config.onEventResize ?: return false
    val snap = editing.config.snapMinutes
    val minDuration = editing.config.minResizeDuration
    // An edge cut by the visible hours stays where it is.
    val clipped =
        if (startDelta != 0) real.start < window.startMinute else real.end > window.endMinute
    if (clipped) return true
    val range =
        if (startDelta != 0) {
            resizeStart(real.start, real.end, real.start + startDelta, snap, minDuration, window.startMinute)
        } else {
            resizeEnd(real.start, real.end, real.end + endDelta, snap, minDuration, window.endMinute)
        }
    if (range != real) {
        callback(positioned.event, minuteToLocalTime(range.start), minuteToLocalTime(range.end))
    }
    return true
}

/**
 * Keyboard and accessibility creation for a day column: focused, the column
 * shows a slot caret (the create ghost) that Up/Down move by one snap step and
 * Shift+Up/Down shrink or grow; Enter or Space commits it.
 */
@Composable
internal fun <T> columnCaretModifier(
    editing: ColumnEditing<T>,
    date: LocalDate,
    hourHeight: Dp,
    window: HourWindow,
): Modifier {
    if (!editing.config.createEnabled) return Modifier
    val hourPx = with(LocalDensity.current) { hourHeight.toPx() }
    val is24Hour = systemUses24HourClock()
    val controller = editing.controller
    val scroll = editing.scrollState
    val snap = editing.config.snapMinutes
    // The first whole slot in view; changes once per snap step while scrolling.
    val topSlot by remember(scroll, hourPx, window.startHour, window.endHour, snap) {
        derivedStateOf {
            snapCeil(minuteAt(scroll.value.toFloat(), hourPx, window), snap)
                .coerceIn(window.startMinute, window.endMinute - snap)
        }
    }
    val label = editing.strings.createAt
    return Modifier
        .onFocusChanged { focus ->
            if (focus.isFocused) {
                controller.showCaret(date, MinuteRange(topSlot, topSlot + snap))
            } else {
                controller.hideCaret(date)
            }
        }.onKeyEvent { onColumnKey(it, editing, date, hourPx, window, topSlot) }
        .focusable()
        .semantics {
            val caret = controller.caretFor(date) ?: MinuteRange(topSlot, topSlot + snap)
            customActions =
                listOf(
                    CustomAccessibilityAction(fillTemplate(label, clockLabel(caret.start, is24Hour))) {
                        commitCaret(editing, date, caret, window)
                        true
                    },
                )
        }
}

@Suppress("LongParameterList", "ReturnCount")
private fun <T> onColumnKey(
    event: KeyEvent,
    editing: ColumnEditing<T>,
    date: LocalDate,
    hourPx: Float,
    window: HourWindow,
    topSlot: Int,
): Boolean {
    if (event.type != KeyEventType.KeyDown || event.isAltPressed || event.isCtrlPressed) return false
    val snap = editing.config.snapMinutes
    val caret = editing.controller.caretFor(date) ?: MinuteRange(topSlot, topSlot + snap)
    when (event.key) {
        Key.DirectionUp, Key.DirectionDown -> {
            val step = if (event.key == Key.DirectionUp) -snap else snap
            val next =
                if (event.isShiftPressed) {
                    MinuteRange(caret.start, (caret.end + step).coerceIn(caret.start + snap, window.endMinute))
                } else {
                    val start = (caret.start + step).coerceIn(window.startMinute, window.endMinute - caret.duration)
                    MinuteRange(start, start + caret.duration)
                }
            editing.controller.showCaret(date, next)
            bringIntoView(editing.scrollState, next, hourPx, window)
        }
        Key.Enter, Key.NumPadEnter, Key.Spacebar -> commitCaret(editing, date, caret, window)
        else -> return false
    }
    return true
}

private fun bringIntoView(
    scroll: ScrollState,
    range: MinuteRange,
    hourPx: Float,
    window: HourWindow,
) {
    val top = (range.start - window.startMinute) / 60f * hourPx
    val bottom = (range.end - window.startMinute) / 60f * hourPx
    val viewportBottom = scroll.value + scroll.viewportSize
    if (top < scroll.value) {
        scroll.dispatchRawDelta(top - scroll.value)
    } else if (bottom > viewportBottom) {
        scroll.dispatchRawDelta(bottom - viewportBottom)
    }
}

/**
 * Commits the keyboard caret: a caret grown past one snap step creates that range;
 * otherwise it is a long press on the slot, or a default-length event when only
 * creation is enabled.
 */
private fun <T> commitCaret(
    editing: ColumnEditing<T>,
    date: LocalDate,
    caret: MinuteRange,
    window: HourWindow,
) {
    val config = editing.config
    val create = config.onSlotCreate
    val longPress = config.onEmptySlotLongPress
    val start = minuteToLocalTime(caret.start)
    when {
        create != null && caret.duration > config.snapMinutes -> create(date, start, minuteToLocalTime(caret.end))
        longPress != null -> longPress(date, start)
        create != null -> {
            val end = minOf(caret.start + DEFAULT_CREATE_DURATION_MINUTES, window.endMinute)
            create(date, start, minuteToLocalTime(end))
        }
    }
}
