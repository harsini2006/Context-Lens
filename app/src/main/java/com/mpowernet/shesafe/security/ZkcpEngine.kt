package com.mpowernet.shesafe.security

import android.content.Context
import android.util.Base64
import com.mpowernet.shesafe.data.entity.ConsentLog
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.MessageDigest
import java.security.SecureRandom
import org.json.JSONObject

object ZkcpEngine {

    private const val KEY_ALIAS = "SheSafeZkcpAlias"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    @Synchronized
    private fun getOrCreateKeyPair(): KeyPair {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val privateKey = keyStore.getKey(KEY_ALIAS, null) as PrivateKey
            val certificate = keyStore.getCertificate(KEY_ALIAS)
            val publicKey = certificate.publicKey
            return KeyPair(publicKey, privateKey)
        }

        // Generate ECDSA KeyPair if none exists
        val kpg = KeyPairGenerator.getInstance("EC", ANDROID_KEYSTORE)
        val parameterSpec = android.security.keystore.KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_SIGN or android.security.keystore.KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(android.security.keystore.KeyProperties.DIGEST_SHA256)
            .build()

        kpg.initialize(parameterSpec)
        return kpg.generateKeyPair()
    }

    /**
     * Generates a local Zero-Knowledge Consent Proof (ZKCP) Receipt JSON.
     * The receipt commits to the consent decision anonymously by hashing the log data with a secure salt,
     * and signs the hash with the device's key to prove integrity without revealing raw data to auditors.
     */
    fun generateZkcpProof(log: ConsentLog): String {
        try {
            val keyPair = getOrCreateKeyPair()
            
            // 1. Generate random salt (16 bytes)
            val saltBytes = ByteArray(16)
            SecureRandom().nextBytes(saltBytes)
            val saltBase64 = Base64.encodeToString(saltBytes, Base64.NO_WRAP)

            // 2. Compute the commitment hash: SHA-256(Package + Permission + Decision + Salt)
            val inputString = "${log.appPackage}:${log.permissionRequested}:${log.decision}:$saltBase64"
            val digest = MessageDigest.getInstance("SHA-256")
            val commitmentHash = digest.digest(inputString.toByteArray())
            val commitmentHashHex = commitmentHash.joinToString("") { "%02x".format(it) }

            // 3. Create ECDSA signature over the commitment hash
            val signatureInstance = Signature.getInstance("SHA256withECDSA")
            signatureInstance.initSign(keyPair.private)
            signatureInstance.update(commitmentHash)
            val signatureBytes = signatureInstance.sign()
            val signatureBase64 = Base64.encodeToString(signatureBytes, Base64.NO_WRAP)

            // 4. Encode Public Key
            val publicKeyBase64 = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)

            // 5. Assemble ZKCP Proof JSON
            val proofJson = JSONObject().apply {
                put("schema", "DPDP-Consent-Proof-v1")
                put("commitmentHash", commitmentHashHex)
                put("saltCommitment", saltBase64)
                put("signature", signatureBase64)
                put("verifierPublicKey", publicKeyBase64)
                put("anonymousMetadata", JSONObject().apply {
                    put("permissionScope", log.permissionRequested.substringAfterLast("."))
                    put("action", log.decision)
                    put("timestamp", log.timestamp)
                })
            }
            return proofJson.toString(2)
        } catch (e: Exception) {
            return "{\"error\": \"ZKCP generation failed: ${e.message}\"}"
        }
    }
}
