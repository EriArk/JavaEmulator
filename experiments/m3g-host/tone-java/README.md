# Candidate ToneControl Replacement

Original AbyssME implementation, Apache-2.0. The public API and sequence grammar
are described by the [MIDP ToneControl specification](https://docs.oracle.com/javame/config/cldc/ref-impl/midp2.0/jsr118/javax/microedition/media/control/ToneControl.html).
This is not a port of the inherited EPL tone compiler. That package is excluded
from candidate compilation; ordinary and baseline builds retain it for now.

`ToneSequence` compiles version-1 tone sequences to standard MIDI format 0:
tempo, resolution, notes, rests, volume, repeated notes and previously defined
blocks. Recursive/undefined blocks and malformed pairs are rejected. Expansion
is bounded to avoid exponential allocation. MIDI delta times longer than four
bytes are split with empty sequencer events. Input and output arrays are copied.

`ToneManager` routes individual tones through the existing MIDI player and closes
one-shot players after completion. `TonePlayer` uses that timed Android backend
for `device://tone`; candidate Manager also routes `audio/x-tone-seq` streams to
it. Controls reject invalid sequences and changes while prefetched/started.
Deallocate allows replacement; finite repeats, completion and replay retain the
existing player lifecycle. No changes to the normal file-MIDI or Mophun player
implementations are made by this adapter.

## Verification

Run `:app:testToneCompiler` with the candidate Gradle properties. Its 14 JVM tests
use the JDK's independent `javax.sound.midi` parser to verify notes, velocities,
ticks and total duration. They are a separate JavaCompile/Test task because the
Android boot classpath does not include that parser.

`TonePlaybackTest` adds seven Android checks, including actual END_OF_MEDIA
callbacks through the host, direct game API calls, stream routing, finite loops,
deallocation/replacement and error handling. Run the host `verify.ps1` for either
variant. Callback completion is not evidence of a human listening to the device,
and these tests do not claim coverage of every vendor's proprietary sound API.
