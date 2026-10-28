package ru.playsoftware.j2meloader.input;

import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.config.ProfilesManager;
import ru.playsoftware.j2meloader.settings.SettingsActivity;
import static org.junit.Assert.*;

public class GameHandlePlacementTest {
    @Test public void oldProfileDefaultsAndIndependentOrientationsSurviveCopyAndRollback() {
        ProfileModel original = new com.google.gson.Gson().fromJson("{}", ProfileModel.class);
        assertTrue(original.gameHandleVisible); assertEquals(100, original.gameShortcutOpacity);
        ProfileModel draft = ProfilesManager.copy(original);
        RectF safe = new RectF(12, 24, 348, 616);
        GameHandlePlacement.move(draft, false, safe, 48, -999, 999);
        GameHandlePlacement.move(draft, true, safe, 48, -100, 80);
        assertNull(original.gameHandlePortrait);
        ProfilesManager.beginPreview(original);
        ProfilesManager.copyInto(draft, original);
        assertEquals(0, original.gameHandlePortrait.x, 0);
        assertEquals(1, original.gameHandlePortrait.y, 0);
        assertTrue(original.gameHandleLandscape.x > 0);
        assertNotSame(draft.gameHandlePortrait, original.gameHandlePortrait);
        ProfilesManager.undoPreview(original);
        assertNull(original.gameHandlePortrait); assertNull(original.gameHandleLandscape);
    }

    @Test public void smallSafeViewportsClampCorruptPositionsAndPreserveDefaultCorner() {
        ProfileModel profile = new ProfileModel();
        for (RectF safe : new RectF[]{new RectF(24, 30, 600, 900), new RectF(16, 0, 700, 300),
                new RectF(10, 12, 35, 40), new RectF(0, 0, 240, 240)}) {
            profile.gameHandlePortrait = null;
            RectF bounds = GameHandlePlacement.bounds(profile, false, safe, 48);
            assertEquals(safe.top, bounds.top, 0); assertEquals(safe.right, bounds.right, 0);
            GameHandlePlacement.move(profile, false, safe, 48, -99999, 99999);
            bounds = GameHandlePlacement.bounds(profile, false, safe, 48);
            assertTrue(safe.contains(bounds));
            profile.gameHandlePortrait.x = Float.NaN; profile.gameHandlePortrait.y = Float.POSITIVE_INFINITY;
            bounds = GameHandlePlacement.bounds(profile, false, safe, 48);
            assertTrue(safe.contains(bounds));
            assertEquals(safe.top, bounds.top, 0); assertEquals(safe.right, bounds.right, 0);
        }
    }

    @Test public void editorCancelRotationAndUseOnlyChangeHandleDraft() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity -> {
                ProfileModel target = new ProfileModel(); target.touchLayout = 1; target.touchOpacity = 73;
                TouchLayoutEditor editor = new TouchLayoutEditor(activity, target, () -> fail("Canceled"), true);
                editor.show();
                find(editor.getWindow().getDecorView(), "Move left").performClick();
                android.os.Bundle state = editor.onSaveInstanceState();
                editor.cancel(); assertNull(target.gameHandlePortrait); assertNull(target.gameHandleLandscape);
                int[] accepted = {0};
                TouchLayoutEditor restored = new TouchLayoutEditor(activity, target, () -> accepted[0]++, true);
                restored.show(); restored.onRestoreInstanceState(state);
                find(restored.getWindow().getDecorView(), "Use layout").performClick();
                assertEquals(1, accepted[0]);
                assertTrue(target.gameHandlePortrait != null || target.gameHandleLandscape != null);
                assertEquals(Integer.valueOf(1), target.touchLayout); assertEquals(73, target.touchOpacity);
                assertNull(target.touchPlacements);
            });
        }
    }

    @Test public void opacityClampsAndDoesNotChangeTheStoredChoice() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            assertEquals(20, TouchAppearance.opacity(null, -1));
            assertEquals(100, TouchAppearance.opacity(null, 500));
            ProfileModel profile = new ProfileModel(); profile.touchOpacity = 30;
            android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
            String previous = prefs.getString("pref_palette", null);
            try {
                prefs.edit().putString("pref_palette", "contrast").commit();
                assertEquals(100, TouchAppearance.opacity(context, profile.touchOpacity));
                assertEquals(30, profile.touchOpacity);
                prefs.edit().putString("pref_palette", "classic").commit();
                assertEquals(30, TouchAppearance.opacity(context, profile.touchOpacity));
            } finally {
                if (previous == null) prefs.edit().remove("pref_palette").commit();
                else prefs.edit().putString("pref_palette", previous).commit();
            }
        });
    }

    @Test public void opacityAffectsPhoneAndGamepadWithoutChangingGameAreaOrKeys() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (int layout : new int[]{0, 1}) for (boolean overlay : new boolean[]{false, true}) {
                ProfileModel profile = new ProfileModel(); profile.touchLayout = layout; profile.touchKeypadOverlay = overlay;
                TouchDeck deck = new TouchDeck(profile, 1, new TouchDeck.Sink() {
                    public void press(int key) { } public void release(int key) { } public void repeat(int key) { }
                    public void feedback() { } public void invalidate() { }
                });
                deck.layout(360, 640);
                java.util.ArrayList<TouchDeck.Key> keys = deck.getKeys(); assertFalse(keys.isEmpty());
                RectF area = deck.gameArea(360, 640);
                android.graphics.Bitmap full = android.graphics.Bitmap.createBitmap(360, 640, android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Bitmap low = android.graphics.Bitmap.createBitmap(360, 640, android.graphics.Bitmap.Config.ARGB_8888);
                try {
                    deck.draw(new android.graphics.Canvas(full));
                    profile.touchOpacity = 20; deck.draw(new android.graphics.Canvas(low));
                    assertFalse(full.sameAs(low));
                    assertEquals(area, deck.gameArea(360, 640));
                    assertEquals(keys.get(0).bounds, deck.getKeys().get(0).bounds);
                    assertEquals(full.getPixel(180, 20), low.getPixel(180, 20));
                } finally { full.recycle(); low.recycle(); deck.cancel(); }
            }
        });
    }

    private static View find(View view, String description) {
        if (description.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View found = find(((ViewGroup) view).getChildAt(i), description); if (found != null) return found;
        }
        return null;
    }
}
