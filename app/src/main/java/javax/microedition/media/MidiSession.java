/* Copyright 2026 AbyssME contributors. SPDX-License-Identifier: GPL-3.0-only */
package javax.microedition.media;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Process;
import java.io.IOException;

/** A private instance of the existing EAS synth and its output, never a shared master. */
final class MidiSession implements AutoCloseable {
    private final long handle;
    private final AudioTrack track;
    private final Thread thread;
    private volatile boolean closed;
    private volatile boolean failed;
    private float leftGain, rightGain;

    MidiSession(float left, float right) throws IOException {
        leftGain = left; rightGain = right;
        int[] config = new int[2];
        handle = NativeAudio.openMidi(config);
        AudioTrack created = null;
        try {
            int min = AudioTrack.getMinBufferSize(config[0], AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT);
            if (min <= 0) throw new IOException("MIDI output format unavailable");
            created = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(config[0])
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setBufferSizeInBytes(Math.max(min, config[1] * 4 * 4))
                    .setTransferMode(AudioTrack.MODE_STREAM).build();
            if (created.getState() != AudioTrack.STATE_INITIALIZED) throw new IOException("Cannot open MIDI output");
            created.play();
            track = created;
            thread = new Thread(() -> pump(config[1]), "DirectMidiAudio");
            thread.start();
        } catch (IOException | RuntimeException | Error e) {
            if (created != null) created.release();
            NativeAudio.closeMidi(handle);
            throw e;
        }
    }

    private void pump(int frames) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
        short[] pcm = new short[frames * 2];
        try {
            while (!closed) {
                int count;
                synchronized (this) {
                    if (closed) break;
                    count = NativeAudio.renderMidi(handle, pcm);
                    // Keep player gain separate from MIDI controllers and apply it
                    // before Android effects/mixing (some vendors meter pre-track gain).
                    if (leftGain != 1f || rightGain != 1f) {
                        for (int i = 0; i < count; i += 2) {
                            pcm[i] = (short) (pcm[i] * leftGain);
                            pcm[i + 1] = (short) (pcm[i + 1] * rightGain);
                        }
                    }
                }
                if (count <= 0) { failed = true; break; }
                int offset = 0;
                while (!closed && offset < count) {
                    int written = track.write(pcm, offset, count - offset, AudioTrack.WRITE_BLOCKING);
                    if (written <= 0) { if (!closed) failed = true; return; }
                    offset += written;
                }
            }
        } catch (RuntimeException e) {
            failed = true;
        }
        // Resources are released by close(), after the pump has stopped.
    }

    synchronized boolean write(byte[] data, int offset, int length) {
        return !closed && !failed && NativeAudio.writeMidi(handle, data, offset, length);
    }

    synchronized void volume(float left, float right) {
        if (!closed) { leftGain = left; rightGain = right; }
    }

    int audioSessionId() { return track.getAudioSessionId(); }

    @Override public void close() {
        synchronized (this) {
            if (closed) return;
            closed = true;
            track.pause();
            track.flush();
        }
        boolean interrupted = false;
        for (;;) {
            try { thread.join(); break; }
            catch (InterruptedException e) { interrupted = true; }
        }
        track.release();
        NativeAudio.closeMidi(handle);
        if (interrupted) Thread.currentThread().interrupt();
    }
}
