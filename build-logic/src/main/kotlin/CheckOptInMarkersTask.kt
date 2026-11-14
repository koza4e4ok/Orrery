import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Fails when a public declaration in a guarded package lacks its opt-in marker (#89).
 *
 * The ABI dump filters and Dokka hide these packages as a whole, so a declaration that
 * forgets its annotation would still ship as API that apps can call without opting in,
 * and nothing else would notice. [packages] maps a package (subpackages included) to the
 * simple name of the annotation that every public declaration in it must carry.
 *
 * Checked: every top-level declaration, and every class, interface or object nested in
 * public classes. Members are covered by their annotated class. Private, internal,
 * protected and override declarations are skipped. This is a line-based scan of the main
 * source sets, not a Kotlin parser: it expects ktlint-formatted code, with annotations on
 * the lines above the declaration or before its keyword on the same line.
 */
@DisableCachingByDefault(because = "A cheap source scan without outputs")
abstract class CheckOptInMarkersTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:Input
    abstract val packages: MapProperty<String, String>

    @TaskAction
    fun verify() {
        val rules = packages.get()
        val problems =
            sources.files
                .filter { it.extension == "kt" }
                .sortedBy { it.path }
                .flatMap { missingOptInMarkers(it, rules) }
        if (problems.isNotEmpty()) {
            throw GradleException(
                "Public declarations without their opt-in marker:\n" +
                    problems.joinToString("\n") { "  $it" } +
                    "\nAnnotate them, or make them internal or private.",
            )
        }
    }
}

private enum class Scope { PUBLIC_CLASS, HIDDEN_CLASS, OTHER }

private val PACKAGE = Regex("""^package\s+([\w.]+)""", RegexOption.MULTILINE)

private val DECLARATION =
    Regex(
        "^((?:(?:@\\S+|public|private|internal|protected|expect|actual|data|sealed|enum|annotation|inner|" +
            "value|open|abstract|final|override|inline|suspend|operator|infix|tailrec|external|const|" +
            "lateinit|companion|fun)\\s+)*)" +
            // A named or companion object; an anonymous object expression is not a declaration.
            "(class|interface|object(?=\\s+\\w)|(?<=companion\\s)object|fun|val|var|typealias)\\b",
    )

private val CLASS_KEYWORDS = setOf("class", "interface", "object")
private val HIDING_MODIFIERS = setOf("private", "internal", "protected", "override")

internal fun missingOptInMarkers(
    file: File,
    rules: Map<String, String>,
): List<String> {
    val code = blankCommentsAndStrings(file.readText())
    val pkg = PACKAGE.find(code)?.groupValues?.get(1) ?: return emptyList()
    val marker =
        rules.entries.firstOrNull { pkg == it.key || pkg.startsWith(it.key + ".") }?.value
            ?: return emptyList()
    val markerUse = Regex("""@(?:[\w.]+\.)?$marker\b""")
    val problems = mutableListOf<String>()
    val scopes = ArrayDeque<Scope>()
    val annotations = StringBuilder()
    var pending = Scope.OTHER
    var parens = 0
    code.lines().forEachIndexed { index, raw ->
        val line = raw.trim()
        // Constructor properties and parameters sit inside parentheses; their class covers them.
        val declaration = if (parens == 0) DECLARATION.find(line) else null
        val annotationOpen = annotations.count { it == '(' } > annotations.count { it == ')' }
        when {
            line.isEmpty() -> Unit
            annotationOpen || (declaration == null && line.startsWith("@")) -> annotations.append(line).append(' ')
            declaration != null -> {
                val modifiers = declaration.groupValues[1]
                val words = modifiers.split(Regex("\\s+"))
                val classLike = declaration.groupValues[2] in CLASS_KEYWORDS
                val hidden = words.any { it in HIDING_MODIFIERS }
                val visible = scopes.all { it == Scope.PUBLIC_CLASS }
                val checked = !hidden && visible && (scopes.isEmpty() || (classLike && "companion" !in words))
                if (checked && !markerUse.containsMatchIn("$annotations $modifiers")) {
                    problems += "${file.path}:${index + 1}: missing @$marker on \"${line.take(MAX_SNIPPET)}\""
                }
                pending =
                    when {
                        !classLike -> Scope.OTHER
                        hidden || !visible -> Scope.HIDDEN_CLASS
                        else -> Scope.PUBLIC_CLASS
                    }
                annotations.clear()
            }
            else -> annotations.clear()
        }
        line.forEach { char ->
            when (char) {
                '(' -> parens++
                ')' -> parens--
                '{' -> {
                    scopes.addLast(if (parens == 0) pending else Scope.OTHER)
                    if (parens == 0) pending = Scope.OTHER
                }
                '}' -> scopes.removeLastOrNull()
            }
        }
    }
    return problems
}

private const val MAX_SNIPPET = 80

/** Replaces comments and string or char literals with spaces, keeping line breaks. */
private fun blankCommentsAndStrings(text: String): String {
    val out = StringBuilder(text.length)
    var i = 0
    fun blank(until: Int) {
        for (j in i until minOf(until, text.length)) out.append(if (text[j] == '\n') '\n' else ' ')
        i = minOf(until, text.length)
    }
    while (i < text.length) {
        when {
            text.startsWith("//", i) -> blank(text.indexOf('\n', i).let { if (it < 0) text.length else it })
            text.startsWith("/*", i) -> {
                var depth = 0
                var j = i
                while (j < text.length) {
                    if (text.startsWith("/*", j)) {
                        depth++
                        j += 2
                    } else if (text.startsWith("*/", j)) {
                        depth--
                        j += 2
                        if (depth == 0) break
                    } else {
                        j++
                    }
                }
                blank(j)
            }
            text.startsWith("\"\"\"", i) -> {
                val end = text.indexOf("\"\"\"", i + 3).let { if (it < 0) text.length else it + 3 }
                blank(end)
            }
            text[i] == '"' || text[i] == '\'' -> {
                val quote = text[i]
                var j = i + 1
                while (j < text.length && text[j] != quote && text[j] != '\n') j += if (text[j] == '\\') 2 else 1
                blank(j + 1)
            }
            else -> out.append(text[i++])
        }
    }
    return out.toString()
}
