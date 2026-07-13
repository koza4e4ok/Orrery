package dev.koza4e4ok.orrery.compose

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import dev.koza4e4ok.orrery.core.CalendarDay
import dev.koza4e4ok.orrery.core.daysOfWeek
import dev.koza4e4ok.orrery.core.monthGrid
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek

/** Expand/collapse state for [CollapsibleCalendarScaffold]. */
@Stable
public class CollapsibleCalendarState internal constructor(
    initialExpanded: Boolean,
) {
    /** 1f = full month visible, 0f = single week row. Snapshot-observable. */
    public var expandFraction: Float by mutableFloatStateOf(if (initialExpanded) 1f else 0f)
        private set

    public val isExpanded: Boolean
        get() = expandFraction > 0.5f

    public suspend fun expand(): Unit = animateTo(1f)

    public suspend fun collapse(): Unit = animateTo(0f)

    internal suspend fun animateTo(target: Float) {
        animate(expandFraction, target, animationSpec = tween(240)) { value, _ ->
            expandFraction = value
        }
    }

    internal fun dragBy(delta: Float): Float {
        val old = expandFraction
        expandFraction = (old + delta).coerceIn(0f, 1f)
        return expandFraction - old
    }

    public companion object {
        public val Saver: Saver<CollapsibleCalendarState, Boolean> =
            Saver(
                save = { it.isExpanded },
                restore = { CollapsibleCalendarState(it) },
            )
    }
}

@Composable
public fun rememberCollapsibleCalendarState(initialExpanded: Boolean = true): CollapsibleCalendarState =
    rememberSaveable(saver = CollapsibleCalendarState.Saver) {
        CollapsibleCalendarState(initialExpanded)
    }

/**
 * Replaces the original CalendarLayout: a month calendar that collapses into
 * a week row as the [content] below is scrolled up, and expands back when the
 * content is scrolled down at its top. Programmatic control via
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
    val visibleMonth = calendarState.firstVisibleMonth
    val rows =
        remember(visibleMonth, calendarState.firstDayOfWeek, calendarState.outDateStyle) {
            monthGrid(visibleMonth, calendarState.firstDayOfWeek, calendarState.outDateStyle).weeks.size
        }
    val monthHeight = dayHeight * rows
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val connection =
        remember(state, gesturesEnabled, dayHeight, rows, density) {
            val rangePx = with(density) { (monthHeight - dayHeight).toPx() }.coerceAtLeast(1f)
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (!gesturesEnabled) return Offset.Zero
                    val dy = available.y
                    if (dy < 0f && state.expandFraction > 0f) {
                        return Offset(0f, state.dragBy(dy / rangePx) * rangePx)
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
                        return Offset(0f, state.dragBy(dy / rangePx) * rangePx)
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    val fraction = state.expandFraction
                    if (gesturesEnabled && fraction > 0f && fraction < 1f) {
                        val target = if (available.y < 0f || (available.y == 0f && fraction <= 0.5f)) 0f else 1f
                        scope.launch { state.animateTo(target) }
                        return available
                    }
                    return Velocity.Zero
                }
            }
        }
    Column(modifier.nestedScroll(connection)) {
        weekHeader?.invoke(this, daysOfWeek(calendarState.firstDayOfWeek))
        Box(
            Modifier
                .fillMaxWidth()
                .height(lerp(dayHeight, monthHeight, state.expandFraction))
                .clipToBounds(),
        ) {
            if (state.expandFraction > 0f) {
                HorizontalCalendar(
                    state = calendarState,
                    weekHeader = null,
                    animateHeight = false,
                    dayContent = dayContent,
                    modifier = Modifier.graphicsLayer { alpha = state.expandFraction },
                )
            } else {
                WeekCalendar(
                    state = weekState,
                    weekHeader = null,
                    dayContent = dayContent,
                )
            }
        }
        Box(Modifier.weight(1f)) { content() }
    }
}
