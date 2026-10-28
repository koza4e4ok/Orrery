package me.kozakov.orrery.compose

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.DayInfo
import me.kozakov.orrery.core.DayPosition
import me.kozakov.orrery.core.OutDateStyle
import me.kozakov.orrery.core.Selection
import me.kozakov.orrery.core.monthGrid

/** State holder for [YearCalendar]. */
@Stable
public class YearCalendarState internal constructor(
    public val startYear: Int,
    public val endYear: Int,
    firstVisibleYear: Int,
) {
    internal val listState: LazyListState =
        LazyListState(
            firstVisibleItemIndex = indexOf(firstVisibleYear),
        )

    public val yearCount: Int
        get() = endYear - startYear + 1

    public val firstVisibleYear: Int
        get() = startYear + listState.firstVisibleItemIndex

    public suspend fun scrollToYear(year: Int) {
        listState.scrollToItem(indexOf(year))
    }

    public suspend fun animateScrollToYear(year: Int) {
        listState.animateScrollToItem(indexOf(year))
    }

    private fun indexOf(year: Int): Int = (year - startYear).coerceIn(0, endYear - startYear)

    public companion object {
        public val Saver: Saver<YearCalendarState, Any> =
            listSaver(
                save = { listOf(it.startYear, it.endYear, it.firstVisibleYear) },
                restore = { YearCalendarState(it[0], it[1], it[2]) },
            )
    }
}

@Composable
public fun rememberYearCalendarState(
    startYear: Int = 1971,
    endYear: Int = 2055,
    firstVisibleYear: Int = currentYearMonth().year,
): YearCalendarState =
    rememberSaveable(saver = YearCalendarState.Saver) {
        YearCalendarState(startYear, endYear, firstVisibleYear)
    }

/**
 * Year overview: one year per page, months in a [columns]-wide grid. A
 * mini-month cell draws a 2.dp `primary` focus ring inside its bounds
 * while it has keyboard / D-pad focus.
 */
@Composable
public fun YearCalendar(
    state: YearCalendarState = rememberYearCalendarState(),
    modifier: Modifier = Modifier,
    userScrollEnabled: Boolean = true,
    columns: Int = 3,
    onMonthClick: (YearMonth) -> Unit,
    monthContent: @Composable BoxScope.(YearMonth) -> Unit = { CalendarDefaults.MiniMonth(it) },
) {
    LazyRow(
        state = state.listState,
        modifier = modifier,
        flingBehavior = rememberSnapFlingBehavior(state.listState),
        userScrollEnabled = userScrollEnabled,
    ) {
        items(count = state.yearCount, key = { it }) { index ->
            val year = state.startYear + index
            Column(Modifier.fillParentMaxWidth()) {
                for (rowMonths in (1..12).chunked(columns)) {
                    Row(Modifier.fillMaxWidth()) {
                        rowMonths.forEach { m ->
                            val ym = YearMonth(year, m)
                            val interactionSource = remember { MutableInteractionSource() }
                            val isFocused by interactionSource.collectIsFocusedAsState()
                            Box(
                                Modifier
                                    .weight(1f)
                                    .focusRing(
                                        focused = isFocused,
                                        color = MaterialTheme.colorScheme.primary,
                                        width = MiniMonthFocusRingWidth,
                                        shape = MiniMonthFocusRingShape,
                                        placement = FocusRingPlacement.Inside,
                                    ).clip(MiniMonthFocusRingShape)
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = LocalIndication.current,
                                        role = Role.Button,
                                    ) { onMonthClick(ym) },
                            ) { monthContent(ym) }
                        }
                    }
                }
            }
        }
    }
}

private val MiniMonthFocusRingWidth = 2.dp
private val MiniMonthFocusRingShape = RoundedCornerShape(8.dp)

/**
 * Default mini-month cell for [YearCalendar]. [today] is drawn on a filled
 * `primary` circle with `onPrimary` text; dates in [selection] (the single
 * date, multi dates, and a range from start to end) sit on a subtle
 * `primaryContainer` pill; day numbers whose [dayInfo] reports
 * [DayInfo.isHoliday] use the `primary` color.
 */
