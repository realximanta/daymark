package com.ximanta.opendiary

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.CompoundButton
import android.widget.Switch
import android.widget.TextView
import java.text.DateFormat
import java.util.Calendar

class SettingsActivity : Activity() {

    private lateinit var prefs: android.content.SharedPreferences

    private fun rid(name: String, type: String): Int =
        resources.getIdentifier(name, type, packageName)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme()
        setContentView(rid("activity_settings", "layout"))
        prefs = getSharedPreferences(Config.PREFS, MODE_PRIVATE)

        // ── 🔔 Reminders ────────────────────────────────────────────────
        findViewById<Switch>(rid("reminderSwitch", "id"))?.let { switch ->
            switch.isChecked =
                prefs.getBoolean(Config.KEY_REMINDER_ENABLED, Config.DEFAULT_REMINDER_ENABLED)
            switch.setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
                prefs.edit().putBoolean(Config.KEY_REMINDER_ENABLED, checked).apply()
                if (checked) {
                    AlarmScheduler.scheduleReminder(this, reminderHour(), reminderMinute())
                } else {
                    AlarmScheduler.cancelReminder(this)
                }
            }
        }

        findViewById<TextView>(rid("reminderTime", "id"))
            ?.setOnClickListener { chooseTime() }

        // ── 🎨 Theme ────────────────────────────────────────────────────
        findViewById<TextView>(rid("themeSetting", "id"))
            ?.setOnClickListener { chooseTheme() }

        // ── 🔐 Security → Reset passcode ────────────────────────────────
        findViewById<TextView>(rid("passcodeReset", "id"))
            ?.setOnClickListener { confirmPasscodeReset() }

        refreshLabels()
    }

    override fun onResume() {
        super.onResume()
        refreshLabels()
    }

    // ═══════════════════════════════════════════════════════════════════
    // Theme
    // ═══════════════════════════════════════════════════════════════════

    private fun applyTheme() {
        val mode = getSharedPreferences(Config.PREFS, MODE_PRIVATE)
            .getString(Config.KEY_THEME, Config.THEME_SYSTEM) ?: Config.THEME_SYSTEM
        val dark = mode == Config.THEME_DARK ||
            (mode == Config.THEME_SYSTEM && (resources.configuration.uiMode and 0x30) == 0x20)
        val styleId = resources.getIdentifier(
            if (dark) "AppTheme_Dark" else "AppTheme", "style", packageName
        )
        if (styleId != 0) setTheme(styleId)
    }

    // ═══════════════════════════════════════════════════════════════════
    // Reminders
    // ═══════════════════════════════════════════════════════════════════

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

    // ═══════════════════════════════════════════════════════════════════
    // Theme picker
    // ═══════════════════════════════════════════════════════════════════

    private fun chooseTheme() {
        val modes = arrayOf("System default", "Light", "Dark")
        val values = arrayOf(Config.THEME_SYSTEM, Config.THEME_LIGHT, Config.THEME_DARK)
        val selected = values
            .indexOf(prefs.getString(Config.KEY_THEME, Config.THEME_SYSTEM))
            .coerceAtLeast(0)

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

    // ═══════════════════════════════════════════════════════════════════
    // Passcode reset
    // ═══════════════════════════════════════════════════════════════════

    private fun confirmPasscodeReset() {
        AlertDialog.Builder(this)
            .setTitle("Reset passcode?")
            .setMessage(
                "Your stored passcode will be wiped from this device. " +
                    "You'll need to enter it again to keep syncing.\n\n" +
                    "Entries already on GitHub are not affected."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Reset") { _, _ -> performPasscodeReset() }
            .show()
    }

    private fun performPasscodeReset() {
        PasscodeStore.clear(this)
        val intent = Intent(this, PasscodeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
    }

    // ═══════════════════════════════════════════════════════════════════
    // Label refresh
    // ═══════════════════════════════════════════════════════════════════

    private fun refreshLabels() {
        // Reminder time
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, reminderHour())
            set(Calendar.MINUTE, reminderMinute())
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        findViewById<TextView>(rid("reminderTime", "id"))?.text =
            DateFormat.getTimeInstance(DateFormat.SHORT).format(calendar.time)

        // Theme summary
        val themeSummary = when (prefs.getString(Config.KEY_THEME, Config.THEME_SYSTEM)) {
            Config.THEME_LIGHT -> "Light"
            Config.THEME_DARK -> "Dark"
            else -> "System default"
        }
        findViewById<TextView>(rid("themeSummary", "id"))?.text = themeSummary

        // Sync status line
        val last = prefs.getLong(Config.KEY_LAST_SYNC_AT, 0L)
        val ok = prefs.getBoolean(Config.KEY_LAST_SYNC_OK, true)
        val err = prefs.getString(Config.KEY_LAST_SYNC_ERROR, null)

        val syncText = when {
            last == 0L -> "Sync endpoint configured • not run yet"
            !ok && err != null -> "Last sync failed • $err"
            !ok -> "Last sync had an issue"
            else -> "Last sync: ${humanizeAge(last)} ago • OK"
        }
        findViewById<TextView>(rid("syncEndpoint", "id"))?.text = syncText
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
