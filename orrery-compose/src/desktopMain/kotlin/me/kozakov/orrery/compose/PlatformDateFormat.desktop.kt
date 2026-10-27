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
