package me.kozakov.orrery.demo

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.datetime.LocalTime

public fun main(): Unit =
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Orrery",
            undecorated = true,
            resizable = false,
            state = rememberWindowState(position = WindowPosition(0.dp, 0.dp), size = DpSize(400.dp, 820.dp)),
        ) {
            DemoApp(platform = "Desktop", now = LocalTime(10, 24), tour = { playTour() })
        }
    }
