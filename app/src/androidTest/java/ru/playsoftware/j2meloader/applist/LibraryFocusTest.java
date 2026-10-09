package ru.playsoftware.j2meloader.applist;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.View;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import ru.playsoftware.j2meloader.BuildConfig;
import ru.playsoftware.j2meloader.MainActivity;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.appsdb.AppDatabase;
import ru.playsoftware.j2meloader.config.Config;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class LibraryFocusTest {
    @Test public void coldLibraryFocusSurvivesLayoutAndDoesNotStealHeaderFocus() throws Exception {
        assumeTrue(BuildConfig.HANDHELD_MODE);
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        File root = Files.createTempDirectory(context.getCacheDir().toPath(), "library-focus-").toFile();
        try (AppDatabase db = AppDatabase.open(context, root.getPath())) {
            for (int i = 0; i < 8; i++) {
                AppItem item = new AppItem("pending-focus-" + i, "Focus game " + i, "Fixture", "1");
                item.setPreparationState("indexed"); db.appItemDao().insertImported(item);
            }
        }
        String original = Config.getEmulatorDir();
        Method init = Config.class.getDeclaredMethod("initDirs", String.class); init.setAccessible(true);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        int oldMode = preferences.getInt("pref_library_view_mode", 0);
        try {
            init.invoke(null, root.getPath());
            for (int mode = 0; mode < 3; mode++) {
                preferences.edit().putInt("pref_library_view_mode", mode).commit();
                MainActivity activity = (MainActivity) instrumentation.startActivitySync(
                        new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                try {
                    AtomicBoolean focused = new AtomicBoolean();
                    long deadline = SystemClock.uptimeMillis() + 8000;
                    do {
                        instrumentation.runOnMainSync(() -> {
                            RecyclerView list = activity.findViewById(R.id.apps_recycler);
                            focused.set(list != null && list.hasFocus() && !list.hasPendingAdapterUpdates());
                        });
                        if (!focused.get()) SystemClock.sleep(50);
                    } while (!focused.get() && SystemClock.uptimeMillis() < deadline);
                    assertTrue("Cold-start game focus, mode " + mode, focused.get());
                    instrumentation.runOnMainSync(() -> {
                        RecyclerView list = activity.findViewById(R.id.apps_recycler);
                        View header = activity.findViewById(R.id.library_menu);
                        if (header.getVisibility() != View.VISIBLE) header = activity.findViewById(R.id.rail_settings);
                        header.setFocusableInTouchMode(true); assertTrue(header.requestFocus());
                        ((AppsListAdapter) list.getAdapter()).setAvailableHeight(140);
                        list.requestLayout();
                    });
                    instrumentation.waitForIdleSync();
                    SystemClock.sleep(150);
                    instrumentation.runOnMainSync(() -> assertFalse("Layout must not steal focus from menu",
                            activity.findViewById(R.id.apps_recycler).hasFocus()));
                } finally {
                    instrumentation.runOnMainSync(activity::finish);
                    instrumentation.waitForIdleSync();
                }
            }
        } finally {
            init.invoke(null, original);
            preferences.edit().putInt("pref_library_view_mode", oldMode).commit();
        }
    }
}
