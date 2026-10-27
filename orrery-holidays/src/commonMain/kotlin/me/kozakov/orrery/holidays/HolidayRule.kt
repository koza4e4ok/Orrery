package me.kozakov.orrery.holidays

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

/** How a [HolidayRule] picks its date in a Gregorian year. */
public sealed interface HolidayDate {
    /** The same month and day every year. */
    public data class Fixed(
        val month: Month,
        val day: Int,
    ) : HolidayDate

    /** The [n]th [dayOfWeek] of [month]; n = -1 is the last one. */
    public data class NthWeekday(
        val month: Month,
        val dayOfWeek: DayOfWeek,
        val n: Int,
    ) : HolidayDate

    /**
     * The first [dayOfWeek] strictly after ([after] = true) or strictly before
     * ([after] = false) [month]/[day], e.g. German Repentance Day is the Wednesday before
     * 23 November.
     */
    public data class WeekdayRelative(
        val month: Month,
        val day: Int,
        val dayOfWeek: DayOfWeek,
        val after: Boolean,
    ) : HolidayDate

    /** [days] after Easter Sunday of [church] (negative for days before). */
    public data class EasterOffset(
        val days: Int,
        val church: Church = Church.Western,
    ) : HolidayDate

    /**
     * A fixed Umm al-Qura Hijri month/day; may fall twice or not at all in a Gregorian year.
     * Holidays from these rules are marked [PublicHoliday.isEstimated].
     */
    public data class HijriFixed(
        val month: Int,
        val day: Int,
    ) : HolidayDate

    /** A fixed Persian (Jalali) month/day. */
    public data class PersianFixed(
        val month: Int,
        val day: Int,
    ) : HolidayDate

    /** One specific date, e.g. a coronation bank holiday. */
    public data class OneOff(
        val date: LocalDate,
    ) : HolidayDate
}

/** Easter computus used by [HolidayDate.EasterOffset]. */
public enum class Church {
    /** Gregorian computus (Catholic and Protestant churches). */
    Western,

    /** Julian computus converted to the Gregorian calendar (Orthodox churches). */
    Orthodox,
}

/** What happens when a holiday falls on a weekend. */
public enum class Observance {
    /** Nothing: no substitute day. */
    None,

    /** Saturday moves to Friday and Sunday to Monday (US federal rule). */
    NearestWeekday,

    /**
     * The next weekday that is not already a holiday gets a substitute day (UK and UA rule).
     * Several holidays on one weekend get successive substitute days; two holidays on the
     * same day give a single substitute day.
     */
    NextWorkingDay,

    /** Sunday moves to Monday; Saturday has no substitute. */
    SundayToMonday,
}

/** Kind of day, used to filter holidays. */
public enum class HolidayType {
    /** Statutory public holiday. */
    Public,

    /** Bank holiday (UK). */
    Bank,

    /** Optional or partial day off. */
    Optional,

    /** Observance that is not a day off. */
    Observance,
}

/**
 * One holiday definition in a [HolidayCalendar].
 *
 * @property id Stable identifier, e.g. `"us.independence-day"`.
 * @property names Holiday name by BCP-47 language tag; contains `"en"` and usually the local
 *   language.
 * @property years Years in which the rule applies, so a law change becomes two rules.
 * @property subdivisions ISO 3166-2 subdivision codes (e.g. `"DE-BY"`) the rule applies to, or
 *   null for the whole country.
 */
public data class HolidayRule(
    val id: String,
    val names: Map<String, String>,
    val date: HolidayDate,
    val type: HolidayType = HolidayType.Public,
    val years: IntRange = Int.MIN_VALUE..Int.MAX_VALUE,
    val observance: Observance = Observance.None,
    val subdivisions: Set<String>? = null,
) {
    init {
        require("en" in names) { "names must contain \"en\" ($id)" }
    }

    /** The name in [language], falling back to English. */
    public fun name(language: String?): String = language?.let { names[it] } ?: names.getValue("en")
}

/**
 * One holiday occurrence produced by [HolidayCalendar].
 *
 * @property isSubstitute True for a day off that replaces a holiday falling on a weekend.
 * @property isEstimated True when the date comes from a calculated calendar (Hijri) and the
 *   official date may still be announced differently.
 */
public data class PublicHoliday(
    val date: LocalDate,
    val ruleId: String,
    val name: String,
    val type: HolidayType,
    val isSubstitute: Boolean = false,
    val isEstimated: Boolean = false,
)
