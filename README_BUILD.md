# Building Leanview

This is the complete corresponding source of Leanview 1.0.0 (GPL-3.0, see LICENSE and NOTICE.md). The APKs published with the
release were built from exactly this tree.

## What you need

- JDK 25 (Temurin) and the Android SDK (`ANDROID_HOME`, or `sdk.dir` in a `local.properties` file you create) with platform
  `android-34`, build-tools `30.0.3`, and NDK `21.0.6113669`.
- Internet access to the Maven repositories (Google, Maven Central).

## Build

```
./gradlew --no-daemon :smarttubetv:assembleLeanviewRelease -PleanviewVersion=1.0.0
```

The APKs (arm64-v8a, armeabi-v7a, x86 and universal) are written to `smarttubetv/build/outputs/apk/leanview/release/`, named
`Leanview_1.0.0_<abi>.apk`. Without a signing configuration they are UNSIGNED. The version code is
major * 10000 + minor * 100 + patch.

`./gradlew --no-daemon :smarttubetv:testLeanviewDebugUnitTest` runs the unit tests of the app and `:common:testDebugUnitTest`
those of the common module (run them as separate Gradle calls); `:youtubeapi:testDebugUnitTest` runs those of the YouTube module,
many of which (upstream's) need the network. `leanview` is the app's only flavor and the libraries have none, so `./gradlew assemble`
builds everything.

## Signing

The signing key is the publisher's own and is never part of the source. To sign the release build with your own key, create
`keystore.properties` in the root folder:

```
storeFile=/path/to/your.keystore
storePassword=...
keyAlias=...
keyPassword=...
```

An APK signed with another key has another signature than the published ones and cannot be installed over them; install it as a
fresh app or uninstall the other first. The signature of the published releases is described in README.md.

## Third-party code

THIRD_PARTY_NOTICES.md lists every component with its license; it is generated from the build's dependency graph.
