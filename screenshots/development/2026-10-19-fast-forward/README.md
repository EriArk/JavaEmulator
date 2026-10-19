# Fast-forward development captures

Captured on Retroid Pocket Classic, Android 14, 2026-10-19. Real device
screenshots of the isolated combined debug app, not concept art or final signed
release captures. Candidate initially used the Beta 4 name; distribution was
changed to Beta 3 revision 1 at the maintainer's request.

- `retroid-settings.png`: per-game target speed, controller binding/mode and
  shortcut visibility, over Snakes.
- `retroid-bounce-tales.png`: active shortcut and temporary-settings confirmation
  over the Java game's menu.
- `retroid-deep-abyss.png`: Mophun active shortcut.
- `retroid-snakes.png`: Symbian/N-Gage active shortcut during startup.

Source: explicit fixture flows in `SymbianSessionUiTest`, log
`build/beta4-retroid-live-final.log`. All six selected cases passed after the
menu-dismissal lifecycle fix. These captures establish UI state, not full-game
compatibility, frame-rate benchmarks or listening-quality acceptance. Games and
phone system files are not included in the repository or release sources.
