package ru.playsoftware.j2meloader.input;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import androidx.core.graphics.ColorUtils;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.config.ProfilesManager;
import ru.playsoftware.j2meloader.ui.AppPalette;
import static org.junit.Assert.*;

public class VisualPolishTest {
    @Test public void captureExistingLibraryAndEditorWhenRequested() throws Exception {
        org.junit.Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("visualCaptures")));
        android.content.SharedPreferences prefs=androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
        String oldStyle=prefs.getString(AppPalette.PREFERENCE,null),oldTheme=prefs.getString("pref_theme",null);
        int oldView=prefs.getInt("pref_library_view_mode",0);
        String brightness=InstrumentationRegistry.getArguments().getString("captureBrightness", "dark");
        try {
            for(String style:AppPalette.VALUES) {
                main(()->prefs.edit().putString(AppPalette.PREFERENCE,style).putString("pref_theme",brightness).commit());
                for(int mode=0;mode<3;mode++) {
                final int view=mode;
                main(()->prefs.edit().putInt("pref_library_view_mode",view).commit());
                try(androidx.test.core.app.ActivityScenario<ru.playsoftware.j2meloader.MainActivity> scenario=
                            androidx.test.core.app.ActivityScenario.launch(ru.playsoftware.j2meloader.MainActivity.class)) {
                    capture(style+"-library-"+new String[]{"gallery","list","grid"}[mode]);
                }
                }
                try(androidx.test.core.app.ActivityScenario<ru.playsoftware.j2meloader.settings.SettingsActivity> scenario=
                            androidx.test.core.app.ActivityScenario.launch(ru.playsoftware.j2meloader.settings.SettingsActivity.class)) {
                    final TouchLayoutEditor[] editor={null};
                    scenario.onActivity(activity->{
                        ProfileModel p=new ProfileModel(); p.touchLayout=0; p.touchTheme=1;
                        editor[0]=new TouchLayoutEditor(activity,p,()->{}); editor[0].show();
                    });
                    capture(style+"-editor");
                    scenario.onActivity(activity->editor[0].dismiss());
                }
            }
        } finally {
            main(()->{
                android.content.SharedPreferences.Editor edit=prefs.edit();
                if(oldStyle==null) edit.remove(AppPalette.PREFERENCE); else edit.putString(AppPalette.PREFERENCE,oldStyle);
                if(oldTheme==null) edit.remove("pref_theme"); else edit.putString("pref_theme",oldTheme);
                edit.commit();
                prefs.edit().putInt("pref_library_view_mode",oldView).commit();
            });
        }
    }
    private void capture(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync(); android.os.SystemClock.sleep(1200);
        Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot(); assertNotNull(bitmap);
        java.io.File dir=new java.io.File(context.getExternalFilesDir(null),"visual-polish"); assertTrue(dir.isDirectory()||dir.mkdirs());
        try(java.io.FileOutputStream stream=new java.io.FileOutputStream(new java.io.File(dir,name+".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,stream));
        } finally { bitmap.recycle(); }
    }
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    @Test public void engineAppearanceSnapshotReadsCurrentMainProcessPreference() {
        android.content.SharedPreferences prefs=androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
        String previous=prefs.getString(AppPalette.PREFERENCE,null);
        try {
            for(String style:AppPalette.VALUES) {
                assertTrue(prefs.edit().putString(AppPalette.PREFERENCE,style).commit());
                assertEquals(style,ru.playsoftware.j2meloader.util.AppearanceContext.refresh(context).getString("palette"));
            }
        } finally {
            android.content.SharedPreferences.Editor edit=prefs.edit();
            if(previous==null) edit.remove(AppPalette.PREFERENCE); else edit.putString(AppPalette.PREFERENCE,previous);
            assertTrue(edit.commit());
        }
    }
    private void main(Runnable action) { InstrumentationRegistry.getInstrumentation().runOnMainSync(action); }
    private TouchDeck deck(ProfileModel profile) {
        return new TouchDeck(profile,1,new TouchDeck.Sink() {
            public void press(int key) { } public void release(int key) { } public void repeat(int key) { }
            public void invalidate() { } public void feedback() { }
        });
    }
    @Test public void palettesHaveReadableTextAndDrawableStatesInBothBrightnessModes() {
        main(()->{
            for(String style:AppPalette.VALUES) for(int mode:new int[]{Configuration.UI_MODE_NIGHT_NO,Configuration.UI_MODE_NIGHT_YES}) {
                Context c=AppPalette.preview(context,style,mode);
                for(int background:new int[]{R.color.background,R.color.phone_panel,R.color.config_card,R.color.touch_button,R.color.selected_surface}) {
                    for(int text:new int[]{R.color.text_primary,R.color.text_secondary})
                        assertTrue(style+" / "+mode+" / "+background+" / "+text,
                                ColorUtils.calculateContrast(AppPalette.color(c,text),AppPalette.color(c,background))>=4.5);
                }
                assertTrue(ColorUtils.calculateContrast(AppPalette.color(c,R.color.on_accent),AppPalette.color(c,R.color.accent))>=4.5);
                for(int resource:new int[]{R.drawable.bg_game_card,R.drawable.bg_card_retro,R.drawable.bg_card_contrast,R.drawable.bg_touch_shortcut}) {
                    android.graphics.drawable.Drawable drawable=c.getDrawable(resource);
                    assertNotNull(drawable); drawable.setBounds(0,0,160,80);
                    Bitmap image=Bitmap.createBitmap(160,80,Bitmap.Config.ARGB_8888);
                    for(int[] state:new int[][]{new int[]{},new int[]{android.R.attr.state_focused},new int[]{android.R.attr.state_pressed}}) {
                        image.eraseColor(Color.TRANSPARENT); drawable.setState(state); drawable.draw(new Canvas(image));
                        assertNotEquals(Color.TRANSPARENT,image.getPixel(80,40));
                    } image.recycle();
                }
            }
        });
    }
    @Test public void transformedKeysStayInsideControlLanesAndOutOfTheGame() {
        main(()->{
            for(int layout:new int[]{TouchDeck.PHONE,TouchDeck.GAMEPAD}) for(int[] size:new int[][]{{360,640},{640,360},{240,240},{480,320}}) {
                ProfileModel p=new ProfileModel(); p.touchLayout=layout; TouchDeck deck=deck(p); deck.layout(size[0],size[1]);
                for(int g=0;g<deck.groupCount();g++) { TouchPlacement place=deck.placement(g); place.scale=1.2f; place.x=1; place.y=1; }
                deck.layout(size[0],size[1]);
                for(float scale:new float[]{.7f,1.2f}) {
                    for(int g=0;g<deck.groupCount();g++) {deck.placement(g).scale=scale; deck.moveGroup(g,-999,999);}
                    RectF game=deck.gameArea(size[0],size[1]);
                    java.util.List<TouchDeck.Key> keys=deck.getKeys();
                    for(int i=0;i<keys.size();i++) {
                        RectF b=keys.get(i).bounds;
                        assertTrue(b.toString(),b.left>=0 && b.top>=0 && b.right<=size[0]+.01 && b.bottom<=size[1]+.01);
                        assertFalse(RectF.intersects(b,game));
                        for(int j=i+1;j<keys.size();j++) {
                            RectF inner=new RectF(b); inner.inset(.1f,.1f);
                            assertFalse("Overlapping touch targets",RectF.intersects(inner,keys.get(j).bounds));
                        }
                    }
                }
                deck.cancel();
            }
        });
    }
    @Test public void layoutDraftIsDetachedAndRoundTripsWithIndependentOrientations() {
        main(()->{
            ProfileModel original=new ProfileModel(); original.touchLayout=0;
            ProfileModel draft=ProfilesManager.copy(original); TouchDeck deck=deck(draft);
            deck.layout(360,640); deck.placement(0).scale=.75f; String portrait=deck.placementId(0);
            deck.layout(640,360); deck.placement(0).scale=.9f; String landscape=deck.placementId(0);
            assertNull(original.touchPlacements);
            TouchLayoutEditor.copyLayout(draft,original);
            draft.touchPlacements.get(portrait).scale=1;
            assertEquals(.75f,original.touchPlacements.get(portrait).scale,.001f);
            assertEquals(.9f,original.touchPlacements.get(landscape).scale,.001f);
            assertEquals(.75f,ProfilesManager.copy(original).touchPlacements.get(portrait).scale,.001f);
            deck.cancel();
        });
    }
    @Test public void editorCancelDoesNotChangeProfile() {
        try(androidx.test.core.app.ActivityScenario<ru.playsoftware.j2meloader.settings.SettingsActivity> scenario=
                    androidx.test.core.app.ActivityScenario.launch(ru.playsoftware.j2meloader.settings.SettingsActivity.class)) {
            scenario.onActivity(activity->{
                ProfileModel original=new ProfileModel(); original.touchLayout=0;
                TouchLayoutEditor editor=new TouchLayoutEditor(activity,original,()->fail("Cancel must not accept"));
                editor.show(); assertTrue(editor.isShowing()); editor.cancel(); assertNull(original.touchPlacements);
            });
        }
    }
}
