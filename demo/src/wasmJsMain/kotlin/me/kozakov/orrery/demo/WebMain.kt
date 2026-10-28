package me.kozakov.orrery.demo

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

@OptIn(ExperimentalComposeUiApi::class)
public fun main() {
    ComposeViewport("root") { DemoApp(platform = "Web") }
}
