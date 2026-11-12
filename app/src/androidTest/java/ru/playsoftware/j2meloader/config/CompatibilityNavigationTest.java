package ru.playsoftware.j2meloader.config;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.input.GameMenuDialog;
import static org.junit.Assert.*;
import static ru.playsoftware.j2meloader.util.Constants.*;

public class CompatibilityNavigationTest {
    @Test public void compatibilityOpensEditorForPreparedAndFirstPreparationIntents() throws Exception {
        Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        File root = Files.createTempDirectory(inst.getTargetContext().getCacheDir().toPath(), "compat-nav-").toFile();
        File app = new File(root, "converted/fixture");
        File config = new File(root, "configs/fixture");
        assertTrue(app.mkdirs()); assertTrue(config.mkdirs());
        assertTrue(ProfilesManager.saveConfig(new ProfileModel(config)));
        Instrumentation.ActivityMonitor game = inst.addMonitor("javax.microedition.shell.MicroActivity", null, true);
        try {
            for (String action : new String[]{Intent.ACTION_DEFAULT, ACTION_EDIT, null}) {
                Instrumentation.ActivityMonitor editor = inst.addMonitor(ConfigActivity.class.getName(), null, false);
                Intent intent = new Intent(inst.getTargetContext(), GameSettingsActivity.class)
                        .setAction(action).setData(Uri.parse(app.getPath()))
                        .putExtra(KEY_MIDLET_NAME, "Navigation fixture").putExtra(KEY_START_ARGUMENTS, "preserved");
                Activity opened = null;
                try (ActivityScenario<GameSettingsActivity> scenario = ActivityScenario.launch(intent)) {
                    scenario.onActivity(activity -> {
                        try {
                            Field field = GameSettingsActivity.class.getDeclaredField("menu"); field.setAccessible(true);
                            GameMenuDialog menu = (GameMenuDialog) field.get(activity);
                            click(menu.getWindow().getDecorView(), activity.getString(R.string.game_advanced));
                            click(menu.getWindow().getDecorView(), activity.getString(R.string.game_compatibility_settings));
                        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                    });
                    opened = editor.waitForActivityWithTimeout(5000);
                    assertNotNull("Compatibility editor did not open for " + action, opened);
                    Activity target = opened;
                    inst.runOnMainSync(() -> {
                        assertNotNull("Editor must remain visible instead of launching the game", target.findViewById(R.id.whole_config_root));
                        assertEquals(app.getPath(), target.getIntent().getDataString());
                        assertEquals("preserved", target.getIntent().getStringExtra(KEY_START_ARGUMENTS));
                    });
                    assertEquals("Settings must not start a MIDlet", 0, game.getHits());
                } finally {
                    if (opened != null) { Activity target = opened; inst.runOnMainSync(target::finish); }
                    inst.removeMonitor(editor); inst.waitForIdleSync();
                }
            }
        } finally {
            inst.removeMonitor(game);
            try (java.util.stream.Stream<java.nio.file.Path> files = Files.walk(root.toPath())) {
                files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> { try { Files.delete(p); } catch (Exception e) { throw new RuntimeException(e); } });
            }
        }
    }
    private static void click(View view, String text) {
        View target = find(view, text); assertNotNull(text, target);
        while (!target.isClickable() && target.getParent() instanceof View) target = (View) target.getParent();
        assertTrue(text, target.performClick());
    }
    private static View find(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View found = find(((ViewGroup) view).getChildAt(i), text); if (found != null) return found;
        }
        return null;
    }
}
