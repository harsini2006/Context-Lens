package com.mpowernet.shesafe.security

import android.content.Context
import android.util.Log
import com.mpowernet.shesafe.SheSafeApplication
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest

object VaultAuthManager {

    private const val TAG = "VaultAuthManager"
    private const val PREFS_NAME = "shesafe_vault_prefs"
    private const val REAL_PIN_HASH_KEY = "real_pin_hash"
    private const val DURESS_PIN_HASH_KEY = "duress_pin_hash"

    enum class AuthResult {
        REAL,
        DURESS,
        FAILED
    }

    fun isPinSetup(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(REAL_PIN_HASH_KEY, null) != null
    }

    fun setupPins(context: Context, realPin: String, duressPin: String): Boolean {
        if (realPin.length < 4 || duressPin.length < 4 || realPin == duressPin) {
            return false
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val realHash = hashPin(realPin)
        val duressHash = hashPin(duressPin)

        prefs.edit()
            .putString(REAL_PIN_HASH_KEY, realHash)
            .putString(DURESS_PIN_HASH_KEY, duressHash)
            .apply()
        return true
    }

    fun authenticate(context: Context, enteredPin: String): AuthResult {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enteredHash = hashPin(enteredPin)

        val realHash = prefs.getString(REAL_PIN_HASH_KEY, "")
        val duressHash = prefs.getString(DURESS_PIN_HASH_KEY, "")

        return when (enteredHash) {
            realHash -> AuthResult.REAL
            duressHash -> {
                Log.w(TAG, "Duress PIN entered! Initiating silent defensive measures.")
                AuthResult.DURESS
            }
            else -> AuthResult.FAILED
        }
    }

    /**
     * Complete cryptographic wipe. Clears database, destroys Keystore keys, and resets preferences.
     */
    fun executePanicWipe(context: Context) {
        Log.e(TAG, "EMERGENCY PANIC WIPE TRIGGERED! Cryptographically destroying all logs and database partitions...")

        try {
            // 1. Delete Keystore database key alias (rendering the encrypted SQLCipher DB forever unreadable)
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keyStore.containsAlias("SheSafeDbAlias")) {
                keyStore.deleteEntry("SheSafeDbAlias")
                Log.d(TAG, "Keystore db alias destroyed.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying keystore keys during wipe", e)
        }

        // 2. Clear vault and log database files on disk
        val dbFile = context.getDatabasePath("shesafe_database")
        if (dbFile.exists()) {
            dbFile.delete()
            Log.d(TAG, "Main DB file deleted.")
        }
        val dbJournal = File(dbFile.path + "-journal")
        if (dbJournal.exists()) dbJournal.delete()
        val dbWal = File(dbFile.path + "-wal")
        if (dbWal.exists()) dbWal.delete()
        val dbShm = File(dbFile.path + "-shm")
        if (dbShm.exists()) dbShm.delete()

        // 3. Clear SharedPreferences
        context.getSharedPreferences("shesafe_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("shesafe_integrity_prefs", Context.MODE_PRIVATE).edit().clear().apply()

        // 4. Force restart the application process to instantiate a clean sandbox state
        Log.d(TAG, "Wipe complete. Sandbox reset.")
    }

    private fun hashPin(pin: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(pin.toByteArray())
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            pin
        }
    }
}
