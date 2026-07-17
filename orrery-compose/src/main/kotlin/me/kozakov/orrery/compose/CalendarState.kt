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

/** State holder for [HorizontalCalendar] and [VerticalCalendar]. */
@Stable
public class CalendarState internal constructor(
    startMonth: YearMonth,
    endMonth: YearMonth,
    firstVisibleMonth: YearMonth,
    firstDayOfWeek: DayOfWeek,
    outDateStyle: OutDateStyle,
) {
    public var startMonth: YearMonth by mutableStateOf(startMonth)
        private set
    public var endMonth: YearMonth by mutableStateOf(endMonth)
        private set
    public var firstDayOfWeek: DayOfWeek by mutableStateOf(firstDayOfWeek)
    public var outDateStyle: OutDateStyle by mutableStateOf(outDateStyle)

    /** List items per month: 2 when sticky headers add a header item per month. */
    internal var itemsPerMonth: Int = 1

    internal val listState: LazyListState =
        LazyListState(
            firstVisibleItemIndex = indexOf(firstVisibleMonth),
        )

    public val monthCount: Int
        get() = CalendarPages.monthCount(startMonth, endMonth)

    /** The month at the first visible page. Snapshot-observable. */
    public val firstVisibleMonth: YearMonth
        get() = CalendarPages.monthAt(startMonth, listState.firstVisibleItemIndex / itemsPerMonth)

    public suspend fun scrollToMonth(month: YearMonth) {
        listState.scrollToItem(indexOf(month))
    }

    public suspend fun animateScrollToMonth(month: YearMonth) {
        listState.animateScrollToItem(indexOf(month))
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

    /** Changes the month range, keeping the visible month if still in range. */
    public fun updateRange(
        startMonth: YearMonth,
        endMonth: YearMonth,
    ) {
        require(startMonth <= endMonth) { "startMonth must be <= endMonth" }
        val visible = firstVisibleMonth
        this.startMonth = startMonth
        this.endMonth = endMonth
        listState.requestScrollToItem(indexOf(visible))
    }

    private fun indexOf(month: YearMonth): Int =
        CalendarPages.monthIndex(startMonth, month).coerceIn(0, monthCount - 1) * itemsPerMonth

    public companion object {
        public val Saver: Saver<CalendarState, Any> =
            listSaver(
                save = {
                    listOf(
                        it.startMonth.toString(),
                        it.endMonth.toString(),
                        it.firstVisibleMonth.toString(),
                        it.firstDayOfWeek.ordinal,
                        it.outDateStyle.ordinal,
                    )
                },
                restore = {
                    CalendarState(
                        startMonth = YearMonth.parse(it[0] as String),
                        endMonth = YearMonth.parse(it[1] as String),
                        firstVisibleMonth = YearMonth.parse(it[2] as String),
                        firstDayOfWeek = DayOfWeek.entries[it[3] as Int],
                        outDateStyle = OutDateStyle.entries[it[4] as Int],
                    )
                },
            )
    }
}

@Composable
public fun rememberCalendarState(
    startMonth: YearMonth = YearMonth(1971, 1),
    endMonth: YearMonth = YearMonth(2055, 12),
    firstVisibleMonth: YearMonth = currentYearMonth(),
    firstDayOfWeek: DayOfWeek = firstDayOfWeekFromLocale(),
    outDateStyle: OutDateStyle = OutDateStyle.EndOfRow,
): CalendarState =
    rememberSaveable(saver = CalendarState.Saver) {
        CalendarState(startMonth, endMonth, firstVisibleMonth, firstDayOfWeek, outDateStyle)
    }
