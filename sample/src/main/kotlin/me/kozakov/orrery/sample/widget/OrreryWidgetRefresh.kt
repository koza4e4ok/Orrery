package me.kozakov.orrery.sample.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.Calendar
import kotlin.time.Duration.Companion.seconds

/**
 * Re-renders the calendar widgets when "today" changes: at local midnight (an alarm set by
 * [scheduleMidnightRefresh]) and when the clock, the time zone or the locale changes.
 * Theme and wallpaper colour changes are picked up by the hourly update in the widget info.
 */
class OrreryWidgetRefreshReceiver : BroadcastReceiver() {
    // An exception that escapes this scope would kill the app process on a background
    // broadcast, so log it instead. The timeout and finally make sure finish() runs before
    // the system's goAsync() limit (about 10 s), even when an update hangs.
    @Suppress("TooGenericExceptionCaught")
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                withTimeout(REFRESH_TIMEOUT) { refreshOrreryWidgets(app) }
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "Widget refresh timed out", e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Widget refresh failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Re-renders every placed calendar widget and keeps the midnight alarm while any is placed. */
internal suspend fun refreshOrreryWidgets(context: Context) {
    OrreryMonthWidget().updateAll(context)
    OrreryAgendaWidget().updateAll(context)
    val manager = GlanceAppWidgetManager(context)
    val placed =
        manager.getGlanceIds(OrreryMonthWidget::class.java).isNotEmpty() ||
            manager.getGlanceIds(OrreryAgendaWidget::class.java).isNotEmpty()
    if (placed) scheduleMidnightRefresh(context)
}

/**
 * Sets an inexact, non-waking alarm for the next local midnight. It fires when the device is
 * next awake after midnight, which is soon enough for a calendar and needs no exact-alarm
 * permission; in Doze it can wait for the next maintenance window. Setting it again replaces
 * the previous one.
 */
internal fun scheduleMidnightRefresh(context: Context) {
    val alarms = context.getSystemService(AlarmManager::class.java) ?: return
    val intent = Intent(context, OrreryWidgetRefreshReceiver::class.java).setAction(ACTION_MIDNIGHT)
    val operation =
        PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    val midnight =
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    alarms.set(AlarmManager.RTC, midnight.timeInMillis, operation)
}

private const val ACTION_MIDNIGHT = "me.kozakov.orrery.sample.widget.MIDNIGHT"

private const val TAG = "OrreryWidgets"

private val REFRESH_TIMEOUT = 8.seconds
