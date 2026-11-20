# Watara Supervision audio and library checks, 2026-11-20

Private ARM64 debug build on Retroid Pocket Classic, Android 14. Cartridge audio comes from Potator's hardware emulation. Its libretro frontend submits 735 stereo frames at 44,100 Hz for each 60 Hz video frame. Android plays the PCM through the shared AAudio game output.

| Cartridge from the supplied collection | Retroid gameplay | Android playback capture |
| --- | --- | --- |
| Crystball (USA, Europe), raw SV | Selected Beginner, entered the brick-and-paddle level and exercised the action and direction controls. | 11.61 seconds during the level, mean −20.2 dBFS, peak −11.1 dBFS. |
| Challenger Tank (USA, Europe), single-cartridge ZIP | Reached the first scrolling road stage and fired. Saved a checkpoint there. After a cold launch, Load state returned to the road with the touch deck still enabled. | 9.56 seconds during the stage, mean −17.0 dBFS, peak −5.4 dBFS. |

Recordings used Android playback capture with audio duplication. The measurements describe the digital signal. Media volume on the device was initially 0/15 and was set to 3/15 for local play. Retroid input checks used taps on the virtual deck and events injected into the connected Xbox controller's input device. The shared settings preview kept the CRT setting, touch deck and per-game X → Start binding. No hand-operated controller or microphone recording was used.

The supplied ZIP ends partway through Witty Cat. Recovered 62 complete cartridges and verified their uncompressed lengths and CRCs against the ZIP local headers. Each recovered cartridge ran for 720 frames in a separate desktop process using the same pinned core with scripted Start, direction and action inputs. Every run returned 529,200 stereo frames, with changing video and at least 53 audio blocks containing an amplitude range greater than 128 PCM units. This measures output over twelve seconds per title. Two titles received the Retroid gameplay checks above.

As a silent control, ran the author-distributed 32 KiB [Jurl: Supervision Edition](https://tonsomo.itch.io/jurl-supervision-edition) for 3,600 frames with scripted inputs. The picture changed, all 2,646,000 stereo frames were silent, and the guest issued zero writes to the wave, noise or DMA sound registers. The same instrumented core counted 20,578 wave writes and 71 noise writes for Challenger Tank. The Jurl author's [release note](https://tonsomo.itch.io/jurl-supervision-edition/devlog/1063749/jurl-supervision-edition) also identifies sound as missing from this edition.

Opening Crystball as a raw SV and again as a headerless BIN reused the same card. Opened Challenger Tank directly from a ZIP containing a nested cartridge. The device checks covered the platform launcher, tab navigation, mixed archive detection and portable backup restoration of the populated handheld libraries, including Supervision's checkpoint. The initial in-place APK update preserved 8,898 checked files, 60 primary database rows and seven source aliases. Supervision has a separate card store. All seven focused Android checks passed on the final build. The [platform screenshot](2026-11-20-supervision-grid.png) shows the tenth tile.

Private build logs, screenshots, the per-cartridge results table and audio captures are in `build/checks/supervision/`. The ordinary release APKs do not include this optional core.
