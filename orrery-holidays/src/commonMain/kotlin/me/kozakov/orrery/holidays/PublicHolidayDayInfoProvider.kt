package me.kozakov.orrery.holidays

import kotlinx.datetime.LocalDate
import me.kozakov.orrery.core.DayInfo
import me.kozakov.orrery.core.DayInfoPriority
import me.kozakov.orrery.core.DayInfoProvider
import kotlin.concurrent.Volatile

/**
 * [DayInfoProvider] that marks the holidays of [calendar]: [DayInfo.label] is the holiday
 * name, [DayInfo.isHoliday] is true and [DayInfo.priority] is
 * [DayInfoPriority.PUBLIC_HOLIDAY], so it wins the label when combined with `+`, for example
 * `PublicHolidayDayInfoProvider(PublicHolidays.forCountry("US")!!) + LunarDayInfoProvider()`.
 *
 * [DayInfo.tags] holds `"holiday"`, the lower-case [HolidayType] and, when they apply,
 * `"substitute"` and `"estimated"`. [DayInfo.payload] is the `List<PublicHoliday>` on that
 * date in rule order; the label comes from the first one.
 *
 * @param subdivision ISO 3166-2 code (e.g. `"DE-BY"`) whose regional holidays are added.
 * @param language BCP-47 language for names; falls back to English.
 * @param types Holiday types to show.
 */
public class PublicHolidayDayInfoProvider(
    private val calendar: HolidayCalendar,
    private val subdivision: String? = null,
    private val language: String? = null,
    private val types: Set<HolidayType> = setOf(HolidayType.Public, HolidayType.Bank),
) : DayInfoProvider {
    /** Holidays by date for the most recently used years; replaced, never mutated. */
    @Volatile
    private var cache: Map<Int, Map<LocalDate, List<PublicHoliday>>> = emptyMap()

    override fun info(date: LocalDate): DayInfo? {
        val holidays = holidaysByDate(date.year)[date] ?: return null
        val first = holidays.first()
        val tags =
            buildList {
                add("holiday")
                add(first.type.name.lowercase())
                if (holidays.any { it.isSubstitute }) add("substitute")
                if (holidays.any { it.isEstimated }) add("estimated")
            }
        return DayInfo(
            label = first.name,
            isHoliday = true,
            tags = tags,
            priority = DayInfoPriority.PUBLIC_HOLIDAY,
            payload = holidays,
        )
    }

    private fun holidaysByDate(year: Int): Map<LocalDate, List<PublicHoliday>> {
        val current = cache
        current[year]?.let { return it }
        val byDate =
            calendar
                .holidaysIn(year, subdivision, language)
                .filter { it.type in types }
                .groupBy { it.date }
        // `info` runs once per visible cell, so keep the last few years (oldest dropped first).
        cache = (current - current.keys.take(maxOf(0, current.size - CACHED_YEARS + 1)).toSet()) + (year to byDate)
        return byDate
    }

    private companion object {
        const val CACHED_YEARS = 4
    }
}
