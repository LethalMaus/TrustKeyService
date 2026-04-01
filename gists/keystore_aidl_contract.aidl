interface IKeystoreCommandService {
    Bundle generateKey(String alias, boolean requestStrongBox);
    Bundle inspectKey(String alias);
    Bundle encrypt(String alias, String plaintext);
    Bundle decrypt(String alias, String cipherTextBase64, String ivBase64);
    void deleteKey(String alias);
}
