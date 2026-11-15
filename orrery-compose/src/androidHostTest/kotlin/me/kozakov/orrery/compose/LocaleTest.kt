package me.kozakov.orrery.compose

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class LocaleTest {
    @Test
    fun firstDayOfWeekFollowsLocale() {
        assertEquals(DayOfWeek.SUNDAY, firstDayOfWeekFromLocale(Locale.US))
        assertEquals(DayOfWeek.MONDAY, firstDayOfWeekFromLocale(Locale.GERMANY))
    }

    @Test
    fun displayNamesAreLocalized() {
        assertEquals("Mon", DayOfWeek.MONDAY.displayName(locale = Locale.US))
        assertEquals("M", DayOfWeek.MONDAY.displayName(narrow = true, locale = Locale.US))
        assertEquals("July 2026", YearMonth(2026, 7).displayName(Locale.US))
    }

    @Test
    fun monthTitlesStartWithACapital() {
        // Ukrainian writes the standalone month in lower case ("листопад").
        assertEquals("Листопад 2026", YearMonth(2026, 11).displayName(Locale.forLanguageTag("uk-UA")))
        assertEquals("November 2026", YearMonth(2026, 11).displayName(Locale.US))
    }

    @Test
    fun dayHeadersUseTheLocaleOrder() {
        val date = LocalDate(2026, 11, 14)
        assertEquals("Sat, Nov 14", dayHeader(date, "en-US"))
        // Not the old fixed "EEE, MMM d" order, which gave "сб, лист. 14".
        assertEquals("сб, 14 лист.", dayHeader(date, "uk-UA"))
    }
}
