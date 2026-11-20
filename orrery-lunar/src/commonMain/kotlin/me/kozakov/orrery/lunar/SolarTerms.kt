package me.kozakov.orrery.lunar

import kotlinx.datetime.LocalDate

/**
 * The 24 solar terms, ordinal order matching the original name table (0 = 春分, spring equinox).
 *
 * Member docs show the Simplified Chinese name; [displayName] returns the Traditional form
 * (e.g. 穀雨, 小滿) for [ChineseVariant.TRADITIONAL_TW] and [ChineseVariant.TRADITIONAL_HK].
 */
public enum class SolarTerm {
    /** 春分 (Spring Equinox): the sun at ecliptic longitude 0 degrees. */
    CHUNFEN,

    /** 清明 (Clear and Bright): the sun at ecliptic longitude 15 degrees. */
    QINGMING,

    /** 谷雨 (Grain Rain): the sun at ecliptic longitude 30 degrees. */
    GUYU,

    /** 立夏 (Start of Summer): the sun at ecliptic longitude 45 degrees. */
    LIXIA,

    /** 小满 (Grain Buds): the sun at ecliptic longitude 60 degrees. */
    XIAOMAN,

    /** 芒种 (Grain in Ear): the sun at ecliptic longitude 75 degrees. */
    MANGZHONG,

    /** 夏至 (Summer Solstice): the sun at ecliptic longitude 90 degrees. */
    XIAZHI,

    /** 小暑 (Minor Heat): the sun at ecliptic longitude 105 degrees. */
    XIAOSHU,

    /** 大暑 (Major Heat): the sun at ecliptic longitude 120 degrees. */
    DASHU,

    /** 立秋 (Start of Autumn): the sun at ecliptic longitude 135 degrees. */
    LIQIU,

    /** 处暑 (End of Heat): the sun at ecliptic longitude 150 degrees. */
    CHUSHU,

    /** 白露 (White Dew): the sun at ecliptic longitude 165 degrees. */
    BAILU,

    /** 秋分 (Autumn Equinox): the sun at ecliptic longitude 180 degrees. */
    QIUFEN,

    /** 寒露 (Cold Dew): the sun at ecliptic longitude 195 degrees. */
    HANLU,

    /** 霜降 (Frost Descent): the sun at ecliptic longitude 210 degrees. */
    SHUANGJIANG,

    /** 立冬 (Start of Winter): the sun at ecliptic longitude 225 degrees. */
    LIDONG,

    /** 小雪 (Minor Snow): the sun at ecliptic longitude 240 degrees. */
    XIAOXUE,

    /** 大雪 (Major Snow): the sun at ecliptic longitude 255 degrees. */
    DAXUE,

    /** 冬至 (Winter Solstice): the sun at ecliptic longitude 270 degrees. */
    DONGZHI,

    /** 小寒 (Minor Cold): the sun at ecliptic longitude 285 degrees. */
    XIAOHAN,

    /** 大寒 (Major Cold): the sun at ecliptic longitude 300 degrees. */
    DAHAN,

    /** 立春 (Start of Spring): the sun at ecliptic longitude 315 degrees. */
    LICHUN,

    /** 雨水 (Rain Water): the sun at ecliptic longitude 330 degrees. */
    YUSHUI,

    /** 惊蛰 (Awakening of Insects): the sun at ecliptic longitude 345 degrees. */
    JINGZHE,
}

/** Returns the Chinese name of this solar term in the given [variant], e.g. 春分 for [SolarTerm.CHUNFEN]. */
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
