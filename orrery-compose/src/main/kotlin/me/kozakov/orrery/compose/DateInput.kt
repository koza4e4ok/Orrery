package me.kozakov.orrery.compose

import android.text.format.DateFormat
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Locale

/** Locale-ordered date pattern with 4-digit years (e.g. MM/dd/yyyy for en-US). */
internal fun localizedDatePattern(locale: Locale = Locale.getDefault()): String =
    DateFormat.getBestDateTimePattern(locale, "yyyyMMdd")

/** Strict formatter for [localizedDatePattern]; y->u is required for STRICT resolution. */
internal fun strictDateFormatter(
    pattern: String,
    locale: Locale = Locale.getDefault(),
): DateTimeFormatter =
    DateTimeFormatter.ofPattern(pattern.replace('y', 'u'), locale).withResolverStyle(ResolverStyle.STRICT)

/**
 * One date text field: strict locale-pattern parsing with error
 * supporting-text. [onValueChange] receives the parsed date, or null
 * while the text is blank or invalid. [externalError] overlays
 * cross-field validation from the range dialog.
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
) {
    val locale = Locale.getDefault()
    val pattern = remember(locale) { localizedDatePattern(locale) }
    val formatter = remember(pattern) { strictDateFormatter(pattern, locale) }

    fun parse(text: String): LocalDate? =
        try {
            java.time.LocalDate
                .parse(text, formatter)
                .toKotlinLocalDate()
        } catch (_: DateTimeParseException) {
            null
        }

    var text by rememberSaveable { mutableStateOf(value?.toJavaLocalDate()?.format(formatter) ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    // Sync external writes (e.g. mode toggle after picking) without clobbering typing.
    LaunchedEffect(value) {
        if (value != parse(text)) {
            text = value?.toJavaLocalDate()?.format(formatter) ?: ""
            error = null
        }
    }
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
                    parsed == null -> strings.invalidFormatError.format(pattern.uppercase(locale))
                    parsed !in bounds -> strings.outOfRangeError
                    isDisabled(parsed) -> strings.disabledDateError
                    else -> null
                }
            onValueChange(if (error == null) parsed else null)
        },
        label = { Text(label) },
        placeholder = { Text(pattern.uppercase(locale)) },
        isError = error != null || externalError != null,
        supportingText = { (error ?: externalError)?.let { Text(it) } },
        singleLine = true,
        keyboardOptions = keyboardOptions,
        modifier = modifier,
    )
}
