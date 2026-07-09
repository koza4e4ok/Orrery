package dev.koza4e4ok.material.calendar.core

import kotlinx.datetime.LocalDate

/**
 * Secondary per-day information (lunar date, festival, holiday flag…).
 * calendar-lunar ships an implementation; consumers can provide their own.
 */
public interface DayInfoProvider {
    public fun info(date: LocalDate): DayInfo?
}

public data class DayInfo(
    val label: String?,
    val isHoliday: Boolean = false,
    val tags: List<String> = emptyList(),
)
