package me.kozakov.orrery.lunar

import kotlinx.datetime.LocalDate
import me.kozakov.orrery.core.DayInfo
import me.kozakov.orrery.core.DayInfoPriority
import me.kozakov.orrery.core.DayInfoProvider

/**
 * Structured lunisolar metadata attached to [DayInfo.payload] by [LunarDayInfoProvider].
 *
 * @property gregorianFestival Gregorian-calendar festival on this date, if any. Besides
 *   fixed-date festivals (e.g. New Year's Day) it also carries floating ones computed from the
 *   weekday, such as Mother's Day, Father's Day and Thanksgiving.
 * @property traditionalFestival Festival on the lunar calendar (e.g. Dragon Boat Festival), if any.
 */
public data class LunarDayMetadata(
    public val lunarDate: LunarDate,
    public val solarTerm: SolarTerm? = null,
    public val gregorianFestival: String? = null,
    public val traditionalFestival: String? = null,
)

/**
 * [DayInfoProvider] backed by the Chinese lunisolar calendar: secondary labels follow the
 * original CalendarView priority — solar term, then Gregorian festival (with Mother's/Father's/
 * Thanksgiving day as fallback), then traditional lunar festival, then the lunar day name.
 *
 * Supported range: 1900..2099 (returns null outside). Not thread-safe; create per composition.
 */
public class LunarDayInfoProvider(
    private val variant: ChineseVariant = ChineseVariant.SIMPLIFIED,
) : DayInfoProvider {
    private val termCache = HashMap<Int, Map<LocalDate, SolarTerm>>()

    @Suppress("CyclomaticComplexMethod")
    override fun info(date: LocalDate): DayInfo? {
        if (date.year < 1900 || date.year > 2099) return null
        val term = termCache.getOrPut(date.year) { solarTermsFor(date.year) }[date]
        val lunar = date.toLunarDate()
        val gregorian = gregorianFestival(date, variant)
        val special = if (gregorian == null) specialFestival(date, variant) else null
        val traditional = traditionalFestival(lunar, date, variant)
        val label =
            when {
                term != null -> term.displayName(variant)
                gregorian != null -> gregorian
                special != null -> special
                traditional != null -> traditional
                else -> lunarLabel(lunar, variant)
            }
        val tags =
            buildList {
                if (term != null) add("solar-term")
                if (gregorian != null) add("gregorian-festival")
                if (special != null) add("special-festival")
                if (traditional != null) add("traditional-festival")
            }
        val hasFestival = gregorian != null || special != null || traditional != null
        val priority =
            when {
                hasFestival -> DayInfoPriority.FESTIVAL
                term != null -> DayInfoPriority.SOLAR_TERM
                else -> DayInfoPriority.DEFAULT
            }
        return DayInfo(
            label = label,
            isHoliday = hasFestival,
            tags = tags,
            priority = priority,
            payload =
                LunarDayMetadata(
                    lunarDate = lunar,
                    solarTerm = term,
                    gregorianFestival = gregorian ?: special,
                    traditionalFestival = traditional,
                ),
        )
    }
}
