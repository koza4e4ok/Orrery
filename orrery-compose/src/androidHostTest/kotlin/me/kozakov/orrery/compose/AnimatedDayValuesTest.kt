package me.kozakov.orrery.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnimatedDayValuesTest {
    @get:Rule
    val rule = createComposeRule()

    private val mon = LocalDate(2026, 7, 6)
    private val tue = LocalDate(2026, 7, 7)

    @Test
    fun valuesReachTheirTargetsAndRemovedDatesReturnNull() {
        lateinit var lookup: (LocalDate) -> Float?
        var values by mutableStateOf(mapOf(mon to 0.8f))
        rule.setContent {
            lookup = rememberAnimatedDayValues(values)
        }
        rule.waitForIdle() // animations auto-advance to completion
        assertEquals(0.8f, lookup(mon)!!, 0.001f)
        assertNull(lookup(tue))

        values = mapOf(mon to 0.2f, tue to 0.5f)
        rule.waitForIdle()
        assertEquals(0.2f, lookup(mon)!!, 0.001f)
        assertEquals(0.5f, lookup(tue)!!, 0.001f)

        values = mapOf(tue to 0.5f)
        rule.waitForIdle()
        assertNull(lookup(mon))
    }
}
