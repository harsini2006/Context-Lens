package com.mpowernet.shesafe.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "consent_logs")
data class ConsentLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val appPackage: String,
    val permissionRequested: String,
    val riskAssigned: String,
    val decision: String // "ALLOWED", "BLOCKED", "DISMISSED"
)
