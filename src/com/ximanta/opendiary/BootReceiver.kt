package com.ximanta.opendiary

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            AlarmScheduler.scheduleSync(ctx)
            val prefs = ctx.getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE)
            val h = prefs.getInt(Config.KEY_REMINDER_HOUR, Config.DEFAULT_REMINDER_HOUR)
            val m = prefs.getInt(Config.KEY_REMINDER_MIN, Config.DEFAULT_REMINDER_MIN)
            AlarmScheduler.scheduleReminder(ctx, h, m)
        }
    }
}
