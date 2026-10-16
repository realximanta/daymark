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
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground service that pushes unsynced local entries to the backend.
 *
 * Auth: the device-stored passcode is sent as
 *   Authorization: Bearer <passcode>
 * on every request. The backend rejects requests without it.
 */
class SyncService : Service() {

    private val running = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Prevent overlapping syncs (alarm + manual tap + auto-start).
        if (!running.compareAndSet(false, true)) return START_NOT_STICKY

        try {
            ensureChannel()
            startForeground(NOTIF_ID, buildSyncNotification("Syncing entries…"))
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
                    prefs.edit()
                        .putLong(Config.KEY_LAST_SYNC_AT, System.currentTimeMillis())
                        .putBoolean(Config.KEY_LAST_SYNC_OK, false)
                        .putString(Config.KEY_LAST_SYNC_ERROR, "No network connection")
                        .apply()
                    return@Thread
                }

                val result = doSync()

                val edit = prefs.edit()
                    .putLong(Config.KEY_LAST_SYNC_AT, System.currentTimeMillis())
                when (result) {
                    SyncResult.OK -> edit.putBoolean(Config.KEY_LAST_SYNC_OK, true)
                        .remove(Config.KEY_LAST_SYNC_ERROR)

                    SyncResult.EMPTY -> edit.putBoolean(Config.KEY_LAST_SYNC_OK, true)
                        .remove(Config.KEY_LAST_SYNC_ERROR)

                    SyncResult.AUTH_FAILED -> {
                        edit.putBoolean(Config.KEY_LAST_SYNC_OK, false)
                            .putString(
                                Config.KEY_LAST_SYNC_ERROR,
                                "Passcode rejected — check Settings → Security"
                            )
                        postPasscodeWarning()
                    }

                    SyncResult.BAD_REQUEST -> {
                        edit.putBoolean(Config.KEY_LAST_SYNC_OK, false)
                            .putString(
                                Config.KEY_LAST_SYNC_ERROR,
                                "Server rejected an entry — check app version"
                            )
                    }

                    SyncResult.FAILED -> {
                        edit.putBoolean(Config.KEY_LAST_SYNC_OK, false)
                            .putString(Config.KEY_LAST_SYNC_ERROR, "Could not reach the backend")
                    }
                }
                edit.apply()
            } catch (t: Throwable) {
                Log.e(TAG, "sync failed", t)
                prefs.edit()
                    .putLong(Config.KEY_LAST_SYNC_AT, System.currentTimeMillis())
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

    // ─────────────────────────────────────────────────────────────────────
    // Core sync
    // ─────────────────────────────────────────────────────────────────────

    private enum class SyncResult { OK, EMPTY, AUTH_FAILED, BAD_REQUEST, FAILED }

    private fun doSync(): SyncResult {
        val passcode = PasscodeStore.get(this)
        if (passcode.isNullOrEmpty()) {
            Log.w(TAG, "Sync requested but no passcode is stored")
            return SyncResult.AUTH_FAILED
        }

        val db = EntryDbHelper(applicationContext)
        try {
            val queue = db.getUnsynced()
            if (queue.isEmpty()) return SyncResult.EMPTY

            for (entry in queue) {
                when (postEntryWithRetry(entry, passcode)) {
                    PostResult.OK -> db.markSynced(entry.id)
                    PostResult.AUTH_FAILED -> return SyncResult.AUTH_FAILED
                    PostResult.BAD_REQUEST -> return SyncResult.BAD_REQUEST
                    PostResult.FAILED -> return SyncResult.FAILED
                }
            }
            return SyncResult.OK
        } finally {
            db.close()
        }
    }

    private enum class PostResult { OK, AUTH_FAILED, BAD_REQUEST, FAILED }

    /** Up to 3 attempts for transient (network / 5xx) failures. */
    private fun postEntryWithRetry(entry: Entry, passcode: String): PostResult {
        val backoffMs = longArrayOf(0L, 2_000L, 5_000L, 12_000L)
        var lastResult = PostResult.FAILED

        for (attempt in 1..3) {
            if (backoffMs[attempt] > 0) {
                try {
                    Thread.sleep(backoffMs[attempt])
                } catch (_: InterruptedException) {
                    return PostResult.FAILED
                }
            }
            when (val r = postEntry(entry, passcode)) {
                PostResult.OK -> return PostResult.OK
                PostResult.AUTH_FAILED -> return PostResult.AUTH_FAILED  // never retry 401
                PostResult.BAD_REQUEST -> return PostResult.BAD_REQUEST  // never retry 4xx
                PostResult.FAILED -> {
                    lastResult = PostResult.FAILED
                    Log.w(TAG, "attempt $attempt failed for entry #${entry.id}")
                }
            }
        }
        return lastResult
    }

    private fun postEntry(entry: Entry, passcode: String): PostResult {
        var conn: HttpURLConnection? = null
        try {
            val body = buildJson(entry)
            val bytes = body.toByteArray(StandardCharsets.UTF_8)

            conn = (URL(Config.BACKEND_URL + "/entry").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Daymark-Android")
                // 🔐 Preferred auth header — matches backend `Authorization: Bearer`
                setRequestProperty("Authorization", "Bearer $passcode")
                setFixedLengthStreamingMode(bytes.size)
                doOutput = true
                connectTimeout = 25_000
                readTimeout = 30_000
            }

            val os: OutputStream = conn.outputStream
            os.use { it.write(bytes) }

            val code = conn.responseCode
            return when {
                code == 401 || code == 403 -> PostResult.AUTH_FAILED
                code == 400 || code == 413 -> {
                    logBadRequest(code, conn)
                    PostResult.BAD_REQUEST
                }
                code in 200..299 -> {
                    val response = readBody(conn)
                    if (isOkResponse(response)) PostResult.OK else PostResult.FAILED
                }
                code in 500..599 -> PostResult.FAILED  // transient, retry
                else -> PostResult.FAILED
            }
        } catch (t: Throwable) {
            Log.e(TAG, "post failed for entry #${entry.id} (${entry.category}): ${t.message}", t)
            return PostResult.FAILED
        } finally {
            conn?.disconnect()
        }
    }

    private fun readBody(conn: HttpURLConnection): String {
        val stream = try { conn.inputStream } catch (_: Throwable) { conn.errorStream }
        if (stream == null) return ""
        return BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
    }

    private fun isOkResponse(response: String): Boolean {
        if (response.isBlank()) return false
        return try {
            JSONObject(response).optBoolean("ok", false)
        } catch (_: Throwable) {
            false
        }
    }

    private fun logBadRequest(code: Int, conn: HttpURLConnection) {
        val body = readBody(conn)
        Log.w(TAG, "backend rejected entry (HTTP $code): $body")
    }

    private fun buildJson(entry: Entry): String = buildString {
        append('{')
        append("\"category\":").append(quote(entry.category)).append(',')
        append("\"rawText\":").append(quote(entry.text)).append(',')
        append("\"timestamp\":").append(entry.timestamp)
        if (entry.completed) append(",\"completed\":true")
        append('}')
    }

    private fun quote(value: String): String = buildString {
        append('"')
        for (c in value) {
            when (c) {
                '"'  -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> if (c.code < 0x20)
                    append("\\u00${c.code.toString(16).padStart(2, '0')}")
                else append(c)
            }
        }
        append('"')
    }

    // ─────────────────────────────────────────────────────────────────────
    // Notifications & network
    // ─────────────────────────────────────────────────────────────────────

    private fun buildSyncNotification(text: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("Daymark")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    private fun postPasscodeWarning() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(WARN_CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    WARN_CHANNEL_ID, "Daymark Sync Errors",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { setShowBadge(true) }
            )
        }
        val intent = android.app.PendingIntent.getActivity(
            this, 0,
            Intent(this, SettingsActivity::class.java),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, WARN_CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        builder.setContentTitle("Daymark can't sync")
            .setContentText("Your passcode was rejected. Tap to fix it in Settings.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(intent)
            .setAutoCancel(true)
        nm.notify(WARN_NOTIF_ID, builder.build())
    }

    private fun hasNetwork(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= 23) {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
        @Suppress("DEPRECATION")
        return cm.activeNetworkInfo?.isConnected == true
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(
                CHANNEL_ID, "Daymark Sync",
                NotificationManager.IMPORTANCE_LOW
            )
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
    }

    companion object {
        const val TAG = "DaymarkSync"
        const val CHANNEL_ID = "daymark_sync"
        const val WARN_CHANNEL_ID = "daymark_sync_warn"
        const val NOTIF_ID = 9001
        const val WARN_NOTIF_ID = 9002
    }
}
