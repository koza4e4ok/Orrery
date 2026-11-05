package me.kozakov.orrery.compose

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.CalendarDay
import me.kozakov.orrery.core.CalendarWeek
import me.kozakov.orrery.core.daysOfWeek
import me.kozakov.orrery.core.monthGrid
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Snap stops of [CollapsibleCalendarScaffold]. [TwoWeeks] is only a snap
 * target when [CollapsibleCalendarState.enableTwoWeekStop] is on; at its
 * fraction the scaffold shows exactly two week rows.
 */
public enum class CollapsibleCalendarAnchor(
    public val expandFraction: Float,
) {
    Week(0f),
    TwoWeeks(0.35f),
    Month(1f),
}

/** Expand/collapse state for [CollapsibleCalendarScaffold]. */
@Stable
public class CollapsibleCalendarState internal constructor(
    initialFraction: Float,
    /** Whether drags and flings also settle on [CollapsibleCalendarAnchor.TwoWeeks]. */
    public val enableTwoWeekStop: Boolean,
) {
    internal constructor(initialExpanded: Boolean) : this(if (initialExpanded) 1f else 0f, false)

    /** 1f = full month visible, 0f = single week row. Snapshot-observable. */
    public var expandFraction: Float by mutableFloatStateOf(initialFraction.coerceIn(0f, 1f))
        private set

    public val isExpanded: Boolean
        get() = expandFraction > 0.5f

    /** Snap stops in collapse order: Week, (TwoWeeks,) Month. */
    internal val anchors: List<CollapsibleCalendarAnchor> =
        if (enableTwoWeekStop) {
            CollapsibleCalendarAnchor.entries
        } else {
            listOf(CollapsibleCalendarAnchor.Week, CollapsibleCalendarAnchor.Month)
        }

    /** The enabled anchor closest to [expandFraction]. Snapshot-observable. */
    public val currentAnchor: CollapsibleCalendarAnchor
        get() {
            val fraction = expandFraction
            return anchors.minBy { abs(it.expandFraction - fraction) }
        }

    /** True while a direct drag on the calendar is in progress. Snapshot-observable. */
    internal var isDragging: Boolean by mutableStateOf(false)

    private val mutex = MutatorMutex()

    public suspend fun expand(): Unit = animateTo(1f)

    public suspend fun collapse(): Unit = animateTo(0f)

    /** Animates to [anchor]; works for [CollapsibleCalendarAnchor.TwoWeeks] even without [enableTwoWeekStop]. */
    public suspend fun animateTo(anchor: CollapsibleCalendarAnchor): Unit = animateTo(anchor.expandFraction)

    /**
     * Springs [expandFraction] to [target], starting at [initialVelocity]
     * (expand fraction per second, positive = expanding) so a released drag or
     * fling keeps its momentum.
     */
    internal suspend fun animateTo(
        target: Float,
        initialVelocity: Float = 0f,
    ) {
        val coerced = target.coerceIn(0f, 1f)
        mutex.mutate {
            animate(
                initialValue = expandFraction,
                targetValue = coerced,
                initialVelocity = initialVelocity,
                animationSpec = SettleSpring,
            ) { value, _ ->
                expandFraction = value.coerceIn(0f, 1f)
            }
        }
    }

    /** Cancels a running [animateTo] so a drag can take over. */
    internal suspend fun stopAnimation() {
        mutex.mutate(MutatePriority.UserInput) {}
    }

    internal fun dragBy(delta: Float): Float {
        val old = expandFraction
        expandFraction = (old + delta).coerceIn(0f, 1f)
        return expandFraction - old
    }

    internal fun snapTo(fraction: Float) {
        expandFraction = fraction.coerceIn(0f, 1f)
    }

    /**
     * Where a released drag settles: the next anchor in the fling direction when
     * |[velocity]| exceeds [velocityThreshold] (px/s, positive = expanding), else
     * the nearest anchor (ties collapse).
     */
    internal fun settleTarget(
        velocity: Float,
        velocityThreshold: Float,
    ): Float {
        val fraction = expandFraction
        val stops = anchors.map { it.expandFraction }
        return when {
            velocity < 0f && -velocity > velocityThreshold ->
                stops.lastOrNull { it < fraction - SETTLE_EPSILON } ?: stops.first()

            velocity > 0f && velocity > velocityThreshold ->
                stops.firstOrNull { it > fraction + SETTLE_EPSILON } ?: stops.last()

            // Nearest stop; on a tie the lower (more collapsed) one wins.
            else -> stops.minBy { stop -> abs(stop - fraction) + (if (stop > fraction) SETTLE_EPSILON else 0f) }
        }
    }

    public companion object {
        public val Saver: Saver<CollapsibleCalendarState, Boolean> =
            Saver(
                save = { it.isExpanded },
                restore = { CollapsibleCalendarState(it) },
            )

        /** Saves the current anchor and the two-week flag. */
        internal val AnchorSaver: Saver<CollapsibleCalendarState, Any> =
            listSaver(
                save = { listOf(it.currentAnchor.ordinal, it.enableTwoWeekStop) },
                restore = {
                    CollapsibleCalendarState(
                        initialFraction = CollapsibleCalendarAnchor.entries[it[0] as Int].expandFraction,
                        enableTwoWeekStop = it[1] as Boolean,
                    )
                },
            )
    }
}

