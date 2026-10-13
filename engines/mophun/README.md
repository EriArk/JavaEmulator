# Mophun Runtime

This directory contains the selectively promoted engine and AbyssME adapter,
not the private test directory. There are no games, saved games, keys, captures,
soundfonts or preview/diagnostic activities. The library activity is not exported.

## Provenance

`vendor/` derives from the Android Java source of
[Wolphun](https://github.com/jamisson2006/wolphun-a-Mophun-emulator/tree/780649421fc5b1faabc7589c3165f17a6a316848),
commit `780649421fc5b1faabc7589c3165f17a6a316848` (GPLv3).
The upstream license is included in `assets/licenses/WOLPHUN-GPL-3.0.txt`.
The author's positive replies are not treated as an EPL linking exception:
this build instead removes the EPL M3G and tone implementations.

`Pip2` and `Renderer3D` credit Radrat Softworks' **nofun** (Apache-2.0).
Its copyright and license are preserved in `assets/licenses/NOFUN-APACHE-2.0.txt`.
This is an attribution to the underlying implementation, not a claim that
Wolphun was rebased to the latest nofun revision.

AbyssME modifications to upstream `Screen.java` and `Sdk.java` include text/
dialogs, pause/input ownership, safe file creation, monotonic ticks and
session-owned MIDI. `java/` is the AbyssME integration: bounded import/loading,
per-game storage and settings, controller/touch input, reversible display
settings, screenshots and lifecycle. These changes are distributed under GPLv3.
Original upstream comments and notices remain intact.

## Scope

Experimental direct `.mpn` import and shared library/controls are included.
Deep Abyss 1.1/1.4 have recorded compatibility work; general Mophun compatibility
and audio are not established. Known-game MIDI is not a universal sound engine.
No Symbian/N-Gage runtime is included. See `docs/PROJECT_STATUS.md` for evidence.

`tests/` contains fixture-free checks only. Commercial game probes remain local
and are not a requirement for building this source tree.
