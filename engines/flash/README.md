# Flash Lite

The optional Flash Lite platform runs user-supplied SWF movies offline in the Android System WebView using a pinned Ruffle self-hosted player. It shares the handheld library, graphics profiles, physical mappings, touch keypad and game-area settings. No firmware is needed.

Open **Flash Lite → Add** and select a raw SWF or a ZIP. Uncompressed and zlib-compressed movies are supported. Keep levels and other assets in their original directories inside the ZIP. When a game contains multiple SWFs, name the ZIP after the starting SWF (for example, `Balloonatroid.zip`). A ZIP containing only one SWF and the same loose file share one card and save. Folder scanning and Android Open With also use the handheld importer. The game runs from a private local origin and can load companions from its package.

Arrows and OK map to direction and Enter. The phone's left and right soft keys map to Page Up and Page Down. Tap the stage for games with pointer targets. Open the game menu to change graphics, the keypad or physical mappings. Games using Flash SharedObject storage keep their saves in the normal handheld backup. Flash Lite device commands and compatibility vary by title. The private Retroid tests cover early AVM1 phone games.

## Build

Run `python3 engines/flash/prepare.py` from the repository root, then add `-PflashProbe=true` to the Android debug build. The preparation script validates the Ruffle 0.8.0 npm package's SHA-512 digest and writes the player and its licenses under the ignored build directory. The Ruffle WebAssembly files, games and Android WebView are supplied separately from the integration source.

[Upstream player](https://github.com/ruffle-rs/ruffle/tree/cd4cb8f45f144d5a9209f496ee09b60ad136b5b3) · [Upstream API](https://ruffle.rs/js-docs/master/) · [Retroid checks](../../docs/checks/2026-11-21-flash-lite.md)
