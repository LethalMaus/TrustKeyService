# Hardware-Backed TrustKeyService

## Feasibility

Yes, this is feasible only if the vendor device already implements Android Keystore on top of secure hardware.

The supplied AOSP Keystore page states that:

- Android apps use `AndroidKeyStore` and the higher-level Java crypto APIs.
- Hardware-backed protection comes from an OEM-provided KeyMint or Keymaster implementation backed by a TEE or StrongBox.
- Key attestation exists to make the key's existence in secure hardware remotely verifiable.

That means the application can request and use keystore keys, but the application cannot create hardware backing by itself. If the vendor shipped only a software-backed keystore, there is nothing the app can do to upgrade that into TEE or StrongBox protection.

## What This PoC Does

- Generates an AES-256 key in `AndroidKeyStore`
- Encrypts and decrypts data with `AES/GCM/NoPadding`
- Inspects `KeyInfo` to show the security level reported by Android
- Optionally requests StrongBox on supported devices
- Exposes the operations through an Android Binder service for on-device clients
- Includes an `adb`-driven host script so a Linux machine can trigger test commands remotely

## What You Need From The Vendor

- A real KeyMint or Keymaster implementation backed by secure hardware
- Correct Android framework integration so `AndroidKeyStore` routes to that implementation
- Attestation provisioning if you need remote proof for a backend, compliance team, or customer

## Important Limits

- Hardware-backed keystore protects key material, not your plaintext after decryption.
- On a rooted device, an attacker may still hook the app, inspect memory, or tamper with the runtime.
- This PoC proves device behavior as reported by Android on the target build. It does not independently certify the vendor implementation.
- Binder does not cross from your workstation into Android directly. A host machine must use a transport such as `adb`, a network API, or a vendor-specific bridge.
- Exposing generic decrypt operations over Binder creates a decryption oracle for any caller you authorize. In production, restrict callers tightly and return only the minimum data required by the protocol.

## Hardening Direction

- The Binder service now requires a signature-level permission, which means only apps signed with the same certificate can bind normally.
- The service also checks the caller UID at runtime and allows only same-signature apps, `shell`, `root`, or `system`.
- The `adb` test path remains intentionally separate through the debug receiver, so workstation testing still works without weakening app-to-app Binder access.
- For production, the next step should be replacing generic decrypt with protocol-specific operations so callers never receive more plaintext than they absolutely need.
