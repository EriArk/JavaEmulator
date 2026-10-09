package ru.playsoftware.j2meloader.applist;

import android.content.Context;
import android.os.SystemClock;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import ru.playsoftware.j2meloader.MainActivity;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.appsdb.AppDatabase;
import ru.playsoftware.j2meloader.config.Config;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class LibraryChromeTest {
    @Test public void compactSearchAndCollectionSurviveRecreationAndCloseCleanly() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assumeTrue(context.getResources().getConfiguration().screenWidthDp < 600);
        File root = Files.createTempDirectory(context.getCacheDir().toPath(), "library-chrome-").toFile();
        try (AppDatabase db = AppDatabase.open(context, root.getPath())) {
            for (int i = 0; i < 2; i++) {
                AppItem item = new AppItem("chrome-" + i, i == 0 ? "Alpha" : "Beta", "Fixture", "1");
                item.setPreparationState("indexed");
                db.appItemDao().insertImported(item);
            }
        }
        String original = Config.getEmulatorDir();
        Method init = Config.class.getDeclaredMethod("initDirs", String.class); init.setAccessible(true);
        try {
            init.invoke(null, root.getPath());
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                awaitCount(scenario, 2);
                scenario.onActivity(activity -> {
                    assertEquals(View.GONE, activity.findViewById(R.id.library_category_row).getVisibility());
                    assertEquals(View.GONE, activity.findViewById(R.id.library_mode_row).getVisibility());
                    assertEquals(View.GONE, activity.findViewById(R.id.library_search_row).getVisibility());
                    assertEquals(View.VISIBLE, activity.findViewById(R.id.library_menu).getVisibility());
                    float density = activity.getResources().getDisplayMetrics().density;
                    assertEquals(48 * density, activity.findViewById(R.id.library_header).getHeight(), 1);
                    activity.findViewById(R.id.library_search_toggle).performClick();
                    ((EditText) activity.findViewById(R.id.library_search)).setText("Alpha");
                });
                awaitCount(scenario, 1);
                scenario.recreate();
                awaitCount(scenario, 1);
                scenario.onActivity(activity -> {
                    assertEquals("Alpha", ((EditText) activity.findViewById(R.id.library_search)).getText().toString());
                    assertEquals(View.VISIBLE, activity.findViewById(R.id.library_search_row).getVisibility());
                    activity.findViewById(R.id.library_search_close).performClick();
                    assertEquals(View.GONE, activity.findViewById(R.id.library_search_row).getVisibility());
                    assertEquals("", ((EditText) activity.findViewById(R.id.library_search)).getText().toString());
                });
                awaitCount(scenario, 2);
                scenario.onActivity(activity -> {
                    activity.findViewById(R.id.library_search_toggle).performClick();
                    activity.getOnBackPressedDispatcher().onBackPressed();
                    assertEquals(View.GONE, activity.findViewById(R.id.library_search_row).getVisibility());
                    activity.findViewById(R.id.rail_favorites).performClick();
                });
                scenario.recreate();
                scenario.onActivity(activity -> assertEquals("Favorites",
                        ((TextView) activity.findViewById(R.id.library_category)).getText().toString()));
                awaitCount(scenario, 0);
            }
        } finally { init.invoke(null, original); }
    }

    private void awaitCount(ActivityScenario<MainActivity> scenario, int count) {
        AtomicBoolean ready = new AtomicBoolean();
        long deadline = SystemClock.uptimeMillis() + 8000;
        do {
            scenario.onActivity(activity -> {
                RecyclerView list = activity.findViewById(R.id.apps_recycler);
                ready.set(list != null && list.getAdapter().getItemCount() == count
                        && (count == 0 || !list.hasPendingAdapterUpdates()));
            });
            if (!ready.get()) SystemClock.sleep(50);
        } while (!ready.get() && SystemClock.uptimeMillis() < deadline);
        assertTrue("Expected " + count + " filtered games", ready.get());
    }
}
