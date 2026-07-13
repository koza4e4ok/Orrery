package dev.koza4e4ok.orrery.lunar

import dev.koza4e4ok.orrery.core.DayInfo
import dev.koza4e4ok.orrery.core.DayInfoProvider
import kotlinx.datetime.LocalDate

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
        return DayInfo(
            label = label,
            isHoliday = gregorian != null || special != null || traditional != null,
            tags = tags,
        )
    }
}