private const val SETTLE_EPSILON = 0.001f

private val SettleSpring =
    spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

@Composable
public fun rememberCollapsibleCalendarState(initialExpanded: Boolean = true): CollapsibleCalendarState =
    rememberSaveable(saver = CollapsibleCalendarState.Saver) {
        CollapsibleCalendarState(initialExpanded)
    }

/**
 * Remembers a saveable [CollapsibleCalendarState] that starts at [initialAnchor]
 * and, with [enableTwoWeekStop], also settles on [CollapsibleCalendarAnchor.TwoWeeks].
 */
@Composable
public fun rememberCollapsibleCalendarState(
    initialAnchor: CollapsibleCalendarAnchor,
    enableTwoWeekStop: Boolean = false,
): CollapsibleCalendarState = rememberAnchoredCollapsibleState(initialAnchor.expandFraction, enableTwoWeekStop)

/** [rememberCollapsibleCalendarState] with a [CollapsibleCalendarAnchor.TwoWeeks] stop switch. */
@Composable
public fun rememberCollapsibleCalendarState(
    initialExpanded: Boolean = true,
    enableTwoWeekStop: Boolean,
): CollapsibleCalendarState = rememberAnchoredCollapsibleState(if (initialExpanded) 1f else 0f, enableTwoWeekStop)

@Composable
private fun rememberAnchoredCollapsibleState(
    initialFraction: Float,
    enableTwoWeekStop: Boolean,
): CollapsibleCalendarState =
    rememberSaveable(enableTwoWeekStop, saver = CollapsibleCalendarState.AnchorSaver) {
        CollapsibleCalendarState(initialFraction, enableTwoWeekStop)
    }

/**
 * Replaces the original CalendarLayout: a month calendar that collapses into
 * a week row as the [content] below is scrolled up, and expands back when the
 * content is scrolled down at its top. Vertical drags directly on the week
 * header and the calendar collapse/expand it too. Programmatic control via
 * [CollapsibleCalendarState.expand]/[CollapsibleCalendarState.collapse].
 */
@Composable
public fun CollapsibleCalendarScaffold(
    calendarState: CalendarState,
    weekState: WeekCalendarState,
    modifier: Modifier = Modifier,
    state: CollapsibleCalendarState = rememberCollapsibleCalendarState(),
    dayHeight: Dp = 48.dp,
    gesturesEnabled: Boolean = true,
    weekHeader: (@Composable ColumnScope.(List<DayOfWeek>) -> Unit)? = { CalendarDefaults.WeekHeader(it) },
    dayContent: @Composable BoxScope.(CalendarDay) -> Unit,
    content: @Composable () -> Unit,
) {
    CollapsibleCalendarScaffoldImpl(
        calendarState = calendarState,
        weekState = weekState,
        modifier = modifier,
        state = state,
        dayHeight = dayHeight,
        gesturesEnabled = gesturesEnabled,
        enableDirectDrag = true,
        dragHandle = null,
        weekNumber = null,
        weekHeader = weekHeader,
        dayContent = dayContent,
        content = content,
    )
}

