# Atari Portfolio

The optional Android host runs the Portfoliator 8088 and LCD core offline in an Android System WebView worker. The combined private build exposes a Portfolio platform tile and tab. The game uses the shared display, controller, artwork and library backup paths.

## Build

Run `python3 engines/portfolio/prepare.py /path/to/portfoliator` with a checkout containing the [pinned revision](https://github.com/delysio/portfoliator/tree/fb3b254a4581b49de5bbe37c90027b1e9b3c4bf7). Enable `-PportfolioProbe=true` for the Android build. Preparation copies the core, selected helpers and upstream license into the ignored build directory.

## Import and controls

Open a DOS COM, DOS MZ EXE or a ZIP containing the program and its companion files. On first launch choose the two 128 KiB system ROMs, 32 KiB LCD ROM and 1,472-byte HD61830 font. The picker accepts multiple files, ROM-set ZIPs and a combined 256 KiB system dump. The same firmware serves the entire library.

The game directory is mounted as a writable 256 KiB FAT12 card on A:. Files beside the selected executable receive DOS 8.3 names. Subdirectories below that directory are not mounted. A DOS executable can require hardware the Portfolio lacks. For packages with multiple programs, use **More → Choose program**.

The directional pad sends cursor keys. The Fire, left soft key and right soft key actions send Enter, Space and Escape. The virtual keypad and physical controller use the shared mapping screen. **More → Portfolio keyboard** sends text, optionally followed by Enter. **Portfolio special keys** includes Ctrl+C. On-screen graphics use the original 240 × 64 display.

Card writes and changes to the internal RAM disk are persisted per game. **More → Save state**, **Load state** and **Save and exit** store a separate machine snapshot for each executable. A saved snapshot resumes on launch. Library backup includes imported programs, firmware, snapshots, disk data and configuration.
