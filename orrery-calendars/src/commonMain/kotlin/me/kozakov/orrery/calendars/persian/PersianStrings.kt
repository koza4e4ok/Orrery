package me.kozakov.orrery.calendars.persian

/**
 * Labels used by [PersianDayInfoProvider]: [monthNames] holds the 12 month names from
 * Farvardin, shown on the first day of each month; [digits] holds the ten digits 0..9 used for
 * the other days' numbers.
 */
public class PersianStrings(
    public val monthNames: List<String>,
    public val digits: String = "0123456789",
) {
    init {
        require(monthNames.size == 12) { "monthNames needs 12 entries, had ${monthNames.size}" }
        require(digits.length == 10) { "digits needs 10 characters, had ${digits.length}" }
    }

    /** [day] written with [digits]. */
    public fun dayLabel(day: Int): String = day.toString().map { digits[it - '0'] }.joinToString("")

    public companion object {
        public val English: PersianStrings =
            PersianStrings(
                listOf(
                    "Farvardin",
                    "Ordibehesht",
                    "Khordad",
                    "Tir",
                    "Mordad",
                    "Shahrivar",
                    "Mehr",
                    "Aban",
                    "Azar",
                    "Dey",
                    "Bahman",
                    "Esfand",
                ),
            )

        public val Persian: PersianStrings =
            PersianStrings(
                listOf(
                    "فروردین",
                    "اردیبهشت",
                    "خرداد",
                    "تیر",
                    "مرداد",
                    "شهریور",
                    "مهر",
                    "آبان",
                    "آذر",
                    "دی",
                    "بهمن",
                    "اسفند",
                ),
                digits = "۰۱۲۳۴۵۶۷۸۹",
            )
    }
}
