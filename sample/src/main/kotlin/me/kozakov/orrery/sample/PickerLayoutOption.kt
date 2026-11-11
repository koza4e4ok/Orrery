package me.kozakov.orrery.sample

import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import me.kozakov.orrery.compose.DateRangeCalendarLayoutMode

/** Calendar layout of the range picker dialog: by window width, always two months, or always one column. */
internal enum class PickerLayoutOption(
    val label: String,
    val mode: DateRangeCalendarLayoutMode,
) {
    Auto("Auto", DateRangeCalendarLayoutMode.Adaptive()),
    Dual("Dual", DateRangeCalendarLayoutMode.DualMonth),
    Single("Single", DateRangeCalendarLayoutMode.SingleMonth),
}

/** One chip per [PickerLayoutOption], tagged `picker_layout_auto` / `_dual` / `_single`; place it in a row. */
@Composable
internal fun PickerLayoutChips(
    selected: PickerLayoutOption,
    onSelect: (PickerLayoutOption) -> Unit,
) {
    PickerLayoutOption.entries.forEach { option ->
        FilterChip(
            selected = option == selected,
            onClick = { onSelect(option) },
            label = { Text(option.label) },
            modifier = Modifier.testTag("picker_layout_${option.name.lowercase()}"),
        )
    }
}
