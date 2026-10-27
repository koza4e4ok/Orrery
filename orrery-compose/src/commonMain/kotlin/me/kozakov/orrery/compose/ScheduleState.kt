package me.kozakov.orrery.compose

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import me.kozakov.orrery.core.CalendarPages
import kotlin.math.abs
import kotlin.math.roundToInt

private val UNBOUNDED_SCHEDULE_START = LocalDate(-19999, 1, 1)
private val UNBOUNDED_SCHEDULE_END = LocalDate(19999, 12, 31)

/**
 * State holder for [DaySchedule] and [WeekSchedule]: the horizontally paged
 * date window, the shared vertical scroll of the 24-hour grid, and the
 * zoomable [hourHeight].
 */
@Stable
public class ScheduleState internal constructor(
    startDate: LocalDate?,
    endDate: LocalDate?,
    firstVisibleDate: LocalDate,
    firstDayOfWeek: DayOfWeek,
    initialHourHeight: Dp,
    public val minHourHeight: Dp,
    public val maxHourHeight: Dp,
    initialScrollOffset: Int,
) {
    init {
        require(minHourHeight <= maxHourHeight) {
            "minHourHeight ($minHourHeight) must not exceed maxHourHeight ($maxHourHeight)"
        }
        if (startDate != null && endDate != null) {
            require(endDate >= startDate) { "endDate ($endDate) must not precede startDate ($startDate)" }
        }
    }

    /** Inclusive range start; null pages unboundedly into the past. */
    public var startDate: LocalDate? by mutableStateOf(startDate)
        private set

    /** Inclusive range end; null pages unboundedly into the future. */
    public var endDate: LocalDate? by mutableStateOf(endDate)
        private set

    public var firstDayOfWeek: DayOfWeek by mutableStateOf(firstDayOfWeek)

    private var hourHeightState: Dp by mutableStateOf(initialHourHeight.coerceIn(minHourHeight, maxHourHeight))

    /**
     * Height of one 60-minute slot; mutable for pinch-to-zoom or programmatic zoom.
     * Values are clamped to [minHourHeight]..[maxHourHeight].
     */
    public var hourHeight: Dp
        get() = hourHeightState
        set(value) {
            hourHeightState = value.coerceIn(minHourHeight, maxHourHeight)
        }

    /** Vertical scroll state of the 24-hour time grid (shared across columns and pages). */
    public val verticalScrollState: ScrollState = ScrollState(initialScrollOffset)

    internal val effectiveStartDate: LocalDate
        get() = startDate ?: UNBOUNDED_SCHEDULE_START

    internal val effectiveEndDate: LocalDate
        get() = endDate ?: UNBOUNDED_SCHEDULE_END

    /**
     * First visible date: the shown day in [DaySchedule]; in [WeekSchedule] the
     * page's first day after a swipe, or the date passed to [scrollToDate].
     */
    public var firstVisibleDate: LocalDate by mutableStateOf(
        firstVisibleDate.coerceIn(effectiveStartDate, effectiveEndDate),
    )
        private set

    private var pagerState: PagerState? = null
    private var paging: SchedulePaging? = null

    public suspend fun scrollToDate(date: LocalDate) {
        val target = date.coerceIn(effectiveStartDate, effectiveEndDate)
        firstVisibleDate = target
        val pager = pagerState ?: return
        val pages = paging ?: return
        pager.scrollToPage(pages.pageOf(target))
    }

    public suspend fun animateScrollToDate(date: LocalDate) {
        val target = date.coerceIn(effectiveStartDate, effectiveEndDate)
        val pager = pagerState
        val pages = paging
        if (pager == null || pages == null) {
            firstVisibleDate = target
            return
        }
        val targetPage = pages.pageOf(target)
        val farJumpPages = (FAR_JUMP_WEEKS * 7 / pages.daysPerPage).coerceAtLeast(1)
        if (abs(targetPage - pager.currentPage) > farJumpPages) {
            val approach = if (targetPage > pager.currentPage) targetPage - 2 else targetPage + 2
            pager.scrollToPage(approach.coerceIn(0, pages.pageCount - 1))
        }
        pager.animateScrollToPage(targetPage)
        firstVisibleDate = target
    }

    /** Scrolls the vertical time grid so [time] is at the top of the viewport. */
    public suspend fun scrollToTime(
        time: LocalTime,
        density: Density,
    ) {
        verticalScrollState.scrollTo(offsetOf(time, density))
    }

    /** Animates the vertical time grid so [time] is at the top of the viewport. */
    public suspend fun animateScrollToTime(
        time: LocalTime,
        density: Density,
    ) {
        verticalScrollState.animateScrollTo(offsetOf(time, density))
    }

    private fun offsetOf(
        time: LocalTime,
        density: Density,
    ): Int = with(density) { (hourHeight.toPx() * (time.hour * 60 + time.minute) / 60f).roundToInt() }

    internal fun attach(
        pagerState: PagerState,
        paging: SchedulePaging,
    ) {
        this.pagerState = pagerState
        this.paging = paging
    }

    internal fun detach(pagerState: PagerState) {
        if (this.pagerState === pagerState) {
            this.pagerState = null
            this.paging = null
        }
    }

    internal fun onPageSettled(
        paging: SchedulePaging,
        page: Int,
    ) {
        if (paging.pageOf(firstVisibleDate) != page) {
            firstVisibleDate = paging.dateAt(page)
        }
    }

    /**
     * Scales [hourHeight] by [zoom] and adjusts the vertical scroll so the time
     * under [focalY] (viewport pixels) stays in place.
     */
    internal fun zoomBy(
        zoom: Float,
        focalY: Float,
        density: Density,
    ) {
        val oldPx = with(density) { hourHeight.toPx() }
        hourHeight = hourHeight * zoom
        val newPx = with(density) { hourHeight.toPx() }
        if (oldPx <= 0f || newPx == oldPx) return
        val anchor = verticalScrollState.value + focalY
        val target = anchor * newPx / oldPx - focalY
        verticalScrollState.dispatchRawDelta(target - verticalScrollState.value)
    }

    public companion object {
        public val Saver: Saver<ScheduleState, Any> =
            listSaver(
                save = {
                    listOf(
                        it.startDate?.toString() ?: "",
                        it.endDate?.toString() ?: "",
                        it.firstVisibleDate.toString(),
                        it.firstDayOfWeek.ordinal,
                        it.hourHeight.value,
                        it.minHourHeight.value,
                        it.maxHourHeight.value,
                        it.verticalScrollState.value,
                    )
                },
                restore = {
                    ScheduleState(
                        startDate = (it[0] as String).takeIf(String::isNotEmpty)?.let(LocalDate::parse),
                        endDate = (it[1] as String).takeIf(String::isNotEmpty)?.let(LocalDate::parse),
                        firstVisibleDate = LocalDate.parse(it[2] as String),
                        firstDayOfWeek = DayOfWeek.entries[it[3] as Int],
                        initialHourHeight = (it[4] as Float).dp,
                        minHourHeight = (it[5] as Float).dp,
                        maxHourHeight = (it[6] as Float).dp,
                        initialScrollOffset = it[7] as Int,
                    )
                },
            )
    }
}

