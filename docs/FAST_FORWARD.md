# Fast-forward

Fast-forward is a temporary, per-game speed override for Java, Mophun and
Symbian/N-Gage. It does not change the saved normal emulation speed.

## Controls

- Open **Game menu > More > Fast-forward settings**, or **Controls > Fast-forward**.
- Choose a target of **150%, 200%, 300% or 400%**, relative to normal emulation.
- Assign **L3/R3, L1/R1, L2/R2 or A/B/X/Y**, or leave the controller button at
  **None**. The assigned button is reserved for fast-forward in that game;
  its original game binding remains saved but does not fire while reserved.
- Choose **Hold** or **Toggle** for the controller. Select keeps its existing
  mapping/menu role. Trigger axes and digital trigger events are deduplicated.
- Choose whether to show the on-screen shortcut. Touch always toggles.
- Use **Try changes**, then **Keep changes**; Undo restores the prior settings.

The game menu also offers an immediate fast-forward command. The shortcut row
does not move or resize the game. While accelerated, a visible shortcut stays
available after the other buttons hide, so it can be switched off directly.

Opening a menu, losing focus, backgrounding or disconnecting a controller stops
fast-forward and restores the saved/trial base speed and frame-rate limit. A new
session starts normally. Activation itself is never saved or backed up.

## Limits

Targets are requested rates, not guaranteed performance. Fast-forward raises a
configured frame cap temporarily, but rendering, CPU load and game-specific
timing can limit acceleration. An unlimited cap stays unlimited. A target below
the current base speed never slows the game down.

Java guest clocks, converted sleeps and timers use the accelerated time; this
does not promise skipping every farming/city-building countdown or server-side
wait. Little Big City and Green Farm have not been individually accepted.
Older converted-only Java installations need their original JAR reimported to
enable guest clock conversion; existing saves should be kept.

Audio tempo and pitch follow speed where supported. Backend rejection can retain
a supported audio rate; this is not pitch-preserving time stretching. Do not
interpret automated audio progress checks as a listening-quality assessment.

See [current availability](PROJECT_STATUS.md) and
[Beta 3 revision checks](releases/v0.4.0-beta.3-r1-checks.md).
