package com.mpowernet.shesafe.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "permission_rules")
data class PermissionRule(
    @PrimaryKey val permissionId: String, // e.g. "android.permission.ACCESS_FINE_LOCATION"
    val systemLabel: String,             // Technical label or short name
    val riskLevel: String,               // "LOW", "MEDIUM", "HIGH"
    val explanationEnglish: String,       // Simple english explanation (max 8 words)
    val explanationHindi: String,         // Simple hindi explanation
    val allowedPackagesCSV: String       // CSV of packages that are safe/expected, e.g. "com.google.android.apps.maps"
)
