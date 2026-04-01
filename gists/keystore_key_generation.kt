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
