@file:OptIn(ExperimentalOrreryApi::class)

package me.kozakov.orrery.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.ExperimentalOrreryApi
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.DayInfo
import me.kozakov.orrery.core.DayInfoProvider
import me.kozakov.orrery.core.EventSpan
import me.kozakov.orrery.core.Selection
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/**
 * Golden images of the Android renderer (renderCalendarSnapshot with a Context). The matrix
 * uses the static Material 3 light and dark schemes picked by the night qualifier, so the
 * images do not depend on Robolectric's wallpaper colours; dynamicColors* covers the
 * API 31+ default.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class CalendarSnapshotGoldenTest {
    private val today = LocalDate(2026, 11, 14)
    private val november = CalendarSnapshotRange.Month(YearMonth(2026, 11))
    private val agenda = CalendarSnapshotRange.Agenda(today, days = 7, maxRows = 6)

    private val holidays =
        object : DayInfoProvider {
            override fun info(date: LocalDate): DayInfo? =
                when (date) {
                    LocalDate(2026, 11, 11) -> DayInfo(label = null, isHoliday = true)
                    LocalDate(2026, 11, 26) -> DayInfo(label = null, isHoliday = true)
                    LocalDate(2026, 11, 20) -> DayInfo(label = null, colorArgb = 0xFFC62828)
                    else -> null
                }
        }

    private val events =
        listOf(
            CalendarEvent<Nothing>("1", "Design review", EventSpan.Timed(at(14, 10), at(14, 11)), colorArgb = PURPLE),
            CalendarEvent<Nothing>("2", "Weekend trip", EventSpan.AllDay(today, LocalDate(2026, 11, 15))),
            CalendarEvent<Nothing>("3", "Stand-up", EventSpan.Timed(at(16, 9, 30), at(16, 9, 45))),
            CalendarEvent<Nothing>(
                "4",
                "A very long event title that never fits on one line of a small widget",
                EventSpan.Timed(at(16, 14), at(16, 15)),
                colorArgb = 0xFF2E7D32,
            ),
            CalendarEvent<Nothing>("5", "Dentist", EventSpan.Timed(at(18, 8), at(18, 9)), colorArgb = 0xFFEF6C00),
        )

    private val marks =
        CalendarSnapshotSpec(
            range = november,
            today = today,
            selection = Selection(rangeStart = LocalDate(2026, 11, 17), rangeEnd = LocalDate(2026, 11, 21)),
            dayInfo = holidays,
            eventCounts = mapOf(today to 1, LocalDate(2026, 11, 18) to 2, LocalDate(2026, 11, 24) to 5),
            showOutsideDays = true,
        )

    private val month = CalendarSnapshotSpec(range = november, today = today, eventCounts = mapOf(today to 2))
    private val week =
        CalendarSnapshotSpec(
            range = CalendarSnapshotRange.Week(today),
            today = today,
            selection = Selection(single = LocalDate(2026, 11, 12)),
            eventCounts = mapOf(LocalDate(2026, 11, 10) to 3),
        )
    private val agendaStrip = CalendarSnapshotSpec(range = agenda, today = today, agendaEvents = events)

    @Test
    fun monthLight() = capture(month, 250, 250)

    @Test
    @Config(qualifiers = "+night")
    fun monthDark() = capture(month, 250, 250)

    @Test
    fun weekLight() = capture(week, 250, 110)

    @Test
    @Config(qualifiers = "+night")
    fun weekDark() = capture(week, 250, 110)

    @Test
    fun agendaLight() = capture(agendaStrip, 250, 250)

    @Test
    @Config(qualifiers = "+night")
    fun agendaDark() = capture(agendaStrip, 250, 250)

    @Test
    fun selectionRangeHolidaysAndDots() = capture(marks, 250, 250)

    @Test
    @Config(qualifiers = "+night")
    fun selectionRangeHolidaysAndDotsDark() = capture(marks, 250, 250)

    @Test
    fun widget2x2() = capture(marks, 110, 110)

    @Test
    fun widget4x2() = capture(marks, 250, 110)

    @Test
    fun agendaWidget4x2() = capture(agendaStrip, 250, 110)

    @Test
    @Config(qualifiers = "+ldrtl")
    fun monthRtl() = capture(marks, 250, 250)

    @Test
    @Config(qualifiers = "+ldrtl")
    fun agendaRtl() = capture(agendaStrip, 250, 180)

    @Test
    @Config(fontScale = 1.3f)
    fun widget4x2FontScale13() = capture(marks, 250, 110)

    @Test
    @Config(fontScale = 2.0f)
    fun widget4x2FontScale20() = capture(marks, 250, 110)

    @Test
    @Config(fontScale = 1.3f)
    fun agendaFontScale13() = capture(agendaStrip, 250, 180)

    @Test
    @Config(fontScale = 2.0f)
    fun agendaFontScale20() = capture(agendaStrip, 250, 180)

    @Test
    @Config(qualifiers = "+uk")
    fun ukrainianTitles() = capture(month, 250, 250)

    @Test
    @Config(qualifiers = "+uk")
    fun ukrainianAgenda() = capture(agendaStrip, 250, 180)

    @Test
    fun dynamicColorsLight() = capture(marks, 250, 250, staticColors = false)

    @Test
    @Config(qualifiers = "+night")
    fun dynamicColorsDark() = capture(marks, 250, 250, staticColors = false)

    private fun capture(
        spec: CalendarSnapshotSpec,
        widthDp: Int,
        heightDp: Int,
        staticColors: Boolean = true,
    ) {
        val context: Context = RuntimeEnvironment.getApplication()
        val density = context.resources.displayMetrics.density
        val width = (widthDp * density).roundToInt()
        val height = (heightDp * density).roundToInt()
        val bitmap =
            runBlocking {
                if (staticColors) {
                    renderCalendarSnapshot(context, spec, width, height, staticColors(context))
                } else {
                    renderCalendarSnapshot(context, spec, width, height)
                }
            }
        bitmap.asAndroidBitmap().captureRoboImage()
    }

    private fun staticColors(context: Context): CalendarSnapshotColors {
        val night =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        return CalendarSnapshotDefaults.colors(if (night) darkColorScheme() else lightColorScheme())
    }

    private fun at(
        day: Int,
        hour: Int,
        minute: Int = 0,
    ) = LocalDateTime(2026, 11, day, hour, minute)

    private companion object {
        const val PURPLE = 0xFF6A1B9AL
    }
}
