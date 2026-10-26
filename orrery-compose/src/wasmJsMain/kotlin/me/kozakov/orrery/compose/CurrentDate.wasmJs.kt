package me.kozakov.orrery.compose

import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

// Reads the browser's local date directly: kotlinx-datetime on Wasm has no time zone
// rules for the system zone unless the app bundles kotlinx-datetime-zoneinfo.
public actual fun currentDate(): LocalDate {
    val packed = jsTodayPacked()
    return LocalDate(packed / 10_000, packed / 100 % 100, packed % 100)
}

public actual fun currentYearMonth(): YearMonth = currentDate().let { YearMonth(it.year, it.month) }

private fun jsTodayPacked(): Int =
    js("{ const d = new Date(); return d.getFullYear() * 10000 + (d.getMonth() + 1) * 100 + d.getDate(); }")
