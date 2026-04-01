package dev.jamescullimore.trustkeyservice.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class KeystoreCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_EXECUTE_COMMAND) return

        val serviceIntent = Intent(context, KeystoreCommandService::class.java).apply {
            action = KeystoreCommandService.ACTION_COMMAND
            putExtra(EXTRA_REQUEST_ID, intent.getStringExtra(EXTRA_REQUEST_ID).orEmpty())
            putExtra(EXTRA_OPERATION, intent.getStringExtra(EXTRA_OPERATION).orEmpty())
            putExtra(EXTRA_ALIAS, intent.getStringExtra(EXTRA_ALIAS).orEmpty())
            putExtra(EXTRA_REQUEST_STRONGBOX, intent.getBooleanExtra(EXTRA_REQUEST_STRONGBOX, false))
            putExtra(EXTRA_PLAINTEXT, intent.getStringExtra(EXTRA_PLAINTEXT).orEmpty())
            putExtra(EXTRA_CIPHERTEXT, intent.getStringExtra(EXTRA_CIPHERTEXT).orEmpty())
            putExtra(EXTRA_IV, intent.getStringExtra(EXTRA_IV).orEmpty())
        }

        runCatching {
            context.startService(serviceIntent)
        }.onFailure {
            Log.e(
                KeystoreCommandService.COMMAND_LOG_TAG,
                "requestId=${intent.getStringExtra(EXTRA_REQUEST_ID).orEmpty()} status=error operation=dispatch payload={\"message\":\"${it.message ?: "Failed to start service"}\"}",
            )
        }
    }

    companion object {
        const val ACTION_EXECUTE_COMMAND = "dev.jamescullimore.trustkeyservice.action.EXECUTE_COMMAND"
    }
}
