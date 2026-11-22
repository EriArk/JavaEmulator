# Empty platforms on the Retroid launcher, 2026-11-22

The combined ARM64 debug build was updated in place on Retroid Pocket Classic. On a cold start the populated library opened on the platform grid with the new checkbox cleared. This build is private.

| Check | Result |
| --- | --- |
| Occupied and empty platforms | Android instrumentation supplied empty and populated catalogs to the launcher. Empty devices disappeared when the checkbox was clear. Revealed devices could receive focus and open setup. Adding a library entry removed the gray filter. An occupied Mophun tile opened its existing tab. |
| Preference and navigation | The checkbox survived activity recreation. Global search, Back, and the setting to skip the launcher passed the library navigation tests. |
| Java ME setup | The system-file action was disabled and the game action offered the existing file and folder choices. |
| Atari Portfolio setup | The system action requested an Android document picker with multiple selection enabled. A simulated cancellation returned to the launcher. |
| Firmware before the first game | In isolated storage, the launcher’s provider entry points installed both Cybiko firmware pairs and four Portfolio ROM/font files. The installed bytes matched the original files. Both shelves stayed empty until game import. Two Portfolio packages then appeared in its shelf. |
| Existing library | The 104 managed package directories and six sampled save files matched the pre-update inventory. Only the existing combined app remained installed after removing instrumentation. |

Eight Android instrumentation checks passed, including seven library UI tests and the firmware import check. The setup UI tests used temporary catalog data and automated input. Gameplay and hand-operated controller input were outside this launcher check.
