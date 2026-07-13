package dev.koza4e4ok.orrery.compose

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

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
