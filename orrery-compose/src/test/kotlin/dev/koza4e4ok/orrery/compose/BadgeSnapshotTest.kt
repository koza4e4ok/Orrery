package dev.koza4e4ok.orrery.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class BadgeSnapshotTest {
    @get:Rule val rule = createComposeRule()

    private fun content(layoutDirection: LayoutDirection = LayoutDirection.Ltr) {
        rule.setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                Surface {
                    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                        BadgedCalendar()
                    }
                }
            }
        }
    }

    @Suppress("TestFunctionName")
    @Composable
    private fun BadgedCalendar() {
        val badges = listOf(DayDecorators.badge { date -> date.day % 12 })
        HorizontalCalendar(
            state =
                rememberCalendarState(
                    startMonth = YearMonth(2026, 7),
                    endMonth = YearMonth(2026, 7),
                    firstVisibleMonth = YearMonth(2026, 7),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                ),
            dayContent = { day ->
                DefaultDay(
                    day = day,
                    today = LocalDate(2026, 7, 15),
                    animateSelection = false,
                    decorators = badges,
                )
            },
        )
    }

    @Test
    fun badgeLight() {
        content()
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun badgeRtl() {
        content(layoutDirection = LayoutDirection.Rtl)
        rule.onRoot().captureRoboImage()
    }
}
