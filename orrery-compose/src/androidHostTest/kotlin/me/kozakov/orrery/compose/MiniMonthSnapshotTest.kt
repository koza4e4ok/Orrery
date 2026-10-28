package me.kozakov.orrery.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.DayInfo
import me.kozakov.orrery.core.Selection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden images for the today, selection and holiday highlights of [CalendarDefaults.MiniMonth]. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class MiniMonthSnapshotTest {
    @get:Rule val rule = createComposeRule()

    private val today = LocalDate(2026, 7, 15)

    // A range across the March/April boundary plus two multi dates in August.
    private val selection =
        Selection(
            rangeStart = LocalDate(2026, 3, 26),
            rangeEnd = LocalDate(2026, 4, 8),
            multi = setOf(LocalDate(2026, 8, 3), LocalDate(2026, 8, 5)),
        )

    private val holidays =
        setOf(
            LocalDate(2026, 1, 1),
            LocalDate(2026, 4, 3),
            LocalDate(2026, 5, 1),
            LocalDate(2026, 7, 15),
            LocalDate(2026, 8, 24),
            LocalDate(2026, 12, 25),
        )

    private val dayInfo: (LocalDate) -> DayInfo? = { date ->
        if (date in holidays) DayInfo(label = null, isHoliday = true) else null
    }

    private fun setThemedContent(
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        content: @Composable () -> Unit,
    ) {
        rule.setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                    Surface { content() }
                }
            }
        }
    }

    private fun year(layoutDirection: LayoutDirection = LayoutDirection.Ltr) {
        setThemedContent(layoutDirection) {
            YearCalendar(
                state = rememberYearCalendarState(startYear = 2026, endYear = 2026, firstVisibleYear = 2026),
                onMonthClick = {},
                monthContent = { month ->
                    CalendarDefaults.MiniMonth(month, today = today, selection = selection, dayInfo = dayInfo)
                },
            )
        }
    }

    @Test
    fun miniMonthHighlightsLight() {
        year()
        rule.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "+night")
    fun miniMonthHighlightsDark() {
        year()
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun miniMonthHighlightsRtl() {
        year(LayoutDirection.Rtl)
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun miniMonthRangeCloseUp() {
        setThemedContent {
            Surface(Modifier.width(220.dp)) {
                CalendarDefaults.MiniMonth(
                    YearMonth(2026, 3),
                    today = LocalDate(2026, 3, 12),
                    selection = Selection(rangeStart = LocalDate(2026, 3, 18), rangeEnd = LocalDate(2026, 3, 25)),
                    dayInfo = { date -> DayInfo(label = null, isHoliday = date.day == 20) },
                )
            }
        }
        rule.onRoot().captureRoboImage()
    }
}
