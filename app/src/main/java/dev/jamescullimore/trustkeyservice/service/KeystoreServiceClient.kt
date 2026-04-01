package dev.jamescullimore.trustkeyservice.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import dev.jamescullimore.trustkeyservice.crypto.DeviceReport
import dev.jamescullimore.trustkeyservice.crypto.EncryptionResult

class KeystoreServiceClient(private val context: Context) {
    private var service: IKeystoreCommandService? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = IKeystoreCommandService.Stub.asInterface(binder)
            onConnected?.invoke()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            onDisconnected?.invoke()
        }
    }

    private var onConnected: (() -> Unit)? = null
    private var onDisconnected: (() -> Unit)? = null
    private var bound = false

    fun bind(onConnected: () -> Unit, onDisconnected: () -> Unit) {
        this.onConnected = onConnected
        this.onDisconnected = onDisconnected
        if (bound) return
        val intent = Intent(KeystoreCommandService.ACTION_BIND).setPackage(context.packageName)
        bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun unbind() {
        if (!bound) return
        context.unbindService(connection)
        bound = false
        service = null
    }

    fun isConnected(): Boolean = service != null

    fun inspect(alias: String): DeviceReport {
        return requireService().inspectKey(alias).toDeviceReport()
    }

    fun generate(alias: String, requestStrongBox: Boolean): DeviceReport {
        return requireService().generateKey(alias, requestStrongBox).toDeviceReport()
    }

    fun encrypt(alias: String, plaintext: String): EncryptionResult {
        return requireService().encrypt(alias, plaintext).toEncryptionResult()
    }

    fun decrypt(alias: String, cipherTextBase64: String, ivBase64: String): String {
        return requireService().decrypt(alias, cipherTextBase64, ivBase64).getString(EXTRA_PLAINTEXT).orEmpty()
    }

    fun delete(alias: String) {
        requireService().deleteKey(alias)
    }

    private fun requireService(): IKeystoreCommandService {
        return requireNotNull(service) { "Keystore Binder service is not connected yet." }
    }
}
