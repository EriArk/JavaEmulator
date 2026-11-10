// SPDX-License-Identifier: GPL-3.0-only
package ru.playsoftware.j2meloader.catalog;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.lifecycle.ViewModelProvider;
import androidx.sqlite.db.SimpleSQLiteQuery;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import java.util.zip.*;
import org.junit.Test;
import ru.playsoftware.j2meloader.BuildConfig;
import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.appsdb.AppDatabase;
import ru.playsoftware.j2meloader.config.*;
import ru.playsoftware.j2meloader.mophun.MophunStore;
import ru.playsoftware.j2meloader.util.FileUtils;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

/** Opt-in API 30 DocumentsUI journey; private payloads and synthetic save sentinels. */
public class ProvidedLibraryJourneyTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context context = instrumentation.getTargetContext();
    private final UiAutomation ui = instrumentation.getUiAutomation();
    private File root, output;
    private ActivityScenario<LibraryImportActivity> scenario;
    private LibraryImportModel model;
    private ExecutorService worker;

    @Test public void mixedLibrarySurvivesDocumentsUiCancelRetryAndCorruptBackup() throws Exception {
        assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("libraryJourney")));
        assertTrue(context.getPackageName().endsWith(".debug"));
        android.accessibilityservice.AccessibilityServiceInfo accessibility = ui.getServiceInfo();
        accessibility.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        ui.setServiceInfo(accessibility);
        String previous = Config.getEmulatorDir();
        root = Files.createTempDirectory(context.getCacheDir().toPath(), "library-journey-").toFile();
        output = new File(context.getExternalFilesDir(null), "library-journey-" + UUID.randomUUID());
        assertTrue(output.mkdirs());
        String archiveName = "AbyssME-journey-" + UUID.randomUUID() + ".zip";
        String corruptName = archiveName.replace(".zip", "-corrupt.zip");
        File source = new File(root, "source"), destination = new File(root, "destination");
        try {
            assertTrue(source.mkdirs()); assertTrue(destination.mkdirs());
            configure(source);
            List<AppItem> originals = prepare(source);
            Map<String,String> originalBytes = dataHashes(source);
            open(source);
            click(backupLabel());
            waitPicker(); capture("01-create-document");
            key(KeyEvent.KEYCODE_BACK); // Cancelling DocumentsUI must leave the library untouched.
            waitApp(); assertTrue(state(s -> !s.busy).idle);
            assertEquals(originalBytes, dataHashes(source));
            click(backupLabel()); waitPicker();
            downloads();
            AccessibilityNodeInfo edit = waitNode(n -> "android.widget.EditText".contentEquals(n.getClassName()));
            Bundle text = new Bundle(); text.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, archiveName);
            assertTrue(edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, text));
            click("Save"); waitApp();
            LibraryImportModel.State exported = state(s -> !s.busy && s.status.startsWith("Backup saved:"));
            assertTrue(exported.status.contains("3 games")); capture("02-exported");
            File archive = new File(root, "backup.zip");
            try (InputStream input = shell("cat /sdcard/Download/" + archiveName)) { Files.copy(input, archive.toPath()); }
            assertTrue(archive.length() > 0);
            try (ZipFile zip = new ZipFile(archive)) {
                assertNotNull(zip.getEntry(LibraryArchive.MANIFEST));
                assertTrue(Collections.list(zip.entries()).stream().noneMatch(e -> e.getName().endsWith(".dex")));
            }
            close();

            // Existing shared-file conflicts must survive restoration.
            write(destination, "fs/shared.txt", "newer destination data");
            open(destination);
            click(restoreLabel()); waitPicker(); key(KeyEvent.KEYCODE_BACK); waitApp();
            assertTrue(state(s -> !s.busy).idle); assertEquals(0, items(destination).size());
            restore(archiveName);
            LibraryImportModel.State preview = state(s -> !s.busy && s.preview != null);
            assertEquals(3, preview.preview.entries.size()); assertTrue(preview.preview.sharedFiles);
            assertTrue(preview.preview.entries.stream().allMatch(e -> e.problem == null && e.hasSaves && e.selected));
            capture("03-preview");
            scenario.recreate(); bind();
            assertEquals(3, state(s -> !s.busy && s.preview != null).preview.entries.size());

            // Hold the actual worker before its first entry, making UI cancellation deterministic.
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            worker.execute(() -> { entered.countDown(); try { release.await(20, TimeUnit.SECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            try { click("Restore (3)"); click("Cancel"); }
            finally { release.countDown(); }
            LibraryImportModel.State stopped = state(s -> !s.busy && s.results != null);
            assertEquals(0, stopped.completed); assertEquals(3, stopped.preview.entries.size());
            assertEquals(0, items(destination).size()); capture("04-cancelled");
            scenario.recreate(); bind(); click("Retry");
            state(s -> !s.busy && s.preview != null && s.results == null);
            click("Restore (3)");
            LibraryImportModel.State done = state(s -> !s.busy && s.results != null);
            assertEquals("Finished. 3 imported, 0 failed, 0 not processed", done.status);
            assertNull(done.preview); capture("05-restored");
            verify(source, destination, originals);
            assertEquals("newer destination data", read(destination, "fs/shared.txt"));
            assertEquals("new shared file", read(destination, "fs/nested/new.txt"));
            for (AppItem game : items(destination))
                write(destination, "data/" + game.getPath() + "/fixture-save.bin", "Newer destination save " + game.getPath());
            Map<String,String> restoredBytes = dataHashes(destination);
            click("Done"); restore(archiveName);
            LibraryImportModel.State duplicates = state(s -> !s.busy && s.preview != null);
            assertEquals(3, duplicates.preview.entries.size());
            assertTrue(duplicates.preview.entries.stream().allMatch(e -> e.problem != null && !e.selected));
            assertFalse(waitText("Restore").isEnabled()); capture("06-duplicates");
            assertEquals(restoredBytes, dataHashes(destination));
            key(KeyEvent.KEYCODE_BACK); waitText(restoreLabel());

            File corrupt = new File(output, corruptName);
            corrupt(archive, corrupt);
            try (InputStream ignored = shell("cp " + corrupt.getAbsolutePath() + " /sdcard/Download/" + corruptName)) {
                while (ignored.read() != -1) { }
            }
            restore(corruptName);
            LibraryImportModel.State rejected = state(s -> !s.busy && !s.idle && s.preview == null && s.results == null);
            assertEquals(context.getString(ru.playsoftware.j2meloader.R.string.library_transfer_source_invalid), rejected.status);
            waitText(restoreLabel()); capture("07-corrupt-rejected");
            assertEquals(3, items(destination).size()); assertEquals(restoredBytes, dataHashes(destination));
            restore(archiveName); state(s -> !s.busy && s.preview != null);
            assertEquals(restoredBytes, dataHashes(destination));
            assertEquals(originalBytes, dataHashes(source));
            Files.write(new File(output, "result.txt").toPath(), ("Three-game Java/Mophun library: real DocumentsUI export and restore.\n"
                    + "Picker cancellation, preview recreation, cancel-before-first-entry, retry, metadata/profiles, shared conflicts, duplicate rejection and checksum failure passed.\n"
                    + "Save bytes are synthetic sentinels; no guest-progress or physical-device acceptance.\n"
                    + "Archive SHA-256: " + SourceIdentity.sha256(archive) + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (Exception | AssertionError error) {
            capture("failure"); throw error;
        } finally {
            try { close(); } finally { configure(new File(previous)); }
            FileUtils.deleteDirectory(root);
            try (InputStream ignored = shell("rm -f /sdcard/Download/" + archiveName + " /sdcard/Download/" + corruptName)) {
                while (ignored.read() != -1) { }
            }
        }
    }

    List<AppItem> prepare(File source) throws Exception {
        File fixtures = new File(context.getExternalFilesDir(null), "sweep");
        String[] names = {"diamond-backup.jar", "simcity-backup.jar"};
        for (int n = 0; n < names.length; n++) {
            File jar = new File(fixtures, "java/" + names[n]); assertTrue(jar.isFile());
            String id = "journey-java-" + n;
            File app = new File(source, "converted/" + id); assertTrue(app.mkdirs());
            Files.copy(jar.toPath(), new File(app, "res.jar").toPath());
            try (ZipFile zip = new ZipFile(jar); InputStream in = zip.getInputStream(zip.getEntry("META-INF/MANIFEST.MF"))) {
                Files.copy(in, new File(app, Config.MIDLET_MANIFEST_FILE).toPath());
            }
            java.util.jar.Manifest mf;
            try (InputStream in = new FileInputStream(new File(app, Config.MIDLET_MANIFEST_FILE))) { mf = new java.util.jar.Manifest(in); }
            AppItem item = new AppItem(id, mf.getMainAttributes().getValue("MIDlet-Name"),
                    mf.getMainAttributes().getValue("MIDlet-Vendor"), mf.getMainAttributes().getValue("MIDlet-Version"));
            try (AppDatabase db = AppDatabase.open(context, source.getPath())) { db.appItemDao().insertImported(item); }
            SourceHints.write(app, names[n]);
        }
        File mpn = new File(fixtures, "mophun/DeepAbyss_[v1.4]_[multiscreen].mpn"); assertTrue(mpn.isFile());
        MophunStore.install(context, source, Uri.fromFile(mpn));
        List<AppItem> games = items(source); assertEquals(3, games.size());
        for (int n = 0; n < games.size(); n++) {
            AppItem item = games.get(n); item.setFavorite(n != 1); item.setPlayCount(7 + n); item.setLastPlayedAt(1700000000000L + n);
            try (AppDatabase db = AppDatabase.open(context, source.getPath())) { db.appItemDao().update(item); }
            write(source, "data/" + item.getPath() + "/fixture-save.bin", "Synthetic save sentinel " + n);
            write(source, "data/" + item.getPath() + "/private/nested.bin", "Nested data " + n);
            File config = new File(source, "configs/" + item.getPath()); assertTrue(config.mkdirs());
            ProfileModel profile = new ProfileModel(config); profile.screenWidth = 320; profile.screenHeight = 240;
            profile.screenRotation = n * 90; assertTrue(ProfilesManager.saveConfig(profile));
        }
        write(source, "fs/shared.txt", "older source data"); write(source, "fs/nested/new.txt", "new shared file");
        return games;
    }
    private void verify(File source, File destination, List<AppItem> originals) throws Exception {
        List<AppItem> restored = items(destination); assertEquals(originals.size(), restored.size());
        for (AppItem before : originals) {
            AppItem after = restored.stream().filter(i -> i.getTitle().equals(before.getTitle())).findFirst().orElse(null);
            assertNotNull(before.getTitle(), after); assertEquals(before.isFavorite(), after.isFavorite());
            assertEquals(before.getPlayCount(), after.getPlayCount()); assertEquals(before.getLastPlayedAt(), after.getLastPlayedAt());
            assertEquals(ProvidedBackupRoundTrip.hashes(new File(source, "data/" + before.getPath())),
                    ProvidedBackupRoundTrip.hashes(new File(destination, "data/" + after.getPath())));
            ProfileModel a = ProfilesManager.loadConfig(new File(source, "configs/" + before.getPath()));
            ProfileModel b = ProfilesManager.loadConfig(new File(destination, "configs/" + after.getPath()));
            assertNotNull(b); assertEquals(a.screenWidth,b.screenWidth); assertEquals(a.screenHeight,b.screenHeight);
            assertEquals(a.screenRotation,b.screenRotation);
            String payload = before.getPath().startsWith("mophun-") ? "game.mpn" : "res.jar";
            assertEquals(SourceIdentity.sha256(new File(source,"converted/"+before.getPath()+"/"+payload)),
                    SourceIdentity.sha256(new File(destination,"converted/"+after.getPath()+"/"+payload)));
            if (payload.equals("res.jar")) assertTrue(new File(destination,"converted/"+after.getPath()+"/converted.dex").isFile());
        }
    }
    private List<AppItem> items(File folder) {
        try (AppDatabase db = AppDatabase.open(context, folder.getPath())) {
            return db.appItemDao().getAllSingle(new SimpleSQLiteQuery("SELECT * FROM apps ORDER BY path")).blockingGet();
        }
    }
    private Map<String,String> dataHashes(File folder) throws Exception {
        Map<String,String> result = new TreeMap<>();
        for (String name : new String[]{"data","configs","fs"})
            ProvidedBackupRoundTrip.hashes(new File(folder,name)).forEach((k,v) -> result.put(name+"/"+k,v));
        return result;
    }
    private void configure(File folder) throws Exception {
        Method init = Config.class.getDeclaredMethod("initDirs",String.class); init.setAccessible(true); init.invoke(null,folder.getPath());
    }
    private void open(File folder) throws Exception { configure(folder); scenario=ActivityScenario.launch(LibraryImportActivity.class); bind(); waitText(backupLabel()); }
    private void bind() throws Exception {
        scenario.onActivity(a -> model=new ViewModelProvider(a).get(LibraryImportModel.class));
        java.lang.reflect.Field field=LibraryImportModel.class.getDeclaredField("executor"); field.setAccessible(true); worker=(ExecutorService)field.get(model);
    }
    private void close() throws Exception {
        if (scenario==null) return;
        scenario.close(); scenario=null;
        assertTrue("Transfer worker must stop before switching roots",worker.awaitTermination(30,TimeUnit.SECONDS));
    }
    private LibraryImportModel.State state(Predicate<LibraryImportModel.State> ready) {
        long deadline=SystemClock.uptimeMillis()+60000;
        while(SystemClock.uptimeMillis()<deadline) {
            LibraryImportModel.State[] current={null}; instrumentation.runOnMainSync(() -> current[0]=model.state.getValue());
            if(current[0]!=null && ready.test(current[0])) return current[0]; SystemClock.sleep(100);
        }
        throw new AssertionError("Transfer state timed out: "+model.state.getValue().status);
    }
    private void restore(String name) {
        click(restoreLabel()); waitPicker(); downloads();
        AccessibilityNodeInfo file=waitText(name);
        while(!file.isFocusable() && file.getParent()!=null) file=file.getParent();
        assertTrue("Focus document row",file.isFocused() || file.performAction(AccessibilityNodeInfo.ACTION_FOCUS));
        key(KeyEvent.KEYCODE_DPAD_CENTER); waitApp();
    }
    private void waitPicker() { waitNode(n -> n.getPackageName()!=null && n.getPackageName().toString().contains("documentsui")); }
    private void waitApp() { waitNode(n -> context.getPackageName().contentEquals(n.getPackageName())); }
    private void downloads() {
        click("Show roots");
        // Breadcrumb/title text remains in the tree behind the drawer. Select its actual row.
        click(waitNode(n -> "Downloads".equals(String.valueOf(n.getText())) && "android:id/title".equals(n.getViewIdResourceName())), "Downloads root");
        // Wait for the drawer animation before tapping a file underneath it.
        try { ui.waitForIdle(500,5000); }
        catch (java.util.concurrent.TimeoutException e) { throw new AssertionError("DocumentsUI drawer did not settle", e); }
    }
    private String backupLabel() { return BuildConfig.SYMBIAN_EXPERIMENT ? "Back up Java / Mophun" : "Back up library"; }
    private String restoreLabel() { return BuildConfig.SYMBIAN_EXPERIMENT ? "Restore Java / Mophun backup" : "Restore backup"; }
    private AccessibilityNodeInfo find(AccessibilityNodeInfo node, Predicate<AccessibilityNodeInfo> match) {
        if(node==null) return null; if(match.test(node)) return node;
        for(int n=0;n<node.getChildCount();n++) { AccessibilityNodeInfo found=find(node.getChild(n),match); if(found!=null)return found; }
        return null;
    }
    private AccessibilityNodeInfo waitNode(Predicate<AccessibilityNodeInfo> match) {
        long deadline=SystemClock.uptimeMillis()+15000;
        do { AccessibilityNodeInfo found=find(ui.getRootInActiveWindow(),match); if(found!=null)return found; SystemClock.sleep(100); }
        while(SystemClock.uptimeMillis()<deadline);
        throw new AssertionError("Missing UI node");
    }
    private AccessibilityNodeInfo waitText(String text) {
        try { return waitNode(n -> text.equalsIgnoreCase(String.valueOf(n.getText())) || text.equalsIgnoreCase(String.valueOf(n.getContentDescription()))); }
        catch (AssertionError e) { throw new AssertionError("Missing UI text: " + text, e); }
    }
    private void click(String label) {
        click(waitText(label), label);
    }
    private void click(AccessibilityNodeInfo node, String label) {
        AccessibilityNodeInfo n=node;
        if (!n.isVisibleToUser()) {
            n.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
            SystemClock.sleep(250); n=waitText(label);
        }
        AccessibilityNodeInfo target=n;
        while(!n.isClickable() && n.getParent()!=null)n=n.getParent();
        assertTrue("Enabled "+label,target.isEnabled());
        if (n.isClickable()) assertTrue("Click "+label,n.performAction(AccessibilityNodeInfo.ACTION_CLICK));
        else {
            // DocumentsUI grid items use RecyclerView touch handling, without ACTION_CLICK.
            assertTrue(target.isVisibleToUser()); android.graphics.Rect bounds=new android.graphics.Rect();
            target.getBoundsInScreen(bounds); assertFalse(bounds.isEmpty());
            long now=SystemClock.uptimeMillis();
            android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,0,bounds.centerX(),bounds.centerY(),0);
            android.view.MotionEvent up=android.view.MotionEvent.obtain(now,now+100,1,bounds.centerX(),bounds.centerY(),0);
            down.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN); up.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
            try { assertTrue(ui.injectInputEvent(down,true)); SystemClock.sleep(100); assertTrue(ui.injectInputEvent(up,true)); }
            finally { down.recycle(); up.recycle(); }
        }
        instrumentation.waitForIdleSync(); SystemClock.sleep(150);
    }
    private void key(int code) {
        long now=SystemClock.uptimeMillis(); assertTrue(ui.injectInputEvent(new KeyEvent(now,now,0,code,0),true));
        assertTrue(ui.injectInputEvent(new KeyEvent(now,SystemClock.uptimeMillis(),1,code,0),true)); instrumentation.waitForIdleSync();
    }
    private InputStream shell(String command) { return new ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(command)); }
    private void capture(String name) throws Exception {
        Bitmap bitmap=ui.takeScreenshot(); assertNotNull(bitmap);
        try(OutputStream out=new FileOutputStream(new File(output,name+".png"))) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out)); }
        finally { bitmap.recycle(); }
    }
    private static void write(File folder,String name,String value) throws Exception {
        File file=new File(folder,name); file.getParentFile().mkdirs(); Files.write(file.toPath(),value.getBytes(StandardCharsets.UTF_8));
    }
    private static String read(File folder,String name) throws Exception { return new String(Files.readAllBytes(new File(folder,name).toPath()),StandardCharsets.UTF_8); }
    private static void corrupt(File source,File target) throws Exception {
        boolean changed=false;
        try(ZipFile zip=new ZipFile(source); ZipOutputStream out=new ZipOutputStream(new FileOutputStream(target))) {
            for(ZipEntry entry:Collections.list(zip.entries())) {
                out.putNextEntry(new ZipEntry(entry.getName()));
                if(entry.getName().endsWith("fixture-save.bin") && !changed) { out.write("tampered save".getBytes(StandardCharsets.UTF_8)); changed=true; }
                else try(InputStream in=zip.getInputStream(entry)) { byte[] buffer=new byte[8192]; int n; while((n=in.read(buffer))!=-1)out.write(buffer,0,n); }
                out.closeEntry();
            }
        }
        assertTrue(changed);
    }
}
