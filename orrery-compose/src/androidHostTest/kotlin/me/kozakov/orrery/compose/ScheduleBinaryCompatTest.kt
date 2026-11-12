package me.kozakov.orrery.compose

import androidx.test.ext.junit.runners.AndroidJUnit4
import me.kozakov.orrery.core.EventOverlapMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Method

/** The 0.8.0 / 0.9.2 schedule signatures must stay in the bytecode after the editing parameters were added. */
@RunWith(AndroidJUnit4::class)
class ScheduleBinaryCompatTest {
    private val viewMethods: Array<Method> = Class.forName("me.kozakov.orrery.compose.ScheduleViewKt").declaredMethods

    /** [parameterCount] includes the trailing Composer, `$changed` and `$default` parameters. */
    private fun count(
        name: String,
        parameterCount: Int,
        overlapMode: Boolean,
    ): Int =
        viewMethods.count { method ->
            method.name == name &&
                method.parameterTypes.size == parameterCount &&
                method.parameterTypes.contains(EventOverlapMode::class.java) == overlapMode
        }

    @Test
    fun previousDayScheduleOverloadsRemain() {
        assertEquals(1, count("DaySchedule", 23, overlapMode = false))
        assertEquals(1, count("DaySchedule", 24, overlapMode = true))
    }

    @Test
    fun previousWeekScheduleOverloadsRemain() {
        assertEquals(1, count("WeekSchedule", 24, overlapMode = false))
        assertEquals(1, count("WeekSchedule", 26, overlapMode = true))
    }

    @Test
    fun previousConstructorsRemain() {
        assertNotNull(ScheduleStrings::class.java.getDeclaredConstructor(String::class.java, String::class.java))
        // Eight Dp (float) sizes, the row count and the value-class constructor marker.
        assertEquals(1, ScheduleDimensions::class.java.declaredConstructors.count { it.parameterTypes.size == 10 })
    }

    @Test
    fun previousDefaultsFactoriesRemain() {
        val defaults = ScheduleDefaults::class.java.declaredMethods
        // dimensions-<hash>(8 x float, int) and strings(String, String, Composer, $changed, $default).
        assertEquals(1, defaults.count { it.name.startsWith("dimensions-") && it.parameterTypes.size == 9 })
        assertEquals(1, defaults.count { it.name == "strings" && it.parameterTypes.size == 5 })
    }
}
