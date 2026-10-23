package me.kozakov.orrery.core

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus

/** ISO-8601 week of year: weeks start Monday, week 1 contains January 4th. */
public fun LocalDate.isoWeekNumber(): Int {
    val thursday = plus(4 - dayOfWeek.isoDayNumber, DateTimeUnit.DAY)
    return (thursday.dayOfYear - 1) / 7 + 1
}
