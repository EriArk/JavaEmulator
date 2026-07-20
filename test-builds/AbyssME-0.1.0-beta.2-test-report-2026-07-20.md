# AbyssME 0.1.0 Beta 2 test report

## Automated checks

- `:app:testFdroidDebugUnitTest`: passed
- `:app:testPhoneDebugUnitTest`: passed
- `:app:lintFdroidDebug`: passed
- `:app:lintPhoneDebug`: passed
- `:app:assembleFdroidRelease`: passed with R8 and resource shrinking
- `:app:assemblePhoneRelease`: passed with R8 and resource shrinking

## Handheld APK

- Package: `io.github.eriark.abyssme`
- Version: `0.1.0-beta.2` (`102`)
- Main activities: landscape (`screenOrientation=0`)
- SHA-256: `EDEDBB4FB781718635226AADF48CEE12DE041857FF2B63944B6785E1EB748DC6`

## Phone APK

- Package: `io.github.eriark.abyssme.phone`
- Version: `0.1.0-beta.2-phone` (`102`)
- Main activities: unspecified/rotatable (`screenOrientation=-1`)
- New profiles: virtual keypad and direct touch enabled
- Portrait library: compact single-pane layout
- SHA-256: `C37492B05C36E687AA8FBA38A6D4130CA9AB9DA44B0A791DA95F2E826E4DFD68`

## Shared verification

- Minimum Android: 10 / API 29; target API 34
- ABI: arm64-v8a, armeabi-v7a, x86, x86_64
- APK Signature Scheme v2: verified
- Signer: `CN=AbyssME, O=EriArk, C=US`
- Certificate SHA-256: `722B6F8E74090BD3895EB0BC76433333C637E881758FC10AC276DD214499C38F`

## Device status

No ADB device was connected during this build. Rotation, pointer input, virtual
key placement, and portrait layout still require a physical-device pass.
