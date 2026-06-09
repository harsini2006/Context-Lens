package com.mpowernet.shesafe

import android.app.Application
import com.mpowernet.shesafe.data.AppDatabase
import com.mpowernet.shesafe.security.DatabaseIntegrityVerifier
import com.mpowernet.shesafe.security.PlayIntegrityVerifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
        
        // 1. Verify boot-time database hash integrity and pre-warm the lazy Room instance in background
        applicationScope.launch(Dispatchers.IO) {
            isDatabaseIntegrityValid = DatabaseIntegrityVerifier.verifyDatabaseIntegrity(this@SheSafeApplication)
            try {
                // Accessing database triggers the builder, runInTransaction opens and warms the file connection
                database.runInTransaction { }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // 2. Verify signature and Play Integrity status
        PlayIntegrityVerifier.attestAppIntegrity(this) { success ->
            isPlayIntegrityValid = success
        }
    }

    fun updateDatabaseHash() {
        applicationScope.launch(Dispatchers.IO) {
            DatabaseIntegrityVerifier.updateTrustedHash(this@SheSafeApplication)
        }
    }
}
