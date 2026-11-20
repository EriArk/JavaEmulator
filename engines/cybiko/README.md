# Cybiko

The Cybiko tile opens the shared library. Import a CyOS `.app` or a ZIP with one application. The importer distinguishes CyOS applications from Dingoo programs with the same extension. It reads the application title, minimum CyOS version and embedded launcher icon when available.

On first play, choose **Classic (v1)** or **Xtreme**. The picker shows the minimum OS version declared by the game. Select the matching boot ROM and CyOS flash image, together or individually. A ZIP with one pair for the model also works. AbyssME remembers the choice for this card and reuses system files across games. On the guest desktop open **Games** or **Apps** and select the installed title. Complete the Xtreme first-run wizard if prompted.

The d-pad moves the cursor, Fire sends Enter, the left soft key sends Select and the right soft key sends Esc. **More → Cybiko keyboard** types letters, digits and spaces. The common Controls and Display pages provide touch layouts, physical mappings, speed and screen positioning. **More → Change model** switches between machines. Each game, model and OS image has its own writable volume, included with system files in the handheld backup. Replacing a system image starts a fresh volume for that image.

Classic v1 was checked with boot ROM 1.1.12 and CyOS 1.2.46, Xtreme with boot ROM 1.5.0 and CyOS 1.5.08. The minimum OS version matters even among games labeled Classic. Classic v2, radio, multiplayer and expansion hardware remain outside this integration.

## Optional ARM64 debug build

Use Dragonfruit at revision `f142c9287f156b559de8d98b0030d2432b938e48`. With `ANDROID_NDK_HOME` set, run `bash engines/cybiko/build-native.sh /path/to/dragonfruit`, then enable `-PcybikoProbe=true` on the F-Droid debug build. The bridge compiles the H8 CPU and Cybiko hardware components from that checkout and uses the common Android audio, video and controller frontend. Firmware and games are supplied separately.

[Upstream core](https://github.com/SovGVD/Dragonfruit-JC4827W543-esp32/tree/f142c9287f156b559de8d98b0030d2432b938e48) · [Firmware and application format references](https://github.com/SovGVD/cybiko-web-emulator) · [Checks](../../docs/checks/2026-11-20-cybiko.md)
