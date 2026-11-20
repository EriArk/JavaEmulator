# Platform expansion issue index

GitHub snapshot reviewed on 2026-11-20. There are 49 open platform research or development issues in the ranges below. Some cover several distinct devices. The [maintainer roadmap (#44)](https://github.com/EriArk/JavaEmulator/issues/44) sets the order, and [engine host and import architecture (#83)](https://github.com/EriArk/JavaEmulator/issues/83) tracks shared work. The links below record scope and priorities. Public availability and device results are tracked in [project status](PROJECT_STATUS.md).

Five distinct native games can justify keeping a platform. An unverified catalog stays under investigation. The roadmap ranks early implementations by existing reusable cores, Android ARM64 feasibility and available game fixtures. The first engine should demonstrate gameplay before the launcher gains another platform tab.

The [device sticker catalog](../design-concepts/platform-stickers/README.md) maps reference hardware to these issues and includes a contact sheet.

## Checking a platform with a real game

Record the game edition, host and scene reached. Check the picture during gameplay and capture or listen to the game audio. Exercise the virtual controls and a connected physical controller separately. Record whether inputs were pressed by hand or injected. Use the existing AbyssME graphics, touch and physical-controller profiles, change a mapping and a display setting in the shared menu, and check their effect after relaunch. Track any missing capability alongside the result for that game.

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
