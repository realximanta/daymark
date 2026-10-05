package com.ximanta.opendiary

import android.os.Handler
import android.os.Looper
import android.util.JsonReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

data class UpdateInfo(val tag: String, val apkUrl: String)

object UpdateChecker {

    private const val RELEASES_API =
        "https://api.github.com/repos/realximanta/daymark/releases/latest"

    fun check(currentVersionName: String, callback: (UpdateInfo?) -> Unit) {
        Thread {
            val result = try {
                fetchLatest(currentVersionName)
            } catch (t: Throwable) {
                null
            }
            Handler(Looper.getMainLooper()).post { callback(result) }
        }.start()
    }

    private fun fetchLatest(currentVersionName: String): UpdateInfo? {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(RELEASES_API)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "Daymark-Android")
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            if (conn.responseCode != 200) return null

            val reader = JsonReader(
                InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)
            )
            var tag: String? = null
            var apkUrl: String? = null

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "tag_name" -> tag = reader.nextString()
                    "assets" -> {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            reader.beginObject()
                            var name = ""
                            var url = ""
                            while (reader.hasNext()) {
                                when (reader.nextName()) {
                                    "name" -> name = reader.nextString()
                                    "browser_download_url" -> url = reader.nextString()
                                    else -> reader.skipValue()
                                }
                            }
                            reader.endObject()
                            if (name.endsWith(".apk", ignoreCase = true) && apkUrl == null) {
                                apkUrl = url
                            }
                        }
                        reader.endArray()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            reader.close()

            if (tag == null || apkUrl == null) return null

            val latest = tag.removePrefix("v")
            val current = currentVersionName.removePrefix("v")
            if (compareVersions(latest, current) > 0) {
                return UpdateInfo(tag, apkUrl)
            }
            return null
        } finally {
            conn?.disconnect()
        }
    }

    private fun compareVersions(a: String, b: String): Int {
        val aParts = a.split(".").map { it.toIntOrNull() ?: 0 }
        val bParts = b.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(aParts.size, bParts.size)
        for (i in 0 until maxLen) {
            val av = if (i < aParts.size) aParts[i] else 0
            val bv = if (i < bParts.size) bParts[i] else 0
            if (av != bv) return av - bv
        }
        return 0
    }
}
