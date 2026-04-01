package dev.jamescullimore.trustkeyservice.crypto

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
private const val AES_MODE = "AES/GCM/NoPadding"

data class DeviceReport(
    val sdkInt: Int,
    val release: String,
    val manufacturer: String,
    val model: String,
    val strongBoxFeature: Boolean,
    val keyAlias: String,
    val keyPresent: Boolean,
    val securityLevelLabel: String,
    val securityVerdict: String,
    val isInsideSecureHardware: Boolean?,
    val isUserAuthenticationRequired: Boolean?,
    val canUseUnlockedDeviceRequirement: Boolean,
    val interpretation: String,
    val vendorChecklist: List<String>,
)

data class EncryptionResult(
    val plaintext: String,
    val cipherTextBase64: String,
    val ivBase64: String,
)

class TrustKeyServiceManager(private val context: Context) {
    private val keyStore: KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

    fun generateOrReplaceKey(alias: String, requestStrongBox: Boolean): DeviceReport {
        deleteKey(alias)
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setKeySize(256)

        if (requestStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true)
        }

        keyGenerator.init(builder.build())
        keyGenerator.generateKey()
        return inspect(alias)
    }

    @Suppress("DEPRECATION")
    fun inspect(alias: String): DeviceReport {
        val packageManager = context.packageManager
        val strongBoxFeature = packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        val secretKey = keyStore.getKey(alias, null) as? SecretKey
        val keyInfo = secretKey?.let(::loadKeyInfo)
        val securityLevelLabel = keyInfo?.toSecurityLevelLabel() ?: "No key generated yet"
        val securityVerdict = keyInfo?.toSecurityVerdict() ?: "NO_KEY"
        val insideSecureHardware = keyInfo?.isInsideSecureHardware
        val userAuthRequired = keyInfo?.isUserAuthenticationRequired
        val interpretation = when {
            keyInfo == null -> "No keystore key exists yet. Generate one on the device to test what this build actually exposes."
            isHardwareBacked(keyInfo) -> "This key is reported as hardware-backed. If the vendor's KeyMint/TEE implementation is real, the AES key material should stay outside normal Android userspace even on a rooted build."
            else -> "This key is available through Android Keystore, but the device reports software-only protection. Your app can still encrypt and decrypt, but this does not prove hardware-backed storage."
        }

        return DeviceReport(
            sdkInt = Build.VERSION.SDK_INT,
            release = Build.VERSION.RELEASE.orEmpty(),
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            strongBoxFeature = strongBoxFeature,
            keyAlias = alias,
            keyPresent = secretKey != null,
            securityLevelLabel = securityLevelLabel,
            securityVerdict = securityVerdict,
            isInsideSecureHardware = insideSecureHardware,
            isUserAuthenticationRequired = userAuthRequired,
            canUseUnlockedDeviceRequirement = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P,
            interpretation = interpretation,
            vendorChecklist = buildVendorChecklist(strongBoxFeature, keyInfo),
        )
    }

    fun encrypt(alias: String, plaintext: String): EncryptionResult {
        val secretKey = requireSecretKey(alias)
        val cipher = Cipher.getInstance(AES_MODE).apply {
            init(Cipher.ENCRYPT_MODE, secretKey)
        }
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return EncryptionResult(
            plaintext = plaintext,
            cipherTextBase64 = encrypted.toBase64(),
            ivBase64 = cipher.iv.toBase64(),
        )
    }

    fun decrypt(alias: String, cipherTextBase64: String, ivBase64: String): String {
        val secretKey = requireSecretKey(alias)
        val cipher = Cipher.getInstance(AES_MODE).apply {
            init(
                Cipher.DECRYPT_MODE,
                secretKey,
                GCMParameterSpec(128, ivBase64.base64Decode()),
            )
        }
        val decrypted = cipher.doFinal(cipherTextBase64.base64Decode())
        return decrypted.toString(Charsets.UTF_8)
    }

    fun deleteKey(alias: String) {
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
    }

    private fun requireSecretKey(alias: String): SecretKey {
        return (keyStore.getKey(alias, null) as? SecretKey)
            ?: error("Generate the keystore key before encrypting or decrypting.")
    }

    private fun loadKeyInfo(secretKey: SecretKey): KeyInfo {
        val factory = SecretKeyFactory.getInstance(secretKey.algorithm, KEYSTORE_PROVIDER)
        return factory.getKeySpec(secretKey, KeyInfo::class.java) as KeyInfo
    }

    @Suppress("DEPRECATION")
    private fun isHardwareBacked(keyInfo: KeyInfo): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT ||
                keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
        } else {
            keyInfo.isInsideSecureHardware
        }
    }

    @Suppress("DEPRECATION")
    private fun KeyInfo.toSecurityLevelLabel(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return if (isInsideSecureHardware) "Trusted hardware (legacy API report)" else "Software only (legacy API report)"
        }

        return when (securityLevel) {
            KeyProperties.SECURITY_LEVEL_SOFTWARE -> "Software"
            KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "Trusted Environment (TEE)"
            KeyProperties.SECURITY_LEVEL_STRONGBOX -> "StrongBox"
            KeyProperties.SECURITY_LEVEL_UNKNOWN_SECURE -> "Secure hardware (unknown class)"
            else -> "Unknown"
        }
    }

    @Suppress("DEPRECATION")
    private fun KeyInfo.toSecurityVerdict(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return if (isInsideSecureHardware) "HARDWARE_BACKED" else "SOFTWARE_BACKED"
        }

        return when (securityLevel) {
            KeyProperties.SECURITY_LEVEL_SOFTWARE -> "SOFTWARE_BACKED"
            KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "HARDWARE_BACKED_TEE"
            KeyProperties.SECURITY_LEVEL_STRONGBOX -> "HARDWARE_BACKED_STRONGBOX"
            KeyProperties.SECURITY_LEVEL_UNKNOWN_SECURE -> "HARDWARE_BACKED_UNKNOWN"
            else -> "UNKNOWN"
        }
    }

    private fun buildVendorChecklist(strongBoxFeature: Boolean, keyInfo: KeyInfo?): List<String> {
        val checklist = mutableListOf(
            "The vendor build must expose Android Keystore with a real KeyMint/Keymaster implementation backed by TEE or StrongBox. An app cannot turn a software-only keystore into hardware-backed storage.",
            "If you need proof for a backend or an auditor, the vendor should also provision key attestation so you can remotely verify the key's security level and policy.",
            "Hardware-backed storage protects key material at rest. It does not stop a rooted OS from reading plaintext after your app decrypts it or from tampering with the app runtime.",
        )

        if (!strongBoxFeature) {
            checklist += "This device does not advertise the StrongBox feature. That is not fatal; TEE-backed keys can still be hardware-backed."
        }

        if (keyInfo == null) {
            checklist += "Run TrustKeyService on the target device and inspect the generated key report before making vendor claims."
        }

        return checklist
    }
}

private fun ByteArray.toBase64(): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Base64.getEncoder().encodeToString(this)
    } else {
        android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)
    }
}

private fun String.base64Decode(): ByteArray {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Base64.getDecoder().decode(this)
    } else {
        android.util.Base64.decode(this, android.util.Base64.DEFAULT)
    }
}
