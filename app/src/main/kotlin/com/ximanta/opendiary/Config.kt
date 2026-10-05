package com.ximanta.opendiary

object Config {
    const val BACKEND_URL = "https://daymark-backend-e18f.onrender.com"
    const val SYNC_INTERVAL_MS = 15L * 60L * 1000L
    const val PREFS = "daymark_prefs"
    const val KEY_REMINDER_HOUR = "reminder_hour"
    const val KEY_REMINDER_MIN = "reminder_min"
    const val KEY_REMINDER_ENABLED = "reminder_enabled"
    const val KEY_THEME = "theme"
    const val KEY_LAST_SYNC_AT = "last_sync_at"
    const val KEY_LAST_SYNC_OK = "last_sync_ok"
    const val KEY_LAST_SYNC_ERROR = "last_sync_error"
    const val DEFAULT_REMINDER_HOUR = 21
    const val DEFAULT_REMINDER_MIN = 0
    const val DEFAULT_REMINDER_ENABLED = true
    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"
}
