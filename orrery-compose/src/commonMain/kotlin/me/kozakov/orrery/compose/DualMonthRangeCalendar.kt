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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
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
import me.kozakov.orrery.core.CalendarMonth
import me.kozakov.orrery.core.CalendarPages
import me.kozakov.orrery.core.DayPosition
import me.kozakov.orrery.core.daysOfWeek

/** Widest and tallest the dual-month dialog card grows. */
internal val DualMonthDialogMaxWidth = 840.dp
internal val DualMonthDialogMaxHeight = 720.dp

/** Padding on each side of a month pane, so the gap between the two grids is twice this. */
private val DualMonthPanePadding = 12.dp

/** Every pane reserves the height of the longest month, so paging never resizes the dialog. */
private const val MAX_WEEKS_PER_MONTH = 6

/** A range drag this close to the pager's left or right edge pages one month. */
internal val DualMonthAutoPageEdge = 24.dp

/**
 * How long a range drag must stay in the edge zone before the first
 * auto-page, so a pass through the outermost column doesn't flip the month.
 */
internal const val DUAL_MONTH_AUTO_PAGE_DWELL_MS = 250L

/** Time between two auto-pages while a range drag stays in the edge zone. */
internal const val DUAL_MONTH_AUTO_PAGE_INTERVAL_MS = 400L

/** One pager item as laid out: [offset] and [size] along the row, from its start edge. */
internal class DualMonthPaneSpan(
    val index: Int,
    val offset: Int,
    val size: Int,
)

/**
 * Index of the pane under [x] (pixels from the left of a pager [width]
 * wide), or null over no pane. Pager offsets run from the start edge, so
 * in RTL [x] is measured from the right first.
 */
internal fun dualMonthPaneAt(
    x: Float,
    width: Int,
    isRtl: Boolean,
    panes: List<DualMonthPaneSpan>,
): Int? {
    val fromStart = if (isRtl) width - x else x
    return panes.firstOrNull { fromStart >= it.offset && fromStart < it.offset + it.size }?.index
}

/**
 * Months a range drag at [x] should page: +1 (later) or -1 (earlier)
 * within [edge] pixels of either side of a pager [width] wide, else 0. The
 * later month is on the right in LTR and on the left in RTL.
 */
internal fun dualMonthEdgePageDirection(
    x: Float,
    width: Int,
    edge: Float,
    isRtl: Boolean,
): Int {
    val towardRight =
        when {
            x < edge -> false
            x > width - edge -> true
            else -> return 0
        }
    return if (towardRight != isRtl) 1 else -1
}

/** A pane's month grid and where it is laid out, for the container's hit test. */
private class DualMonthPaneGrid(
    val month: CalendarMonth,
    val coordinates: LayoutCoordinates,
)

/**
 * Maps a position in the pager container to the in-month day under it: the
 * pane from the pager's visible items, then the day with [monthDayAt].
 * Plain (non-snapshot) fields, refreshed on placement.
 */
private class DualMonthHitTest(
    private val calendar: CalendarState,
) {
    var container: LayoutCoordinates? = null
    val panes = mutableMapOf<Int, DualMonthPaneGrid>()

    fun dayAt(
        position: Offset,
        isRtl: Boolean,
    ): CalendarDay? {
        val container = container?.takeIf { it.isAttached } ?: return null
        val width = container.size.width
        if (width <= 0) return null
        // A drag past the sides keeps hitting the outermost column.
        val x = position.x.coerceIn(0f, width - 1f)
        val spans =
            calendar.listState.layoutInfo.visibleItemsInfo
                .map { DualMonthPaneSpan(it.index, it.offset, it.size) }
        val index = dualMonthPaneAt(x, width, isRtl, spans) ?: return null
        val pane = panes[index]?.takeIf { it.coordinates.isAttached } ?: return null
        val local = pane.coordinates.localPositionOf(container, Offset(x, position.y))
        return monthDayAt(pane.month, 0f, pane.coordinates.size, local, isRtl)
            // Blank cells stand for the neighbor's dates, which that pane already shows.
            ?.takeIf { it.position == DayPosition.MonthDate }
    }
}

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
 * half its width, so the visible pair is always `displayedMonth` and the
 * month after it, and paging moves both by one month.
 */
