package ru.playsoftware.j2meloader.config;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import ru.playsoftware.j2meloader.input.GameMenuDialog;
import ru.playsoftware.j2meloader.settings.SettingsActivity;
import static org.junit.Assert.*;

public class GamePresetsUiTest {
    private ActivityScenario<SettingsActivity> scenario;
    private GameMenuDialog menu;
    private GameSettingsPages pages;
    private ProfileModel source, applied;
    private int backs;

    @Before public void setup() {
        scenario = ActivityScenario.launch(SettingsActivity.class);
        scenario.onActivity(activity -> {
            source = new ProfileModel(activity.getCacheDir());
            source.systemProperties = "microedition.platform: My custom phone\nmy.option: keep\n";
            source.screenWidth = 176; source.screenHeight = 220;
            menu = new GameMenuDialog(activity, "Preset fixture"); menu.show();
            pages = new GameSettingsPages(menu, source, true, new GameSettingsPages.Host() {
                public void preview(ProfileModel draft) { applied = ProfilesManager.copy(draft); }
                public void mapping(ProfileModel draft, Runnable back) { }
                public void advanced() { }
                public void back() { backs++; }
            });
        });
    }
    @After public void close() { scenario.onActivity(activity -> menu.dismiss()); scenario.close(); }
    private View root() { return menu.getWindow().getDecorView(); }
    private void idle() { InstrumentationRegistry.getInstrumentation().waitForIdleSync(); }
    private void choose(int index) {
        scenario.onActivity(activity -> ((Spinner) find(root(), Spinner.class, null)).setSelection(index)); idle();
    }

    @Test public void tactileFeedbackIsPerGameAndRequiresTryForEveryTouchLayout() {
        org.junit.Assume.assumeFalse(ru.playsoftware.j2meloader.BuildConfig.HANDHELD_MODE);
        scenario.onActivity(activity -> {
            for (boolean javaGame : new boolean[]{true, false}) for (int layout = 0; layout < (javaGame ? 3 : 2); layout++) {
                source.touchLayout = layout; source.vkFeedback = false; applied = null;
                GameSettingsPages editor = new GameSettingsPages(menu, source, false, javaGame, new GameSettingsPages.Host() {
                    public void preview(ProfileModel draft) { applied = ProfilesManager.copy(draft); }
                    public void mapping(ProfileModel draft, Runnable back) { }
                    public void advanced() { }
                    public void back() { }
                });
                editor.controls();
                find(root(), TextView.class, "Tactile feedback").performClick();
                assertFalse(source.vkFeedback); assertNull(applied); assertTrue(editor.draft().vkFeedback);
                assertTrue(find(root(), TextView.class, "Try in game").performClick());
                assertTrue(applied.vkFeedback); assertEquals(layout, applied.touchLayout.intValue());
            }
        });
    }

    @Test public void screenAutoFallbackIsReversibleAndRequiresTry() {
        scenario.onActivity(activity -> pages.screenPresets(() -> backs++)); idle();
        choose(0);
        scenario.onActivity(activity -> {
            assertEquals(176, source.screenWidth); assertNull(applied);
            assertEquals(240, pages.draft().screenWidth); assertEquals(320, pages.draft().screenHeight);
            assertTrue(find(root(), TextView.class, "Try changes").performClick());
            assertEquals(240, applied.screenWidth);
            assertEquals(source.systemProperties, applied.systemProperties);
        });
    }

    @Test public void frameRateRetainsCustomValuesAndRequiresTry() {
        scenario.onActivity(activity -> {
            pages.draft().fpsLimit = 37;
            pages.frameRate(() -> backs++);
        }); idle();
        scenario.onActivity(activity -> {
            Spinner selector = (Spinner) find(root(), Spinner.class, null);
            assertEquals("37 FPS", selector.getSelectedItem().toString());
        });
        choose(2); // 20 FPS; unchanged until Try.
        scenario.onActivity(activity -> {
            assertEquals(0, source.fpsLimit); assertNull(applied);
            assertTrue(find(root(), TextView.class, "Try changes").performClick());
            assertEquals(20, applied.fpsLimit);
        });
    }

    @Test public void phoneCurrentSelectionRestoresCustomPropertiesWithoutRestarting() {
        scenario.onActivity(activity -> pages.phonePresets(() -> backs++, draft -> applied = draft)); idle();
        choose(3); // Nokia, after Current / Auto / Generic.
        scenario.onActivity(activity -> assertTrue(pages.draft().systemProperties.contains("Nokia6233")));
        choose(0);
        scenario.onActivity(activity -> {
            assertEquals(source.systemProperties, pages.draft().systemProperties); assertNull(applied);
        });
    }

    @Test public void phonePresetOnlyReachesHostAfterExplicitRestartAction() {
        scenario.onActivity(activity -> pages.phonePresets(() -> backs++, draft -> applied = ProfilesManager.copy(draft))); idle();
        choose(4); // Sony Ericsson.
        scenario.onActivity(activity -> {
            assertNull(applied); assertTrue(source.systemProperties.contains("My custom phone"));
            assertTrue(find(root(), TextView.class, "Restart & try").performClick());
            assertTrue(applied.systemProperties.contains("SonyEricssonK800i"));
            assertEquals(176, applied.screenWidth); assertEquals(220, applied.screenHeight);
        });
    }

    private static View find(View view, Class<?> type, String text) {
        if (type.isInstance(view) && (text == null || text.contentEquals(((TextView) view).getText()))) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View child = find(((ViewGroup) view).getChildAt(i), type, text); if (child != null) return child;
        }
        return null;
    }
}
