# AbyssME — Platform History Cards

**An Interactive Archive of Pocket Gaming History.**

Short, readable historical portraits for AbyssME's platform gallery. The style is deliberately restrained: **one device image, one headline, one compact paragraph, and a few facts** — closer to a *Gran Turismo 7* vehicle description than an encyclopedia page.

**Editorial snapshot: 2026-11-22.** The initial collection covers **32 existing development platform shelves**, with **8 complete locale versions per card (256 articles)**. These are content drafts stored in the repository. **No history-card UI or long-press behavior has been added to the Android app by these files.** Interaction and release acceptance remain under [issue #114](https://github.com/EriArk/JavaEmulator/issues/114).

## The collection

| File | Covered platforms | Cards |
| --- | --- | ---: |
| [01-public.json](01-public.json) | Java ME, Mophun, Symbian/S60, N-Gage | 4 |
| [02-phone-runtimes.json](02-phone-runtimes.json) | WIPI/SKVM, MRE, Flash Lite, webOS, Firefox OS, KaiOS | 6 |
| [03-pocket-computers.json](03-pocket-computers.json) | Palm OS, Pocket PC, early iPhone OS, Atari Portfolio | 4 |
| [04-handhelds-a.json](04-handhelds-a.json) | Dingoo A320, Gemei A330, P/ECE, GP32, Supervision, Mega Duck | 6 |
| [05-handhelds-b.json](05-handhelds-b.json) | Cybiko, Gamate, GameKing, Gizmondo, Hartung, Epoch | 6 |
| [06-maker-handhelds.json](06-maker-handhelds.json) | Arduboy, Gamebuino, Pokitto, Thumby, Thumby Color, ESPboy | 6 |
| [localization.json](localization.json) | UI labels, country names, translated specifications and date-period labels | — |

The remaining planned platforms can use **the same format** once their exact representative devices and source materials are settled. Nokia Asha/Series 40 is intentionally represented by the existing Java ME shelf rather than counted as an extra platform.

## Languages

The set matches `app/src/main/java/ru/playsoftware/j2meloader/util/AppLanguage.java` and `app/src/main/res/xml/app_locales.xml`:

| Tag | Language |
| --- | --- |
| `en` | English |
| `es` | Español |
| `pt-BR` | Português (Brasil) |
| `ru` | Русский |
| `fr` | Français |
| `de` | Deutsch |
| `ja` | 日本語 |
| `zh-CN` | 简体中文 |

Every card stores `copy[locale].headline` and `copy[locale].article`, with no English placeholders in the other locales. Generic UI words and **descriptive technical values** are translated separately in `localization.json`. Model names, CPU identifiers, file extensions, numerical dimensions and other genuine technical names remain unchanged.

## Format and example

Each group file has `schemaVersion`, `editorialStatus`, `snapshot`, `group`, `locales` and `cards`. A record looks like this (abridged):

```json
{
  "id": "n-gage",
  "device": "Nokia N-Gage",
  "year": "2003",
  "maker": "Nokia",
  "country": "FI",
  "image": "app/src/main/res/drawable-nodpi/sticker_ngage.webp",
  "imageAttribution": "design-concepts/platform-stickers/README.md",
  "sources": ["https://wakkatobakka.github.io/ngage-archive/history/index.html"],
  "specs": {
    "display": "176 × 208 px",
    "media": "MMC",
    "connectivity": "Bluetooth"
  },
  "copy": {
    "en": {
      "headline": "The phone that wanted to be a console",
      "article": "…"
    },
    "ru": {
      "headline": "Телефон, мечтавший стать приставкой",
      "article": "…"
    }
  }
}
```

**All media paths are repository-root-relative.** They point to images already in the AbyssME sticker collections; this content addition does not duplicate or alter artwork. Consult [sticker catalog credits](../../design-concepts/platform-stickers/README.md) and source records before packaging or redistributing photographs and derived art.

The `device` is an **illustrative historical device**, not an assertion that it is the only device for that software family or that every game has been verified on it. For broad runtimes such as WIPI or MRE, one representative handset is pictured, clearly described as such in the underlying data. Similarly, a shelf such as N-Gage can cover more than one generation.

### Suggested rendering behavior (future UI)

- Use the platform's stable card `id` to find its record. A gallery adapter will need to map existing platform IDs to these IDs; these content files alone do not change navigation.
- On **long-press** or controller **History** action, show a compact responsive surface with the device image, localized headline, paragraph and at most **2–4 facts**. Do not turn it into a multi-screen presentation.
- Select the configured AbyssME locale; if that locale is unknown, fall back to English. Resolve labels, countries, descriptive specs and non-numeric year periods through [localization.json](localization.json).
- Keep the existing normal tap-to-open-library action unchanged. Close/Back returns focus to the original platform tile.
- Package text and approved images locally if the future UI needs offline access; **the JSON files in the GitHub tree are not automatically Android assets**.
- Provide a small **Sources / Image credits** affordance without overwhelming the card.

### An example, in Russian

**Nokia N-Gage — 2003 · Nokia · Финляндия**

**Телефон, мечтавший стать приставкой**

> В 2003 году Nokia выпустила N-Gage — телефон с играми на картах памяти и мультиплеером по Bluetooth. Первая модель прославилась необычной формой и слотом для игр, спрятанным под аккумулятором. В 2004-м появилась исправленная N-Gage QD. Позднее имя N-Gage вернулось как игровой сервис для других телефонов Symbian. Одно название, два разных поколения и смелый эксперимент в истории портативных игр.

**Экран:** 176 × 208 · **Носитель:** MMC · **Связь:** Bluetooth

## Editorial and technical safeguards

1. **Historical accuracy.** These are editorial drafts, not certified reference articles. Check device dates, origin, representative models, hardware specifications and primary/archival sources before final publication. Some sources currently point to project research issues; replace or supplement those with historical primary evidence when available.
2. **Image rights.** A linked sticker and its attributed source do not automatically permit every repackaging or redistribution method. Retain source credits and review license obligations, especially ShareAlike works and photographs adapted with generative tooling.
3. **Concise copy.** Keep each English original around 60–100 words; translated lengths may differ naturally. Prefer a real fact or an interesting design story over marketing hype. Avoid repeating the same boilerplate across all cards.
4. **Existing translations.** Preserve eight-language completeness when changing text. Historical product names and personal names must remain accurate.
5. **Release boundaries.** The 32 platform records represent *private/test development integration* as of this snapshot, while the official public `0.7.0 Beta 10` contains only four platforms. No history-card UI is publicly available yet.
6. **No game/firmware content.** These records only describe history. No ROM, game, BIOS or proprietary firmware is included.

Run `node archive/platform-history/validate.mjs` from the repository root to check the collection's structure, locale coverage, image path existence and basic editorial length constraints.

**Related:** [history-card UI issue #114](https://github.com/EriArk/JavaEmulator/issues/114) · [platform roadmap](../../docs/PLATFORM_ISSUES.md) · [current test evidence](../../docs/PROJECT_STATUS.md).