@Suppress("LongParameterList")
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
    // Synchronously, before the pager measures: the single-month column uses
    // two list items per month, the pager one.
    remember(calendar) { calendar.useItemsPerMonth(1) }
    val scope = rememberCoroutineScope()
    val keyboard =
        remember(calendar, scope) {
            fun pageFor(date: LocalDate): YearMonth? =
                dualMonthPageTarget(
                    calendar.firstVisibleMonth,
                    YearMonth(date.year, date.month),
                    calendar.effectiveStartMonth,
                )
            CalendarKeyboardNavigation(
                scope = scope,
                dualMonth = true,
                isOnScreen = { pageFor(it) == null },
            ) { date -> pageFor(date)?.let { calendar.animateScrollToMonth(it) } }
        }
    val initialFocus = rememberInitialDayFocus(state.selectedStartDate ?: today)
    val paneFraction = if (calendar.monthCount > 1) 0.5f else 1f
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val haptics = LocalHapticFeedback.current
    val hitTest = remember(calendar) { DualMonthHitTest(calendar) }
    val previewDate = remember { mutableStateOf<LocalDate?>(null) }
    val previewColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    // One decorator for every day; it reads previewDate while drawing, so hovering only redraws.
    val previewDecorators =
        remember(state.selection, previewColor) {
            listOf(DayDecorators.rangePreviewDecorator(state.selection, { previewDate.value }, previewColor))
        }
    Column(modifier) {
        DualMonthNavHeader(calendar, today, strings)
        LazyRow(
            state = calendar.listState,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .onPlaced { hitTest.container = it }
                    // Both gestures sit above the panes so they can cross from one month to the other.
                    .dualMonthRangeDrag(hitTest, state.selection, calendar, scope, haptics, isRtl)
                    .dualMonthHoverPreview(hitTest, state.selection, previewDate, isRtl)
                    .drawBehind {
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
                DisposableEffect(hitTest, index) { onDispose { hitTest.panes.remove(index) } }
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
                                    selectionState = state.selection,
                                    today = today,
                                    colors = colors,
                                    shapes = shapes,
                                    // A date belongs to exactly one pane; its neighbors' days would repeat.
                                    showOutDates = false,
                                    decorators = previewDecorators,
                                )
                            },
                            modifier = Modifier.onPlaced { hitTest.panes[index] = DualMonthPaneGrid(month, it) },
                            // The container owns the drag, so it can cross panes.
                            dragSelection = null,
                            keyboardNavigation = keyboard,
                            selectionForKeys = state.selection,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Long-press drag range selection across both panes. Runs on the Initial
 * pass once the long press fires, so the pager under it never starts a
 * swipe and the day under the finger never sees a click. Once the pointer
 * has stayed within [DualMonthAutoPageEdge] of either side for
 * [DUAL_MONTH_AUTO_PAGE_DWELL_MS], the pager moves one month, then one more
 * every [DUAL_MONTH_AUTO_PAGE_INTERVAL_MS] while it stays there (moving or
 * not), and the range follows to the day now under the pointer.
 */
@Suppress("LongParameterList")
private fun Modifier.dualMonthRangeDrag(
    hitTest: DualMonthHitTest,
    selection: CalendarSelectionState,
    calendar: CalendarState,
    scope: CoroutineScope,
    haptics: HapticFeedback,
    isRtl: Boolean,
): Modifier =
    pointerInput(hitTest, selection, calendar, isRtl) {
        val edge = DualMonthAutoPageEdge.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val longPress = awaitLongPressOrCancellation(down.id)
            val first = longPress?.let { hitTest.dayAt(it.position, isRtl) }
            if (longPress != null && first != null && selection.supportsDrag) {
                val drag = DualMonthRangeDrag(hitTest, selection, calendar, scope, haptics, isRtl, edge)
                drag.start(first.date, longPress.position)
                try {
                    followRangeDrag(longPress.id, drag)
                } finally {
                    drag.stop()
                    selection.dragEnd()
                }
            }
        }
    }

