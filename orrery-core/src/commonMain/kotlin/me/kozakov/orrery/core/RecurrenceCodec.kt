package me.kozakov.orrery.core

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number

/** RFC 5545 `RRULE` / `EXDATE` value parser and canonical formatter (spec section 1.5). */
@Suppress("TooManyFunctions")
internal object RecurrenceCodec {
    private val supportedParts =
        setOf("FREQ", "INTERVAL", "COUNT", "UNTIL", "BYDAY", "BYMONTHDAY", "BYMONTH", "BYSETPOS", "WKST")
    private val unsupportedParts =
        setOf(
            "BYYEARDAY",
            "BYWEEKNO",
            "BYHOUR",
            "BYMINUTE",
            "BYSECOND",
            "BYEASTER",
            "RDATE",
            "EXRULE",
            "EXDATE",
            "DTSTART",
        )
    private val weekdayCodes = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")
    private val byDayOrder: Comparator<ByDay> =
        compareBy({ it.dayOfWeek.isoDayNumber }, { it.ordinal ?: Int.MIN_VALUE })

    fun parse(
        text: String,
        exDates: Set<LocalDate>,
    ): RecurrenceRule {
        val parts = splitParts(stripPropertyName(text, "RRULE"))
        val frequency = parseFrequency(requireNotNull(parts["FREQ"]) { "RRULE must contain FREQ" })
        return RecurrenceRule(
            frequency = frequency,
            interval = parts["INTERVAL"]?.let { parseNumber("INTERVAL", it) } ?: 1,
            byDay = parts["BYDAY"]?.let(::parseByDays).orEmpty(),
            byMonthDay = parts["BYMONTHDAY"]?.let { parseNumbers("BYMONTHDAY", it) }.orEmpty(),
            byMonth = parts["BYMONTH"]?.let { parseNumbers("BYMONTH", it) }.orEmpty(),
            bySetPos = parts["BYSETPOS"]?.let { parseNumbers("BYSETPOS", it) }.orEmpty(),
            count = parts["COUNT"]?.let { parseNumber("COUNT", it) },
            until = parts["UNTIL"]?.let { parseDate("UNTIL", it) },
            weekStart = parts["WKST"]?.let { parseWeekday("WKST", it) } ?: DayOfWeek.MONDAY,
            exDates = exDates,
        )
    }

    fun parseExDates(text: String): Set<LocalDate> {
        val value = stripPropertyName(text, "EXDATE")
        if (value.isBlank()) return emptySet()
        return value.split(',').mapTo(LinkedHashSet()) { parseDate("EXDATE", it) }
    }

    fun format(
        rule: RecurrenceRule,
        includePrefix: Boolean,
    ): String =
        buildString {
            if (includePrefix) append("RRULE:")
            append("FREQ=").append(rule.frequency.name)
            if (rule.interval != 1) append(";INTERVAL=").append(rule.interval)
            rule.count?.let { append(";COUNT=").append(it) }
            rule.until?.let { append(";UNTIL=").append(formatDate(it)) }
            appendNumbers("BYMONTH", rule.byMonth)
            appendNumbers("BYMONTHDAY", rule.byMonthDay)
            if (rule.byDay.isNotEmpty()) {
                append(";BYDAY=").append(rule.byDay.sortedWith(byDayOrder).joinToString(",") { formatByDay(it) })
            }
            appendNumbers("BYSETPOS", rule.bySetPos)
            if (rule.weekStart != DayOfWeek.MONDAY) append(";WKST=").append(weekdayCode(rule.weekStart))
        }

    fun formatExDates(dates: Set<LocalDate>): String? {
        if (dates.isEmpty()) return null
        return "EXDATE;VALUE=DATE:" + dates.sorted().joinToString(",") { formatDate(it) }
    }

    /** Removes an optional `NAME[;params]:` prefix; any other property name is rejected. */
    private fun stripPropertyName(
        text: String,
        name: String,
    ): String {
        val trimmed = text.trim()
        val colon = trimmed.indexOf(':')
        if (colon < 0) return trimmed
        val property =
            trimmed
                .substring(0, colon)
                .substringBefore(';')
                .trim()
                .uppercase()
        require(property == name) { "Unsupported RRULE part $property" }
        return trimmed.substring(colon + 1).trim()
    }

    private fun splitParts(value: String): Map<String, String> {
        val parts = LinkedHashMap<String, String>()
        for (segment in value.split(';').filter { it.isNotBlank() }) {
            require('=' in segment) { "Malformed RRULE part '$segment'" }
            val key = segment.substringBefore('=').trim().uppercase()
            val partValue = segment.substringAfter('=').trim()
            require(key !in unsupportedParts) { "Unsupported RRULE part $key" }
            require(key in supportedParts) { "Unknown RRULE part $key" }
            require(partValue.isNotEmpty()) { "Empty value for RRULE part $key" }
            require(key !in parts) { "Duplicate RRULE part $key" }
            parts[key] = partValue
        }
        return parts
    }

