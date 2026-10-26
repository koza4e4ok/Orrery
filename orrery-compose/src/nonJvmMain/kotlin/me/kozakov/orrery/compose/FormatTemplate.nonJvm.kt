package me.kozakov.orrery.compose

// The subset of printf the built-in templates use: %s, %d, positional %1$s / %2$d, and %%.
private val templateSpecifier = Regex("""%(?:(\d+)\$)?([sd%])""")

internal actual fun String.formatTemplate(vararg args: Any): String {
    var next = 0
    return templateSpecifier.replace(this) { match ->
        val (position, conversion) = match.destructured
        if (conversion == "%") {
            "%"
        } else {
            val index = if (position.isEmpty()) next++ else position.toInt() - 1
            args[index].toString()
        }
    }
}