@Composable
public fun CalendarDefaults.MiniMonth(
    yearMonth: YearMonth,
    modifier: Modifier = Modifier,
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    today: LocalDate = currentDate(),
    selection: Selection? = null,
    dayInfo: ((LocalDate) -> DayInfo?)? = null,
) {
    val isCurrentMonth = yearMonth.year == today.year && yearMonth.month == today.month
    val colors = MaterialTheme.colorScheme
    val range = selection?.range
    val lastDay = yearMonth.numberOfDays
    Column(modifier.padding(8.dp)) {
        Text(
            text = PlatformDateFormat.monthName(yearMonth.month, short = true),
            style = MaterialTheme.typography.titleSmall,
            color =
                if (isCurrentMonth) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
        )
        monthGrid(yearMonth, firstDayOfWeek, OutDateStyle.None).weeks.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.days.forEachIndexed { column, day ->
                    val date = day.date
                    val inMonth = day.position == DayPosition.MonthDate
                    val isToday = inMonth && date == today
                    val inRange = inMonth && range != null && date in range
                    val selected =
                        inMonth &&
                            selection != null &&
                            (
                                inRange ||
                                    date == selection.single ||
                                    date in selection.multi ||
                                    date == selection.rangeStart ||
                                    date == selection.rangeEnd
                            )
                    val isHoliday = inMonth && dayInfo?.invoke(date)?.isHoliday == true
                    val pill =
                        if (selected) {
                            MiniPill(
                                roundStart =
                                    !inRange || date == range?.start || column == 0 || date.day == 1,
                                roundEnd =
                                    !inRange ||
                                        date == range?.endInclusive ||
                                        column == week.days.lastIndex ||
                                        date.day == lastDay,
                            )
                        } else {
                            null
                        }
                    Text(
                        text = if (inMonth) date.day.toString() else "",
                        modifier =
                            Modifier
                                .weight(1f)
                                .drawBehind {
                                    drawMiniDayBackground(
                                        pill = pill,
                                        pillColor = colors.primaryContainer,
                                        todayColor = if (isToday) colors.primary else null,
                                    )
                                },
                        fontSize = 9.sp,
                        fontWeight = if (isToday) FontWeight.Bold else null,
                        textAlign = TextAlign.Center,
                        color =
                            when {
                                isToday -> colors.onPrimary
                                isHoliday -> colors.primary
                                selected -> colors.onPrimaryContainer
                                else -> colors.onSurfaceVariant
                            },
                    )
                }
            }
        }
    }
}

@Deprecated(
    "Kept for binary compatibility; use the overload with selection and dayInfo.",
    level = DeprecationLevel.HIDDEN,
)
@Composable
public fun CalendarDefaults.MiniMonth(
    yearMonth: YearMonth,
    modifier: Modifier = Modifier,
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    today: LocalDate = currentDate(),
) {
    MiniMonth(
        yearMonth = yearMonth,
        modifier = modifier,
        firstDayOfWeek = firstDayOfWeek,
        today = today,
        selection = null,
        dayInfo = null,
    )
}

/** Which ends of a mini-month selection pill segment are rounded. */
private class MiniPill(
    val roundStart: Boolean,
    val roundEnd: Boolean,
)

private val MiniDayMaxRadius = 8.dp

private fun DrawScope.drawMiniDayBackground(
    pill: MiniPill?,
    pillColor: Color,
    todayColor: Color?,
) {
    val radius = minOf(size.width, size.height, MiniDayMaxRadius.toPx() * 2) / 2
    val top = (size.height - radius * 2) / 2
    if (pill != null) {
        val rtl = layoutDirection == LayoutDirection.Rtl
        val roundLeft = if (rtl) pill.roundEnd else pill.roundStart
        val roundRight = if (rtl) pill.roundStart else pill.roundEnd
        // Rounded ends inset to the circle's edge so a lone date reads as a circle-like pill.
        val left = if (roundLeft) center.x - radius else 0f
        val right = if (roundRight) center.x + radius else size.width
        drawRoundRect(
            color = pillColor,
            topLeft = Offset(left, top),
            size = Size(right - left, radius * 2),
            cornerRadius = CornerRadius(radius),
        )
        if (!roundLeft) {
            drawRect(pillColor, Offset(0f, top), Size(center.x, radius * 2))
        }
        if (!roundRight) {
            drawRect(pillColor, Offset(center.x, top), Size(size.width - center.x, radius * 2))
        }
    }
    if (todayColor != null) {
        drawCircle(todayColor, radius, center)
    }
}
