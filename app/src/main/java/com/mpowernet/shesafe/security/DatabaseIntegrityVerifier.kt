package com.mpowernet.shesafe.security

import android.content.Context
import android.util.Log
import com.mpowernet.shesafe.SheSafeApplication

object DatabaseIntegrityVerifier {

    private const val TAG = "SheSafeIntegrity"

    /**
     * Verifies that the database has not been tampered with or modified offline.
     * Since the database is encrypted with SQLCipher, attempting to open it via Room
     * and running an integrity check verifies both cryptographic and structural integrity.
     */
    fun verifyDatabaseIntegrity(context: Context): Boolean {
        return try {
            val app = context.applicationContext as SheSafeApplication
            val db = app.database
            
            // Warm connection and run a quick pragma integrity check
            var isValid = false
            db.runInTransaction {
                val cursor = db.query("PRAGMA integrity_check(1)", null)
                if (cursor != null && cursor.moveToFirst()) {
                    val status = cursor.getString(0)
                    isValid = status.equals("ok", ignoreCase = true)
                }
                cursor?.close()
            }
            isValid
        } catch (e: Exception) {
            Log.e(TAG, "Database cryptographic integrity check failed: ${e.message}")
            false
        }
    }

    fun updateTrustedHash(context: Context) {
        // No-op: Cryptographic check handles integrity verification dynamically
    }
}
