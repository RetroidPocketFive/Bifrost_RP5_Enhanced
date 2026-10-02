# v1.7.0 Baseline

This repository state is anchored to the user-tested **Bifrost RP5 Edition v1.7.0-alpha.3-debug APK**.

## Baseline artifact

- APK: `Bifrost-RP5-Edition-v1.7.0-baseline-test.apk`
- Size: 32,887,578 bytes
- SHA-256: `cfc9e5753ded3d4202e62b8e5d70d48bab6ccd5ced56a509397c774a7419e`
- Tested/confirmed by project owner before baseline adoption: **yes**
- APK was not modified; the test copy was a byte-for-byte copy of the uploaded artifact.

## Baseline rule

Future application changes must be made incrementally from this point. Existing v1.7.0 functionality is considered protected unless a change explicitly targets it.

Every future release should record:

1. Git commit/ref used for the build.
2. APK version and SHA-256.
3. A changelog describing functional changes.
4. A source/recovery ZIP corresponding to the build.
5. The tested APK as the build artifact.

## Recovery package

A forensic baseline package was created outside the repository containing the exact APK, extracted APK contents, resource inventory, DEX inventory, native-library inventory, dependency/version metadata, checksums, and recovery notes.

The APK is a compiled artifact, so this baseline does **not** claim that the original Kotlin/Gradle source has been fully reconstructed. The packaged code/resources are the reference for recovering behavior that must not be lost.

## Important

Do not use a later APK as the new baseline unless it has first been tested and explicitly confirmed as the new known-good build.
