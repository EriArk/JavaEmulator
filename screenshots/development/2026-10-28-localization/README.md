# First Language Batch - Development Captures

Date: 2026-10-28. These are actual local combined Debug UI captures, not
release screenshots or artwork mockups. No new signed APK was published.

The Retroid Pocket Classic (Android 14, 1240 x 1080) captures come from
`AppLanguageTest`. It opens the real Settings picker and shared game-setting
panels using an isolated profile named `Example game / 50%`; no guest game is
running behind those panels. They prove the shown UI, not gameplay, network
compatibility or native-speaker acceptance.

| Capture | What it shows |
| --- | --- |
| [Language picker](retroid-language-picker.png) | Native names, Spanish selected; focus and selection are separate |
| [Russian controls](retroid-ru-controls.png) | Shared Controls panel, wrapping and updated menu-button terminology |
| [Portuguese display](retroid-pt-BR-display.png) | Shared Display panel, long labels and picture options |
| [Spanish exit warning](retroid-es-exit.png) | Distinct Resume / Leave without saving actions |

See [implementation, checks and limits](../../../docs/checks/2026-10-28-app-languages.md)
and [localization status](../../../docs/LOCALIZATION.md). UI text outside the
translated app resources, such as the fixture's game name, remains unchanged.
