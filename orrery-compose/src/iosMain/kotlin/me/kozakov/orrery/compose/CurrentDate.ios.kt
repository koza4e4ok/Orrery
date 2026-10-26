package me.kozakov.orrery.compose

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.todayIn
import kotlin.time.Clock

public actual fun currentDate(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

public actual fun currentYearMonth(): YearMonth = currentDate().let { YearMonth(it.year, it.month) }
