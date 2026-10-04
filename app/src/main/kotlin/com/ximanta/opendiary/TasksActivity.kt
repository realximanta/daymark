package com.ximanta.opendiary

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
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
        super.onCreate(savedInstanceState)
        setContentView(rid("activity_tasks", "layout"))

        val c = intent.getStringExtra("category")
        if (c != null) category = c

        db = EntryDbHelper(this)

        val title = findViewById<TextView>(rid("tasksTitle", "id"))
        title.text = category

        val list = findViewById<ListView>(rid("taskList", "id"))
        adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, items)
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

    private fun reload() {
        items.clear()
        val list = db.getTasksForCategory(category)
        var i = 0
        while (i < list.size) {
            items.add(list[i].text)
            i++
        }
        adapter.notifyDataSetChanged()
    }

    override fun onDestroy() {
        super.onDestroy()
        db.close()
    }
}
