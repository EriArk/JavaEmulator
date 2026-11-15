# Independent right stick — 2026-11-15

## Implementation checkpoint

Local source for issue #55 adds eight independently mappable right-stick inputs
(four cardinal and four diagonal directions) to the existing physical controller
editor under Sticks. Identify selects the right direction; R3 remains a button.
Missing diagonal assignments combine the two cardinal outputs; explicit Unbound
suppresses that diagonal. Profiles retain their existing IDs and values; old
presets leave the new right-stick directions unbound. These are digital phone
key mappings, not proportional analog camera control.

One Android motion adapter serves J2ME, Mophun and Symbian/N-Gage. It selects a
complete centered Z/RZ pair from device motion ranges, or RX/RY when available.
Missing, incomplete or unipolar pairs do not become a stick; dedicated trigger,
brake and gas axes remain separate. Right-stick normalization respects device
flat ranges, and state uses the same press/release hysteresis as the left stick.
Combined ownership prevents neutral/disconnect from releasing a key still held
by another input. Unusual devices with ambiguous metadata still need testing.

## Checks and limits

- Fresh application JVM suite: 434 tests, no failures/errors/skips, including
  12 new axis-selection and right-stick state regressions.
- Retroid Pocket Classic / Android 14: 22 instrumentation checks pass, covering
  both sticks, mapper identification, serialization, old defaults, existing
  mapper layouts, device lifecycle and fast-forward controls.
- Two additional checks pass at the Mophun keypad/button-mask and Symbian native
  scan-code boundaries, including independent release and disconnect.
- Android motion fixtures use the device's reported joystick ranges. A separate
  kernel uinput joystick is available for signed-build UI/game checks and video.
  [Android uinput reference](https://android.googlesource.com/platform/frameworks/base/+/android15-platform-release/cmds/uinput/).

The owner cannot provide a physical two-stick controller for this release.
Automated/virtual-device checks are not physical-stick, Bluetooth disconnection,
full-game or ergonomic acceptance. Issue #55's physical-controller criterion
remains open. Publication and final signed-build evidence follow in the release
report; this checkpoint alone does not distribute an APK.

Private logs and commands: `build/checks/2026-11-15-right-stick/`.