    private fun parseFrequency(value: String): Frequency =
        when (val upper = value.uppercase()) {
            "DAILY" -> Frequency.DAILY
            "WEEKLY" -> Frequency.WEEKLY
            "MONTHLY" -> Frequency.MONTHLY
            "YEARLY" -> Frequency.YEARLY
            "HOURLY", "MINUTELY", "SECONDLY" -> invalid("Unsupported RRULE part FREQ=$upper (dates only)")
            else -> invalid("Invalid RRULE part FREQ=$value")
        }

    private fun parseNumber(
        part: String,
        value: String,
    ): Int {
        val trimmed = value.trim()
        val digits = trimmed.removePrefix("+").removePrefix("-")
        val valid = digits.length in 1..9 && isAsciiDigits(digits)
        require(valid) { "Invalid number '$value' in RRULE part $part" }
        return trimmed.removePrefix("+").toInt()
    }

    private fun parseNumbers(
        part: String,
        value: String,
    ): Set<Int> = value.split(',').mapTo(LinkedHashSet()) { parseNumber(part, it) }

    private fun parseByDays(value: String): Set<ByDay> = value.split(',').mapTo(LinkedHashSet()) { parseByDay(it) }

    /** `(+|-)?<1..2 digits>?(MO|TU|WE|TH|FR|SA|SU)`. */
    private fun parseByDay(token: String): ByDay {
        val trimmed = token.trim().uppercase()
        require(trimmed.length >= 2) { "Invalid BYDAY value '$token'" }
        val weekday = parseWeekday("BYDAY", trimmed.takeLast(2))
        val prefix = trimmed.dropLast(2)
        if (prefix.isEmpty()) return ByDay(weekday)
        val digits = prefix.removePrefix("+").removePrefix("-")
        require(digits.length in 1..2 && isAsciiDigits(digits)) { "Invalid BYDAY value '$token'" }
        val ordinal = prefix.removePrefix("+").toInt()
        require(ordinal != 0) { "Invalid BYDAY value '$token': ordinal must not be 0" }
        return ByDay(weekday, ordinal)
    }

    private fun parseWeekday(
        part: String,
        value: String,
    ): DayOfWeek {
        val index = weekdayCodes.indexOf(value.trim().uppercase())
        require(index >= 0) { "Invalid weekday '$value' in RRULE part $part" }
        return DayOfWeek.entries[index]
    }

    /** `yyyyMMdd`, `yyyyMMdd'T'HHmmss` or `yyyyMMdd'T'HHmmss'Z'`; only the date is kept. */
    private fun parseDate(
        part: String,
        value: String,
    ): LocalDate {
        val text = value.trim().uppercase()
        val datePart = text.take(8)
        require(datePart.length == 8 && isAsciiDigits(datePart) && isTimeSuffix(text.drop(8))) {
            "Invalid date '$value' in RRULE part $part (expected yyyyMMdd)"
        }
        val year = datePart.substring(0, 4).toInt()
        val month = datePart.substring(4, 6).toInt()
        val day = datePart.substring(6).toInt()
        val valid = month in 1..12 && day in 1..daysInMonth(year, month)
        require(valid) { "Invalid date '$value' in RRULE part $part" }
        return LocalDate(year, month, day)
    }

    /** Empty, or `'T'HHmmss` with an optional trailing `Z`. */
    private fun isTimeSuffix(text: String): Boolean {
        if (text.isEmpty()) return true
        val body = text.removeSuffix("Z")
        return body.length == 7 && body[0] == 'T' && isAsciiDigits(body.substring(1))
    }

    private fun isAsciiDigits(text: String): Boolean = text.all { it in '0'..'9' }

    private fun StringBuilder.appendNumbers(
        part: String,
        values: Set<Int>,
    ) {
        if (values.isEmpty()) return
        append(';').append(part).append('=').append(values.sorted().joinToString(","))
    }

    private fun formatByDay(day: ByDay): String = (day.ordinal?.toString() ?: "") + weekdayCode(day.dayOfWeek)

    private fun weekdayCode(day: DayOfWeek): String = weekdayCodes[day.isoDayNumber - 1]

    private fun formatDate(date: LocalDate): String {
        val month = date.month.number
        return date.year.toString().padStart(4, '0') +
            month.toString().padStart(2, '0') +
            date.day.toString().padStart(2, '0')
    }

    private fun invalid(message: String): Nothing = throw IllegalArgumentException(message)
}
