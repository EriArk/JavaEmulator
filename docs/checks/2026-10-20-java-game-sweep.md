# Twenty-game Java compatibility sample

Date: 2026-10-20. Source baseline: `f1c1a3d6`.
Unreleased Debug changes, Android 15 x86_64 emulator with SwiftShader.
Retroid was unavailable. Public APKs were not changed; live release state was
not rechecked. This is a bounded startup/input/rendering sample, not a
twenty-game compatibility certification or a handheld performance benchmark.

## Shared repairs

### GameCanvas key polling

`getKeyStates()` previously returned only currently held keys. A press and
release between two polls disappeared, two physical keys mapped to the same
action could release each other, and hidden canvases could report stale input.
The shared GameCanvas implementation now latches complete taps until the next
poll, tracks held physical aliases independently, synchronizes cross-thread
access and clears state across visibility changes.

This implements the [MIDP GameCanvas polling contract](https://docs.oracle.com/javame/config/cldc/ref-impl/midp2.0/jsr118/javax/microedition/lcdui/game/GameCanvas.html).
It is not a game-title workaround. Four new tests all failed against the
installed baseline; the corrected runtime suite passed 41 tests. Both
key-callback suppression modes, multiple actions, cross-thread polling, aliases,
hide/resume and existing Canvas/timing behavior are covered.

Need for Speed Most Wanted in this sample references `getKeyStates()`.
Simply extending GameCanvas does not prove that a game uses polling.

### Interrupted installation recovery

The Android emulator process terminated during the first import sweep. After
restarting the same AVD, Worms still had a catalog entry but its converted DEX,
descriptor and resource JAR were zero length. Opening it failed with
`DescriptorException: Bad descriptor`. The original supplied JAR was intact.
The cause of the host emulator termination was not established.

Reopening the original source now validates the prepared files before reusing
an installed entry. Missing/empty files or invalid prepared metadata trigger
conversion in place, preserving the entry identity, custom title, favorite,
play count, settings and RMS directory. Same-source resource JAR mismatches
also trigger rebuilding. This is not a complete DEX corruption detector or
automatic recovery without an accessible original source.

Remembered alternate source URIs no longer launch an invalid prepared copy.
An invalid alias target is retained and a new ready copy is imported; this is
not an alias-based in-place save migration.

Both new recovery tests failed before the repair; all 22 installer tests passed
afterward. The interrupted Worms installation was then repaired through normal
JAR reopening and reached a battle map with input and turn-timer changes.

### Canvas-to-native-screen overlay cleanup

Snake III exposed a stale phone keypad over its native LCDUI lists. The keypad
did not operate those lists, which remained usable by direct touch.
`clearDisplayableView()` discarded the inner SurfaceView before its delayed
destruction callback; that callback then correctly rejected the obsolete view,
but never removed its controls, soft bar, LCD layer or FPS counter.

Canvas now performs the same surface cleanup before dropping its view reference.
Obsolete callbacks still cannot tear down a replacement view. A new installed
regression failed on the stale overlay before the fix, then passed the
Canvas/native-screen/Canvas round trip in software, GLES and hardware modes.
It verifies both overlay removal and actual rendered pixels after recreation.
The rebuilt app was also reopened into Snake III: its native menu no longer has
the stale keypad, and its own bottom SELECT / EXIT commands are visible.

## Fixture sample

The supplied Kevtech archive is the source except for Metal Slug Mobile Impact,
which is the previously staged Retroid fixture. No games or extracted bytecode
are included in Git. Exact hashes, manifests and API-reference inventory remain
in ignored `build/game-sweep-2026-10-20/inventory.json`.

The four M3G-reference samples are NFS Most Wanted, Snake III, Deep and Rally 3D.
Other samples cover older MIDP 1.0, GameCanvas, ordinary Canvas and Nokia APIs.
API references alone are not a successful execution result.

| Fixture | Version | Observed checkpoint / limitation |
| --- | --- | --- |
| Doodle Jump | 1.3.0 | Active jumping scene, score 236; pause menu. Default 240x320 clips this variant; live 320x240 trial shows the complete pause menu. |
| Bounce Tales | 1.15 | Reopened existing copy, selected Chapter 1 and reached its rendered opening dialogue. |
| Ancient Empires II | 1.0.2 | Reopened existing copy to animated publisher splash; earlier same-day introduction evidence is separate. |
| Tower Bloxx | 1.3.10 | Language selection rendered. |
| Doom RPG | 1.0.92 | JAMDAT opening logo; not yet gameplay. |
| Worms | 1.9.0 | Repaired interrupted import; language, menu and battle map reached; input changes battle state. |
| Rayman 3 | 1.0.3 | Sound selection rendered. |
| Prince of Persia: The Two Thrones, S700 file | 1.1.5 | Sound selection rendered. |
| Splinter Cell 2: Extended Ops | 1.0.1 | New Game menu; small fixed-size content inside a larger canvas. |
| Gravity Defied 3 archive variant | 1.0 | Main menu and moving background demo. This is the supplied modified variant. |
| Block Breaker Deluxe | 1.0.1 | Main menu rendered. |
| Bubble Bash 2 | 1.0.9 | Sound prompt with clipping; correct target dimensions still need confirmation. |
| Need for Speed Most Wanted | 6.0 | Language/sound/menu flow and instant race; textured scenery, moving car, speed 005 to 035 and steering input captured. |
| Crash Nitro Kart | 1.6.0 | Title screen rendered. |
| Metal Slug Mobile Impact S40v3 | 0.0.6 | Reopened existing copy, reached New Game, pause/resume and rendered first playable jungle scene. Short menu taps were unreliable; held soft/fire inputs progressed. |
| N.O.V.A., 240x320 file | 1.0.6 | Warm launch reaches Spanish sound selection; initial short capture was the blank host during startup, not a confirmed hang. |
| Snake III | 1.0.1 | Native menus through Classic / 1 player, moving 3D board and Game over. Exposed stale native-menu keypad; edge/viewport artifacts remain to isolate. |
| Deep: Submarine Odyssey | 1.0.2 | Animated textured 3D menu, numeric name entry, character selection and opening introduction. |
| Rally 3D, 6280 file | 1.2 | Textured car selection and race, steering and speed display observed. Nonfatal startup exception noted below. |
| Darkest Fear | 1.19 | English language selection rendered. |

Earlier same-day Bounce/Ancient/Metal Slug evidence:
[Java frame cadence investigation](2026-10-20-java-frame-cadence.md).

## Remaining findings

- Some fixed-resolution game builds do not declare usable screen dimensions.
  The conservative 240x320 fallback can crop landscape content or leave margins
  around smaller fixed layouts. Doodle Jump's complete 320x240 pause-menu trial
  is evidence for that fixture, not permission to assume every unknown JAR is
  landscape. No title-specific auto-profile rules were added.
- Rally 3D logs a nonfatal `showNotify` null-image exception before its title
  assets finish loading, on both initial and repeat startup. Local bytecode
  inspection confirms the callback draws the image field without a null check.
  The game subsequently reaches a controllable race. No broad suppression of
  invalid `drawImage(null)` calls or arbitrary startup delay was introduced.
- Snake III reaches gameplay but the captured board has a vertical edge and
  discontinuous background regions. This is not visual-parity acceptance;
  logical resolution, viewport and renderer behavior need isolation.
- Metal Slug's menu did not consistently respond to short taps. A debugger
  breakpoint confirmed delivery to the game's own `keyPressed`; its event
  queue was not deadlocked and Canvas remained visible. Held inputs progressed
  into gameplay and resumed from pause. This ordinary-Canvas behavior is not
  covered by the GameCanvas polling repair; no input-delay workaround was added.
- The entire Windows Android-emulator process terminated twice during the
  extended sweep, without an identified cause in its saved output. The second
  interruption occurred while reopening Bounce Tales. This is not evidence of
  a reproduced AbyssME crash in either game.
- Sound prompts are not listening tests. No full-game, physical-controller,
  game-save round-trip, Retroid FPS or comparative engine acceptance is claimed.

## Build and regression evidence

- Fresh JVM suite: **49 passed**.
- Final Android runtime/lifecycle/timing/input suite: **42 passed**, 41.649 seconds.
- Android installer/source/JAD/archive suite: **22 passed**, 99.446 seconds.
- Debug app/test builds and lint passed; **0 lint errors, 400 warnings**.
- Local Debug APK retains the existing version label; it is not a new release.

Ignored evidence under `build/game-sweep-2026-10-20/`:
`polling-before.log`, `polling-after.log`, `recovery-before.log`,
`recovery-after.log`, `overlay-before.log`, `final-runtime.log`,
`fixed-build.log`, `recovery-build.log`, `final-build.log`, `inventory.json`,
`actions.txt`, numbered startup and interactive PNG/log captures, emulator
stdout/stderr. `00-gameplay.png`, `00-landscape-applied.png`, `05-battle.png`,
`01-chapter.png`, `12-race.png`, `12-racing.png`, `16-single.png`,
`16-moving.png`, `16-clean-menu.png`, `18-loaded.png`, `18-name.png`,
`18-intro.png`, `14-held-resume.png`, `metal-threads.txt`, `19-track.png`,
`19-startline.png` and `19-gameplay.png` record the deeper checkpoints.

SHA-256 prefixes (first 16 hex digits, in inventory order):

```text
00 DoodleJump.jar                            3f9972bc57b8d4a9
01 BounceTales.jar                           d69de01b5e452678
02 AncienEmpiresII.jar                       98f6af1dd266dec0
03 Tower_Bloxx.jar                           c9f77eb6aed177a1
04 DoomRPG.jar                               ddd65f2e0703a0fa
05 Worms.jar                                 d877a025e19e8753
06 Rayman3.jar                               4bcfe076108cb5a6
07 Prince_of_Persia_the_Two_Thrones_S700.jar   5fb93d7b8b9e169d
08 SplinterCell2ExtendedOps.jar               8b91c6c96067ecbf
09 Gravity Defied 3.jar                      45aed0dbcd1474ac
10 BlockBreakerDeluxe.jar                    ca08ad47b6e192f2
11 BubbleBash2.jar                           4ebeba06d3f26bc9
12 Need for Speed Most Wanted.jar            077472c0c6e75d93
13 crashnitrokart.jar                        57bd5b5b06e08c58
14 MetalSlugMobileImpact_S40v3.jar            fa2852ef2b9a87a2
15 TNOVA 240x320.jar                         93a5b066b84d4aec
16 Snake_III.jar                             667a241453acdb90
17 DarkestFear.jar                           8b2773c13535d245
18 Deep3D.jar                                89f3a940ff1be281
19 rally3d6280.jar                           675de9455c08cb03
```
