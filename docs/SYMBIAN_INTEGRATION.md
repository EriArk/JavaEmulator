# Symbian and N-Gage Integration Plan

**Source audit: 2026-10-11. Status: researched, not implemented or built.**
The selected product direction is one AbyssME application and library, with
an embedded engine rather than requiring users to install and configure a
second emulator. Existing Java and experimental Mophun workflows stay intact.

## Evidence and Decision

The inspected EKA2L1 revision is
[`11f5b6cd141c0884a130ee45743f1f5e17bb1783`](https://github.com/EKA2L1/EKA2L1/tree/11f5b6cd141c0884a130ee45743f1f5e17bb1783).
The ignored local checkout is `local-games/eka2l1-audit/upstream`. Submodules
were not initialized; no firmware, game, native build or runtime was tested.

**Decision:** EKA2L1 is a plausible embedded-engine candidate, not a drop-in
Android library. Proceed to an isolated, opt-in local prototype. Do not claim
Symbian support in an AbyssME APK until runtime acceptance is recorded.

| Inspected source | Finding and implication |
| --- | --- |
| [Android JNI API](https://github.com/EKA2L1/EKA2L1/blob/11f5b6cd141c0884a130ee45743f1f5e17bb1783/src/emu/android/app/src/main/java/com/github/eka2l1/emu/Emulator.java) | Device installation/selection, app enumeration, icons, UID launch, keys, touch and screenshots already have entry points. Java helpers still depend on upstream UI, storage and Android services. |
| [Native bridge](https://github.com/EKA2L1/EKA2L1/blob/11f5b6cd141c0884a130ee45743f1f5e17bb1783/src/emu/android/app/src/main/cpp/src/native-lib.cpp) | Global engine/window state, process-wide working-directory changes and hardcoded Java class names require a host adapter. Surface destruction pauses threads; a complete host pause/resume/shutdown contract is not exposed. |
| [Launcher](https://github.com/EKA2L1/EKA2L1/blob/11f5b6cd141c0884a130ee45743f1f5e17bb1783/src/emu/android/app/src/main/cpp/src/launcher.cpp) | Launch uses a guest app UID. Device selection uses an array index; do not store that index as persistent game identity. Firmware installation currently chooses variant zero and supplies no progress/cancel callbacks. |
| [Engine build selection](https://github.com/EKA2L1/EKA2L1/blob/11f5b6cd141c0884a130ee45743f1f5e17bb1783/src/emu/CMakeLists.txt) | Android uses its own bridge, not the Qt frontend. Reusing the Android core does not require embedding Qt. |
| [Package/card installation](https://github.com/EKA2L1/EKA2L1/blob/11f5b6cd141c0884a130ee45743f1f5e17bb1783/src/emu/system/src/epoc.cpp) | Original N-Gage card folders and archive staging have installation paths. This is source evidence, not tested format/game compatibility in AbyssME. |
| [Device storage](https://github.com/EKA2L1/EKA2L1/blob/11f5b6cd141c0884a130ee45743f1f5e17bb1783/src/emu/system/src/devices.cpp) | Isolated C/D/E drives exist, with firmware-specific paths and additional OS state. A game's folder alone is not a reliable save backup. |

Upstream `Emulator.exitInstance()` kills its Android process. Keep that behavior
out of AbyssME's library process. Guest exit must become a session result, with
storage shutdown/flush verified before returning to the library.

## User Experience

1. Import a game through the existing Add games flow. Detect its format from
   content where possible; do not route every ZIP to Java or trust an extension.
2. If no suitable environment exists, show a short **Set up Symbian** wizard.
   Request the user's firmware/dump files, explain what is missing, validate
   them and show real install progress. Do not bundle or automatically download
   proprietary firmware or games. Upstream supports ROM/RPKG and firmware
   installation paths; exact accepted combinations need prototype tests.
3. Reuse a compatible installed environment on subsequent imports. Suggest an
   environment using package/device requirements when known; ask only when
   ambiguous. Do not claim one firmware works for every Symbian generation.
4. Show the installed game's name and native icon in the shared library. Use
   the existing fallback art policy; do not invent a cover or duplicate an icon
   over artwork. Hide OS utility apps from the main library without deleting them.
5. Play opens the game directly. Display, controls, screenshots and return to
   library should use AbyssME's existing vocabulary and interaction patterns.

Keep virtual-phone management under Settings, with friendly device names and
storage usage. Ordinary launches should not ask for drive letters, app UIDs,
CPU modes or a phone model every time. Never hide an unresolved compatibility
choice behind a fake automatic test/progress bar.

## Host Architecture

- Use a dedicated native Android library module and narrow host bridge. Start
  with a pinned upstream revision; maintain small, explicit integration patches.
- Run the Symbian session in an internal `:symbian` process in the same APK.
  This is lifecycle/crash isolation, not a separate user-facing app and not a
  licensing workaround. Keep its activities/services non-exported by default.
- Define initialize, install, enumerate, launch, pause, resume, release-input,
  stop/flush and error-result operations. Reconcile interrupted jobs after
  process death. Do not treat a surface pause as a durable saved game.
- Preserve or deliberately adapt upstream JNI class/callback names. Storage,
  text entry, dialogs, screenshots and Android service callbacks need explicit
  host implementations; simply copying a `.so` will not work.
- Reuse the current [AdditionalGames](../app/src/main/java/ru/playsoftware/j2meloader/catalog/AdditionalGames.java)
  boundary. It currently loads **one** optional engine. Before adding another,
  introduce a small dispatcher with stable engine IDs and backward-compatible
  reading of existing `additional-game.json` entries. Never replace the Mophun
  provider with Symbian or route every managed folder to the same runtime.
- Store a stable environment UUID plus app UID and package relationship in
  versioned metadata. An environment's directory isolates OS state; one package
  may expose several apps. Resolve native device indices at runtime. Handle
  duplicate imports, upgrades and missing environments explicitly.
- Map AbyssME controls to Symbian scan codes through an adapter, not raw MIDP
  key values. Include soft keys, phone keys, diagonals and key releases. Preserve
  short/held Select ownership and controller-disconnect cleanup. Touch must
  use inverse screen rotation/scaling and the guest viewport, not screen pixels.

## Storage and Backup

Use SAF grants and bounded staging into app-owned storage, not upstream's
all-files permission as a default. Validate archive paths, sizes and available
space; stage new environments before committing their library entries. Failed
imports must not delete or silently replace an existing virtual phone.

First reliable backup scope: **a stopped virtual phone and its installed games**,
including writable drives, registry/configuration, environment identity and host
library bindings. Define whether firmware is included in a private backup or
must be reselected by hash; make that requirement visible before export/restore.
Restore as a new validated environment, then switch library references. Test
interruption, corruption, low space and rollback before exposing the workflow.

Do not claim per-game save export until shared OS files and ownership have been
audited. Reusing Java's per-game folder archive without an engine-specific
backup contract would miss Symbian state. Do not force users to move files into
Android system folders.

## Build, Permissions and Publication Gates

- Upstream's actual root CMake requires **C++20**, despite older C++17 wording.
  Its Android project is `src/emu/android`, not the stale `src/android` path
  mentioned in BUILDING.md. Android configuration uses NDK `25.1.8937393` and
  CMake `3.22.1`; current AbyssME uses NDK `22.1.7171670` with ndk-build.
  Test module-specific toolchains and packaged native dependencies before any
  global upgrade. Keep AbyssME's Android 10 minimum.
- Begin with arm64, then x86_64 for emulator checks. Neither ABI has been built
  in this audit. Check C++ runtime packaging, native library name collisions,
  memory use and Android native page-size requirements before distribution.
- Inventory resolved submodules and their licenses, not only root LICENSE.
  The pinned tree references CPU/JIT, audio, FFmpeg, LuaJIT, archive, graphics
  and other dependencies. Their contents were not audited here.
- Native asset staging includes guest patches, shaders, compatibility files,
  scripts and sound banks. All need provenance and distribution review. The
  Android bridge still links Catch2 with tests disabled; separate test-only
  entry points/assets from a distributable build deliberately.
- Do not merge upstream's manifest wholesale. Camera, microphone, location and
  broad storage access are not baseline requirements for our chosen UX. Define
  denied/unsupported behavior and request any later capability only when needed.
- EKA2L1 uses GPLv3-or-later source notices and a GPLv3 license text. Its inclusion
  needs its own review against our EPL-1.0 M3G components. Wolphun permission
  would not authorize EKA2L1. See [LICENSING](LICENSING.md).
  No root license changes, combined public source import or APK publication are
  authorized by this research document.

For the long-term single-APK direction, resume the separate
[`codex/m3g-replacement`](https://github.com/EriArk/JavaEmulator/tree/codex/m3g-replacement)
investigation alongside engine work. Replacing both EPL native code and Java
bindings with a reviewed compatible implementation is a candidate resolution,
not a completed task. Do not remove working Java 3D support to get a quick build.

## Prototype Stages and Acceptance

| Stage | Deliverable | Gate |
| --- | --- | --- |
| 0: source audit | This plan and source evidence | Complete as a source-only review; dependency/license audit remains open |
| 1: isolated build | Pinned dependency inventory, native Android build, bootstrap and controlled shutdown without user firmware | Build logs, ABI/package inspection, repeat initialization/stop, no impact on existing Java/Mophun builds |
| 2: environment and import | Firmware wizard prototype, app catalog/icon enumeration, SIS/SISX and original N-Gage card import paths | Permitted fixtures, truthful progress/cancel, failed-install rollback, no duplicate environments |
| 3: game session | Unified launch, screen, audio, controller/touch input and return | Visible gameplay, listening check, input release, pause/resume, game save followed by cold relaunch |
| 4: product workflow | Phone/Handheld UX, whole-environment backup/restore and external-launcher integration | Small-screen checks, real device controls, interrupted restore tests, Java/Mophun regressions |
| 5: distribution | Reviewed dependencies/notices/source materials and licensing resolution | Explicit release decision; no claim of platform-wide compatibility |

Track original N-Gage (EKA1), other Symbian generations and N-Gage 2 separately.
Upstream exposes N-Gage 2 license helpers, but this audit did **not** establish
a complete `.n-gage` import workflow. Do not promise N-Gage 2 in the first slice
or bypass rights checks. Choose a small representative set of permitted games,
not a growing collection of one-off title patches.

Record source/build evidence, Android emulator observations and physical-device
acceptance separately. No performance estimate, APK size estimate, game
compatibility percentage or release date has been established by this audit.
