package me.kozakov.orrery.calendars

import com.ibm.icu.util.Calendar
import com.ibm.icu.util.IslamicCalendar
import com.ibm.icu.util.PersianCalendar
import com.ibm.icu.util.TimeZone
import com.ibm.icu.util.ULocale
import kotlinx.datetime.LocalDate
import me.kozakov.orrery.calendars.hijri.HijriDate
import me.kozakov.orrery.calendars.hijri.HijriVariant
import me.kozakov.orrery.calendars.hijri.toHijriDate
import me.kozakov.orrery.calendars.hijri.toLocalDate
import me.kozakov.orrery.calendars.persian.PersianDate
import me.kozakov.orrery.calendars.persian.toLocalDate
import me.kozakov.orrery.calendars.persian.toPersianDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * JVM-only exhaustive checks against independent implementations used as test oracles:
 * java.time's HijrahDate (Umm al-Qura) and ICU4J's civil IslamicCalendar and PersianCalendar.
 * Common tests cover known dates, range ends and a ten-year daily window on all targets.
 */
class CalendarOraclesTest {
    private fun forEachDay(
        first: LocalDate,
        last: LocalDate,
        check: (LocalDate) -> String?,
    ) {
        val failures = mutableListOf<String>()
        var count = 0
        for (epochDay in first.toEpochDays()..last.toEpochDays()) {
            check(LocalDate.fromEpochDays(epochDay))?.let { failures += it }
            count++
        }
        assertTrue(failures.isEmpty(), "${failures.size} of $count days differ, first: ${failures.take(MAX_REPORTED)}")
    }

    @Test
    fun ummAlQuraMatchesJavaTimeEveryDay() =
        forEachDay(LocalDate(1882, 11, 12), LocalDate(2174, 11, 25)) { date ->
            val oracle = HijrahDate.from(java.time.LocalDate.ofEpochDay(date.toEpochDays()))
            val expected =
                HijriDate(
                    oracle.get(ChronoField.YEAR),
                    oracle.get(ChronoField.MONTH_OF_YEAR),
                    oracle.get(ChronoField.DAY_OF_MONTH),
                )
            val actual = date.toHijriDate()
            when {
                actual != expected -> "$date: $actual, java.time $expected"
                actual.toLocalDate() != date -> "$date: round trip ${actual.toLocalDate()}"
                else -> null
            }
        }

    @Test
    fun tabularMatchesIcuCivilCalendar1800To2200() {
        val icu = IslamicCalendar(TimeZone.GMT_ZONE, ULocale.ENGLISH)
        icu.calculationType = IslamicCalendar.CalculationType.ISLAMIC_CIVIL
        forEachDay(LocalDate(1800, 1, 1), LocalDate(2200, 12, 31)) { date ->
            icu.timeInMillis = date.toEpochDays() * MILLIS_PER_DAY
            val expected =
                HijriDate(
                    icu.get(Calendar.EXTENDED_YEAR),
                    icu.get(Calendar.MONTH) + 1,
                    icu.get(Calendar.DAY_OF_MONTH),
                    HijriVariant.Tabular,
                )
            val actual = date.toHijriDate(HijriVariant.Tabular)
            when {
                actual != expected -> "$date: $actual, ICU $expected"
                actual.toLocalDate() != date -> "$date: round trip ${actual.toLocalDate()}"
                else -> null
            }
        }
    }

    @Test
    fun persianMatchesIcuPersianCalendar1300To1499() {
        val icu = PersianCalendar(TimeZone.GMT_ZONE, ULocale.ENGLISH)
        forEachDay(LocalDate(1921, 3, 21), LocalDate(2121, 3, 20)) { date ->
            icu.timeInMillis = date.toEpochDays() * MILLIS_PER_DAY
            val expected =
                PersianDate(
                    icu.get(Calendar.EXTENDED_YEAR),
                    icu.get(Calendar.MONTH) + 1,
                    icu.get(Calendar.DAY_OF_MONTH),
                )
            val actual = date.toPersianDate()
            when {
                actual != expected -> "$date: $actual, ICU $expected"
                actual.toLocalDate() != date -> "$date: round trip ${actual.toLocalDate()}"
                else -> null
            }
        }
    }

    @Test
    fun persianRoundTripsOverTheWholeSupportedRange() =
        forEachDay(LocalDate(560, 3, 20), LocalDate(3798, 3, 20)) { date ->
            val persian = date.toPersianDate()
            if (persian.toLocalDate() != date) "$date: $persian round trip ${persian.toLocalDate()}" else null
        }

    @Test
    fun ummAlQuraRangeCoversWholeYears() {
        assertEquals(HijriDate(1300, 1, 1), LocalDate(1882, 11, 12).toHijriDate())
        assertEquals(HijriDate(1600, 12, 30), LocalDate(2174, 11, 25).toHijriDate())
    }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
        const val MAX_REPORTED = 20
    }
}
