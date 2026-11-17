// language: Kotlin, file: AlarmScheduler.kt, target: Android API 26-35
package com.ximanta.opendiary

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * Sync uses an inexact repeating alarm because it is useless without network —
 * bundling it with radio wake-ups is correct.
 *
 * Reminders use a one-shot exact alarm and self-rearm in ReminderReceiver.
 * setInexactRepeating fires only on maintenance windows (which correlate with
 * network activity), so a diary reminder built on it is late by hours and
 * never arrives offline. setExactAndAllowWhileIdle is Doze-exempt per shot.
 */
object AlarmScheduler {

    const val REQ_SYNC = 1001
    const val REQ_REMINDER = 1002

    private const val FLAGS =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    // ─────────────────────────────────────────────────────────────────
    // Sync — inexact repeating is fine, network is a prerequisite
    // ─────────────────────────────────────────────────────────────────

    fun scheduleSync(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getForegroundService(
            ctx, REQ_SYNC, Intent(ctx, SyncService::class.java), FLAGS
        )
        am.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + Config.SYNC_INTERVAL_MS,
            Config.SYNC_INTERVAL_MS,
            pi
        )
    }

    fun cancelSync(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getForegroundService(
            ctx, REQ_SYNC, Intent(ctx, SyncService::class.java), FLAGS
        )
        am.cancel(pi)
        pi.cancel()
    }

    // ─────────────────────────────────────────────────────────────────
    // Reminder — exact, one-shot, rearmed by ReminderReceiver
    // ─────────────────────────────────────────────────────────────────

    fun scheduleReminder(ctx: Context, hour: Int, minute: Int) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = reminderPendingIntent(ctx)
        val triggerAt = nextTriggerMillis(hour, minute)

        // API 31+ may deny exact alarms. Fall back to a Doze-tolerant alarm
        // rather than throwing SecurityException.
        val canBeExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()

        if (!canBeExact) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            return
        }

        if (Build.VERSION.SDK_INT >= 23) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            @Suppress("DEPRECATION")
            am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancelReminder(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = reminderPendingIntent(ctx)
        am.cancel(pi)
        pi.cancel()
    }

    /**
     * Next occurrence of hour:minute strictly in the future. Handles:
     *  - trigger time already passed today → tomorrow
     *  - device clock moved forward while alarm was armed (self-heals)
     */
    fun nextTriggerMillis(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return cal.timeInMillis
    }

    private fun reminderPendingIntent(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx,
            REQ_REMINDER,
            Intent(ctx, ReminderReceiver::class.java),
            FLAGS
        )
}
