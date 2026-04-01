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

    throw SecurityException("Caller is not allowed to use this keystore service.")
}
