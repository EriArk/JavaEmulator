# Atari Portfolio on Retroid Pocket Classic, 2026-11-21

The existing combined ARM64 debug installation was updated in place on Android 14. The final private APK SHA-256 was `7337bfe865e10fac05fe98809bca2f7d9e7c1d458c6405683e26574f921c3aec`. The device imported the supplied Portfolio ROM set and HD61830 font through Android's file picker. All three games used the same installed firmware. See the [engine and package guide](../../engines/portfolio/README.md).

| Supplied game | Observed on Retroid |
| --- | --- |
| Dozen (`12.COM`) | Entering `a=1` changed the first value in column A from 33 to 34 and advanced the move counter to 2. Saving a machine snapshot and reopening after force-stop restored the board. With the CRT preset and the virtual keypad enabled, a remapped Xbox B sent 1 and advanced the counter to 3. Two taps on the virtual 1 advanced it to 4 and 5. An 11-second Android playback recording captured the game's beeps at −16.0 dB mean and −0.1 dB peak. |
| Mike's Cribbage (`CRIB.COM`) | The introductory screen responded to the Xbox Enter mapping. The game dealt six cards and reached the prompt to discard to the crib. |
| Bomber (`BOMBER.EXE`) | Enter and Space advanced the Portfolio-specific executable to round one. The aircraft visibly moved between captures. The captured 14-second gameplay interval was silent. |

A separate DOS program created a file on the emulated card and another on the internal RAM disk. After an Android force-stop and restart, the program reopened both through DOS file calls without loading a manual snapshot.

Twelve on-device instrumentation tests passed, including the populated library backup/restore into an isolated directory. All 61 pre-existing managed card paths survived. The device retained 64 cards after adding the three supplied games and removing the temporary disk-test program. All four pre-existing webOS storage files retained their SHA-256 hashes.

Controller events were injected through the Retroid's Xbox input device and touch events through Android. These checks cover specific game interactions. Hand-operated controls, complete playthroughs and audible output in Cribbage remain unverified. The current card builder mounts files alongside the chosen executable, with a 256 KiB capacity. Local screenshots, lossless audio, headless checks, build and instrumentation logs are in `build/checks/portfolio/`. The firmware, games and private APK are excluded from the repository.
