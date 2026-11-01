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
                click(activity.getWindow().getDecorView(), "Game vibration");
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
                assertTrue(find(activity.getWindow().getDecorView(), backupLabel()).isShown());
                new ViewModelProvider(activity).get(LibraryImportModel.class).state.setValue(
                        new LibraryImportModel.State(false, "8 games found", 0, 0, preview, null));
            });
            idle();
            scenario.onActivity(activity -> {
                View root = activity.getWindow().getDecorView();
                assertFalse(find(root, backupLabel()).isShown());
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
                assertTrue(find(activity.getWindow().getDecorView(), restoreLabel()).isShown());
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
                assertTrue(find(activity.getWindow().getDecorView(), restoreLabel()).isShown());
                assertNotNull(find(activity.getWindow().getDecorView(), "Cannot open backup"));
            });
        }
    }

    @Test public void partialResultOffersReviewBeforeRetryAndSurvivesRecreation() {
        LibraryImporter.Preview remaining = new LibraryImporter.Preview();
        LibraryImporter.Entry entry = new LibraryImporter.Entry();
        entry.title = "Sudoku"; entry.vendor = "Test"; entry.version = "1";
        entry.selected = true; entry.hasSaves = true; remaining.entries.add(entry);
        try (ActivityScenario<LibraryImportActivity> scenario = ActivityScenario.launch(LibraryImportActivity.class)) {
            scenario.onActivity(activity -> new ViewModelProvider(activity).get(LibraryImportModel.class).state.setValue(
                    new LibraryImportModel.State(false, "Finished. 1 imported, 1 failed", 2, 2, remaining,
                            Arrays.asList("First game\nImported with saves", "Sudoku\nSource could not be read"))));
            idle(); scenario.recreate(); idle();
            scenario.onActivity(activity -> {
                View root = activity.getWindow().getDecorView();
                assertTrue(find(root, "Retry").isShown()); assertTrue(find(root, "Done").isShown());
                assertFalse(find(root, "Select all").isShown());
            });
            capture("transfer-partial-result.png");
            scenario.onActivity(activity -> click(activity.getWindow().getDecorView(), "Retry"));
            idle();
            scenario.onActivity(activity -> {
                LibraryImportModel.State state = new ViewModelProvider(activity).get(LibraryImportModel.class).state.getValue();
                assertFalse(state.busy); assertNull(state.results); assertSame(remaining, state.preview);
                assertTrue(find(activity.getWindow().getDecorView(), "Import (1)").isEnabled());
                assertFalse(find(activity.getWindow().getDecorView(), "Retry").isShown());
            });
            capture("transfer-retry-review.png");
        }
    }

    @Test public void sharedOnlyRetryRemainsAvailableWithoutGameSelection() {
        LibraryImporter.Preview remaining = new LibraryImporter.Preview(); remaining.sharedFiles = true;
        try (ActivityScenario<LibraryImportActivity> scenario = ActivityScenario.launch(LibraryImportActivity.class)) {
            scenario.onActivity(activity -> {
                new ViewModelProvider(activity).get(LibraryImportModel.class).state.setValue(
                        new LibraryImportModel.State(false, "Shared files could not be copied", 1, 1,
                                remaining, Arrays.asList("Fixture\nImported with saves")));
                click(activity.getWindow().getDecorView(), "Retry");
            });
            idle();
            scenario.onActivity(activity -> {
                View root = activity.getWindow().getDecorView();
                String shared = activity.getString(R.string.transfer_shared_files);
                assertTrue(find(root, shared).isShown()); assertTrue(find(root, shared).isEnabled());
                assertFalse(find(root, "Select all").isShown());
            });
        }
    }

    @Test public void cancelledEmptyResultDoesNotRenderPendingGamesAsResultRows() {
        LibraryImporter.Preview remaining = new LibraryImporter.Preview();
        LibraryImporter.Entry entry = new LibraryImporter.Entry(); entry.title = "Pending"; entry.selected = true;
        remaining.entries.add(entry);
        try (ActivityScenario<LibraryImportActivity> scenario = ActivityScenario.launch(LibraryImportActivity.class)) {
            scenario.onActivity(activity -> new ViewModelProvider(activity).get(LibraryImportModel.class).state.setValue(
                    new LibraryImportModel.State(false, "Stopped", 0, 1, remaining, java.util.Collections.emptyList())));
            idle();
            scenario.onActivity(activity -> {
                assertEquals(0, recycler(activity.findViewById(R.id.settings_content)).getAdapter().getItemCount());
                assertTrue(find(activity.getWindow().getDecorView(), "Retry").isShown());
            });
        }
    }

    private void capture(String name) {
        android.graphics.Bitmap screenshot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(screenshot);
        java.io.File file = new java.io.File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null), name);
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
            assertTrue(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out));
        } catch (java.io.IOException e) { throw new AssertionError(e); }
        finally { screenshot.recycle(); }
    }
    @Test public void folderAccessRecoverySurvivesRecreationAndShowsFullMessage() {
        android.net.Uri tree = android.provider.DocumentsContract.buildTreeDocumentUri("fixture.library", "root");
        LibraryImporter.FolderAccessRequired request = new LibraryImporter.FolderAccessRequired(
                android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, "root/data/game"), "data/game");
        try (ActivityScenario<LibraryImportActivity> scenario = ActivityScenario.launch(LibraryImportActivity.class)) {
            scenario.onActivity(activity -> new ViewModelProvider(activity).get(LibraryImportModel.class).state.setValue(
                    new LibraryImportModel.State(false, request.getMessage(), 0, 0, null, null, request)));
            idle(); scenario.recreate(); idle();
            scenario.onActivity(activity -> {
                View root = activity.getWindow().getDecorView();
                TextView message = (TextView) find(root, request.getMessage());
                assertNotNull(message); assertTrue(message.isShown()); assertNull(message.getEllipsize());
                assertTrue(message.getMaxLines() > 2);
                assertTrue(find(root, "Allow folder access").isEnabled());
                LibraryImportModel model = new ViewModelProvider(activity).get(LibraryImportModel.class);
                assertSame(request, model.state.getValue().access);
                model.allowFolder(android.provider.DocumentsContract.buildTreeDocumentUri("fixture.library", "wrong"));
                assertSame(request, model.state.getValue().access);
                assertTrue(model.state.getValue().status.startsWith("A different folder"));
            });
        }
    }

    private static String backupLabel() {
        return ru.playsoftware.j2meloader.BuildConfig.SYMBIAN_EXPERIMENT ? "Back up Java / Mophun" : "Back up library";
    }
    private static String restoreLabel() {
        return ru.playsoftware.j2meloader.BuildConfig.SYMBIAN_EXPERIMENT ? "Restore Java / Mophun backup" : "Restore backup";
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
