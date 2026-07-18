# AbyssME 0.1.0 Beta 1 test report

## Automated checks

- `:app:testFdroidDebugUnitTest`: passed
- `:app:lintFdroidDebug`: passed
- `:app:assembleFdroidDebug`: passed
- `:app:assembleFdroidRelease`: passed with R8 and resource shrinking
- APK package: `io.github.eriark.abyssme`
- Version: `0.1.0-beta.1` (`101`)
- SDK range: Android 10 / API 29 to target API 34
- ABI: arm64-v8a, armeabi-v7a, x86, x86_64
- APK Signature Scheme v2: verified
- Signer certificate: `CN=AbyssME, O=EriArk, C=US`
- APK SHA-256: `9714DF7D9E7F3F835334A8460A6ECD5F007B90B9507A59D8034DB1C3C274A3D3`

## Covered by static and build verification

- Room catalog schema with source identity, variants, favorites, and recents
- SAF folder indexing with lazy first-play preparation
- JAR, JAD, ZIP, and 7Z intent filters and archive size limits
- Multi-JAR archive chooser
- ES-DE custom systems and emulator find rules
- Three-action in-game overlay and landscape-only activities

## Device status

Odin 2 was not reachable through USB or its previous wireless ADB endpoint during
this build. Installation, controller focus, external-launch return behavior, and
screenshots still require a connected-device pass.
