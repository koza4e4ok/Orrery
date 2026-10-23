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
