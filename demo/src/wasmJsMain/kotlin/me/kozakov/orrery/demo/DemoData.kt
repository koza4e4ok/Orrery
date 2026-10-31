package me.kozakov.orrery.demo

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.DayInfo
import me.kozakov.orrery.core.DayInfoPriority
import me.kozakov.orrery.core.DayInfoProvider
import me.kozakov.orrery.core.EventSpan
import me.kozakov.orrery.holidays.PublicHolidayDayInfoProvider
import me.kozakov.orrery.holidays.PublicHolidays

private const val WORK = 0xFF64B5F6L
private const val PERSONAL = 0xFFE57373L
private const val HEALTH = 0xFF81C784L
private const val MONEY = 0xFF26A69AL
private const val TRAVEL = 0xFFFFB74DL
private const val TEAM = 0xFF9575CDL

/** Days of event data on each side of today. */
internal const val DATA_DAYS = 60

/** Working hours shown by the Day / Week schedule. */
internal const val WORK_DAY_START_HOUR = 7
internal const val WORK_DAY_END_HOUR = 21

internal val WORK_WEEK =
    setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
internal val ALL_DAYS = DayOfWeek.entries.toSet()

/** A multi-day all-day event, [startOffset] days from today, lasting [days] days. */
private class MultiDayEvent(
    val id: String,
    val title: String,
    val startOffset: Int,
    val days: Int,
    val color: Long,
)

private val multiDayEvents =
    listOf(
        MultiDayEvent("offsite", "Product offsite", -12, 3, TEAM),
        MultiDayEvent("berlin", "Berlin trip", -4, 4, TRAVEL),
        MultiDayEvent("hackathon", "Hackathon", 1, 2, TEAM),
        MultiDayEvent("family", "Family visit", 3, 5, PERSONAL),
        MultiDayEvent("conf", "KotlinConf", 9, 3, WORK),
        MultiDayEvent("lisbon", "Lisbon vacation", 15, 6, TRAVEL),
        MultiDayEvent("workshop", "Design workshop", 17, 2, TEAM),
        MultiDayEvent("audit", "Quarterly audit", 24, 3, MONEY),
    )

internal fun buildDemoEvents(today: LocalDate): List<CalendarEvent<Nothing>> {
    val events = mutableListOf<CalendarEvent<Nothing>>()
    for (offset in -DATA_DAYS..DATA_DAYS) {
        val date = today.plus(offset, DateTimeUnit.DAY)

        fun timed(
            id: String,
            startMinutes: Int,
            durationMinutes: Int,
            title: String,
            color: Long,
            subtitle: String? = null,
        ) {
            val end = startMinutes + durationMinutes
            events +=
                CalendarEvent<Nothing>(
                    id = "$date-$id",
                    title = title,
                    span =
                        EventSpan.Timed(
                            start = LocalDateTime(date, LocalTime(startMinutes / 60, startMinutes % 60)),
                            end = LocalDateTime(date, LocalTime(end / 60, end % 60)),
                        ),
                    subtitle = subtitle,
                    colorArgb = color,
                )
        }
        val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
        if (!weekend) timed("standup", 9 * 60 + 30, 30, "Team standup", WORK)
        when (((offset % 4) + 4) % 4) {
            0 -> {
                timed("run", 7 * 60, 45, "Morning run", HEALTH, "Riverside park")
                timed("review", 9 * 60 + 45, 90, "Design review", WORK, "Room 302")
                timed("coffee", 10 * 60 + 15, 45, "Coffee chat", PERSONAL)
                timed("lunch", 12 * 60 + 30, 60, "Lunch with Sam", PERSONAL)
            }

            1 -> {
                timed("interview", 13 * 60 + 30, 60, "Interview", TEAM)
                timed("dentist", 14 * 60, 60, "Dentist", HEALTH)
                timed("book", 19 * 60 + 30, 90, "Book club", PERSONAL)
            }

            2 -> {
                events +=
                    CalendarEvent<Nothing>(
                        id = "$date-planning",
                        title = "Sprint planning",
                        span = EventSpan.AllDay(startDate = date),
                        colorArgb = WORK,
                    )
                timed("oneonone", 11 * 60, 45, "1:1 with manager", WORK)
                timed("sync", 11 * 60 + 15, 60, "Roadmap sync", TEAM)
                timed("gym", 18 * 60, 60, "Gym", HEALTH)
            }

            else -> {
                Unit
            }
        }
    }
    multiDayEvents.forEach { event ->
        val start = today.plus(event.startOffset, DateTimeUnit.DAY)
        events +=
            CalendarEvent<Nothing>(
                id = event.id,
                title = event.title,
                span = EventSpan.AllDay(startDate = start, endDate = start.plus(event.days - 1, DateTimeUnit.DAY)),
                colorArgb = event.color,
            )
    }
    return events
}

private val holidays = PublicHolidayDayInfoProvider(checkNotNull(PublicHolidays.forCountry("US")))

private val paydays =
    object : DayInfoProvider {
        override fun info(date: LocalDate): DayInfo? =
            if (date.day == 1 || date.day == 15) {
                DayInfo(label = "Payday", priority = DayInfoPriority.SECONDARY, colorArgb = MONEY)
            } else {
                null
            }
    }

internal val dayInfo = holidays + paydays

/** Nightly price shown on the Stays tab: weekend and holiday nights cost more. */
internal fun nightlyPrice(date: LocalDate): Int {
    val weekend = date.dayOfWeek == DayOfWeek.FRIDAY || date.dayOfWeek == DayOfWeek.SATURDAY
    val base = if (weekend) 149 else 109
    return if (holidays.info(date) != null) base + 40 else base
}
