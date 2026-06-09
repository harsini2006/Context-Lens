package com.mpowernet.shesafe.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.security.SecureRandom

object KeyStoreManager {

    private const val KEY_ALIAS = "SheSafeDbAlias"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val PREFS_NAME = "shesafe_secure_prefs"
    private const val ENCRYPTED_PASSPHRASE_KEY = "encrypted_db_passphrase"
    private const val IV_KEY = "db_passphrase_iv"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    @Synchronized
    fun getOrCreateDatabasePassphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encryptedPassphrase = prefs.getString(ENCRYPTED_PASSPHRASE_KEY, null)
        val ivString = prefs.getString(IV_KEY, null)

        return if (encryptedPassphrase != null && ivString != null) {
            // Decrypt existing passphrase
            try {
                val iv = Base64.decode(ivString, Base64.DEFAULT)
                val encryptedBytes = Base64.decode(encryptedPassphrase, Base64.DEFAULT)
                
                val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                val secretKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey 
                    ?: throw IllegalStateException("Key not found in Keystore")

                val cipher = Cipher.getInstance(TRANSFORMATION)
                val spec = GCMParameterSpec(128, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
                
                cipher.doFinal(encryptedBytes)
            } catch (e: Exception) {
                // Key might be invalidated/corrupted; fallback: generate a new one
                generateAndSaveNewPassphrase(context)
            }
        } else {
            // Generate a fresh passphrase
            generateAndSaveNewPassphrase(context)
        }
    }

    private fun generateAndSaveNewPassphrase(context: Context): ByteArray {
        // Generate cryptographic random passphrase
        val random = SecureRandom()
        val passphraseBytes = ByteArray(32)
        random.nextBytes(passphraseBytes)

        // Ensure key exists in Keystore
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }

        val secretKey = keyStore.getKey(KEY_ALIAS, null) as SecretKey
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        
        val encryptedBytes = cipher.doFinal(passphraseBytes)
        val iv = cipher.iv

        // Save IV and Ciphertext base64 strings to SharedPreferences
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putString(ENCRYPTED_PASSPHRASE_KEY, Base64.encodeToString(encryptedBytes, Base64.DEFAULT))
            putString(IV_KEY, Base64.encodeToString(iv, Base64.DEFAULT))
            apply()
        }

        return passphraseBytes
    }
}
