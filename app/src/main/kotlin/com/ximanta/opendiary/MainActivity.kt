package com.ximanta.opendiary

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var db: EntryDbHelper
    private var updateInProgress = false
    private var appliedTheme = ""

    private val categories = arrayOf(
        "Currently Doing", "What I Ate", "New Idea", "Schedule/Tasks", "Regret",
        "Success of the Day", "Travelled To", "Plan for Tomorrow", "Watched"
    )
    private val icons = arrayOf("🏃", "🍽️", "💡", "✅", "😞", "🏆", "🚗", "📅", "🎬")

    private fun rid(name: String, type: String): Int = resources.getIdentifier(name, type, packageName)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyThemeIfNeeded()
        setContentView(rid("activity_main", "layout"))
        appliedTheme = currentThemeKey()
        db = EntryDbHelper(this)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)

        val header = findViewById<LinearLayout>(rid("header", "id"))
        header.alpha = 0f
        header.translationY = -40f
        val grid = findViewById<GridView>(rid("gridButtons", "id"))
        grid.adapter = CategoryAdapter()
        grid.setOnItemClickListener { _, view, position, _ ->
            if (view != null) {
                view.animate().scaleX(0.94f).scaleY(0.94f).setDuration(80).withEndAction {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                }.start()
            }
            val category = categories[position]
            if (category == "Schedule/Tasks" || category == "Plan for Tomorrow") {
                startActivity(Intent(this, TasksActivity::class.java).putExtra("category", category))
            } else showEntryDialog(category)
        }

        findViewById<Button>(rid("historyBtn", "id")).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<Button>(rid("settingsBtn", "id")).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(rid("syncBtn", "id")).setOnClickListener {
            startSyncService(this)
            Toast.makeText(this, "Sync started", Toast.LENGTH_SHORT).show()
            refreshStats()
        }

        scheduleAlarms()
        startSyncService(this)
        checkForUpdates()
    }

    override fun onResume() {
        super.onResume()
        if (::db.isInitialized && appliedTheme != currentThemeKey()) {
            recreate()
            return
        }
        if (!::db.isInitialized) return
        refreshStats()
        val header = findViewById<LinearLayout>(rid("header", "id"))
        header.animate().alpha(1f).translationY(0f).setDuration(500).setStartDelay(50).start()
        val grid = findViewById<GridView>(rid("gridButtons", "id"))
        grid.postDelayed({
            for (i in 0 until grid.childCount) {
                val child = grid.getChildAt(i)
                child.alpha = 0f
                child.translationY = 60f
                child.animate().alpha(1f).translationY(0f).setDuration(350)
                    .setStartDelay((i * 55).toLong()).start()
            }
        }, 120)
    }

    private fun refreshStats() {
        if (!::db.isInitialized) return
        val total = db.getEntryCount()
        val pending = db.getUnsyncedCount()
        val prefs = getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(Config.KEY_LAST_SYNC_AT, 0L)
        val status = when {
            pending > 0 -> "$pending waiting to sync"
            last > 0L && !prefs.getBoolean(Config.KEY_LAST_SYNC_OK, true) -> "Sync needs attention"
            last > 0L -> "All synced"
            else -> "Sync not run yet"
        }
        findViewById<TextView>(rid("statsView", "id")).text = "$total entries • $status"
    }

    private fun scheduleAlarms() {
        AlarmScheduler.scheduleSync(this)
        val prefs = getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(Config.KEY_REMINDER_ENABLED, Config.DEFAULT_REMINDER_ENABLED)) {
            AlarmScheduler.scheduleReminder(
                this,
                prefs.getInt(Config.KEY_REMINDER_HOUR, Config.DEFAULT_REMINDER_HOUR),
                prefs.getInt(Config.KEY_REMINDER_MIN, Config.DEFAULT_REMINDER_MIN)
            )
        } else AlarmScheduler.cancelReminder(this)
    }

    private fun applyThemeIfNeeded() {
        val mode = getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE)
            .getString(Config.KEY_THEME, Config.THEME_SYSTEM) ?: Config.THEME_SYSTEM
        val dark = mode == Config.THEME_DARK ||
            (mode == Config.THEME_SYSTEM && (resources.configuration.uiMode and 0x30) == 0x20)
        val styleId = resources.getIdentifier(if (dark) "AppTheme_Dark" else "AppTheme", "style", packageName)
        if (styleId != 0) setTheme(styleId)
    }

    private fun currentThemeKey(): String =
        getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE)
            .getString(Config.KEY_THEME, Config.THEME_SYSTEM) ?: Config.THEME_SYSTEM

    private fun checkForUpdates() {
        val versionName = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "0.0" }
        catch (_: Exception) { "0.0" }
        UpdateChecker.check(versionName) { info ->
            if (info != null && !updateInProgress) {
                val btn = findViewById<Button>(rid("updateBtn", "id"))
                btn.visibility = View.VISIBLE
                btn.isEnabled = true
                btn.text = "⬆ Update to ${info.tag}"
                btn.setOnClickListener {
                    if (updateInProgress) return@setOnClickListener
                    updateInProgress = true
                    btn.isEnabled = false
                    btn.text = "Preparing…"
                    UpdateDownloader.downloadAndInstall(this, info.apkUrl) { msg, finished ->
                        btn.text = msg
                        if (finished) {
                            updateInProgress = false
                            btn.isEnabled = true
                        }
                    }
                }
            }
        }
    }

    private inner class CategoryAdapter : BaseAdapter() {
        private val inflater = LayoutInflater.from(this@MainActivity)
        override fun getCount(): Int = categories.size
        override fun getItem(position: Int): Any = categories[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: inflater.inflate(rid("item_category", "layout"), parent, false)
            view.findViewById<TextView>(rid("itemIcon", "id")).text = icons[position]
            view.findViewById<TextView>(rid("itemLabel", "id")).text = categories[position]
            return view
        }
    }

    private fun showEntryDialog(category: String) {
        val input = EditText(this)
        input.hint = "Write whatever is on your mind…"
        input.minLines = 5
        input.gravity = android.view.Gravity.TOP
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE
        val dlg = AlertDialog.Builder(this).setTitle(category).setView(input)
            .setPositiveButton("Save", null).setNegativeButton("Cancel", null).create()
        dlg.setOnShowListener(object : DialogInterface.OnShowListener {
            override fun onShow(di: DialogInterface?) {
                dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val text = input.text.toString().trim()
                    if (text.isEmpty()) {
                        Toast.makeText(this@MainActivity, "Type something first", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    if (db.insertEntry(category, text, System.currentTimeMillis()) < 0) {
                        Toast.makeText(this@MainActivity, "Could not save entry", Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                    Toast.makeText(this@MainActivity, "Saved offline; syncing when possible", Toast.LENGTH_SHORT).show()
                    dlg.dismiss()
                    refreshStats()
                    startSyncService(this@MainActivity)
                }
            }
        })
        dlg.show()
    }

    override fun onDestroy() {
        if (::db.isInitialized) db.close()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_NOTIFICATIONS = 1001
        fun startSyncService(ctx: Context) {
            try {
                val intent = Intent(ctx, SyncService::class.java)
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(intent) else ctx.startService(intent)
            } catch (_: Throwable) {
                Toast.makeText(ctx, "Sync could not start", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
