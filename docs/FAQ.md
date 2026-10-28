# AbyssME — Complete Setup & FAQ

*Java ME • Mophun • Symbian • N-Gage • Android phones & gaming handhelds*

**Guide for the current public release: AbyssME 0.7.0 Beta 1 Revision 1 (Android 10+, ARM64). Updated October 28, 2026.** AbyssME is still in beta. Not every game, phone firmware or network combination will work yet.

**New here?** Start with [What files do I need?](#what-files-do-i-need), then follow the section for your platform. **Having trouble with buttons?** Jump to [Touchscreen controls](#7-touchscreen-controls) or [Physical controllers](#8-physical-controllers). If you're stuck, the [Troubleshooting](#12-troubleshooting) section tells you what to check.

## 1. Getting started

### What is AbyssME?

AbyssME is an Android app for playing classic mobile games. It brings **Java ME, Mophun, Symbian and both generations of N-Gage** together in one library, with touchscreen and physical-controller support.

It is an independent J2ME Loader fork and uses an integrated Symbian/N-Gage engine based on EKA2L1. You do **not** need to install J2ME Loader or EKA2L1 separately to use AbyssME.

### Where can I get AbyssME?

- **Project page:** https://abysstail.art/en/projects/abyssme
- **Official APK and release notes:** https://github.com/EriArk/JavaEmulator/releases
- **Questions, game reports and help:** https://discord.gg/xBN9DXt8Q

Download the latest **signed APK** from an official link. The current public unified build is `AbyssME-0.7.0-beta.1-r1.apk`. Android may ask you to allow installation from your browser or file manager.

**Requirements:** Android 10 or newer and a **64-bit ARM (ARM64)** device. The app adapts to touchscreens and gaming handhelds. A controller is optional.

### Are any games or phone system files included?

**No.** AbyssME includes the emulator engines, not commercial games, Nokia firmware, phone dumps or proprietary N-Gage installers. You supply game files and, where necessary, system files you are entitled to use.

### What files do I need?

| What you want to play | Required game files | Extra system files? |
| --- | --- | --- |
| **Java ME** | Usually a `.jar`; sometimes a matching `.jad` or supported game archive | **No** |
| **Mophun** | A `.mpn` game, directly or in a supported ZIP/7z | **No** |
| **Symbian / S60** | A suitable `.sis` / `.sisx` game or supported game archive | **Yes — a compatible virtual phone system** |
| **Original N-Gage / N-Gage QD** | A compatible original N-Gage game/card archive or supported installer | **Yes — an original N-Gage-compatible phone system** |
| **N-Gage 2.0** | A compatible `.n-gage` game or supported installer | **Yes — a compatible newer S60 phone system AND the separate N-Gage 2.0 platform installer** |

**Important:** A *phone system* is not the same thing as a *game*. For N-Gage 2.0, the *gaming platform installer* is a **third, separate item**. Most unsuccessful first-time setups come from mixing those up.

## 2. Importing Java ME and Mophun games

### How do I add a Java game?

1. Open **AbyssME** and choose the **Java ME** platform tab.
2. Tap the **+** button (or open the library menu and choose **Add game**).
3. Select the game's `.jar` file with Android's file picker. If it came with a matching `.jad`, keep both available and follow any prompts.
4. Let AbyssME prepare the game the first time, then select its card to play.

**You do not need a Nokia BIOS, ROM or RPKG for Java ME.** Those are for Symbian/N-Gage, not ordinary Java games.

If you have a whole collection of loose Java games, use **Add folder** from the library menu to scan the folder instead of picking every JAR individually. A ZIP/7z containing multiple games may offer a game chooser; it is **not** a promise to install every game inside a mixed archive automatically.

### How do I add a Mophun game?

1. Choose the **Mophun** platform tab.
2. Select **+ / Add game** and choose an `.mpn` file, or a ZIP/7z containing a supported Mophun game.
3. Select the imported game's card and start it.

**Mophun does not need Symbian firmware.** Audio, graphics and game compatibility vary. If a game runs without sound or has broken controls, please report its exact title and edition.

### Why do I have two versions of the same game?

Old phone games often had different releases for different manufacturers, screen sizes, languages and platforms. A Java, BREW, DoJa and Symbian edition with the same title are **not interchangeable**. Even two Java JARs may behave differently. When reporting a problem, the exact edition matters.

## 3. Symbian and N-Gage: understand the three pieces

This section matters most if AbyssME displays **“System files needed”** or **“Pending import.”**

### What is a virtual phone system?

Unlike Java ME and Mophun, Symbian games run **inside an emulated phone operating system**. AbyssME therefore needs compatible files from that phone before it can run the game.

Think of them as three different layers:

1. **Phone system / firmware:** the emulated Nokia/Symbian operating system. Required for Symbian and N-Gage games.
2. **N-Gage 2.0 platform:** the N-Gage gaming application/service installed **on a compatible newer phone**. Required for N-Gage 2.0 games, but **not** for original N-Gage or ordinary Java games.
3. **Game:** the actual SIS/SISX, `.n-gage` package, or original N-Gage card files you want to play.

Installing layer 3 without its required earlier layers will not work. AbyssME can keep the game in **Pending import** while you prepare what is missing.

### What is the difference between ROM, RPKG and the Z drive?

- **ROM (`.rom`, often called `SYM.ROM`):** a dump of the phone's read-only system image.
- **RPKG (`.rpkg`):** additional read-only phone-system data packaged from the phone's **Z drive**. It must correspond to the **same phone/firmware** as the ROM.
- **Prepared EKA2L1 system:** an archive with a ROM and a prepared **Z-drive folder**. You may see paths like `data/roms/<phone>/SYM.ROM` and `data/drives/z/<phone>/...`.
- **SIS/SISX (`.sis`, `.sisx`):** an **installer**, usually an app, component or game — **not a phone ROM**.
- **N-Gage game (`.n-gage`):** a game package for the **N-Gage 2.0 platform** — not a phone ROM or the platform installer itself.

**Does every ROM need an RPKG?** No. An original S60 1st Edition system (such as original N-Gage/QD) can sometimes be installed from its ROM alone. Many newer systems require additional matching system data. If AbyssME requests an RPKG, it must match the ROM; choosing an arbitrary one from another phone won't help.

### Which ZIP or 7z archive should I choose?

Choose it by **what is inside**, not merely its filename:

| Example archive contents | What it is | How to use it |
| --- | --- | --- |
| `phone.rom` + matching `phone.rpkg` | A phone system dump | **System files → Add system files** |
| `data/roms/PHONE/SYM.ROM` + `data/drives/z/PHONE/...` | A prepared EKA2L1 system | **System files → Add system files** |
| Just a supported S60 1st Edition `SYM.ROM` | A potentially sufficient older phone ROM | Add it as a system; follow AbyssME's requirements |
| One N-Gage 2.0 platform `.sis` | The N-Gage **platform installer** | Supply when AbyssME asks for the N-Gage 2.0 platform |
| `.sis` / `.sisx` game files | Games/apps | **+ / Add game** |
| `.n-gage` package | An N-Gage 2.0 game | **+ / Add game** (after the required phone/platform setup) |
| Original N-Gage card game archive | Original N-Gage game files | **+ / Add game**; keep the archive/folder structure intact |

AbyssME supports **ZIP/7z system archives**, including ROM/RPKG files inside folders. You normally **do not need to extract them manually**. It also accepts prepared ROM+Z systems, which have a different structure. A raw phone-dump archive should contain **one matching phone system**, not an assortment of unrelated firmware and games.

**Do not mix** several different phones, a phone ROM, an N-Gage platform SIS and multiple games in one improvised ZIP and expect AbyssME to guess their roles. Keep the items separate.

**What about VPL files?** EKA2L1's standalone documentation describes a firmware/VPL installation method. **AbyssME's built-in system importer uses supported ROM/RPKG or prepared ROM+Z inputs**, so do not assume that an arbitrary VPL download will work through **Add system files**.

### Where do I get phone system files?

Use a compatible dump from a device you own or another source you're authorized to use. AbyssME does not include, host or automatically download commercial phone firmware or games.

For background on the **different system-file formats** and preparing your own files, see the [official EKA2L1 device installation guide](https://eka2l1.github.io/quickstart/basic/installdevice/). Its app-specific menus are **not identical to AbyssME**; follow the steps in this FAQ when actually using AbyssME.

## 4. Installing original N-Gage games

### Which system do I need?

For classic N-Gage games, use a compatible **original N-Gage / N-Gage QD (S60 1st Edition)** system. A **QD / RH-29** system is one tested/recommended option inside AbyssME. Phone models are not universally interchangeable, and a QD system is not the same generation as an N95.

An archive named something like **`N-Gage.zip`** may contain a compatible prepared system; another named **`NGageQDv4.60Dev.zip`** may be a QD alternative. Those are **example filenames**, not official requirements — check the contents.

### Step by step

1. From AbyssME's library menu or Settings, open **System files**.
2. Select **Add system files** and choose your compatible N-Gage/QD ROM, ROM+RPKG archive, or prepared system ZIP/7z.
3. Follow the import result. If it is recognized, return to the library.
4. Tap **+ / Add game** and select your original N-Gage game/card archive or supported installer.
5. If AbyssME asks which phone to use, choose the compatible **original N-Gage/QD** system you just installed.
6. Complete any required guest installation prompts; then select the game in your **N-Gage** library tab.

**You do not need the N-Gage 2.0 platform installer for original N-Gage games.** These two generations use different environments.

**Note:** Some original game releases depend on particular card/DRM behavior or additional components; a correct ROM does not guarantee every game will run.

## 5. Installing N-Gage 2.0 games

### What do I need for N-Gage 2.0?

**Three separate things:**

1. A **compatible newer Symbian S60 3rd Edition phone system** (for AbyssME, the **N95 / RM-320** is a useful tested starting point; other compatible systems can vary).
2. The **N-Gage 2.0 platform installer** (usually a `.sis` file, for example a package labelled `v1.40.1557`).
3. The actual **N-Gage 2.0 game**, often a `.n-gage` package.

The platform installer is **not** the phone BIOS. The game is **not** the platform installer. An N95 phone system alone is **not sufficient** to launch an N-Gage 2.0 game.

An archive called **`N95.zip`** might contain the correct ROM/RPKG pair. An archive called **`N-Gage 2.0 v1.40.1557.zip`** might contain the platform **SIS**. These are illustrative filenames; **inspect contents** and select them for the correct purpose.

### Step by step

1. In **System files → Add system files**, import the compatible **N95/RM-320 system** from its ZIP/7z or matching ROM/RPKG files.
2. Return to the library, tap **+**, and choose the N-Gage 2.0 game's supported package/archive.
3. If AbyssME says the **N-Gage 2.0 platform is missing**, provide the **platform `.sis`** (or a supported ZIP containing that installer) **when prompted**. Do not select the `.n-gage` game file as the platform installer.
4. Let AbyssME prepare the virtual phone and launch the guest N-Gage platform. **Follow any installation prompts inside that virtual phone.** Copying/staging the game is not the same as finishing installation.
5. If the app returns to **Pending import**, continue it and confirm the installation result when appropriate.
6. Launch the installed title from the **N-Gage** library tab.

**Example: Tomb Raider: Underworld (N-Gage 2.0 edition).** Use a compatible **N95 system + separate N-Gage 2.0 platform installer + the matching N-Gage 2.0 game package**. Do not start with an original N-Gage/QD ROM for this edition.

### Does the phone have to be an N95?

No. N-Gage 2.0 ran on several compatible newer Nokia phones. However, **N95/RM-320 is a documented, tested preference in AbyssME's current setup flow**, so it is the easiest starting point in this FAQ. Another phone's system may require different setup and can behave differently.

## 6. Installing other Symbian games

### How do I install regular Symbian/S60 games?

1. Determine which **Symbian/S60 generation** the game was made for (for example S60 2nd Edition versus 3rd Edition). The game's installer type and phone requirements matter.
2. Import a **compatible phone system** via **System files → Add system files**.
3. Select **+ / Add game** and choose the game's `.sis`, `.sisx` or supported archive.
4. If prompted, choose the compatible virtual phone and supply any **legitimate required components/dependencies**.
5. Follow any guest installation dialogs. Then open the game from its library card.

Different generations of Symbian are **not automatically interchangeable**. A game made for an older device may not install on a newer one and vice versa.

### Can I install multiple games at once?

**Partly, yes.** Java collections can be discovered with **Add folder**. Symbian/N-Gage also supports selecting multiple compatible native game files and batch review/installing several from supported game archives. This is not a universal importer for every nested, encrypted or mixed-platform archive. Keep phone-system archives separate from game archives.

### Can I import games already installed in a separate EKA2L1 installation?

**Not automatically in the current public release.** AbyssME includes its own Symbian environments. Pointing **Add folder** at another emulator's E:/C: game folders is not a supported universal migration method. Import the original game package into AbyssME, or use AbyssME's own virtual-phone backup/restore tools for moving an existing AbyssME phone installation. Direct EKA2L1-installed-game import is planned/researched, not a current guarantee.

## 7. Touchscreen controls

### Can I play without a physical gamepad?

**Yes.** AbyssME includes both a **Phone Keypad** and a **virtual Gamepad**. You can play many games entirely on the touchscreen. A real Bluetooth/USB controller is optional.

### How do I switch between Phone Keypad and Gamepad?

1. Start the game, open its **Game menu** and select **Controls**. You can also open a game's **Controls** settings from its library actions.
2. Under **Touch layout**, select **Phone** or **Gamepad**. Java games also retain an older **Legacy** option.
3. Under **On-screen buttons**, choose **Auto**, **Always** or **Off** as needed.
4. Use **Try changes / Try in game**, then **Keep** to save. If something feels wrong, choose **Undo** or leave the preview unconfirmed.

**Phone layout** provides the familiar 0–9, `*`, `#`, arrow/OK and soft keys. It can use **Separate** (controls in their own space) or **Overlay** (buttons over the game picture). Separate is a good starting point if buttons cover the game.

**Virtual Gamepad** provides a compact D-pad/actions layout. Its **123/Actions** control switches the displayed action/numeric keys; it is not a button inside the original game.

### How do I move, resize or fade the buttons?

Open **Game menu → Controls → Edit touch layout**.

- **Layout:** select Phone/Gamepad, orientation preview and button size.
- **Position:** select **Directions**, **Actions** or the full keypad block, then drag in the preview or use the move controls. Change the block size if needed.
- **Style:** choose the button theme and opacity. The available opacity range can differ between Separate and Overlay in the current public build.
- Select **Use layout**. When prompted, **Try changes** and **Keep** to make them permanent; **Cancel/Undo** restores the previous layout.

Button positions are saved **per game**, with separate portrait and landscape arrangements. Changing the touch layout **does not change the physical-controller mapping**.

### My game expects number keys instead of arrows. What should I do?

Some old Java games use numeric `2/4/6/8` for movement and `5` for action rather than the standardized arrow/Fire codes.

- Try the **Phone Keypad** so you can press those actual digits.
- With a **physical controller**, use **Controller mapping** and the built-in **Numpad 2/4/6/8** preset.
- For the **virtual gamepad**, open **Controls > Edit touch layout > Buttons**. Select a drawn button, then choose the phone key it sends, or start with an arrow/numpad template. Select **Use layout**, then **Try changes** and **Keep** to save it for that game. Cancel/Undo leaves the previous mapping intact; physical-controller mappings are separate.

The wrong-key symptom can also be caused by a phone-specific keyboard layout or a particular game edition. A new mapping preset might help, but is **not a guaranteed fix**.

## 8. Physical controllers

### Do Bluetooth or USB controllers work?

**Yes.** Pair a Bluetooth controller in **Android's Bluetooth settings**, or connect a supported USB controller, then launch AbyssME. The app supports physical-controller input and per-game mapping. Actual button detection depends on how Android reports the device.

### How do I remap a physical controller?

1. Launch the game or open its actions from the library.
2. Go to **Controls → Controller mapping**.
3. Start with an existing profile such as **Native arrows**, **Numpad 2/4/6/8**, **Numpad diagonals**, or **Action digits**. Use **8-way numpad** if the game needs separate diagonals.
4. Select a **physical source button or D-pad/stick direction** from the controller side. You can use the **Detect** control to help identify an input.
5. Select the **phone key/game action** it should send from the phone keypad side (for example `5`, `*`, **Left Soft**, **Right Soft**, **Fire**).
6. Repeat for other buttons, then **Save** the mapping. Keep any trial if the settings screen asks you to confirm it.

If the current scheme is close to what you want, **duplicate it first**, then change the copy. This lets you preserve a usable original. The physical mapper and virtual touch-button editor are **separate**.

### Which controls should I try first?

| Symptom | Suggested starting point |
| --- | --- |
| Normal Java game expects arrows/OK | **Native arrows** |
| Game says “use 2, 4, 6, 8” | **Numpad 2/4/6/8** |
| Game uses diagonal number keys 1/3/7/9 | **Numpad diagonals** or **8-way numpad** |
| A/B/X/Y work but do the wrong actions | Map each **physical source button** to the game's expected phone key |
| Soft-key menu doesn't respond | Map **Left Soft / Right Soft** to accessible physical buttons |
| Controller is connected but touch controls are absent | Set **On-screen buttons → Always** if you want both inputs shown |

### What if a game still has mixed-up controls after remapping?

Please report **exactly which button you press and what the game does instead**, together with the edition/resolution and whether you used Phone Keypad, virtual Gamepad or a physical controller. That distinction helps identify whether the issue is game-specific, screen/orientation-related or in the input mapping.

## 9. Picture, sound and performance

### How do I fix the game being stretched, tiny, rotated or cropped?

Open **Game menu → Display**. Try **Picture size**, rotation and orientation where supported. **Integer** scaling preserves whole-pixel enlargement for compatible engines. Some Java games also need the correct **phone resolution/profile** in **Advanced**; changing the simulated phone profile may require restarting the game. Keep/Undo lets you safely test supported display adjustments.

### Are LCD and CRT effects available?

AbyssME provides image effects such as **LCD Soft / LCD Classic** for supported game presentation. Java and Mophun also have additional enhancement presets including sharp scaling, FSR, xBRZ and CRT EasyMode when the necessary renderer is available. **Symbian/N-Gage does not support the same Java/Mophun GPU-effect set.** Effects are optional, not fixes for all graphic bugs.

### Why is my game slow, too fast or missing sound?

Compatibility depends on the exact game edition and engine. Some games need a different graphics mode, screen resolution or phone profile. Other problems are genuine emulator bugs. You can inspect **Emulation speed**, **Frame rate limit**, or **Fast-forward** in the game menus, but don't assume 60 FPS or speed changes will repair every title. Audio may change pitch with speed.

If **music is very quiet but sound effects are normal**, report that distinction. It's especially useful for identifying sound-engine problems. Mophun and Symbian audio compatibility is still being improved. Do not assume that increasing the overall Android volume repairs the original issue.

## 10. Game saves, backups and updates

### Does AbyssME automatically save my exact game position?

**Not universally.** Game progress relies on the game's own saving system and what the emulator can preserve. **There is no universal “save state anywhere in every game” guarantee** in the current public beta. Back up your important progress.

### How do I back up Java and Mophun games?

Open **Settings → Library → Library transfer → Back up library**. Save the resulting ZIP somewhere you control. To restore it later, choose **Restore backup** and review what AbyssME will import.

### What about Symbian/N-Gage saves and system files?

Use **Symbian / N-Gage phone backups**. A virtual-phone backup is separate from the normal **Java/Mophun library backup** and can include the virtual phone, its installed games, saves and settings. Don't assume one Java library backup contains every Symbian phone.

### Can I transfer my existing J2ME Loader collection?

Yes: **Settings → Library → Library transfer → Import from J2ME Loader**. You can choose the **whole library** or select an individual game and its saves/settings. Android may ask you to grant access to multiple source folders. Review the import results and verify the transferred saves before deleting the old installation.

### How do I update without losing my games?

Install a newer **officially signed unified AbyssME APK over the existing unified app**. **Do not uninstall first.** Make backups beforehand, especially for important game progress.

The old, separately installed **AbyssME Phone** app has a different package identity. Move its content with **Library transfer → Move from AbyssME Phone**, then confirm your games and saves before removing the old app.

## 11. Online play and social features

### Can I play old Java Bluetooth games over the internet?

**Some supported games, yes — in an early beta form.** Open a compatible Java game's actions and choose **Play Together**, or visit **Social → Lobbies**. Online features use **Fluxer** for account/social/lobby coordination. The first gameplay adapter supports selected **two-player Java Bluetooth games** using a direct encrypted network connection.

Both players need the **matching game edition**, and both must own/provide the game files. Some games and network combinations won't connect; there is **no universal relay fallback** yet. Ordinary single-player use **does not require a Fluxer account**. Mophun and Symbian/N-Gage online play are not included in this public release.

## 12. Troubleshooting

### “System files needed” / “Pending import”

**This usually means AbyssME has accepted your game but cannot run it until the compatible virtual phone and, for N-Gage 2.0, its platform installer are provided.** Your selected game is retained. Add the missing system files, then return to the pending import; **you do not normally need to choose the game again**.

### The ZIP/7z isn't recognized as a phone system

Open the archive with a normal file manager and check **its actual contents**. It must contain a supported ROM (with matching RPKG where required), or a prepared EKA2L1 ROM+Z system. A ZIP containing only `.sis` or `.n-gage` installers is **not** a phone-system archive. Encrypted, damaged, mismatched or mixed-phone archives may be rejected. If you're unsure, send a screenshot of the file list *inside* the archive — not just the archive's filename.

### I have a ROM, but AbyssME asks for RPKG

The selected phone ROM needs additional matching read-only system data. Obtain the **RPKG that belongs to that exact phone/firmware**. Renaming a random file to `.rpkg`, or mixing N95 and 5320 files, will not fix it. Original S60 1st Edition ROMs may work without an RPKG when the importer accepts them.

### It says the N-Gage 2.0 platform is missing

A compatible S60 3rd Edition phone ROM is only the first layer. You still need the separate **N-Gage 2.0 platform installer**, normally a `.sis`. Supply that file **when prompted**, then follow any installer dialogs inside the virtual phone. Do not select your `.n-gage` game as if it were the platform installer.

### I installed an original N-Gage system, but my N-Gage 2.0 game won't install

Those are **different generations**. Original N-Gage/QD uses an older S60 phone environment; N-Gage 2.0 uses a compatible later S60 phone **plus the N-Gage 2.0 platform**. Start with a matching newer system, such as the tested N95/RM-320 setup.

### The game disappeared / only says Pending import

Check **System files** and **Pending import** first. An imported package waiting for dependencies is not necessarily installed into the guest phone yet. Complete the missing setup, then resume the import instead of adding duplicates.

### A Symbian game installs but doesn't boot

Confirm the game is for the same **S60 generation** as the virtual phone, that required components have been provided and that the exact game edition is supported. A successful install is **not proof of full-game compatibility**. Try a fresh launch, note the error, and report it before resetting or deleting anything.

### The game shows a black screen or wrong picture size

For Java, try **Display** and, if necessary, its **Advanced phone resolution/profile** settings. For Symbian, check that the chosen virtual phone is actually compatible with that title. Record whether audio still plays, whether inputs respond and whether the game ever reaches a menu — that helps separate rendering failures from boot failures.

### My D-pad is backwards / buttons do the wrong things

Determine whether you're using **virtual touch controls** or a **physical gamepad**. On a physical controller, try the numeric-key presets and remap source buttons through **Controls → Controller mapping**. On a touchscreen, test the **Phone Keypad** with the game's number keys. Remember that **custom per-button virtual Gamepad mapping is not in public 0.6 R1**, even though work on it exists in development.

### A Bluetooth controller is paired but nothing happens

Check Android recognizes it, then open the game's **Controls** page, start with **Control mode → Auto**, and inspect **Controller mapping**. Try the **Detect** function to see whether AbyssME receives the intended button. Some devices report unusual button or axis codes.

### Can I reset a game's settings without deleting its saves?

Use the relevant **reset/defaults** action in the game's settings rather than deleting the app or its game data. **Read confirmations carefully**: resetting a mapping/display profile is not the same as clearing the game's saved data. Back up saves first if you're unsure.

### Why do I get a problem only in one particular game?

Old mobile titles frequently use different manufacturer APIs, screen sizes, input codes and audio engines. A bug in one title can reveal an underlying compatibility issue shared by others. **Please report small problems too** — they can lead to fixes that benefit many games.

## 13. How to report a problem (please do!)

The fastest way to help is to post in **[AbyssME Discord](https://discord.gg/xBN9DXt8Q)** or open an issue on **[GitHub](https://github.com/EriArk/JavaEmulator/issues)** with:

- **Game:** complete title, platform and **exact edition/version/resolution** if known. **Game actions → Game information** can show Java's version and vendor.
- **Device:** Android version, model and whether it's a phone or gaming handheld.
- **AbyssME version:** from the app or downloaded APK name.
- **What happens:** what you expected, what actually happened, and when (launch, menu, sound, controls, saves, etc.).
- **Controls:** Phone Keypad, virtual Gamepad or physical controller, and the relevant preset/mapping.
- **For Symbian/N-Gage:** virtual phone model/system you imported, whether a platform installer was required, the archive **contents/filenames**, and the full error message. **Do not upload proprietary firmware or commercial game files publicly.**
- **Optional:** screenshot, short video or exported local diagnostics. Remove personal information, private tokens and account details before sharing.

Even if the problem seems tiny, **please tell us**. Fixing one specific game often uncovers an issue that affects many others. Real user reports are essential to improving AbyssME. ❤️

## 14. Feature availability and useful links

**This FAQ describes the public 0.7.0 Beta 1 build.** It includes per-game virtual Gamepad remapping, movable/hideable phone menu handles, opacity settings and **Settings > Language**. Choose System default, English, Spanish, Brazilian Portuguese, Russian, French, German, Japanese or Simplified Chinese. This changes emulator menus, not the game's language. Some low-level diagnostics may still appear in English. Java audio and N-Gage 2.0 installer fixes are included; see the [release notes and limits](releases/v0.7.0-beta.1.md).

- **AbyssME website:** https://abysstail.art/en/projects/abyssme
- **APK, release notes and matching source:** https://github.com/EriArk/JavaEmulator/releases
- **Discord:** https://discord.gg/xBN9DXt8Q
- **GitHub issues:** https://github.com/EriArk/JavaEmulator/issues
- **EKA2L1 phone-system background:** https://eka2l1.github.io/quickstart/basic/installdevice/

*AbyssME is an independent project. Game names and platform trademarks belong to their respective owners. No games, firmware or proprietary N-Gage installers are distributed with the emulator.*
