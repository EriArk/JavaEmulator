# Platform expansion issue index

GitHub snapshot reviewed on 2026-11-21. There are 51 open platform research or development issues in the ranges below. Some cover several distinct devices. The [maintainer roadmap (#44)](https://github.com/EriArk/JavaEmulator/issues/44) defines the scope. Follow the [integration queue](#integration-queue-easiest-first) for the order of upcoming work. [Engine host and import architecture (#83)](https://github.com/EriArk/JavaEmulator/issues/83) tracks shared work. Public availability and device results are tracked in [project status](PROJECT_STATUS.md).

Five distinct native games can justify keeping a platform. An unverified catalog stays under investigation. The roadmap ranks early implementations by existing reusable cores, Android ARM64 feasibility and available game fixtures. The first engine should demonstrate gameplay before the launcher gains another platform tab.

The [device sticker catalog](../design-concepts/platform-stickers/README.md) maps reference hardware to these issues and includes a contact sheet.

## Checking a platform with a real game

For each new platform, add a transparent sticker of the corresponding hardware to the grid and the sticker catalog. Check its highlight, enlargement and library navigation with controller focus.

At the start of each integration, inventory the original device controls against its manual, hardware photograph and the core's input table. Match the printed letters or symbols and face-button positions on the touch surface. Expose the same guest actions in the physical controller mapper, including shoulders, system buttons, soft keys and keyboard modifiers when the device has them. Keep a keypad on keypad phones and check the stylus or keyboard path on devices that use one. Exercise a held direction plus an action, release or cancel, and a remap after relaunch. For systems whose controls vary by cartridge or model, record which layout the game used.

Record the game edition, host and scene reached. Check the picture during gameplay and capture or listen to the game audio. Exercise the virtual controls and a connected physical controller separately. Record whether inputs were pressed by hand or injected. Use the existing AbyssME graphics, touch and physical-controller profiles, change a mapping and a display setting in the shared menu, and check their effect after relaunch. Track any missing capability alongside the result for that game.

Check the user journey from the platform grid and document picker through game installation and first launch. For systems with firmware or companion files, check detection, selection among alternatives and reuse on the next launch. Verify guest saves or states across a cold start, package reimport, in-place app updates and the available backup/restore path. Keep compatibility notes about individual games separate from the formats accepted by the importer. The [nine-platform audit](checks/2026-11-20-platform-lifecycle.md) records open work for the current build.

## Integration queue, easiest first

**Next platform: Hartung Game Master, step 13.** The device check requires the internal MCU ROM and a cartridge. MicroCity and Galaxy Fighter ran in the [integrated Pokitto host](checks/2026-11-22-pokitto.md). Bomber and CHIP-8 BRIX ran in the [Gamebuino Classic host on Retroid](checks/2026-11-22-gamebuino.md). ArduGolf and Dazzle Dash ran through the [integrated Arduboy / FX host on Retroid](checks/2026-11-21-arduboy.md). Hartung and Epoch remain adjacent because they share NEC CPU work. The [investigation of twelve maker ecosystems](checks/2026-11-21-maker-handhelds.md) records formats, engines, licensing, fixtures and limits. Castle Smasher reached The Wall in the [private iPhone OS build](checks/2026-11-21-iphone.md). Carmageddon beta reached Main Street with captured audio on the [private Gizmondo build](checks/2026-11-21-gizmondo.md). Age of Empires reached a playable map on the [private Pocket PC build](checks/2026-11-21-pocketpc.md). Three Atari Portfolio games ran on the [private Retroid build](checks/2026-11-21-portfolio.md). Firefox OS and KaiOS packaged games reached gameplay in the [Retroid build](checks/2026-11-21-b2g.md). Mojo and Enyo games reached gameplay on the [private Retroid webOS build](checks/2026-11-21-webos.md). Step 1 reached gameplay in the [private Retroid MRE build](checks/2026-11-21-mre.md). This is the working order as of 2026-11-22. Difficulty estimates cover the path from import to a playable game on Android ARM64, including firmware selection where needed, picture and sound, touch and physical controls, shared settings and cold restore. The numbered steps assume earlier work can be reused. Within a tier, shared dependencies break ties. If a required file or upstream decision holds a step, record what is missing and take the next feasible step.

Already represented in the development build: WIPI/SKVM (#57), Dingoo/Gemei (#89), P/ECE (#97), GP32 (#87), the four handheld families in #94, Cybiko (#86), Flash Lite (#64), Asha/S40 (#73) and Palm OS (#65). Remaining model and game compatibility work stays in their issues. The [device reports](PROJECT_STATUS.md) describe the builds and tests for each. Shared host work (#83) follows the next engine that needs it. WonderSwan (#96) stays in scope review.

### 1. Shortest paths to a playable game

| Order | Platform | What determines the effort |
| ---: | --- | --- |
| 1 | [MediaTek MRE (#58)](https://github.com/EriArk/JavaEmulator/issues/58) | Private ARM64 checkpoint reached. Crossfire and Flappy Bird ran on Retroid. Public distribution terms remain open. |
| 2 | [Mythroad / Sky-Mobi MRP (#59)](https://github.com/EriArk/JavaEmulator/issues/59) | Deferred by the maintainer until a game and matching `cfunction.ext` are available. The MRE host and ARM backend are reusable. |
| 3 | [In-Fusio ExEn (#60)](https://github.com/EriArk/JavaEmulator/issues/60) | Awaiting an `.exn` game and the ExEn 2 runtime file `exen_builtins.bin` for the Handyplay VM. |
| 4 | [Palm webOS · Mojo / Enyo (#69)](https://github.com/EriArk/JavaEmulator/issues/69) | Private device checkpoint reached with 2048, Far Out Fowl, Wordsel and Tetris. [Web host coverage and pending work](checks/2026-11-21-webos.md). |
| 5 | [Firefox OS / KaiOS · packaged web games (#72)](https://github.com/EriArk/JavaEmulator/issues/72) | Firefox OS, KaiOS legacy and a modern KaiOS package reached gameplay in the private Retroid build. A game specifically targeting KaiOS 4 remains unverified. |
| 6 | [Atari Portfolio (#101)](https://github.com/EriArk/JavaEmulator/issues/101) | Bomber, Dozen and Cribbage ran under DIP-DOS on Retroid. The private build imports firmware, runs programs from a writable card and supports machine snapshots. |

### 2. Existing engines that need a platform adapter

| Order | Platform | What determines the effort |
| ---: | --- | --- |
| 7 | [Pocket PC 2003 / Windows Mobile (#66)](https://github.com/EriArk/JavaEmulator/issues/66) | Age of Empires reached a random map with captured audio, stylus and controller input, and an in-game save restored after restart. Games specific to 2003 SE and later editions need separate checks. |
| 8 | [Gizmondo (#84)](https://github.com/EriArk/JavaEmulator/issues/84) | The dedicated SD card host ran Carmageddon beta on Retroid with 3D graphics, controller input and captured effects. Other titles and hardware services need separate checks. |
| 9 | [Early iPhone OS (#67)](https://github.com/EriArk/JavaEmulator/issues/67) | The embedded touchHLE host ran Castle Smasher 2.01 on Retroid with picture, sound, direct touch and a controller cursor. The library backs up IPA packages and guest sandboxes. |
| 10 | [Arduboy / FX (#113)](https://github.com/EriArk/JavaEmulator/issues/113) | The private Retroid build runs ArduGolf HEX and Dazzle Dash FX through the shared library. Device checks covered game input, mixed audio and a cold save-state restore. |
| 11 | [Gamebuino Classic (#113)](https://github.com/EriArk/JavaEmulator/issues/113) | Bomber ran in the private Android build with touch, controller input and recorded audio. The CHIP-8 player loaded BRIX from SD, and a guest EEPROM write survived a cold launch. |
| 12 | [Pokitto (#113)](https://github.com/EriArk/JavaEmulator/issues/113) | The private Retroid build runs MicroCity BIN and Galaxy Fighter POP with SD companions. The device check covered city save/load, combat, audio, controller remapping and the focused platform sticker. |
| 13 | [Hartung Game Master (#110)](https://github.com/EriArk/JavaEmulator/issues/110) | Adapt MAME’s NEC CPU and LCD/speaker path to the cartridge host. Test a cartridge with the internal MCU ROM. |
| 14 | [Epoch Game Pocket Computer (#100)](https://github.com/EriArk/JavaEmulator/issues/100) | Reuse NEC CPU integration work where it applies. This machine needs its own BIOS, cartridge map, LCD and input wiring. |
| 15 | [Original Thumby (#113)](https://github.com/EriArk/JavaEmulator/issues/113) | The official emulator and game catalog supply a board and MicroPython fixtures. Adapt the guest filesystem, buzzer and two-button input. |
| 16 | [Thumby Color (#113)](https://github.com/EriArk/JavaEmulator/issues/113) | Reuse game-directory handling. Port the separate Tiny Game Engine host from Linux/SDL or WASM and verify its audio, assets and saves. |
| 17 | [ESPboy Little Game Engine (#113)](https://github.com/EriArk/JavaEmulator/issues/113) | Embed the BSD-licensed browser VM for device LGE games. Test the first native package and its audio and controls. |
| 18 | [J-SKY / Vodafone V-Appli / SoftBank S! Appli (#62)](https://github.com/EriArk/JavaEmulator/issues/62) | Port the required ReMEXA carrier APIs to the existing Android Java host. Check a matching game edition per carrier. |
| 19 | [DoJa 5.1 / i-mode (#61)](https://github.com/EriArk/JavaEmulator/issues/61) | Use openDoJa as a starting point. Replace desktop graphics, input and sound bindings and preserve JAM/JAR scratchpad data. |
| 20 | [Tizen Mobile · web games (#76)](https://github.com/EriArk/JavaEmulator/issues/76) | Extend the packaged web host for `.wgt` manifests, Tizen APIs and phone input. |
| 21 | [MakeCode Arcade (#113)](https://github.com/EriArk/JavaEmulator/issues/113) | Build an offline simulator path for stock MakeCode projects and shared board controls. Check extension dependencies before supporting additional projects. |
| 22 | [Milton Bradley Microvision (#109)](https://github.com/EriArk/JavaEmulator/issues/109) | Adapt MAME’s cartridge MCU and LCD path. Each game needs the correct keypad overlay and controller type. |

### 3. Substantial device or runtime work

| Order | Platform | What determines the effort |
| ---: | --- | --- |
| 23 | [Brick Game (#99)](https://github.com/EriArk/JavaEmulator/issues/99) | Pick one dumped hardware revision. Match its MCU, segment layout, buttons and buzzer before expanding to other revisions. |
| 24 | [Tamagotchi P1 / P2 (#98)](https://github.com/EriArk/JavaEmulator/issues/98) | Resolve core provenance, import the device ROM and implement segmented LCD, buzzer and elapsed time across app restarts. |
| 25 | [Game & Watch / Tiger LCD / Tiger R-Zone (#99)](https://github.com/EriArk/JavaEmulator/issues/99) | Reuse the MCU path for matching machines. Each title needs its segment artwork and controls. R-Zone also needs its distinct display layout. |
| 26 | [Tiger Game.com / Pocket Pro (#93)](https://github.com/EriArk/JavaEmulator/issues/93) | Assess the MAME driver with a cartridge. Integrate firmware, touchscreen, two cartridge slots where applicable and audio. |
| 27 | [Playdate (#111)](https://github.com/EriArk/JavaEmulator/issues/111) | CrankLab has an Android build and Cranked is an alternative. Establish a reusable core, then check crank, Lua games and native C games separately. |
| 28 | [PewPew / Lite (#113)](https://github.com/EriArk/JavaEmulator/issues/113) | Reuse Python hosting for the 8 × 8 display and keys. Independently test a native game with the timing and palette of a specific board. |
| 29 | [PicoPad / picogame (#113)](https://github.com/EriArk/JavaEmulator/issues/113) | Reuse Python hosting for the picogame API. PicoRacer ran in the headless simulator, which still needs an Android audio path. |
| 30 | [Psion Series 3 / Series 5 / Revo (#102)](https://github.com/EriArk/JavaEmulator/issues/102) | Resolve core reuse terms and ROMs. Verify installation and keyboard/stylus input separately for SIBO and EPOC32 games. |
| 31 | [Apple Newton (#104)](https://github.com/EriArk/JavaEmulator/issues/104) | Einstein provides a device model. Verify source terms, ARM64 embedding, ROM import and pen input with a native package. |
| 32 | [Tapwave Zodiac (#85)](https://github.com/EriArk/JavaEmulator/issues/85) | Reuse Palm library and session handling. Zodiac games add model-specific graphics, sound and analog input beyond the Tungsten E2. |
| 33 | [Star i-appli (#61)](https://github.com/EriArk/JavaEmulator/issues/61) | Build on DoJa input and storage. Establish coverage of the experimental Star APIs with a Star-specific title. |
| 34 | [KDDI EZplus / EZ-appli Java (#63)](https://github.com/EriArk/JavaEmulator/issues/63) | Reuse Japanese Java integration. Identify a carrier-specific game and implement its KJX packaging and required APIs. |
| 35 | [VTech V.Smile Pocket (#95)](https://github.com/EriArk/JavaEmulator/issues/95) | Evaluate a cartridge with the MAME machine and verify portable controls, video and audio. |
| 36 | [LeapFrog Leapster / Leapster 2 (#95)](https://github.com/EriArk/JavaEmulator/issues/95) | Separate machines from V.Smile. The preliminary driver adds CPU/peripheral and sound work as well as stylus input. |

### 4. Major compatibility gaps or unresolved reuse terms

| Order | Platform | What determines the effort |
| ---: | --- | --- |
| 37 | [Qualcomm BREW phones (#56)](https://github.com/EriArk/JavaEmulator/issues/56) | Resume the existing Melange prototype when reuse permission or replacement code and Qualcomm library substitutes make a device run practical. |
| 38 | [Zeebo (#68)](https://github.com/EriArk/JavaEmulator/issues/68) | Assess Zeebx with a console title. Reuse applicable BREW work and verify Zeebo-specific 3D, controls and storage. |
| 39 | [GP2X / Wiz / Caanoo (#88)](https://github.com/EriArk/JavaEmulator/issues/88) | Resolve Ginge reuse or an alternative. Reproduce Linux ARM libraries and device mappings for native games on each handheld. |
| 40 | [GVM / GNEX (#74)](https://github.com/EriArk/JavaEmulator/issues/74) | Identify the VM and imports for each Korean runtime. Use WIPI host services where the behavior matches. |
| 41 | [BlackBerry OS 4–7 (#70)](https://github.com/EriArk/JavaEmulator/issues/70) | Handle COD loading and the BlackBerry Java APIs used by a game. The available conversion tool alone cannot execute it. |
| 42 | [Motorola P2K / ElfPack (#105)](https://github.com/EriArk/JavaEmulator/issues/105) | Native game examples exist. Reproduce the handset-specific ELF loader, imports and graphics/input calls. |
| 43 | [Siemens / BenQ-Siemens / LG PMB887x (#81)](https://github.com/EriArk/JavaEmulator/issues/81) | Identify a bootable board/flash pair, resolve QEMU component terms and bring peripherals far enough to run a firmware game. |
| 44 | [Nokia 9000 / 9110 Communicator (#103)](https://github.com/EriArk/JavaEmulator/issues/103) | FreeGEOS is a base. Reproduce Nokia-specific drivers, keyboard and display for a native GEOS game. |
| 45 | [Maemo 5 / MeeGo Harmattan (#78)](https://github.com/EriArk/JavaEmulator/issues/78) | Match ARM libraries, graphics and packaging of an N900 and an N9 game independently. |
| 46 | [Sailfish OS (#79)](https://github.com/EriArk/JavaEmulator/issues/79) | Reuse applicable Linux integration. Reproduce the Qt/Silica and OS services a native Jolla game uses. |

### 5. Research first; the implementation path depends on a game and its ABI

| Order | Platform | What determines the effort |
| ---: | --- | --- |
| 47 | [Palm webOS · native PDK (#69)](https://github.com/EriArk/JavaEmulator/issues/69) | Identify a native ARM game and reproduce its graphics, audio and binary interfaces. |
| 48 | [Tizen Mobile · native games (#76)](https://github.com/EriArk/JavaEmulator/issues/76) | Trace an ARM `.tpk` and its native platform dependencies. |
| 49 | [BlackBerry 10 / QNX (#71)](https://github.com/EriArk/JavaEmulator/issues/71) | Classify native BAR games and Android-runtime packages separately. A native title needs QNX services. |
| 50 | [Windows Phone 7 / 8 (#77)](https://github.com/EriArk/JavaEmulator/issues/77) | Identify a managed Silverlight/XNA title and a native title. Each needs its own graphics and runtime path. |
| 51 | [Samsung bada (#75)](https://github.com/EriArk/JavaEmulator/issues/75) | Trace a native Wave game and its device APIs. Establish a usable Android host. |
| 52 | [Danger Hiptop / Sidekick (#80)](https://github.com/EriArk/JavaEmulator/issues/80) | Find a runnable archived game and establish the device APIs and a reusable runtime. |
| 53 | [WGE and other unidentified phone formats (#82)](https://github.com/EriArk/JavaEmulator/issues/82) | Identify the executable format and a distinct game before estimating an engine. |

For the first three steps, [Handyplay’s core and runtime inventory](https://github.com/mohamedahmed166e/my-mre-emulator#readme) lists the game formats, external files and ARM backend. A working upstream core still needs a Retroid gameplay check inside AbyssME. Use the [platform lifecycle checks](#checking-a-platform-with-a-real-game) for each entry.

### Maker handhelds awaiting an engine decision

Pimoroni PicoSystem has native UF2 games. DERP's reuse terms and the board support in pico-emu need investigation. Gamebuino META has a partial MIT SAMD21 implementation and Tiny Arcade has a downloadable catalog but still needs a TinyScreen+ host. These three stay in research while the nine candidates above enter the numbered queue. [PicoSystem (#112)](https://github.com/EriArk/JavaEmulator/issues/112) · [DIY ecosystems (#113)](https://github.com/EriArk/JavaEmulator/issues/113) · [Evidence and next test for each](checks/2026-11-21-maker-handhelds.md).

## First integration candidates in the order recorded in #44

[WIPI / SKVM (#57)](https://github.com/EriArk/JavaEmulator/issues/57) · [Dingoo A320 / Gemei A330 (#89)](https://github.com/EriArk/JavaEmulator/issues/89) · [Aquaplus P/ECE (#97)](https://github.com/EriArk/JavaEmulator/issues/97) · [GamePark GP32 (#87)](https://github.com/EriArk/JavaEmulator/issues/87) · [Watara Supervision and Mega Duck first, Gamate and GameKing separately (#94)](https://github.com/EriArk/JavaEmulator/issues/94) · [Cybiko Classic / Xtreme (#86)](https://github.com/EriArk/JavaEmulator/issues/86) · [Flash Lite (#64)](https://github.com/EriArk/JavaEmulator/issues/64) · [Nokia Asha / S40 extensions to Java ME (#73)](https://github.com/EriArk/JavaEmulator/issues/73)

## Active experimental branch

[Qualcomm BREW / Melange (#56)](https://github.com/EriArk/JavaEmulator/issues/56)

The [BREW research branch](https://github.com/EriArk/JavaEmulator/tree/research/brew-runtime-migration) contains an experimental host. The issue records pending permission for Melange reuse and work on Qualcomm dependencies. No Android game pass is claimed there.

## Other phone runtimes and operating systems

[MediaTek MRE (#58)](https://github.com/EriArk/JavaEmulator/issues/58) · [Mythroad / MRP (#59)](https://github.com/EriArk/JavaEmulator/issues/59) · [In-Fusio ExEn (#60)](https://github.com/EriArk/JavaEmulator/issues/60) · [DoJa / Star (#61)](https://github.com/EriArk/JavaEmulator/issues/61) · [J-SKY / S! Appli (#62)](https://github.com/EriArk/JavaEmulator/issues/62) · [EZplus (#63)](https://github.com/EriArk/JavaEmulator/issues/63) · [GVM / GNEX (#74)](https://github.com/EriArk/JavaEmulator/issues/74) · [Zeebo (BREW 4.x) (#68)](https://github.com/EriArk/JavaEmulator/issues/68) · [BlackBerry OS 4–7 (#70)](https://github.com/EriArk/JavaEmulator/issues/70) · [BlackBerry 10 (#71)](https://github.com/EriArk/JavaEmulator/issues/71) · [KaiOS / Firefox OS (#72)](https://github.com/EriArk/JavaEmulator/issues/72) · [Samsung bada (#75)](https://github.com/EriArk/JavaEmulator/issues/75) · [Tizen Mobile (#76)](https://github.com/EriArk/JavaEmulator/issues/76) · [Windows Phone 7/8 (#77)](https://github.com/EriArk/JavaEmulator/issues/77) · [Maemo / MeeGo Harmattan (#78)](https://github.com/EriArk/JavaEmulator/issues/78) · [Sailfish OS (#79)](https://github.com/EriArk/JavaEmulator/issues/79) · [Danger OS / Sidekick (#80)](https://github.com/EriArk/JavaEmulator/issues/80) · [Siemens / LG PMB887x (#81)](https://github.com/EriArk/JavaEmulator/issues/81) · [WGE and unidentified feature-phone formats (#82)](https://github.com/EriArk/JavaEmulator/issues/82) · [Motorola P2K / ElfPack (#105)](https://github.com/EriArk/JavaEmulator/issues/105)

## PDAs, communicators and related handhelds

[Palm OS (#65)](https://github.com/EriArk/JavaEmulator/issues/65) · [Tapwave Zodiac (#85)](https://github.com/EriArk/JavaEmulator/issues/85) · [Pocket PC / Windows Mobile (#66)](https://github.com/EriArk/JavaEmulator/issues/66) · [Gizmondo (#84)](https://github.com/EriArk/JavaEmulator/issues/84) · [Early iPhone OS (#67)](https://github.com/EriArk/JavaEmulator/issues/67) · [Palm webOS (#69)](https://github.com/EriArk/JavaEmulator/issues/69) · [Atari Portfolio (#101)](https://github.com/EriArk/JavaEmulator/issues/101) · [Psion SIBO / EPOC (#102)](https://github.com/EriArk/JavaEmulator/issues/102) · [Nokia 9000 / 9110 GEOS (#103)](https://github.com/EriArk/JavaEmulator/issues/103) · [Apple Newton (#104)](https://github.com/EriArk/JavaEmulator/issues/104)

## Other handheld and LCD families

[GP2X / Wiz / Caanoo (#88)](https://github.com/EriArk/JavaEmulator/issues/88) · [Tiger Game.com (#93)](https://github.com/EriArk/JavaEmulator/issues/93) · [Leapster / V.Smile Pocket (#95)](https://github.com/EriArk/JavaEmulator/issues/95) · [Tamagotchi (#98)](https://github.com/EriArk/JavaEmulator/issues/98) · [Brick Game / Game & Watch / Tiger R-Zone and other LCD systems (#99)](https://github.com/EriArk/JavaEmulator/issues/99) · [Epoch Game Pocket Computer (#100)](https://github.com/EriArk/JavaEmulator/issues/100) · [Milton Bradley Microvision (#109)](https://github.com/EriArk/JavaEmulator/issues/109) · [Hartung Game Master (#110)](https://github.com/EriArk/JavaEmulator/issues/110) · [Playdate (#111)](https://github.com/EriArk/JavaEmulator/issues/111)

## Scope review only

[WonderSwan (#96)](https://github.com/EriArk/JavaEmulator/issues/96)

The maintainer excluded Pokémon Mini (#90), PocketStation (#91), Dreamcast VMU (#92), Neo Geo Pocket / Color and Atari Lynx. Earlier suggestions in comments on #83 predate that scope decision.
