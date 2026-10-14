package io.github.eriark.abyssme.symbian;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.nio.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.*;
import org.junit.Assume;
import org.junit.Test;
import ru.playsoftware.j2meloader.MainActivity;
import ru.playsoftware.j2meloader.R;
import static org.junit.Assert.*;

public final class UnifiedImportUiTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context context = instrumentation.getTargetContext();
    private UiAutomation ui;
    @Test public void libraryPlusRoutesSevenZToArchivePreview() throws Exception {
        String fixture=InstrumentationRegistry.getArguments().getString("sevenFixture");
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("compressedUi")));Assume.assumeNotNull(fixture);
        ArchiveBatch store=new ArchiveBatch(new File(context.getFilesDir(),"symbian-archive-import"));
        Assume.assumeFalse(store.exists() || new PendingGameImport(new File(context.getFilesDir(),"symbian-game-import")).exists());
        File root=Files.createTempDirectory(context.getCacheDir().toPath(),"compressed-ui-").toFile();
        File source=new File(root,"Symbian-games.7z");Files.copy(new File(fixture).toPath(),source.toPath());
        ui=instrumentation.getUiAutomation();
        MainActivity activity=(MainActivity)instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        String id=null;
        try {
            waitText("All games",15000);pick(activity,source);waitText("Archive import",20000);waitText("Import 3",5000);
            id=store.read().id;assertEquals(4,store.read().items.size());capture("symbian-library-seven-preview.png");
            key(KeyEvent.KEYCODE_BACK);click("Library");waitText("All games",15000);assertTrue(source.isFile());
        } finally {
            instrumentation.runOnMainSync(activity::finish);
            if(id!=null && store.exists() && id.equals(store.read().id))store.discard();
            PhoneBackupTest.remove(root);
        }
    }
    @Test public void libraryPlusRoutesJavaNativePlatformAndSystemWithoutReplacingInstalledGames() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("importUi")));
        PendingGameImport pending = new PendingGameImport(new File(context.getFilesDir(), "symbian-game-import"));
        Assume.assumeFalse("Do not replace an owner's pending import", pending.exists());
        SystemFileCatalog catalog = new SystemFileCatalog(context);
        Assume.assumeTrue(catalog.candidates(GameImport.Kind.SIS_MODERN, "new-ui-fixture").size() > 1);
        File inputs = new File(context.getExternalFilesDir(null), "symbian-input");
        Assume.assumeTrue(new File(inputs, "ngage2.sis").isFile());
        File root = Files.createTempDirectory(context.getCacheDir().toPath(), "import-ui-").toFile();
        File pointer = new File(context.getFilesDir(), "symbian-platforms/selected");
        byte[] previous = pointer.isFile() ? Files.readAllBytes(pointer.toPath()) : null;
        List<String> systemsBefore = new ArrayList<>();
        for (SystemFileCatalog.Entry e : catalog.list()) systemsBefore.add(e.id);
        ui = instrumentation.getUiAutomation();
        MainActivity activity = (MainActivity) instrumentation.startActivitySync(new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            waitText("All games", 15000);
            File java = zip(root, "java.zip", new String[]{"one.jar", "two.jar"}, new byte[][]{{1}, {2}});
            pick(activity, java);
            waitText("one.jar", 15000); waitText("two.jar", 5000);
            capture("symbian-unified-java-archive.png"); key(KeyEvent.KEYCODE_BUTTON_B);
            waitText("All games", 5000);

            File mixed = zip(root, "mixed.zip", new String[]{"game.jar", "game.sis"}, new byte[][]{{1}, {2}});
            pick(activity, mixed); waitText("This archive mixes game types or system files. Select one game or system archive.", 10000);
            capture("symbian-unified-import-error.png"); key(KeyEvent.KEYCODE_BUTTON_B);
            waitText("All games", 5000);

            File platform = zip(root, "N-Gage-platform.zip", new String[]{"platform.sis"},
                    new byte[][]{Files.readAllBytes(new File(inputs, "ngage2.sis").toPath())});
            pick(activity, platform); waitText("N-Gage platform saved", 20000);
            try (GameImport saved = new PlatformStore(pointer.getParentFile()).open()) {
                assertNotNull(saved); assertEquals(SystemFileCatalog.hash(new File(inputs, "ngage2.sis")), saved.hash);
            }
            capture("symbian-unified-platform-saved.png"); click("Close"); click("Library"); waitText("All games", 15000);

            File game = new File(root, "Import-UI-fixture.sis");
            Files.write(game.toPath(), ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN).putInt(0x10201a7a).putInt(0).putInt(123).array());
            pick(activity, game); waitText("Choose system for this game", 20000);
            assertEquals(game.getName(), pending.read().name);
            assertEquals(SystemFileCatalog.hash(game), pending.read().hash);
            capture("symbian-unified-game-choice.png"); click("Later"); click("Library"); waitText("All games", 15000);
            pending.discard();

            pick(activity, new File(inputs, "n95.zip")); waitText("System import", 30000);
            capture("symbian-unified-system-import.png"); click("Close"); click("Library"); waitText("All games", 15000);
            List<String> systemsAfter = new ArrayList<>();
            for (SystemFileCatalog.Entry e : catalog.list()) systemsAfter.add(e.id);
            assertEquals(systemsBefore, systemsAfter);
            assertTrue(java.isFile()); assertTrue(game.isFile()); assertTrue(platform.isFile());
            capture("symbian-unified-library-return.png");
        } finally {
            instrumentation.runOnMainSync(activity::finish);
            if (pending.exists() && "Import-UI-fixture.sis".equals(pending.read().name)) pending.discard();
            if (previous == null) new android.util.AtomicFile(pointer).delete();
            else Files.write(pointer.toPath(), previous);
        }
    }
    // The OS document chooser is stubbed, not the library button, result callback or import pipeline.
    private void pick(MainActivity activity, File file) {
        AtomicInteger calls = new AtomicInteger();
        Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor() {
            @Override public Instrumentation.ActivityResult onStartActivity(Intent intent) {
                if (!Intent.ACTION_OPEN_DOCUMENT.equals(intent.getAction())
                        && (intent.getComponent() == null || !intent.getComponent().getClassName().endsWith("FilteredFilePickerActivity"))) return null;
                calls.incrementAndGet();
                return new Instrumentation.ActivityResult(Activity.RESULT_OK, new Intent().setData(Uri.fromFile(file)));
            }
        };
        instrumentation.addMonitor(monitor);
        try {
            instrumentation.runOnMainSync(() -> activity.findViewById(R.id.rail_add_game).performClick());
            instrumentation.waitForIdleSync();
            assertEquals("Library + must open the picker", 1, calls.get());
        } finally { instrumentation.removeMonitor(monitor); }
    }
    private File zip(File root, String name, String[] entries, byte[][] contents) throws IOException {
        File file = new File(root, name);
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(file))) {
            for (int i = 0; i < entries.length; i++) { out.putNextEntry(new ZipEntry(entries[i])); out.write(contents[i]); out.closeEntry(); }
        }
        return file;
    }
    private AccessibilityNodeInfo waitText(String text, long timeout) {
        long end = SystemClock.uptimeMillis() + timeout;
        do {
            AccessibilityNodeInfo root = ui.getRootInActiveWindow();
            if (root != null) for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text))
                if (node.getText() != null && text.equalsIgnoreCase(node.getText().toString())) return node;
            SystemClock.sleep(100);
        } while (SystemClock.uptimeMillis() < end);
        try { capture("symbian-unified-ui-failure.png"); } catch (Exception ignored) {}
        throw new AssertionError("Missing text: " + text);
    }
    private void click(String text) {
        AccessibilityNodeInfo node = waitText(text, 15000);
        while (!node.isClickable() && node.getParent() != null) node = node.getParent();
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK));
    }
    private void key(int code) {
        long now = SystemClock.uptimeMillis();
        ui.injectInputEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0), true);
        ui.injectInputEvent(new KeyEvent(now, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0), true);
        instrumentation.waitForIdleSync();
    }
    private void capture(String name) throws Exception {
        ui.waitForIdle(500, 5000); Bitmap image = ui.takeScreenshot(); assertNotNull(image);
        try (OutputStream out = new FileOutputStream(new File(context.getExternalFilesDir(null), name))) {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, out));
        } finally { image.recycle(); }
    }
}
