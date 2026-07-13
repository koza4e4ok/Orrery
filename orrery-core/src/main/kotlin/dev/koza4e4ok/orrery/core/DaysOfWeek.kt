package dev.koza4e4ok.orrery.core

import kotlinx.datetime.DayOfWeek

/** The 7 days of the week starting at [firstDayOfWeek]. */
public fun daysOfWeek(firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY): List<DayOfWeek> =
    List(7) { DayOfWeek.entries[(firstDayOfWeek.ordinal + it) % 7] }
