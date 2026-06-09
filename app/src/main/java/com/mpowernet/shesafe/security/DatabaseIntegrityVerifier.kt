package com.mpowernet.shesafe.security

import android.content.Context
import android.util.Base64
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object DatabaseIntegrityVerifier {

    private const val TAG = "SheSafeIntegrity"
    private const val PREFS_NAME = "shesafe_integrity_prefs"
    private const val TRUSTED_HASH_KEY = "trusted_db_hash"

    fun verifyDatabaseIntegrity(context: Context): Boolean {
        val dbFile = context.getDatabasePath("shesafe_database")
        if (!dbFile.exists()) {
            // Database file doesn't exist yet, which is expected on first launch
            return true
        }

        val currentHash = calculateFileSha256(dbFile) ?: return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val trustedHash = prefs.getString(TRUSTED_HASH_KEY, null)

        return if (trustedHash == null) {
            // First time computing hash (initial seed complete)
            prefs.edit().putString(TRUSTED_HASH_KEY, currentHash).apply()
            Log.d(TAG, "Trusted database hash stored successfully.")
            true
        } else {
            val isValid = currentHash == trustedHash
            if (!isValid) {
                Log.e(TAG, "DB Tampering Detected! Expected: $trustedHash, Current: $currentHash")
            }
            isValid
        }
    }

    /**
     * Force-update the trusted hash (called when database modifications are legitimate,
     * e.g., user consents or settings changes).
     */
    fun updateTrustedHash(context: Context) {
        val dbFile = context.getDatabasePath("shesafe_database")
        if (dbFile.exists()) {
            val currentHash = calculateFileSha256(dbFile)
            if (currentHash != null) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(TRUSTED_HASH_KEY, currentHash)
                    .apply()
            }
        }
    }

    private fun calculateFileSha256(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            var bytesRead: Int
            val fis = FileInputStream(file)
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
            fis.close()
            Base64.encodeToString(digest.digest(), Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating hash", e)
            null
        }
    }
}
