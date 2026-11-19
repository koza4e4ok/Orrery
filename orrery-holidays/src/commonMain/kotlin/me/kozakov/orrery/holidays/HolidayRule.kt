package me.kozakov.orrery.holidays

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

/**
 * How a [HolidayRule] picks its date in a Gregorian year.
 *
 * New entries/subtypes may be added in a minor release: do not rely on an exhaustive
 * `when` over this type across versions; add an `else` branch.
 */
public sealed interface HolidayDate {
    /**
     * The same month and day every year.
     *
     * @property month Month of the holiday.
     * @property day Day of [month]; a day the month lacks in a year (e.g. 29 February outside
     *   leap years) produces no date that year.
     */
    public data class Fixed(
        val month: Month,
        val day: Int,
    ) : HolidayDate

    /**
     * The [n]th [dayOfWeek] of [month]; n = -1 is the last one. With n = 5 the rule produces
     * no date in years where [month] has only four such weekdays.
     */
    public data class NthWeekday(
        val month: Month,
        val dayOfWeek: DayOfWeek,
        val n: Int,
    ) : HolidayDate

    /**
     * The first [dayOfWeek] strictly after ([after] = true) or strictly before
     * ([after] = false) [month]/[day], e.g. German Repentance Day is the Wednesday before
     * 23 November.
     *
     * @property month Month of the reference date.
     * @property day Day of [month] of the reference date; the reference date itself never matches.
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
     * Holidays from these rules are marked [PublicHoliday.isEstimated]. Outside the Umm al-Qura
     * range, or for a day the Hijri month lacks that year, the rule produces no date.
     *
     * @property month Hijri month, 1 (Muharram) .. 12 (Dhu al-Hijjah).
     * @property day Day of the Hijri month, 1..30.
     */
    public data class HijriFixed(
        val month: Int,
        val day: Int,
    ) : HolidayDate

    /**
     * A fixed Persian (Jalali) month/day. A day the Persian month lacks that year (e.g. Esfand 30
     * outside leap years) produces no date.
     *
     * @property month Persian month, 1 (Farvardin) .. 12 (Esfand).
     * @property day Day of the Persian month, 1..31.
     */
    public data class PersianFixed(
        val month: Int,
        val day: Int,
    ) : HolidayDate

    /**
     * One specific date, e.g. a coronation bank holiday.
     *
     * @property date The date; the rule produces nothing in other years.
     */
    public data class OneOff(
        val date: LocalDate,
    ) : HolidayDate
}

/**
 * Easter computus used by [HolidayDate.EasterOffset].
 *
 * New entries/subtypes may be added in a minor release: do not rely on an exhaustive
 * `when` over this type across versions; add an `else` branch.
 */
public enum class Church {
    /** Gregorian computus (Catholic and Protestant churches). */
    Western,

    /** Julian computus converted to the Gregorian calendar (Orthodox churches). */
    Orthodox,
}

/**
 * What happens when a holiday falls on a weekend.
 *
 * New entries/subtypes may be added in a minor release: do not rely on an exhaustive
 * `when` over this type across versions; add an `else` branch.
 */
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

/**
 * Kind of day, used to filter holidays.
 *
 * New entries/subtypes may be added in a minor release: do not rely on an exhaustive
 * `when` over this type across versions; add an `else` branch.
 */
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
 * @property date How the rule picks its date in each year.
 * @property type Kind of day; defaults to [HolidayType.Public].
 * @property years Years in which the rule applies, so a law change becomes two rules.
 * @property subdivisions ISO 3166-2 subdivision codes (e.g. `"DE-BY"`) the rule applies to, or
 *   null for the whole country.
 * @property observance Substitute-day rule for a holiday that falls on a weekend; defaults to
 *   [Observance.None].
 */
public class HolidayRule(
    public val id: String,
    public val names: Map<String, String>,
    public val date: HolidayDate,
    public val type: HolidayType = HolidayType.Public,
    public val years: IntRange = Int.MIN_VALUE..Int.MAX_VALUE,
    public val observance: Observance = Observance.None,
    public val subdivisions: Set<String>? = null,
) {
    init {
        require("en" in names) { "names must contain \"en\" ($id)" }
    }

    /**
     * The name in [language], falling back to its primary language subtag (`"de-DE"` and
     * `"de_AT"` find `"de"`) and then to English.
     */
    public fun name(language: String?): String {
        if (language == null) return names.getValue("en")
        val primary = language.substringBefore('-').substringBefore('_').lowercase()
        return names[language] ?: names[primary] ?: names.getValue("en")
    }

    /** Returns a copy of this rule with the given properties replaced. */
    @Suppress("LongParameterList")
    public fun copy(
        id: String = this.id,
        names: Map<String, String> = this.names,
        date: HolidayDate = this.date,
        type: HolidayType = this.type,
        years: IntRange = this.years,
        observance: Observance = this.observance,
        subdivisions: Set<String>? = this.subdivisions,
    ): HolidayRule =
        HolidayRule(
            id = id,
            names = names,
            date = date,
            type = type,
            years = years,
            observance = observance,
            subdivisions = subdivisions,
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HolidayRule) return false
        return id == other.id &&
            names == other.names &&
            date == other.date &&
            type == other.type &&
            years == other.years &&
            observance == other.observance &&
            subdivisions == other.subdivisions
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + names.hashCode()
        result = 31 * result + date.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + years.hashCode()
        result = 31 * result + observance.hashCode()
        result = 31 * result + subdivisions.hashCode()
        return result
    }

    override fun toString(): String =
        "HolidayRule(id=$id, names=$names, date=$date, type=$type, years=$years, observance=$observance, " +
            "subdivisions=$subdivisions)"
}

/**
 * One holiday occurrence produced by [HolidayCalendar].
 *
 * @property date The day of the holiday; for a substitute, the substitute day off.
 * @property ruleId [HolidayRule.id] of the rule that produced this holiday.
 * @property name Holiday name in the requested language, as returned by [HolidayRule.name].
 * @property type [HolidayRule.type] of the rule.
 * @property isSubstitute True for a day off that replaces a holiday falling on a weekend.
 * @property isEstimated True when the date comes from a calculated calendar (Hijri) and the
 *   official date may still be announced differently.
 */
public class PublicHoliday(
    public val date: LocalDate,
    public val ruleId: String,
    public val name: String,
    public val type: HolidayType,
    public val isSubstitute: Boolean = false,
    public val isEstimated: Boolean = false,
) {
    /** Returns a copy of this holiday with the given properties replaced. */
    @Suppress("LongParameterList")
    public fun copy(
        date: LocalDate = this.date,
        ruleId: String = this.ruleId,
        name: String = this.name,
        type: HolidayType = this.type,
        isSubstitute: Boolean = this.isSubstitute,
        isEstimated: Boolean = this.isEstimated,
    ): PublicHoliday =
        PublicHoliday(
            date = date,
            ruleId = ruleId,
            name = name,
            type = type,
            isSubstitute = isSubstitute,
            isEstimated = isEstimated,
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PublicHoliday) return false
        return date == other.date &&
            ruleId == other.ruleId &&
            name == other.name &&
            type == other.type &&
            isSubstitute == other.isSubstitute &&
            isEstimated == other.isEstimated
    }

    override fun hashCode(): Int {
        var result = date.hashCode()
        result = 31 * result + ruleId.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + isSubstitute.hashCode()
        result = 31 * result + isEstimated.hashCode()
        return result
    }

    override fun toString(): String =
        "PublicHoliday(date=$date, ruleId=$ruleId, name=$name, type=$type, isSubstitute=$isSubstitute, " +
            "isEstimated=$isEstimated)"
}
