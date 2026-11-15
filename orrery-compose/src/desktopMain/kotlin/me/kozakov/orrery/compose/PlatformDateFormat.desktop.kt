package me.kozakov.orrery.compose

import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The JDK has no skeleton API like Android's getBestDateTimePattern, so take the
 * locale's SHORT date pattern (field order and separators) and widen its numeric
 * fields to MM, dd and yyyy.
 */
internal actual fun localizedDatePattern(locale: Locale): String {
    val short =
        DateTimeFormatterBuilder.getLocalizedDateTimePattern(
            FormatStyle.SHORT,
            null,
            IsoChronology.INSTANCE,
            locale,
        )
    return normalizeInputPattern(short)
}

/**
 * Without a skeleton API, derive `EEEMMMd` from the locale's LONG date pattern: drop the
 * year with its separators, abbreviate the month and put the short weekday in front, so
 * "MMMM d, y" (en-US) becomes "EEE, MMM d" and "d MMMM y 'р'." (uk) becomes "EEE, d MMM".
 * Locales that write the weekday after the date (ja: "M月d日(EEE)") still get it in front.
 */
internal actual fun localeDayHeaderPattern(locale: Locale): String {
    val long =
        DateTimeFormatterBuilder.getLocalizedDateTimePattern(
            FormatStyle.LONG,
            null,
            IsoChronology.INSTANCE,
            locale,
        )
    return "EEE, ${abbreviatedMonthDayPattern(long)}"
}

/**
 * [pattern] without its year field and the literals that separate the year from the
 * rest, with a full month (MMMM or LLLL) abbreviated: "d MMMM y 'р'." becomes "d MMM",
 * "y年M月d日" becomes "M月d日". Quoted literals are kept whole.
 */
internal fun abbreviatedMonthDayPattern(pattern: String): String {
    val tokens = datePatternParts(pattern)
    val year = tokens.indexOfFirst { it.isField && it.text[0] in YEAR_FIELDS }
    val kept =
        if (year < 0) {
            tokens
        } else {
            var from = year
            var to = year + 1
            while (to < tokens.size && !tokens[to].isField) to++
            // A trailing year takes the separator before it too ("MMMM d, y").
            if (to == tokens.size) {
                while (from > 0 && !tokens[from - 1].isField) from--
            }
            tokens.subList(0, from) + tokens.subList(to, tokens.size)
        }
    return kept
        .joinToString("") { if (it.isField && it.text in FULL_MONTH_FIELDS) it.text.dropLast(1) else it.text }
        .trim()
}

private const val YEAR_FIELDS = "yYu"
private val FULL_MONTH_FIELDS = setOf("MMMM", "LLLL")

private class DatePatternPart(
    val text: String,
    val isField: Boolean,
)

/** Splits a date pattern into runs of one ASCII pattern letter, quoted literals and other literal text. */
private fun datePatternParts(pattern: String): List<DatePatternPart> {
    val tokens = ArrayList<DatePatternPart>()
    var index = 0
    while (index < pattern.length) {
        val char = pattern[index]
        val end =
            when {
                char == '\'' -> {
                    var close = index + 1
                    // '' inside a quoted literal is an escaped quote.
                    while (close < pattern.length &&
                        (pattern[close] != '\'' || pattern.getOrNull(close + 1) == '\'')
                    ) {
                        close += if (pattern[close] == '\'') 2 else 1
                    }
                    minOf(close + 1, pattern.length)
                }

                char.isAsciiLetter() -> {
                    var next = index + 1
                    while (next < pattern.length && pattern[next] == char) next++
                    next
                }

                else -> {
                    var next = index + 1
                    while (next < pattern.length && pattern[next] != '\'' && !pattern[next].isAsciiLetter()) next++
                    next
                }
            }
        tokens += DatePatternPart(pattern.substring(index, end), isField = char.isAsciiLetter())
        index = end
    }
    return tokens
}

private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

internal actual fun localeUses24HourClock(locale: Locale): Boolean = patternUses24HourClock(shortTimePattern(locale))

/** JDK 17 has no skeleton API either, so rewrite the SHORT time pattern when it is on the other clock. */
internal actual fun localeTimePattern(
    locale: Locale,
    is24Hour: Boolean,
): String = timePatternForClock(shortTimePattern(locale), is24Hour)

/** The locale's SHORT time pattern, e.g. "h:mm a" for en-US or "HH:mm" for de-DE. */
private fun shortTimePattern(locale: Locale): String =
    DateTimeFormatterBuilder.getLocalizedDateTimePattern(
        null,
        FormatStyle.SHORT,
        IsoChronology.INSTANCE,
        locale,
    )
