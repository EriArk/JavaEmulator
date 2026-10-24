# Live Fluxer Account Checkpoint

2026-10-24. Unreleased development verification on Retroid Pocket Classic,
Android 14. Only the separate `io.github.eriark.abyssme.debug` installation was
used. Public app data, release assets and the shared service were not changed.

## Observed

- An email-verified test account approved the device code in the official Fluxer
  browser flow. AbyssME showed the connected account and a securely saved session.
- A fresh instrumentation process restored the stored session. Recreating the
  account activity retained the connected, remembered state.
- Opening Lobbies automatically obtained membership in the expected shared lobby
  community. The web client, which previously did not list that community, then
  displayed it. Its TrainerOS name is intentional; product filtering is separate
  from shared membership.
- Authenticated directory access completed with `No compatible lobbies right now.`
  This is empty-directory evidence, not live advertisement/filtering acceptance.
- Two opt-in Android tests passed (`OK (2 tests)`, 5.514 seconds). Production
  runtime code did not require changes in this checkpoint.

## Reproduction

Use an authorized test account, complete browser approval normally, and explicitly
permit joining the shared community. The test can change community membership and
captures account/directory screens locally. Never run it on an unsuspecting user's
account or publish credentials, approval codes or private screenshots.

```text
gradlew.bat :app:assembleFdroidDebugAndroidTest -PsymbianRelease=true -PsymbianSourceDir=build/symbian-source-check --console=plain
adb -s DEVICE install -r app/build/symbian-release/outputs/apk/androidTest/fdroid/debug/app-fdroid-debug-androidTest.apk
adb -s DEVICE shell am instrument -w -r -e fluxerApprovedLive true -e class ru.playsoftware.j2meloader.multiplayer.FluxerLiveAccountDeviceTest io.github.eriark.abyssme.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Without explicit opt-in or a saved session these tests are skipped, not passed.
A repeat run with existing membership verifies access, not another first join.
The test never reads or exports the session token. Captures stay in the debug
app's external-files directory under `fluxer-live-checks`.

Local ignored evidence: `build/fluxer-live-retroid.txt`,
`build/fluxer-live-test-build.log`, `build/fluxer-live-connected.png`,
`build/fluxer-live-checks/account-restored.png` and
`build/fluxer-live-checks/directory-live.png`.

The existing development APK label is 0.5.0-beta.1 / code 310; it is not the public
release artifact. The corresponding local app artifact SHA-256 is
`4e78bb914080ea32cc9c17279ec5269b6c2e344ef699276037f8f5c9e6c068ef`.
The app was not reinstalled in this checkpoint; only the instrumentation APK was.

## Remaining Boundaries

Live logout, two-account chat and friendships, room creation/publication/admission,
temporary-contact cleanup, physical gamepad navigation and real online gameplay
remain unverified. Earlier fixture suites were not rerun as part of this two-test
checkpoint. No claim is made about full-game compatibility or network performance.
The shared service v2/result work remains a separate undeployed change; production
game adapters remain empty. No new release or deployment was performed or audited.
