# Flash Lite on Retroid Classic · November 20–21, 2026

The optional Android bridge uses the self-hosted Ruffle 0.8.0 player and the common handheld renderer. A combined ARM64 debug APK replaced the existing installation on a Retroid Pocket Classic running Android 14. The final replacement preserved 9,013 inventoried files and all 81 Java catalog rows. Both phone games acquired preview art from their own frames. This device check produced no public APK.

| Game or check | Result |
| --- | --- |
| MicroInvaders, SWF4, 176 × 208 | Imported the developer's phone game. Used the left soft key to open Options, chose Play and reached Stage 1 with moving enemies and a player ship. |
| Balloonatroid, SWF7, 240 × 320 | Imported the main movie and Level13 together in a ZIP, tapped Play and reached the balloon and volley controls. Both files were in a nested directory. Setting the loader base to the main movie's directory fixed the missing level. |
| Rendering and controls | Used the actual Retroid screen, injected touch events and Linux input events on the Xbox controller device. Enabled the virtual keypad for MicroInvaders through the shared Controls menu, saved the setting, and restarted a level with the on-screen left key. Phone soft keys reach the SWF as Page Up and Page Down. The private display gives the renderer keyboard focus so that a controller can operate the title menu before any stage tap. |
| Audio | The two games and the companion movie contain no embedded sound tags. A separate generated SWF played a 440 Hz PCM tone. Android system-output capture included 390,612 nonzero mono samples out of 561,152 and reached a peak magnitude of 5,498. Six successive one-second windows measured 440 positive-going zero crossings each. |
| Storage and import | A generated SWF incremented a SharedObject counter. Two cold launches advanced the on-disk value from 1 to 2 to 3. Nine Android instrumentation tests passed across game import, library tabs, import routing and a populated backup restored into a separate directory. The backup test compared game packages and save contents by SHA-256. The import tests covered a single-movie ZIP matching the loose SWF, repeat imports, truncation and fetching a companion from the archive. |

Ruffle reports an unsupported Flash Lite `FSCommand2` opcode. Device-specific services and other Flash Lite versions require further compatibility checks. The two gameplay checks stop in the first playable scene. A person did not operate the physical controller during these checks.

[Retroid screenshots](2026-11-21-flash-lite-retroid.png) · [Import and build instructions](../../engines/flash/README.md) · [Platform issue #64](https://github.com/EriArk/JavaEmulator/issues/64) · [MicroInvaders developer](https://www.panebianco3d.com/en/flashlite-games-microinvaders) · [Balloonatroid developer project](https://github.com/PureMVC/puremvc-as2-demo-flashlite-balloonatroid)
