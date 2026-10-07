# Local game sample: 2026-10-08

Initial sample from a maintainer-provided archive, not a download list or
redistributable fixture pack. 1,126 JAR files were found; 1,122 readable manifests
were surveyed. Files and raw survey data remain local and ignored by Git.

Tested with Phone debug, source revision `749ea2ed`, on the Android emulator
`emulator-5554` (x86_64). Imports and launches used the normal application flow.
The optional private engine was present but these are Java games. No physical
controller, audio, save round trip or complete playthrough is claimed here.

Device labels are **evidence strength**, not confirmed original device builds.
Many archives rename or modify games. A publisher, API reference, advertising
line or filename is not proof of the intended screen or phone model.

| Archive file / version | Device evidence | Automatic selection | Observed result |
| --- | --- | --- | --- |
| `AgeOfEmpires2_S40v3.jar` / 1.1.1 | Filename says S40v3; Nokia API hints; MIDP 1.0 | Nokia S40, low confidence; 240x320 fallback | Title/menu rendered. A short OK tap did not advance; gameplay/control acceptance remains open. |
| `QuadraPop.jar` / 1.4.2 | Manifest publisher Sony Ericsson | Sony Ericsson JP-8; 240x320 fallback | New Game menu rendered, touch OK opened the moving game board. Full framing/audio/save checks remain open. |
| `GoldHunter1599.jar` / 01.00 | Manifest publisher Motorola; MIDP 1.0 | Motorola; **255x160 from background.png** | Title rendered. Short OK tap did not advance. Background dimensions are not reliable evidence of a native screen size. |
| `DarkestFear2.jar` / 1.30 | Siemens mentioned in a distributor's delete-confirm text, not a confirmed device; MIDP 1.0 | Siemens; 240x320 fallback | Language list rendered; left softkey advanced to the sound question. This archive contains a Russian translation. Gameplay not checked. |
| `TeamMcLarenMercedes.jar` / 0.1.6 | Samsung name comes from distributor branding, **target unconfirmed** | Samsung; 240x320 resource hint | Language picker rendered; OK selected English and advanced to the loading/title artwork. Not a completed race test. |
| `Ghost_Recon_2_SE_128x160.jar` / 1.2.6 | Filename says SE 128x160; not present in MIDlet metadata | Generic MIDP; **240x320 fallback** | Black game surface. Log contains NullPointerException in game paint code (`a.a`, `a.d`, `a.r`). Root cause not established. |

## Findings to address

- Do not treat unusual background dimensions as a reliable native screen size.
- Distinguish target metadata from publisher/distributor advertising and web
  addresses. Current static heuristics can overstate vendor confidence.
- Evaluate retaining source-filename resolution hints when the manifest/resources
  carry no equivalent information, without overriding explicit/manual settings.
- Reproduce Ghost Recon 2 with 128x160 and a suitable profile, then isolate the
  resource/rendering failure. A black screen is not automatically a size bug.
- Expand the sample with verifiable Samsung touch, 176x220 and 360x640/480x800
  builds. The current archive labels do not establish those targets.

These findings keep #14 open. The 50-test public-source regression suite passing
does not make these six game releases fully compatible. Screenshots were captured
locally and shown in the development conversation; commercial game files/artwork
are not being added as repository fixtures.

## Exact file identities

```text
372b636c8830d907c6b92ef85d61f9e2bc64355803cbc04be3d5302110ccac68  AgeOfEmpires2_S40v3.jar
ba4788574fc3991e3b87ed7c93302272c30a465291120f68954ba6507444eba9  QuadraPop.jar
251c3daa4b76c2ccf1953fda90b797095fa43f17f9666e196befc0e3afcc4d5e  TeamMcLarenMercedes.jar
1b95723ff37b32d456aad85694e5381256e0c14b1eed1dcaa3c0018d3f9283be  GoldHunter1599.jar
14c29c44a5e08c99501ea264b206b311db310b0fbbc75318a7532133c07b5541  DarkestFear2.jar
101a60cdd265117e832ce10646c066fb542f086e2afb9895bca6d950d3e7d4af  Ghost_Recon_2_SE_128x160.jar
```
