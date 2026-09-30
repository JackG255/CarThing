# Releasing

## Versions
CI takes the version from the git tag through the `CARTHING_VERSION` environment variable:
`v1.2.3` → version name `1.2.3`, version code `10203` (major×10000 + minor×100 + patch).
Minor and patch must be below 100. Version codes must only go up, or Android refuses the update,
so every tag must be higher than the last. Local builds are `0.1.0` / `1`.

## Signing
Release builds are signed when `keystore.properties` exists in the project root. The file is git-ignored.

```properties
storeFile=C:/Users/<you>/.carthing/carthing-release.jks
storePassword=...
keyAlias=carthing
keyPassword=...
```

Release builds are also signed when the environment variables `CARTHING_KEYSTORE`,
`CARTHING_KEYSTORE_PASSWORD`, `CARTHING_KEY_ALIAS` and `CARTHING_KEY_PASSWORD` are set. Without
either, the release APK is unsigned. That's what the CI workflow builds, to catch missing R8 keep
rules early.

**Keep the keystore and its password backed up.** Updates must be signed with the same key. If the
key is lost, users have to uninstall the app, which deletes its data, before installing a build
signed with a new key. They can restore their data from a CarThing backup afterwards.

## Publishing
1. Make sure `main` is green in CI.
2. Add a section to [CHANGELOG.md](../CHANGELOG.md).
3. Tag and push:
   ```
   git tag v0.4.0
   git push origin v0.4.0
   ```

The Release workflow (`.github/workflows/release.yml`) then:
1. runs the unit tests;
2. restores the keystore from the `CARTHING_KEYSTORE_BASE64` secret;
3. builds the signed release;
4. publishes a GitHub Release with `CarThing-<version>.apk` (arm64) and `CarThing-<version>-older-phones.apk` (armeabi-v7a), install notes, and auto-generated PR notes.

Repository secrets: `CARTHING_KEYSTORE_BASE64`, `CARTHING_KEYSTORE_PASSWORD`, `CARTHING_KEY_ALIAS`,
`CARTHING_KEY_PASSWORD`.

Share <https://github.com/JackG255/CarThing/releases/latest>, which always points to the newest build.

## Building locally
`./gradlew assembleRelease` writes one APK per ABI to `app/build/outputs/apk/release/`. Phones use
`app-arm64-v8a-release.apk`.
