package dev.koza4e4ok.material.calendar.lunar

import kotlinx.datetime.LocalDate

/** The 24 solar terms, ordinal order matching the original name table (0 = 春分, spring equinox). */
public enum class SolarTerm {
    CHUNFEN,
    QINGMING,
    GUYU,
    LIXIA,
    XIAOMAN,
    MANGZHONG,
    XIAZHI,
    XIAOSHU,
    DASHU,
    LIQIU,
    CHUSHU,
    BAILU,
    QIUFEN,
    HANLU,
    SHUANGJIANG,
    LIDONG,
    XIAOXUE,
    DAXUE,
    DONGZHI,
    XIAOHAN,
    DAHAN,
    LICHUN,
    YUSHUI,
    JINGZHE,
}

public fun SolarTerm.displayName(variant: ChineseVariant): String = strings(variant).solarTermNames[ordinal]

/**
 * All 24 solar-term dates falling inside Gregorian [year] (Beijing time, UTC+8),
 * matching the original SolarTermUtil.getSolarTerms.
 */
public fun solarTermsFor(year: Int): Map<LocalDate, SolarTerm> {
    val result = HashMap<LocalDate, SolarTerm>(32)
    // 小寒(19)..惊蛰(23) fall early in `year` and are computed from the previous year's base.
    val jdPrev = 365.2422 * (year - 1 - 2000)
    for (i in 19..23) {
        result[termDate(jdPrev + i * 15.2, i * 15.0)] = SolarTerm.entries[i]
    }
    val jd = 365.2422 * (year - 2000)
    for (i in 0..18) {
        result[termDate(jd + i * 15.2, i * 15.0)] = SolarTerm.entries[i]
    }
    return result
}

private fun termDate(
    start: Double,
    angleDeg: Double,
): LocalDate {
    val q = timeOfSunLongitude(start, angleDeg) + J2000 + 8.0 / 24
    return julianToDate(q)
}
