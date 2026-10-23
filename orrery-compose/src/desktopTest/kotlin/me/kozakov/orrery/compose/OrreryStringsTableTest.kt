package me.kozakov.orrery.compose

import androidx.compose.ui.text.intl.Locale
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/** The non-Android string tables must match the Android string resources they mirror. */
class OrreryStringsTableTest {
    @Test
    fun tablesMatchAndroidResources() {
        val locales =
            mapOf(
                "values" to "en",
                "values-de" to "de",
                "values-es" to "es",
                "values-ja" to "ja",
                "values-uk" to "uk",
                "values-zh-rCN" to "zh-CN",
            )
        for ((folder, tag) in locales) {
            val resources = readStrings(File("src/androidMain/res/$folder/strings.xml"))
            val table = translationsFor(Locale(tag))
            for (string in OrreryString.entries) {
                assertEquals(resources[string.resourceName], table[string], "$folder/${string.resourceName}")
            }
        }
    }

    @Test
    fun unknownLocalesFallBackToEnglish() {
        assertEquals(translationsFor(Locale("en")), translationsFor(Locale("fr")))
        assertEquals(translationsFor(Locale("en")), translationsFor(Locale("zh-TW")))
    }

    private fun readStrings(file: File): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length)
            .map { nodes.item(it) as Element }
            .associate { it.getAttribute("name") to unescapeAndroid(it.textContent) }
    }

    private fun unescapeAndroid(text: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\\' && i + 1 < text.length -> {
                    out.append(
                        when (val next = text[i + 1]) {
                            'n' -> '\n'
                            't' -> '\t'
                            else -> next
                        },
                    )
                    i += 2
                }
                c == '"' -> i++
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.toString()
    }
}
