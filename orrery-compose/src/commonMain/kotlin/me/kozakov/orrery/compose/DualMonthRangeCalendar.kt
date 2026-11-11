package me.kozakov.orrery.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.CalendarDay
import me.kozakov.orrery.core.CalendarPages
import me.kozakov.orrery.core.DayPosition
import me.kozakov.orrery.core.daysOfWeek
import me.kozakov.orrery.core.monthGrid

/** Widest and tallest the dual-month dialog card grows. */
internal val DualMonthDialogMaxWidth = 840.dp
internal val DualMonthDialogMaxHeight = 720.dp

/** Padding on each side of a month pane, so the gap between the two grids is twice this. */
private val DualMonthPanePadding = 12.dp

/** A drag held this close to the pager's left or right edge pages the calendar. */
private val DualMonthAutoPageEdge = 24.dp

/** Pause before each auto-page while a drag rests at the edge. */
private const val DUAL_MONTH_AUTO_PAGE_MILLIS = 400L

/** Every pane reserves the height of the longest month, so paging never resizes the dialog. */
private const val MAX_WEEKS_PER_MONTH = 6

/**
 * The month keyboard paging must scroll to (as the left pane) so that
 * [target] is visible in a dual-month pager whose left pane shows [left],
 * or null when [target] is already one of the two visible months. An
 * earlier target becomes the left pane; a later one becomes the right
 * pane, so moving forward slides by one month. [start] is the first month
 * of the calendar.
 */
internal fun dualMonthPageTarget(
    left: YearMonth,
    target: YearMonth,
    start: YearMonth,
): YearMonth? =
    when {
        target == left || target == CalendarPages.monthAt(left, 1) -> null
        target < left -> target
        else -> maxOf(CalendarPages.monthAt(target, -1), start)
    }

/**
 * Two consecutive months side by side on the picker's own calendar state:
 * a navigation header with both month titles, then a pager whose pages are
 * half the width, so the visible pair is always
 * `displayedMonth` and the month after it. Tapping, long-press dragging and
 * keyboard moves select across both months; hovering with a mouse or
 * stylus previews the range while only its start is set.
 */
@Composable
internal fun DualMonthRangeCalendar(
    state: OrreryDateRangePickerState,
    today: LocalDate,
    colors: CalendarDayColors,
    shapes: CalendarDayShapes,
    strings: DatePickerStrings,
    modifier: Modifier = Modifier,
) {
    val calendar = state.calendar
    val selection = state.selection
    val scope = rememberCoroutineScope()
    val keyboard =
        remember(calendar, scope) {
            CalendarKeyboardNavigation(scope, dualMonth = true) { date ->
                val target = YearMonth(date.year, date.month)
                dualMonthPageTarget(calendar.firstVisibleMonth, target, calendar.effectiveStartMonth)
                    ?.let { calendar.animateScrollToMonth(it) }
            }
        }
    val hitTest = remember(calendar) { DualMonthHitTest(calendar) }
    hitTest.isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val autoPager = remember(calendar, scope) { DualMonthAutoPager(scope, calendar) }
    val haptics = LocalHapticFeedback.current
    var hoveredDate by remember { mutableStateOf<LocalDate?>(null) }
    val rangeIsOpen = selection.selection.rangeStart != null && selection.selection.rangeEnd == null
    val decorators =
        if (rangeIsOpen && hoveredDate != null) {
            listOf(DayDecorators.rangePreview(selection, hoveredDate))
        } else {
            emptyList()
        }
    val initialFocus = rememberInitialDayFocus(state.selectedStartDate ?: today)
    val paneFraction = if (calendar.monthCount > 1) 0.5f else 1f
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    Column(modifier) {
        DualMonthNavHeader(calendar, today, strings)
        LazyRow(
            state = calendar.listState,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .onPlaced { hitTest.container = it }
                    .pointerInput(hitTest) { detectDualMonthHover(hitTest) { hoveredDate = it } }
                    .pointerInput(hitTest, selection, autoPager, haptics) {
                        detectDualMonthDrag(hitTest, selection, autoPager, haptics)
                    }.drawBehind {
                        if (paneFraction < 1f) {
                            val inset = 16.dp.toPx()
                            drawLine(
                                color = dividerColor,
                                start = Offset(size.width / 2f, inset),
                                end = Offset(size.width / 2f, size.height - inset),
                                strokeWidth = 1.dp.toPx(),
                            )
                        }
                    },
            flingBehavior = rememberSnapFlingBehavior(calendar.listState, SnapPosition.Start),
        ) {
            items(count = calendar.monthCount, key = { it }) { index ->
                val month = rememberMonth(calendar, index)
                val title = PlatformDateFormat.yearMonthTitle(month.yearMonth)
                Column(
                    Modifier
                        .fillParentMaxWidth(paneFraction)
                        .padding(horizontal = DualMonthPanePadding)
                        .semantics {
                            isTraversalGroup = true
                            contentDescription = title
                        },
                ) {
                    CalendarDefaults.WeekHeader(daysOfWeek(calendar.firstDayOfWeek, calendar.visibleDaysOfWeek))
                    Box(Modifier.heightIn(min = CalendarDensity.Comfortable.minCellHeight * MAX_WEEKS_PER_MONTH)) {
                        MonthContent(
                            month = month,
                            monthHeader = null,
                            dayContent = { day ->
                                DefaultDay(
                                    day = day,
                                    modifier = initialFocus(day),
                                    selectionState = selection,
                                    today = today,
                                    colors = colors,
                                    shapes = shapes,
                                    // A date belongs to exactly one pane; its neighbors' days would repeat.
                                    showOutDates = false,
                                    decorators = decorators,
                                )
                            },
                            modifier = Modifier.onPlaced { hitTest.grids[index] = it },
                            // The pager owns the drag, so it can cross from one month to the other.
                            dragSelection = null,
                            keyboardNavigation = keyboard,
                            selectionForKeys = selection,
                        )
                    }
                }
                DisposableEffect(index) { onDispose { hitTest.grids.remove(index) } }
            }
        }
    }
}