/**
 * Remembers saveable [ScheduleState]. Null [startDate]/[endDate] (the defaults)
 * leave that side unbounded. The time grid initially shows [initialScrollTime]
 * at the top of the viewport.
 */
@Composable
public fun rememberScheduleState(
    startDate: LocalDate? = null,
    endDate: LocalDate? = null,
    firstVisibleDate: LocalDate = currentDate(),
    firstDayOfWeek: DayOfWeek = PlatformDateFormat.firstDayOfWeek(),
    initialHourHeight: Dp = 60.dp,
    minHourHeight: Dp = 36.dp,
    maxHourHeight: Dp = 160.dp,
    initialScrollTime: LocalTime = LocalTime(8, 0),
): ScheduleState {
    val density = LocalDensity.current
    return rememberSaveable(saver = ScheduleState.Saver) {
        val hourHeightPx = with(density) { initialHourHeight.coerceIn(minHourHeight, maxHourHeight).toPx() }
        val minutes = initialScrollTime.hour * 60 + initialScrollTime.minute
        ScheduleState(
            startDate = startDate,
            endDate = endDate,
            firstVisibleDate = firstVisibleDate,
            firstDayOfWeek = firstDayOfWeek,
            initialHourHeight = initialHourHeight,
            minHourHeight = minHourHeight,
            maxHourHeight = maxHourHeight,
            initialScrollOffset = (hourHeightPx * minutes / 60f).roundToInt(),
        )
    }
}

/**
 * Maps pager pages to blocks of [daysPerPage] dates counted from [anchor].
 * Seven-day pages are anchored to a week start so they always show whole weeks.
 */
internal class SchedulePaging private constructor(
    val anchor: LocalDate,
    val daysPerPage: Int,
    val pageCount: Int,
) {
    fun pageOf(date: LocalDate): Int = (anchor.daysUntil(date) / daysPerPage).coerceIn(0, pageCount - 1)

    fun dateAt(page: Int): LocalDate = anchor.plus(page * daysPerPage, DateTimeUnit.DAY)

    fun datesAt(page: Int): List<LocalDate> {
        val first = dateAt(page)
        return List(daysPerPage) { first.plus(it, DateTimeUnit.DAY) }
    }

    companion object {
        fun create(
            startDate: LocalDate,
            endDate: LocalDate,
            firstDayOfWeek: DayOfWeek,
            daysPerPage: Int,
        ): SchedulePaging {
            require(daysPerPage >= 1) { "daysPerPage ($daysPerPage) must be at least 1" }
            val anchor = if (daysPerPage == 7) CalendarPages.weekStart(startDate, firstDayOfWeek) else startDate
            val totalDays = anchor.daysUntil(endDate) + 1
            return SchedulePaging(anchor, daysPerPage, (totalDays + daysPerPage - 1) / daysPerPage)
        }
    }
}
