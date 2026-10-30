package me.kozakov.orrery.compose

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.CalendarPages
import me.kozakov.orrery.core.OutDateStyle
import kotlin.math.abs

/** Far animated jumps teleport near the target first (months). */
internal const val FAR_JUMP_MONTHS: Int = 12

private val UNBOUNDED_START_MONTH = YearMonth(-19999, 1)
private val UNBOUNDED_END_MONTH = YearMonth(19999, 12)

/** State holder for [HorizontalCalendar] and [VerticalCalendar]. */
@Stable
public class CalendarState internal constructor(
    startMonth: YearMonth?,
    endMonth: YearMonth?,
    firstVisibleMonth: YearMonth,
    firstDayOfWeek: DayOfWeek,
    outDateStyle: OutDateStyle,
    visibleDaysOfWeek: Set<DayOfWeek> = ALL_DAYS_OF_WEEK_SET,
) {
    init {
        require(visibleDaysOfWeek.isNotEmpty()) {
            "visibleDaysOfWeek must not be empty"
        }
    }

    /** Inclusive range start; null scrolls unboundedly into the past. */
    public var startMonth: YearMonth? by mutableStateOf(startMonth)
        private set

    /** Inclusive range end; null scrolls unboundedly into the future. */
    public var endMonth: YearMonth? by mutableStateOf(endMonth)
        private set

    internal val effectiveStartMonth: YearMonth
        get() = startMonth ?: UNBOUNDED_START_MONTH

    internal val effectiveEndMonth: YearMonth
        get() = endMonth ?: UNBOUNDED_END_MONTH
    public var firstDayOfWeek: DayOfWeek by mutableStateOf(firstDayOfWeek)
    public var outDateStyle: OutDateStyle by mutableStateOf(outDateStyle)

    private var visibleDaysOfWeekState: Set<DayOfWeek> by mutableStateOf(visibleDaysOfWeek)

    /** Subset of weekdays rendered in each week row (must not be empty). */
    public var visibleDaysOfWeek: Set<DayOfWeek>
        get() = visibleDaysOfWeekState
        set(value) {
            require(value.isNotEmpty()) { "visibleDaysOfWeek must not be empty" }
            visibleDaysOfWeekState = value
        }

    /** List items per month: 2 when sticky headers add a header item per month. */
    internal var itemsPerMonth: Int = 1

    internal val listState: LazyListState =
        LazyListState(
            firstVisibleItemIndex = indexOf(firstVisibleMonth),
        )

    /** Months in the effective range; unbounded sides count to the supported date extremes. */
    public val monthCount: Int
        get() = CalendarPages.monthCount(effectiveStartMonth, effectiveEndMonth)

    /** The month at the first visible page. Snapshot-observable. */
    public val firstVisibleMonth: YearMonth
        get() = CalendarPages.monthAt(effectiveStartMonth, listState.firstVisibleItemIndex / itemsPerMonth)

    public suspend fun scrollToMonth(month: YearMonth) {
        listState.scrollToItem(indexOf(month))
    }

    public suspend fun animateScrollToMonth(month: YearMonth) {
        val target = indexOf(month)
        val current = listState.firstVisibleItemIndex
        if (abs(target - current) > FAR_JUMP_MONTHS * itemsPerMonth) {
            val approach =
                if (target > current) target - 2 * itemsPerMonth else target + 2 * itemsPerMonth
            listState.scrollToItem(approach.coerceIn(0, (monthCount - 1) * itemsPerMonth))
        }
        listState.animateScrollToItem(target)
    }

    /** Animates back to the current month. */
    public suspend fun animateScrollToToday() {
        animateScrollToMonth(currentYearMonth())
    }

    public suspend fun scrollToDate(date: LocalDate) {
        scrollToMonth(YearMonth(date.year, date.month))
    }

    /** Animated counterpart of [scrollToDate]. */
    public suspend fun animateScrollToDate(date: LocalDate) {
        animateScrollToMonth(YearMonth(date.year, date.month))
    }

    /** Changes the month range (null = unbounded side), keeping the visible month if still in range. */
    public fun updateRange(
        startMonth: YearMonth?,
        endMonth: YearMonth?,
    ) {
        if (startMonth != null && endMonth != null) {
            require(startMonth <= endMonth) { "startMonth must be <= endMonth" }
        }
        val visible = firstVisibleMonth
        this.startMonth = startMonth
        this.endMonth = endMonth
        listState.requestScrollToItem(indexOf(visible))
    }

    private fun indexOf(month: YearMonth): Int =
        CalendarPages.monthIndex(effectiveStartMonth, month).coerceIn(0, monthCount - 1) * itemsPerMonth

    public companion object {
        public val Saver: Saver<CalendarState, Any> =
            listSaver(
                save = {
                    listOf(
                        it.startMonth?.toString() ?: "",
                        it.endMonth?.toString() ?: "",
                        it.firstVisibleMonth.toString(),
                        it.firstDayOfWeek.ordinal,
                        it.outDateStyle.ordinal,
                        it.visibleDaysOfWeek.toBitMask(),
                    )
                },
                restore = {
                    CalendarState(
                        startMonth = (it[0] as String).takeIf(String::isNotEmpty)?.let(YearMonth::parse),
                        endMonth = (it[1] as String).takeIf(String::isNotEmpty)?.let(YearMonth::parse),
                        firstVisibleMonth = YearMonth.parse(it[2] as String),
                        firstDayOfWeek = DayOfWeek.entries[it[3] as Int],
                        outDateStyle = OutDateStyle.entries[it[4] as Int],
                        visibleDaysOfWeek =
                            (it.getOrNull(5) as? Int)?.let(::dayOfWeekSetFromBitMask)
                                ?: ALL_DAYS_OF_WEEK_SET,
                    )
                },
            )
    }
}

/**
 * Remembers saveable [CalendarState]. Null [startMonth]/[endMonth] (the
 * defaults) leave that side unbounded — the calendar scrolls indefinitely.
 */
@Composable
public fun rememberCalendarState(
    startMonth: YearMonth? = null,
    endMonth: YearMonth? = null,
    firstVisibleMonth: YearMonth = currentYearMonth(),
    firstDayOfWeek: DayOfWeek = PlatformDateFormat.firstDayOfWeek(),
    outDateStyle: OutDateStyle = OutDateStyle.EndOfRow,
): CalendarState =
    rememberSaveable(saver = CalendarState.Saver) {
        CalendarState(startMonth, endMonth, firstVisibleMonth, firstDayOfWeek, outDateStyle)
    }

/**
 * Remembers saveable [CalendarState] configured with [visibleDaysOfWeek]
 * (e.g. Monday–Friday work week).
 */
@Composable
public fun rememberCalendarState(
    startMonth: YearMonth? = null,
    endMonth: YearMonth? = null,
    firstVisibleMonth: YearMonth = currentYearMonth(),
    firstDayOfWeek: DayOfWeek = PlatformDateFormat.firstDayOfWeek(),
    outDateStyle: OutDateStyle = OutDateStyle.EndOfRow,
    visibleDaysOfWeek: Set<DayOfWeek> = ALL_DAYS_OF_WEEK_SET,
): CalendarState =
    rememberSaveable(saver = CalendarState.Saver) {
        CalendarState(
            startMonth = startMonth,
            endMonth = endMonth,
            firstVisibleMonth = firstVisibleMonth,
            firstDayOfWeek = firstDayOfWeek,
            outDateStyle = outDateStyle,
            visibleDaysOfWeek = visibleDaysOfWeek,
        )
    }
