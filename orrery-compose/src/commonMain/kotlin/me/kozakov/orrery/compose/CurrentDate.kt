package me.kozakov.orrery.compose

import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/** Today in the system time zone. */
public expect fun currentDate(): LocalDate

/** The month containing [currentDate]. */
public expect fun currentYearMonth(): YearMonth
