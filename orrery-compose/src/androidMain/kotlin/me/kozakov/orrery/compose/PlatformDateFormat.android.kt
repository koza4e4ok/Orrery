package me.kozakov.orrery.compose

import android.text.format.DateFormat
import java.util.Locale

internal actual fun localizedDatePattern(locale: Locale): String = DateFormat.getBestDateTimePattern(locale, "yyyyMMdd")
