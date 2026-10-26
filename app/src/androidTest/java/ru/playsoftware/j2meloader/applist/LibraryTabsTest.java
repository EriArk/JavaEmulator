package ru.playsoftware.j2meloader.applist;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import ru.playsoftware.j2meloader.MainActivity;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.appsdb.AppDatabase;
import ru.playsoftware.j2meloader.catalog.ExternalLibrary;
import ru.playsoftware.j2meloader.config.Config;
import static org.junit.Assert.*;

public class LibraryTabsTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context context = instrumentation.getTargetContext();

    @Test public void migrationRetainsViewSelectionAndLegacyAnchorsOnlyOnce() {
        SharedPreferences p = context.getSharedPreferences("library-tabs-migration-test", 0);
        p.edit().clear().putInt("libraryPlatform", 4).putInt("libraryCategory", 2)
                .putInt("pref_library_view_mode", 1).putString("librarySelection", "symbian:one")
                .putString("libraryLocation.2.4", "symbian:one").commit();
        try {
            LibraryNavigation n = new LibraryNavigation(p);
            assertEquals(4, n.active()); assertEquals(1, n.view(4)); assertEquals(2, n.category(4));
            assertEquals("symbian:one", n.selection(4)); assertNull(n.selection(1));
            assertEquals("symbian:one", p.getString("libraryLocation.2.4", null));
            n.save(1, 2, 0, 0, "Bounce", "java:two");
            n = new LibraryNavigation(p);
            assertEquals(1, n.active()); assertEquals(2, n.view(1)); assertEquals(1, n.view(4));
            assertEquals("Bounce", n.query(1)); assertEquals("", n.query(4));
            assertEquals(4, LibraryNavigation.next(1, -1)); assertEquals(1, LibraryNavigation.next(4, 1));
            assertEquals(1, LibraryNavigation.platform(0));
        } finally { p.edit().clear().commit(); }
    }

    private AppItem nativeItem(String id, String platform) {
        return new ExternalLibrary.Item(id, "Game", platform, "Phone") {
            public void launch(Activity a, boolean settings) { }
            public void favorite() { }
            public void rename(String title) { }
        };
    }
    @Test public void ngageGenerationFilterNeverMixesSymbianOrRequiresBothForSingleGeneration() {
        AppItem classic = nativeItem("symbian:c", "N-Gage"), modern = nativeItem("symbian:m", "N-Gage 2.0"),
                symbian = nativeItem("symbian:s", "Symbian");
        assertTrue(LibraryQuery.hasBothNgageGenerations(Arrays.asList(classic, modern, symbian)));
        assertFalse(LibraryQuery.hasBothNgageGenerations(Arrays.asList(classic, symbian)));
        assertEquals(Arrays.asList(classic), LibraryQuery.select(Arrays.asList(classic, modern, symbian), 0, 4, 1, ""));
        assertEquals(Arrays.asList(modern), LibraryQuery.select(Arrays.asList(classic, modern, symbian), 0, 4, 2, ""));
        assertEquals(Arrays.asList(symbian), LibraryQuery.select(Arrays.asList(classic, modern, symbian), 0, 3, 2, ""));
    }

    @Test public void tabsRestoreViewSearchAndColdSelectionWithoutResettingOtherPlatforms() throws Exception {
        File root = Files.createTempDirectory(context.getCacheDir().toPath(), "library-tabs-").toFile();
        try (AppDatabase db = AppDatabase.open(context, root.getPath())) {
            for (int i = 0; i < 12; i++) {
                AppItem item = new AppItem("tabs-" + i, i == 0 ? "Alpha" : "Beta " + i, "Fixture", "1");
                item.setPreparationState("indexed"); db.appItemDao().insertImported(item);
            }
        }
        String original = Config.getEmulatorDir();
        Method init = Config.class.getDeclaredMethod("initDirs", String.class); init.setAccessible(true);
        try (LibraryPreferencesSnapshot snapshot = new LibraryPreferencesSnapshot(context)) {
            init.invoke(null, root.getPath());
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                awaitCount(scenario, 12);
                scenario.onActivity(a -> {
                    assertEquals(4, ((LinearLayout) a.findViewById(R.id.library_platform_tabs)).getChildCount());
                    assertTrue(a.findViewById(R.id.platform_java).isSelected());
                    a.findViewById(R.id.view_mode_list).performClick();
                    a.findViewById(R.id.library_search_toggle).performClick();
                    ((EditText) a.findViewById(R.id.library_search)).setText("Alpha");
                    AppsListFragment fragment = (AppsListFragment) a.getSupportFragmentManager()
                            .getFragments().get(0);
                    assertTrue("Consume launch until the new query has published",
                            fragment.handleControllerKey(KeyEvent.KEYCODE_BUTTON_A));
                });
                awaitCount(scenario, 1);
                scenario.onActivity(a -> a.findViewById(R.id.platform_mophun).performClick());
                awaitCount(scenario, 0);
                scenario.onActivity(a -> {
                    assertEquals("", ((EditText) a.findViewById(R.id.library_search)).getText().toString());
                    a.findViewById(R.id.view_mode_grid).performClick();
                    a.findViewById(R.id.platform_java).performClick();
                });
                awaitCount(scenario, 1);
                scenario.onActivity(a -> {
                    assertTrue(a.findViewById(R.id.view_mode_list).isSelected());
                    assertEquals("Alpha", ((EditText) a.findViewById(R.id.library_search)).getText().toString());
                    assertTrue(a.findViewById(R.id.library_social).isShown());
                    RecyclerView list = a.findViewById(R.id.apps_recycler);
                    drag(list, 50, 30, 50, 180, 160);
                    assertTrue(a.findViewById(R.id.platform_java).isSelected());
                    drag(list, 40, 30, list.getWidth() - 30, 30, 650);
                    assertTrue(a.findViewById(R.id.platform_java).isSelected());
                    drag(list, list.getWidth() - 30, 30, 40, 30, 160);
                    assertTrue(a.findViewById(R.id.platform_mophun).isSelected());
                    a.findViewById(R.id.platform_java).performClick();
                });
                awaitCount(scenario, 1);
                scenario.recreate(); awaitCount(scenario, 1);
                scenario.onActivity(a -> {
                    assertTrue(a.findViewById(R.id.platform_java).isSelected());
                    assertTrue(a.findViewById(R.id.view_mode_list).isSelected());
                    a.findViewById(R.id.platform_mophun).performClick();
                });
                awaitCount(scenario, 0);
                scenario.onActivity(a -> assertTrue(a.findViewById(R.id.view_mode_grid).isSelected()));
            }
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                awaitCount(scenario, 0);
                scenario.onActivity(a -> {
                    assertTrue(a.findViewById(R.id.platform_mophun).isSelected());
                    assertTrue(a.findViewById(R.id.view_mode_grid).isSelected());
                    // A held digital trigger must not cycle through several platforms.
                    a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_L2));
                    a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_L2));
                    assertTrue(a.findViewById(R.id.platform_java).isSelected());
                    a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_L2));
                    a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_L2));
                    assertTrue(a.findViewById(R.id.platform_ngage).isSelected());
                    a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_L2));
                    a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_R2));
                    a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_R2));
                });
                awaitCount(scenario, 1);
            }
        } finally { init.invoke(null, original); }
    }

    private void drag(View view, float x, float y, float endX, float endY, int duration) {
        long now = SystemClock.uptimeMillis();
        int[] actions = {android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_MOVE, android.view.MotionEvent.ACTION_UP};
        for (int i = 0; i < 3; i++) {
            android.view.MotionEvent event = android.view.MotionEvent.obtain(now, now + (i == 0 ? 0 : duration),
                    actions[i], i == 0 ? x : endX, i == 0 ? y : endY, 0);
            view.dispatchTouchEvent(event); event.recycle();
        }
    }

    @Test public void staleCardCannotLaunchOrReplaceNextPlatformsSelection() throws Exception {
        AppsListAdapter[] adapter = new AppsListAdapter[1];
        java.util.concurrent.CountDownLatch loaded = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger clicks = new java.util.concurrent.atomic.AtomicInteger();
        instrumentation.runOnMainSync(() -> {
            adapter[0] = new AppsListAdapter(new AppsListAdapter.Listener() {
                public void onAppClicked(AppItem item) { clicks.incrementAndGet(); }
                public void onAppFocused(AppItem item) { }
                public void onAppActionsRequested(View v, AppItem item) { clicks.incrementAndGet(); }
                public void onItemsFiltered() { loaded.countDown(); }
            });
            adapter[0].setItems(Collections.singletonList(new AppItem("fixture", "Fixture", "Vendor", "1")));
        });
        assertTrue(loaded.await(5, java.util.concurrent.TimeUnit.SECONDS));
        instrumentation.runOnMainSync(() -> {
            android.view.ContextThemeWrapper themed = new android.view.ContextThemeWrapper(context, R.style.AppTheme);
            AppsListAdapter.ViewHolder holder = adapter[0].onCreateViewHolder(new android.widget.FrameLayout(themed), 0);
            adapter[0].onBindViewHolder(holder, 0);
            holder.itemView.performClick(); assertEquals(1, clicks.get());
            adapter[0].setQuery(0, 2, 0, "");
            holder.itemView.performClick(); holder.itemView.performLongClick();
            assertEquals(1, clicks.get());
        });
    }

    private void awaitCount(ActivityScenario<MainActivity> scenario, int count) {
        AtomicBoolean ready = new AtomicBoolean(); long end = SystemClock.uptimeMillis() + 10000;
        do {
            scenario.onActivity(a -> {
                RecyclerView list = a.findViewById(R.id.apps_recycler);
                ready.set(list != null && list.getAdapter() != null && list.getAdapter().getItemCount() == count
                        && (count == 0 || !list.hasPendingAdapterUpdates()));
            });
            if (!ready.get()) SystemClock.sleep(50);
        } while (!ready.get() && SystemClock.uptimeMillis() < end);
        assertTrue("Expected " + count + " games", ready.get());
        instrumentation.waitForIdleSync();
    }
}
