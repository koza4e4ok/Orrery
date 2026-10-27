package me.kozakov.orrery.compose

import android.text.format.DateFormat
import java.util.Locale

internal actual fun localizedDatePattern(locale: Locale): String = DateFormat.getBestDateTimePattern(locale, "yyyyMMdd")

internal actual fun localeUses24HourClock(locale: Locale): Boolean =
    patternUses24HourClock(DateFormat.getBestDateTimePattern(locale, "jm"))
