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

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tasks)

        intent.getStringExtra("category")?.let { category = it }

        db = EntryDbHelper(this)

        findViewById<TextView>(R.id.tasksTitle).text = category

        val list = findViewById<ListView>(R.id.taskList)
        adapter = ArrayAdapter<String>(
            this,
            R.layout.item_task,
            R.id.taskText,
            items
        )
        list.adapter = adapter

        val input = findViewById<EditText>(R.id.taskInput)
        findViewById<Button>(R.id.addTaskBtn).setOnClickListener(object : View.OnClickListener {
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

        val emptyView = findViewById<LinearLayout>(R.id.tasksEmpty)
        val listView = findViewById<ListView>(R.id.taskList)
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
