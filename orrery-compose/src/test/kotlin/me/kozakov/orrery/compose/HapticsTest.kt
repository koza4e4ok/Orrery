package me.kozakov.orrery.compose

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.LocalDate
import me.kozakov.orrery.core.CalendarDay
import me.kozakov.orrery.core.DayPosition
import me.kozakov.orrery.core.SelectionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HapticsTest {
    @get:Rule
    val rule = createComposeRule()

    private class RecordingHaptics : HapticFeedback {
        val performed = mutableListOf<HapticFeedbackType>()

        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            performed += hapticFeedbackType
        }
    }

    @Test
    fun clickingADayPerformsConfirmHaptic() {
        val haptics = RecordingHaptics()
        rule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                val selection = rememberCalendarSelectionState(mode = SelectionMode.Single())
                DefaultDay(
                    day = CalendarDay(LocalDate(2026, 7, 6), DayPosition.MonthDate),
                    modifier = Modifier.testTag("day"),
                    selectionState = selection,
                )
            }
        }
        rule.onNodeWithTag("day").performClick()
        rule.runOnIdle { assertEquals(listOf(HapticFeedbackType.Confirm), haptics.performed) }
    }

    @Test
    fun hapticsEnabledFalseSilencesClicks() {
        val haptics = RecordingHaptics()
        rule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                val selection = rememberCalendarSelectionState(mode = SelectionMode.Single())
                DefaultDay(
                    day = CalendarDay(LocalDate(2026, 7, 6), DayPosition.MonthDate),
                    modifier = Modifier.testTag("day"),
                    selectionState = selection,
                    hapticsEnabled = false,
                )
            }
        }
        rule.onNodeWithTag("day").performClick()
        rule.runOnIdle { assertTrue(haptics.performed.isEmpty()) }
    }
}
