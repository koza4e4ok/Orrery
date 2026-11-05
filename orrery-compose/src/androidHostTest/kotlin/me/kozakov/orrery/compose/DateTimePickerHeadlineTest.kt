package me.kozakov.orrery.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Native graphics for real text metrics; a Pixel 5 screen like DateTimePickerDialogTest.
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class DateTimePickerHeadlineTest {
    @get:Rule val rule = createComposeRule()

    private lateinit var dateStrings: DatePickerStrings

    private fun content(initialDateTime: LocalDateTime? = null) {
        rule.setContent {
            MaterialTheme {
                dateStrings = DatePickerDefaults.strings()
                OrreryDateTimePickerDialog(
                    onDismissRequest = {},
                    confirmButton = { TextButton(onClick = {}) { Text("OK") } },
                    state =
                        rememberOrreryDateTimePickerState(
                            initialDateTime = initialDateTime,
                            bounds = LocalDate(2026, 7, 1)..LocalDate(2026, 7, 31),
                            is24Hour = false,
                        ),
                    today = LocalDate(2026, 7, 15),
                )
            }
        }
    }

    private fun assertOneUnclippedLine(text: String) {
        val results = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val layout = results.single()
        assertEquals("'$text' wrapped", 1, layout.lineCount)
        assertFalse("'$text' was cut off", layout.isLineEllipsized(0))
    }

    @Test
    fun headlineDateAndTimeStayOnOneLine() {
        content(initialDateTime = LocalDateTime(2026, 7, 28, 21, 45))
        assertOneUnclippedLine("Jul 28, 2026")
    }

    @Test
    fun headlinePlaceholderStaysOnOneLine() {
        content()
        assertOneUnclippedLine(rule.runOnIdle { dateStrings.headlinePlaceholder })
    }
}
