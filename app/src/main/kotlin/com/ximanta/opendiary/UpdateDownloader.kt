package com.ximanta.opendiary

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object UpdateDownloader {
    private const val APK_NAME = "update.apk"
    private const val MAX_APK_BYTES = 100L * 1024L * 1024L
    const val AUTHORITY = "com.ximanta.opendiary.fileprovider"

    fun downloadAndInstall(ctx: Context, apkUrl: String, onProgress: (String, Boolean) -> Unit) {
        Thread {
            var conn: HttpURLConnection? = null
            val outFile = File(ctx.cacheDir, APK_NAME)
            val tempFile = File(ctx.cacheDir, "$APK_NAME.part")
            try {
                if (!apkUrl.startsWith("https://", ignoreCase = true)) {
                    fail(ctx, onProgress, "Update link is not secure")
                    return@Thread
                }
                tempFile.delete()
                outFile.delete()
                conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Daymark-Android")
                    connectTimeout = 20_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                }
                if (conn!!.responseCode !in 200..299) {
                    fail(ctx, onProgress, "Download failed: HTTP ${conn!!.responseCode}")
                    return@Thread
                }
                val announced = conn!!.contentLengthLong
                if (announced > MAX_APK_BYTES) {
                    fail(ctx, onProgress, "Update is too large")
                    return@Thread
                }
                conn!!.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var written = 0L
                        var lastPct = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            written += read
                            if (written > MAX_APK_BYTES) throw IllegalStateException("Update is too large")
                            output.write(buffer, 0, read)
                            if (announced > 0) {
                                val pct = ((written * 100) / announced).toInt().coerceIn(0, 100)
                                if (pct >= lastPct + 10) {
                                    lastPct = pct
                                    postProgress(onProgress, "Downloading $pct%", false)
                                }
                            }
                        }
                    }
                }
                if (!tempFile.renameTo(outFile)) {
                    tempFile.copyTo(outFile, overwrite = true)
                    tempFile.delete()
                }
                if (!isCompatibleApk(ctx, outFile)) {
                    outFile.delete()
                    fail(ctx, onProgress, "Downloaded file is not a Daymark APK")
                    return@Thread
                }
                Handler(Looper.getMainLooper()).post {
                    onProgress("Installing…", false)
                    installApk(ctx, outFile, onProgress)
                }
            } catch (t: Throwable) {
                tempFile.delete()
                fail(ctx, onProgress, "Update failed: ${t.message ?: "unknown error"}")
            } finally {
                conn?.disconnect()
            }
        }.start()
    }

    private fun isCompatibleApk(ctx: Context, apk: File): Boolean {
        val info = ctx.packageManager.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_META_DATA)
        return info?.packageName == ctx.packageName
    }

    private fun installApk(ctx: Context, apk: File, callback: (String, Boolean) -> Unit) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ctx.packageManager.canRequestPackageInstalls()) {
                callback("Allow installs, then tap Update again", true)
                val settingsIntent = Intent(
                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${ctx.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(settingsIntent)
                return
            }
            val uri = Uri.parse("content://$AUTHORITY/${apk.name}")
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ctx.startActivity(intent)
            callback("Installer opened", true)
        } catch (t: Throwable) {
            fail(ctx, callback, "Cannot install: ${t.message ?: "unknown error"}")
        }
    }

    private fun fail(ctx: Context, callback: (String, Boolean) -> Unit, message: String) {
        Handler(Looper.getMainLooper()).post {
            callback(message, true)
            Toast.makeText(ctx, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun postProgress(callback: (String, Boolean) -> Unit, message: String, finished: Boolean) {
        Handler(Looper.getMainLooper()).post { callback(message, finished) }
    }
}
