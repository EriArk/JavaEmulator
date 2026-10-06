# Launcher integration

## ES-DE

The example files are [es_find_rules.xml](es-de/es_find_rules.xml) and
[es_systems.xml](es-de/es_systems.xml). If you already have custom rules, back
them up and merge the AbyssME entries; do not overwrite your existing files.
Otherwise, copy the examples to `ES-DE/custom_systems`, then restart ES-DE. Select
`AbyssME (Standalone)` as the alternative emulator for the J2ME system.

The intended flow sends a readable content URI to AbyssME, prepares a new game,
and launches prepared games directly on later requests. The calling frontend
must grant read access to the URI.

The component name below was checked against the beta.2 APK manifest. This is
not an end-to-end ES-DE or Beacon certification: cold/warm launch, archive
selection and return-to-frontend checks remain tracked in
[#7](https://github.com/EriArk/JavaEmulator/issues/7).

## Beacon and other launchers

Configure the launcher with package `io.github.eriark.abyssme`, activity
`ru.playsoftware.j2meloader.MainActivity`, action `android.intent.action.VIEW`, and the game as the intent
data URI. Supported sources are JAR, JAD, ZIP and 7Z. Content URIs are preferred.

The touch-oriented build can be addressed as `io.github.eriark.abyssme.phone`
with the same activity, action and URI format. The two packages keep independent
libraries and per-game settings.

Full Android component names:

```text
io.github.eriark.abyssme/ru.playsoftware.j2meloader.MainActivity
io.github.eriark.abyssme.phone/ru.playsoftware.j2meloader.MainActivity
```

Do not shorten the class to `.MainActivity` relative to the installed package:
the Java namespace is still inherited from J2ME Loader. The bundled ES-DE
example targets Handheld and lists JAR/ZIP/7Z; direct JAD import is available in
the app but is not included in that example's extension list.
