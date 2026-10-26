package me.kozakov.orrery.compose

import androidx.compose.ui.text.intl.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/** The bundled string tables used off Android (Desktop, iOS, Web). */
class OrreryStringsTranslationsTest {
    @Test
    fun everyLocaleTranslatesEveryString() {
        for (tag in listOf("en", "de", "es", "ja", "uk", "zh-CN")) {
            val table = translationsFor(Locale(tag))
            assertEquals(OrreryString.entries.toSet(), table.keys, tag)
        }
    }

    @Test
    fun picksTheLocaleTable() {
        assertEquals("Heute", translationsFor(Locale("de-AT"))[OrreryString.DayToday])
        assertEquals("Сьогодні", translationsFor(Locale("uk"))[OrreryString.DayToday])
        assertEquals("今天", translationsFor(Locale("zh-Hans-SG"))[OrreryString.DayToday])
        assertEquals("Today", translationsFor(Locale("fr"))[OrreryString.DayToday])
    }
}
