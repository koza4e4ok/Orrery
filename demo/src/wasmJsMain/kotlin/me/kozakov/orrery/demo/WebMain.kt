package me.kozakov.orrery.demo

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.datetime.LocalTime

private fun jsMinutesNow(): Int = js("{ const d = new Date(); return d.getHours() * 60 + d.getMinutes(); }")

@OptIn(ExperimentalComposeUiApi::class)
public fun main() {
    val minutes = jsMinutesNow()
    ComposeViewport("root") { DemoApp(platform = "Web", now = LocalTime(minutes / 60, minutes % 60)) }
}
