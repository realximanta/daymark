// language: Kotlin, file: MainActivity.kt, target: Android API 26-35
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
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var db: EntryDbHelper
    private var updateInProgress = false
    private var appliedTheme = ""

    private lateinit var drawer: LinearLayout
    private lateinit var scrim: View
    private lateinit var menuBtn: ImageButton
    private var drawerOpen = false
    private val drawerWidthPx by lazy { (290 * resources.displayMetrics.density).toInt() }

    private var pendingUpdateTag: String? = null
    private var downX = 0f

    private val categories = arrayOf(
        "Currently Doing", "What I Ate", "New Idea", "Schedule/Tasks", "Regret",
        "Success of the Day", "Travelled To", "Plan for Tomorrow", "Watched"
    )
    private val icons = arrayOf("🏃", "🍽️", "💡", "✅", "😞", "🏆", "🚗", "📅", "🎬")

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemeIfNeeded()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        appliedTheme = currentThemeKey()

        db = EntryDbHelper(this)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }

        setupDrawer()
        setupGrid()
        setupVersionFooter()

        // Populates dateText and adapts grid columns for tablets. Was never
        // called — the hero date stayed static and tablets stayed 2-column.
        DaymarkPolish.enhanceHome(this)

        scheduleAlarms()
        maybeStartSync()
        checkForUpdates()
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev != null) {
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> downX = ev.x
                MotionEvent.ACTION_UP -> {
                    val deltaX = ev.x - downX
                    if (!drawerOpen && downX < resources.displayMetrics.widthPixels * 0.15f && deltaX > 150) {
                        openDrawer()
                    } else if (drawerOpen && deltaX < -150) {
                        closeDrawer()
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun maybeStartSync() {
        if (PasscodeStore.hasPasscode(this)) {
            startSyncService(this)
        } else {
            Toast.makeText(this, "Tip: set your passcode in Preferences to enable syncing.", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupDrawer() {
        drawer = findViewById(R.id.navDrawer)
        scrim = findViewById(R.id.scrim)
        menuBtn = findViewById(R.id.menuBtn)

        menuBtn.setOnClickListener { if (drawerOpen) closeDrawer() else openDrawer() }
        scrim.setOnClickListener { closeDrawer() }

        findViewById<View>(R.id.navHistory).setOnClickListener {
            closeDrawer()
            startActivity(Intent(this, HistoryActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        findViewById<View>(R.id.navSettings).setOnClickListener {
            closeDrawer()
            startActivity(Intent(this, SettingsActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        findViewById<View>(R.id.navSync).setOnClickListener {
            closeDrawer()
            if (PasscodeStore.hasPasscode(this)) {
                startSyncService(this)
                Toast.makeText(this, "Sync started", Toast.LENGTH_SHORT).show()
                refreshStats()
            } else {
                Toast.makeText(this, "No passcode set. Open Preferences → Security to set one.", Toast.LENGTH_LONG).show()
            }
        }

        findViewById<Button>(R.id.updateBtn).setOnClickListener { triggerUpdateInstall() }

        findViewById<ImageButton>(R.id.syncIconBtn).setOnClickListener {
            if (PasscodeStore.hasPasscode(this)) {
                startSyncService(this)
                Toast.makeText(this, "Sync started", Toast.LENGTH_SHORT).show()
                refreshStats()
            } else {
                Toast.makeText(this, "No passcode set. Open Preferences → Security to set one.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openDrawer() {
        if (drawerOpen) return
        drawerOpen = true
        scrim.visibility = View.VISIBLE
        scrim.alpha = 0f
        scrim.animate().alpha(1f).setDuration(220).start()
        drawer.animate().translationX(0f).setDuration(300)
            .setInterpolator(OvershootInterpolator(0.85f)).start()
        menuBtn.animate().rotation(90f).setDuration(260).start()
        menuBtn.postDelayed({ menuBtn.setImageResource(R.drawable.ic_close) }, 90)
    }

    private fun closeDrawer() {
        if (!drawerOpen) return
        drawerOpen = false
        scrim.animate().alpha(0f).setDuration(200)
            .withEndAction { scrim.visibility = View.GONE }.start()
        drawer.animate().translationX(-drawerWidthPx.toFloat() - 30f).setDuration(240).start()
        menuBtn.animate().rotation(0f).setDuration(240).start()
        menuBtn.postDelayed({ menuBtn.setImageResource(R.drawable.ic_menu) }, 90)
    }

    override fun onBackPressed() {
        if (drawerOpen) closeDrawer() else super.onBackPressed()
    }

    private fun setupGrid() {
        val grid = findViewById<GridView>(R.id.gridButtons)
        grid.adapter = CategoryAdapter()
        grid.setOnItemClickListener { _, view, position, _ ->
            view?.animate()?.scaleX(0.93f)?.scaleY(0.93f)?.setDuration(80)?.withEndAction {
                view.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
            }?.start()

            val category = categories[position]
            if (category == "Schedule/Tasks" || category == "Plan for Tomorrow") {
                startActivity(Intent(this, TasksActivity::class.java).putExtra("category", category))
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            } else {
                showEntryDialog(category, icons[position])
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::db.isInitialized && appliedTheme != currentThemeKey()) {
            recreate()
            return
        }
        if (!::db.isInitialized) return
        refreshStats()

        val header = findViewById<LinearLayout>(R.id.header)
        header.alpha = 0f
        header.translationY = -30f
        header.animate().alpha(1f).translationY(0f).setDuration(480).setStartDelay(40).start()

        val grid = findViewById<GridView>(R.id.gridButtons)
        grid.postDelayed({
            for (i in 0 until grid.childCount) {
                val child = grid.getChildAt(i)
                child.alpha = 0f
                child.translationY = 70f
                child.animate().alpha(1f).translationY(0f).setDuration(360)
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
            !PasscodeStore.hasPasscode(this) -> "Set passcode to sync"
            pending > 0 -> "$pending waiting to sync"
            last > 0L && !prefs.getBoolean(Config.KEY_LAST_SYNC_OK, true) -> "Sync needs attention"
            last > 0L -> "All synced"
            else -> "Sync not run yet"
        }
        findViewById<TextView>(R.id.statsView).text = "$total entries • $status"
    }

    private fun setupVersionFooter() {
        try {
            val vn = packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
            findViewById<TextView>(R.id.versionFooter).text = "Daymark v$vn"
        } catch (_: Throwable) { }
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
        } else {
            AlarmScheduler.cancelReminder(this)
        }
    }

    private fun applyThemeIfNeeded() {
        val mode = getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE)
            .getString(Config.KEY_THEME, Config.THEME_SYSTEM) ?: Config.THEME_SYSTEM
        val dark = mode == Config.THEME_DARK ||
            (mode == Config.THEME_SYSTEM && (resources.configuration.uiMode and 0x30) == 0x20)
        setTheme(if (dark) R.style.AppTheme_Dark else R.style.AppTheme)
    }

    private fun currentThemeKey(): String =
        getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE)
            .getString(Config.KEY_THEME, Config.THEME_SYSTEM) ?: Config.THEME_SYSTEM

    private fun checkForUpdates() {
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "0.0"
        } catch (_: Exception) { "0.0" }

        UpdateChecker.check(versionName) { info ->
            if (info != null && !updateInProgress) {
                pendingUpdateTag = info.tag
                val btn = findViewById<Button>(R.id.updateBtn)
                btn.visibility = View.VISIBLE
                btn.text = "⬆ Update to ${info.tag}"
            }
        }
    }

    private fun triggerUpdateInstall() {
        if (updateInProgress) return
        val tag = pendingUpdateTag ?: return
        val btn = findViewById<Button>(R.id.updateBtn)
        updateInProgress = true
        btn.isEnabled = false
        closeDrawer()
        btn.postDelayed({
            btn.text = "Preparing…"
            val versionName = try {
                packageManager.getPackageInfo(packageName, 0).versionName ?: "0.0"
            } catch (_: Exception) { "0.0" }

            UpdateChecker.check(versionName) { info2 ->
                if (info2 == null) {
                    updateInProgress = false
                    btn.isEnabled = true
                    btn.text = "⬆ Update to $tag"
                    return@check
                }
                UpdateDownloader.downloadAndInstall(this, info2.apkUrl) { msg, finished ->
                    btn.text = msg
                    if (finished) { updateInProgress = false; btn.isEnabled = true }
                }
            }
        }, 250)
    }

    private inner class CategoryAdapter : BaseAdapter() {
        private val inflater = LayoutInflater.from(this@MainActivity)

        override fun getCount(): Int = categories.size
        override fun getItem(position: Int): Any = categories[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: inflater.inflate(R.layout.item_category, parent, false)
            view.findViewById<TextView>(R.id.itemIcon).text = icons[position]
            view.findViewById<TextView>(R.id.itemLabel).text = categories[position]
            // Per-card description + badge tint. Was never called — cards showed
            // whatever the layout shipped and stayed un-tinted.
            DaymarkPolish.bindCategory(view, position)
            return view
        }
    }

    private fun showEntryDialog(category: String, emoji: String) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_entry, null)
        val emojiView = view.findViewById<TextView>(R.id.dialogEmoji)
        val titleView = view.findViewById<TextView>(R.id.dialogTitle)
        val input = view.findViewById<EditText>(R.id.dialogInput)
        val counter = view.findViewById<TextView>(R.id.dialogCounter)

        emojiView.text = emoji
        titleView.text = category

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                counter.text = "${s?.length ?: 0} / 2000"
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        val dlg = AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

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
                    if (PasscodeStore.hasPasscode(this@MainActivity)) startSyncService(this@MainActivity)
                }
            }
        })
        dlg.show()
        input.requestFocus()
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
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(intent)
                else ctx.startService(intent)
            } catch (_: Throwable) {
                Toast.makeText(ctx, "Sync could not start", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
