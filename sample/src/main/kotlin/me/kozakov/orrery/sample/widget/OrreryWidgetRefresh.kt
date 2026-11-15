package me.kozakov.orrery.sample.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Re-renders the calendar widgets when "today" changes: at local midnight (an alarm set by
 * [scheduleMidnightRefresh]) and when the clock, the time zone or the locale changes.
 * Theme and wallpaper colour changes are picked up by the hourly update in the widget info.
 */
class OrreryWidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                refreshOrreryWidgets(app)
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
 * permission. Setting it again replaces the previous one.
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
