/* Copyright 2026 AbyssME contributors. SPDX-License-Identifier: Apache-2.0 */
package javax.microedition.media.tone;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.microedition.media.Manager;
import javax.microedition.media.MediaException;
import javax.microedition.media.Player;
import javax.microedition.media.PlayerListener;

/** Routes synthesized MIDI through the existing host player. */
public final class ToneManager {
    private ToneManager() { }
    public static Player createPlayer(int note, int duration, int volume) throws MediaException {
        byte[] midi = ToneSequence.singleTone(note, duration, volume);
        try { return Manager.createPlayer(new ByteArrayInputStream(midi), "audio/midi"); }
        catch (IOException e) { throw new MediaException(e.toString()); }
    }
    public static void play(int note, int duration, int volume) throws MediaException {
        Player player = createPlayer(note, duration, volume);
        player.addPlayerListener((source, event, data) -> {
            if (PlayerListener.END_OF_MEDIA.equals(event) || PlayerListener.ERROR.equals(event)) source.close();
        });
        try { player.start(); }
        catch (MediaException | RuntimeException e) { player.close(); throw e; }
    }
}
