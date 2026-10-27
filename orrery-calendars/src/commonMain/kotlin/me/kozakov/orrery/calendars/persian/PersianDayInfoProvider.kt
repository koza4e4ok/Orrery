package me.kozakov.orrery.calendars.persian

import kotlinx.datetime.LocalDate
import me.kozakov.orrery.core.DayInfo
import me.kozakov.orrery.core.DayInfoPriority
import me.kozakov.orrery.core.DayInfoProvider

/**
 * [DayInfoProvider] that labels each day with its Persian (Jalali) date: the month name on
 * the first day of a month, the day number otherwise. [DayInfo.payload] is the [PersianDate],
 * [DayInfo.tags] is `["persian"]` and [DayInfo.priority] is [DayInfoPriority.SECONDARY].
 * Dates outside the supported years -61..3177 AP return null.
 */
public class PersianDayInfoProvider(
    private val strings: PersianStrings = PersianStrings.English,
) : DayInfoProvider {
    override fun info(date: LocalDate): DayInfo? {
        if (!PersianCalendar.supports(date)) return null
        val persian = date.toPersianDate()
        return DayInfo(
            label = if (persian.day == 1) strings.monthNames[persian.month - 1] else strings.dayLabel(persian.day),
            tags = listOf("persian"),
            priority = DayInfoPriority.SECONDARY,
            payload = persian,
        )
    }
}
