/* Copyright 2026. Licensed under the Apache License, Version 2.0. */
package ru.playsoftware.j2meloader.runtime;

import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.util.Log;

/** Owned by one player. Apply only after start: setPlaybackParams can unpause it. */
public final class AudioSpeed {
    private int applied = 100;
    private int rejected = -1;

    public void reset() { applied = 100; rejected = -1; }

    public void applyPlaying(MediaPlayer player, int percent) {
        int next = EmulationClock.normalize(percent);
        if (rejected == next || applied == next) return;
        try {
            // Rate conversion intentionally changes pitch as well as tempo.
            float rate = next / 100f;
            player.setPlaybackParams(new PlaybackParams().allowDefaults()
                    .setSpeed(rate).setPitch(rate));
            applied = next;
            rejected = -1;
        } catch (IllegalArgumentException | IllegalStateException failure) {
            rejected = next;
            Log.w("AudioSpeed", "Backend rejected playback rate; retaining audio", failure);
        }
    }
}
