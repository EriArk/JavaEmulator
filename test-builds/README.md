# Build archive

For normal installation, use [GitHub Releases](https://github.com/EriArk/JavaEmulator/releases),
not one of the old experimental APKs in this directory.

## Current published beta

[AbyssME 0.4.0 Beta 3 Revision 1](https://github.com/EriArk/JavaEmulator/releases/tag/v0.4.0-beta.3)
provides one adaptive arm64 APK for phones and handhelds, with Java, Mophun and
experimental Symbian / N-Gage. Games and firmware are not bundled:

- `AbyssME-0.4.0-beta.3-r1.apk`: controller and touch use, portrait and landscape,
  plus configurable fast-forward. Code 309 updates the original Beta 3 in place.
- Use the revision assets; original Beta 3 files remain historical downloads.
- Existing Handheld installs update in place; use the in-app migration workflow
  for the separate old Phone app. Keep it installed until saves are checked.

Use the [SHA-256 list](../docs/releases/v0.4.0-beta.3-r1/SHA256SUMS.txt) to verify downloads.
See the [release notes](../docs/releases/v0.4.0-beta.3-r1.md) and
[current limitations](../README.md#known-limitations) before reporting a problem.

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
