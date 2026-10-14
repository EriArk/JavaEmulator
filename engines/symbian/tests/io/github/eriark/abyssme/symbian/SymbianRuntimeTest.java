package io.github.eriark.abyssme.symbian;

import android.content.Context;
import android.content.Intent;
import android.app.UiAutomation;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import android.os.Bundle;
import androidx.test.platform.app.InstrumentationRegistry;
import com.github.eka2l1.emu.Emulator;
import java.io.File;
import java.nio.file.Files;
import org.junit.Test;
import org.junit.Assume;
import static org.junit.Assert.*;

public final class SymbianRuntimeTest {
    @Test public void retainedNgageImportOpensPlatformWithoutRestaging() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("handoffUi")));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        UiAutomation ui = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        SystemFileCatalog catalog = new SystemFileCatalog(context);
        File active = catalog.activeRoot(SymbianFiles.root(context));
        SystemFileCatalog.Entry previous = null;
        for (SystemFileCatalog.Entry entry : catalog.list()) if (catalog.runtime(entry).equals(active)) previous = entry;
        assertNotNull(previous);
        PendingGameImport pending = new PendingGameImport(new File(context.getFilesDir(), "symbian-game-import"));
        Assume.assumeFalse(pending.exists());
        try (GameImport game = GameImport.inspect(permittedInput(context, "asphalt3.n-gage"))) {
            java.util.List<SystemFileCatalog.Entry> choices = catalog.candidates(game.kind, game.hash);
            assertEquals(1, choices.size());
            pending.begin(game, "Asphalt 3 - resume installer");
            pending.update(pending.read(), choices.get(0).id, PendingGameImport.Step.GUEST);
            File staged = new File(catalog.runtime(choices.get(0)), "data/drives/rm-320/e/n-gage/" + game.hash + ".n-gage");
            assertFalse("Existing guest has already consumed its input", staged.exists());
            context.startActivity(new Intent(context, SymbianActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitText(ui, "phones available", 30000);
            clickExact(ui, "Pending import"); clickExact(ui, "Continue import");
            SystemClock.sleep(30000);
            AccessibilityNodeInfo root = ui.getRootInActiveWindow();
            assertNotNull(root); assertEquals(context.getPackageName(), root.getPackageName().toString());
            assertTrue(root.findAccessibilityNodeInfosByText("Import phone").isEmpty());
            saveScreen(ui, context, "symbian-import-ngage-handoff.png");
            assertFalse("Continuing must not requeue the package", staged.exists());
            long now = SystemClock.uptimeMillis();
            ui.injectInputEvent(new android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_DOWN, 4, 0), true);
            ui.injectInputEvent(new android.view.KeyEvent(now, SystemClock.uptimeMillis(), android.view.KeyEvent.ACTION_UP, 4, 0), true);
            clickExact(ui, "Library");
            clickExact(ui, "Leave without saving");
            waitText(ui, "N-Gage installation", 15000);
            saveScreen(ui, context, "symbian-import-ngage-return.png");
            clickExact(ui, "Keep for later");
            assertEquals(PendingGameImport.Step.GUEST, pending.read().step);
            clickExact(ui, "Library"); SystemClock.sleep(1000);
        } finally {
            pending.discard(); catalog.activate(previous);
        }
    }
    @Test public void pendingImportCanReopenCancelSelectionAndRemoveThroughUi() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SystemFileCatalog catalog = new SystemFileCatalog(context);
        PendingGameImport pending = new PendingGameImport(new File(context.getFilesDir(), "symbian-game-import"));
        Assume.assumeFalse(pending.exists());
        File source = File.createTempFile("pending-ui-", ".sis", context.getCacheDir());
        Files.write(source.toPath(), java.nio.ByteBuffer.allocate(20).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(0x10201a7a).array());
        String hash = SystemFileCatalog.hash(source);
        Assume.assumeTrue(catalog.candidates(GameImport.Kind.SIS_MODERN, hash).size() > 1);
        File active = catalog.activeRoot(SymbianFiles.root(context));
        UiAutomation ui = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try (GameImport game = GameImport.inspect(source)) {
            pending.begin(game, "Import workflow test");
            for (int i = 0; i < 2; i++) {
                context.startActivity(new Intent(context, SymbianActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                waitText(ui, "phones available", 30000);
                clickExact(ui, "Pending import"); clickExact(ui, "Continue import");
                waitText(ui, "Choose system for this game", 15000);
                if (i == 0) saveScreen(ui, context, "symbian-import-system-choice.png");
                clickExact(ui, "Later");
                assertEquals(PendingGameImport.Step.READY, pending.read().step);
                assertEquals(active, catalog.activeRoot(SymbianFiles.root(context)));
                if (i == 0) { clickExact(ui, "Library"); SystemClock.sleep(1000); }
            }
            clickExact(ui, "Pending import");
            saveScreen(ui, context, "symbian-import-pending.png");
            clickExact(ui, "Remove pending import"); clickExact(ui, "Remove");
            waitText(ui, "phones available", 5000);
            assertFalse(pending.exists()); assertTrue(source.isFile());
            clickExact(ui, "Library");
        } finally {
            PendingGameImport.Entry job = pending.read();
            if (job != null && job.hash.equals(hash)) pending.discard();
            Files.deleteIfExists(source.toPath());
        }
    }
    @Test public void importPreparedArchiveThroughPicker() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("preparedUi")));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        UiAutomation ui = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        SystemFileCatalog catalog = new SystemFileCatalog(context);
        File active = catalog.activeRoot(SymbianFiles.root(context));
        context.startActivity(new Intent(context, SymbianActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitText(ui, "phones available", 30000);
        saveScreen(ui, context, "symbian-collection-library.png");
        clickExact(ui, "Add system files");
        // The external device driver selects the private fixture in Android's real SAF picker.
        waitText(ui, "System import", 180000);
        assertNotNull(waitText(ui, "1 already available", 5000));
        saveScreen(ui, context, "symbian-collection-import.png");
        assertTrue(waitText(ui, "Close", 5000).performAction(AccessibilityNodeInfo.ACTION_CLICK));
        clickExact(ui, "Installed phones");
        saveScreen(ui, context, "symbian-collection-catalog.png");
        assertTrue(waitText(ui, "Close", 5000).performAction(AccessibilityNodeInfo.ACTION_CLICK));
        assertEquals(active, catalog.activeRoot(SymbianFiles.root(context)));
        assertNotNull(waitText(ui, "phones available", 5000));
        assertTrue(waitText(ui, "Library", 5000).performAction(AccessibilityNodeInfo.ACTION_CLICK));
    }
    private static void clickExact(UiAutomation ui, String text) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = ui.getRootInActiveWindow();
            if (root != null) for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text))
                if (node.isClickable() && node.getText() != null && text.equalsIgnoreCase(node.getText().toString())
                        && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return;
            SystemClock.sleep(100);
        }
        fail("Missing clickable action: " + text);
    }
    @Test public void selectPreloadedSystemThroughUi() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("catalogUi")));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        UiAutomation ui = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        context.startActivity(new Intent(context, SymbianActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitText(ui, "phones available", 30000);
        ui.waitForIdle(700, 5000);
        clickExact(ui, "Installed phones");
        AccessibilityNodeInfo legacy = waitText(ui, "RH-29", 5000);
        assertNotNull(waitText(ui, "RM-320", 5000));
        saveScreen(ui, context, "symbian-system-catalog.png");
        assertTrue(legacy.performAction(AccessibilityNodeInfo.ACTION_CLICK));
        waitText(ui, "phones available", 15000);
        ui.waitForIdle(700, 5000);
        saveScreen(ui, context, "symbian-system-snakes.png");
        assertTrue(waitText(ui, "Library", 5000).performAction(AccessibilityNodeInfo.ACTION_CLICK));
        SystemClock.sleep(700);
        context.startActivity(new Intent(context, SymbianActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitText(ui, "phones available", 15000);
        ui.waitForIdle(700, 5000);
        assertTrue(waitText(ui, "Library", 5000).performAction(AccessibilityNodeInfo.ACTION_CLICK));
    }
    private static AccessibilityNodeInfo waitText(UiAutomation ui, String text, long timeout) {
        long deadline = SystemClock.uptimeMillis() + timeout;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = ui.getRootInActiveWindow();
            if (root != null) {
                java.util.List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
                if (nodes.isEmpty() && "phones available".equals(text)) nodes = root.findAccessibilityNodeInfosByText("1 phone available");
                if (!nodes.isEmpty()) return nodes.get(0);
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Missing UI text: " + text);
    }
    private static void saveScreen(UiAutomation ui, Context context, String name) throws Exception {
        ui.waitForIdle(700, 5000);
        android.graphics.Bitmap image = ui.takeScreenshot();
        assertNotNull(image);
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(new File(context.getExternalFilesDir(null), name))) {
            assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out));
        } finally { image.recycle(); }
    }
    @Test public void catalogProvidedSystemsAndGames() throws Exception {
        Bundle args = InstrumentationRegistry.getArguments();
        Assume.assumeTrue("true".equals(args.getString("catalogFixtures")));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SystemFileCatalog catalog = new SystemFileCatalog(context);
        try {
            for (String archive : new String[]{"ngage.zip", "n95.zip"}) {
                SystemFileCatalog.Entry entry = catalog.add(context, java.util.Collections.singletonList(permittedInput(context, archive)));
                assertTrue(entry.firmware.equals("RH-29") || entry.firmware.equals("RM-320"));
                Emulator.prepare(catalog.runtime(entry).getAbsolutePath());
                assertEquals(entry.firmware, Emulator.getDeviceInfo()[0]);
                Emulator.stopSession();
            }
            try (GameImport card = GameImport.inspect(permittedInput(context, "snakes.zip"));
                 GameImport packageFile = GameImport.inspect(permittedInput(context, "asphalt3.n-gage"))) {
                java.util.List<SystemFileCatalog.Entry> legacy = catalog.candidates(card.kind, card.hash);
                java.util.List<SystemFileCatalog.Entry> modern = catalog.candidates(packageFile.kind, packageFile.hash);
                assertEquals(1, legacy.size()); assertEquals(1, modern.size());
                assertEquals("RH-29", legacy.get(0).firmware);
                assertEquals("RM-320", modern.get(0).firmware);
                assertNotEquals(legacy.get(0).id, modern.get(0).id);
                Emulator.prepare(catalog.runtime(legacy.get(0)).getAbsolutePath());
                assertEquals(0, Emulator.installNGageGame(card.file.getAbsolutePath()));
                Emulator.prepare(catalog.runtime(legacy.get(0)).getAbsolutePath());
                assertTrue(java.util.Arrays.asList(Emulator.getCatalogApps()).contains("Snakes"));
                catalog.bind(card.hash, legacy.get(0));
                catalog.activate(legacy.get(0));
                if ("true".equals(args.getString("catalogModern"))) {
                    File runtime = catalog.runtime(modern.get(0));
                    Emulator.stopSession();
                    Emulator.prepare(runtime.getAbsolutePath());
                    if (!java.util.Arrays.asList(Emulator.getCatalogApps()).contains("536902457"))
                        assertEquals(0, Emulator.installApp(permittedInput(context, "ngage2.sis").getAbsolutePath()));
                    Emulator.prepare(runtime.getAbsolutePath());
                    assertTrue(java.util.Arrays.asList(Emulator.getCatalogApps()).contains("536902457"));
                    File drive = new File(Emulator.driveE());
                    String name = packageFile.hash + ".n-gage";
                    File destination = new File(drive, "n-gage/" + name);
                    if (!destination.exists()) SymbianFiles.stageCardPackage(runtime, drive, packageFile.file, name);
                    assertEquals(packageFile.hash, SystemFileCatalog.hash(destination));
                    catalog.bind(packageFile.hash, modern.get(0));
                    catalog.activate(modern.get(0));
                }
            }
        } finally { Emulator.stopSession(); }
    }

    @Test public void fallbackFontDoesNotReplaceAnExistingFile() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File root = Files.createTempDirectory(context.getCacheDir().toPath(), "font-fallback-").toFile();
        File source = new File(root, "source.ttf"), target = new File(root, "fonts/fallback.ttf");
        Files.write(source.toPath(), new byte[]{1, 2, 3});
        SymbianFiles.retainFallbackFont(source, target);
        Files.write(source.toPath(), new byte[]{4, 5, 6});
        SymbianFiles.retainFallbackFont(source, target);
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(target.toPath()));
        assertArrayEquals(new String[]{"fallback.ttf"}, target.getParentFile().list());
    }

    @Test public void phoneZipExtractsOnlyOneSystemPair() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File root = Files.createTempDirectory(context.getCacheDir().toPath(), "phone-zip-").toFile();
        File archive = new File(root, "phone.zip");
        writeZip(archive, "Data/rom/SYM.ROM", "Phone.RPKG", "Data/config.yml", "Data/drives/c/save.dat");
        File extracted = new File(root, "extracted");
        assertTrue(extracted.mkdir());
        java.util.List<File> inputs = SymbianFiles.extractPhoneInputs(archive, extracted);
        assertEquals(2, inputs.size());
        assertEquals(2, extracted.list().length);
        assertTrue(inputs.get(0).getName().endsWith(".rom"));
        assertTrue(inputs.get(1).getName().endsWith(".rpkg"));
        for (File file : inputs) assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(file.toPath()));
    }

    @Test public void phoneZipRejectsAmbiguityAndUnsafePathsWithoutLeavingFiles() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File root = Files.createTempDirectory(context.getCacheDir().toPath(), "phone-invalid-").toFile();
        File archive = new File(root, "phone.zip");
        File extracted = new File(root, "extracted");
        assertTrue(extracted.mkdir());
        for (String[] names : new String[][]{
                {"first.rom", "second.ROM"}, {"first.rom", "one.rpkg", "two.RPKG"},
                {"only.rpkg"}, {"phone.rom", "../escape"}, {"phone.rom", "..\\escape"},
                {"phone.rom", "/absolute"}, {"phone.rom", "C:/drive"}}) {
            writeZip(archive, names);
            try { SymbianFiles.extractPhoneInputs(archive, extracted); fail(java.util.Arrays.toString(names)); }
            catch (java.io.IOException expected) { }
            assertEquals("Failed extraction must leave no partial phone", 0, extracted.list().length);
        }
        Files.write(archive.toPath(), new byte[]{0, 1, 2});
        try { SymbianFiles.extractPhoneInputs(archive, extracted); fail("Malformed ZIP"); }
        catch (java.io.IOException expected) { }
        assertEquals(0, extracted.list().length);
        byte[] data = new byte[]{1, 2, 3};
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(data);
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(archive))) {
            java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry("phone.rom");
            entry.setMethod(java.util.zip.ZipEntry.STORED);
            entry.setSize(data.length);
            entry.setCrc(crc.getValue());
            zip.putNextEntry(entry);
            zip.write(data);
            zip.closeEntry();
        }
        // Corrupt a stored entry while leaving its central-directory checksum intact.
        try (java.io.RandomAccessFile file = new java.io.RandomAccessFile(archive, "rw")) {
            file.seek(30 + "phone.rom".length());
            file.write(99);
        }
        try { SymbianFiles.extractPhoneInputs(archive, extracted); fail("Corrupt payload"); }
        catch (java.io.IOException expected) { }
        assertEquals(0, extracted.list().length);
    }

    @Test public void inspectProvidedPhoneArchive() throws Exception {
        Bundle args = InstrumentationRegistry.getArguments();
        Assume.assumeTrue(args.containsKey("phoneArchive"));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File extracted = Files.createTempDirectory(context.getCacheDir().toPath(), "phone-private-").toFile();
        java.util.List<File> inputs = SymbianFiles.extractPhoneInputs(
                permittedInput(context, args.getString("phoneArchive")), extracted);
        try {
            assertEquals(Integer.parseInt(args.getString("inputCount", "2")), inputs.size());
            File rom = null, rpkg = null;
            for (File file : inputs) {
                if (file.getName().endsWith(".rom")) rom = file;
                if (file.getName().endsWith(".rpkg")) rpkg = file;
            }
            assertNotNull(rom);
            SymbianFiles.prepareAssets(context);
            Emulator.prepare(SymbianFiles.root(context).getAbsolutePath());
            if (Emulator.doesRomNeedRPKG(rom.getAbsolutePath())) assertNotNull(rpkg);
        } finally {
            Emulator.stopSession();
            for (File input : inputs) Files.deleteIfExists(input.toPath());
            Files.deleteIfExists(extracted.toPath());
        }
    }

    private static void writeZip(File file, String... names) throws Exception {
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(file))) {
            for (String name : names) {
                zip.putNextEntry(new java.util.zip.ZipEntry(name));
                zip.write(new byte[]{1, 2, 3});
                zip.closeEntry();
            }
        }
    }

    @Test public void installProvidedModernPhoneAndPlatform() throws Exception {
        Bundle args = InstrumentationRegistry.getArguments();
        Assume.assumeTrue(args.containsKey("modernRom") && args.containsKey("platform"));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SymbianFiles.prepareAssets(context);
        try {
            Emulator.prepare(SymbianFiles.root(context).getAbsolutePath());
            if (!"true".equals(args.getString("reusePhone"))) {
                assertEquals("Modern firmware install", 0, Emulator.installDevice(
                        permittedInput(context, args.getString("modernRpkg")).getAbsolutePath(),
                        permittedInput(context, args.getString("modernRom")).getAbsolutePath(), true, true));
                Emulator.prepare(SymbianFiles.root(context).getAbsolutePath());
            }
            String[] codes = Emulator.getDeviceFirmwareCodes();
            int selected = java.util.Arrays.asList(codes).indexOf(args.getString("firmware", "RM-320"));
            assertTrue("Installed firmware: " + java.util.Arrays.toString(codes), selected >= 0);
            Emulator.setCurrentDevice(selected, false);
            Emulator.prepare(SymbianFiles.root(context).getAbsolutePath());
            if (!"true".equals(args.getString("reusePlatform")))
                assertEquals("N-Gage platform install", 0,
                        Emulator.installApp(permittedInput(context, args.getString("platform")).getAbsolutePath()));
            Emulator.prepare(SymbianFiles.root(context).getAbsolutePath());
            String[] apps = Emulator.getCatalogApps();
            assertTrue("Platform must appear: " + java.util.Arrays.toString(apps) + "\n" + Emulator.environmentReport(),
                    java.util.Arrays.asList(apps).contains("536902457"));
            if (args.containsKey("package")) {
                File source = permittedInput(context, args.getString("package"));
                String drive = Emulator.driveE();
                assertNotNull(drive);
                File target = new File(drive, "n-gage/" + source.getName());
                if (!target.exists()) SymbianFiles.stageCardPackage(SymbianFiles.root(context), new File(drive), source, source.getName());
                assertEquals(source.length(), target.length());
            }
        } finally { Emulator.stopSession(); }
    }

    @Test public void environmentPageCanReopenInSeparateProcess() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        UiAutomation ui = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        for (int i = 0; i < 3; i++) {
            context.startActivity(new Intent(context, SymbianActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            long deadline = SystemClock.uptimeMillis() + 30000;
            boolean ready = false;
            while (SystemClock.uptimeMillis() < deadline) {
                AccessibilityNodeInfo root = ui.getRootInActiveWindow();
                if (root != null && context.getPackageName().contentEquals(root.getPackageName())
                        && !root.findAccessibilityNodeInfosByText("Add system files").isEmpty()
                        && (!root.findAccessibilityNodeInfosByText("No phone files added").isEmpty()
                        || !root.findAccessibilityNodeInfosByText("phones available").isEmpty()
                        || !root.findAccessibilityNodeInfosByText("1 phone available").isEmpty())) {
                    ready = true;
                    break;
                }
                SystemClock.sleep(100);
            }
            assertTrue("Separate-process environment must finish preparing", ready);
            ui.waitForIdle(700, 5000);
            SystemClock.sleep(300);
            if (i == 2) {
                android.graphics.Bitmap screenshot = ui.takeScreenshot();
                assertNotNull(screenshot);
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(
                        new File(context.getExternalFilesDir(null), "symbian-environment.png"))) {
                    assertTrue(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out));
                } finally { screenshot.recycle(); }
            }
            AccessibilityNodeInfo root = ui.getRootInActiveWindow();
            assertNotNull(root);
            java.util.List<AccessibilityNodeInfo> exit = root.findAccessibilityNodeInfosByText("Library");
            assertFalse(exit.isEmpty());
            assertTrue(exit.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK));
            deadline = SystemClock.uptimeMillis() + 5000;
            boolean closed = false;
            while (SystemClock.uptimeMillis() < deadline) {
                root = ui.getRootInActiveWindow();
                if (root != null && root.findAccessibilityNodeInfosByText("Add system files").isEmpty()) {
                    closed = true;
                    break;
                }
                SystemClock.sleep(100);
            }
            assertTrue("Environment must leave the foreground", closed);
            ui.waitForIdle(700, 5000);
        }
    }

    @Test public void engineAssetsCanBePreparedTwice() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SymbianFiles.prepareAssets(context);
        SymbianFiles.prepareAssets(context);
        assertTrue(new File(SymbianFiles.root(context), "resources").isDirectory());
    }

    @Test public void cardStagingPreservesExistingPackageAndRejectsEscapes() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File root = Files.createTempDirectory(context.getCacheDir().toPath(), "symbian-card-").toFile();
        File source = new File(root, "source");
        Files.write(source.toPath(), new byte[]{1, 2, 3});
        File drive = new File(root, "e");
        SymbianFiles.stageCardPackage(root, drive, source, "test.n-gage");
        File installed = new File(drive, "n-gage/test.n-gage");
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(installed.toPath()));
        for (String name : new String[]{"test.n-gage", "../escape.n-gage", "..\\escape.n-gage", "/escape.n-gage"}) {
            try { SymbianFiles.stageCardPackage(root, drive, source, name); fail(name); }
            catch (java.io.IOException expected) { }
        }
        try { SymbianFiles.stageCardPackage(root, root.getParentFile(), source, "escape.n-gage"); fail("Outside root"); }
        catch (java.io.IOException expected) { }
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(installed.toPath()));
        assertArrayEquals(new String[]{"test.n-gage"}, new File(drive, "n-gage").list());
    }

    @Test public void emptyEnvironmentCanPrepareAndStopRepeatedly() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        for (int i = 0; i < 3; i++) {
            File root = Files.createTempDirectory(context.getCacheDir().toPath(), "symbian-boot-").toFile();
            try {
                Emulator.prepare(root.getAbsolutePath());
                assertEquals(0, Emulator.getDevices().length);
                assertFalse(Emulator.startSession());
                assertNull(Emulator.driveE());
            } finally { Emulator.stopSession(); }
        }
    }

    /** Games and firmware are provisioned outside the APK by the maintainer. */
    @Test public void installProvidedPhoneAndCard() throws Exception {
        Bundle args = InstrumentationRegistry.getArguments();
        Assume.assumeTrue(args.containsKey("rom") && args.containsKey("card"));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File rom = permittedInput(context, args.getString("rom"));
        File card = permittedInput(context, args.getString("card"));
        String rpkg = args.containsKey("rpkg") ? permittedInput(context, args.getString("rpkg")).getAbsolutePath() : "";
        SymbianFiles.prepareAssets(context);
        try {
            Emulator.prepare(SymbianFiles.root(context).getAbsolutePath());
            if (!"true".equals(args.getString("reusePhone")))
                assertEquals("Firmware install", 0, Emulator.installDevice(rpkg, rom.getAbsolutePath(), true, true));
            Emulator.prepare(SymbianFiles.root(context).getAbsolutePath());
            assertTrue(Emulator.getDevices().length > 0);
            String[] codes = Emulator.getDeviceFirmwareCodes();
            int selected = java.util.Arrays.asList(codes).indexOf(args.getString("firmware", "RH-29"));
            assertTrue("Installed firmware: " + java.util.Arrays.toString(codes), selected >= 0);
            Emulator.setCurrentDevice(selected, false);
            Emulator.prepare(SymbianFiles.root(context).getAbsolutePath());
            assertEquals("N-Gage card install", 0, Emulator.installNGageGame(card.getAbsolutePath()));
            Emulator.prepare(SymbianFiles.root(context).getAbsolutePath());
            String[] apps = Emulator.getCatalogApps();
            android.util.Log.i("AbyssSymbianTest", Emulator.environmentReport());
            assertTrue("Installed app must be catalogued\n" + Emulator.environmentReport(), apps.length >= 2);
            for (int i = 1; i < apps.length; i += 2) assertFalse("App title must not be blank", apps[i].trim().isEmpty());
            android.util.Log.i("AbyssSymbianTest", java.util.Arrays.toString(apps));
        } finally { Emulator.stopSession(); }
    }

    @Test public void launchProvidedGame() throws Exception {
        Bundle args = InstrumentationRegistry.getArguments();
        Assume.assumeTrue(args.containsKey("launchTitle"));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        UiAutomation ui = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        context.startActivity(new Intent(context, SymbianActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        String title = args.getString("launchTitle");
        waitText(ui, "Phone tools", 30000);
        clickExact(ui, "Phone tools");
        long deadline = SystemClock.uptimeMillis() + 30000;
        AccessibilityNodeInfo game = null;
        while (SystemClock.uptimeMillis() < deadline && game == null) {
            AccessibilityNodeInfo root = ui.getRootInActiveWindow();
            if (root != null) for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(title)) {
                if (title.contentEquals(node.getText()) && node.isClickable()) { game = node; break; }
            }
            if (game == null) SystemClock.sleep(100);
        }
        assertNotNull("Installed game must appear: " + title, game);
        ui.waitForIdle(500, 5000);
        assertTrue(game.performAction(AccessibilityNodeInfo.ACTION_CLICK));
        int seconds = Integer.parseInt(args.getString("seconds", "15"));
        String[] keys = args.getString("keys", "").split(",");
        for (int i = 0; i < seconds; i++) {
            SystemClock.sleep(1000);
            if (i < keys.length && !keys[i].isEmpty()) {
                int code = Integer.parseInt(keys[i]);
                long now = SystemClock.uptimeMillis();
                ui.injectInputEvent(new android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_DOWN,
                        code, 0), true);
                SystemClock.sleep(100);
                ui.injectInputEvent(new android.view.KeyEvent(now, SystemClock.uptimeMillis(),
                        android.view.KeyEvent.ACTION_UP, code, 0), true);
            }
            if (i == 2 || i == seconds - 1 || ("true".equals(args.getString("stepScreens")) && i % 5 == 0)) {
                android.graphics.Bitmap shot = ui.takeScreenshot();
                assertNotNull(shot);
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(new File(context.getExternalFilesDir(null),
                        "symbian-game-" + i + ".png"))) {
                    assertTrue(shot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out));
                } finally { shot.recycle(); }
            }
        }
        AccessibilityNodeInfo root = ui.getRootInActiveWindow();
        assertNotNull(root);
        assertEquals(context.getPackageName(), root.getPackageName().toString());
        assertTrue("Game must not have returned to setup", root.findAccessibilityNodeInfosByText("Add system files").isEmpty());
        // A captured frame is evidence for visual inspection, not an automatic gameplay pass.
        if (!"true".equals(args.getString("stay"))) {
            long now = SystemClock.uptimeMillis();
            ui.injectInputEvent(new android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_DOWN, 4, 0), true);
            ui.injectInputEvent(new android.view.KeyEvent(now, SystemClock.uptimeMillis(), android.view.KeyEvent.ACTION_UP, 4, 0), true);
            SystemClock.sleep(700);
            root = ui.getRootInActiveWindow();
            java.util.List<AccessibilityNodeInfo> exit = root.findAccessibilityNodeInfosByText("Library");
            assertFalse(exit.isEmpty());
            assertTrue(exit.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK));
            clickExact(ui, "Leave without saving");
            deadline = SystemClock.uptimeMillis() + 15000;
            boolean returned = false;
            while (SystemClock.uptimeMillis() < deadline) {
                root = ui.getRootInActiveWindow();
                if (root != null && !root.findAccessibilityNodeInfosByText("Add system files").isEmpty()
                        && root.findAccessibilityNodeInfosByText("Closing game").isEmpty()) { returned = true; break; }
                SystemClock.sleep(100);
            }
            assertTrue("Active guest must stop and return to its catalog", returned);
        }
    }
    private static File permittedInput(Context context, String name) throws Exception {
        File root = new File(context.getExternalFilesDir(null), "symbian-input").getCanonicalFile();
        File file = new File(root, name).getCanonicalFile();
        if (!file.toPath().startsWith(root.toPath()) || !file.isFile()) throw new IllegalArgumentException("Missing private input: " + name);
        return file;
    }
}
