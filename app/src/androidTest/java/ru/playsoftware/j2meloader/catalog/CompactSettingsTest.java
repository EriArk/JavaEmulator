package ru.playsoftware.j2meloader.catalog;

import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.util.Arrays;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.settings.SettingsActivity;
import static org.junit.Assert.*;

public class CompactSettingsTest {
    @Test public void settingsSectionsAndSwitchSurviveRecreation() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
                InstrumentationRegistry.getInstrumentation().getTargetContext());
        boolean existed = prefs.contains("pref_vibration_switch");
        boolean original = prefs.getBoolean("pref_vibration_switch", true);
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            idle();
            scenario.onActivity(activity -> {
                assertNotNull(find(activity.getWindow().getDecorView(), "Library"));
                assertNotNull(find(activity.getWindow().getDecorView(), "Support"));
                click(activity.getWindow().getDecorView(), "Playing");
            });
            idle();
            scenario.onActivity(activity -> {
                assertEquals("Playing", activity.getTitle());
                click(activity.getWindow().getDecorView(), "Vibration");
                assertEquals(!original, prefs.getBoolean("pref_vibration_switch", true));
            });
            scenario.recreate(); idle();
            scenario.onActivity(activity -> {
                assertEquals("Playing", activity.getTitle());
                assertEquals(!original, prefs.getBoolean("pref_vibration_switch", true));
                activity.findViewById(R.id.settings_back).performClick();
            });
            idle();
            scenario.onActivity(activity -> {
                assertEquals("Settings", activity.getTitle());
                click(activity.getWindow().getDecorView(), "Library");
            });
            idle();
            scenario.onActivity(activity -> {
                assertNotNull(find(activity.getWindow().getDecorView(), "Library transfer"));
                assertNotNull(find(activity.getWindow().getDecorView(), "Game folders"));
            });
        } finally {
            if (existed) prefs.edit().putBoolean("pref_vibration_switch", original).commit();
            else prefs.edit().remove("pref_vibration_switch").commit();
        }
    }

    @Test public void previewHasRoomSelectionAndResultsAreSeparateFromHome() {
        LibraryImporter.Preview preview = new LibraryImporter.Preview();
        for (int i = 0; i < 8; i++) {
            LibraryImporter.Entry entry = new LibraryImporter.Entry();
            entry.title = "Transfer fixture " + i; entry.vendor = "Test"; entry.version = "1";
            entry.hasSaves = true; entry.selected = i != 7;
            if (i == 7) entry.problem = "Already imported; existing saves will be kept";
            preview.entries.add(entry);
        }
        try (ActivityScenario<LibraryImportActivity> scenario = ActivityScenario.launch(LibraryImportActivity.class)) {
            scenario.onActivity(activity -> {
                assertTrue(find(activity.getWindow().getDecorView(), "Back up library").isShown());
                new ViewModelProvider(activity).get(LibraryImportModel.class).state.setValue(
                        new LibraryImportModel.State(false, "8 games found", 0, 0, preview, null));
            });
            idle();
            scenario.onActivity(activity -> {
                View root = activity.getWindow().getDecorView();
                assertFalse(find(root, "Back up library").isShown());
                assertTrue(find(root, "Import (7)").isEnabled());
                RecyclerView list = recycler(activity.findViewById(R.id.settings_content));
                assertTrue("Preview list starved by chrome", list.getHeight() > activity.findViewById(R.id.settings_content).getHeight() / 3);
                click(root, "Select all");
                assertFalse(find(root, "Import").isEnabled());
                click(root, "Select all");
                assertTrue(find(root, "Import (7)").isEnabled());
                assertFalse(preview.entries.get(7).selected);
            });
            scenario.recreate(); idle();
            scenario.onActivity(activity -> {
                assertTrue(find(activity.getWindow().getDecorView(), "Import (7)").isEnabled());
                new ViewModelProvider(activity).get(LibraryImportModel.class).state.setValue(
                        new LibraryImportModel.State(false, "Finished. 1 imported", 1, 1, null, Arrays.asList("Fixture\nImported")));
            });
            idle();
            scenario.onActivity(activity -> {
                click(activity.getWindow().getDecorView(), "Done");
                assertTrue(find(activity.getWindow().getDecorView(), "Restore backup").isShown());
            });
        }
    }

    @Test public void busyBackCancelsWithoutClosingAndErrorsAllowRetry() {
        try (ActivityScenario<LibraryImportActivity> scenario = ActivityScenario.launch(LibraryImportActivity.class)) {
            scenario.onActivity(activity -> {
                LibraryImportModel model = new ViewModelProvider(activity).get(LibraryImportModel.class);
                model.state.setValue(new LibraryImportModel.State(true, "Reading library...", 0, 0, null, null));
                activity.onBackPressed();
                assertFalse(activity.isFinishing());
                assertNotNull(find(activity.getWindow().getDecorView(), "Stopping safely..."));
                model.state.setValue(new LibraryImportModel.State(false, "Cannot open backup", 0, 0, null, null));
                assertTrue(find(activity.getWindow().getDecorView(), "Restore backup").isShown());
                assertNotNull(find(activity.getWindow().getDecorView(), "Cannot open backup"));
            });
        }
    }
    private void idle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        android.os.SystemClock.sleep(150);
    }
    private static View find(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View found = find(((ViewGroup) view).getChildAt(i), text); if (found != null) return found;
        }
        return null;
    }
    private static void click(View root, String label) {
        View view = find(root, label); assertNotNull(label, view);
        while (!view.isClickable() && view.getParent() instanceof View) view = (View) view.getParent();
        assertTrue(view.isEnabled()); view.performClick();
    }
    private static RecyclerView recycler(View view) {
        if (view instanceof RecyclerView) return (RecyclerView) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            RecyclerView result = recycler(((ViewGroup) view).getChildAt(i)); if (result != null) return result;
        }
        return null;
    }
}
