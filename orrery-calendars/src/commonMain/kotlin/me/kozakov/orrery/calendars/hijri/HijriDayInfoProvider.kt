package me.kozakov.orrery.calendars.hijri

import kotlinx.datetime.LocalDate
import me.kozakov.orrery.core.DayInfo
import me.kozakov.orrery.core.DayInfoPriority
import me.kozakov.orrery.core.DayInfoProvider

/**
 * [DayInfoProvider] that labels each day with its Hijri date: the month name on the first
 * day of a month, the day number otherwise. [DayInfo.payload] is the [HijriDate],
 * [DayInfo.tags] is `["hijri"]` and [DayInfo.priority] is [DayInfoPriority.SECONDARY], so a
 * holiday provider combined with `+` wins the label.
 *
 * With [HijriVariant.UmmAlQura] dates outside 1882-11-12 .. 2174-11-25 return null. Umm
 * al-Qura is a calculated calendar; dates announced after a moon sighting may differ by a day.
 */
public class HijriDayInfoProvider(
    private val variant: HijriVariant = HijriVariant.UmmAlQura,
    private val strings: HijriStrings = HijriStrings.English,
) : DayInfoProvider {
    override fun info(date: LocalDate): DayInfo? {
        if (variant == HijriVariant.UmmAlQura && !UmmAlQura.supports(date)) return null
        val hijri = date.toHijriDate(variant)
        return DayInfo(
            label = if (hijri.day == 1) strings.monthNames[hijri.month - 1] else strings.dayLabel(hijri.day),
            tags = listOf("hijri"),
            priority = DayInfoPriority.SECONDARY,
            payload = hijri,
        )
    }
}

internal fun UmmAlQura.supports(date: LocalDate): Boolean {
    val epochDay = date.toEpochDays()
    return epochDay >= UmmAlQuraTable.FIRST_EPOCH_DAY && epochDay < UmmAlQuraTable.endEpochDayExclusive
}