/**
 * [CollapsibleCalendarScaffold] with direct-drag options. With
 * [enableDirectDrag] (default), vertical drags on the week header, the
 * calendar grid and the optional [dragHandle] (drawn below the calendar, for
 * example [CalendarDefaults.CollapsibleDragHandle]) collapse and expand the
 * calendar even when [content] does not scroll; releases snap to the
 * [CollapsibleCalendarAnchor]s enabled on [state]. [gesturesEnabled] = false
 * turns off both nested-scroll and direct-drag gestures.
 *
 * While collapsed, paging the week strip horizontally keeps [calendarState]
 * on the paged week's month, and while expanded, paging months keeps
 * [weekState] inside the visible month.
 *
 * @param showWeekNumbers Shows a leading week-number column in both the month
 *   grid and the collapsed week strip, as in [HorizontalCalendar].
 * @param weekNumber Content of that column for each week.
 */
@Composable
public fun CollapsibleCalendarScaffold(
    calendarState: CalendarState,
    weekState: WeekCalendarState,
    modifier: Modifier = Modifier,
    state: CollapsibleCalendarState = rememberCollapsibleCalendarState(),
    dayHeight: Dp = 48.dp,
    gesturesEnabled: Boolean = true,
    enableDirectDrag: Boolean = true,
    dragHandle: (@Composable () -> Unit)? = null,
    showWeekNumbers: Boolean = false,
    weekNumber: @Composable (CalendarWeek) -> Unit = { CalendarDefaults.WeekNumber(it) },
    weekHeader: (@Composable ColumnScope.(List<DayOfWeek>) -> Unit)? = {
        CalendarDefaults.WeekHeader(it, leadingSpacer = showWeekNumbers)
    },
    dayContent: @Composable BoxScope.(CalendarDay) -> Unit,
    content: @Composable () -> Unit,
) {
    CollapsibleCalendarScaffoldImpl(
        calendarState = calendarState,
        weekState = weekState,
        modifier = modifier,
        state = state,
        dayHeight = dayHeight,
        gesturesEnabled = gesturesEnabled,
        enableDirectDrag = enableDirectDrag,
        dragHandle = dragHandle,
        weekNumber = if (showWeekNumbers) weekNumber else null,
        weekHeader = weekHeader,
        dayContent = dayContent,
        content = content,
    )
}

@Deprecated("Kept for binary compatibility; use the overload with showWeekNumbers.", level = DeprecationLevel.HIDDEN)
@Composable
public fun CollapsibleCalendarScaffold(
    calendarState: CalendarState,
    weekState: WeekCalendarState,
    modifier: Modifier = Modifier,
    state: CollapsibleCalendarState = rememberCollapsibleCalendarState(),
    dayHeight: Dp = 48.dp,
    gesturesEnabled: Boolean = true,
    enableDirectDrag: Boolean = true,
    dragHandle: (@Composable () -> Unit)? = null,
    weekHeader: (@Composable ColumnScope.(List<DayOfWeek>) -> Unit)? = { CalendarDefaults.WeekHeader(it) },
    dayContent: @Composable BoxScope.(CalendarDay) -> Unit,
    content: @Composable () -> Unit,
) {
    CollapsibleCalendarScaffoldImpl(
        calendarState = calendarState,
        weekState = weekState,
        modifier = modifier,
        state = state,
        dayHeight = dayHeight,
        gesturesEnabled = gesturesEnabled,
        enableDirectDrag = enableDirectDrag,
        dragHandle = dragHandle,
        weekNumber = null,
        weekHeader = weekHeader,
        dayContent = dayContent,
        content = content,
    )
}

/** A Material 3 grab bar for the `dragHandle` slot of [CollapsibleCalendarScaffold]. */
@Composable
public fun CalendarDefaults.CollapsibleDragHandle(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
) {
    Box(modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(width = 32.dp, height = 4.dp).background(color, RoundedCornerShape(2.dp)))
    }
}

/**
 * Pixel geometry of the morph: heights and the vertical shift of the month
 * grid as a function of the expand fraction. Week (0f) shows the anchor week
 * row, TwoWeeks shows exactly that row plus its neighbour, Month (1f) all rows.
 */
