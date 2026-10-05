package com.ximanta.opendiary

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import java.text.DateFormat
import java.util.Date

class HistoryActivity : Activity() {
    private lateinit var db: EntryDbHelper
    private lateinit var adapter: HistoryAdapter
    private val allEntries = ArrayList<Entry>()
    private val visibleEntries = ArrayList<Entry>()

    private fun rid(name: String, type: String): Int = resources.getIdentifier(name, type, packageName)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme()
        setContentView(rid("activity_history", "layout"))
        db = EntryDbHelper(this)
        val list = findViewById<ListView>(rid("historyList", "id"))
        adapter = HistoryAdapter()
        list.adapter = adapter
        list.setOnItemLongClickListener { _, _, position, _ ->
            confirmDelete(visibleEntries[position])
            true
        }
        findViewById<EditText>(rid("historySearch", "id")).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { filter(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        reload()
    }

    private fun applyTheme() {
        val mode = getSharedPreferences(Config.PREFS, MODE_PRIVATE)
            .getString(Config.KEY_THEME, Config.THEME_SYSTEM) ?: Config.THEME_SYSTEM
        val dark = mode == Config.THEME_DARK ||
            (mode == Config.THEME_SYSTEM && (resources.configuration.uiMode and 0x30) == 0x20)
        val styleId = resources.getIdentifier(if (dark) "AppTheme_Dark" else "AppTheme", "style", packageName)
        if (styleId != 0) setTheme(styleId)
    }

    private fun reload() {
        allEntries.clear()
        allEntries.addAll(db.getAllEntries())
        filter(findViewById<EditText>(rid("historySearch", "id")).text.toString())
    }

    private fun filter(query: String) {
        val q = query.trim().lowercase()
        visibleEntries.clear()
        for (entry in allEntries) {
            if (q.isEmpty() || entry.category.lowercase().contains(q) || entry.text.lowercase().contains(q)) {
                visibleEntries.add(entry)
            }
        }
        adapter.notifyDataSetChanged()
        findViewById<TextView>(rid("historyEmpty", "id")).visibility =
            if (visibleEntries.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun confirmDelete(entry: Entry) {
        AlertDialog.Builder(this)
            .setTitle("Delete entry?")
            .setMessage("This removes the local copy. A synced copy in your GitHub journal is not changed.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                db.deleteEntry(entry.id)
                reload()
            }.show()
    }

    private inner class HistoryAdapter : ArrayAdapter<Entry>(this, 0, visibleEntries) {
        private val inflater = LayoutInflater.from(this@HistoryActivity)
        private val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: inflater.inflate(rid("item_history", "layout"), parent, false)
            val entry = visibleEntries[position]
            view.findViewById<TextView>(rid("historyCategory", "id")).text = entry.category
            val syncLabel = if (entry.synced) "Synced" else "Waiting to sync"
            view.findViewById<TextView>(rid("historyDate", "id")).text =
                "${dateFormat.format(Date(entry.timestamp))} • $syncLabel"
            view.findViewById<TextView>(rid("historyText", "id")).text = entry.text
            return view
        }
    }

    override fun onDestroy() {
        if (::db.isInitialized) db.close()
        super.onDestroy()
    }
}
