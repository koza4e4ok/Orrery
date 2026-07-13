package me.kozakov.orrery.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
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
class DecoratorsSnapshotTest {
    @get:Rule val rule = createComposeRule()

    private val dotPalette =
        listOf(Color(0xFFE57373), Color(0xFF64B5F6), Color(0xFF81C784))

    private fun setThemedContent(
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        content: @Composable () -> Unit,
    ) {
        rule.setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                Surface {
                    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                        content()
                    }
                }
            }
        }
    }

    @Suppress("TestFunctionName")
    @Composable
    private fun DecoratedCalendar(decorators: List<DayDecorator>) {
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
                    decorators = decorators,
                )
            },
        )
    }

    private fun dotColors(date: LocalDate): List<Color> = dotPalette.take(date.day % 4)

    @Test
    fun eventDots() {
        setThemedContent {
            DecoratedCalendar(listOf(DayDecorators.eventDots(colors = ::dotColors)))
        }
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun progressRing() {
        setThemedContent {
            DecoratedCalendar(
                listOf(DayDecorators.progressRing { date -> (date.day % 11) / 10f }),
            )
        }
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun progressBar() {
        setThemedContent {
            DecoratedCalendar(
                listOf(DayDecorators.progressBar { date -> (date.day % 11) / 10f }),
            )
        }
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun strikethrough() {
        setThemedContent {
            DecoratedCalendar(
                listOf(
                    DayDecorators.strikethrough { date ->
                        date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
                    },
                ),
            )
        }
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun underline() {
        setThemedContent {
            DecoratedCalendar(listOf(DayDecorators.underline { date -> date.day in 6..10 }))
        }
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun heatmap() {
        setThemedContent {
            DecoratedCalendar(listOf(DayDecorators.heatmap { date -> date.day / 31f }))
        }
        rule.onRoot().captureRoboImage()
    }

    @Suppress("TestFunctionName")
    @Composable
    private fun StackedCalendar() {
        DecoratedCalendar(
            listOf(
                DayDecorators.heatmap { date -> if (date.day % 3 == 0) date.day / 31f else null },
                DayDecorators.eventDots(colors = ::dotColors),
                DayDecorators.progressRing { date -> if (date.day % 5 == 0) 0.6f else null },
                DayDecorators.strikethrough { date -> date.dayOfWeek == DayOfWeek.SUNDAY },
            ),
        )
    }

    @Test
    fun stackedLight() {
        setThemedContent { StackedCalendar() }
        rule.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "+night")
    fun stackedDark() {
        setThemedContent { StackedCalendar() }
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun stackedRtl() {
        setThemedContent(layoutDirection = LayoutDirection.Rtl) { StackedCalendar() }
        rule.onRoot().captureRoboImage()
    }
}
