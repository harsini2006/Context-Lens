package com.mpowernet.shesafe

import android.app.Application
import com.mpowernet.shesafe.data.AppDatabase
import com.mpowernet.shesafe.security.DatabaseIntegrityVerifier
import com.mpowernet.shesafe.security.PlayIntegrityVerifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class SheSafeApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob())
    val database by lazy { AppDatabase.getDatabase(this, applicationScope) }

    companion object {
        var isDatabaseIntegrityValid = true
            set(value) { field = value }
        var isPlayIntegrityValid = true
            set(value) { field = value }
    }

    override fun onCreate() {
        super.onCreate()
        
        // 1. Verify boot-time database hash integrity
        isDatabaseIntegrityValid = DatabaseIntegrityVerifier.verifyDatabaseIntegrity(this)
        
        // 2. Verify signature and Play Integrity status
        PlayIntegrityVerifier.attestAppIntegrity(this) { success ->
            isPlayIntegrityValid = success
        }
    }

    fun updateDatabaseHash() {
        DatabaseIntegrityVerifier.updateTrustedHash(this)
    }
}
