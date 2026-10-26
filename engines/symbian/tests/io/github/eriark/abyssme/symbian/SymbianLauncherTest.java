package io.github.eriark.abyssme.symbian;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.*;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.Assume;
import ru.playsoftware.j2meloader.MainActivity;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.applist.*;
import static org.junit.Assert.*;

public final class SymbianLauncherTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context context = instrumentation.getTargetContext();
    private UiAutomation ui;

    @Test public void staleUidDoesNotLaunchAnotherGameOrPhone() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("libraryUi")));
        List<SymbianLibraryIndex.Entry> entries = new SymbianLibraryIndex(context).read();
        Assume.assumeFalse(entries.isEmpty());
        ui = instrumentation.getUiAutomation();
        SystemFileCatalog catalog = new SystemFileCatalog(context);
        File previous = catalog.activeRoot(SymbianFiles.root(context));
        try {
            context.startActivity(new Intent(context, SymbianActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(SymbianLibraryIndex.SYSTEM_EXTRA, entries.get(0).system)
                    .putExtra(SymbianLibraryIndex.UID_EXTRA, 1L));
            waitText("This game is no longer installed on its selected phone", 15000);
            assertTrue(ui.getRootInActiveWindow().findAccessibilityNodeInfosByText("Import phone").isEmpty());
            assertEquals(catalog.runtime(catalog.read(entries.get(0).system)), catalog.activeRoot(SymbianFiles.root(context)));
            click("Library"); SystemClock.sleep(700);
        } finally {
            for (SystemFileCatalog.Entry entry : catalog.list()) if (catalog.runtime(entry).equals(previous)) catalog.activate(entry);
        }
    }

    @Test public void sharedViewsSearchFavoritesDirectLaunchAndReturn() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("libraryUi")));
        List<SymbianLibraryIndex.Entry> entries = new SymbianLibraryIndex(context).read();
        Assume.assumeTrue(entries.stream().anyMatch(e -> e.title.equals("Snakes")));
        Assume.assumeTrue(entries.stream().anyMatch(e -> e.uid == 536915939));
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        int oldMode = prefs.getInt("pref_library_view_mode", 0);
        SharedPreferences presentation = context.getSharedPreferences("symbian-library-presentation", Context.MODE_PRIVATE);
        Map<String, ?> oldPresentation = presentation.getAll();
        SystemFileCatalog catalog = new SystemFileCatalog(context);
        File previous = catalog.activeRoot(SymbianFiles.root(context));
        ui = instrumentation.getUiAutomation();
        LibraryPreferencesSnapshot navigation = new LibraryPreferencesSnapshot(context);
        MainActivity activity = (MainActivity) instrumentation.startActivitySync(
                new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            instrumentation.runOnMainSync(() -> activity.findViewById(R.id.platform_ngage).performClick());
            waitLibrary(activity);
            int[] modes = {R.id.view_mode_gallery, R.id.view_mode_list, R.id.view_mode_grid};
            String[] names = {"gallery", "list", "grid"};
            for (int i = 0; i < modes.length; i++) {
                final int mode = modes[i];
                instrumentation.runOnMainSync(() -> activity.findViewById(mode).performClick());
                instrumentation.waitForIdleSync();
                capture("symbian-shared-" + names[i] + ".png");
            }
            search(activity, "Snakes");
            instrumentation.runOnMainSync(() -> assertEquals(1, adapter(activity).getItemCount()));
            select(activity, "Snakes"); key(KeyEvent.KEYCODE_BUTTON_X);
            waitText("System files", 5000);
            assertTrue(ui.getRootInActiveWindow().findAccessibilityNodeInfosByText("Delete").isEmpty());
            assertTrue(ui.getRootInActiveWindow().findAccessibilityNodeInfosByText("Reinstall").isEmpty());
            capture("symbian-shared-options.png");
            boolean original = presentation.getBoolean(entries.stream().filter(e -> e.title.equals("Snakes")).findFirst().get().key() + ".favorite", false);
            click(original ? "Remove favorite" : "Add favorite");
            search(activity, "");
            category(activity, "Favorites");
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> {
                boolean found = false;
                for (int i = 0; i < adapter(activity).getItemCount(); i++) found |= adapter(activity).getItem(i).getTitle().equals("Snakes");
                assertEquals(!original, found);
            });
            category(activity, "All games");
            for (String title : new String[]{"Snakes", "Asphalt3"}) {
                select(activity, title); key(KeyEvent.KEYCODE_BUTTON_A);
                SystemClock.sleep(15000);
                AccessibilityNodeInfo root = ui.getRootInActiveWindow();
                assertNotNull(root); assertEquals(context.getPackageName(), root.getPackageName().toString());
                assertTrue("Must launch game, not setup", root.findAccessibilityNodeInfosByText("Import phone").isEmpty());
                assertTrue("Must leave shared library", root.findAccessibilityNodeInfosByText("All games").isEmpty());
                capture("symbian-shared-direct-" + title.toLowerCase(Locale.ROOT) + ".png");
                key(KeyEvent.KEYCODE_BACK); click("Exit game"); click("Leave without saving");
                waitText("All games", 15000); waitLibrary(activity);
                category(activity, "Recent");
                instrumentation.waitForIdleSync();
                instrumentation.runOnMainSync(() -> {
                    assertTrue(adapter(activity).getItemCount() > 0);
                    assertEquals(title, adapter(activity).getItem(0).getTitle());
                });
                category(activity, "All games");
            }
            capture("symbian-shared-return.png");
        } finally {
            instrumentation.runOnMainSync(activity::finish);
            navigation.close();
            prefs.edit().putInt("pref_library_view_mode", oldMode).commit();
            SharedPreferences.Editor restore = presentation.edit();
            for (SymbianLibraryIndex.Entry entry : entries) for (String suffix : new String[]{".favorite", ".lastPlayed", ".playCount"}) {
                String name = entry.key() + suffix; Object value = oldPresentation.get(name);
                if (value instanceof Boolean) restore.putBoolean(name, (Boolean) value);
                else if (value instanceof Long) restore.putLong(name, (Long) value);
                else if (value instanceof Integer) restore.putInt(name, (Integer) value);
                else restore.remove(name);
            }
            restore.commit();
            for (SystemFileCatalog.Entry entry : catalog.list()) if (catalog.runtime(entry).equals(previous)) catalog.activate(entry);
        }
    }
    private void category(MainActivity activity, String label) {
        instrumentation.runOnMainSync(() -> activity.findViewById(R.id.library_category).performClick());
        click(label);
    }
    private AppsListAdapter adapter(MainActivity activity) {
        return (AppsListAdapter) ((RecyclerView) activity.findViewById(R.id.apps_recycler)).getAdapter();
    }
    private void waitLibrary(MainActivity activity) {
        AtomicBoolean ready = new AtomicBoolean(); long deadline = SystemClock.uptimeMillis() + 15000;
        do {
            instrumentation.runOnMainSync(() -> {
                RecyclerView list = activity.findViewById(R.id.apps_recycler);
                ready.set(list != null && list.getAdapter() != null && list.getAdapter().getItemCount() >= 2 && !list.hasPendingAdapterUpdates());
            });
            if (!ready.get()) SystemClock.sleep(100);
        } while (!ready.get() && SystemClock.uptimeMillis() < deadline);
        assertTrue("Shared library ready", ready.get());
    }
    private void search(MainActivity activity, String query) {
        instrumentation.runOnMainSync(() -> ((EditText) activity.findViewById(R.id.library_search)).setText(query));
        instrumentation.waitForIdleSync(); SystemClock.sleep(200);
    }
    private void select(MainActivity activity, String title) {
        AtomicBoolean focused = new AtomicBoolean(); long deadline = SystemClock.uptimeMillis() + 5000;
        do {
            instrumentation.runOnMainSync(() -> {
                RecyclerView list = activity.findViewById(R.id.apps_recycler);
                for (int i = 0; i < adapter(activity).getItemCount(); i++) if (adapter(activity).getItem(i).getTitle().equals(title)) {
                    RecyclerView.ViewHolder row = list.findViewHolderForAdapterPosition(i);
                    if (row == null) { list.scrollToPosition(i); list.requestLayout(); return; }
                    row.itemView.setFocusableInTouchMode(true); focused.set(row.itemView.requestFocus()); return;
                }
            });
            if (!focused.get()) SystemClock.sleep(100);
        } while (!focused.get() && SystemClock.uptimeMillis() < deadline);
        assertTrue("Focus game: " + title, focused.get());
        instrumentation.waitForIdleSync();
    }
    private void key(int code) {
        long now = SystemClock.uptimeMillis();
        ui.injectInputEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0), true);
        ui.injectInputEvent(new KeyEvent(now, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0), true);
    }
    private AccessibilityNodeInfo waitText(String text, long timeout) {
        long deadline = SystemClock.uptimeMillis() + timeout;
        do {
            AccessibilityNodeInfo root = ui.getRootInActiveWindow();
            if (root != null) for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text))
                if (node.getText() != null && text.equalsIgnoreCase(node.getText().toString())) return node;
            SystemClock.sleep(100);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Missing text: " + text);
    }
    private void click(String text) {
        AccessibilityNodeInfo node = waitText(text, 15000);
        while (!node.isClickable() && node.getParent() != null) node = node.getParent();
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK));
    }
    private void capture(String name) throws Exception {
        ui.waitForIdle(500, 5000);
        Bitmap image = ui.takeScreenshot(); assertNotNull(image);
        try (OutputStream output = new FileOutputStream(new File(context.getExternalFilesDir(null), name))) {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally { image.recycle(); }
    }
}
