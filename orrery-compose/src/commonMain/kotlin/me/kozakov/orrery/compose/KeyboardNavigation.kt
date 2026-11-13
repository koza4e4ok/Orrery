package me.kozakov.orrery.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import me.kozakov.orrery.core.CalendarDay
import me.kozakov.orrery.core.DayPosition

/**
 * Shared state for arrow-key/D-pad edge paging: [page] scrolls to the
 * target date's page, then [pendingFocus] tells that date's cell to
 * grab focus once composed.
 *
 * With [dualMonth], every arrow key resolves its target date and goes
 * through [page] instead of native focus search, which cannot be trusted
 * across the two side-by-side month panes (blank cells, RTL order). When
 * [isOnScreen] says the target needs no paging and its cell is composed,
 * [page] focuses it at once, so a held arrow key always repeats from the
 * cell it just moved to.
 */
@Stable
internal class CalendarKeyboardNavigation(
    private val scope: CoroutineScope,
    val dualMonth: Boolean = false,
    private val isOnScreen: (LocalDate) -> Boolean = { false },
    private val pageToDate: suspend (LocalDate) -> Unit,
) {
    val pendingFocus: MutableState<LocalDate?> = mutableStateOf(null)

    /** Focus requesters of the composed in-month cells, by date. Plain state: never read in composition. */
    private val cells = mutableMapOf<LocalDate, FocusRequester>()

    /** Bumped by every [page], so a slower earlier page never steals focus back. */
    private var generation = 0

    fun register(
        date: LocalDate,
        requester: FocusRequester,
    ) {
        cells[date] = requester
    }

    fun unregister(
        date: LocalDate,
        requester: FocusRequester,
    ) {
        if (cells[date] === requester) cells.remove(date)
    }

    fun page(target: LocalDate) {
        val current = ++generation
        val cell = cells[target]
        if (cell != null && isOnScreen(target) && runCatching { cell.requestFocus() }.isSuccess) {
            pendingFocus.value = null
            return
        }
        scope.launch {
            pageToDate(target)
            if (current == generation) pendingFocus.value = target
        }
    }
}

/**
 * Day offset of a horizontal arrow [key] under [layoutDirection], or null
 * for any other key. In LTR, DirectionLeft is −1 day and DirectionRight +1;
 * RTL grids run right to left, so the keys swap (DirectionLeft moves to the
 * next day, visually leftward).
 */
internal fun horizontalArrowDayOffset(
    key: Key,
    layoutDirection: LayoutDirection,
): Int? {
    val rtl = layoutDirection == LayoutDirection.Rtl
    return when (key) {
        Key.DirectionLeft -> if (rtl) 1 else -1
        Key.DirectionRight -> if (rtl) -1 else 1
        else -> null
    }
}

/**
 * Initial focus for picker dialogs: returns a per-day modifier that marks
 * the in-month cell of [date] and requests focus on it once, when the
 * picker enters the composition (dialog opened in, or toggled back to,
 * calendar mode), so the first arrow key starts from the active date.
 * Day cells are focusable only in keyboard input mode, so touch users see
 * no change; nothing happens when [date] is not on a visible page.
 */
@Composable
internal fun rememberInitialDayFocus(date: LocalDate): (CalendarDay) -> Modifier {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // Lazy pages compose during layout: wait one frame so the cell is attached.
        withFrameNanos { }
        // Throws when no cell of [date] is attached (another month is visible).
        runCatching { requester.requestFocus() }
    }
    return remember(date, requester) {
        { day: CalendarDay ->
            if (day.date == date && day.position == DayPosition.MonthDate) {
                Modifier.focusRequester(requester)
            } else {
                Modifier
            }
        }
    }
}
