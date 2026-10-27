package me.kozakov.orrery.compose

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

internal actual fun localizedDatePattern(locale: Locale): String = DateFormat.getBestDateTimePattern(locale, "yyyyMMdd")

internal actual fun localeUses24HourClock(locale: Locale): Boolean =
    patternUses24HourClock(DateFormat.getBestDateTimePattern(locale, "jm"))

/** Skeleton "Hm" or "hm": ICU picks the locale's separators and AM/PM placement ("h:mm a", "ah:mm"). */
internal actual fun localeTimePattern(
    locale: Locale,
    is24Hour: Boolean,
): String = DateFormat.getBestDateTimePattern(locale, if (is24Hour) "Hm" else "hm")

/** The system "Use 24-hour format" setting, which falls back to the locale when unset (as M3's TimePicker does). */
@Composable
internal actual fun systemUses24HourClock(): Boolean = DateFormat.is24HourFormat(LocalContext.current)
