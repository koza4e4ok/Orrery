package me.kozakov.orrery.compose

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource

@Composable
@ReadOnlyComposable
internal actual fun orreryString(string: OrreryString): String = stringResource(string.resId)

@get:StringRes
private val OrreryString.resId: Int
    get() =
        when (this) {
            OrreryString.DatePickerTitle -> R.string.orrery_date_picker_title
            OrreryString.DatePickerRangeTitle -> R.string.orrery_date_picker_range_title
            OrreryString.DatePickerHeadlinePlaceholder -> R.string.orrery_date_picker_headline_placeholder
            OrreryString.DatePickerRangeStartPlaceholder -> R.string.orrery_date_picker_range_start_placeholder
            OrreryString.DatePickerRangeEndPlaceholder -> R.string.orrery_date_picker_range_end_placeholder
            OrreryString.DatePickerInputLabel -> R.string.orrery_date_picker_input_label
            OrreryString.DatePickerRangeStartInputLabel -> R.string.orrery_date_picker_range_start_input_label
            OrreryString.DatePickerRangeEndInputLabel -> R.string.orrery_date_picker_range_end_input_label
            OrreryString.DatePickerInvalidFormatError -> R.string.orrery_date_picker_invalid_format_error
            OrreryString.DatePickerOutOfRangeError -> R.string.orrery_date_picker_out_of_range_error
            OrreryString.DatePickerDisabledDateError -> R.string.orrery_date_picker_disabled_date_error
            OrreryString.DatePickerInvalidRangeError -> R.string.orrery_date_picker_invalid_range_error
            OrreryString.DatePickerSwitchToInput -> R.string.orrery_date_picker_switch_to_input
            OrreryString.DatePickerSwitchToPicker -> R.string.orrery_date_picker_switch_to_picker
            OrreryString.PreviousMonth -> R.string.orrery_previous_month
            OrreryString.NextMonth -> R.string.orrery_next_month
            OrreryString.Close -> R.string.orrery_close
            OrreryString.MonthYearPickerDecade -> R.string.orrery_month_year_picker_decade
            OrreryString.HeatmapLegendLess -> R.string.orrery_heatmap_legend_less
            OrreryString.HeatmapLegendMore -> R.string.orrery_heatmap_legend_more
            OrreryString.HeatmapDayDescription -> R.string.orrery_heatmap_day_description
            OrreryString.HeatmapDayDescriptionEmpty -> R.string.orrery_heatmap_day_description_empty
            OrreryString.HeatmapSummaryActiveDays -> R.string.orrery_heatmap_summary_active_days
            OrreryString.HeatmapSummaryStreak -> R.string.orrery_heatmap_summary_streak
            OrreryString.DayRangeStart -> R.string.orrery_day_state_range_start
            OrreryString.DayRangeEnd -> R.string.orrery_day_state_range_end
            OrreryString.DayInRange -> R.string.orrery_day_state_in_range
            OrreryString.DayToday -> R.string.orrery_day_state_today
            OrreryString.DayUnavailable -> R.string.orrery_day_state_unavailable
            OrreryString.AgendaTodayPrefix -> R.string.orrery_agenda_today_prefix
            OrreryString.AgendaAllDay -> R.string.orrery_agenda_all_day
            OrreryString.AgendaNoEvents -> R.string.orrery_agenda_no_events
            OrreryString.ScheduleNow -> R.string.orrery_schedule_now
            OrreryString.DatePickerPresetLast7Days -> R.string.orrery_date_picker_preset_last_7_days
            OrreryString.DatePickerPresetLast30Days -> R.string.orrery_date_picker_preset_last_30_days
            OrreryString.DatePickerPresetThisWeek -> R.string.orrery_date_picker_preset_this_week
            OrreryString.DatePickerPresetThisMonth -> R.string.orrery_date_picker_preset_this_month
            OrreryString.DateTimePickerTitle -> R.string.orrery_date_time_picker_title
            OrreryString.DateTimePickerNext -> R.string.orrery_date_time_picker_next
            OrreryString.DateTimePickerBack -> R.string.orrery_date_time_picker_back
            OrreryString.DateTimePickerTimeTitle -> R.string.orrery_date_time_picker_time_title
            OrreryString.DateTimePickerSwitchToClock -> R.string.orrery_date_time_picker_switch_to_clock
            OrreryString.DateTimePickerSwitchToKeyboard -> R.string.orrery_date_time_picker_switch_to_keyboard
            OrreryString.MonthPickerTitle -> R.string.orrery_month_picker_title
            OrreryString.MonthPickerHeadlinePlaceholder -> R.string.orrery_month_picker_headline_placeholder
        }
