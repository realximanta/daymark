// language: Kotlin, file: SettingsActivity.kt, target: Android API 26-35
package com.ximanta.opendiary

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.CompoundButton
import android.widget.Switch
import android.widget.TextView
import java.text.DateFormat
import java.util.Calendar

class SettingsActivity : Activity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        // Theme must be applied before super.onCreate so the window picks
        // up the correct style before the layout inflates.
        applyTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = getSharedPreferences(Config.PREFS, MODE_PRIVATE)

        // ── Reminders ────────────────────────────────────────────────
        findViewById<Switch>(R.id.reminderSwitch)?.let { switch ->
            switch.isChecked = prefs.getBoolean(
                Config.KEY_REMINDER_ENABLED,
                Config.DEFAULT_REMINDER_ENABLED
            )
            switch.setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
                prefs.edit().putBoolean(Config.KEY_REMINDER_ENABLED, checked).apply()
                if (checked) {
                    AlarmScheduler.scheduleReminder(this, reminderHour(), reminderMinute())
                } else {
                    AlarmScheduler.cancelReminder(this)
                }
            }
        }

        findViewById<TextView>(R.id.reminderTime)
            ?.setOnClickListener { chooseTime() }

        // ── Theme ────────────────────────────────────────────────────
        findViewById<TextView>(R.id.themeSetting)
            ?.setOnClickListener { chooseTheme() }

        // ── Security → Passcode ──────────────────────────────────────
        findViewById<TextView>(R.id.passcodeSetup)
            ?.setOnClickListener {
                startActivity(Intent(this, PasscodeActivity::class.java))
            }

        refreshLabels()
    }

    override fun onResume() {
        super.onResume()
        refreshLabels()
    }

    private fun applyTheme() {
        val mode = getSharedPreferences(Config.PREFS, MODE_PRIVATE)
            .getString(Config.KEY_THEME, Config.THEME_SYSTEM) ?: Config.THEME_SYSTEM
        val dark = mode == Config.THEME_DARK ||
            (mode == Config.THEME_SYSTEM && (resources.configuration.uiMode and 0x30) == 0x20)
        setTheme(if (dark) R.style.AppTheme_Dark else R.style.AppTheme)
    }

    private fun reminderHour() =
        prefs.getInt(Config.KEY_REMINDER_HOUR, Config.DEFAULT_REMINDER_HOUR)

    private fun reminderMinute() =
        prefs.getInt(Config.KEY_REMINDER_MIN, Config.DEFAULT_REMINDER_MIN)

    private fun chooseTime() {
        TimePickerDialog(
            this,
            { _, hour, minute ->
                prefs.edit()
                    .putInt(Config.KEY_REMINDER_HOUR, hour)
                    .putInt(Config.KEY_REMINDER_MIN, minute)
                    .apply()
                if (prefs.getBoolean(Config.KEY_REMINDER_ENABLED, Config.DEFAULT_REMINDER_ENABLED)) {
                    AlarmScheduler.scheduleReminder(this, hour, minute)
                }
                refreshLabels()
            },
            reminderHour(),
            reminderMinute(),
            android.text.format.DateFormat.is24HourFormat(this)
        ).show()
    }

    private fun chooseTheme() {
        val modes = arrayOf("System default", "Light", "Dark")
        val values = arrayOf(Config.THEME_SYSTEM, Config.THEME_LIGHT, Config.THEME_DARK)
        val selected = values.indexOf(
            prefs.getString(Config.KEY_THEME, Config.THEME_SYSTEM)
        ).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("Theme")
            .setSingleChoiceItems(modes, selected) { dialog, which ->
                prefs.edit().putString(Config.KEY_THEME, values[which]).apply()
                dialog.dismiss()
                recreate()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshLabels() {
        if (!::prefs.isInitialized) return

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, reminderHour())
            set(Calendar.MINUTE, reminderMinute())
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        findViewById<TextView>(R.id.reminderTime)?.text =
            DateFormat.getTimeInstance(DateFormat.SHORT).format(calendar.time)

        val themeSummary = when (prefs.getString(Config.KEY_THEME, Config.THEME_SYSTEM)) {
            Config.THEME_LIGHT -> "Light"
            Config.THEME_DARK -> "Dark"
            else -> "System default"
        }
        findViewById<TextView>(R.id.themeSummary)?.text = themeSummary

        val last = prefs.getLong(Config.KEY_LAST_SYNC_AT, 0L)
        val ok = prefs.getBoolean(Config.KEY_LAST_SYNC_OK, true)
        val err = prefs.getString(Config.KEY_LAST_SYNC_ERROR, null)
        val hasPass = PasscodeStore.hasPasscode(this)

        val syncText = when {
            !hasPass -> "Passcode not set — sync will fail until you set it above."
            last == 0L -> "Sync endpoint configured • not run yet"
            !ok && err != null -> "Last sync failed • $err"
            !ok -> "Last sync had an issue"
            else -> "Last sync: ${humanizeAge(last)} ago • OK"
        }
        findViewById<TextView>(R.id.syncEndpoint)?.text = syncText
    }

    private fun humanizeAge(timestampMs: Long): String {
        val seconds = ((System.currentTimeMillis() - timestampMs) / 1000).coerceAtLeast(0)
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m"
            seconds < 86_400 -> "${seconds / 3600}h"
            else -> "${seconds / 86_400}d"
        }
    }
}
