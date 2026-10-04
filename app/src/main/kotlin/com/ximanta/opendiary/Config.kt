package com.ximanta.opendiary

object Config {
    const val BACKEND_URL = "https://daymark-backend-e18f.onrender.com"
    const val SECRET = "REPLACE_WITH_YOUR_SECRET"
    const val DEVICE_ID = "daymark-phone-1"
    const val SYNC_INTERVAL_MS = 15L * 60L * 1000L
    const val PREFS = "daymark_prefs"
    const val KEY_REMINDER_HOUR = "reminder_hour"
    const val KEY_REMINDER_MIN = "reminder_min"
    const val DEFAULT_REMINDER_HOUR = 21
    const val DEFAULT_REMINDER_MIN = 0
}
