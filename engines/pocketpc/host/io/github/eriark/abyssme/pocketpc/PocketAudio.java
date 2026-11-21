/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.pocketpc;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import com.pockethle.app.NativeBridge;

final class PocketAudio implements AutoCloseable {
    interface Format { long read(long session); }
    interface Samples { short[] read(long session, int limit); }
    private final Format formatReader;
    private final Samples sampleReader;
    private final Thread worker;
    private volatile boolean closed;
    volatile boolean paused;
    PocketAudio(long session) {
        this(session, NativeBridge::nativeAudioFormat, NativeBridge::nativePollAudio);
    }
    PocketAudio(long session, Format format, Samples samples) {
        formatReader = format;
        sampleReader = samples;
        worker = new Thread(()->play(session),"Windows CE audio");
        worker.start();
    }
    private void play(long session) {
        AudioTrack track=null;
        long previous=0;
        int pullSamples=0;
        try {
            while (!closed) {
                if (paused) {
                    if(track!=null && track.getPlayState()==AudioTrack.PLAYSTATE_PLAYING) { track.pause(); track.flush(); }
                    Thread.sleep(12); continue;
                }
                long format=formatReader.read(session);
                if(format==0) { Thread.sleep(12); continue; }
                if(track==null || format!=previous) {
                    if(track!=null) track.release();
                    int rate=(int)(format>>>16);
                    int channels=(int)(format&65535);
                    // WinMM mixers poll the play cursor modulo their DMA ring size.
                    // Draining a whole ring each time makes it appear stationary.
                    pullSamples=Math.min(16384, Math.max(1, rate / 100) * channels);
                    int mask=channels==1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
                    track=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).build())
                        .setAudioFormat(new AudioFormat.Builder().setChannelMask(mask).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).build())
                        .setBufferSizeInBytes(Math.max(4096,AudioTrack.getMinBufferSize(rate,mask,AudioFormat.ENCODING_PCM_16BIT))).build();
                    previous=format;
                }
                if(track.getPlayState()!=AudioTrack.PLAYSTATE_PLAYING) track.play();
                short[] samples=sampleReader.read(session,pullSamples);
                if(samples==null || samples.length==0) { Thread.sleep(5); continue; }
                int offset=0;
                while(offset<samples.length && !closed && !paused) {
                    int n=track.write(samples,offset,samples.length-offset,AudioTrack.WRITE_NON_BLOCKING);
                    if(n<0) return;
                    if(n==0) Thread.sleep(3);
                    offset+=n;
                }
            }
        } catch (InterruptedException done) { Thread.currentThread().interrupt(); }
        catch (RuntimeException error) { android.util.Log.w("PocketHLE","Android audio unavailable",error); }
        finally { if(track!=null) track.release(); }
    }
    public void close() {
        closed=true;
        worker.interrupt();
        boolean interrupted=false;
        while(worker.isAlive()) try { worker.join(); } catch(InterruptedException e) { interrupted=true; }
        if(interrupted) Thread.currentThread().interrupt();
    }
}
