package com.mpowernet.shesafe.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.mpowernet.shesafe.data.entity.ConsentLog

@Dao
interface ConsentLogDao {
    @Query("SELECT * FROM consent_logs ORDER BY timestamp DESC")
    suspend fun getAllLogs(): List<ConsentLog>

    @Insert
    suspend fun insertLog(log: ConsentLog)

    @Query("DELETE FROM consent_logs")
    suspend fun clearLogs()
}
