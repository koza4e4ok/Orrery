package me.kozakov.orrery.demo

import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.datetime.LocalTime
import platform.Foundation.NSLog
import platform.UIKit.UIViewController

// TEMPORARY: iOS entry point that plays the tour for the README GIF recording.
@Suppress("FunctionNaming")
public fun MainViewController(): UIViewController =
    ComposeUIViewController {
        // NSLog is unbuffered, so the recorder sees each loop marker when it happens.
        DemoApp(platform = "iOS", now = LocalTime(10, 24), tour = { playTour(onLoop = { NSLog("ORRERY_DEMO_LOOP") }) })
    }
