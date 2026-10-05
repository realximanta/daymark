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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var db: EntryDbHelper

    private val categories = arrayOf(
        "Currently Doing",
        "What I Ate",
        "New Idea",
        "Schedule/Tasks",
        "Regret",
        "Success of the Day",
        "Travelled To",
        "Plan for Tomorrow",
        "Watched"
    )

    private val icons = arrayOf(
        "🏃", "🍽️", "💡", "✅", "😞", "🏆", "🚗", "📅", "🎬"
    )

    private fun rid(name: String, type: String): Int =
        resources.getIdentifier(name, type, packageName)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(rid("activity_main", "layout"))

        db = EntryDbHelper(this)

        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }

        val header = findViewById<LinearLayout>(rid("header", "id"))
        header.alpha = 0f
        header.translationY = -40f

        val grid = findViewById<GridView>(rid("gridButtons", "id"))
        grid.adapter = CategoryAdapter()

        grid.setOnItemClickListener(object : AdapterView.OnItemClickListener {
            override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (view != null) {
                    view.animate()
                        .scaleX(0.94f).scaleY(0.94f)
                        .setDuration(80)
                        .withEndAction {
                            view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                        }.start()
                }
                val category = categories[position]
                if (category == "Schedule/Tasks" || category == "Plan for Tomorrow") {
                    val i = Intent(this@MainActivity, TasksActivity::class.java)
                    i.putExtra("category", category)
                    startActivity(i)
                } else {
                    showEntryDialog(category)
                }
            }
        })

        AlarmScheduler.scheduleSync(this)
        val prefs = getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE)
        val h = prefs.getInt(Config.KEY_REMINDER_HOUR, Config.DEFAULT_REMINDER_HOUR)
        val m = prefs.getInt(Config.KEY_REMINDER_MIN, Config.DEFAULT_REMINDER_MIN)
        AlarmScheduler.scheduleReminder(this, h, m)

        startSyncService(this)
    }

    override fun onResume() {
        super.onResume()

        val header = findViewById<LinearLayout>(rid("header", "id"))
        header.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(500)
            .setStartDelay(50)
            .start()

        val grid = findViewById<GridView>(rid("gridButtons", "id"))
        grid.postDelayed({
            for (i in 0 until grid.childCount) {
                val child = grid.getChildAt(i)
                child.alpha = 0f
                child.translationY = 60f
                child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(350)
                    .setStartDelay((i * 55).toLong())
                    .start()
            }
        }, 120)
    }

    private inner class CategoryAdapter : BaseAdapter() {
        private val inflater = LayoutInflater.from(this@MainActivity)

        override fun getCount(): Int = categories.size
        override fun getItem(position: Int): Any = categories[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: inflater.inflate(rid("item_category", "layout"), parent, false)
            val icon = view.findViewById<TextView>(rid("itemIcon", "id"))
            val label = view.findViewById<TextView>(rid("itemLabel", "id"))
            icon.text = icons[position]
            label.text = categories[position]
            return view
        }
    }

    private fun showEntryDialog(category: String) {
        val input = EditText(this)
        input.hint = "Type here"
        input.minLines = 4

        val builder = AlertDialog.Builder(this)
        builder.setTitle(category)
        builder.setView(input)
        builder.setPositiveButton("Save", null)
        builder.setNegativeButton("Cancel", null)

        val dlg = builder.create()
        dlg.setOnShowListener(object : DialogInterface.OnShowListener {
            override fun onShow(di: DialogInterface?) {
                val b = dlg.getButton(AlertDialog.BUTTON_POSITIVE)
                b.setOnClickListener(object : View.OnClickListener {
                    override fun onClick(v: View?) {
                        val text = input.text.toString().trim()
                        if (text.isEmpty()) {
                            Toast.makeText(this@MainActivity, "Type something first", Toast.LENGTH_SHORT).show()
                            return
                        }
                        db.insertEntry(category, text, System.currentTimeMillis())
                        Toast.makeText(this@MainActivity, "Saved", Toast.LENGTH_SHORT).show()
                        dlg.dismiss()
                        startSyncService(this@MainActivity)
                    }
                })
            }
        })
        dlg.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        db.close()
    }

    companion object {
        fun startSyncService(ctx: Context) {
            val i = Intent(ctx, SyncService::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                ctx.startForegroundService(i)
            } else {
                ctx.startService(i)
            }
        }
    }
}
