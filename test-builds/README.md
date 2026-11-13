# Build archive

For normal installation, use [GitHub Releases](https://github.com/EriArk/JavaEmulator/releases),
not one of the old experimental APKs in this directory.

## Current published beta

[AbyssME 0.7.0 Beta 5](https://github.com/EriArk/JavaEmulator/releases/tag/v0.7.0-beta.5)
provides two adaptive APKs for phones and handhelds, both Android 10+ / code 319:

- `AbyssME-0.7.0-beta.5.apk`: ARM64, Java/Mophun and experimental Symbian/N-Gage.
- `AbyssME-0.7.0-beta.5-armv7.apk`: 32-bit ARM Android, Java/Mophun only.
- Both retain the current shared UI and Beta 4 navigation fix. About identifies
  the distribution. Choose ARM64 when supported; older low-memory ARM32 device
  coverage remains limited.
- Games and firmware are not bundled. These alternate APKs update the same
  official app; do not uninstall to switch. Existing saves remain stored, while
  Symbian entries are unavailable in ARMv7. Keep a backup before switching.
- Use the in-app migration workflow for the separate old Phone app. Keep that
  app installed until saves are checked. Older release files remain historical.

Use the [SHA-256 list](../docs/releases/v0.7.0-beta.5/SHA256SUMS.txt) to verify downloads.
See the [release notes](../docs/releases/v0.7.0-beta.5.md) and
[current limitations](../README.md#known-limitations).

## Historical files

The 0.1.0 betas and `J2ME_Loader-*-debug.apk` files are historical snapshots, not the
recommended current downloads. Their names reflect earlier development stages;
they may use different package IDs or signing keys. Do not assume they can
update a current installation or preserve its data.

Reports and screenshots describe the particular build and test environment
named in each report; they do not certify later releases. Existing APKs remain
as an archive; they are not removed or rewritten. Git history is preserved.

## Publication policy

As approved in [#6](https://github.com/EriArk/JavaEmulator/issues/6), new APKs
are published only as manually uploaded GitHub Release assets. Do not add new
APKs to Git or replace an archived APK in this directory. The `.gitignore`
rule excludes new APKs, while previously tracked files remain tracked.

Text release notes, checksums and test reports may still be committed. Keep
new release documentation under `docs/releases`, with download links pointing
to Releases. Follow the [manual release checklist](../docs/RELEASING.md).
No APKs are built or published automatically by GitHub Actions.
