# Original filename hints for automatic Java setup

Date: **2026-10-15**. Issue: [#14](https://github.com/EriArk/JavaEmulator/issues/14).
Source follows `68ed951f`. This batch updates source and installed debug builds,
not release APKs. GitHub Releases was checked today: `v0.3.0-beta.1`, published
2026-10-14, remains the newest prerelease. Asset hashes were not rechecked.

## Change

The installer previously lost filenames when a content provider copied a JAR
to `tmp.jar` or an archive entry became `archive-game.jar`. Preparation could
not see labels such as `SE_128x160` even though the user had supplied them.

- Preserve the original leaf filename in optional `source-hints.json`, beside
  the converted game. No catalog migration or source/launcher identity change.
- File imports use the leaf name; content imports use provider DISPLAY_NAME,
  never opaque document IDs. Missing/denied metadata is optional.
- ZIP/7z imports use the selected entry's leaf, not the container or folders.
  Choosing a JAR for a JAD records that JAR's name. ID-based recompilation keeps
  the hint. Reusing an existing installation does not rewrite its provenance.
- Library backup/restore preserves sanitized hints. Old backups and J2ME Loader
  libraries without this file remain supported. Invalid, unknown-version and
  oversized hint metadata is ignored; game/save recovery does not depend on it.
- Explicit, recognized `WxH` filename labels outrank artwork, but not declared
  display metadata. Equal contradictory size hints remain unresolved.
- Bounded phone-family tokens are medium-confidence, unverified hints, below
  declared target metadata. `SE` needs a recognized resolution in the filename;
  shared API references alone cannot force that profile. Conflicts remain neutral.
- Parent paths, URL hosts/queries and advertising addresses are not evidence.
  Filename underscore separators are normalized before removing web addresses
  so an adjacent resolution is not swallowed by the advertisement.

This remains static file analysis, not background gameplay benchmarking.
Renamed archive files are not proof of their original device target. Existing,
imported and template settings are not automatically reset. Older installations
without the sidecar are not silently migrated or reconfigured.

## Verification

Built combined Handheld/Phone debug and both test APKs with:

```text
gradlew -PsymbianExperiment=true -PsymbianSourceDir=build/symbian-source-check :app:assembleFdroidDebug :app:assemblePhoneDebug :app:assembleFdroidDebugAndroidTest :app:assemblePhoneDebugAndroidTest
```

Also built ordinary Phone debug/test APKs for the Android 15 x86_64 emulator.
Installed using `adb install -r -d`; no application data was cleared.

Suite: `CompatibilityProfileTest`, `InstallerSourceHintsTest`,
`InstallerSourceTest`, `InstallerJadTest`, `InstallerSourceAliasTest`,
`LibraryImporterTest`.

| Installed target | Result |
| --- | --- |
| Retroid Pocket Classic, Android 14, combined Phone debug | 67 passed, no skips |
| Retroid Pocket Classic, Android 14, combined Handheld debug | 67 passed, no skips |
| Android 15 x86_64 emulator, ordinary Phone debug | 67 passed, no skips |

Checks cover actual installer dialogs, file/content imports, multi-entry ZIP,
7z, JAD selection, ID recompilation, alias reuse, saved settings and backup
round trip. The tests use the repository's MIT Sudoku fixture or synthetic
metadata, not private game files. A new regression initially exposed the
advertising-suffix bug; the final complete runs above include its fix.

Local logs: `build/source-hints-build.log`, `build/source-hints-avd-build.log`,
`build/source-hints-retroid-phone.log`, `build/source-hints-retroid-handheld.log`,
`build/source-hints-avd-phone.log`.

## Original archive sample on Retroid

Four unmodified JARs were freshly installed through normal VIEW import in
`io.github.eriark.abyssme.symbian.phone.debug`. The saved profile JSON and
preparation diagnostics were read back after launching. No existing game was
replaced and no saved setting was manually corrected for these runs.

| Original file | Selected setup | Observed screen |
| --- | --- | --- |
| `Ghost_Recon_2_SE_128x160.jar` | 128x160, Sony Ericsson JP-8, medium | Gameloft logo, then the fully framed sound prompt |
| `AgeOfEmpires2_S40v3.jar` | 240x320 fallback, Nokia S40, medium | Title and main menu |
| `america_s_army_128x160.jar` | 128x160, Generic MIDP, low | Copyright/intro text with OK softkey |
| `TPredator 176x220 (1).jar` | 176x220, Generic MIDP, low | Predator title artwork |

These are first-launch/setup observations, not full-game, audio, controller or
save acceptance. A successful Ghost Recon launch does not resolve its previously
intermittent failure in #15. Tall Samsung/touch builds still need a verified
sample; #14 stays open for that remaining evidence.

Screenshots remain local in `build/source-hints-ghost-ready.png`,
`build/source-hints-age.png`, `build/source-hints-army.png`, and
`build/source-hints-predator.png`. Profile readback is in
`build/source-hints-profile-readback`; diagnostics in
`build/source-hints-retroid-launches.jsonl`. No games, saves or private artifacts
are included in this commit.

Exact SHA-256 identities:

```text
101a60cdd265117e832ce10646c066fb542f086e2afb9895bca6d950d3e7d4af  Ghost_Recon_2_SE_128x160.jar
372b636c8830d907c6b92ef85d61f9e2bc64355803cbc04be3d5302110ccac68  AgeOfEmpires2_S40v3.jar
2d43a3663c70e427f8b62933d61b99d04bd5752d98d10413e310e006dd60fb7e  america_s_army_128x160.jar
5261720212a0dec348acb427310304824d69bab0918bfc7996eaab6270700769  TPredator 176x220 (1).jar
```
