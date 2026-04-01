package dev.jamescullimore.trustkeyservice.ui

import android.os.Build
import android.security.keystore.StrongBoxUnavailableException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.jamescullimore.trustkeyservice.crypto.DeviceReport
import dev.jamescullimore.trustkeyservice.crypto.EncryptionResult
import dev.jamescullimore.trustkeyservice.service.KeystoreServiceClient

private const val DemoAlias = "hardware-backed-demo-key"
private const val DefaultPlaintext = "Vendor PoC secret: keep this AES key inside Android Keystore."

@Composable
fun TrustKeyServiceApp() {
    val context = LocalContext.current
    val serviceClient = remember(context) { KeystoreServiceClient(context = context) }
    var requestStrongBox by rememberSaveable { mutableStateOf(false) }
    var plaintext by rememberSaveable { mutableStateOf(DefaultPlaintext) }
    var encrypted by remember { mutableStateOf<EncryptionResult?>(null) }
    var decryptedText by rememberSaveable { mutableStateOf<String?>(null) }
    var report by remember { mutableStateOf<DeviceReport?>(null) }
    var serviceConnected by remember { mutableStateOf(false) }
    var status by rememberSaveable {
        mutableStateOf("Connecting to the keystore Binder service…")
    }

    DisposableEffect(serviceClient) {
        serviceClient.bind(
            onConnected = {
                serviceConnected = true
                runCatching {
                    serviceClient.inspect(DemoAlias)
                }.onSuccess {
                    report = it
                    status = "Binder service connected. Generate a key on the target device to validate the path."
                }.onFailure { error ->
                    status = formatError("Binder service connected, but initial inspection failed", error)
                }
            },
            onDisconnected = {
                serviceConnected = false
                status = "Binder service disconnected."
            },
        )
        onDispose {
            serviceClient.unbind()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Android TrustKeyService",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "This screen generates an AES key in Android Keystore, uses it for GCM encryption, and reports whether Android says the key lives in secure hardware on this device.",
                style = MaterialTheme.typography.bodyLarge,
            )
            LabeledValue("Binder Service Connected", serviceConnected.toString())

            CardBlock(title = "Device Report") {
                val currentReport = report
                if (currentReport == null) {
                    Text("Loading device report…")
                } else {
                    LabeledValue("Device", "${currentReport.manufacturer} ${currentReport.model}".trim())
                    LabeledValue("Android", "${currentReport.release} (SDK ${currentReport.sdkInt})")
                    LabeledValue("Alias", currentReport.keyAlias)
                    LabeledValue("Key Present", currentReport.keyPresent.toString())
                    LabeledValue("Verdict", currentReport.securityVerdict)
                    LabeledValue("Security Level", currentReport.securityLevelLabel)
                    LabeledValue("Inside Secure Hardware", currentReport.isInsideSecureHardware?.toString() ?: "Unknown")
                    LabeledValue("User Auth Required", currentReport.isUserAuthenticationRequired?.toString() ?: "Unknown")
                    LabeledValue("StrongBox Feature", currentReport.strongBoxFeature.toString())
                    Text(
                        text = currentReport.interpretation,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            CardBlock(title = "PoC Controls") {
                OutlinedTextField(
                    value = plaintext,
                    onValueChange = { plaintext = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Plaintext") },
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    RowCheckbox(
                        checked = requestStrongBox,
                        onCheckedChange = { requestStrongBox = it },
                        label = "Request StrongBox-backed key if the device supports it",
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                runCatching {
                                    serviceClient.generate(DemoAlias, requestStrongBox)
                                }.onSuccess {
                                    report = it
                                    encrypted = null
                                    decryptedText = null
                                    status = "Key generated successfully. ${it.securityLevelLabel} reported for alias $DemoAlias."
                                }.onFailure { error ->
                                    status = formatError("Key generation failed", error)
                                    runCatching { serviceClient.inspect(DemoAlias) }.onSuccess { report = it }
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Generate Key")
                        }

                        Button(
                            onClick = {
                                runCatching {
                                    serviceClient.encrypt(DemoAlias, plaintext)
                                }.onSuccess {
                                    encrypted = it
                                    decryptedText = null
                                    runCatching { serviceClient.inspect(DemoAlias) }.onSuccess { report = it }
                                    status = "Encryption succeeded. Ciphertext and IV are shown below."
                                }.onFailure { error ->
                                    status = formatError("Encryption failed", error)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Encrypt")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                val currentEncrypted = encrypted
                                if (currentEncrypted == null) {
                                    status = "Encrypt data first so there is a ciphertext payload to decrypt."
                                } else {
                                    runCatching {
                                        serviceClient.decrypt(
                                            DemoAlias,
                                            currentEncrypted.cipherTextBase64,
                                            currentEncrypted.ivBase64,
                                        )
                                    }.onSuccess {
                                        decryptedText = it
                                        status = "Decryption succeeded. The plaintext round trip completed using Android Keystore."
                                    }.onFailure { error ->
                                        status = formatError("Decryption failed", error)
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Decrypt")
                        }

                        TextButton(
                            onClick = {
                                runCatching {
                                    serviceClient.inspect(DemoAlias)
                                }.onSuccess {
                                    report = it
                                    status = "Device report refreshed."
                                }.onFailure { error ->
                                    status = formatError("Refresh failed", error)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Refresh Report")
                        }
                    }

                    TextButton(onClick = {
                        runCatching {
                            serviceClient.delete(DemoAlias)
                        }.onSuccess {
                            encrypted = null
                            decryptedText = null
                            runCatching { serviceClient.inspect(DemoAlias) }.onSuccess { report = it }
                            status = "Key deleted."
                        }.onFailure { error ->
                            status = formatError("Key deletion failed", error)
                        }
                    }) {
                        Text("Delete Key")
                    }
                }
            }

            CardBlock(title = "Round Trip Output") {
                LabeledValue("Status", status)
                LabeledValue("Ciphertext (Base64)", encrypted?.cipherTextBase64 ?: "Not generated yet")
                LabeledValue("IV (Base64)", encrypted?.ivBase64 ?: "Not generated yet")
                LabeledValue("Decrypted Text", decryptedText ?: "Not decrypted yet")
            }

            CardBlock(title = "What You Need From The Vendor") {
                val currentReport = report
                (currentReport?.vendorChecklist ?: emptyList()).forEach { line ->
                    Text(
                        text = "• $line",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun CardBlock(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Surface(
            tonalElevation = 2.dp,
            shape = RoundedCornerShape(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RowCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(14.dp),
        onClick = { onCheckedChange(!checked) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun formatError(prefix: String, error: Throwable): String {
    val reason = when (error) {
        is StrongBoxUnavailableException -> "The device does not provide StrongBox for this request."
        else -> buildString {
            append(error::class.java.simpleName)
            if (!error.message.isNullOrBlank()) {
                append(": ")
                append(error.message)
            }
            val cause = error.cause
            if (cause != null && !cause.message.isNullOrBlank()) {
                append(" | cause=")
                append(cause::class.java.simpleName)
                append(": ")
                append(cause.message)
            }
        }
    }
    return "$prefix. $reason"
}
