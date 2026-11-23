package me.kozakov.orrery.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertTrue
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
        rule.onAllNodes(hasClickAction()).assertCountEquals(3)
        val chevron = hasContentDescription("Previous month") or hasContentDescription("Next month")
        // The M3 chevron IconButtons are 40 dp; their 48 dp comes from Compose's touch-target extension.
        rule.onAllNodes(hasClickAction() and chevron).assertEachTouchBoundsAtLeast(MinTouchTarget, expectedCount = 2)
        // The month/year title button: its own layout is at least 48 dp, not only the touch extension.
        rule.onAllNodes(hasClickAction() and !chevron).assertEachLayoutAtLeast(MinTouchTarget, expectedCount = 1)
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
            .assertEachLayoutAtLeast(MinTouchTarget, expectedCount = 2)
    }

    /**
     * Layout size of the clickable node itself. Touch bounds would not do here: Compose
     * extends the touch bounds of every clickable to 48 dp, so they pass on a 32 dp target too.
     */
    private fun SemanticsNodeInteractionCollection.assertEachLayoutAtLeast(
        min: Dp,
        expectedCount: Int,
    ) {
        assertCountEquals(expectedCount)
        repeat(expectedCount) { index -> get(index).assertLayoutAtLeast(min) }
    }

    private fun SemanticsNodeInteraction.assertLayoutAtLeast(min: Dp) {
        assertWidthIsAtLeast(min)
        assertHeightIsAtLeast(min)
    }

    /**
     * Touch bounds: the layout bounds plus the extension Compose gives to small clickables.
     * ui-test only has exact touch-size assertions, so compare SemanticsNode.touchBoundsInRoot directly.
     */
    private fun SemanticsNodeInteractionCollection.assertEachTouchBoundsAtLeast(
        min: Dp,
        expectedCount: Int,
    ) {
        assertCountEquals(expectedCount)
        repeat(expectedCount) { index ->
            val bounds = get(index).fetchSemanticsNode().touchBoundsInRoot
            val (width, height) = with(rule.density) { bounds.width.toDp() to bounds.height.toDp() }
            // Half a pixel of slack for px -> dp rounding.
            val slack = with(rule.density) { 0.5f.toDp() }
            assertTrue("node $index touch width $width < $min", width >= min - slack)
            assertTrue("node $index touch height $height < $min", height >= min - slack)
        }
    }
}
