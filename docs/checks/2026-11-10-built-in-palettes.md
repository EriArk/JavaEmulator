# Ocean and Amber Styles — 2026-11-10

Local-only #11 implementation. Ocean adds cool blue/teal surfaces; Amber adds
warm cream/brown surfaces. Both provide light and dark variants for the existing
host UI, including cards, focus/selection, menus and touch controls. Classic,
Retro LCD and High contrast remain available. Artwork, guest rendering and
stored game controls are unchanged; imported skins are outside this scope.
Names are available in all eight supported languages. The scrollable picker now
exposes each row as a checked/unchecked radio choice to accessibility services,
independently of gamepad focus.

## Evidence

- Combined Debug and instrumentation APKs assemble locally.
- Eleven targeted Android cases pass across `VisualPolishTest`,
  `ConsoleSurfaceTest`, and `SettingsResourcesTest`. The first run passed 10/11;
  an obsolete test expected English in translated locales. It now checks all
  eight localized labels, unknown-locale English fallback and preserved file
  names. The corrected case and light-mode capture scenario pass on rerun.
- All five palettes pass text contrast >= 4.5:1 against principal surfaces in
  both brightness modes, including on-accent text. New-palette minima in those
  checked pairs: Ocean light 5.72/dark 5.67; Amber light 5.36/dark 6.37.
- Press, focus and selected drawable states are distinct. Main/engine appearance
  propagation, compact toolbar reflow, touch-layout bounds and cancel behavior
  pass. Captures include both new styles in Gallery/List/Grid, the touch editor
  and picker in light/dark; representative captures were visually inspected.
- Lint: zero errors, 804 warnings, unchanged.

The capture fixture accepts `visualCaptures=true`, `captureStyles=ocean,amber`
and `captureBrightness=light` or `dark`. It restores preferences after completion.
Local APKs, logs, source hashes and captures are retained under ignored
`build/checks/2026-11-10-palettes/`. `BUILD-INFO.json` identifies the snapshot.
No new JVM execution is claimed for these UI-only additions.

API 30 emulator evidence does not establish physical controller, TalkBack-user
or real-game acceptance. The offered handheld still awaits ADB authorization.
Nothing from this checkpoint is published as source or a release asset.
