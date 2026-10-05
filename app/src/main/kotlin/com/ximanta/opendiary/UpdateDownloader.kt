package com.ximanta.opendiary

import android.content.Context
import android.content.Intent
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
    const val AUTHORITY = "com.ximanta.opendiary.fileprovider"

    fun downloadAndInstall(ctx: Context, apkUrl: String, onProgress: (String) -> Unit) {
        Thread {
            try {
                val outFile = File(ctx.cacheDir, APK_NAME)
                if (outFile.exists()) outFile.delete()

                val url = URL(apkUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "Daymark-Android")
                conn.connectTimeout = 20000
                conn.readTimeout = 30000
                conn.instanceFollowRedirects = true

                if (conn.responseCode !in 200..299) {
                    postToast(ctx, "Download failed: HTTP ${conn.responseCode}")
                    return@Thread
                }

                val total = conn.contentLength.toLong()
                conn.inputStream.use { input ->
                    FileOutputStream(outFile).use { output ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        var written = 0L
                        var lastPct = -1
                        while (input.read(buffer).also { read = it } > 0) {
                            output.write(buffer, 0, read)
                            written += read
                            if (total > 0) {
                                val pct = ((written * 100) / total).toInt()
                                if (pct != lastPct && pct % 10 == 0) {
                                    lastPct = pct
                                    postProgress(onProgress, "Downloading $pct%")
                                }
                            }
                        }
                    }
                }
                conn.disconnect()

                Handler(Looper.getMainLooper()).post {
                    onProgress("Installing...")
                    installApk(ctx, outFile)
                }
            } catch (t: Throwable) {
                postToast(ctx, "Update failed: ${t.message}")
            }
        }.start()
    }

    private fun installApk(ctx: Context, apk: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!ctx.packageManager.canRequestPackageInstalls()) {
                    postToast(ctx, "Allow installs from Daymark, then tap Update again")
                    val settingsIntent = Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${ctx.packageName}")
                    )
                    settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(settingsIntent)
                    return
                }
            }

            val uri = Uri.parse("content://$AUTHORITY/${apk.name}")
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ctx.startActivity(intent)
        } catch (t: Throwable) {
            postToast(ctx, "Cannot install: ${t.message}")
        }
    }

    private fun postToast(ctx: Context, msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun postProgress(cb: (String) -> Unit, msg: String) {
        Handler(Looper.getMainLooper()).post { cb(msg) }
    }
}
