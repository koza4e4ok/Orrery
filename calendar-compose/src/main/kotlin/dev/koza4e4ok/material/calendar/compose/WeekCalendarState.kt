package dev.koza4e4ok.material.calendar.compose

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.koza4e4ok.material.calendar.core.CalendarPages
import dev.koza4e4ok.material.calendar.core.CalendarWeek
import dev.koza4e4ok.material.calendar.core.weekGrid
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** State holder for [WeekCalendar]. */
@Stable
public class WeekCalendarState internal constructor(
    startDate: LocalDate,
    endDate: LocalDate,
    firstVisibleDate: LocalDate,
    firstDayOfWeek: DayOfWeek,
) {
    public var startDate: LocalDate by mutableStateOf(startDate)
        private set
    public var endDate: LocalDate by mutableStateOf(endDate)
        private set
    public var firstDayOfWeek: DayOfWeek by mutableStateOf(firstDayOfWeek)

    internal val listState: LazyListState =
        LazyListState(
            firstVisibleItemIndex = indexOf(firstVisibleDate),
        )

    public val weekCount: Int
        get() = CalendarPages.weekCount(startDate, endDate, firstDayOfWeek)

    public val firstVisibleWeek: CalendarWeek
        get() = weekAt(listState.firstVisibleItemIndex)

    internal fun weekAt(index: Int): CalendarWeek =
        weekGrid(
            CalendarPages.weekStart(startDate, firstDayOfWeek).plus(index * 7, DateTimeUnit.DAY),
            firstDayOfWeek,
        )

    public suspend fun scrollToDate(date: LocalDate) {
        listState.scrollToItem(indexOf(date))
    }

    public suspend fun animateScrollToDate(date: LocalDate) {
        listState.animateScrollToItem(indexOf(date))
    }

    private fun indexOf(date: LocalDate): Int =
        CalendarPages.weekIndex(startDate, date, firstDayOfWeek).coerceIn(
            0,
            weekCount - 1,
        )

    public companion object {
        public val Saver: Saver<WeekCalendarState, Any> =
            listSaver(
                save = {
                    listOf(
                        it.startDate.toString(),
                        it.endDate.toString(),
                        it.firstVisibleWeek.days
                            .first()
                            .date
                            .toString(),
                        it.firstDayOfWeek.ordinal,
                    )
                },
                restore = {
                    WeekCalendarState(
                        startDate = LocalDate.parse(it[0] as String),
                        endDate = LocalDate.parse(it[1] as String),
                        firstVisibleDate = LocalDate.parse(it[2] as String),
                        firstDayOfWeek = DayOfWeek.entries[it[3] as Int],
                    )
                },
            )
    }
}

@Composable
public fun rememberWeekCalendarState(
    startDate: LocalDate,
    endDate: LocalDate,
    firstVisibleDate: LocalDate = currentDate(),
    firstDayOfWeek: DayOfWeek = firstDayOfWeekFromLocale(),
): WeekCalendarState =
    rememberSaveable(saver = WeekCalendarState.Saver) {
        WeekCalendarState(startDate, endDate, firstVisibleDate, firstDayOfWeek)
    }
