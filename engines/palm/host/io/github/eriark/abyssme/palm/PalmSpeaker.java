/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.palm;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/** Stream the DragonBall speaker's PWM output through Android's game audio path. */
final class PalmSpeaker implements AutoCloseable {
    private static final int RATE = 48000;
    private volatile double frequency, duty;
    private volatile boolean closed, paused;
    private Thread worker;

    synchronized void tone(double hz, double cycle) {
        if (closed) return;
        duty = Math.max(0, Math.min(1, cycle));
        frequency = hz > 0 && hz < RATE / 2 ? hz : 0;
        if (worker == null && frequency > 0) {
            worker = new Thread(this::play, "Palm speaker");
            worker.start();
        }
        notifyAll();
    }
    synchronized void pause(boolean value) { paused = value; notifyAll(); }
    private void play() {
        AudioTrack track = null;
        try {
            int min = AudioTrack.getMinBufferSize(RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);
            track = new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setBufferSizeInBytes(Math.max(1920,min)).setTransferMode(AudioTrack.MODE_STREAM).build();
            short[] block = new short[240];
            double phase = 0;
            while (!closed) {
                synchronized(this) {
                    while (!closed && (paused || frequency == 0)) {
                        if (track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) track.stop();
                        wait();
                    }
                }
                if (closed) break;
                if (track.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) track.play();
                double step = frequency / RATE, cycle = duty;
                for (int i=0; i<block.length; i++) {
                    block[i]=(short)(10000*((phase < cycle ? 1 : 0)-cycle));
                    phase+=step; phase-=Math.floor(phase);
                }
                if (track.write(block,0,block.length)<0) break;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            android.util.Log.w("PalmSpeaker","Android playback unavailable",e);
        } finally {
            if (track!=null)track.release();
            synchronized(this) { worker=null; }
        }
    }
    @Override public synchronized void close() { closed=true; notifyAll(); }
}
