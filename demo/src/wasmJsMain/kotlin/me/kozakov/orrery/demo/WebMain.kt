package me.kozakov.orrery.demo

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.datetime.LocalTime

@OptIn(ExperimentalComposeUiApi::class)
public fun main() {
    // TEMPORARY: plays the tour for the README GIF recording.
    ComposeViewport("root") { DemoApp(platform = "Web", now = LocalTime(10, 24), tour = { playTour() }) }
}
