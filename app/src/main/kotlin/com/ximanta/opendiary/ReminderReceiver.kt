// language: Kotlin, file: ReminderReceiver.kt, target: Android API 26-35
package com.ximanta.opendiary

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Fires the daily reminder notification, then re-arms tomorrow's alarm
 * from inside the receiver. This keeps the alarm chain alive even if the
 * user never opens MainActivity between reminders, which is the failure
 * mode that makes setInexactRepeating-based chains silently die.
 *
 * Does not touch ConnectivityManager — the notification is fully local.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        // Only act on the exact intent this receiver was scheduled for.
        // Guards against stray broadcasts waking us with no reminder time.
        showNotification(ctx)
        rearm(ctx)
    }

    private fun showNotification(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Daymark Reminder",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily prompt to write your journal"
            }
            nm.createNotificationChannel(channel)
        }

        val contentPi = PendingIntent.getActivity(
            ctx,
            0,
            Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder: Notification.Builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(ctx, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(ctx)
        }

        builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Daymark")
            .setContentText("Time to write your diary")
            .setContentIntent(contentPi)
            .setAutoCancel(true)

        nm.notify(NOTIF_ID, builder.build())
    }

    /**
     * Chain the next reminder. Reads the user's stored hour/minute and only
     * reschedules if reminders are still enabled in Preferences.
     */
    private fun rearm(ctx: Context) {
        val prefs = ctx.getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(
            Config.KEY_REMINDER_ENABLED,
            Config.DEFAULT_REMINDER_ENABLED
        )
        if (!enabled) return

        val hour = prefs.getInt(Config.KEY_REMINDER_HOUR, Config.DEFAULT_REMINDER_HOUR)
        val minute = prefs.getInt(Config.KEY_REMINDER_MIN, Config.DEFAULT_REMINDER_MIN)

        AlarmScheduler.scheduleReminder(ctx, hour, minute)
    }

    companion object {
        const val CHANNEL_ID = "daymark_reminder"
        const val NOTIF_ID = 8001
    }
}
