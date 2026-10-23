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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import me.kozakov.orrery.core.CalendarPages
import me.kozakov.orrery.core.CalendarWeek
import me.kozakov.orrery.core.weekGrid
import kotlin.math.abs

/** Far animated jumps teleport near the target first (weeks). */
internal const val FAR_JUMP_WEEKS: Int = 52

private val UNBOUNDED_START_DATE = LocalDate(-19999, 1, 1)
private val UNBOUNDED_END_DATE = LocalDate(19999, 12, 31)

/** State holder for [WeekCalendar]. */
@Stable
public class WeekCalendarState internal constructor(
    startDate: LocalDate?,
    endDate: LocalDate?,
    firstVisibleDate: LocalDate,
    firstDayOfWeek: DayOfWeek,
) {
    /** Inclusive range start; null scrolls unboundedly into the past. */
    public var startDate: LocalDate? by mutableStateOf(startDate)
        private set

    /** Inclusive range end; null scrolls unboundedly into the future. */
    public var endDate: LocalDate? by mutableStateOf(endDate)
        private set

    public var firstDayOfWeek: DayOfWeek by mutableStateOf(firstDayOfWeek)

    private val effectiveStartDate: LocalDate
        get() = startDate ?: UNBOUNDED_START_DATE

    private val effectiveEndDate: LocalDate
        get() = endDate ?: UNBOUNDED_END_DATE

    internal val listState: LazyListState =
        LazyListState(
            firstVisibleItemIndex = indexOf(firstVisibleDate),
        )

    /** Weeks in the effective range; unbounded sides count to the supported date extremes. */
    public val weekCount: Int
        get() = CalendarPages.weekCount(effectiveStartDate, effectiveEndDate, firstDayOfWeek)

    public val firstVisibleWeek: CalendarWeek
        get() = weekAt(listState.firstVisibleItemIndex)

    internal fun weekAt(index: Int): CalendarWeek =
        weekGrid(
            CalendarPages.weekStart(effectiveStartDate, firstDayOfWeek).plus(index * 7, DateTimeUnit.DAY),
            firstDayOfWeek,
        )

    public suspend fun scrollToDate(date: LocalDate) {
        listState.scrollToItem(indexOf(date))
    }

    public suspend fun animateScrollToDate(date: LocalDate) {
        val target = indexOf(date)
        val current = listState.firstVisibleItemIndex
        if (abs(target - current) > FAR_JUMP_WEEKS) {
            val approach = if (target > current) target - 2 else target + 2
            listState.scrollToItem(approach.coerceIn(0, weekCount - 1))
        }
        listState.animateScrollToItem(target)
    }

    private fun indexOf(date: LocalDate): Int =
        CalendarPages.weekIndex(effectiveStartDate, date, firstDayOfWeek).coerceIn(
            0,
            weekCount - 1,
        )

    public companion object {
        public val Saver: Saver<WeekCalendarState, Any> =
            listSaver(
                save = {
                    listOf(
                        it.startDate?.toString() ?: "",
                        it.endDate?.toString() ?: "",
                        it.firstVisibleWeek.days
                            .first()
                            .date
                            .toString(),
                        it.firstDayOfWeek.ordinal,
                    )
                },
                restore = {
                    WeekCalendarState(
                        startDate = (it[0] as String).takeIf(String::isNotEmpty)?.let(LocalDate::parse),
                        endDate = (it[1] as String).takeIf(String::isNotEmpty)?.let(LocalDate::parse),
                        firstVisibleDate = LocalDate.parse(it[2] as String),
                        firstDayOfWeek = DayOfWeek.entries[it[3] as Int],
                    )
                },
            )
    }
}

/**
 * Remembers saveable [WeekCalendarState]. Null [startDate]/[endDate] (the
 * defaults) leave that side unbounded — the week strip scrolls indefinitely.
 */
@Composable
public fun rememberWeekCalendarState(
    startDate: LocalDate? = null,
    endDate: LocalDate? = null,
    firstVisibleDate: LocalDate = currentDate(),
    firstDayOfWeek: DayOfWeek = firstDayOfWeekFromLocale(),
): WeekCalendarState =
    rememberSaveable(saver = WeekCalendarState.Saver) {
        WeekCalendarState(startDate, endDate, firstVisibleDate, firstDayOfWeek)
    }
