package com.example.yolodetect.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ScanDao {
    @Insert
    suspend fun insert(r: ScanResult): Long

    @Query("SELECT * FROM scan_results ORDER BY ts DESC LIMIT :limit")
    suspend fun latest(limit: Int = 50): List<ScanResult>
}
