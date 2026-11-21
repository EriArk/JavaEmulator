# Firefox OS and KaiOS packaged web games

Enable `-Pb2gProbe=true` for the optional fdroid debug host. The two platforms share the in-app WebView display, graphics settings and controller profiles with the Palm webOS host. No KaiOS SDK, operating-system image, signing key or game is part of the build.

## Installing a game

Select a ZIP from the relevant library tab, open it from Android, or index its folder. The archive must contain its manifest and local launch page at the root or under a single wrapper directory. Typical Firefox OS and KaiOS 2.5 packages use `manifest.webapp`. For ambiguous legacy packages the active import tab selects the edition. Opening the file externally offers both choices and remembers the selection for that document. A modern `manifest.webmanifest` with KaiOS B2G metadata selects KaiOS. A generic PWA manifest alone does not identify a phone edition.

AbyssME reads the title, version, icon and launch URL from the package and serves its resources directly from the ZIP. Reimporting identical bytes under the same platform reuses the card. Firefox OS and KaiOS have independent storage and profiles. Each package receives its own local HTTPS origin, so games using relative URLs can run offline. The ZIP, portable localStorage data, screen size and per-game profile participate in library backup. The browser's service-worker caches and IndexedDB are outside that backup.

## Guest behavior

- Firefox OS defaults to 320 × 480 and KaiOS to 240 × 320. A landscape manifest swaps the dimensions. **Game menu → More** offers alternate guest sizes.
- Touch, D-pad, Enter and digits use the shared game controls. Left and right soft keys, Backspace, star and pound are delivered as phone keyboard events. HTML audio uses Android playback. The legacy `navigator.mozApps.getSelf()` request reads the current package's manifest.
- The Android WebView runs in a separate process. Main navigation and resource requests are limited to the local origin. File and content access are disabled, including for service workers. The app applies a host content security policy and any additional manifest CSP. KaiStore signatures confer no device permissions. Camera, SIM, telephony, KaiAds and vendor system services are unavailable. Games requiring a particular Gecko build or hard-coded vendor origin need separate compatibility work.
- Both KaiOS 3 and 4 use modern web manifests. The file extension alone cannot establish which OS generation a game targets. See [KaiOS manifest documentation](https://developer.kaiostech.com/docs/sfp-3.0/getting-started/main-concepts/manifest/) and the [device results by edition](../../docs/checks/2026-11-21-b2g.md).

The platform tiles use photographs of the ZTE Open and Nokia 8110 4G. [Credits and licenses](bridge/src/main/assets/licenses/b2g-art.txt).
