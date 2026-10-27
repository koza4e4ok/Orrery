package me.kozakov.orrery.holidays.rules

/** Holiday names: English plus the local language. */
internal fun names(
    en: String,
    vararg local: Pair<String, String>,
): Map<String, String> = mapOf("en" to en, *local)

/** Open end of a rule's year range. */
internal const val FOREVER: Int = Int.MAX_VALUE
