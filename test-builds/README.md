# Build archive

For normal installation, use [GitHub Releases](https://github.com/EriArk/JavaEmulator/releases),
not one of the old experimental APKs in this directory.

## Current published beta

[AbyssME 0.1.0 beta.2](https://github.com/EriArk/JavaEmulator/releases/tag/v0.1.0-beta.2)
provides two separately installable variants:

- `AbyssME-0.1.0-beta.2-fdroid-release.apk`: Handheld, landscape and physical controls.
- `AbyssME-0.1.0-beta.2-phone-release.apk`: Phone, touch controls and a portrait keypad layout.

Use the [SHA-256 list](AbyssME-0.1.0-beta.2.sha256) to verify downloads.
See the [release notes](../docs/releases/v0.1.0-beta.2.md) and
[current limitations](../README.md#known-limitations) before reporting a problem.

## Historical files

Beta.1 and `J2ME_Loader-*-debug.apk` files are historical snapshots, not the
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
