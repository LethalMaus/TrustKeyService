# TrustKeyService

This project is a proof of concept for using Android Keystore on a custom Android device and exposing keystore-backed crypto operations through Binder.

Current confirmed result on the Rhino C6:

- `securityVerdict=HARDWARE_BACKED_TEE`
- `securityLevel=Trusted Environment (TEE)`
- `insideSecureHardware=true`

That means the generated AES key is reported by Android as hardware-backed in the device TEE. It is not StrongBox-backed, but it is still hardware-backed.

## What This Project Contains

- An Android app that generates and inspects an AES key in `AndroidKeyStore`
- A Binder service with AIDL methods for generate, inspect, encrypt, decrypt, and delete
- A debug-only remote test path from a host machine through `adb`
- A host script at [scripts/test_keystore_service.sh](scripts/test_keystore_service.sh)

Important distinction:

- Binder is local to Android.
- Your Linux workstation does not talk to Binder directly.
- The workstation test path uses `adb` to trigger a debug receiver on the device.

## Security Status

What this PoC proves:

- The device can generate and use an `AndroidKeyStore` AES key.
- The device reports that key as hardware-backed in the TEE.
- The app and Binder service can use the key without ever exporting raw key bytes.

What this PoC does not prove:

- It does not independently certify the vendor KeyMint/TEE implementation.
- It does not protect plaintext after decryption.
- It does not make a generic decrypt API safe by itself.

## Current Binder Access Control

The Binder service is protected in two layers.

Manifest layer:

- The service requires the signature permission `dev.jamescullimore.trustkeyservice.permission.ACCESS_KEYSTORE_SERVICE`
- Only apps signed with the same signing certificate can normally bind to it

Runtime layer:

- The service additionally checks the Binder caller UID
- Allowed callers are:
- same-signature apps
- `shell`
- `system`
- `root`

Relevant files:

- [AndroidManifest.xml](app/src/main/AndroidManifest.xml)
- [KeystoreCommandService.kt](app/src/main/java/dev/jamescullimore/trustkeyservice/service/KeystoreCommandService.kt)

## What A Future Trusted Caller Needs

There are two realistic caller models.

### 1. Another app or app-like component

If the future caller is an Android app, privileged app, or system app:

- It must be signed with the same certificate as this app, or be changed to a certificate the service explicitly trusts
- It must request the permission `dev.jamescullimore.trustkeyservice.permission.ACCESS_KEYSTORE_SERVICE`
- It must bind with the action `dev.jamescullimore.trustkeyservice.action.BIND_KEYSTORE_SERVICE`
- It must use the AIDL interface `IKeystoreCommandService`

Practical implication:

- A random third-party APK cannot use this service
- A platform-signed or same-signature trusted component can

### 2. A native Linux daemon / system service on the device

If the future caller is a native daemon running on the Android device:

- It does not use Android manifest permissions in the same way an APK does
- The important part is the Binder caller identity and deployment model

To be trusted by the current service, it should run as one of:

- `system`
- `root`
- a process whose app package is signed with the same certificate, if the caller is app-based rather than purely native

Practical guidance:

- If the long-term caller is a true platform/system component, make it a system service or a platform-signed privileged component
- If the long-term caller is native-only, the cleanest production architecture is often a platform-owned service boundary rather than an ordinary third-party app service
- SELinux policy will likely matter for the final production integration even if it is not needed for this PoC

## Binder Contract

Current AIDL methods:

- `generateKey(alias, requestStrongBox)`
- `inspectKey(alias)`
- `encrypt(alias, plaintext)`
- `decrypt(alias, cipherTextBase64, ivBase64)`
- `deleteKey(alias)`

These are fine for a PoC, but they are too broad for a hardened production API.

## Security Caveats

The key point is this:

- Hardware-backed key storage protects the key material
- It does not automatically protect the plaintext returned to a caller

That means a generic decrypt API becomes a decrypt oracle for every caller you trust.

If the caller is compromised, overly broad, or hookable, it can still obtain plaintext even though the raw key never leaves secure hardware.

So the real production problem is not only "is the key hardware-backed?"
It is also "who is allowed to ask the service to decrypt, and what data do they get back?"

## Hardening Recommendations

These are the next steps, in order of value.

### 1. Keep the caller trust boundary narrow

- Keep Binder access limited to same-signature or system-trusted callers
- Do not broaden the service to arbitrary apps
- Keep the `adb` bridge as debug-only behavior

### 2. Replace generic decrypt with protocol-specific operations

Examples:

- decrypt a specific payload format only
- unwrap a session key only for a specific trusted caller
- sign or attest data instead of returning plaintext
- perform decrypt-and-use internally, returning only the minimum business result

This is the single biggest security improvement once the real use case is known.

### 3. Add caller-level policy checks

When the final caller is known, add policy such as:

- allow only one specific package name
- allow only one signing certificate
- allow only one UID or SELinux domain
- allow only specific operation types per caller

### 4. Add audit logging

For production, log:

- caller identity
- operation type
- alias used
- success/failure

Do not log plaintext, ciphertext, or other sensitive payloads.

### 5. Consider attestation if remote proof matters

If a backend or auditor needs proof that a key is hardware-backed:

- use Android Keystore attestation
- validate the attestation chain and security level remotely

This PoC does not yet implement attestation.

## Host Script

The host script is for testing only.

Usage:

```bash
scripts/test_keystore_service.sh generate
scripts/test_keystore_service.sh inspect
scripts/test_keystore_service.sh encrypt "hello"
scripts/test_keystore_service.sh decrypt "<ciphertext>" "<iv>"
scripts/test_keystore_service.sh delete
```

Expected inspect output on the Rhino C6:

```text
{"alias":"hardware-backed-demo-key","keyPresent":true,"securityLevel":"Trusted Environment (TEE)","securityVerdict":"HARDWARE_BACKED_TEE","insideSecureHardware":"true","strongBoxFeature":false,"interpretation":"..."}
VERDICT: HARDWARE_BACKED_TEE | LEVEL: Trusted Environment (TEE) | KEY_PRESENT: true | STRONGBOX_FEATURE: false
```

## Final Position

For the current knowledge level, the main security points are already in place:

- confirmed hardware-backed TEE keystore on the target device
- Binder access restricted to trusted callers
- debug host testing kept separate from normal Binder access

The biggest remaining security question is not the keystore itself. It is the final decrypt/use protocol. Once that is known, the service should be reshaped around that exact operation instead of keeping a generic decrypt API.
