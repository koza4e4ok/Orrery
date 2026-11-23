package me.kozakov.orrery.compose

import androidx.compose.runtime.Immutable
import kotlinx.datetime.LocalDate

/**
 * A quick-pick chip for [OrreryDateRangePickerDialog]: tapping it passes
 * [range] to [OrreryDateRangePickerState.setSelection], so it is ignored when
 * it breaks the picker's year range, `minDays`/`maxDays` or disabled dates,
 * or the range guardrails ([OrreryDateRangePickerState.minRangeDays],
 * [OrreryDateRangePickerState.maxRangeDays] and
 * [OrreryDateRangePickerState.disallowDisabledInRange]). Build the standard
 * set with [DatePickerDefaults.rangePresets].
 *
 * @property label Text of the chip.
 */
@Immutable
public class DateRangePreset(
    public val label: String,
    public val range: ClosedRange<LocalDate>,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is DateRangePreset && label == other.label && range == other.range)

    override fun hashCode(): Int = 31 * label.hashCode() + range.hashCode()

    override fun toString(): String = "DateRangePreset(label=$label, range=$range)"
}
