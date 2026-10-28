package me.kozakov.orrery.calendars.hijri

/**
 * Labels used by [HijriDayInfoProvider]: [monthNames] holds the 12 month names from Muharram,
 * shown on the first day of each month; [digits] holds the ten digits 0..9 used for the other
 * days' numbers.
 */
public class HijriStrings(
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
        public val English: HijriStrings =
            HijriStrings(
                listOf(
                    "Muharram",
                    "Safar",
                    "Rabi I",
                    "Rabi II",
                    "Jumada I",
                    "Jumada II",
                    "Rajab",
                    "Sha'ban",
                    "Ramadan",
                    "Shawwal",
                    "Dhu al-Qi'dah",
                    "Dhu al-Hijjah",
                ),
            )

        public val Arabic: HijriStrings =
            HijriStrings(
                listOf(
                    "محرم",
                    "صفر",
                    "ربيع الأول",
                    "ربيع الآخر",
                    "جمادى الأولى",
                    "جمادى الآخرة",
                    "رجب",
                    "شعبان",
                    "رمضان",
                    "شوال",
                    "ذو القعدة",
                    "ذو الحجة",
                ),
                digits = "٠١٢٣٤٥٦٧٨٩",
            )
    }
}
