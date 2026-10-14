package io.github.eriark.abyssme.symbian;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import org.junit.Test;
import org.junit.Assume;
import static org.junit.Assert.*;

public final class GameInstallFlowTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private File root() throws Exception { return Files.createTempDirectory(context.getCacheDir().toPath(), "import-flow-").toFile(); }
    private File input(File root, GameImport.Kind kind) throws Exception {
        File input = new File(root, "input");
        byte[] data = kind == GameImport.Kind.NGAGE2
                ? "Content-Type: multipart/mixed; boundary=test\r\nContent-Description: Ngage-Install-File\r\n".getBytes(StandardCharsets.US_ASCII)
                : ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN).putInt(0x10201a7a).array();
        Files.write(input.toPath(), data); return input;
    }
    private PendingGameImport begin(File root, GameImport.Kind kind) throws Exception {
        PendingGameImport pending = new PendingGameImport(new File(root, "pending"));
        try (GameImport game = GameImport.inspect(input(root, kind))) { pending.begin(game, "Test game"); }
        return pending;
    }
    private File platform(File root) throws Exception {
        File input = new File(root, "platform.sis");
        Files.write(input.toPath(), ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(0x10201a7a).putInt(0).putInt(0x20003b78).array());
        return input;
    }
    private SystemFileCatalog.Entry phone(File root, SystemFileCatalog catalog, int id, String code, String os) throws Exception {
        File rom = new File(root, "test.rom"); Files.write(rom.toPath(), new byte[]{(byte)id});
        return catalog.add(Collections.singletonList(rom), (runtime, r, p) -> {
            File data = new File(runtime, "data"); assertTrue(data.mkdirs());
            Files.write(new File(data, "devices.yml").toPath(), new byte[]{1});
            return new String[]{code, "Test phone", os};
        });
    }
    private static final class Backend implements GameInstallFlow.Backend {
        int installs, stages, opens;
        boolean platform, fail, installPlatform;
        String selected;
        @Override public void open(SystemFileCatalog.Entry entry) { selected = entry.id; opens++; }
        @Override public boolean hasPlatform() { return platform; }
        @Override public void install(File file, GameImport.Kind kind) throws IOException {
            installs++; if (fail) throw new IOException("Interrupted");
            platform |= installPlatform;
        }
        @Override public void stage(File file, String hash) { stages++; }
    }
    @Test public void missingSystemSurvivesRecreationAndResumesAfterCompatibleImport() throws Exception {
        File root = root(); PendingGameImport pending = begin(root, GameImport.Kind.SIS_MODERN);
        SystemFileCatalog catalog = new SystemFileCatalog(new File(root, "systems")); Backend backend = new Backend();
        assertEquals(GameInstallFlow.Action.SYSTEM, new GameInstallFlow(pending, catalog, backend).resume(null, false).action);
        Files.delete(new File(root, "input").toPath());
        pending = new PendingGameImport(new File(root, "pending")); pending.recover();
        phone(root, catalog, 1, "RH-29", "epoc6");
        assertEquals(GameInstallFlow.Action.SYSTEM, new GameInstallFlow(pending, catalog, backend).resume(null, false).action);
        assertEquals(0, backend.installs);
        SystemFileCatalog.Entry modern = phone(root, catalog, 2, "RM-320", "epoc93fp1");
        String hash = pending.read().hash;
        assertEquals(GameInstallFlow.Action.DONE, new GameInstallFlow(pending, catalog, backend).resume(null, false).action);
        assertEquals(1, backend.installs); assertEquals(modern.id, backend.selected);
        assertFalse(pending.exists()); assertEquals(modern.id, catalog.candidates(GameImport.Kind.SIS_MODERN, hash).get(0).id);
    }
    @Test public void platformUsesLockedSystemAndHandsOffOnlyAfterActualRegistration() throws Exception {
        File root = root(); PendingGameImport pending = begin(root, GameImport.Kind.NGAGE2);
        SystemFileCatalog catalog = new SystemFileCatalog(new File(root, "systems")); Backend backend = new Backend();
        SystemFileCatalog.Entry first = phone(root, catalog, 1, "RM-320", "epoc93fp1");
        GameInstallFlow flow = new GameInstallFlow(pending, catalog, backend);
        assertEquals(GameInstallFlow.Action.PLATFORM, flow.resume(null, false).action);
        assertEquals(0, backend.stages);
        SystemFileCatalog.Entry second = phone(root, catalog, 2, "RM-320", "epoc93fp1"); catalog.activate(second);
        try (GameImport wrong = GameImport.inspect(input(root, GameImport.Kind.NGAGE2))) {
            try { flow.addPlatform(wrong); fail(); } catch (IOException expected) { }
        }
        assertEquals(0, backend.installs);
        try (GameImport wrong = GameImport.inspect(input(root, GameImport.Kind.SIS_MODERN))) {
            try { flow.addPlatform(wrong); fail(); } catch (IOException expected) { }
        }
        assertEquals(0, backend.installs);
        try (GameImport platform = GameImport.inspect(platform(root))) {
            try { flow.addPlatform(platform); fail(); } catch (IOException expected) { }
            assertEquals(PendingGameImport.Step.INSTALLING, pending.read().step);
            assertEquals(GameInstallFlow.Action.RETRY, flow.resume(null, false).action);
            assertEquals(0, backend.stages);
            backend.installPlatform = true;
            assertEquals(GameInstallFlow.Action.OPEN_PLATFORM, flow.addPlatform(platform).action);
        }
        assertEquals(first.id, backend.selected); assertEquals(1, backend.stages);
        assertEquals(PendingGameImport.Step.GUEST, pending.read().step);
        assertEquals(GameInstallFlow.Action.OPEN_PLATFORM, flow.resume(null, false).action);
        assertEquals("Never restage a consumed guest package", 1, backend.stages);
    }
    @Test public void existingPlatformIsReusedWithoutInstallingAnything() throws Exception {
        File root = root(); PendingGameImport pending = begin(root, GameImport.Kind.NGAGE2);
        SystemFileCatalog catalog = new SystemFileCatalog(new File(root, "systems")); Backend backend = new Backend();
        phone(root, catalog, 1, "RM-320", "epoc93fp1"); backend.platform = true;
        assertEquals(GameInstallFlow.Action.OPEN_PLATFORM, new GameInstallFlow(pending, catalog, backend).resume(null, false).action);
        assertEquals(0, backend.installs); assertEquals(1, backend.stages);
    }
    @Test public void preloadedPlatformIsReusedAndFailuresRequireExplicitRetry() throws Exception {
        File root = root(); PendingGameImport pending = begin(root, GameImport.Kind.NGAGE2);
        SystemFileCatalog catalog = new SystemFileCatalog(new File(root, "systems")); Backend backend = new Backend();
        phone(root, catalog, 1, "RM-320", "epoc93fp1");
        PlatformStore store = new PlatformStore(new File(root, "platforms"));
        try (GameImport platform = GameImport.inspect(platform(root))) { store.save(platform); }
        Files.delete(new File(root, "platform.sis").toPath());
        GameInstallFlow flow = new GameInstallFlow(pending, catalog, backend, store);
        try { flow.resume(null, false); fail(); } catch (IOException expected) { }
        assertEquals(1, backend.installs); assertEquals(0, backend.stages);
        assertEquals(GameInstallFlow.Action.RETRY, flow.resume(null, false).action);
        assertEquals(1, backend.installs);
        backend.installPlatform = true;
        assertEquals(GameInstallFlow.Action.OPEN_PLATFORM, flow.resume(null, true).action);
        assertEquals(2, backend.installs); assertEquals(1, backend.stages);
        assertEquals(GameInstallFlow.Action.OPEN_PLATFORM, flow.resume(null, false).action);
        assertEquals(2, backend.installs); assertEquals(1, backend.stages);
    }
    @Test public void interruptedInstallerNeedsExplicitRetry() throws Exception {
        File root = root(); PendingGameImport pending = begin(root, GameImport.Kind.SIS_MODERN);
        SystemFileCatalog catalog = new SystemFileCatalog(new File(root, "systems")); Backend backend = new Backend();
        phone(root, catalog, 1, "RM-320", "epoc93fp1"); backend.fail = true;
        GameInstallFlow flow = new GameInstallFlow(pending, catalog, backend);
        try { flow.resume(null, false); fail(); } catch (IOException expected) { }
        pending = new PendingGameImport(new File(root, "pending"));
        assertEquals(PendingGameImport.Step.INSTALLING, pending.read().step);
        flow = new GameInstallFlow(pending, catalog, backend);
        assertEquals(GameInstallFlow.Action.RETRY, flow.resume(null, false).action); assertEquals(1, backend.installs);
        backend.fail = false;
        assertEquals(GameInstallFlow.Action.DONE, flow.resume(null, true).action); assertEquals(2, backend.installs);
    }
    @Test public void ambiguousSelectionStaysPendingAndCannotChangeAfterLock() throws Exception {
        File root = root(); PendingGameImport pending = begin(root, GameImport.Kind.NGAGE2);
        SystemFileCatalog catalog = new SystemFileCatalog(new File(root, "systems")); Backend backend = new Backend();
        SystemFileCatalog.Entry first = phone(root, catalog, 1, "RM-320", "epoc93fp1");
        SystemFileCatalog.Entry second = phone(root, catalog, 2, "RM-320", "epoc93fp1");
        GameInstallFlow flow = new GameInstallFlow(pending, catalog, backend);
        assertEquals(2, flow.resume(null, false).choices.size()); assertEquals(0, backend.opens);
        assertEquals(GameInstallFlow.Action.PLATFORM, flow.resume(first.id, false).action);
        try { flow.resume(second.id, false); fail(); } catch (IOException expected) { }
        assertEquals(first.id, pending.read().system); assertEquals(0, backend.installs);
    }
    @Test public void changedPayloadAndMissingSelectedSystemDoNotFallBackOrInstall() throws Exception {
        File root = root(); PendingGameImport pending = begin(root, GameImport.Kind.SIS_MODERN);
        SystemFileCatalog catalog = new SystemFileCatalog(new File(root, "systems")); Backend backend = new Backend();
        SystemFileCatalog.Entry system = phone(root, catalog, 1, "RM-320", "epoc93fp1");
        pending.update(pending.read(), system.id, PendingGameImport.Step.READY);
        Files.delete(new File(catalog.runtime(system), "data/devices.yml").toPath());
        GameInstallFlow flow = new GameInstallFlow(pending, catalog, backend);
        try { flow.resume(null, false); fail(); } catch (IOException expected) { }
        Files.write(pending.payload().toPath(), new byte[]{9});
        try { flow.resume(null, false); fail(); } catch (IOException expected) { }
        assertEquals(0, backend.opens); assertEquals(0, backend.installs); assertTrue(pending.exists());
    }
    @Test public void duplicateBeginDoesNotReplacePendingAndCleanupDoesNotFollowLinks() throws Exception {
        File root = root(); PendingGameImport pending = begin(root, GameImport.Kind.NGAGE2);
        String hash = pending.read().hash;
        try (GameImport next = GameImport.inspect(input(root, GameImport.Kind.SIS_MODERN))) {
            try { pending.begin(next, "Other"); fail(); } catch (IOException expected) { }
        }
        assertEquals(hash, pending.read().hash);
        File outside = new File(root, "keep"); Files.write(outside.toPath(), new byte[]{7});
        File staging = new File(root, "pending/staging-interrupted"); assertTrue(staging.mkdir());
        Files.createSymbolicLink(new File(staging, "link").toPath(), outside.toPath());
        pending.recover(); assertTrue(pending.exists()); assertFalse(staging.exists());
        Files.createSymbolicLink(new File(root, "pending/current/link").toPath(), outside.toPath());
        pending.discard(); assertFalse(pending.exists()); assertTrue(outside.isFile());
        assertTrue(new File(root, "input").isFile());
    }
    @Test public void completedJournalIsCleanedWithoutReinstallAfterRestart() throws Exception {
        File root = root(); PendingGameImport pending = begin(root, GameImport.Kind.SIS_MODERN);
        pending.update(pending.read(), "", PendingGameImport.Step.COMPLETE);
        pending = new PendingGameImport(new File(root, "pending")); pending.recover(); assertFalse(pending.exists());
    }
    @Test public void realPlatformDependencyInIsolatedEnvironment() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("installFlow")));
        File inputs = new File(context.getExternalFilesDir(null), "symbian-input");
        File root = root(); PendingGameImport pending = new PendingGameImport(new File(root, "pending"));
        SystemFileCatalog catalog = new SystemFileCatalog(new File(root, "systems"));
        final File[] runtime = {null};
        GameInstallFlow.Backend backend = new GameInstallFlow.Backend() {
            @Override public void open(SystemFileCatalog.Entry entry) throws Exception {
                com.github.eka2l1.emu.Emulator.stopSession(); runtime[0] = catalog.runtime(entry);
                SymbianFiles.prepareAssets(context, runtime[0]);
                com.github.eka2l1.emu.Emulator.prepare(runtime[0].getAbsolutePath());
            }
            @Override public boolean hasPlatform() { return Arrays.asList(com.github.eka2l1.emu.Emulator.getCatalogApps()).contains("536902457"); }
            @Override public void install(File file, GameImport.Kind kind) throws Exception {
                assertEquals(0, com.github.eka2l1.emu.Emulator.installApp(file.getAbsolutePath()));
                com.github.eka2l1.emu.Emulator.prepare(runtime[0].getAbsolutePath());
            }
            @Override public void stage(File file, String hash) throws Exception {
                SymbianFiles.stageCardPackage(runtime[0], new File(com.github.eka2l1.emu.Emulator.driveE()), file, hash + ".n-gage");
            }
        };
        try (GameImport game = GameImport.inspect(new File(inputs, "asphalt3.n-gage"));
             GameImport platform = GameImport.inspect(new File(inputs, "ngage2.sis"))) {
            PlatformStore store = new PlatformStore(new File(root, "platforms"));
            pending.begin(game, "Asphalt 3"); GameInstallFlow flow = new GameInstallFlow(pending, catalog, backend, store);
            assertEquals(GameInstallFlow.Action.SYSTEM, flow.resume(null, false).action);
            catalog.add(context, Collections.singletonList(new File(inputs, "n95.zip")));
            assertEquals(GameInstallFlow.Action.PLATFORM, flow.resume(null, false).action);
            store.save(platform);
            assertEquals(GameInstallFlow.Action.OPEN_PLATFORM, flow.resume(null, false).action);
            File staged = new File(com.github.eka2l1.emu.Emulator.driveE(), "n-gage/" + game.hash + ".n-gage");
            assertEquals(game.hash, SystemFileCatalog.hash(staged));
            Files.delete(staged.toPath());
            pending = new PendingGameImport(new File(root, "pending"));
            assertEquals(GameInstallFlow.Action.OPEN_PLATFORM, new GameInstallFlow(pending, catalog, backend).resume(null, false).action);
            assertFalse("Guest consumption must not trigger a second installation", staged.exists());
        } finally { com.github.eka2l1.emu.Emulator.stopSession(); }
    }
}
