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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import me.kozakov.orrery.core.CalendarDay
import me.kozakov.orrery.core.DayPosition

/**
 * Shared state for arrow-key/D-pad edge paging: [page] scrolls to the
 * target date's page, then [pendingFocus] tells that date's cell to
 * grab focus once composed.
 */
@Stable
internal class CalendarKeyboardNavigation(
    private val scope: CoroutineScope,
    private val pageToDate: suspend (LocalDate) -> Unit,
) {
    val pendingFocus: MutableState<LocalDate?> = mutableStateOf(null)

    fun page(target: LocalDate) {
        scope.launch {
            pageToDate(target)
            pendingFocus.value = target
        }
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
