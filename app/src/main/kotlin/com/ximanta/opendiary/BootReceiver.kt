// language: Kotlin, file: BootReceiver.kt, target: Android API 26-35
package com.ximanta.opendiary

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-arms alarms after events that invalidate their absolute fire time:
 *
 *  - BOOT_COMPLETED       — every alarm is dropped on reboot
 *  - MY_PACKAGE_REPLACED  — app update drops the app's alarms
 *  - TIME_SET             — user changed the device clock
 *  - TIMEZONE_CHANGED     — wall-clock time of an exact alarm shifted
 *
 * Sync is rescheduled unconditionally; the reminder is rescheduled only
 * when the user has it enabled, and cancelled otherwise.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                AlarmScheduler.scheduleSync(ctx)

                val prefs = ctx.getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE)
                val enabled = prefs.getBoolean(
                    Config.KEY_REMINDER_ENABLED,
                    Config.DEFAULT_REMINDER_ENABLED
                )

                if (enabled) {
                    val hour = prefs.getInt(Config.KEY_REMINDER_HOUR, Config.DEFAULT_REMINDER_HOUR)
                    val minute = prefs.getInt(Config.KEY_REMINDER_MIN, Config.DEFAULT_REMINDER_MIN)
                    AlarmScheduler.scheduleReminder(ctx, hour, minute)
                } else {
                    AlarmScheduler.cancelReminder(ctx)
                }
            }
        }
    }
}
