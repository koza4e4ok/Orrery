package me.kozakov.orrery.compose

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import me.kozakov.orrery.core.CalendarPages
import me.kozakov.orrery.core.CalendarWeek
import me.kozakov.orrery.core.multiWeekGrid
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
    visibleWeeksCount: Int = 1,
    visibleDaysOfWeek: Set<DayOfWeek> = ALL_DAYS_OF_WEEK_SET,
) {
    init {
        require(visibleWeeksCount in 1..4) {
            "visibleWeeksCount ($visibleWeeksCount) must be in 1..4"
        }
        require(visibleDaysOfWeek.isNotEmpty()) {
            "visibleDaysOfWeek must not be empty"
        }
    }

    /** Inclusive range start; null scrolls unboundedly into the past. */
    public var startDate: LocalDate? by mutableStateOf(startDate)
        private set

    /** Inclusive range end; null scrolls unboundedly into the future. */
    public var endDate: LocalDate? by mutableStateOf(endDate)
        private set

    public var firstDayOfWeek: DayOfWeek by mutableStateOf(firstDayOfWeek)

    private var visibleWeeksCountState: Int by mutableIntStateOf(visibleWeeksCount)

    /** Number of week rows shown per page (`1..4`, e.g. `2` for two-week mode). */
    public var visibleWeeksCount: Int
        get() = visibleWeeksCountState
        set(value) {
            require(value in 1..4) { "visibleWeeksCount ($value) must be in 1..4" }
            visibleWeeksCountState = value
        }

    private var visibleDaysOfWeekState: Set<DayOfWeek> by mutableStateOf(visibleDaysOfWeek)

    /** Subset of weekdays rendered in each week row (must not be empty). */
    public var visibleDaysOfWeek: Set<DayOfWeek>
        get() = visibleDaysOfWeekState
        set(value) {
            require(value.isNotEmpty()) { "visibleDaysOfWeek must not be empty" }
            visibleDaysOfWeekState = value
        }

    private val effectiveStartDate: LocalDate
        get() = startDate ?: UNBOUNDED_START_DATE

    private val effectiveEndDate: LocalDate
        get() = endDate ?: UNBOUNDED_END_DATE

    internal val listState: LazyListState =
        LazyListState(
            firstVisibleItemIndex = indexOf(firstVisibleDate),
        )

    /** Pages of [visibleWeeksCount] weeks in the effective range. */
    public val weekCount: Int
        get() = pageCount(visibleWeeksCount)

    internal fun pageCount(weeksPerPage: Int = visibleWeeksCount): Int =
        CalendarPages.weekCount(
            effectiveStartDate,
            effectiveEndDate,
            firstDayOfWeek,
            weeksPerPage,
        )

    /** The first week row of the first visible page. */
    public val firstVisibleWeek: CalendarWeek
        get() = weekAt(listState.firstVisibleItemIndex)

    /** All [visibleWeeksCount] week rows of the first visible page. */
    public val firstVisibleWeeks: List<CalendarWeek>
        get() = weeksAt(listState.firstVisibleItemIndex)

    internal fun weekAt(
        index: Int,
        daysFilter: Set<DayOfWeek> = visibleDaysOfWeek,
    ): CalendarWeek = weeksAt(index, visibleWeeksCount, daysFilter).first()

    internal fun weeksAt(
        index: Int,
        weeksPerPage: Int = visibleWeeksCount,
        daysFilter: Set<DayOfWeek> = visibleDaysOfWeek,
    ): List<CalendarWeek> =
        multiWeekGrid(
            anchor = CalendarPages.weekAt(effectiveStartDate, index, firstDayOfWeek, weeksPerPage),
            weeksCount = weeksPerPage,
            firstDayOfWeek = firstDayOfWeek,
            visibleDaysOfWeek = daysFilter,
        )

    private var programmaticScrolls: Int by mutableIntStateOf(0)

    /** Week index the last programmatic scroll (or the initial state) left visible. */
    private var anchorIndex: Int by mutableIntStateOf(listState.firstVisibleItemIndex)
    private var anchorSource: ScrollSource by mutableStateOf(ScrollSource.Initial)

    /**
     * What brought [firstVisibleWeek] into view: [ScrollSource.Initial] until
     * anything scrolls, [ScrollSource.Programmatic] during and after
     * [scrollToDate]/[animateScrollToDate], [ScrollSource.UserDrag] while the
     * user drags or flings the strip and after such a gesture settles on another
     * week. Snapshot-observable.
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

    public suspend fun scrollToDate(date: LocalDate) {
        val target = indexOf(date)
        programmaticScroll(target) { listState.scrollToItem(target) }
    }

    public suspend fun animateScrollToDate(date: LocalDate) {
        val target = indexOf(date)
        programmaticScroll(target) {
            val current = listState.firstVisibleItemIndex
            val farJumpPages = (FAR_JUMP_WEEKS / visibleWeeksCount).coerceAtLeast(1)
            if (abs(target - current) > farJumpPages) {
                val approach = if (target > current) target - 2 else target + 2
                listState.scrollToItem(approach.coerceIn(0, weekCount - 1))
            }
            listState.animateScrollToItem(target)
        }
    }

    private fun indexOf(date: LocalDate): Int =
        CalendarPages.weekIndex(effectiveStartDate, date, firstDayOfWeek, visibleWeeksCount).coerceIn(
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
                        it.visibleWeeksCount,
                        it.visibleDaysOfWeek.toBitMask(),
                    )
                },
                restore = {
                    WeekCalendarState(
                        startDate = (it[0] as String).takeIf(String::isNotEmpty)?.let(LocalDate::parse),
                        endDate = (it[1] as String).takeIf(String::isNotEmpty)?.let(LocalDate::parse),
                        firstVisibleDate = LocalDate.parse(it[2] as String),
                        firstDayOfWeek = DayOfWeek.entries[it[3] as Int],
                        visibleWeeksCount = (it.getOrNull(4) as? Int) ?: 1,
                        visibleDaysOfWeek =
                            (it.getOrNull(5) as? Int)?.let(::dayOfWeekSetFromBitMask)
                                ?: ALL_DAYS_OF_WEEK_SET,
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
    firstDayOfWeek: DayOfWeek = PlatformDateFormat.firstDayOfWeek(),
): WeekCalendarState =
    rememberSaveable(saver = WeekCalendarState.Saver) {
        WeekCalendarState(startDate, endDate, firstVisibleDate, firstDayOfWeek)
    }

/**
 * Remembers saveable [WeekCalendarState] configured with [visibleWeeksCount]
 * (`1..4`, e.g. `2` for a biweekly / two-week calendar view) and
 * [visibleDaysOfWeek] (e.g. Monday–Friday work week).
 */
@Composable
public fun rememberWeekCalendarState(
    startDate: LocalDate? = null,
    endDate: LocalDate? = null,
    firstVisibleDate: LocalDate = currentDate(),
    firstDayOfWeek: DayOfWeek = PlatformDateFormat.firstDayOfWeek(),
    visibleWeeksCount: Int = 1,
    visibleDaysOfWeek: Set<DayOfWeek> = ALL_DAYS_OF_WEEK_SET,
): WeekCalendarState =
    rememberSaveable(saver = WeekCalendarState.Saver) {
        WeekCalendarState(
            startDate = startDate,
            endDate = endDate,
            firstVisibleDate = firstVisibleDate,
            firstDayOfWeek = firstDayOfWeek,
            visibleWeeksCount = visibleWeeksCount,
            visibleDaysOfWeek = visibleDaysOfWeek,
        )
    }
