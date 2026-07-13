package dev.koza4e4ok.orrery.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.datetime.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class PickerSnapshotTest {
    @get:Rule val rule = createComposeRule()

    private fun content() {
        rule.setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                Surface {
                    MonthYearPicker(
                        current = YearMonth(2026, 7),
                        range = YearMonth(2020, 1)..YearMonth(2030, 12),
                        onSelect = {},
                    )
                }
            }
        }
    }

    @Test
    fun pickerYears() {
        content()
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun pickerMonths() {
        content()
        rule.onNodeWithText("2026").performClick()
        rule.waitForIdle()
        rule.onRoot().captureRoboImage()
    }
}
