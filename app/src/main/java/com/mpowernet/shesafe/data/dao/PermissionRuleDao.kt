package com.mpowernet.shesafe.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mpowernet.shesafe.data.entity.PermissionRule

@Dao
interface PermissionRuleDao {
    @Query("SELECT * FROM permission_rules WHERE permissionId = :permissionId LIMIT 1")
    suspend fun getRuleForPermission(permissionId: String): PermissionRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<PermissionRule>)

    @Query("SELECT COUNT(*) FROM permission_rules")
    suspend fun getCount(): Int
}