/**
 * `[<]  July 2026  |  August 2026  [>] [Today]`: each title is centered over
 * its month pane and the chevrons shift both panes by one month. Today
 * shows only while today's month is in neither pane.
 */
@Composable
private fun DualMonthNavHeader(
    calendar: CalendarState,
    today: LocalDate,
    strings: DatePickerStrings,
) {
    val scope = rememberCoroutineScope()
    val left = calendar.firstVisibleMonth
    val start = calendar.effectiveStartMonth
    val end = calendar.effectiveEndMonth
    val right = CalendarPages.monthAt(left, 1).takeIf { left < end }
    val todayMonth = YearMonth(today.year, today.month)
    val todayInRange = todayMonth >= start && todayMonth <= end
    val leftTitle = PlatformDateFormat.yearMonthTitle(left)
    val rightTitle = right?.let { PlatformDateFormat.yearMonthTitle(it) }
    val titles = if (rightTitle != null) "$leftTitle – $rightTitle" else leftTitle
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                // One announcement per page turn instead of one per title.
                .clearAndSetSemantics {
                    contentDescription = titles
                    liveRegion = LiveRegionMode.Polite
                },
        ) {
            DualMonthTitle(leftTitle, Modifier.weight(1f))
            if (rightTitle != null) DualMonthTitle(rightTitle, Modifier.weight(1f))
        }
        IconButton(
            onClick = { scope.launch { calendar.animateScrollToPreviousMonth() } },
            enabled = left > start,
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            NavChevron(forward = false, contentDescription = strings.previousMonthDescription)
        }
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { scope.launch { calendar.animateScrollToNextMonth() } },
                // The last reachable pair ends on the calendar's last month.
                enabled = right != null && right < end,
            ) {
                NavChevron(forward = true, contentDescription = strings.nextMonthDescription)
            }
            AnimatedVisibility(visible = todayInRange && todayMonth != left && todayMonth != right) {
                TextButton(
                    onClick = { scope.launch { calendar.animateScrollToMonth(todayMonth) } },
                    contentPadding = TodayButtonPadding,
                ) {
                    Text(orreryString(OrreryString.Today), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun DualMonthTitle(
    title: String,
    modifier: Modifier,
) {
    Text(
        text = title,
        // Clear of the chevron that overlays the outer end of each title.
        modifier = modifier.padding(horizontal = 48.dp),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Reports the month date under a mouse or stylus that hovers the pager; touch has no hover. */
private suspend fun PointerInputScope.detectDualMonthHover(
    hitTest: DualMonthHitTest,
    onHover: (LocalDate?) -> Unit,
) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull()
            if (change != null && (change.type == PointerType.Mouse || change.type == PointerType.Stylus)) {
                when (event.type) {
                    PointerEventType.Move, PointerEventType.Enter -> {
                        onHover(hitTest.dayAt(change.position, insideOnly = true)?.date)
                    }

                    PointerEventType.Exit -> {
                        onHover(null)
                    }
                }
            }
        }
    }
}

/**
 * Long-press drag selection over the whole pager, so a range can start in
 * one month and end in the other. Resting at the left or right edge pages
 * the calendar through [autoPager].
 */
private suspend fun PointerInputScope.detectDualMonthDrag(
    hitTest: DualMonthHitTest,
    selection: CalendarSelectionState,
    autoPager: DualMonthAutoPager,
    haptics: HapticFeedback,
) {
    val edge = DualMonthAutoPageEdge.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val longPress = awaitLongPressOrCancellation(down.id)
        val first = longPress?.let { hitTest.dayAt(it.position, insideOnly = false) }
        if (longPress != null && first != null) {
            val drag = DualMonthDrag(hitTest, selection, haptics, first.date, longPress.position)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            selection.dragStart(first.date)
            autoPager.onPaged = drag::update
            try {
                var change = awaitDragChange(down.id)
                while (change != null) {
                    drag.moveTo(change.position)
                    autoPager.direction = edgeDirection(change.position.x, size.width, edge, hitTest.isRtl)
                    change = awaitDragChange(down.id)
                }
            } finally {
                autoPager.direction = 0
                autoPager.onPaged = null
                selection.dragEnd()
            }
        }
    }
}

/**
 * The next position of the dragged pointer, or null once it lifts. Events
 * are consumed in the Initial pass, before the pager's own scrolling and
 * the day cells see them, so the drag never swipes the pager or clicks.
 */
private suspend fun AwaitPointerEventScope.awaitDragChange(id: PointerId): PointerInputChange? {
    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == id }
    change?.consume()
    return change?.takeIf { it.pressed }
}

