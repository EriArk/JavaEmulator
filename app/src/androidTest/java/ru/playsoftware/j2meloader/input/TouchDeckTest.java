package ru.playsoftware.j2meloader.input;

import android.graphics.RectF;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.content.Context;
import android.content.res.Configuration;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.gson.Gson;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.ArrayList;
import java.util.Arrays;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.R;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TouchDeckTest {
    @Test public void shortcutsSharePaletteWithoutChangingHostOrActions() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context original = InstrumentationRegistry.getInstrumentation().getTargetContext();
            Configuration config = new Configuration(original.getResources().getConfiguration());
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
            Context light = original.createConfigurationContext(config);
            android.widget.FrameLayout root = new android.widget.FrameLayout(light);
            int[] clicks = {0};
            for (int id : new int[]{R.id.game_display_button, R.id.game_screenshot_button, R.id.game_menu_button}) {
                android.widget.ImageButton button = new android.widget.ImageButton(light);
                button.setId(id); button.setOnClickListener(view -> clicks[0]++);
                root.addView(button);
            }
            ProfileModel profile = new ProfileModel();
            for (int theme : new int[]{TouchDeck.THEME_DARK, TouchDeck.THEME_LIGHT, TouchDeck.THEME_APP}) {
                profile.touchTheme = theme;
                TouchAppearance.shortcuts(root, profile);
                if (ru.playsoftware.j2meloader.BuildConfig.HANDHELD_MODE) continue;
                Context palette = TouchAppearance.palette(light, profile);
                for (int i = 0; i < root.getChildCount(); i++) {
                    android.widget.ImageButton button = (android.widget.ImageButton) root.getChildAt(i);
                    assertEquals(palette.getColor(R.color.text_primary),
                            androidx.core.widget.ImageViewCompat.getImageTintList(button).getDefaultColor());
                    for (boolean pressed : new boolean[]{false, true}) {
                        button.setPressed(pressed);
                        android.graphics.drawable.Drawable background = button.getBackground();
                        background.setBounds(0, 0, 96, 96);
                        Bitmap bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
                        background.draw(new Canvas(bitmap));
                        assertEquals(palette.getColor(pressed ? R.color.touch_surface : R.color.touch_button), bitmap.getPixel(48, 48));
                        bitmap.recycle();
                    }
                    button.performClick();
                }
            }
            assertEquals(ru.playsoftware.j2meloader.BuildConfig.HANDHELD_MODE ? 0 : 9, clicks[0]);
            assertEquals(Configuration.UI_MODE_NIGHT_NO, light.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK);
        });
    }
    private TouchDeck create(ProfileModel p, ArrayList<String> events) {
        return new TouchDeck(p,1,new TouchDeck.Sink() {
            public void press(int code) { events.add("+"+code); }
            public void release(int code) { events.add("-"+code); }
            public void repeat(int code) { events.add("r"+code); }
            public void feedback() { }
            public void invalidate() { }
        });
    }
    @Test public void layoutsReserveGameAreaAndDoNotOverlap() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (int style=0;style<2;style++) for (int size=0;size<3;size++) {
                ProfileModel p = new ProfileModel(); p.touchLayout=style; p.touchSize=size;
                TouchDeck deck=create(p,new ArrayList<>());
                for (int[] dimensions : new int[][]{{360,640},{640,360},{620,540},{320,568},{800,360},{412,915}}) {
                    int w=dimensions[0], h=dimensions[1];
                    deck.layout(w,h);
                    RectF area=deck.gameArea(w,h);
                    assertEquals("No reserved shortcut row", 8, area.top, .01f);
                    assertTrue(area.width()>0 && area.height()>0);
                    ArrayList<TouchDeck.Key> keys=deck.getKeys();
                    for (int i=0;i<keys.size();i++) {
                        RectF key=keys.get(i).bounds;
                        assertTrue(keys.get(i).label,key.left>=0 && key.top>=0 && key.right<=w+.01f && key.bottom<=h+.01f);
                        assertTrue("Touch target",key.width()>=47.9f && key.height()>=47.9f);
                        assertFalse("Game overlap",RectF.intersects(area,key));
                        for (int j=i+1;j<keys.size();j++) {
                            RectF inset = new RectF(key); inset.inset(.1f,.1f);
                            assertFalse("Overlapping keys",RectF.intersects(inset,keys.get(j).bounds));
                        }
                    }
                }
                deck.cancel();
            }
        });
    }
    @Test public void multiTouchAndSlidingKeepSharedKeysPressed() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            ProfileModel p=new ProfileModel(); p.touchLayout=1;
            ArrayList<String> events=new ArrayList<>();
            TouchDeck deck=create(p,events); deck.layout(800,360);
            TouchDeck.Key up=key(deck,"\u2191"), diagonal=key(deck,"\u2196");
            deck.down(0,up.bounds.centerX(),up.bounds.centerY());
            deck.down(1,up.bounds.centerX(),up.bounds.centerY());
            deck.up(0); assertEquals(Arrays.asList("+-1"),events);
            deck.move(1,diagonal.bounds.centerX(),diagonal.bounds.centerY());
            assertEquals(Arrays.asList("+-1","+-3"),events);
            deck.cancel();
            assertTrue(events.contains("--1")); assertTrue(events.contains("--3"));
            assertFalse(deck.up(1));
            events.clear();
            assertFalse(deck.down(31,400,100));
            assertFalse(deck.move(31,up.bounds.centerX(),up.bounds.centerY()));
            assertTrue(events.isEmpty());
        });
    }
    @Test public void rotateReleasesInputsAndSettingsRoundTrip() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            ProfileModel p=new ProfileModel(); p.touchLayout=0;
            p.touchPortraitReach=1; p.touchLandscapeReach=0;
            ArrayList<String> events=new ArrayList<>();
            TouchDeck deck=create(p,events); deck.layout(360,640);
            TouchDeck.Key five=key(deck,"5");
            deck.down(0,five.bounds.centerX(),five.bounds.centerY());
            deck.layout(640,360);
            assertEquals(Arrays.asList("+53","-53"),events);
            ProfileModel copy=new Gson().fromJson(new Gson().toJson(p),ProfileModel.class);
            assertEquals(0,copy.touchLayout.intValue());
            assertEquals(1,copy.touchPortraitReach,0);
            assertEquals(0,copy.touchLandscapeReach,0);
            assertEquals(100,copy.touchOpacity);
        });
    }
    private TouchDeck.Key key(TouchDeck deck,String label) {
        for (TouchDeck.Key key:deck.getKeys()) if (label.equals(key.label)) return key;
        throw new AssertionError(label);
    }

    @Test public void portraitGamepadCanHoldDirectionAndActionAndSwitchToNumbers() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            ProfileModel p=new ProfileModel(); p.touchLayout=1;
            ArrayList<String> events=new ArrayList<>();
            TouchDeck deck=create(p,events); deck.layout(360,640);
            TouchDeck.Key left=key(deck,"\u2190"), action=key(deck,"5");
            assertTrue(deck.down(0,left.bounds.centerX(),left.bounds.centerY()));
            assertTrue(deck.down(1,action.bounds.centerX(),action.bounds.centerY()));
            assertEquals(Arrays.asList("+-3","+53"),events);
            TouchDeck.Key numbers=key(deck,"123");
            deck.down(2,numbers.bounds.centerX(),numbers.bounds.centerY());
            assertTrue(events.contains("--3")); assertTrue(events.contains("-53"));
            for(String label:new String[]{"1","2","3","4","5","6","7","8","9","*","0","#"})
                assertNotNull(key(deck,label));
            deck.cancel();
        });
    }

    @Test public void buttonThemeCanOverrideAppAndChangeLiveWithoutChangingInputs() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (int night : new int[]{Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES}) {
                Context context = themed(night);
                for (int style : new int[]{TouchDeck.PHONE, TouchDeck.GAMEPAD}) {
                    ProfileModel profile = new ProfileModel(); profile.touchLayout = style;
                    ArrayList<String> events = new ArrayList<>();
                    TouchDeck deck = create(profile, events); deck.setTheme(context); deck.layout(360, 640);
                    TouchDeck.Key five = key(deck, "5");
                    RectF original = new RectF(five.bounds);
                    deck.down(0, five.bounds.centerX(), five.bounds.centerY());
                    for (int theme : new int[]{TouchDeck.THEME_DARK, TouchDeck.THEME_LIGHT, TouchDeck.THEME_APP, 99}) {
                        profile.touchTheme = theme;
                        int effective = theme == TouchDeck.THEME_DARK ? Configuration.UI_MODE_NIGHT_YES
                                : theme == TouchDeck.THEME_LIGHT ? Configuration.UI_MODE_NIGHT_NO : night;
                        Bitmap bitmap = Bitmap.createBitmap(360, 640, Bitmap.Config.ARGB_8888);
                        try {
                            deck.draw(new Canvas(bitmap));
                            assertEquals(themed(effective).getColor(R.color.touch_surface), bitmap.getPixel(0, 639));
                            assertEquals("App palette must not change", context.getColor(R.color.touch_surface),
                                    themed(night).getColor(R.color.touch_surface));
                            assertEquals(original, key(deck, "5").bounds);
                            assertEquals(Arrays.asList("+53"), events);
                        } finally { bitmap.recycle(); }
                    }
                    deck.up(0); assertEquals(Arrays.asList("+53", "-53"), events);
                    deck.cancel();
                }
            }
        });
    }

    @Test public void oldProfilesFollowAppAndNewThemeSurvivesSerialization() {
        Gson gson = new Gson();
        assertEquals(TouchDeck.THEME_APP, gson.fromJson("{}", ProfileModel.class).touchTheme);
        ProfileModel profile = new ProfileModel(); profile.touchTheme = TouchDeck.THEME_DARK;
        assertEquals(TouchDeck.THEME_DARK, gson.fromJson(gson.toJson(profile), ProfileModel.class).touchTheme);
    }

    @Test public void themeAndPressedKeyRenderingHasContrastInBothOrientations() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (int[] dimensions : new int[][]{{320, 568}, {360, 640}, {640, 360}}) {
                for (int style : new int[]{TouchDeck.PHONE, TouchDeck.GAMEPAD}) {
                    for (int theme : new int[]{TouchDeck.THEME_DARK, TouchDeck.THEME_LIGHT}) {
                        ProfileModel profile = new ProfileModel(); profile.touchLayout = style; profile.touchTheme = theme;
                        TouchDeck deck = create(profile, new ArrayList<>()); deck.setTheme(themed(Configuration.UI_MODE_NIGHT_NO));
                        deck.layout(dimensions[0], dimensions[1]);
                        TouchDeck.Key five = key(deck, "5");
                        Bitmap up = Bitmap.createBitmap(dimensions[0], dimensions[1], Bitmap.Config.ARGB_8888);
                        Bitmap down = Bitmap.createBitmap(dimensions[0], dimensions[1], Bitmap.Config.ARGB_8888);
                        try {
                            deck.draw(new Canvas(up));
                            deck.down(0, five.bounds.centerX(), five.bounds.centerY()); deck.draw(new Canvas(down));
                            int differences = 0, foreground = 0;
                            int expectedText = themed(theme == TouchDeck.THEME_DARK
                                    ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO).getColor(R.color.text_primary);
                            for (int y = (int) five.bounds.top; y < five.bounds.bottom; y++) {
                                for (int x = (int) five.bounds.left; x < five.bounds.right; x++) {
                                    if (up.getPixel(x, y) != down.getPixel(x, y)) differences++;
                                    if (up.getPixel(x, y) == expectedText) foreground++;
                                }
                            }
                            assertTrue("Pressed face must visibly change", differences > 200);
                            assertTrue("Digit must retain the palette foreground", foreground > 10);
                        } finally { deck.cancel(); up.recycle(); down.recycle(); }
                    }
                }
            }
        });
    }

    private Context themed(int night) {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
        return context.createConfigurationContext(config);
    }
}
