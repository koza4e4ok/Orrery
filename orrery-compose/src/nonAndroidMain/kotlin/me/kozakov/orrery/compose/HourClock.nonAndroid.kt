package me.kozakov.orrery.compose

import androidx.compose.runtime.Composable

/** The current locale's clock; on iOS NSLocale.currentLocale already reflects the 24-hour time setting. */
@Composable
internal actual fun systemUses24HourClock(): Boolean = PlatformDateFormat.is24HourClock()
