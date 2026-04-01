package dev.jamescullimore.trustkeyservice.service

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import android.util.Log
import dev.jamescullimore.trustkeyservice.crypto.TrustKeyServiceManager
import org.json.JSONObject

class KeystoreCommandService : Service() {
    private val manager by lazy { TrustKeyServiceManager(this) }

    private val binder = object : IKeystoreCommandService.Stub() {
        override fun generateKey(alias: String, requestStrongBox: Boolean) = withAuthorizedCaller {
            manager.generateOrReplaceKey(alias, requestStrongBox).toBundle()
        }

        override fun inspectKey(alias: String) = withAuthorizedCaller {
            manager.inspect(alias).toBundle()
        }

        override fun encrypt(alias: String, plaintext: String) = withAuthorizedCaller {
            manager.encrypt(alias, plaintext).toBundle()
        }

        override fun decrypt(alias: String, cipherTextBase64: String, ivBase64: String) = withAuthorizedCaller {
            Bundle().apply {
                putString(EXTRA_PLAINTEXT, manager.decrypt(alias, cipherTextBase64, ivBase64))
            }
        }

        override fun deleteKey(alias: String) = withAuthorizedCaller {
            manager.deleteKey(alias)
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_COMMAND) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID).orEmpty()
        val operation = intent.getStringExtra(EXTRA_OPERATION).orEmpty()
        val alias = intent.getStringExtra(EXTRA_ALIAS).orEmpty()
        val requestStrongBox = intent.getBooleanExtra(EXTRA_REQUEST_STRONGBOX, false)
        val plaintext = intent.getStringExtra(EXTRA_PLAINTEXT).orEmpty()
        val cipherText = intent.getStringExtra(EXTRA_CIPHERTEXT).orEmpty()
        val iv = intent.getStringExtra(EXTRA_IV).orEmpty()

        val response = runCatching {
            when (operation) {
                OP_GENERATE -> jsonForReport(manager.generateOrReplaceKey(alias, requestStrongBox))
                OP_INSPECT -> jsonForReport(manager.inspect(alias))
                OP_ENCRYPT -> jsonForEncryption(manager.encrypt(alias, plaintext))
                OP_DECRYPT -> JSONObject()
                    .put("plaintext", manager.decrypt(alias, cipherText, iv))
                    .toString()
                OP_DELETE -> {
                    manager.deleteKey(alias)
                    JSONObject().put("deleted", true).put("alias", alias).toString()
                }

                else -> error("Unsupported operation: $operation")
            }
        }

        if (response.isSuccess) {
            Log.i(
                COMMAND_LOG_TAG,
                "requestId=$requestId status=ok operation=$operation payload=${response.getOrThrow()}",
            )
        } else {
            val error = response.exceptionOrNull()
            val payload = JSONObject()
                .put("message", error?.message ?: error?.javaClass?.simpleName ?: "Unknown error")
                .toString()
            Log.e(
                COMMAND_LOG_TAG,
                "requestId=$requestId status=error operation=$operation payload=$payload",
            )
        }

        stopSelf(startId)
        return START_NOT_STICKY
    }

    private fun <T> withAuthorizedCaller(block: () -> T): T {
        enforceAuthorizedCaller()
        return block()
    }

    private fun enforceAuthorizedCaller() {
        val callerUid = Binder.getCallingUid()
        if (
            callerUid == Process.myUid() ||
            callerUid == Process.SHELL_UID ||
            callerUid == Process.SYSTEM_UID ||
            callerUid == 0
        ) {
            return
        }

        val packages = packageManager.getPackagesForUid(callerUid).orEmpty()
        val hasMatchingSignature = packages.any { packageName ->
            packageManager.checkSignatures(packageName, applicationContext.packageName) ==
                PackageManager.SIGNATURE_MATCH
        }

        if (hasMatchingSignature) {
            return
        }

        throw SecurityException(
            "Caller UID $callerUid is not allowed to use this keystore service. Only same-signature apps, shell, root, or system are allowed.",
        )
    }

    private fun jsonForReport(report: dev.jamescullimore.trustkeyservice.crypto.DeviceReport): String {
        return JSONObject()
            .put("alias", report.keyAlias)
            .put("keyPresent", report.keyPresent)
            .put("securityLevel", report.securityLevelLabel)
            .put("securityVerdict", report.securityVerdict)
            .put("insideSecureHardware", report.isInsideSecureHardware?.toString())
            .put("strongBoxFeature", report.strongBoxFeature)
            .put("interpretation", report.interpretation)
            .toString()
    }

    private fun jsonForEncryption(result: dev.jamescullimore.trustkeyservice.crypto.EncryptionResult): String {
        return JSONObject()
            .put("plaintext", result.plaintext)
            .put("ciphertext", result.cipherTextBase64)
            .put("iv", result.ivBase64)
            .toString()
    }

    companion object {
        const val ACTION_BIND = "dev.jamescullimore.trustkeyservice.action.BIND_KEYSTORE_SERVICE"
        const val ACTION_COMMAND = "dev.jamescullimore.trustkeyservice.action.COMMAND"
        const val BIND_PERMISSION = "dev.jamescullimore.trustkeyservice.permission.ACCESS_KEYSTORE_SERVICE"

        const val OP_GENERATE = "generate"
        const val OP_INSPECT = "inspect"
        const val OP_ENCRYPT = "encrypt"
        const val OP_DECRYPT = "decrypt"
        const val OP_DELETE = "delete"

        const val COMMAND_LOG_TAG = "TrustKeyServiceCommand"
    }
}