/** +1 at the edge toward later months, -1 at the edge toward earlier ones, else 0. */
private fun edgeDirection(
    x: Float,
    width: Int,
    edge: Float,
    isRtl: Boolean,
): Int {
    val towardRight =
        when {
            x < edge -> -1
            x > width - edge -> 1
            else -> 0
        }
    return if (isRtl) -towardRight else towardRight
}

/** One long-press drag: follows the pointer and extends the selection to the day under it. */
private class DualMonthDrag(
    private val hitTest: DualMonthHitTest,
    private val selection: CalendarSelectionState,
    private val haptics: HapticFeedback,
    private var lastDate: LocalDate,
    private var pointer: Offset,
) {
    fun moveTo(position: Offset) {
        pointer = position
        update()
    }

    /** Re-reads the day under the pointer, which changes without a move when the pager pages. */
    fun update() {
        val date = hitTest.dayAt(pointer, insideOnly = false)?.date ?: return
        if (date != lastDate) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        lastDate = date
        selection.dragUpdate(date)
    }
}

/**
 * Maps a pointer position in the pager to a day of whichever month pane it
 * is over. Panes report their grid coordinates as they are placed, so the
 * lookup follows the pager while it scrolls and needs no RTL arithmetic of
 * its own.
 */
private class DualMonthHitTest(
    private val calendar: CalendarState,
) {
    var container: LayoutCoordinates? = null
    val grids: MutableMap<Int, LayoutCoordinates> = mutableMapOf()
    var isRtl: Boolean = false

    /**
     * The day under [position] (in the pager's coordinates). With
     * [insideOnly] only a month's own date directly under the pointer
     * counts; otherwise the nearest pane answers and positions past its
     * grid resolve to the nearest row and column, as in a single month.
     */
    fun dayAt(
        position: Offset,
        insideOnly: Boolean,
    ): CalendarDay? {
        val pager = container?.takeIf { it.isAttached } ?: return null
        return grids.entries
            .filter { it.value.isAttached }
            .mapNotNull { (index, grid) ->
                hit(index, grid, position - pager.localPositionOf(grid, Offset.Zero), insideOnly)
            }.minByOrNull { it.second }
            ?.first
    }

    /** The day of pane [index] at [local] (in its grid's coordinates) with the pointer's distance from that grid. */
    private fun hit(
        index: Int,
        grid: LayoutCoordinates,
        local: Offset,
        insideOnly: Boolean,
    ): Pair<CalendarDay, Float>? {
        val distance =
            when {
                local.x < 0f -> -local.x
                local.x >= grid.size.width -> local.x - grid.size.width
                else -> 0f
            }
        val inside = distance == 0f && local.y >= 0f && local.y < grid.size.height
        if (insideOnly && !inside) return null
        val month =
            monthGrid(
                yearMonth = CalendarPages.monthAt(calendar.effectiveStartMonth, index),
                firstDayOfWeek = calendar.firstDayOfWeek,
                outDateStyle = calendar.outDateStyle,
                visibleDaysOfWeek = calendar.visibleDaysOfWeek,
            )
        return monthDayAt(month, 0f, grid.size, local, isRtl, rowTops = null)
            ?.takeUnless { insideOnly && it.position != DayPosition.MonthDate }
            ?.let { it to distance }
    }
}

/**
 * Pages the calendar by one month each time a drag has rested at a pager
 * edge for [DUAL_MONTH_AUTO_PAGE_MILLIS]. [direction] is +1 toward later
 * months, -1 toward earlier ones and 0 away from the edges; a page turn in
 * flight always finishes, so the pager never rests between two months.
 */
private class DualMonthAutoPager(
    private val scope: CoroutineScope,
    private val calendar: CalendarState,
) {
    /** Called after each page turn so the drag can pick up the day now under the pointer. */
    var onPaged: (() -> Unit)? = null
    private var job: Job? = null

    var direction: Int = 0
        set(value) {
            field = value
            if (value != 0 && job?.isActive != true) job = scope.launch { pageWhileAtEdge() }
        }

    private suspend fun pageWhileAtEdge() {
        delay(DUAL_MONTH_AUTO_PAGE_MILLIS)
        while (direction != 0) {
            if (direction > 0) calendar.animateScrollToNextMonth() else calendar.animateScrollToPreviousMonth()
            onPaged?.invoke()
            delay(DUAL_MONTH_AUTO_PAGE_MILLIS)
        }
    }
}
