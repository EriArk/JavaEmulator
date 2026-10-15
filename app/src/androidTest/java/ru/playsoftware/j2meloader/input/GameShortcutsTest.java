package ru.playsoftware.j2meloader.input;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import androidx.test.core.app.ActivityScenario;
import org.junit.Test;
import ru.playsoftware.j2meloader.BuildConfig;
import ru.playsoftware.j2meloader.settings.SettingsActivity;
import ru.playsoftware.j2meloader.util.GameShortcuts;
import ru.playsoftware.j2meloader.util.Screenshots;
import static org.junit.Assert.*;

public class GameShortcutsTest {
    @Test public void sharedPageShowsOnlyAvailableShortcutsAndKeepsIndependentChoices() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity -> {
                boolean display = GameShortcuts.isDisplayVisible(activity), screenshot = Screenshots.isButtonVisible(activity);
                GameMenuDialog menu = new GameMenuDialog(activity, "Shortcut fixture");
                int[] refreshes = {0};
                try {
                    menu.show();
                    GameShortcuts.setDisplayVisible(activity, true); Screenshots.setButtonVisible(activity, true);
                    GameShortcuts.page(activity, menu, true, () -> refreshes[0]++, () -> {});
                    View root = menu.getWindow().getDecorView();
                    Switch displaySwitch = find(root, "Display button"), screenshotSwitch = find(root, "Screenshot button");
                    assertNotNull(screenshotSwitch);
                    if (BuildConfig.HANDHELD_MODE) assertNull(displaySwitch);
                    else {
                        assertTrue(displaySwitch.isChecked()); displaySwitch.performClick();
                        assertFalse(GameShortcuts.isDisplayVisible(activity));
                        assertTrue(Screenshots.isButtonVisible(activity));
                    }
                    screenshotSwitch.performClick(); assertFalse(Screenshots.isButtonVisible(activity));
                    assertEquals(BuildConfig.HANDHELD_MODE ? 1 : 2, refreshes[0]);
                    GameShortcuts.page(activity, menu, false, () -> refreshes[0]++, () -> {});
                    assertNull(find(root, "Display button"));
                    assertFalse(find(root, "Screenshot button").isChecked());
                    find(root, "Screenshot button").performClick(); assertTrue(Screenshots.isButtonVisible(activity));
                    assertEquals(BuildConfig.HANDHELD_MODE, GameShortcuts.isDisplayVisible(activity));
                } finally {
                    menu.dismiss(); GameShortcuts.setDisplayVisible(activity, display); Screenshots.setButtonVisible(activity, screenshot);
                }
            });
        }
    }

    private static Switch find(View view, String text) {
        if (view instanceof Switch && text.contentEquals(((Switch) view).getText())) return (Switch) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            Switch found = find(((ViewGroup) view).getChildAt(i), text); if (found != null) return found;
        }
        return null;
    }
}
