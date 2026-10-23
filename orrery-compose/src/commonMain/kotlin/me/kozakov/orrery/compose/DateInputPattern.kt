package me.kozakov.orrery.compose

import kotlinx.datetime.LocalDate
import kotlin.math.absoluteValue

/*
 * Strict date input in pure Kotlin. Patterns come from
 * PlatformDateFormat.inputDatePattern(): y/M/d fields plus literal separators,
 * with java.time quoting ('text', '' for a quote). Parsing follows the
 * java.time STRICT rules the field used before: one-letter fields take 1-2
 * digits, two-letter fields exactly 2, yyyy exactly 4, and the date must exist.
 */

private sealed interface PatternToken {
    data class Literal(
        val text: String,
    ) : PatternToken

    data class Field(
        val letter: Char,
        val width: Int,
    ) : PatternToken
}

private const val FIELD_LETTERS = "yMd"
private const val MAX_VARIABLE_DIGITS = 9
private const val TWO_DIGIT_YEAR_BASE = 2000
private const val FULL_YEAR_WIDTH = 4

private fun tokenize(pattern: String): List<PatternToken> {
    val tokens = mutableListOf<PatternToken>()
    val literal = StringBuilder()

    fun flushLiteral() {
        if (literal.isNotEmpty()) {
            tokens += PatternToken.Literal(literal.toString())
            literal.clear()
        }
    }
    var i = 0
    while (i < pattern.length) {
        val c = pattern[i]
        when {
            c == '\'' -> {
                if (i + 1 < pattern.length && pattern[i + 1] == '\'') {
                    literal.append('\'')
                    i += 2
                } else {
                    val end = pattern.indexOf('\'', i + 1).let { if (it < 0) pattern.length else it }
                    literal.append(pattern, i + 1, end)
                    i = end + 1
                }
            }
            c in FIELD_LETTERS -> {
                var end = i
                while (end < pattern.length && pattern[end] == c) end++
                flushLiteral()
                tokens += PatternToken.Field(c, end - i)
                i = end
            }
            else -> {
                literal.append(c)
                i++
            }
        }
    }
    flushLiteral()
    return tokens
}

/** Widens every y/M/d field of [pattern] to yyyy, MM and dd, keeping order and separators. */
internal fun normalizeInputPattern(pattern: String): String =
    tokenize(pattern).joinToString("") { token ->
        when (token) {
            is PatternToken.Literal -> quoteLiteral(token.text)
            is PatternToken.Field -> if (token.letter == 'y') "yyyy" else token.letter.toString().repeat(2)
        }
    }

private fun quoteLiteral(text: String): String =
    if (text.any { it.isLetter() || it == '\'' }) "'" + text.replace("'", "''") + "'" else text

/** Formats [date] with an input [pattern]; the inverse of [parseStrictDate]. */
internal fun formatInputDate(
    date: LocalDate,
    pattern: String,
): String =
    tokenize(pattern).joinToString("") { token ->
        when (token) {
            is PatternToken.Literal -> token.text
            is PatternToken.Field ->
                when (token.letter) {
                    'y' -> formatYear(date.year, token.width)
                    'M' -> (date.month.ordinal + 1).toString().padStart(token.width, '0')
                    else -> date.day.toString().padStart(token.width, '0')
                }
        }
    }

private fun formatYear(
    year: Int,
    width: Int,
): String =
    when {
        width == 2 -> (year.absoluteValue % 100).toString().padStart(2, '0')
        year < 0 -> "-" + year.absoluteValue.toString().padStart(width, '0')
        width >= FULL_YEAR_WIDTH && year.toString().length > width -> "+" + year
        else -> year.toString().padStart(width, '0')
    }

/** Parses [text] against an input [pattern]; null unless it matches exactly and names a real date. */
internal fun parseStrictDate(
    text: String,
    pattern: String,
): LocalDate? {
    val fields = mutableMapOf<Char, Int>()
    var pos = 0
    for (token in tokenize(pattern)) {
        pos =
            when (token) {
                is PatternToken.Literal -> if (text.startsWith(token.text, pos)) pos + token.text.length else return null
                is PatternToken.Field -> readField(text, pos, token, fields) ?: return null
            }
    }
    val year = fields['y']
    val month = fields['M']
    val day = fields['d']
    if (pos != text.length || year == null || month == null || day == null) return null
    return try {
        LocalDate(year, month, day)
    } catch (_: IllegalArgumentException) {
        null
    }
}

/** Reads one numeric [field] at [start] into [values]; returns the end index, or null if the digits do not fit. */
private fun readField(
    text: String,
    start: Int,
    field: PatternToken.Field,
    values: MutableMap<Char, Int>,
): Int? {
    val (minDigits, maxDigits) = digitBounds(field)
    var end = start
    while (end < text.length && end - start < maxDigits && text[end] in '0'..'9') end++
    if (end - start < minDigits) return null
    val value = text.substring(start, end).toInt()
    values[field.letter] = if (field.letter == 'y' && field.width == 2) TWO_DIGIT_YEAR_BASE + value else value
    return end
}

private fun digitBounds(field: PatternToken.Field): Pair<Int, Int> =
    when {
        field.letter == 'y' && field.width == 1 -> 1 to MAX_VARIABLE_DIGITS
        field.letter == 'y' -> field.width to field.width
        field.width == 1 -> 1 to 2
        else -> field.width to field.width
    }
