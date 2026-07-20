# Launcher integration

## ES-DE

Copy `docs/es-de/es_find_rules.xml` and `docs/es-de/es_systems.xml` to the
`ES-DE/custom_systems` directory on the handheld, then restart ES-DE. Select
`AbyssME (Standalone)` as the alternative emulator for the J2ME system.

The command sends a read-only content URI through `ACTION_VIEW`. AbyssME indexes,
prepares and starts a new game on the first request. Later requests from the same
source launch the prepared game directly. Exiting the game returns to ES-DE.

## Beacon and other launchers

Configure the launcher with package `io.github.eriark.abyssme`, activity
`.MainActivity`, action `android.intent.action.VIEW`, and the game as the intent
data URI. Supported sources are JAR, JAD, ZIP and 7Z. Content URIs are preferred.

The touch-oriented build can be addressed as `io.github.eriark.abyssme.phone`
with the same activity, action and URI format. The two packages keep independent
libraries and per-game settings.
