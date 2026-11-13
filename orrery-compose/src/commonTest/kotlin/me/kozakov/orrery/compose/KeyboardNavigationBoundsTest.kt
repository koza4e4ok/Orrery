package me.kozakov.orrery.compose

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Keyboard paging never targets a date outside the calendar (#88b). */
class KeyboardNavigationBoundsTest {
    private val first = LocalDate(2026, 1, 1)
    private val last = LocalDate(2027, 12, 31)
    private val paged = mutableListOf<LocalDate>()
    private val keyboard =
        CalendarKeyboardNavigation(
            scope = CoroutineScope(Dispatchers.Unconfined),
            dualMonth = true,
            isInBounds = { it >= first && it <= last },
        ) { paged += it }

    @Test
    fun aTargetPastTheLastDayNeitherPagesNorLeavesAPendingFocus() {
        keyboard.pendingFocus.value = LocalDate(2027, 12, 1)
        keyboard.page(LocalDate(2028, 1, 1))
        assertNull(keyboard.pendingFocus.value)
        assertTrue(paged.isEmpty())
    }

    @Test
    fun aTargetBeforeTheFirstDayNeitherPagesNorLeavesAPendingFocus() {
        keyboard.page(LocalDate(2025, 12, 31))
        assertNull(keyboard.pendingFocus.value)
        assertTrue(paged.isEmpty())
    }

    @Test
    fun theLastDayItselfStillPagesAndWaitsForItsCell() {
        keyboard.page(last)
        assertEquals(listOf(last), paged)
        assertEquals(last, keyboard.pendingFocus.value)
    }
}
