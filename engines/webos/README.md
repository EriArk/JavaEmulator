# Palm webOS packaged web games

The optional Android host runs HTML, JavaScript and CSS from a Palm webOS IPK in a dedicated in-app WebView process. No firmware or Palm SDK is required. The archive reader accepts ar and gzip-compressed tar IPKs with a data.tar or data.tar.gz payload. Single-game ZIPs and loose IPKs share one library identity. The library extracts the title, version and PNG icon from the application manifest. Games open offline with separate origins. Installation scripts and native PDK executables are outside the web runtime.

## Build

Clone [enyojs/enyo-1.0](https://github.com/enyojs/enyo-1.0), then run the commands below from the repository root. The preparation step reads the pinned Git object even if the checkout is on a different branch.

```sh
python3 engines/webos/prepare.py /path/to/enyo-1.0
./gradlew -PwebosProbe=true :app:assembleFdroidDebug
```

The preparation script extracts the original Enyo 1 framework, its license and notices into the ignored build directory. The framework is Apache-2.0 with component notices including the Opus-Js MIT notice. The device sticker has a [separate attribution](bridge/src/main/assets/licenses/webos-art.txt).

## Runtime coverage

- Enyo 1 loads from device or SDK framework URLs. The independent Mojo adapter implements scene assistants, template loading, tap/drag and key events, button widgets, dialogs, cookies and synthesized system feedback. Games can also use ordinary web audio.
- Touch reaches the guest screen directly. The shared keypad and physical-controller mappings deliver arrow keys, Enter, Space, Escape and digits. The shared display presets, game area and controller profiles remain available.
- Under **Game menu → More → webOS screen size**, choose 320 × 480, 480 × 800 or 1024 × 768. Changing the size restarts that game. The selection is included in its backup.
- The original IPK and the portable localStorage snapshot are backed up beside the game's configuration. Reopening or reimporting the same package retains its data. Browser databases, general Luna services, native plugins and entire system emulation are outside this checkpoint. Web SQL availability depends on the installed Android WebView.
- Game speed scales newly registered JavaScript timers. It does not change audio pitch or control existing animation clocks.

See the [Retroid acceptance record](../../docs/checks/2026-11-21-webos.md) for individual results.
