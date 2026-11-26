package com.ximanta.opendiary

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(CHANNEL_ID, "Daymark Reminder", NotificationManager.IMPORTANCE_DEFAULT)
            nm.createNotificationChannel(ch)
        }

        val openIntent = Intent(ctx, MainActivity::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val contentPi = PendingIntent.getActivity(ctx, 0, openIntent, flags)

        val builder: Notification.Builder
        if (Build.VERSION.SDK_INT >= 26) {
            builder = Notification.Builder(ctx, CHANNEL_ID)
        } else {
            builder = Notification.Builder(ctx)
        }
        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Daymark")
            .setContentText("Time to write your diary")
            .setContentIntent(contentPi)
            .setAutoCancel(true)

        nm.notify(NOTIF_ID, builder.build())
    }

    companion object {
        const val CHANNEL_ID = "daymark_reminder"
        const val NOTIF_ID = 8001
    }
}
