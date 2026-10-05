package com.ximanta.opendiary

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.CompoundButton
import android.widget.Switch
import android.widget.TextView
import java.text.DateFormat
import java.util.Calendar

class SettingsActivity : Activity() {
    private lateinit var prefs: android.content.SharedPreferences

    private fun rid(name: String, type: String): Int = resources.getIdentifier(name, type, packageName)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme()
        setContentView(rid("activity_settings", "layout"))
        prefs = getSharedPreferences(Config.PREFS, MODE_PRIVATE)

        val reminderSwitch = findViewById<Switch>(rid("reminderSwitch", "id"))
        reminderSwitch.isChecked = prefs.getBoolean(Config.KEY_REMINDER_ENABLED, Config.DEFAULT_REMINDER_ENABLED)
        reminderSwitch.setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
            prefs.edit().putBoolean(Config.KEY_REMINDER_ENABLED, checked).apply()
            if (checked) AlarmScheduler.scheduleReminder(this, reminderHour(), reminderMinute())
            else AlarmScheduler.cancelReminder(this)
        }
        findViewById<TextView>(rid("reminderTime", "id")).setOnClickListener { chooseTime() }
        findViewById<TextView>(rid("themeSetting", "id")).setOnClickListener { chooseTheme() }
        refreshLabels()
    }

    private fun applyTheme() {
        val mode = getSharedPreferences(Config.PREFS, MODE_PRIVATE)
            .getString(Config.KEY_THEME, Config.THEME_SYSTEM) ?: Config.THEME_SYSTEM
        val dark = mode == Config.THEME_DARK ||
            (mode == Config.THEME_SYSTEM && (resources.configuration.uiMode and 0x30) == 0x20)
        val styleId = resources.getIdentifier(if (dark) "AppTheme_Dark" else "AppTheme", "style", packageName)
        if (styleId != 0) setTheme(styleId)
    }

    private fun reminderHour() = prefs.getInt(Config.KEY_REMINDER_HOUR, Config.DEFAULT_REMINDER_HOUR)
    private fun reminderMinute() = prefs.getInt(Config.KEY_REMINDER_MIN, Config.DEFAULT_REMINDER_MIN)

    private fun chooseTime() {
        TimePickerDialog(this, { _, hour, minute ->
            prefs.edit().putInt(Config.KEY_REMINDER_HOUR, hour).putInt(Config.KEY_REMINDER_MIN, minute).apply()
            if (prefs.getBoolean(Config.KEY_REMINDER_ENABLED, Config.DEFAULT_REMINDER_ENABLED)) {
                AlarmScheduler.scheduleReminder(this, hour, minute)
            }
            refreshLabels()
        }, reminderHour(), reminderMinute(), android.text.format.DateFormat.is24HourFormat(this)).show()
    }

    private fun chooseTheme() {
        val modes = arrayOf("System default", "Light", "Dark")
        val values = arrayOf(Config.THEME_SYSTEM, Config.THEME_LIGHT, Config.THEME_DARK)
        val selected = values.indexOf(prefs.getString(Config.KEY_THEME, Config.THEME_SYSTEM)).coerceAtLeast(0)
        AlertDialog.Builder(this).setTitle("Theme").setSingleChoiceItems(modes, selected) { dialog, which ->
            prefs.edit().putString(Config.KEY_THEME, values[which]).apply()
            dialog.dismiss()
            recreate()
        }.setNegativeButton("Cancel", null).show()
    }

    private fun refreshLabels() {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, reminderHour())
            set(Calendar.MINUTE, reminderMinute())
        }
        findViewById<TextView>(rid("reminderTime", "id")).text =
            DateFormat.getTimeInstance(DateFormat.SHORT).format(calendar.time)
        val theme = when (prefs.getString(Config.KEY_THEME, Config.THEME_SYSTEM)) {
            Config.THEME_LIGHT -> "Light"
            Config.THEME_DARK -> "Dark"
            else -> "System default"
        }
        findViewById<TextView>(rid("themeSummary", "id")).text = theme
    }
}
