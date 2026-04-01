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