internal class CollapsibleGeometry(
    private val dayPx: Float,
    private val rows: Int,
    private val anchorRow: Int,
) {
    private val twoWeeks = CollapsibleCalendarAnchor.TwoWeeks.expandFraction
    private val twoWeekTopRow = anchorRow.coerceAtMost(rows - 2).coerceAtLeast(0)
    private val minHeight = dayPx
    private val midHeight = dayPx * minOf(2, rows)
    private val maxHeight = dayPx * rows

    fun heightAt(fraction: Float): Float =
        if (fraction >= twoWeeks) {
            lerp(midHeight, maxHeight, (fraction - twoWeeks) / (1f - twoWeeks))
        } else {
            lerp(minHeight, midHeight, fraction / twoWeeks)
        }

    fun shiftAt(fraction: Float): Float =
        if (fraction >= twoWeeks) {
            lerp(twoWeekTopRow * dayPx, 0f, (fraction - twoWeeks) / (1f - twoWeeks))
        } else {
            lerp(anchorRow * dayPx, twoWeekTopRow * dayPx, fraction / twoWeeks)
        }

    fun fractionAt(height: Float): Float {
        val h = height.coerceIn(minHeight, maxHeight)
        return if (h >= midHeight) {
            val span = maxHeight - midHeight
            if (span <= 0f) 1f else twoWeeks + (h - midHeight) / span * (1f - twoWeeks)
        } else {
            val span = midHeight - minHeight
            if (span <= 0f) 0f else (h - minHeight) / span * twoWeeks
        }
    }

    /**
     * Converts a release velocity in px/s (positive = expand) into expand
     * fraction per second at [fraction], using the slope of the segment the
     * fraction is on ([heightAt] is piecewise linear around TwoWeeks).
     */
    fun fractionVelocity(
        fraction: Float,
        velocityPx: Float,
    ): Float {
        val upper = fraction >= twoWeeks
        val spanPx = if (upper) maxHeight - midHeight else midHeight - minHeight
        val spanFraction = if (upper) 1f - twoWeeks else twoWeeks
        return if (spanPx <= 0f) 0f else velocityPx * spanFraction / spanPx
    }

    /** Applies a vertical drag of [deltaPx] (positive = expand); returns the consumed pixels. */
    fun dragBy(
        state: CollapsibleCalendarState,
        deltaPx: Float,
    ): Float {
        val old = heightAt(state.expandFraction)
        val new = (old + deltaPx).coerceIn(minHeight, maxHeight)
        if (new == old) return 0f
        state.snapTo(fractionAt(new))
        return new - old
    }

    private fun lerp(
        start: Float,
        stop: Float,
        fraction: Float,
    ): Float = start + (stop - start) * fraction.coerceIn(0f, 1f)
}

private val SETTLE_VELOCITY_THRESHOLD = 125.dp

private fun CalendarWeek.startDate(): LocalDate = days.first().date

private fun CalendarWeek.touches(month: YearMonth): Boolean =
    days.any { YearMonth(it.date.year, it.date.month) == month }

