# V5 Complete Source Verification

Status: VERIFIED

Protected V5 APK:
- Version: 1.7.0-alpha.3
- SHA-256: cfc9e5753ded3d4202e62b8e5d70d48bab6ccd5ced56a509397c774a7419e
- Size: 32,887,578 bytes

Verified source:
- Branch: v5/complete-source-verification
- Source base: 3345e4dd49d0d1bc3f224fa1a63c26bdfe8b7b4f
- Verification marker commit: 149a8e8c25be170096fb07e34bb2d41dc23b5007
- Main Kotlin source files: 89
- Main resource files: 86

Build verification:
- GitHub Actions run: 37114647407
- Unit tests: PASS
- Debug APK build: PASS
- Built APK size: 32,887,578 bytes
- Built APK SHA-256: 46acd680308e5cd49c8b334e00c0bd92eca4c17491c8d67aa045b55d0204efb8

APK content comparison:
- 11 DEX files: identical
- resources.arsc: identical
- AndroidManifest.xml: identical
- Native libraries: identical
- Complete extracted APK file inventory: identical
- SHA-256 comparison of every extracted APK file: zero content differences

The differing outer APK SHA-256 is packaging/container metadata only; the extracted APK contents are identical.

Protected baseline remains immutable. This verification does not authorize any feature or layout change.
