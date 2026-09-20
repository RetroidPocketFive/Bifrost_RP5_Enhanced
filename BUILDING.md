# Building Bifrost RP5 Edition

## Requirements

- Android Studio with a compatible Android SDK
- JDK 17 for the Android Gradle Plugin
- Android SDK Platform 36 / Build Tools 35.0.0

The repository includes the Gradle Wrapper configured for **Gradle 8.13**, so a separate Gradle installation is not required.

## Local build

From the repository root:

```bash
./gradlew assembleDebug
```

The debug APK will be created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## GitHub Actions build

The repository includes `.github/workflows/android-build.yml`.

After pushing the repository to GitHub:

1. Open **Actions**.
2. Select **Android Build**.
3. Run it manually with **Run workflow**, or push a commit.
4. Open the completed workflow run.
5. Download the `bifrost-rp5-edition-debug` artifact.
6. Extract the APK and install it on the Retroid Pocket 5.

The workflow provisions JDK 17 and uses the Gradle Wrapper, which downloads the exact Gradle 8.13 distribution required by the project.

## Release signing

Release signing remains externalized. Do not commit keystores or `keystore.properties`.
