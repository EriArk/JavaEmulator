package ru.playsoftware.j2meloader.mophun;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import com.manedsoftware.wolphun.MophunCrypt;
import wolphun.Screen;
import wolphun.vm.Pip2;
import wolphun.vm.Sdk;
import wolphun.vm.Vmgp;

/** Owns one Mophun worker, game clock, audio and save lifecycle. */
public final class MophunSession implements AutoCloseable {
    private final File game, saves;
    private final File audioCache;
    private final int model, width, height;
    private final Thread worker;
    private final MophunClock clock = new MophunClock();
    private volatile int frameRateLimit = 60;
    private volatile int cpuBudget = 100;
    public void setCpuBudget(int percent) {
        cpuBudget = Math.max(100, Math.min(300, percent));
    }
    public int getCpuBudget() { return cpuBudget; }
    private final javax.microedition.lcdui.FramePacer framePacer =
            new javax.microedition.lcdui.FramePacer(() -> frameRateLimit);
    private volatile boolean stopped, paused, parked, finished;
    private volatile Screen screen;
    private volatile Throwable error;
    private MophunMidi midi;

    public MophunSession(File game, File saves, int model, int width, int height) {
        this(game, saves, model, width, height, null);
    }

    public MophunSession(File game, File saves, int model, int width, int height, File audioCache) {
        this.game = game;
        this.saves = saves;
        this.audioCache = audioCache;
        this.model = model;
        this.width = width;
        this.height = height;
        worker = new Thread(this::run, "abyssme-mophun");
    }

    public void start() { worker.start(); }
    public Screen screen() { return screen; }
    public Throwable error() { return error; }
    public boolean finished() { return finished; }
    public boolean parked() { return parked; }

    public void setFrameRateLimit(int fps) {
        frameRateLimit = Math.max(0, Math.min(240, fps));
        framePacer.reset();
    }

    public int getFrameRateLimit() { return frameRateLimit; }
    public synchronized void setEmulationSpeed(int percent) {
        clock.speed(percent);
        if (midi != null) midi.speed(clock.speed());
    }
    public int getEmulationSpeed() { return clock.speed(); }

    public synchronized void pause(boolean value) {
        if (paused == value) return;
        if (screen != null) screen.clearButtons();
        clock.pause(value);
        if (midi != null) midi.pause(value);
        paused = value;
        framePacer.reset();
    }

    private static final class SessionStopped extends RuntimeException { }

    private void checkpoint() {
        if (Thread.currentThread() != worker) return;
        try {
            while (paused && !stopped) {
                parked = true;
                Thread.sleep(10);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            throw new SessionStopped();
        }
        if (stopped) throw new SessionStopped();
        parked = false;
    }

    public static byte[] readExecutable(File file) throws IOException {
        if (!file.isFile() || file.length() < 40 || file.length() > 16 * 1024 * 1024)
            throw new IOException("Invalid Mophun executable size");
        byte[] data = Files.readAllBytes(file.toPath());
        if (data.length < 40 || data[0] != 'V' || data[1] != 'M' || data[2] != 'G' || data[3] != 'P')
            throw new IOException("This prototype accepts unpacked VMGP executables only");
        long code = u32(data, 12), section = u32(data, 16), bss = u32(data, 20);
        long resources = u32(data, 24), pool = u32(data, 32), strings = u32(data, 36);
        long end = 40 + code + section + resources + pool * 8 + strings;
        if (end > data.length || pool > 100000 || code + section + bss > 16 * 1024 * 1024)
            throw new IOException("Invalid VMGP section sizes");
        return data;
    }

    private static long u32(byte[] data, int offset) {
        return (data[offset] & 255L) | ((data[offset + 1] & 255L) << 8)
                | ((data[offset + 2] & 255L) << 16) | ((data[offset + 3] & 255L) << 24);
    }

    public static Vmgp executable(File game) throws IOException {
        return executable(readExecutable(game));
    }

    private static Vmgp executable(byte[] data) throws IOException {
        if (MophunCrypt.looksEncrypted(data)) {
            data = MophunCrypt.decrypt(data);
            if (data == null) throw new IOException("Cannot decode Mophun executable");
        }
        try { return new Vmgp(data); }
        catch (RuntimeException failure) { throw new IOException("Invalid Mophun executable", failure); }
    }

    private void run() {
        Sdk sdk = null;
        try {
            byte[] executable = readExecutable(game);
            boolean explicitCreation = MophunCompatibility.requireExplicitFileCreation(executable);
            Vmgp exe = executable(executable);
            if (!saves.isDirectory() && !saves.mkdirs()) throw new IOException("Cannot create saves directory");
            if (explicitCreation) MophunCompatibility.repairPrototypeLevelSelection(saves);
            Screen target = new Screen(width, height, 1, 1);
            target.hostCheckpoint = this::checkpoint;
            target.targetFps = 60;
            target.hostFramePacer = () -> {
                framePacer.awaitFrame();
                checkpoint();
            };
            Pip2 vm = new Pip2(exe, 4 * 1024 * 1024, 256 * 1024);
            sdk = new Sdk(vm, target, clock);
            sdk.soundEnabled = false;
            synchronized (this) {
                // Enable only the verified Deep Abyss 1.4 path; PCM games remain unaccepted.
                if (audioCache != null && MophunCompatibility.isDeepAbyss14(executable)) {
                    midi = new MophunMidi(audioCache);
                    midi.speed(clock.speed());
                    midi.pause(paused);
                    sdk.midiBackend = midi;
                    sdk.soundEnabled = true;
                }
            }
            sdk.deviceModel = model;
            sdk.deviceVendor = 3;
            sdk.saveDir = saves;
            sdk.requireExplicitFileCreation = explicitCreation;
            vm.sdk = sdk;
            screen = target;
            checkpoint();
            vm.runConstructors();
            while (!stopped && !vm.stopped) {
                checkpoint();
                int flips = target.flips;
                // More guest instructions per scheduling slice; clock and frame pacing stay independent.
                vm.run(500 * cpuBudget);
                if (target.flips == flips) Thread.sleep(1);
            }
        } catch (SessionStopped ignored) {
            // A host stop can unwind a blocking SDK dialog as well as the VM loop.
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Throwable failure) {
            error = failure;
        } finally {
            if (sdk != null) {
                try { sdk.flushSaves(); } catch (Throwable failure) { if (error == null) error = failure; }
                sdk.disposeAudio();
            }
            boolean interrupted = Thread.interrupted();
            try { if (midi != null) midi.close(); }
            catch (Exception failure) { if (error == null) error = failure; }
            finally { if (interrupted) Thread.currentThread().interrupt(); }
            parked = true;
            finished = true;
        }
    }

    public synchronized void stop() {
        if (stopped) return;
        stopped = true;
        if (midi != null) midi.pause(true);
        if (screen != null) screen.clearButtons();
        worker.interrupt();
    }

    @Override public void close() throws InterruptedException {
        stop();
        worker.join(3000);
        if (worker.isAlive()) throw new IllegalStateException("Mophun worker did not stop");
    }
}
