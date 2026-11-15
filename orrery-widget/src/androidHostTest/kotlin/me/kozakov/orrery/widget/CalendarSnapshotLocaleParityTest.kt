package me.kozakov.orrery.widget

import android.content.Context
import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * renderCalendarSnapshot(context, …) formats with the context's locale, the CalendarSnapshot
 * composable with the platform formatting of orrery-compose for the default locale. Both
 * must print the same day headings and titles (#136).
 */
@RunWith(AndroidJUnit4::class)
class CalendarSnapshotLocaleParityTest {
    private val date = LocalDate(2026, 11, 14)
    private val month = YearMonth(2026, 11)

    @Test
    fun englishTextMatches() = assertSameText("en-US", heading = "Sat, Nov 14", title = "November 2026")

    @Test
    fun ukrainianTextMatches() {
        assertSameText("uk-UA", heading = "сб, 14 лист.", title = "Листопад 2026")
        // The composable's old fixed "EEE, MMM d" pattern gave another order, so this test fails on it.
        val old = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.forLanguageTag("uk-UA"))
        assertNotEquals("сб, 14 лист.", old.format(java.time.LocalDate.of(2026, 11, 14)))
    }

    private fun assertSameText(
        languageTag: String,
        heading: String,
        title: String,
    ) {
        val locale = Locale.forLanguageTag(languageTag)
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        try {
            val app: Context = RuntimeEnvironment.getApplication()
            val configuration = Configuration(app.resources.configuration).apply { setLocale(locale) }
            val context = app.createConfigurationContext(configuration)
            // The formatters behind the two entry points.
            val renderer = AndroidSnapshotEnvironment.of(context).formatter()
            val composable = PlatformSnapshotFormatter()
            assertEquals(heading, renderer.dayHeading(date), "renderer heading")
            assertEquals(heading, composable.dayHeading(date), "composable heading")
            assertEquals(title, renderer.monthTitle(month), "renderer title")
            assertEquals(title, composable.monthTitle(month), "composable title")
        } finally {
            Locale.setDefault(previous)
        }
    }
}