/** Feeds [pointer]'s moves to [drag] until it lifts, consuming them before the pager sees them. */
private suspend fun AwaitPointerEventScope.followRangeDrag(
    pointer: PointerId,
    drag: DualMonthRangeDrag,
) {
    while (true) {
        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == pointer }
        if (change == null || !change.pressed) {
            change?.consume()
            return
        }
        if (change.positionChanged()) drag.moveTo(change.position, size.width)
        change.consume()
    }
}

/** One long-press range drag: follows the pointer across panes and auto-pages at the edges. */
@Suppress("LongParameterList")
private class DualMonthRangeDrag(
    private val hitTest: DualMonthHitTest,
    private val selection: CalendarSelectionState,
    private val calendar: CalendarState,
    private val scope: CoroutineScope,
    private val haptics: HapticFeedback,
    private val isRtl: Boolean,
    private val edgePx: Float,
) {
    private var lastDate: LocalDate? = null
    private var position = Offset.Zero
    private var ended = false

    /** Edge the pointer is in (+1 later, -1 earlier, 0 none) and the job paging toward it. */
    private var pageDirection = 0
    private var pageJob: Job? = null

    fun start(
        date: LocalDate,
        at: Offset,
    ) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        selection.dragStart(date)
        lastDate = date
        position = at
    }

    fun moveTo(
        at: Offset,
        width: Int,
    ) {
        position = at
        follow()
        val direction = dualMonthEdgePageDirection(at.x, width, edgePx, isRtl)
        if (direction == pageDirection) return
        pageJob?.cancel()
        pageDirection = direction
        pageJob = if (direction == 0) null else scope.launch { autoPage(direction) }
    }

    /** Ends the drag: no more pages, and a page still animating no longer moves the range. */
    fun stop() {
        ended = true
        pageJob?.cancel()
        pageJob = null
    }

    /**
     * Runs while the pointer stays in one edge zone, held still or not:
     * waits out the dwell, then pages toward [direction] every interval.
     */
    private suspend fun autoPage(direction: Int) {
        delay(DUAL_MONTH_AUTO_PAGE_DWELL_MS)
        while (true) {
            // Its own job, so leaving the zone mid-animation still lands on a whole month.
            scope
                .launch {
                    if (direction > 0) calendar.animateScrollToNextMonth() else calendar.animateScrollToPreviousMonth()
                    // The pointer now rests over another month's day.
                    follow()
                }.join()
            delay(DUAL_MONTH_AUTO_PAGE_INTERVAL_MS)
        }
    }

    private fun follow() {
        if (ended) return
        val day = hitTest.dayAt(position, isRtl) ?: return
        if (day.date != lastDate) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        lastDate = day.date
        selection.dragUpdate(day.date)
    }
}

/**
 * Mouse and stylus hover over either pane sets [previewDate] while the
 * range has a start but no end, so DayDecorators.rangePreview draws the
 * range the next click would make; exit or a closed range clears it.
 * Touch has no hover and never sets it.
 */
private fun Modifier.dualMonthHoverPreview(
    hitTest: DualMonthHitTest,
    selection: CalendarSelectionState,
    previewDate: MutableState<LocalDate?>,
    isRtl: Boolean,
): Modifier =
    pointerInput(hitTest, selection, isRtl) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull()
                if (change != null && (change.type == PointerType.Mouse || change.type == PointerType.Stylus)) {
                    val open = selection.selection.rangeStart != null && selection.selection.rangeEnd == null
                    previewDate.value =
                        if (event.type == PointerEventType.Exit || !open) {
                            null
                        } else {
                            hitTest.dayAt(change.position, isRtl)?.date ?: previewDate.value
                        }
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
