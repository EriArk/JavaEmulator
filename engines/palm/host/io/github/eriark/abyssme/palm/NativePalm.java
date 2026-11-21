/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.palm;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.util.AtomicFile;
import android.util.Log;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Each machine has its own native process. Its guest RAM and hardware globals die with it. */
final class NativePalm implements AutoCloseable {
    interface Listener {
        void ready(int width, int height);
        void legacy();
        void saved(String error);
        void failed(String error);
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService commands = Executors.newSingleThreadExecutor();
    private final Listener listener;
    private final AtomicFile checkpoint;
    private volatile boolean closed;
    private volatile Process process;
    private int[] latest;
    private boolean dirty;

    NativePalm(File state, Listener listener) {
        checkpoint = new AtomicFile(state);
        this.listener = listener;
    }

    void start(List<String> args, File cache, ZipFile archive, List<ZipEntry> entries) {
        new Thread(() -> {
            File temp = null;
            try {
                if (archive != null) {
                    temp = Files.createTempDirectory(cache.toPath(), "palm-dbs-").toFile();
                    for (ZipEntry e : entries) {
                        if (closed) return;
                        File file = new File(temp, Integer.toString(args.size()));
                        try (InputStream in = archive.getInputStream(e)) { Files.copy(in, file.toPath()); }
                        args.add(file.getPath());
                    }
                }
                synchronized (this) {
                    if (closed) return;
                    process = new ProcessBuilder(args).redirectError(new File("/dev/null")).start();
                }
                try (DataInputStream input = new DataInputStream(new BufferedInputStream(process.getInputStream(), 65536))) {
                    receive(input);
                }
            } catch (Exception e) {
                post(() -> listener.failed("Palm OS: " + e.getMessage()));
            } finally {
                Process child = process;
                if (child != null) child.destroy();
                if (temp != null) {
                    File[] files = temp.listFiles();
                    if (files != null) for (File file : files) file.delete();
                    temp.delete();
                }
            }
        }, "Palm device").start();
    }

    private void post(Runnable task) { main.post(() -> { if (!closed) task.run(); }); }

    private void receive(DataInputStream in) throws IOException {
        byte[] frame = null, pcm = new byte[7350 * 4];
        IntBuffer words = null;
        AudioTrack track = null;
        boolean audioAvailable = true;
        try {
            while (!closed) {
                int type = in.read();
                if (type == -1) throw new EOFException("Native machine stopped");
                int size = Integer.reverseBytes(in.readInt());
                if (size < 0) throw new IOException("Invalid native packet length");
                switch (type) {
                    case 'W':
                        post(listener::legacy);
                        return;
                    case 'R':
                        if (size != 8) throw new IOException("Invalid native screen");
                        int width = Integer.reverseBytes(in.readInt()), height = Integer.reverseBytes(in.readInt());
                        if (width <= 0 || height <= 0 || width > 1024 || height > 1024) throw new IOException("Invalid Palm dimensions");
                        frame = new byte[width * height * 4];
                        words = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
                        synchronized (this) { latest = new int[width * height]; }
                        post(() -> listener.ready(width, height));
                        break;
                    case 'F':
                        if (frame == null || size != frame.length) throw new IOException("Invalid Palm frame");
                        in.readFully(frame);
                        synchronized (this) { words.position(0); words.get(latest); dirty = true; }
                        break;
                    case 'A':
                        if (size > pcm.length || (size & 3) != 0) throw new IOException("Invalid PCM block");
                        in.readFully(pcm, 0, size);
                        if (audioAvailable) {
                            try {
                                if (track == null) track = audioTrack();
                                if (track.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) track.play();
                                int written = track.write(pcm, 0, size, AudioTrack.WRITE_BLOCKING);
                                if (written < 0) throw new IllegalStateException("AudioTrack error " + written);
                            } catch (RuntimeException e) {
                                audioAvailable = false;
                                Log.w("NativePalm", "PCM playback unavailable", e);
                            }
                        }
                        break;
                    case 'P':
                        if (track != null) { track.pause(); track.flush(); }
                        break;
                    case 'S':
                        String error = writeCheckpoint(in, size);
                        post(() -> listener.saved(error));
                        break;
                    case 's':
                    case 'E':
                        byte[] message = new byte[size];
                        in.readFully(message);
                        String reason = new String(message, java.nio.charset.StandardCharsets.UTF_8);
                        if (type == 's') post(() -> listener.saved(reason));
                        else { post(() -> listener.failed(reason)); return; }
                        break;
                    default: throw new IOException("Unknown native packet " + type);
                }
            }
        } finally {
            if (track != null) track.release();
        }
    }

    private static AudioTrack audioTrack() {
        int min = AudioTrack.getMinBufferSize(44100, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(44100).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(Math.max(16384, min)).setTransferMode(AudioTrack.MODE_STREAM).build();
        track.setVolume(0.25f);
        return track;
    }

    private String writeCheckpoint(DataInputStream in, int size) throws IOException {
        byte[] buf = new byte[65536];
        FileOutputStream file = null;
        String error = null;
        try {
            for (int remaining = size; remaining > 0;) {
                int count = Math.min(buf.length, remaining);
                in.readFully(buf, 0, count);
                remaining -= count;
                if (error == null) try {
                    if (file == null) file = checkpoint.startWrite();
                    file.write(buf, 0, count);
                } catch (IOException e) { error = e.toString(); }
            }
            if (file == null && error == null) return "Empty Palm checkpoint";
            if (error == null) {
                file.getFD().sync();
                checkpoint.finishWrite(file);
                file = null;
            }
            return error;
        } catch (SyncFailedException e) {
            return e.toString();
        } finally {
            if (file != null) checkpoint.failWrite(file);
        }
    }

    synchronized boolean frame(int[] target) {
        if (!dirty) return false;
        System.arraycopy(latest, 0, target, 0, latest.length);
        dirty = false;
        return true;
    }

    // Commands are small atomic writes. File I/O and serialization never block the UI thread.
    void command(char type, int a, int b) {
        Process child = process;
        if (closed || child == null) return;
        byte[] packet = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(type).putInt(a).putInt(b).array();
        commands.execute(() -> {
            if (closed) return;
            try {
                OutputStream out = child.getOutputStream();
                out.write(packet);
                out.flush();
            } catch (IOException e) { /* The reader reports a stopped machine. */ }
        });
    }

    @Override public synchronized void close() {
        closed = true;
        commands.shutdownNow();
        if (process != null) process.destroy();
    }
}
