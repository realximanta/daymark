package com.ximanta.opendiary

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class SyncService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            ensureChannel()
            val n: Notification
            if (Build.VERSION.SDK_INT >= 26) {
                n = Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("Daymark")
                    .setContentText("Syncing entries")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .build()
            } else {
                n = Notification.Builder(this)
                    .setContentTitle("Daymark")
                    .setContentText("Syncing entries")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .build()
            }
            startForeground(NOTIF_ID, n)
        } catch (t: Throwable) {
            Log.e(TAG, "startForeground failed", t)
        }

        val th = Thread(object : Runnable {
            override fun run() {
                try {
                    doSync()
                } catch (t: Throwable) {
                    Log.e(TAG, "sync failed", t)
                } finally {
                    try { stopForeground(true) } catch (t: Throwable) { }
                    stopSelf()
                }
            }
        })
        th.start()

        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(CHANNEL_ID, "Daymark Sync", NotificationManager.IMPORTANCE_MIN)
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
    }

    private fun doSync() {
        val db = EntryDbHelper(applicationContext)
        try {
            val pending = db.getUnsynced()
            var i = 0
            while (i < pending.size) {
                val e = pending[i]
                if (postEntry(e)) db.markSynced(e.id)
                i++
            }
        } finally {
            db.close()
        }
    }

    private fun postEntry(e: Entry): Boolean {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(Config.BACKEND_URL + "/entry")
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.doOutput = true
            conn.connectTimeout = 20000
            conn.readTimeout = 30000

            val body = buildJson(e)
            val os: OutputStream = conn.outputStream
            os.write(body.toByteArray(StandardCharsets.UTF_8))
            os.flush()
            os.close()

            val code = conn.responseCode
            if (code == 200) {
                val isr = InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)
                val br = BufferedReader(isr)
                val sb = StringBuilder()
                var line = br.readLine()
                while (line != null) {
                    sb.append(line)
                    line = br.readLine()
                }
                br.close()
                val resp = sb.toString()
                return resp.indexOf("\"ok\":true") >= 0 || resp.indexOf("\"ok\": true") >= 0
            }
            return false
        } catch (t: Throwable) {
            Log.e(TAG, "post failed: " + t.message, t)
            return false
        } finally {
            if (conn != null) conn.disconnect()
        }
    }

    private fun buildJson(e: Entry): String {
        val sb = StringBuilder()
        sb.append('{')
        sb.append("\"secret\":").append(quote(Config.SECRET)).append(',')
        sb.append("\"deviceId\":").append(quote(Config.DEVICE_ID)).append(',')
        sb.append("\"category\":").append(quote(e.category)).append(',')
        sb.append("\"text\":").append(quote(e.text)).append(',')
        sb.append("\"timestamp\":").append(e.timestamp)
        sb.append('}')
        return sb.toString()
    }

    private fun quote(s: String): String {
        val sb = StringBuilder()
        sb.append('"')
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> {
                    if (c.code < 0x20) {
                        sb.append("\\u00")
                        sb.append(hexDigit((c.code shr 4) and 0xF))
                        sb.append(hexDigit(c.code and 0xF))
                    } else {
                        sb.append(c)
                    }
                }
            }
            i++
        }
        sb.append('"')
        return sb.toString()
    }

    private fun hexDigit(v: Int): Char {
        return if (v < 10) (('0'.code + v).toChar()) else (('a'.code + (v - 10)).toChar())
    }

    companion object {
        const val TAG = "DaymarkSync"
        const val CHANNEL_ID = "daymark_sync"
        const val NOTIF_ID = 9001
    }
}
