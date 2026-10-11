# Project Handoff and Status Documentation

For website copy, announcements, release summaries or "what is done" questions,
read `docs/PROJECT_STATUS.md` first, then `CHANGELOG.md`. Follow their evidence
links rather than relying on chat history or commit subjects alone.

After a meaningful implementation, verified result, test-build distribution or
release, update the relevant status rows and dated changelog entry. Include
local-only work as a factual summary with explicit availability and evidence;
do not force-add ignored engine sources, ROMs, credentials or private artifacts.
Do not treat documentation of a private build as authorization to publish it.

Keep public release APKs, unreleased public source, private combined test builds
and local experiments distinct. Check dates and live release state before
calling something latest. State which facts were not rechecked. Never convert
emulator/automated evidence into physical-device or complete-game acceptance.

Use descriptive commit subjects and, for substantial changes, a body explaining
user-visible changes, important limits and checks performed. Do not rewrite
published history merely to expand old commit messages.

Keep app UI and repository documentation in English. Read `CONTRIBUTING.md`
and `docs/LICENSING.md` before implementation, publication or licensing work.
GitHub Actions remains disabled; do not add automatic publishing as a side effect.
