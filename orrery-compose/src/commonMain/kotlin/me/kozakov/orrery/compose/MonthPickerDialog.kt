package me.kozakov.orrery.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.number

/**
 * State for [OrreryMonthPickerDialog]: the selected month within [range].
 * [selectedMonth] is null until the user (or [select]) picks one; months
 * outside [range] are ignored.
 */
@Stable
public class OrreryMonthPickerState internal constructor(
    initialSelectedMonth: YearMonth?,
    public val range: ClosedRange<YearMonth>,
) {
    /** The picked month, or null until one is picked. Snapshot-observable. */
    public var selectedMonth: YearMonth? by mutableStateOf(initialSelectedMonth?.takeIf { it in range })
        private set

    /** Selects [month], or clears the selection when null. Months outside [range] are ignored. */
    public fun select(month: YearMonth?) {
        if (month == null || month in range) selectedMonth = month
    }

    internal companion object {
        /** Months since year 0, so the saved value is a single Int. */
        internal fun encode(month: YearMonth): Int = month.year * MONTHS_PER_YEAR + month.month.number - 1

        internal fun decode(value: Int): YearMonth =
            YearMonth(value.floorDiv(MONTHS_PER_YEAR), value.mod(MONTHS_PER_YEAR) + 1)

        // listSaver drops an empty list, so a cleared selection is saved as NO_MONTH.
        internal fun saver(range: ClosedRange<YearMonth>): Saver<OrreryMonthPickerState, Any> =
            listSaver(
                save = { state -> listOf(state.selectedMonth?.let(::encode) ?: NO_MONTH) },
                restore = { saved ->
                    OrreryMonthPickerState(saved[0].takeIf { it != NO_MONTH }?.let(::decode), range)
                },
            )

        private const val MONTHS_PER_YEAR = 12
        private const val NO_MONTH = Int.MIN_VALUE
    }
}

/**
 * Remembers saveable [OrreryMonthPickerState] for [OrreryMonthPickerDialog].
 * For a card-expiry field pass `range = thisMonth..thisMonth.plus(20, DateTimeUnit.YEAR)`.
 */
@Composable
public fun rememberOrreryMonthPickerState(
    initialSelectedMonth: YearMonth? = null,
    range: ClosedRange<YearMonth> = DatePickerDefaults.monthRange(),
): OrreryMonthPickerState {
    val saver = remember(range) { OrreryMonthPickerState.saver(range) }
    return rememberSaveable(range, saver = saver) { OrreryMonthPickerState(initialSelectedMonth, range) }
}

/**
 * Material-3-shaped dialog for picking a month and year without a day, for
 * example a card expiry date. It opens on the month grid of the selected
 * month's year (or [today]'s), clamped to the state's range; tapping the year
 * zooms out to the year grid, then to decades. Tapping a month selects it and
 * keeps the dialog open; read state.selectedMonth in your confirm button.
 *
 * ```
 * val thisMonth = currentYearMonth()
 * val state = rememberOrreryMonthPickerState(range = thisMonth..thisMonth.plus(20, DateTimeUnit.YEAR))
 * ```
 */
@Composable
public fun OrreryMonthPickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    state: OrreryMonthPickerState = rememberOrreryMonthPickerState(),
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    today: LocalDate = currentDate(),
    strings: MonthPickerStrings = DatePickerDefaults.monthPickerStrings(),
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = modifier.requiredWidth(360.dp),
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    strings.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    state.selectedMonth?.let(PlatformDateFormat::yearMonthTitle) ?: strings.headlinePlaceholder,
                    style = MaterialTheme.typography.headlineLarge,
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                val todayMonth = YearMonth(today.year, today.month)
                // Scrolls on short windows so the button row stays inside the dialog.
                Box(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    MonthYearPickerContent(
                        current = todayMonth,
                        range = state.range,
                        onSelect = state::select,
                        selected = state.selectedMonth,
                        startInMonths = true,
                        selectable = true,
                        modifier = Modifier.height(320.dp),
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}
