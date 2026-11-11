package me.kozakov.orrery.compose

import androidx.compose.runtime.Composer
import androidx.compose.ui.Modifier
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * The range dialog signatures released before `layoutMode` stay in the
 * bytecode as hidden bridges, so apps compiled against them keep linking.
 */
class DateRangePickerBinaryCompatTest {
    private val fileClass = Class.forName("me.kozakov.orrery.compose.DateRangePickerDialogKt")
    private val int = Integer.TYPE

    private val shared =
        arrayOf(
            Function0::class.java,
            Function2::class.java,
            OrreryDateRangePickerState::class.java,
            Modifier::class.java,
            CalendarDayColors::class.java,
            CalendarDayShapes::class.java,
            LocalDate::class.java,
            DatePickerStrings::class.java,
        )

    private fun dialog(vararg extra: Class<*>) =
        fileClass.getDeclaredMethod(
            "OrreryDateRangePickerDialog",
            *shared,
            *extra,
            Composer::class.java,
            int,
            int,
        )

    @Test
    fun `dialog without presets remains callable from bytecode`() {
        assertNotNull(dialog())
    }

    @Test
    fun `dialog with presets and no layout mode remains callable from bytecode`() {
        assertNotNull(dialog(List::class.java))
    }

    @Test
    fun `dialog with layout mode is the current signature`() {
        assertNotNull(dialog(List::class.java, DateRangeCalendarLayoutMode::class.java))
    }
}