@Composable
private fun CollapsibleCalendarScaffoldImpl(
    calendarState: CalendarState,
    weekState: WeekCalendarState,
    modifier: Modifier,
    state: CollapsibleCalendarState,
    dayHeight: Dp,
    gesturesEnabled: Boolean,
    enableDirectDrag: Boolean,
    dragHandle: (@Composable () -> Unit)?,
    weekNumber: (@Composable (CalendarWeek) -> Unit)?,
    weekHeader: (@Composable ColumnScope.(List<DayOfWeek>) -> Unit)?,
    dayContent: @Composable BoxScope.(CalendarDay) -> Unit,
    content: @Composable () -> Unit,
) {
    val visibleMonth = calendarState.firstVisibleMonth
    val grid =
        remember(visibleMonth, calendarState.firstDayOfWeek, calendarState.outDateStyle) {
            monthGrid(visibleMonth, calendarState.firstDayOfWeek, calendarState.outDateStyle)
        }
    val rows = grid.weeks.size
    val weekStart = weekState.firstVisibleWeek.startDate()
    val anchorRow = grid.weeks.indexOfFirst { it.startDate() == weekStart }.coerceAtLeast(0)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dayPx = with(density) { dayHeight.toPx() }
    val geometry = remember(dayPx, rows, anchorRow) { CollapsibleGeometry(dayPx, rows, anchorRow) }
    val currentGeometry by rememberUpdatedState(geometry)
    val thresholdPx = with(density) { SETTLE_VELOCITY_THRESHOLD.toPx() }

    // Collapsed: paging the week strip moves the month pager along.
    LaunchedEffect(calendarState, weekState, state) {
        snapshotFlow { weekState.firstVisibleWeek }.collect { week ->
            if (state.expandFraction > 0f) return@collect
            val current = calendarState.firstVisibleMonth
            if (week.touches(current)) return@collect
            val start = week.startDate()
            val date = if (YearMonth(start.year, start.month) > current) start else week.days.last().date
            calendarState.scrollToMonth(YearMonth(date.year, date.month))
        }
    }
    // Expanded: paging months keeps the week strip inside the visible month.
    LaunchedEffect(calendarState, weekState, state) {
        snapshotFlow { calendarState.firstVisibleMonth }.collect { month ->
            if (state.expandFraction <= 0f) return@collect
            if (weekState.firstVisibleWeek.touches(month)) return@collect
            weekState.scrollToDate(LocalDate(month.year, month.month, 1))
        }
    }

    val connection =
        remember(state, gesturesEnabled) {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (!gesturesEnabled) return Offset.Zero
                    val dy = available.y
                    if (dy < 0f && state.expandFraction > 0f) {
                        return Offset(0f, currentGeometry.dragBy(state, dy))
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (!gesturesEnabled) return Offset.Zero
                    val dy = available.y
                    if (dy > 0f && state.expandFraction < 1f) {
                        return Offset(0f, currentGeometry.dragBy(state, dy))
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    val fraction = state.expandFraction
                    if (gesturesEnabled && fraction > 0f && fraction < 1f) {
                        val target = state.settleTarget(available.y, velocityThreshold = 0f)
                        val velocity = currentGeometry.fractionVelocity(fraction, available.y)
                        scope.launch { state.animateTo(target, initialVelocity = velocity) }
                        return available
                    }
                    return Velocity.Zero
                }
            }
        }
    val dragState = rememberDraggableState { delta -> currentGeometry.dragBy(state, delta) }
    val dragModifier =
        Modifier.draggable(
            state = dragState,
            orientation = Orientation.Vertical,
            enabled = gesturesEnabled && enableDirectDrag,
            onDragStarted = {
                state.isDragging = true
                state.stopAnimation()
            },
            onDragStopped = { velocity ->
                state.isDragging = false
                val target = state.settleTarget(velocity, thresholdPx)
                val fractionVelocity = currentGeometry.fractionVelocity(state.expandFraction, velocity)
                state.animateTo(target, initialVelocity = fractionVelocity)
            },
        )
    Column(modifier.nestedScroll(connection)) {
        Column(dragModifier) {
            weekHeader?.invoke(this, daysOfWeek(calendarState.firstDayOfWeek))
            val fraction = state.expandFraction
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(with(density) { geometry.heightAt(fraction).toDp() })
                    .clipToBounds(),
            ) {
                // The month grid stays fully opaque while it morphs: the clip and
                // the vertical shift leave only the anchor week row visible at 0f,
                // so swapping in the week pager once settled shows no blink.
                if (fraction > 0f || state.isDragging) {
                    HorizontalCalendar(
                        state = calendarState,
                        showWeekNumbers = weekNumber != null,
                        weekNumber = weekNumber ?: { CalendarDefaults.WeekNumber(it) },
                        weekHeader = null,
                        animateHeight = false,
                        dayContent = dayContent,
                        modifier =
                            Modifier
                                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                                .offset { IntOffset(0, -geometry.shiftAt(state.expandFraction).roundToInt()) },
                    )
                } else {
                    WeekCalendar(
                        state = weekState,
                        showWeekNumbers = weekNumber != null,
                        weekNumber = weekNumber ?: { CalendarDefaults.WeekNumber(it) },
                        weekHeader = null,
                        dayContent = dayContent,
                    )
                }
            }
            dragHandle?.invoke()
        }
        Box(Modifier.weight(1f)) { content() }
    }
}
