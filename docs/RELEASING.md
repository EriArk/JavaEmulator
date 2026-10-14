# Manual release checklist

GitHub Actions is disabled. Releases are prepared locally and published manually
by a maintainer; this document does not enable any automation.

## Artifact policy

- Preserve the APKs already tracked in `test-builds` as historical snapshots.
- Publish new APKs only as GitHub Release assets, not as Git blobs. Do not use
  `git add -f` to bypass the APK ignore rule or replace an archived binary.
- Commit release notes, checksums and test reports under `docs/releases`.
- Do not rewrite old Git history as part of routine release preparation.
- Keep game fixtures, signing keys, local settings and personal backups out of
  the APK/upload bundle.

## Prepare

Use the replacement M3G/tone build and preserve the component notices described
in [licensing](LICENSING.md). There is no EPL linking exception: the old code
was removed. Build from a clean source checkout or run `:app:clean` when changing
source sets; stale compiler outputs must not survive into a release.

1. Agree on the version and scope. Record the source commit and update the app
   version separately as part of the release work. Do not reuse a published tag
   for a different build.
2. Run the local checks in [CONTRIBUTING](../CONTRIBUTING.md) for both variants.
   Record exactly which checks ran and which device/frontend cases remain
   untested; do not turn an earlier report into a fresh verification claim.
3. Use the maintainer's private signing configuration to build the Handheld and
   Phone release variants. The disposable contributor key is not a release key.
4. Inspect each APK's package ID, version, minimum Android and signing
   certificate. Check upgrade/signature continuity with the previous release.
5. Create release notes identifying the two APKs, changes, limitations and
   test results. Compute SHA-256 after the final build, using the exact asset
   filenames in the checksum list. Keep the APKs in local build output until
   uploading them; no copy into the Git tree is needed.
6. Run `:app:verifyProductionApks`. Prepare the matching app source archive and
   dependency source/notice bundle using `:app:releaseDependencyInventory` and
   the pinned additional native sources. Resolve missing-source entries; do not
   confuse a POM's declared license with a completed source inventory.

## Publish

For 0.3 combined builds, use `-PsymbianRelease=true` for every build, inventory
and APK verification command. Prepare `tools/prepare-release-sources.ps1 -Symbian`
and `tools/prepare-symbian-sources.py`; include both dependency bundles with the
matching app source. Test restoration of the exported engine source rather than
assuming an ignored local checkout is sufficient. See
[asset curation and source restoration](../engines/symbian/DISTRIBUTION.md).
The optional `-PsymbianReleaseCheck=true` rehearsal is debug-signed, uses isolated
IDs and retains test-runner APIs; it is not a substitute for inspecting and
installing the final signed APKs. Original upstream sound banks, ROMs, game
packages and private test inputs must not be shipped.

1. Push the reviewed source/docs and the corresponding version tag, then create
   a draft GitHub Release. Mark beta releases as prereleases.
2. Manually upload the two signed APKs and their checksum list. Never upload a
   keystore, `keystore.properties`, game collection or device backup.
3. Verify asset names and download hashes, review the release notes, then
   publish. Verify that the README download link leads to the intended release.
4. Update the current-beta pointer in `test-builds/README.md` and any versioned
   documentation links without adding or replacing archived APKs.

If a published APK is faulty, document the problem and ship a new version.
Do not silently overwrite a release asset under an existing version.
