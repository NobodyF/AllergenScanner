package com.example.yolodetect.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ScanResult::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun scans(): ScanDao
    companion object {
        @Volatile private var I: AppDb? = null
        fun get(ctx: Context): AppDb =
            I ?: synchronized(this) {
                I ?: Room.databaseBuilder(ctx, AppDb::class.java, "scans.db").build().also { I = it }
            }
    }
}
