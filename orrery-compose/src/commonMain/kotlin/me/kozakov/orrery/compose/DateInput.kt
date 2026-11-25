package me.kozakov.orrery.compose

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import kotlinx.datetime.LocalDate

/**
 * One date text field: strict locale-pattern parsing with error
 * supporting-text. [onValueChange] receives the parsed date, or null
 * while the text is blank or invalid. [externalError] overlays
 * cross-field validation from the range dialog. With
 * [requestInitialFocus] the field takes focus when it enters the
 * composition, i.e. when a dialog opens in or switches to input mode.
 */
@Composable
internal fun DateInputField(
    value: LocalDate?,
    onValueChange: (LocalDate?) -> Unit,
    label: String,
    bounds: ClosedRange<LocalDate>,
    isDisabled: (LocalDate) -> Boolean,
    strings: DatePickerStrings,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    externalError: String? = null,
    requestInitialFocus: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    if (requestInitialFocus) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
    val locale = Locale.current
    val pattern = remember(locale) { PlatformDateFormat.inputDatePattern() }
    val displayPattern = remember(pattern) { PlatformDateFormat.uppercase(pattern) }

    fun parse(text: String): LocalDate? = parseStrictDate(text, pattern)

    var text by rememberSaveable { mutableStateOf(value?.let { formatInputDate(it, pattern) } ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    // Sync external writes (e.g. mode toggle after picking) without clobbering typing.
    LaunchedEffect(value) {
        if (value != parse(text)) {
            text = value?.let { formatInputDate(it, pattern) } ?: ""
            error = null
        }
    }
    val shownError = error ?: externalError
    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            text = new
            if (new.isBlank()) {
                error = null
                onValueChange(null)
                return@OutlinedTextField
            }
            val parsed = parse(new)
            error =
                when {
                    parsed == null -> strings.invalidFormatError.formatTemplate(displayPattern)
                    parsed !in bounds -> strings.outOfRangeError
                    isDisabled(parsed) -> strings.disabledDateError
                    else -> null
                }
            onValueChange(if (error == null) parsed else null)
        },
        label = { Text(label) },
        placeholder = { Text(displayPattern) },
        isError = shownError != null,
        supportingText = { shownError?.let { Text(it) } },
        singleLine = true,
        keyboardOptions = keyboardOptions,
        // The field reports the message itself, not only Material's generic "Invalid input".
        modifier = modifier.focusRequester(focusRequester).semantics { if (shownError != null) this.error(shownError) },
    )
}
