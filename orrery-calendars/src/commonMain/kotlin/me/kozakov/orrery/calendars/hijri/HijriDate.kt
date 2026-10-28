package me.kozakov.orrery.calendars.hijri

import kotlinx.datetime.LocalDate

/** Rule set used to convert between Gregorian and Hijri dates. */
public enum class HijriVariant {
    /**
     * Saudi Arabia's official Umm al-Qura calendar, supported for 1300-01-01 .. 1600-12-30 AH
     * (1882-11-12 .. 2174-11-25). It is calculated in advance; religious dates announced after
     * a moon sighting may differ by a day.
     */
    UmmAlQura,

    /**
     * Arithmetic (civil) Islamic calendar: epoch 16 July 622 (Julian), 30-year cycle with leap
     * years 2, 5, 7, 10, 13, 16, 18, 21, 24, 26 and 29. Unbounded, but can differ from observed
     * dates by a day or two.
     */
    Tabular,
}

/**
 * A date in the Hijri (Islamic lunar) calendar. [month] is 1 (Muharram) .. 12 (Dhu al-Hijjah).
 * Construction checks the month and that [day] is 1..30; [toLocalDate] also checks the day
 * against the month length of [variant].
 */
public data class HijriDate(
    val year: Int,
    val month: Int,
    val day: Int,
    val variant: HijriVariant = HijriVariant.UmmAlQura,
) {
    init {
        require(month in 1..12) { "month must be in 1..12, was $month" }
        require(day in 1..30) { "day must be in 1..30, was $day" }
    }
}

/**
 * Converts this date to the Hijri calendar.
 *
 * @throws IllegalArgumentException for [HijriVariant.UmmAlQura] outside 1882-11-12 .. 2174-11-25.
 */
public fun LocalDate.toHijriDate(variant: HijriVariant = HijriVariant.UmmAlQura): HijriDate =
    when (variant) {
        HijriVariant.UmmAlQura -> UmmAlQura.fromEpochDay(toEpochDays())
        HijriVariant.Tabular -> TabularHijri.fromEpochDay(toEpochDays())
    }

/**
 * Converts this Hijri date to a Gregorian [LocalDate].
 *
 * @throws IllegalArgumentException if [HijriDate.day] exceeds the month length, or for
 *   [HijriVariant.UmmAlQura] outside 1300..1600 AH.
 */
public fun HijriDate.toLocalDate(): LocalDate {
    val length = monthLength()
    require(day <= length) { "day $day exceeds the $length days of $year-$month ($variant)" }
    val epochDay =
        when (variant) {
            HijriVariant.UmmAlQura -> UmmAlQura.monthStart(year, month)
            HijriVariant.Tabular -> TabularHijri.monthStart(year, month)
        } + day - 1
    return LocalDate.fromEpochDays(epochDay)
}

/**
 * Number of days (29 or 30) in this date's month.
 *
 * @throws IllegalArgumentException for [HijriVariant.UmmAlQura] outside 1300..1600 AH.
 */
public fun HijriDate.monthLength(): Int =
    when (variant) {
        HijriVariant.UmmAlQura -> {
            UmmAlQura.requireYear(year)
            UmmAlQuraTable.monthLength(year, month)
        }

        HijriVariant.Tabular -> {
            TabularHijri.monthLength(year, month)
        }
    }

internal object UmmAlQura {
    fun requireYear(year: Int) {
        require(year in UmmAlQuraTable.FIRST_YEAR..UmmAlQuraTable.LAST_YEAR) {
            "Umm al-Qura supports ${UmmAlQuraTable.FIRST_YEAR}..${UmmAlQuraTable.LAST_YEAR} AH, was $year"
        }
    }

    fun monthStart(
        year: Int,
        month: Int,
    ): Long {
        requireYear(year)
        var start = UmmAlQuraTable.yearStart(year)
        for (m in 1 until month) start += UmmAlQuraTable.monthLength(year, m)
        return start
    }

    fun fromEpochDay(epochDay: Long): HijriDate {
        require(epochDay >= UmmAlQuraTable.FIRST_EPOCH_DAY && epochDay < UmmAlQuraTable.endEpochDayExclusive) {
            "Umm al-Qura supports 1882-11-12..2174-11-25, was ${LocalDate.fromEpochDays(epochDay)}"
        }
        val year = UmmAlQuraTable.yearOf(epochDay)
        var remaining = epochDay - UmmAlQuraTable.yearStart(year)
        var month = 1
        while (remaining >= UmmAlQuraTable.monthLength(year, month)) {
            remaining -= UmmAlQuraTable.monthLength(year, month)
            month++
        }
        return HijriDate(year, month, remaining.toInt() + 1, HijriVariant.UmmAlQura)
    }
}

internal object TabularHijri {
    /** Epoch day of 1 Muharram 1 AH: Julian day 1948440 (16 July 622 Julian). */
    private const val EPOCH: Long = 1_948_440L - 2_440_588L

    fun isLeapYear(year: Int): Boolean = (14 + 11 * year.toLong()).mod(30L) < 11

    fun monthLength(
        year: Int,
        month: Int,
    ): Int =
        when {
            month % 2 == 1 -> 30
            month == 12 && isLeapYear(year) -> 30
            else -> 29
        }

    private fun yearStart(year: Int): Long = (year - 1L) * 354 + (3 + 11L * year).floorDiv(30L)

    fun monthStart(
        year: Int,
        month: Int,
    ): Long = EPOCH + yearStart(year) + (59L * (month - 1) + 1) / 2

    fun fromEpochDay(epochDay: Long): HijriDate {
        val days = epochDay - EPOCH
        var year = (30 * days + 10646).floorDiv(10631L).toInt()
        // The estimate can be one year high just before a year boundary.
        if (yearStart(year) > days) year--
        var month = 12
        while (monthStart(year, month) > epochDay) month--
        return HijriDate(year, month, (epochDay - monthStart(year, month)).toInt() + 1, HijriVariant.Tabular)
    }
}
