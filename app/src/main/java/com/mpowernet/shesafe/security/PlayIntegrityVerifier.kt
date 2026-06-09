package com.mpowernet.shesafe.security

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import android.util.Log
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import java.security.MessageDigest

object PlayIntegrityVerifier {

    private const val TAG = "PlayIntegrityVerifier"
    
    // Expected SHA-256 fingerprint of the developer's signing certificate (Base64-encoded).
    // In production, this matches the Google Play Console release key fingerprint.
    private const val EXPECTED_SIGNATURE_HASH = "MPOWERNET_DEV_SIGNING_FINGERPRINT_PLACEHOLDER"

    /**
     * Attest app and system integrity.
     * In a production setup, the token is sent to the developer's backend server to be decrypted
     * and verified via Google API. Since ContextLens is offline-first, we implement a local fallback
     * package signature fingerprint check, and set up the Play Integrity API boilerplate.
     */
    fun attestAppIntegrity(context: Context, callback: (Boolean) -> Unit) {
        // 1. Perform local package signature check (essential offline defense)
        if (!verifyAppSignature(context)) {
            Log.e(TAG, "Local signature fingerprint mismatch. Sideloading detected.")
            callback(false)
            return
        }

        // 2. Trigger Play Integrity API request
        val integrityManager = IntegrityManagerFactory.create(context)
        
        // Nonce to prevent replay attacks (should contain a random or timestamped element)
        val nonce = Base64.encodeToString(
            "SheSafe_${System.currentTimeMillis()}".toByteArray(), 
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )

        val request = IntegrityTokenRequest.builder()
            .setCloudProjectNumber(1234567890L) // Hardcoded cloud project number placeholder
            .setNonce(nonce)
            .build()

        integrityManager.requestIntegrityToken(request)
            .addOnSuccessListener { response ->
                val token = response.token()
                Log.d(TAG, "Play Integrity Token acquired successfully: ${token.take(20)}...")
                // In a production backend client, we decrypt the token to confirm MEETS_DEVICE_INTEGRITY.
                // For this on-device demo/hackathon build, we successfully pass check since signature matches.
                callback(true)
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Play Integrity failed. Checking device signature as fallback.", exception)
                // Fallback: If device has no Google Play Services (e.g. customized ROM),
                // we rely on local signature verification which already passed above.
                callback(true)
            }
    }

    @SuppressLint("PackageManagerGetSignatures")
    @Suppress("DEPRECATION")
    private fun verifyAppSignature(context: Context): Boolean {
        try {
            val pm = context.packageManager
            val packageName = context.packageName
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                packageInfo.signatures
            }

            if (signatures.isNullOrEmpty()) return false

            for (signature in signatures) {
                val md = MessageDigest.getInstance("SHA-256")
                md.update(signature.toByteArray())
                val currentHash = Base64.encodeToString(md.digest(), Base64.NO_WRAP)
                
                // Allow debug builds or match the expected release certificate hash
                if (currentHash == EXPECTED_SIGNATURE_HASH || 
                    Build.PRODUCT.contains("sdk") || Build.HARDWARE.contains("goldfish")) {
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying signatures", e)
        }
        
        // Strictly return false in production release mode if signature matches failed
        val isDebuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        return isDebuggable
    }
}
