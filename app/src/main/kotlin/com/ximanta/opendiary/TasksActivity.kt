package com.ximanta.opendiary

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

class TasksActivity : Activity() {

    private lateinit var db: EntryDbHelper
    private var category: String = "Schedule/Tasks"
    private val items = ArrayList<String>()
    private lateinit var adapter: ArrayAdapter<String>

    private fun rid(name: String, type: String): Int =
        resources.getIdentifier(name, type, packageName)

    override fun onCreate(savedInstanceState: Bundle?) {
        // 🔥 FIX: Apply theme before super.onCreate
        applyTheme()

        super.onCreate(savedInstanceState)
        setContentView(rid("activity_tasks", "layout"))

        val c = intent.getStringExtra("category")
        if (c != null) category = c

        db = EntryDbHelper(this)

        val title = findViewById<TextView>(rid("tasksTitle", "id"))
        title.text = category

        val list = findViewById<ListView>(rid("taskList", "id"))
        
        // Use the custom item_task layout
        adapter = ArrayAdapter<String>(
            this, 
            rid("item_task", "layout"), 
            rid("taskText", "id"), 
            items
        )
        list.adapter = adapter

        val input = findViewById<EditText>(rid("taskInput", "id"))
        val btn = findViewById<Button>(rid("addTaskBtn", "id"))
        btn.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                val text = input.text.toString().trim()
                if (text.isEmpty()) {
                    Toast.makeText(this@TasksActivity, "Type a task", Toast.LENGTH_SHORT).show()
                    return
                }
                db.insertEntry(category, text, System.currentTimeMillis())
                input.setText("")
                reload()
                MainActivity.startSyncService(this@TasksActivity)
            }
        })

        reload()
    }

    private fun applyTheme() {
        val mode = getSharedPreferences(Config.PREFS, MODE_PRIVATE)
            .getString(Config.KEY_THEME, Config.THEME_SYSTEM) ?: Config.THEME_SYSTEM
        val dark = mode == Config.THEME_DARK ||
            (mode == Config.THEME_SYSTEM && (resources.configuration.uiMode and 0x30) == 0x20)
            
        // 🔥 FIX: Use direct R.style references. No more silent failures.
        setTheme(if (dark) R.style.AppTheme_Dark else R.style.AppTheme)
    }

    private fun reload() {
        items.clear()
        val list = db.getTasksForCategory(category)
        var i = 0
        while (i < list.size) {
            items.add(list[i].text)
            i++
        }
        adapter.notifyDataSetChanged()

        // Toggle empty state visibility
        val emptyView = findViewById<LinearLayout>(rid("tasksEmpty", "id"))
        val listView = findViewById<ListView>(rid("taskList", "id"))
        if (items.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            listView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            listView.visibility = View.VISIBLE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::db.isInitialized) db.close()
    }
}
