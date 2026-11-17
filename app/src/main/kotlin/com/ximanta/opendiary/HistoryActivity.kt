package com.ximanta.opendiary

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.text.DateFormat
import java.util.Date

class HistoryActivity : Activity() {

    private lateinit var db: EntryDbHelper
    private lateinit var adapter: HistoryAdapter
    private lateinit var listView: ListView
    private val allEntries = ArrayList<Entry>()
    private val visibleEntries = ArrayList<Entry>()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        db = EntryDbHelper(this)

        listView = findViewById(R.id.historyList)
        adapter = HistoryAdapter()
        listView.adapter = adapter

        listView.setOnItemLongClickListener { _, _, position, _ ->
            visibleEntries.getOrNull(position)?.let { showDeleteOptions(it) }
            true
        }

        attachSwipeToDelete()

        findViewById<EditText>(R.id.historySearch).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        reload()
    }

    override fun onResume() {
        super.onResume()
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
        if (!::db.isInitialized) return
        allEntries.clear()
        allEntries.addAll(db.getSyncedEntries())
        val query = findViewById<EditText>(R.id.historySearch).text?.toString().orEmpty()
        filter(query)
    }

    private fun filter(query: String) {
        val q = query.trim().lowercase()
        visibleEntries.clear()
        for (entry in allEntries) {
            if (q.isEmpty() ||
                entry.category.lowercase().contains(q) ||
                entry.text.lowercase().contains(q)
            ) {
                visibleEntries.add(entry)
            }
        }
        adapter.notifyDataSetChanged()
        toggleEmptyState()
    }

    private fun toggleEmptyState() {
        val empty = findViewById<View>(R.id.historyEmpty)
        val isEmpty = visibleEntries.isEmpty()
        empty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        listView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun attachSwipeToDelete() {
        listView.setOnTouchListener(object : View.OnTouchListener {
            private var downX = 0f
            private var downY = 0f
            private var position = ListView.INVALID_POSITION
            private var swiping = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.x
                        downY = event.y
                        position = listView.pointToPosition(event.x.toInt(), event.y.toInt())
                        swiping = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.x - downX
                        val dy = event.y - downY
                        if (dx < -30 && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.5f) {
                            swiping = true
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (swiping && position != ListView.INVALID_POSITION) {
                            visibleEntries.getOrNull(position)?.let { showDeleteOptions(it) }
                            swiping = false
                            return true
                        }
                        swiping = false
                    }
                    MotionEvent.ACTION_CANCEL -> swiping = false
                }
                return false
            }
        })
    }

    private fun showDeleteOptions(entry: Entry) {
        AlertDialog.Builder(this)
            .setTitle("Delete this entry?")
            .setMessage(
                "Choose how to delete.\n\n" +
                    "• Locally — removes it from this device only.\n" +
                    "• From GitHub — also removes it from your journal repo. " +
                    "This cannot be undone."
            )
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Delete locally") { _, _ -> deleteLocally(entry) }
            .setPositiveButton("Delete from GitHub") { _, _ -> deleteFromGitHub(entry) }
            .show()
    }

    private fun deleteLocally(entry: Entry) {
        db.deleteEntry(entry.id)
        Toast.makeText(this, "Entry deleted locally.", Toast.LENGTH_SHORT).show()
        reload()
    }

    private fun deleteFromGitHub(entry: Entry) {
        val passcode = PasscodeStore.get(this)
        if (passcode.isNullOrEmpty()) {
            Toast.makeText(this, "No passcode set — open Settings first.", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(this, "Deleting from GitHub…", Toast.LENGTH_SHORT).show()

        Thread {
            val result = requestBackendDelete(entry, passcode)
            mainHandler.post {
                when (result) {
                    BackendDeleteResult.OK -> {
                        db.deleteEntry(entry.id)
                        Toast.makeText(this, "Removed from GitHub.", Toast.LENGTH_SHORT).show()
                        reload()
                    }
                    BackendDeleteResult.NOT_FOUND -> {
                        db.deleteEntry(entry.id)
                        Toast.makeText(this, "Not on GitHub — cleaned up locally.", Toast.LENGTH_SHORT).show()
                        reload()
                    }
                    BackendDeleteResult.AUTH_FAILED -> {
                        Toast.makeText(this, "Passcode rejected. Check Settings.", Toast.LENGTH_LONG).show()
                    }
                    BackendDeleteResult.FAILED -> {
                        Toast.makeText(this, "Could not reach GitHub. Try again.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }.start()
    }

    private enum class BackendDeleteResult { OK, NOT_FOUND, AUTH_FAILED, FAILED }

    private fun requestBackendDelete(entry: Entry, passcode: String): BackendDeleteResult {
        var conn: HttpURLConnection? = null
        try {
            val body = JSONObject()
                .put("category", entry.category)
                .put("timestamp", entry.timestamp)
                .toString()
            val bytes = body.toByteArray(StandardCharsets.UTF_8)

            conn = (URL(Config.BACKEND_URL + "/entry").openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Daymark-Android")
                setRequestProperty("Authorization", "Bearer $passcode")
                setFixedLengthStreamingMode(bytes.size)
                doOutput = true
                connectTimeout = 25_000
                readTimeout = 30_000
            }

            conn.outputStream.use { it.write(bytes) }

            return when (val code = conn.responseCode) {
                in 200..299 -> BackendDeleteResult.OK
                404 -> BackendDeleteResult.NOT_FOUND
                401, 403 -> BackendDeleteResult.AUTH_FAILED
                else -> {
                    Log.w(TAG, "delete failed with HTTP $code")
                    BackendDeleteResult.FAILED
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "delete request failed: ${t.message}", t)
            return BackendDeleteResult.FAILED
        } finally {
            conn?.disconnect()
        }
    }

    private inner class HistoryAdapter : BaseAdapter() {
        private val inflater = LayoutInflater.from(this@HistoryActivity)
        private val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

        override fun getCount(): Int = visibleEntries.size
        override fun getItem(position: Int): Any = visibleEntries[position]
        override fun getItemId(position: Int): Long = visibleEntries[position].id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: inflater.inflate(R.layout.item_history, parent, false)
            val entry = visibleEntries[position]

            view.findViewById<TextView>(R.id.historyCategory).text = entry.category
            view.findViewById<TextView>(R.id.historyDate).text =
                dateFormat.format(Date(entry.timestamp))
            view.findViewById<TextView>(R.id.historyText).text = entry.text
            return view
        }
    }

    override fun onDestroy() {
        if (::db.isInitialized) db.close()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "DaymarkHistory"
    }
}
