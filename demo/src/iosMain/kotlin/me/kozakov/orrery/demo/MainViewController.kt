package me.kozakov.orrery.demo

import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.datetime.LocalTime
import platform.UIKit.UIViewController

public fun MainViewController(): UIViewController =
    ComposeUIViewController { DemoApp(platform = "iOS", now = LocalTime(10, 24), tour = { playTour() }) }
