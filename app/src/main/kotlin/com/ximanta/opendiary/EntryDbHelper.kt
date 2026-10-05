package com.ximanta.opendiary

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Entry(
    val id: Long,
    val category: String,
    val text: String,
    val timestamp: Long,
    val synced: Boolean = false
)

class EntryDbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        const val DB_NAME = "daymark.db"
        const val DB_VERSION = 2
        const val TBL = "entries"
        const val COL_ID = "_id"
        const val COL_CATEGORY = "category"
        const val COL_TEXT = "text"
        const val COL_TIMESTAMP = "timestamp"
        const val COL_SYNCED = "synced"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE " + TBL + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_CATEGORY + " TEXT NOT NULL, " +
                COL_TEXT + " TEXT NOT NULL, " +
                COL_TIMESTAMP + " INTEGER NOT NULL, " +
                COL_SYNCED + " INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_timestamp ON $TBL ($COL_TIMESTAMP DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_category ON $TBL ($COL_CATEGORY)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Never drop journal data during an app update. Version 1 already has
        // the complete schema; version 2 only adds indexes for faster history.
        if (oldVersion < 2) {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_timestamp ON $TBL ($COL_TIMESTAMP DESC)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_category ON $TBL ($COL_CATEGORY)")
        }
    }

    fun insertEntry(category: String, text: String, timestamp: Long): Long {
        val cv = ContentValues()
        cv.put(COL_CATEGORY, category)
        cv.put(COL_TEXT, text)
        cv.put(COL_TIMESTAMP, timestamp)
        cv.put(COL_SYNCED, 0)
        return writableDatabase.insert(TBL, null, cv)
    }

    fun getUnsynced(): ArrayList<Entry> {
        val list = ArrayList<Entry>()
        val c = readableDatabase.query(
            TBL,
            arrayOf(COL_ID, COL_CATEGORY, COL_TEXT, COL_TIMESTAMP, COL_SYNCED),
            COL_SYNCED + " = 0",
            null, null, null,
            COL_TIMESTAMP + " ASC"
        )
        while (c.moveToNext()) {
            list.add(Entry(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), c.getInt(4) != 0))
        }
        c.close()
        return list
    }

    fun markSynced(id: Long) {
        val cv = ContentValues()
        cv.put(COL_SYNCED, 1)
        writableDatabase.update(TBL, cv, COL_ID + " = ?", arrayOf(id.toString()))
    }

    fun getTasksForCategory(category: String): ArrayList<Entry> {
        val list = ArrayList<Entry>()
        val c = readableDatabase.query(
            TBL,
            arrayOf(COL_ID, COL_CATEGORY, COL_TEXT, COL_TIMESTAMP, COL_SYNCED),
            COL_CATEGORY + " = ?",
            arrayOf(category),
            null, null,
            COL_TIMESTAMP + " DESC"
        )
        while (c.moveToNext()) {
            list.add(Entry(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), c.getInt(4) != 0))
        }
        c.close()
        return list
    }

    fun getAllEntries(): ArrayList<Entry> {
        val list = ArrayList<Entry>()
        val c = readableDatabase.query(
            TBL,
            arrayOf(COL_ID, COL_CATEGORY, COL_TEXT, COL_TIMESTAMP, COL_SYNCED),
            null, null, null, null,
            "$COL_TIMESTAMP DESC"
        )
        c.use {
            while (it.moveToNext()) {
                list.add(Entry(it.getLong(0), it.getString(1), it.getString(2), it.getLong(3), it.getInt(4) != 0))
            }
        }
        return list
    }

    fun getEntryCount(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $TBL", null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    fun getUnsyncedCount(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $TBL WHERE $COL_SYNCED = 0", null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    fun deleteEntry(id: Long): Boolean = writableDatabase.delete(
        TBL, "$COL_ID = ?", arrayOf(id.toString())
    ) > 0
}
