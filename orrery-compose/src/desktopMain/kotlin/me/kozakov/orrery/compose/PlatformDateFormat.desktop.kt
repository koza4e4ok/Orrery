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

/** The locale's SHORT time pattern, e.g. "h:mm a" for en-US or "HH:mm" for de-DE. */
internal actual fun localeUses24HourClock(locale: Locale): Boolean =
    patternUses24HourClock(
        DateTimeFormatterBuilder.getLocalizedDateTimePattern(
            null,
            FormatStyle.SHORT,
            IsoChronology.INSTANCE,
            locale,
        ),
    )
