package dev.koza4e4ok.material.calendar.compose

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.DayOfWeek
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
}
