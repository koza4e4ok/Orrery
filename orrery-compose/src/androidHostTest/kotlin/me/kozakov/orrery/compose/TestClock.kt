package me.kozakov.orrery.compose

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider

/** Sets Android's "Use 24-hour format", which DateFormat.is24HourFormat (and so systemUses24HourClock) reads. */
internal fun setSystem24HourClock(on: Boolean) {
    val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
    Settings.System.putString(resolver, Settings.System.TIME_12_24, if (on) "24" else "12")
}
