# Non-Friend and Shared Directory Checkpoint

2026-10-24. Unreleased source and local combined Debug builds only. The public
APK and shared lobby service were not changed in this checkpoint.

## Shared Service

The owner updated the quota decision: preserve 32 active TrainerOS listings per
account, independently of one active AbyssME public room. Existing UUIDs retain
both authenticated owner and product identity, including withdrawn/closed rows.
Clients cannot overwrite another product's announcement.

Read-only inspection found the deployed v1 service ahead of the initial PR
baseline. The isolated [TrainerOS PR #260](https://github.com/EriArk/TrainerOS/pull/260)
now preserves scheduled meetings, separate listing/session IDs, titles, member
counts and provider-authoritative avatars. Recovery after a lost create response
uses the listing ID. Commit `f772f38` passes 22 Python tests, including quota
coexistence/release, migration, metadata validation and recovery. The unrelated
TrainerOS checkout and server data were not changed. Deployment with backup and
live v1/v2 publication/withdrawal acceptance remains a separate gate.

## Client Repairs

- Schedule the next inbox poll from completion, not its start. Otherwise a poll
  lasting more than ten seconds could immediately start another poll indefinitely,
  starving queued invitations and terminal controls. A regression queues an
  invitation and close across slow reads and verifies both are sent before polling
  resumes. Existing delivery expiry, rate-limit and ambiguous-write rules remain.
- Preserve only a bounded provider machine error code, never the raw error body.
  Show specific notices for email verification, non-friend message restrictions,
  permission denial and rate limits. The room owner no longer discards the safe
  delivery reason in favor of a generic connection error. No provider restriction
  or account privacy setting is bypassed.
- An AbyssME quota conflict no longer tells players to close a TrainerOS room.
- Replaying inbox history after restart could resurrect an already-used offer:
  the host snapshot confirming its earlier acceptance was ignored outside the
  current room. Historical authenticated admission/close/removal now consumes
  the matching offer and fences older packets, without restoring membership.
  Regression checks retain sender and host-incarnation boundaries. This is not
  a new persistent journal of all declined invitations.

## Live Evidence and Limits

The previous [live friend/private-room checkpoint](FLUXER_LIVE_PAIR.md) remains
separate. For this run, an opt-in native test removed only the existing friendship
between the two dedicated AbyssME test accounts and confirmed it absent. No
personal contacts, blocks, privacy settings or account credentials were changed.

Retroid Pocket Classic hosted; an Android emulator was the guest. The production
room/contact actors used real HTTP. The first attempt timed out before admission.
After both accounts had accessed the shared community, explicit message probes
returned HTTP 200 in both directions. Thus a persistent provider prohibition was
not established; initial community-admission timing is a possible cause, not a
proven diagnosis. No privacy or email-verification workaround was applied.

The repeated **cleanup cycle passed on both devices**: no initial relationship,
room invitation/admission, automatic temporary request/acceptance, confirmed
friendship, activity recreation, guest departure, host close and provider-confirmed
absence of the relationship at both ends. Host 76.191s, guest 97.143s.
The second account's email verification remains pending, but accepting the
verified host's request and the observed room exchange were normally permitted.
The first reduced-memory emulator boot showed a System UI ANR; the successful
repeat used the prior default-memory configuration. This remains one physical
device plus an emulator, not two physical handhelds.

The final **Keep cycle also passed on both devices**, after the invitation-history
repair: automatic friendship, screen recreation, guest Keep acknowledgement,
guest departure, host close and fresh provider reads confirming the retained
friendship at both ends. Host 60.090s, guest 70.423s. These are four successful
live role runs across cleanup and Keep; earlier failed attempts are retained as
diagnostic evidence, not counted as passes. Test commands use production models,
not a claim of physical-button acceptance or online gameplay.

Combined Debug app/test builds passed, with **292 JVM tests**, zero failures,
errors or skips. Four Retroid native core checks passed. The explicit named-test-
friend removal and both message probes passed independently; those probes alone
are not room acceptance. Production online game adapters remain empty.

`FluxerLiveContactsDeviceTest` is opt-in and requires dedicated test accounts.
The cleanup/Keep path refuses to replace an existing room or start from an
existing relationship. Its setup removal requires an exact named test friend.
A separate explicit probe sends one labelled test message only in an existing
named test conversation and reports HTTP status plus a sanitized machine code.
No tokens, email contents or raw provider errors are reported.

Ignored evidence: `build/fluxer-public-build.log`,
`build/fluxer-contact-cleanup-host.log`, `build/fluxer-contact-cleanup-guest.log`,
`build/fluxer-contact-cleanup-retry-host.log`,
`build/fluxer-contact-cleanup-retry-guest.log`, `build/fluxer-contact-native-core.log`,
`build/fluxer-contact-keep-final-host.log`, `build/fluxer-contact-keep-final-guest.log`,
`build/fluxer-contacts-final-build.log`, `build/fluxer-invitation-history-build.log`.
The initial Keep run also exposed the replayed-offer defect above; failed logs
remain `build/fluxer-contact-keep-host.log` and `build/fluxer-contact-keep-guest.log`.
These logs are local, not release assets.

Final installed Debug APK SHA-256:
`cfc80d045891f09cd0449ae3cf7b932b562587b17120f1c7af083c1afed3b299`.
Label/code remain `0.5.0-beta.1-debug` / 310 in the separate development package.
No release asset was rebuilt, replaced or distributed.

Still open: shared service rollout and live product isolation,
physical gamepad acceptance, online
game adapters and playable multiplayer. Do not announce these as completed.
