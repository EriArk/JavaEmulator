# Native Symbian Screen Trials - 2026-11-01

## Availability and Scope

Unreleased source and a separate Debug APK installed on Retroid Pocket Classic.
No release asset, version bump, firmware or game payload is published by this
change. Issue #50 remains open: arbitrary resolution and genuine widescreen
gameplay are **not** delivered.

Symbian 9 phone profiles now offer an experimental **Emulated screen** choice
in pre-launch settings and Game menu > Display > Performance & advanced:
Original, Portrait (phone dimensions), Landscape (phone dimensions).
These change the virtual phone's initial display geometry, not Android image
rotation or Fit/Stretch. The game can still request its own screen mode.

Only dimensions already present in the phone's native screen configuration are
accepted. EKA1 and unknown phone generations do not offer the control. Native
validation also rejects EKA1 overrides and unadvertised dimensions. This is a
conservative capability boundary, not a claim that no EKA1 program could adapt.

## Implementation

- `guest_screen.h` adjusts the primary screen configuration before framebuffer
  allocation. Mode numbers, rotations, formats and hardware-state references
  remain intact; rotated modes retain transposed dimensions. The existing HAL
  panel information, window-server modes and framebuffer pitch derive from this
  configuration. Secondary screens remain unchanged.
- `patches.cmake` generates the window-server change against the pinned engine;
  the source checkout and supplied ROM are not edited.
- The native request resets on every prepare. Normal phone management and the
  next game cannot inherit a preceding game's override.
- `SymbianScreenPreset` is per phone/game. Missing or invalid values resolve to
  Original. It is separate from Java resolution and host image rotation.
- A running change requires explicit restart confirmation. The one-launch draft
  is consumed before native initialization, not when constructing the overlay.
  Keep persists it; Undo of an orientation trial restarts the confirmed setup.
  A rejected restart removes the staged draft. A failed boot cannot replay it.
- Existing native controls, screenshots, LCD geometry and touch use the active
  mode. The new choice and warnings are translated into all eight app languages.

## Retroid Evidence

Device: Retroid Pocket Classic, Android 14. Package:
`io.github.eriark.abyssme.debug`; the release app was not replaced or cleared.

`GuestScreenDeviceTest` uses disposable copies of the owner's installed phones
and compares every original file hash after the successful cycle.

| Fixture / requested screen | Panel | Active mode | Row bytes | Result |
| --- | --- | --- | --- | --- |
| N95 / Original | 320 x 240 | 320 x 240 | 1280 | Native capture available |
| N95 / Portrait | 240 x 320 | 240 x 320 | 960 | Native capture available |
| N95 / Landscape | 320 x 240 | 320 x 240 | 1280 | Native capture available |
| N95 / Original again | 320 x 240 | 320 x 240 | 1280 | Original geometry restored |
| N-Gage EKA1 / Original | 176 x 208 | 176 x 208 | 352 | Snakes startup capture available |
| N-Gage EKA1 / Portrait or Landscape | unchanged | unchanged | unchanged | Override rejected before launch |

The N95 fixture starts Asphalt3 through its N-Gage environment. Captures in this
check show the **N-Gage startup screen**, not a race or complete-game acceptance.
The portrait capture has an appropriately reshaped native display; it does not
establish additional game content or FOV. GLES screenshots may be host-sized,
so PNG dimensions alone were not used as guest-resolution evidence. The table
comes from native panel, active-mode and framebuffer-pitch inspection; guest
system-call tracing was not added.

Further checks:

- `run-native-tests.ps1` passes on Retroid, including the new orientation policy
  cases, mode-ID/rotation preservation, secondary-screen exclusion and bounds.
- Four Android test classes report `OK (34 tests)`: 28 pass, six existing
  optional cases skip. They cover native settings, session chrome, touch and
  shared display controls. Added cases verify trial consumption before boot,
  Keep, restart on Undo, game isolation and legacy/unknown capability policy.
- The additional native-screen selector UI test passes: selection remains a
  draft, does not change image rotation and is absent on the legacy path.
- The actual `SymbianActivity` Snakes launch/menu/Resume/LCD Soft/LCD Classic/
  Original/exit path passes the existing moving-frame check on Retroid
  (`runningNativeFramesSurviveMenuPause`; the three other opt-in methods skip).
  This verifies the regular application launch path after moving profile loading
  before boot; it is not a complete-game or arbitrary-resolution acceptance.
- `:app:assembleFdroidDebug` and `:app:assembleFdroidDebugAndroidTest` pass with
  the pinned combined engine. Debug installation succeeds with `adb install -r -d`.

Local evidence (ignored/private, not release media):
`build/guest-screen-build.log`, `build/guest-screen-settings-tests.log`,
`build/guest-screen-session-ui.log`,
`build/guest-screen-menu.png`, `build/guest-screen-modern-portrait.png` and
device-side `guest-screen-*.png` in the Debug app's external files directory.

## Rejected Experiments and Remaining Work

An initial unrestricted prototype also tried 320 x 240 and 384 x 216. Snakes on
EKA1 visibly corrupted its directly drawn image at changed widths. The N95
384 x 216 attempt changed engine geometry but did not yield a capturable frame
within the bounded startup check. Neither result is a widescreen success.
**Arbitrary-size presets were removed**, and EKA1 overrides were blocked rather
than adding a Snakes-specific workaround or shipping the broken choices.

Still needed for #50: broader adaptive-application and actual 3D-game evidence,
custom/widescreen geometry with consistent native UI/viewport behavior, and
additional OS-family coverage. Original remains the recommended setting for
fixed-layout games. Never describe this checkpoint as universal widescreen,
increased game detail, higher internal 3D resolution or a completed #50.
