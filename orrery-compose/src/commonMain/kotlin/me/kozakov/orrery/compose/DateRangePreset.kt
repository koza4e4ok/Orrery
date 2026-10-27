package me.kozakov.orrery.compose

import androidx.compose.runtime.Immutable
import kotlinx.datetime.LocalDate

/**
 * A quick-pick chip for [OrreryDateRangePickerDialog]: tapping it selects
 * [range] (validated like taps, so it is ignored when it breaks the
 * state's bounds, `minDays`/`maxDays` or disabled dates). Build the
 * standard set with [DatePickerDefaults.rangePresets].
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
