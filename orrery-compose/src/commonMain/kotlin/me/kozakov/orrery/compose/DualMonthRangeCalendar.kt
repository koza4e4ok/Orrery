package me.kozakov.orrery.compose

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.CalendarPages
import me.kozakov.orrery.core.daysOfWeek

/** Widest and tallest the dual-month dialog card grows. */
internal val DualMonthDialogMaxWidth = 840.dp
internal val DualMonthDialogMaxHeight = 720.dp

/** Padding on each side of a month pane, so the gap between the two grids is twice this. */
private val DualMonthPanePadding = 12.dp

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
            CalendarKeyboardNavigation(scope, dualMonth = true) { date ->
                val target = YearMonth(date.year, date.month)
                dualMonthPageTarget(calendar.firstVisibleMonth, target, calendar.effectiveStartMonth)
                    ?.let { calendar.animateScrollToMonth(it) }
            }
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
                                )
                            },
                            dragSelection = state.selection,
                            keyboardNavigation = keyboard,
                        )
                    }
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
