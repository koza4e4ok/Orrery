package me.kozakov.orrery.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.DisabledDates
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Two-month layout of [OrreryDateRangePickerDialog] on a tablet-sized window. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = TABLET_WINDOW)
class DualMonthRangePickerSnapshotTest {
    @get:Rule val rule = createComposeRule()

    private val today = LocalDate(2026, 7, 15)

    @Suppress("LongParameterList")
    private fun content(
        start: LocalDate? = LocalDate(2026, 7, 8),
        end: LocalDate? = LocalDate(2026, 7, 12),
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        displayMode: DatePickerDisplayMode = DatePickerDisplayMode.Picker,
        withPresets: Boolean = false,
        disabledDates: DisabledDates = DisabledDates.None,
        minRangeDays: Int = 1,
    ) {
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                MaterialTheme(
                    colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
                ) {
                    val state =
                        rememberOrreryDateRangePickerState(
                            initialStartDate = start,
                            initialEndDate = end,
                            initialDisplayedMonth = YearMonth(2026, 7),
                            yearRange = 2026..2026,
                            initialDisplayMode = displayMode,
                            disabledDates = disabledDates,
                            firstDayOfWeek = DayOfWeek.MONDAY,
                            disallowDisabledInRange = disabledDates !== DisabledDates.None,
                            minRangeDays = minRangeDays,
                        )
                    OrreryDateRangePickerDialog(
                        onDismissRequest = {},
                        confirmButton = { TextButton(onClick = {}) { Text("Save") } },
                        state = state,
                        today = today,
                        presets =
                            if (withPresets) {
                                DatePickerDefaults.rangePresets(today, DayOfWeek.MONDAY)
                            } else {
                                emptyList()
                            },
                    )
                }
            }
        }
    }

    @Test
    fun dualMonthLight() {
        content()
        rule.onNode(isDialog()).captureRoboImage()
    }

    @Test
    @Config(qualifiers = "+night")
    fun dualMonthDark() {
        content()
        rule.onNode(isDialog()).captureRoboImage()
    }

    @Test
    fun dualMonthRtl() {
        content(end = LocalDate(2026, 8, 6), layoutDirection = LayoutDirection.Rtl)
        rule.onNode(isDialog()).captureRoboImage()
    }

    @Test
    fun dualMonthRangeAcrossMonthsLight() {
        content(start = LocalDate(2026, 7, 28), end = LocalDate(2026, 8, 6))
        rule.onNode(isDialog()).captureRoboImage()
    }

    @Test
    fun dualMonthHoverPreviewLight() {
        content(start = LocalDate(2026, 7, 28), end = null)
        rule
            .onNode(hasContentDescription("August 6, 2026", substring = true))
            .performMouseInput { moveTo(center) }
        rule.waitForIdle()
        rule.onNode(isDialog()).captureRoboImage()
    }

    @Test
    fun dualMonthWithPresetsLight() {
        content(withPresets = true)
        rule.onNode(isDialog()).captureRoboImage()
    }

    @Test
    fun dualMonthInputLight() {
        content(displayMode = DatePickerDisplayMode.Input)
        rule.onNode(isDialog()).captureRoboImage()
    }

    @Test
    fun dualMonthGuardrailsLight() {
        content(
            start = LocalDate(2026, 7, 30),
            end = LocalDate(2026, 8, 4),
            disabledDates =
                DisabledDates {
                    range(LocalDate(2026, 7, 20)..LocalDate(2026, 7, 23))
                    range(LocalDate(2026, 8, 10)..LocalDate(2026, 8, 12))
                    daysOfWeek(DayOfWeek.SUNDAY)
                },
            minRangeDays = 3,
        )
        rule.onNode(isDialog()).captureRoboImage()
    }

    /** The default layout on a phone is still the full-screen column of months. */
    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
    fun singleMonthPhoneLight() {
        content()
        rule.onNode(isDialog()).captureRoboImage()
    }
}

private const val TABLET_WINDOW = "w1024dp-h768dp-xhdpi"
