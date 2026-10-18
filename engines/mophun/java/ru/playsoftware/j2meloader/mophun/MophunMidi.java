package ru.playsoftware.j2meloader.mophun;

import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.HandlerThread;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import wolphun.vm.MidiBridge;

/** One session owns one player; no global MIDI backend or work on the UI thread. */
public final class MophunMidi implements MidiBridge.Backend, AutoCloseable {
    private final File cache;
    private final HandlerThread thread = new HandlerThread("mophun-midi");
    private final Handler handler;
    private MediaPlayer player;
    private final ru.playsoftware.j2meloader.runtime.AudioSpeed audioSpeed =
            new ru.playsoftware.j2meloader.runtime.AudioSpeed();
    private volatile int speed = 100;
    private boolean prepared;
    private volatile boolean paused, closed, playing;
    private volatile int starts;
    private volatile Throwable error;

    public MophunMidi(File cache) {
        this.cache = cache;
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    public boolean playing() { return playing; }
    public int starts() { return starts; }
    public Throwable error() { return error; }

    public void speed(int percent) {
        speed = percent <= 0 ? 100 : Math.max(50, Math.min(200, percent));
        if (!closed) handler.post(() -> {
            if (player != null && playing && !paused) audioSpeed.applyPlaying(player, speed);
        });
    }

    @Override public void play(byte[] smf, boolean loop) {
        if (closed) return;
        if (smf.length < 14 || smf.length > 1024*1024
                || smf[0] != 'M' || smf[1] != 'T' || smf[2] != 'h' || smf[3] != 'd') {
            throw new IllegalArgumentException("Invalid MIDI resource");
        }
        byte[] data = smf.clone();
        handler.post(() -> {
            release();
            if (closed) return;
            File temporary = null;
            try {
                temporary = File.createTempFile("mophun-midi-", ".mid", cache);
                Files.write(temporary.toPath(),data);
                MediaPlayer next = new MediaPlayer();
                player = next;
                next.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
                next.setOnPreparedListener(p -> {
                    if (p != player || closed) return;
                    prepared = true;
                    try { p.setLooping(loop); applyPause(); }
                    catch (RuntimeException failure) { fail(failure); }
                });
                next.setOnCompletionListener(p -> { if (p == player) release(); });
                next.setOnErrorListener((p,what,extra) -> {
                    if (p == player) fail(new java.io.IOException("MIDI playback error " + what + "/" + extra));
                    return true;
                });
                try (FileInputStream in = new FileInputStream(temporary)) {
                    next.setDataSource(in.getFD(),0,data.length);
                }
                next.prepareAsync();
            } catch (Exception failure) { fail(failure); }
            finally {
                if (temporary != null && !temporary.delete())
                    android.util.Log.w("Mophun", "Could not remove temporary MIDI file");
            }
        });
    }

    public void pause(boolean value) {
        paused = value;
        if (!closed) handler.post(() -> {
            try { applyPause(); } catch (RuntimeException failure) { fail(failure); }
        });
    }

    private void applyPause() {
        if (player == null || !prepared || closed) return;
        if (paused && playing) { player.pause(); playing = false; }
        else if (!paused && !playing) {
            player.start(); playing = true; starts++;
            audioSpeed.applyPlaying(player, speed);
        }
    }

    private void fail(Throwable failure) {
        error = failure;
        android.util.Log.w("Mophun", "MIDI playback",failure);
        release();
    }

    private void release() {
        if (player != null) { player.release(); player = null; }
        prepared = false;
        playing = false;
        audioSpeed.reset();
    }

    @Override public void stop() { if (!closed) handler.post(this::release); }

    @Override public void close() throws InterruptedException {
        if (!closed) {
            closed = true;
            handler.post(() -> { release(); thread.quitSafely(); });
        }
        thread.join(3000);
        if (thread.isAlive()) throw new IllegalStateException("MIDI worker did not stop");
    }
}
