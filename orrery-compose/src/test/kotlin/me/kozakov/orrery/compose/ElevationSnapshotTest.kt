package me.kozakov.orrery.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.Selection
import me.kozakov.orrery.core.SelectionMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class ElevationSnapshotTest {
    @get:Rule val rule = createComposeRule()

    private fun content() {
        rule.setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                Surface {
                    val selection =
                        rememberCalendarSelectionState(
                            mode = SelectionMode.Single(),
                            initialSelection = Selection(single = LocalDate(2026, 7, 15)),
                        )
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
                                selectionState = selection,
                                today = LocalDate(2026, 7, 15),
                                animateSelection = false,
                                selectedElevation = 4.dp,
                            )
                        },
                    )
                }
            }
        }
    }

    @Test
    fun elevatedSelectionLight() {
        content()
        rule.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "+night")
    fun elevatedSelectionDark() {
        content()
        rule.onRoot().captureRoboImage()
    }
}
