package com.mpowernet.shesafe.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mpowernet.shesafe.data.entity.VaultItem

@Dao
interface VaultItemDao {
    @Query("SELECT * FROM vault_items WHERE isDecoy = 0 ORDER BY timestamp DESC")
    suspend fun getRealItems(): List<VaultItem>

    @Query("SELECT * FROM vault_items WHERE isDecoy = 1 ORDER BY timestamp DESC")
    suspend fun getDecoyItems(): List<VaultItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: VaultItem)

    @Query("DELETE FROM vault_items")
    suspend fun clearVault()
}
