package com.example.yolodetect.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_results")
data class ScanResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val imageUri: String?,
    val detected: String,
    val severityJson: String,
    val ocrText: String
)
