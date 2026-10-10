# Licensing status

## Decision and current scope

On 2026-10-07, the maintainer requested publishing accumulated development
changes while withholding new release APKs until upstream permission is
resolved. A future project-wide license remains undecided; no license change
has taken effect. The integration blocker is the existing EPL-1.0 M3G
implementation described below, not the presence of the root LICENSE file.

Public source updates cover AbyssME's UI, controls, importer, database and Java
display work. The optional-engine interface contains no Wolphun implementation;
it also preserves managed game folders when the corresponding engine is absent.
Private engine sources, integration implementations, test game archives and
local APKs are excluded from these commits. New release APKs are on hold.

The root [LICENSE](../LICENSE) remains Apache-2.0. It does not override
component-specific licenses, existing copyright notices, or separately licensed
games and artwork. Wolphun experiments are limited to ignored local files and
opt-in local debug builds, not the distributable source tree. Published APKs
have not been replaced or retroactively relicensed.

## Upstream response: 2026-10-10

The permission request is recorded in
[Wolphun issue #1](https://github.com/jamisson2006/wolphun-a-Mophun-emulator/issues/1).
The author has [responded positively](https://github.com/jamisson2006/wolphun-a-Mophun-emulator/issues/1#issuecomment-6093620526),
welcoming use of the project as a reference. We have sent a
[short clarification](https://github.com/jamisson2006/wolphun-a-Mophun-emulator/issues/1#issuecomment-6100205259)
to record permission for code inclusion/modification and distribution of the
combined source and APKs with the EPL-1.0 M3G components, including the scope
of a GPLv3 section 7 additional permission and any other contributors' rights.

Status: **positive upstream response; precise additional-permission scope being
documented**. This is no longer an unanswered request. Integration preparation
can continue in the existing app; separate end-user applications are not the
selected design. No new license text is attributed to the author by this update,
and the upstream GPLv3 license and inherited notices remain unchanged.
Public combined source/APK distribution still awaits the recorded permission
scope and the release checks below. No release has been published by this update.

## Verified integration blocker

| Component | Evidence in the checkout or upstream | Terms |
| --- | --- | --- |
| Current fork and inherited Apache-licensed code | Root LICENSE and individual source headers | Apache-2.0 |
| Nokia/Symbian M3G native engine | `app/src/main/cpp/m3g/`, for example `inc/m3g_core.h` | EPL-1.0 |
| M3G Java bindings | `app/src/main/java/javax/microedition/m3g/`, including `Platform.java` | EPL-1.0 |
| Proposed Wolphun engine | [Upstream LICENSE](https://github.com/jamisson2006/wolphun-a-Mophun-emulator/blob/main/LICENSE) | GPLv3; not integrated |

This is executable code, not an unused notice: `app/src/main/cpp/Android.mk`
builds `javam3g`, and `Platform.java` loads it through `System.loadLibrary`.
Other component notices are recorded in
[`licenses.html`](../app/src/main/assets/licenses.html). That inherited list and
the table above are not a complete dependency or release-artifact audit.

Apache-2.0 code may be included in a GPLv3 work while retaining its notices;
see the [Apache Software Foundation's compatibility explanation](https://www.apache.org/licenses/GPL-compatibility.html).
However, the [Eclipse EPL-1.0 FAQ, question 32](https://www.eclipse.org/legal/epl/faq/)
identifies incompatibility with GPLv2 and GPLv3 for combined/linked works.
Replacing the root license or moving code into another Gradle module does not
resolve that issue. Maintainer approval cannot change third-party rights.

## Paths to resolve before integration

1. Request suitable additional permission or alternative licensing from the
   relevant Wolphun rights holders, covering the actual EPL-1.0 combination.
   Review provenance and dependencies too; one author's permission cannot
   override other contributors' rights. The positive reply and scope clarification
   are linked above; do not substitute our requested wording for the author's grant.
2. Evaluate a GPL-compatible replacement for the EPL-1.0 M3G implementation
   and bindings, with regression tests for existing J2ME 3D games. Removing 3D
   support is not approved as a shortcut.
3. Evaluate Wolphun as a genuinely separate application with a launch protocol.
   This changes the integration design and needs its own licensing review;
   a separate module, shared library or Android process alone is not clearance.

The selected preparation path is additional upstream permission, which could
preserve existing J2ME compatibility without replacing M3G. The author's positive
response is recorded above; exact scope and dependency review remain outstanding.

## Transition and release checklist

- Resolve and document the EPL/GPL integration path before publishing combined
  Wolphun/M3G source or binaries. Keep private experiments out of tracked files.
- Review all bundled source and resolved binary dependencies, including native
  libraries, for notices, redistribution terms and source obligations.
- Select the exact SPDX expression and scope for AbyssME-owned changes; retain
  third-party terms and copyright headers rather than bulk-relabeling files.
- Update LICENSE, README, contributor guidance and in-app notices together.
- Supply required license texts, notices and corresponding source/build
  materials for each newly distributed binary, with a matching source revision.
- Keep game fixtures under their own terms; do not bundle personal test games.

Until these checks are complete, do not describe a combined Wolphun/M3G APK as
cleared for redistribution. This document records engineering evidence and a
release gate, not a legal opinion or a completed compliance audit.
