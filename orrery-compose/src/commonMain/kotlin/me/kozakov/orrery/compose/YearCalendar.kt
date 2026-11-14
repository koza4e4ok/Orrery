@file:OptIn(InternalOrreryApi::class)

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.InternalOrreryApi
import me.kozakov.orrery.compose.internal.MonthTextKey
import me.kozakov.orrery.core.DayInfo
import me.kozakov.orrery.core.Selection

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

    private var programmaticScrolls: Int by mutableIntStateOf(0)

    /** Year index the last programmatic scroll (or the initial state) left visible. */
    private var anchorIndex: Int by mutableIntStateOf(listState.firstVisibleItemIndex)
    private var anchorSource: ScrollSource by mutableStateOf(ScrollSource.Initial)

    /**
     * What brought [firstVisibleYear] into view: [ScrollSource.Initial] until
     * anything scrolls, [ScrollSource.Programmatic] during and after
     * [scrollToYear]/[animateScrollToYear], [ScrollSource.UserDrag] while the
     * user drags or flings the pager and after such a gesture settles on another
     * year. Snapshot-observable.
     */
    public val lastScrollSource: ScrollSource
        get() {
            if (programmaticScrolls > 0) return ScrollSource.Programmatic
            if (listState.isScrollInProgress) return ScrollSource.UserDrag
            val visible = listState.firstVisibleItemIndex
            val clampedAtEnd = visible < anchorIndex && !listState.canScrollForward
            return if (visible != anchorIndex && !clampedAtEnd) ScrollSource.UserDrag else anchorSource
        }

    private suspend fun programmaticScroll(
        target: Int,
        block: suspend () -> Unit,
    ) {
        anchorIndex = target
        anchorSource = ScrollSource.Programmatic
        programmaticScrolls++
        try {
            block()
        } finally {
            programmaticScrolls--
        }
    }

    public suspend fun scrollToYear(year: Int) {
        val target = indexOf(year)
        programmaticScroll(target) { listState.scrollToItem(target) }
    }

    public suspend fun animateScrollToYear(year: Int) {
        val target = indexOf(year)
        programmaticScroll(target) { listState.animateScrollToItem(target) }
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
    val caches = rememberMiniMonthCaches()
    val monthRows = remember(columns) { (1..12).chunked(columns) }
    CompositionLocalProvider(LocalMiniMonthCaches provides caches) {
        LazyRow(
            state = state.listState,
            modifier = modifier,
            flingBehavior = rememberSnapFlingBehavior(state.listState),
            userScrollEnabled = userScrollEnabled,
        ) {
            items(count = state.yearCount, key = { it }, contentType = { YEAR_PAGE_CONTENT_TYPE }) { index ->
                val year = state.startYear + index
                Column(Modifier.fillParentMaxWidth()) {
                    for (rowMonths in monthRows) {
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
}

private const val YEAR_PAGE_CONTENT_TYPE = "orrery-year-page"

private val MiniMonthFocusRingWidth = 2.dp
private val MiniMonthFocusRingShape = RoundedCornerShape(8.dp)

/**
 * Default mini-month cell for [YearCalendar]. [today] is drawn on a filled
 * `primary` circle with `onPrimary` text; dates in [selection] (the single
 * date, multi dates, and a range from start to end) sit on a subtle
 * `primaryContainer` pill; day numbers whose [dayInfo] reports
 * [DayInfo.isHoliday] use the `primary` color.
 *
 * The month is a single layout node drawn on a canvas with shared, cached
 * text layouts, so a [YearCalendar] page stays cheap to compose and scroll.
 * For accessibility it exposes the month name and year as its content
 * description, "today" as its state description when the month contains
 * [today], and the selected state when it contains selected dates; the
 * individual day numbers are not separate accessibility nodes.
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
    val caches = rememberMiniMonthCaches()
    val model = remember(caches, yearMonth, firstDayOfWeek) { caches.models[yearMonth, firstDayOfWeek] }
    val dayState = remember(model, today, selection, dayInfo) { miniMonthDayState(model, today, selection, dayInfo) }
    val baseStyle = LocalTextStyle.current
    val dayStyle = remember(baseStyle) { baseStyle.merge(MiniDayTextStyle) }
    val texts =
        caches.texts.texts(
            MonthTextKey(
                dayStyle = dayStyle,
                titleStyle = MaterialTheme.typography.titleSmall,
                density = LocalDensity.current,
                layoutDirection = LocalLayoutDirection.current,
                fontFamilyResolver = LocalFontFamilyResolver.current,
            ),
        )
    val title = texts.title(PlatformDateFormat.monthName(yearMonth.month, short = true))
    val scheme = MaterialTheme.colorScheme
    val colors =
        remember(scheme) {
            MiniMonthColors(
                today = scheme.primary,
                onToday = scheme.onPrimary,
                holiday = scheme.primary,
                pill = scheme.primaryContainer,
                onPill = scheme.onPrimaryContainer,
                day = scheme.onSurfaceVariant,
            )
        }
    val isCurrentMonth = dayState.todayDay > 0
    val titleColor = if (isCurrentMonth) scheme.primary else scheme.onSurface
    val painter =
        remember(model, dayState, texts, title, titleColor, colors) {
            MiniMonthPainter(model, dayState, texts, title, titleColor, colors)
        }
    val description = PlatformDateFormat.yearMonthTitle(yearMonth)
    val todayState = orreryString(OrreryString.DayToday)
    val hasSelection = dayState.selectedMask != 0
    Spacer(
        modifier
            .padding(8.dp)
            .layout { measurable, constraints ->
                val width =
                    if (constraints.hasBoundedWidth) {
                        constraints.maxWidth
                    } else {
                        constraints.constrainWidth(painter.minWidth)
                    }
                val height = constraints.constrainHeight(painter.contentHeight)
                val placeable = measurable.measure(Constraints.fixed(width, height))
                layout(width, height) { placeable.place(0, 0) }
            }.semantics {
                contentDescription = description
                if (isCurrentMonth) stateDescription = todayState
                if (hasSelection) selected = true
            }.drawBehind { painter.draw(this) },
    )
}

private val MiniDayTextStyle = TextStyle(fontSize = 9.sp, textAlign = TextAlign.Center)

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
