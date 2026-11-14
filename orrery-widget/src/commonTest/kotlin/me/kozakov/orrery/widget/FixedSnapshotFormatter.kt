package me.kozakov.orrery.widget

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.YearMonth

/** Locale-free date text for painter tests. */
internal object FixedSnapshotFormatter : SnapshotFormatter {
    override fun monthTitle(month: YearMonth): String = month.toString()

    override fun dateRange(
        start: LocalDate,
        end: LocalDate,
    ): String = "$start - $end"

    override fun weekdayLabel(dayOfWeek: DayOfWeek): String = dayOfWeek.name.take(2)

    override fun dayHeading(date: LocalDate): String = date.toString()

    override fun time(time: LocalTime): String = time.toString()
}
