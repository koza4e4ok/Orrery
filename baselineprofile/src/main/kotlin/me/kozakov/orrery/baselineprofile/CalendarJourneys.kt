package me.kozakov.orrery.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

internal const val TARGET_PACKAGE = "me.kozakov.orrery.sample"

private const val UI_TIMEOUT_MS = 5_000L
private const val FLINGS_PER_DIRECTION = 3

/** Selects a bottom navigation tab of the sample by its label. */
internal fun MacrobenchmarkScope.openTab(label: String) {
    val tab = device.wait(Until.findObject(By.text(label)), UI_TIMEOUT_MS)
    checkNotNull(tab) { "Tab '$label' not found" }.click()
    device.waitForIdle()
}

/** Pages the Almanac tab's HorizontalCalendar forward and back. */
internal fun MacrobenchmarkScope.scrollHorizontalCalendar() {
    val calendar = findCalendar("almanac_calendar")
    fling(calendar, Direction.RIGHT)
    fling(calendar, Direction.LEFT)
}

/** Scrolls the Trips tab's VerticalCalendar down and back up. */
internal fun MacrobenchmarkScope.scrollVerticalCalendar() {
    val calendar = findCalendar("trips_calendar")
    fling(calendar, Direction.DOWN)
    fling(calendar, Direction.UP)
}

private fun MacrobenchmarkScope.findCalendar(tag: String): UiObject2 {
    val calendar = device.wait(Until.findObject(By.res(tag)), UI_TIMEOUT_MS)
    return checkNotNull(calendar) { "Calendar '$tag' not found" }.apply {
        // Keep gestures clear of the system navigation edges.
        setGestureMargin(device.displayWidth / 5)
    }
}

private fun MacrobenchmarkScope.fling(
    calendar: UiObject2,
    direction: Direction,
) {
    repeat(FLINGS_PER_DIRECTION) {
        calendar.fling(direction)
        device.waitForIdle()
    }
}
