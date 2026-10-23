package me.kozakov.orrery.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import me.kozakov.orrery.core.CalendarEvent
import kotlin.math.abs

/** Far animated jumps teleport near the target first (items). */
internal const val FAR_JUMP_AGENDA_ITEMS: Int = 40

/** State holder for [Agenda]. */
@Stable
public class AgendaState internal constructor(
    public val startDate: LocalDate,
    public val endDate: LocalDate,
    initialVisibleDate: LocalDate,
) {
    init {
        require(endDate >= startDate) {
            "endDate ($endDate) must not precede startDate ($startDate)"
        }
    }

    private val initialDate: LocalDate = initialVisibleDate.coerceIn(startDate, endDate)

    internal val listState: LazyListState = LazyListState()

    private var dateStartIndices: List<Pair<LocalDate, Int>> = emptyList()
    private var itemDates: List<LocalDate> = emptyList()
    private var layoutInitialized: Boolean = false
    private var isProgrammaticScroll: Boolean = false
    private var programmaticTarget: Pair<Int, LocalDate>? by mutableStateOf(0 to initialDate)

    /** First date currently visible at the top of the agenda viewport (snapshot-observable). */
    public val firstVisibleDate: LocalDate
        get() {
            val dates = itemDates
            val target = programmaticTarget
            if (!layoutInitialized || dates.isEmpty()) {
                return target?.second ?: initialDate
            }
            val currentIndex = listState.firstVisibleItemIndex.coerceIn(0, dates.lastIndex)
            if (target != null && currentIndex == target.first) {
                return target.second
            }
            return dates[currentIndex]
        }

    public suspend fun scrollToDate(date: LocalDate) {
        val targetDate = resolveDateForScroll(date)
        val targetIndex = resolveIndexForDate(targetDate)
        programmaticTarget = targetIndex to targetDate
        if (!layoutInitialized || itemDates.isEmpty()) return
        isProgrammaticScroll = true
        try {
            listState.scrollToItem(targetIndex)
        } finally {
            isProgrammaticScroll = false
        }
        programmaticTarget = listState.firstVisibleItemIndex to targetDate
    }

    public suspend fun animateScrollToDate(date: LocalDate) {
        val targetDate = resolveDateForScroll(date)
        val targetIndex = resolveIndexForDate(targetDate)
        programmaticTarget = targetIndex to targetDate
        if (!layoutInitialized || itemDates.isEmpty()) return
        val current = listState.firstVisibleItemIndex
        isProgrammaticScroll = true
        try {
            if (abs(targetIndex - current) > FAR_JUMP_AGENDA_ITEMS) {
                val approach = if (targetIndex > current) targetIndex - 10 else targetIndex + 10
                listState.scrollToItem(approach.coerceIn(0, itemDates.lastIndex))
            }
            listState.animateScrollToItem(targetIndex)
        } finally {
            isProgrammaticScroll = false
        }
        programmaticTarget = listState.firstVisibleItemIndex to targetDate
    }

    internal fun updateLayout(
        dateStartIndices: List<Pair<LocalDate, Int>>,
        itemDates: List<LocalDate>,
    ) {
        this.dateStartIndices = dateStartIndices
        this.itemDates = itemDates
        if (!layoutInitialized && dateStartIndices.isNotEmpty()) {
            layoutInitialized = true
            val desired = programmaticTarget?.second ?: initialDate
            val resolvedDate = resolveDateForScroll(desired)
            val targetIndex = resolveIndexForDate(resolvedDate)
            if (targetIndex != 0) {
                listState.requestScrollToItem(targetIndex)
            }
            programmaticTarget = targetIndex to resolvedDate
        }
    }

    internal fun onMeasured() {
        val target = programmaticTarget ?: return
        val currentIndex = listState.firstVisibleItemIndex
        if (currentIndex != target.first) {
            programmaticTarget = currentIndex to target.second
        }
    }

    internal fun onUserScrollStarted() {
        if (!isProgrammaticScroll) {
            programmaticTarget = null
        }
    }

    private fun resolveDateForScroll(date: LocalDate): LocalDate {
        val clamped = date.coerceIn(startDate, endDate)
        if (dateStartIndices.isEmpty()) return clamped
        val match = dateStartIndices.firstOrNull { (d, _) -> d >= clamped } ?: dateStartIndices.last()
        return match.first
    }

    private fun resolveIndexForDate(date: LocalDate): Int {
        val clamped = date.coerceIn(startDate, endDate)
        if (dateStartIndices.isEmpty()) return 0
        val match = dateStartIndices.firstOrNull { (d, _) -> d >= clamped } ?: dateStartIndices.last()
        return match.second
    }

    public companion object {
        public val Saver: Saver<AgendaState, Any> =
            listSaver(
                save = {
                    listOf(
                        it.startDate.toString(),
                        it.endDate.toString(),
                        it.firstVisibleDate.toString(),
                    )
                },
                restore = {
                    AgendaState(
                        startDate = LocalDate.parse(it[0] as String),
                        endDate = LocalDate.parse(it[1] as String),
                        initialVisibleDate = LocalDate.parse(it[2] as String),
                    )
                },
            )
    }
}

