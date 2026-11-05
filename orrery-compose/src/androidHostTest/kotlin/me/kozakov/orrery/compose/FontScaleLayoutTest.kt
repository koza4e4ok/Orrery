package me.kozakov.orrery.compose

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.LocalDate
import me.kozakov.orrery.core.CalendarDay
import me.kozakov.orrery.core.DayInfo
import me.kozakov.orrery.core.DayPosition
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Large system text (1.3×) with real font metrics.
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(fontScale = 1.3f)
class FontScaleLayoutTest {
    @get:Rule val rule = createComposeRule()

    private fun SemanticsNodeInteraction.lineCount(): Int {
        val results = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single().lineCount
    }

    @Test
    fun labelledDayLeavesRoomForDecoratorsBelowTheLabel() {
        rule.setContent {
            MaterialTheme {
                DefaultDay(
                    day = CalendarDay(LocalDate(2026, 7, 8), DayPosition.MonthDate),
                    modifier = Modifier.width(52.dp).testTag("cell"),
                    today = LocalDate(2026, 7, 15),
                    info = DayInfo(label = "廿四"),
                    decorators = listOf(DayDecorators.eventDots { listOf(Color.Red) }),
                )
            }
        }
        val cell = rule.onNodeWithTag("cell").getUnclippedBoundsInRoot()
        val label = rule.onNodeWithText("廿四", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(
            "label bottom ${label.bottom}, cell bottom ${cell.bottom}",
            cell.bottom - label.bottom >= 5.9.dp,
        )
    }
}
