package com.ximanta.opendiary

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class Entry(val id: Long, val category: String, val text: String, val timestamp: Long)

class EntryDbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        const val DB_NAME = "daymark.db"
        const val DB_VERSION = 1
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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS " + TBL)
        onCreate(db)
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
            arrayOf(COL_ID, COL_CATEGORY, COL_TEXT, COL_TIMESTAMP),
            COL_SYNCED + " = 0",
            null, null, null,
            COL_TIMESTAMP + " ASC"
        )
        while (c.moveToNext()) {
            list.add(Entry(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3)))
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
            arrayOf(COL_ID, COL_CATEGORY, COL_TEXT, COL_TIMESTAMP),
            COL_CATEGORY + " = ?",
            arrayOf(category),
            null, null,
            COL_TIMESTAMP + " DESC"
        )
        while (c.moveToNext()) {
            list.add(Entry(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3)))
        }
        c.close()
        return list
    }
}
