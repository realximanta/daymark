package com.ximanta.opendiary

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Entry model.
 *
 * [completed] is only meaningful for task-like categories
 * ("Schedule/Tasks" and "Plan for Tomorrow"). It defaults to false everywhere
 * so existing call sites keep working.
 */
data class Entry(
    val id: Long,
    val category: String,
    val text: String,
    val timestamp: Long,
    val synced: Boolean = false,
    var completed: Boolean = false
)

class EntryDbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        const val DB_NAME = "daymark.db"
        const val DB_VERSION = 3

        const val TBL = "entries"
        const val COL_ID = "_id"
        const val COL_CATEGORY = "category"
        const val COL_TEXT = "text"
        const val COL_TIMESTAMP = "timestamp"
        const val COL_SYNCED = "synced"
        const val COL_COMPLETED = "completed"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE " + TBL + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_CATEGORY + " TEXT NOT NULL, " +
                COL_TEXT + " TEXT NOT NULL, " +
                COL_TIMESTAMP + " INTEGER NOT NULL, " +
                COL_SYNCED + " INTEGER NOT NULL DEFAULT 0, " +
                COL_COMPLETED + " INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_timestamp ON $TBL ($COL_TIMESTAMP DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_category ON $TBL ($COL_CATEGORY)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_synced ON $TBL ($COL_SYNCED)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Never drop journal data during an app update.
        if (oldVersion < 2) {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_timestamp ON $TBL ($COL_TIMESTAMP DESC)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_category ON $TBL ($COL_CATEGORY)")
        }
        if (oldVersion < 3) {
            // Add the completed flag for interactive tasks. Existing rows default to 0.
            db.execSQL("ALTER TABLE $TBL ADD COLUMN $COL_COMPLETED INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_synced ON $TBL ($COL_SYNCED)")
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────

    /** Column list shared by every SELECT so indices stay in sync. */
    private val allColumns = arrayOf(
        COL_ID, COL_CATEGORY, COL_TEXT, COL_TIMESTAMP, COL_SYNCED, COL_COMPLETED
    )

    private fun cursorToEntry(c: Cursor): Entry = Entry(
        id = c.getLong(0),
        category = c.getString(1),
        text = c.getString(2),
        timestamp = c.getLong(3),
        synced = c.getInt(4) != 0,
        completed = c.getInt(5) != 0
    )

    // ─────────────────────────────────────────────────────────────────────
    // Writes
    // ─────────────────────────────────────────────────────────────────────

    fun insertEntry(category: String, text: String, timestamp: Long): Long {
        val cv = ContentValues()
        cv.put(COL_CATEGORY, category)
        cv.put(COL_TEXT, text)
        cv.put(COL_TIMESTAMP, timestamp)
        cv.put(COL_SYNCED, 0)
        cv.put(COL_COMPLETED, 0)
        return writableDatabase.insert(TBL, null, cv)
    }

    fun markSynced(id: Long) {
        val cv = ContentValues()
        cv.put(COL_SYNCED, 1)
        writableDatabase.update(TBL, cv, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun toggleTaskCompleted(id: Long, completed: Boolean) {
        val cv = ContentValues()
        cv.put(COL_COMPLETED, if (completed) 1 else 0)
        writableDatabase.update(TBL, cv, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun deleteEntry(id: Long): Boolean =
        writableDatabase.delete(TBL, "$COL_ID = ?", arrayOf(id.toString())) > 0

    // ─────────────────────────────────────────────────────────────────────
    // Reads
    // ─────────────────────────────────────────────────────────────────────

    fun getUnsynced(): ArrayList<Entry> {
        val list = ArrayList<Entry>()
        readableDatabase.query(
            TBL, allColumns,
            "$COL_SYNCED = 0", null, null, null,
            "$COL_TIMESTAMP ASC"
        ).use { c ->
            while (c.moveToNext()) list.add(cursorToEntry(c))
        }
        return list
    }

    /**
     * Only returns entries that have already been pushed to the backend.
     * Used by the redesigned History screen.
     */
    fun getSyncedEntries(): ArrayList<Entry> {
        val list = ArrayList<Entry>()
        readableDatabase.query(
            TBL, allColumns,
            "$COL_SYNCED = 1", null, null, null,
            "$COL_TIMESTAMP DESC"
        ).use { c ->
            while (c.moveToNext()) list.add(cursorToEntry(c))
        }
        return list
    }

    fun getTasksForCategory(category: String): ArrayList<Entry> {
        val list = ArrayList<Entry>()
        readableDatabase.query(
            TBL, allColumns,
            "$COL_CATEGORY = ?", arrayOf(category), null, null,
            "$COL_TIMESTAMP DESC"
        ).use { c ->
            while (c.moveToNext()) list.add(cursorToEntry(c))
        }
        return list
    }

    fun getAllEntries(): ArrayList<Entry> {
        val list = ArrayList<Entry>()
        readableDatabase.query(
            TBL, allColumns,
            null, null, null, null,
            "$COL_TIMESTAMP DESC"
        ).use { c ->
            while (c.moveToNext()) list.add(cursorToEntry(c))
        }
        return list
    }

    fun getEntryCount(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $TBL", null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    fun getUnsyncedCount(): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TBL WHERE $COL_SYNCED = 0", null
        ).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }
}
