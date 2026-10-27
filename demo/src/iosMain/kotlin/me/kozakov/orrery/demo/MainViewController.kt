package me.kozakov.orrery.demo

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

public fun MainViewController(): UIViewController = ComposeUIViewController { DemoApp(platform = "iOS") }
