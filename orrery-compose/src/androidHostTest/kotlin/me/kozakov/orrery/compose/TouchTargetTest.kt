package me.kozakov.orrery.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assertTouchHeightIsAtLeast
import androidx.compose.ui.test.assertTouchWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Material's minimum touch target: interactive controls in the default dialogs are at least this big. */
private val MinTouchTarget = 48.dp

// Native graphics for real text metrics, so the sizes match a device.
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class TouchTargetTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun navHeaderTitleAndChevronsAreAtLeast48dp() {
        rule.setContent {
            MaterialTheme {
                CalendarNavHeader(
                    state =
                        rememberCalendarState(
                            startMonth = YearMonth(2026, 7),
                            endMonth = YearMonth(2026, 9),
                            firstVisibleMonth = YearMonth(2026, 8),
                            firstDayOfWeek = DayOfWeek.MONDAY,
                        ),
                    onTitleClick = {},
                )
            }
        }
        // Previous chevron, month/year title button, next chevron.
        rule.onAllNodes(hasClickAction()).assertEachAtLeast(MinTouchTarget, expectedCount = 3)
    }

    @Test
    fun dateTimeStepPillsAreAtLeast48dp() {
        rule.setContent {
            MaterialTheme {
                OrreryDateTimePickerDialog(
                    onDismissRequest = {},
                    confirmButton = { TextButton(onClick = {}) { Text("OK") } },
                    state =
                        rememberOrreryDateTimePickerState(
                            initialDateTime = LocalDateTime(2026, 7, 28, 21, 45),
                            bounds = LocalDate(2026, 7, 1)..LocalDate(2026, 7, 31),
                            is24Hour = false,
                        ),
                    today = LocalDate(2026, 7, 15),
                )
            }
        }
        // The Date and Time step pills in the headline.
        rule
            .onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .assertEachAtLeast(MinTouchTarget, expectedCount = 2)
    }

    private fun SemanticsNodeInteractionCollection.assertEachAtLeast(
        min: Dp,
        expectedCount: Int,
    ) {
        val count = fetchSemanticsNodes().size
        assertEquals("interactive nodes", expectedCount, count)
        // Touch bounds: the layout bounds plus the extension Compose gives to small targets
        // (a 40 dp IconButton still takes touches over 48 dp).
        repeat(count) { index -> get(index).assertTouchHeightIsAtLeast(min).assertTouchWidthIsAtLeast(min) }
    }
}