/**
 * Remembers saveable [AgendaState]. Defaults to a 2-year window around
 * [currentDate], initially positioned at [currentDate].
 */
@Composable
public fun rememberAgendaState(
    startDate: LocalDate = currentDate().minus(365, DateTimeUnit.DAY),
    endDate: LocalDate = currentDate().plus(365, DateTimeUnit.DAY),
    initialVisibleDate: LocalDate = currentDate(),
): AgendaState =
    rememberSaveable(saver = AgendaState.Saver) {
        AgendaState(
            startDate = startDate,
            endDate = endDate,
            initialVisibleDate = initialVisibleDate,
        )
    }

private class AgendaDayEntry<T>(
    val date: LocalDate,
    val events: List<CalendarEvent<T>>,
)

/**
 * Vertically scrolling chronological agenda list of [CalendarEvent]s grouped
 * by date across [state]'s range.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
public fun <T> Agenda(
    events: (LocalDate) -> List<CalendarEvent<T>>,
    state: AgendaState = rememberAgendaState(),
    modifier: Modifier = Modifier,
    today: LocalDate = currentDate(),
    showEmptyDays: Boolean = false,
    stickyDayHeaders: Boolean = true,
    onEventClick: ((CalendarEvent<T>) -> Unit)? = null,
    strings: AgendaStrings = AgendaDefaults.strings(),
    dayHeader: @Composable (LocalDate, Boolean) -> Unit = { date, isToday ->
        AgendaDefaults.DayHeader(date = date, isToday = isToday, strings = strings)
    },
    eventContent: @Composable (CalendarEvent<T>) -> Unit = { event ->
        AgendaDefaults.EventRow(
            event = event,
            onClick = onEventClick?.let { cb -> { cb(event) } },
            strings = strings,
        )
    },
    emptyDayContent: @Composable (LocalDate) -> Unit = {
        AgendaDefaults.EmptyDay(strings = strings)
    },
    emptyRangeContent: @Composable () -> Unit = {
        AgendaDefaults.EmptyRange(strings = strings)
    },
) {
    val startEpoch = state.startDate.toEpochDays()
    val endEpoch = state.endDate.toEpochDays()
    val dayEntries =
        buildList {
            for (epoch in startEpoch..endEpoch) {
                val date = LocalDate.fromEpochDays(epoch)
                val dayEvents = events(date)
                if (showEmptyDays || dayEvents.isNotEmpty()) {
                    add(AgendaDayEntry(date, dayEvents))
                }
            }
        }
    val dateStartIndices = ArrayList<Pair<LocalDate, Int>>(dayEntries.size)
    val itemDates = ArrayList<LocalDate>(dayEntries.size * 2)
    for (entry in dayEntries) {
        dateStartIndices.add(entry.date to itemDates.size)
        val itemCount = 1 + maxOf(1, entry.events.size)
        repeat(itemCount) {
            itemDates.add(entry.date)
        }
    }
    state.updateLayout(dateStartIndices, itemDates)

    LaunchedEffect(state, dayEntries.size) {
        state.onMeasured()
    }
    val scrolling = state.listState.isScrollInProgress
    LaunchedEffect(scrolling) {
        if (scrolling) {
            state.onUserScrollStarted()
        }
    }

    if (dayEntries.isEmpty()) {
        Box(modifier) {
            emptyRangeContent()
        }
        return
    }

    LazyColumn(
        state = state.listState,
        modifier = modifier,
    ) {
        for (entry in dayEntries) {
            val date = entry.date
            val isToday = date == today
            if (stickyDayHeaders) {
                stickyHeader(key = "header-$date") {
                    Box(
                        Modifier
                            .fillParentMaxWidth()
                            .background(MaterialTheme.colorScheme.surface),
                    ) {
                        dayHeader(date, isToday)
                    }
                }
            } else {
                item(key = "header-$date") {
                    Box(Modifier.fillParentMaxWidth()) {
                        dayHeader(date, isToday)
                    }
                }
            }
            if (entry.events.isEmpty()) {
                item(key = "empty-$date") {
                    emptyDayContent(date)
                }
            } else {
                val dayEvents = entry.events
                items(
                    count = dayEvents.size,
                    key = { index -> "event-$date-$index-${dayEvents[index].id}" },
                ) { index ->
                    CompositionLocalProvider(LocalAgendaDate provides date) {
                        eventContent(dayEvents[index])
                    }
                }
            }
        }
    }
}
