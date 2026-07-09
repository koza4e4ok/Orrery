package dev.koza4e4ok.material.calendar.compose

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposeSmokeTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun composeRendersUnderRobolectric() {
        rule.setContent { Text("hello") }
        rule.onNodeWithText("hello").assertIsDisplayed()
    }
}
