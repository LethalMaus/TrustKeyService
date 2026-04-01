package dev.jamescullimore.trustkeyservice.service

import android.os.Bundle
import dev.jamescullimore.trustkeyservice.crypto.DeviceReport
import dev.jamescullimore.trustkeyservice.crypto.EncryptionResult

internal const val EXTRA_ALIAS = "alias"
internal const val EXTRA_REQUEST_STRONGBOX = "request_strongbox"
internal const val EXTRA_PLAINTEXT = "plaintext"
internal const val EXTRA_CIPHERTEXT = "ciphertext"
internal const val EXTRA_IV = "iv"
internal const val EXTRA_REQUEST_ID = "request_id"
internal const val EXTRA_OPERATION = "operation"

private const val KEY_SDK_INT = "sdkInt"
private const val KEY_RELEASE = "release"
private const val KEY_MANUFACTURER = "manufacturer"
private const val KEY_MODEL = "model"
private const val KEY_STRONGBOX_FEATURE = "strongBoxFeature"
private const val KEY_KEY_ALIAS = "keyAlias"
private const val KEY_KEY_PRESENT = "keyPresent"
private const val KEY_SECURITY_LEVEL_LABEL = "securityLevelLabel"
private const val KEY_SECURITY_VERDICT = "securityVerdict"
private const val KEY_INSIDE_SECURE_HARDWARE = "insideSecureHardware"
private const val KEY_USER_AUTH_REQUIRED = "userAuthenticationRequired"
private const val KEY_CAN_USE_UNLOCKED = "canUseUnlockedDeviceRequirement"
private const val KEY_INTERPRETATION = "interpretation"
private const val KEY_VENDOR_CHECKLIST = "vendorChecklist"

internal fun DeviceReport.toBundle(): Bundle = Bundle().apply {
    putInt(KEY_SDK_INT, sdkInt)
    putString(KEY_RELEASE, release)
    putString(KEY_MANUFACTURER, manufacturer)
    putString(KEY_MODEL, model)
    putBoolean(KEY_STRONGBOX_FEATURE, strongBoxFeature)
    putString(KEY_KEY_ALIAS, keyAlias)
    putBoolean(KEY_KEY_PRESENT, keyPresent)
    putString(KEY_SECURITY_LEVEL_LABEL, securityLevelLabel)
    putString(KEY_SECURITY_VERDICT, securityVerdict)
    putString(KEY_INSIDE_SECURE_HARDWARE, isInsideSecureHardware?.toString())
    putString(KEY_USER_AUTH_REQUIRED, isUserAuthenticationRequired?.toString())
    putBoolean(KEY_CAN_USE_UNLOCKED, canUseUnlockedDeviceRequirement)
    putString(KEY_INTERPRETATION, interpretation)
    putStringArrayList(KEY_VENDOR_CHECKLIST, ArrayList(vendorChecklist))
}

internal fun Bundle.toDeviceReport(): DeviceReport {
    return DeviceReport(
        sdkInt = getInt(KEY_SDK_INT),
        release = getString(KEY_RELEASE).orEmpty(),
        manufacturer = getString(KEY_MANUFACTURER).orEmpty(),
        model = getString(KEY_MODEL).orEmpty(),
        strongBoxFeature = getBoolean(KEY_STRONGBOX_FEATURE),
        keyAlias = getString(KEY_KEY_ALIAS).orEmpty(),
        keyPresent = getBoolean(KEY_KEY_PRESENT),
        securityLevelLabel = getString(KEY_SECURITY_LEVEL_LABEL).orEmpty(),
        securityVerdict = getString(KEY_SECURITY_VERDICT).orEmpty(),
        isInsideSecureHardware = getString(KEY_INSIDE_SECURE_HARDWARE)?.toBooleanStrictOrNull(),
        isUserAuthenticationRequired = getString(KEY_USER_AUTH_REQUIRED)?.toBooleanStrictOrNull(),
        canUseUnlockedDeviceRequirement = getBoolean(KEY_CAN_USE_UNLOCKED),
        interpretation = getString(KEY_INTERPRETATION).orEmpty(),
        vendorChecklist = getStringArrayList(KEY_VENDOR_CHECKLIST)?.toList().orEmpty(),
    )
}

internal fun EncryptionResult.toBundle(): Bundle = Bundle().apply {
    putString(EXTRA_PLAINTEXT, plaintext)
    putString(EXTRA_CIPHERTEXT, cipherTextBase64)
    putString(EXTRA_IV, ivBase64)
}

internal fun Bundle.toEncryptionResult(): EncryptionResult {
    return EncryptionResult(
        plaintext = getString(EXTRA_PLAINTEXT).orEmpty(),
        cipherTextBase64 = getString(EXTRA_CIPHERTEXT).orEmpty(),
        ivBase64 = getString(EXTRA_IV).orEmpty(),
    )
}
