package com.ximanta.opendiary

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

class SyncService : Service() {
    private val running = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running.compareAndSet(false, true)) return START_NOT_STICKY
        try {
            ensureChannel()
            val notification = if (Build.VERSION.SDK_INT >= 26) {
                Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("Daymark")
                    .setContentText("Syncing entries")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setOngoing(true)
                    .build()
            } else {
                Notification.Builder(this)
                    .setContentTitle("Daymark")
                    .setContentText("Syncing entries")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setOngoing(true)
                    .build()
            }
            startForeground(NOTIF_ID, notification)
        } catch (t: Throwable) {
            running.set(false)
            Log.e(TAG, "startForeground failed", t)
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        Thread {
            val prefs = getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE)
            try {
                if (!hasNetwork()) {
                    prefs.edit().putLong(Config.KEY_LAST_SYNC_AT, System.currentTimeMillis())
                        .putBoolean(Config.KEY_LAST_SYNC_OK, false)
                        .putString(Config.KEY_LAST_SYNC_ERROR, "No network connection")
                        .apply()
                    return@Thread
                }
                doSync()
                prefs.edit().putLong(Config.KEY_LAST_SYNC_AT, System.currentTimeMillis())
                    .putBoolean(Config.KEY_LAST_SYNC_OK, true)
                    .remove(Config.KEY_LAST_SYNC_ERROR)
                    .apply()
            } catch (t: Throwable) {
                Log.e(TAG, "sync failed", t)
                prefs.edit().putLong(Config.KEY_LAST_SYNC_AT, System.currentTimeMillis())
                    .putBoolean(Config.KEY_LAST_SYNC_OK, false)
                    .putString(Config.KEY_LAST_SYNC_ERROR, t.message ?: "Sync failed")
                    .apply()
            } finally {
                running.set(false)
                try { stopForeground(true) } catch (_: Throwable) { }
                stopSelfResult(startId)
            }
        }.start()
        return START_NOT_STICKY
    }

    private fun hasNetwork(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= 23) {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            // A captive portal or a newly connected network may not be marked
            // VALIDATED yet; the request itself will provide the final check.
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
        @Suppress("DEPRECATION")
        return cm.activeNetworkInfo?.isConnected == true
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(CHANNEL_ID, "Daymark Sync", NotificationManager.IMPORTANCE_LOW)
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
    }

    private fun doSync() {
        val db = EntryDbHelper(applicationContext)
        try {
            for (entry in db.getUnsynced()) {
                if (postEntry(entry)) db.markSynced(entry.id) else break
            }
        } finally {
            db.close()
        }
    }

    private fun postEntry(entry: Entry): Boolean {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(Config.BACKEND_URL + "/entry").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Daymark-Android")
                doOutput = true
                connectTimeout = 20_000
                readTimeout = 30_000
            }
            val body = buildJson(entry)
            val os: OutputStream = conn.outputStream
            os.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) return false
            val response = BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use {
                it.readText()
            }
            return response.contains("\"ok\":true") || response.contains("\"ok\": true")
        } catch (t: Throwable) {
            Log.e(TAG, "post failed: ${t.message}", t)
            return false
        } finally {
            conn?.disconnect()
        }
    }

    private fun buildJson(entry: Entry): String = buildString {
        append('{')
        append("\"category\":").append(quote(entry.category)).append(',')
        append("\"rawText\":").append(quote(entry.text)).append(',')
        append("\"timestamp\":").append(entry.timestamp)
        append('}')
    }

    private fun quote(value: String): String = buildString {
        append('"')
        for (c in value) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> if (c.code < 0x20) append("\\u00${c.code.toString(16).padStart(2, '0')}") else append(c)
            }
        }
        append('"')
    }

    companion object {
        const val TAG = "DaymarkSync"
        const val CHANNEL_ID = "daymark_sync"
        const val NOTIF_ID = 9001
    }
}
