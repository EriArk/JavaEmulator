// SPDX-License-Identifier: GPL-3.0-only
package ru.playsoftware.j2meloader.catalog;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.LocaleList;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.config.GameSettingsPages;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.input.GameMenuDialog;
import ru.playsoftware.j2meloader.input.GameMenuPages;
import ru.playsoftware.j2meloader.settings.SettingsActivity;
import static org.junit.Assert.*;

/** Isolated pseudolocales: never change device language, guest Locale or saved app preferences. */
public class LocalizationJourneyTest {
    private Context localized(Context context, String language, float font) {
        Configuration override = new Configuration();
        override.setLocales(LocaleList.forLanguageTags(language)); override.fontScale = font;
        ContextThemeWrapper wrapper = new ContextThemeWrapper(context, R.style.AppTheme);
        wrapper.applyOverrideConfiguration(override);
        return wrapper;
    }

    @Test public void pseudoTranslationPreservesUserValuesAndDoesNotMutateLocale() {
        Context base = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Locale original = Locale.getDefault();
        Context context = localized(base, "en-XA", 1);
        assertNotEquals("Game menu", context.getString(R.string.game_menu_title));
        String value = "RH-29/Green Farm 3/%20_my-save.zip";
        assertTrue(context.getString(R.string.library_play_last, value).contains(value));
        assertTrue(context.getString(R.string.installer_use_copy, value, "01.00", "entry-42").contains(value));
        assertTrue(context.getString(R.string.installer_use_copy, value, "01.00", "entry-42").contains("01.00"));
        assertTrue(context.getString(R.string.transfer_not_imported, value, "EACCES").contains("EACCES"));
        assertEquals(original, Locale.getDefault());
    }

    @Test public void localizedChoiceArraysKeepAllStoredValueIndices() {
        Context base = InstrumentationRegistry.getInstrumentation().getTargetContext();
        int[] arrays = {R.array.game_picture_sizes, R.array.game_native_picture_sizes, R.array.game_orientations,
                R.array.game_control_modes, R.array.game_touch_visibility_modes, R.array.game_touch_layouts,
                R.array.game_native_touch_layouts, R.array.game_keypad_placements, R.array.game_button_themes,
                R.array.game_button_sizes, R.array.game_touch_positions, R.array.game_capture_modes,
                R.array.game_cpu_budgets, R.array.game_lcd_effects, R.array.game_screenshot_modes,
                R.array.game_forward_rates, R.array.game_forward_buttons, R.array.game_forward_modes,
                R.array.game_image_presets, R.array.game_software_image_presets, R.array.game_display_native_presets,
                R.array.game_display_gpu_presets, R.array.game_display_software_presets,
                R.array.library_collections, R.array.library_view_modes, R.array.library_generations,
                R.array.library_cover_formats, R.array.transfer_folder_titles, R.array.transfer_folder_hints};
        int[] counts = {4,3,3,3,3,3,2,2,3,3,2,2,4,3,2,4,11,2,6,3,3,6,3,3,3,3,3,3,3};
        for (String language : new String[]{"en", "es", "pt-BR", "en-XA", "ar-XB", "ru", "fr", "de", "ja", "zh-CN"}) {
            Context context = localized(base, language, 1);
            for (int i = 0; i < arrays.length; i++) {
                String[] values = context.getResources().getStringArray(arrays[i]);
                assertEquals(language + ": " + base.getResources().getResourceEntryName(arrays[i]), counts[i], values.length);
                for (String value : values) assertFalse(value.trim().isEmpty());
            }
        }
    }

    @Test public void transferStateIsIndependentOfTranslatedStatus() {
        assertTrue(LibraryImportModel.State.idle("Choose a source in any language").idle);
        assertFalse(new LibraryImportModel.State(false, "No folder selected", 0, 0, null, null).idle);
    }

    @Test public void translatedMapperKeepsProfileNamesAndKeyCodes() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = localized(InstrumentationRegistry.getInstrumentation().getTargetContext(), "en-XA", 1);
            java.util.ArrayList<ProfileModel.KeyMappingProfile> original = javax.microedition.lcdui.keyboard.KeyMapper.createBuiltInProfiles();
            String before = new com.google.gson.Gson().toJson(original);
            int[] saves = {0};
            ru.playsoftware.j2meloader.input.ControllerMapperView editor = new ru.playsoftware.j2meloader.input.ControllerMapperView(
                    context, "My game 50%", original, 0, new ru.playsoftware.j2meloader.input.ControllerMapperView.Listener() {
                public void cancel() { fail("Unexpected cancel"); }
                public void save(java.util.ArrayList<ProfileModel.KeyMappingProfile> profiles, int active) {
                    assertEquals("Native arrows", profiles.get(0).name);
                    assertEquals("Custom 1", profiles.get(active).name);
                    assertEquals(55, profiles.get(active).mappings.get(96)); saves[0]++;
                }
            });
            description(editor, context.getString(R.string.controls_phone_key, "7")).performClick();
            text(editor, context.getString(R.string.mapper_save)).performClick();
            assertEquals(1, saves[0]);
            assertEquals(before, new com.google.gson.Gson().toJson(original));
        });
    }

    @Test public void pluralCountsAndTechnicalValuesStayIntact() {
        Context context = localized(InstrumentationRegistry.getInstrumentation().getTargetContext(), "en", 1);
        assertEquals("1 game found", context.getResources().getQuantityString(R.plurals.transfer_games_found, 1, 1));
        assertEquals("2 games found", context.getResources().getQuantityString(R.plurals.transfer_games_found, 2, 2));
        assertEquals("Backup saved: 1 game with saves and settings", context.getResources().getQuantityString(R.plurals.transfer_backup_saved, 1, 1));
        assertEquals("Emulation speed: 150%", context.getString(R.string.game_speed_value, 150));
        assertEquals("Unlimited", GameSettingsPages.frameRateLabel(context, 0));
        assertEquals("45 FPS", GameSettingsPages.frameRateLabel(context, 45));
        Context russian = localized(context, "ru", 1);
        assertTrue(russian.getString(R.string.alert_msg_workdir_not_exists, "/Games/My archive").contains("/Games/My archive"));
    }

    @Test public void resolutionSelectionNeverParsesTranslatedLabels() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            GameMenuDialog[] menu = {null}; GameSettingsPages[] pages = {null}; Spinner[] spinner = {null};
            try {
                scenario.onActivity(activity -> {
                    Context context = localized(activity, "ar", 1);
                    menu[0] = new GameMenuDialog(context, "Fixture"); menu[0].show();
                    ProfileModel profile = new ProfileModel(); profile.screenWidth = 333; profile.screenHeight = 444;
                    profile.detectedScreenWidth = 176; profile.detectedScreenHeight = 208;
                    pages[0] = new GameSettingsPages(menu[0], profile, false, host());
                    pages[0].screenPresets(() -> {});
                    spinner[0] = (Spinner) description(menu[0].getWindow().getDecorView(), context.getString(R.string.game_phone_resolution));
                    assertNotNull(spinner[0]);
                    assertEquals(333, pages[0].draft().screenWidth);
                    spinner[0].setSelection(1);
                });
                idle();
                scenario.onActivity(activity -> {
                    assertEquals(128, pages[0].draft().screenWidth); assertEquals(128, pages[0].draft().screenHeight);
                    spinner[0].setSelection(0);
                });
                idle();
                scenario.onActivity(activity -> {
                    assertEquals(176, pages[0].draft().screenWidth); assertEquals(208, pages[0].draft().screenHeight);
                });
            } finally { scenario.onActivity(activity -> { if (menu[0] != null) menu[0].dismiss(); }); }
        }
    }

    @Test public void expandedMenuTextAtDoubleFontUsesFullWidthChoices() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            GameMenuDialog[] menu = {null};
            try {
                scenario.onActivity(activity -> {
                    Context context = localized(activity, "en-XA", 2);
                    menu[0] = new GameMenuDialog(context, "Metal Slug 3 / user title");
                    menu[0].useEdgePanel(); menu[0].show();
                    new GameSettingsPages(menu[0], new ProfileModel(), false, host()).display();
                });
                idle();
                scenario.onActivity(activity -> {
                    View root = menu[0].getWindow().getDecorView();
                    Spinner spinner = (Spinner) description(root, menu[0].text(R.string.game_fps_limit));
                    assertNotNull(spinner);
                    LinearLayout row = (LinearLayout) spinner.getParent();
                    assertEquals(LinearLayout.VERTICAL, row.getOrientation());
                    assertEquals(row.getWidth() - row.getPaddingLeft() - row.getPaddingRight(), spinner.getWidth());
                    TextView label = (TextView) row.getChildAt(0);
                    assertNotNull(label.getLayout());
                    assertEquals(0, label.getLayout().getEllipsisCount(label.getLineCount() - 1));
                    assertTrue(label.getHeight() >= label.getLayout().getHeight() + label.getCompoundPaddingTop() + label.getCompoundPaddingBottom());
                    capture(root, activity, "expanded-display-200.png");
                });
            } finally { scenario.onActivity(activity -> { if (menu[0] != null) menu[0].dismiss(); }); }
        }
    }

    @Test public void translatedConfirmationKeepsEngineCallbacksSeparate() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
          for(String tag:new String[]{"en-XA","fr","de","ja","zh-CN"}) {
            scenario.onActivity(activity -> {
                GameMenuDialog menu = new GameMenuDialog(localized(activity, tag, 1), "Guest title");
                menu.show(); final int[] exits = {0};
                try {
                    GameMenuPages pages = new GameMenuPages(menu, new GameMenuPages.Host() {
                        public void display() {} public void controls() {} public void more() {}
                        public void exit() { exits[0]++; } public void restart() { exits[0] += 10; }
                        public boolean hasPreview() { return true; } public void keep() {} public void undo() {}
                    }, null);
                    pages.confirmRestart();
                    assertNotNull(text(menu.getWindow().getDecorView(), menu.text(R.string.game_restart_preview_warning)));
                    text(menu.getWindow().getDecorView(), menu.text(R.string.game_cancel)).performClick();
                    assertEquals(0, exits[0]); assertTrue(menu.isShowing());
                    pages.confirmExit();
                    text(menu.getWindow().getDecorView(), menu.text(R.string.game_leave_without_saving)).performClick();
                    assertEquals(1, exits[0]);
                } finally { menu.dismiss(); }
            });
          }
        }
    }

    @Test public void longChoiceWordsStackAndRestoreColumnsWhenWidthChanges() {
        try(ActivityScenario<SettingsActivity> scenario=ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity -> {
                Context context=localized(activity,"de",1);
                GameMenuDialog menu=new GameMenuDialog(context,"Fixture");menu.show();
                try {
                    String label=context.getString(R.string.game_speed);
                    menu.choice(label,new String[]{"100%"},0,index->fail("Layout must not change selection"));
                    Spinner spinner=(Spinner)description(menu.getWindow().getDecorView(),label);
                    LinearLayout row=(LinearLayout)spinner.getParent();
                    TextView name=(TextView)row.getChildAt(0);
                    int width=(int)Math.ceil(name.getPaint().measureText(label)*1.5f);
                    row.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
                    row.layout(0,0,width,row.getMeasuredHeight());
                    assertEquals(LinearLayout.VERTICAL,row.getOrientation());
                    assertEquals(1,name.getLineCount());assertEquals(width,spinner.getWidth());
                    assertTrue(spinner.getTop()>=name.getBottom());
                    width*=2;
                    row.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
                    row.layout(0,0,width,row.getMeasuredHeight());
                    if(context.getResources().getConfiguration().screenWidthDp>=384)
                        assertEquals(LinearLayout.HORIZONTAL,row.getOrientation());
                    assertEquals(0,spinner.getSelectedItemPosition());
                } finally {menu.dismiss();}
            });
        }
    }

    @Test public void cjkChoiceLabelsWrapWithoutClippingOrChangingSelection() {
        try(ActivityScenario<SettingsActivity> scenario=ActivityScenario.launch(SettingsActivity.class)) {
            for(String tag:new String[]{"ja","zh-CN"})
            for(int widthDp:new int[]{320,480,720})
            for(float font:new float[]{1f,1.5f,2f}) {
                scenario.onActivity(activity -> {
                    Context context=localized(activity,tag,font);
                    GameMenuDialog menu=new GameMenuDialog(context,"Guest title / 50%");menu.show();
                    try {
                        String label=context.getString(R.string.settings_keep_awake);
                        menu.choice(label,new String[]{"100%","200%"},1,index->fail("Layout must not change selection"));
                        Spinner spinner=(Spinner)description(menu.getWindow().getDecorView(),label);
                        LinearLayout row=(LinearLayout)spinner.getParent();
                        int width=Math.round(widthDp*context.getResources().getDisplayMetrics().density);
                        row.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
                        row.layout(0,0,width,row.getMeasuredHeight());
                        TextView name=(TextView)row.getChildAt(0);
                        android.text.Layout layout=name.getLayout();
                        assertNotNull(tag,layout);
                        assertEquals(label,name.getText().toString());
                        assertTrue(tag,layout.getHeight()<=name.getHeight()-name.getCompoundPaddingTop()-name.getCompoundPaddingBottom());
                        for(int line=0;line<layout.getLineCount();line++) {
                            assertEquals(tag,0,layout.getEllipsisCount(line));
                            assertTrue(tag,layout.getLineWidth(line)<=layout.getWidth()+1);
                        }
                        assertTrue(tag,spinner.getRight()<=width);
                        assertEquals(1,spinner.getSelectedItemPosition());
                    } finally {menu.dismiss();}
                });
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            }
        }
    }

    @Test public void dismissAfterHostRecreationFinishesPreviewOnlyOnce() {
        GameMenuDialog[] menu={null};int[] cleanups={0};
        try(ActivityScenario<SettingsActivity> scenario=ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity -> {
                menu[0]=new GameMenuDialog(activity,"Guest title");
                menu[0].useEdgePanel();menu[0].show();
                menu[0].setPageCleanup(()->cleanups[0]++);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertFalse(menu[0].getWindow().getDecorView().isAttachedToWindow());
                assertFalse(menu[0].isShowing());
                assertEquals(1,cleanups[0]);
                menu[0].dismiss();menu[0].dismiss();
                assertEquals(1,cleanups[0]);
            });
        }
    }

    private static GameSettingsPages.Host host() {
        return new GameSettingsPages.Host() {
            public void preview(ProfileModel draft) {} public void mapping(ProfileModel draft, Runnable back) {}
            public void advanced() {} public void back() {}
        };
    }
    private static View description(View view, String value) {
        if (value.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View found = description(((ViewGroup) view).getChildAt(i), value); if (found != null) return found;
        }
        return null;
    }
    private static View text(View view, String value) {
        if (view instanceof TextView && value.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View found = text(((ViewGroup) view).getChildAt(i), value); if (found != null) return found;
        }
        return null;
    }
    private static void idle() { InstrumentationRegistry.getInstrumentation().waitForIdleSync(); android.os.SystemClock.sleep(150); }
    private static void capture(View view, Context context, String name) {
        if (!"true".equals(InstrumentationRegistry.getArguments().getString("captureUi"))) return;
        File dir = new File(context.getExternalFilesDir(null), "localization"); dir.mkdirs();
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new android.graphics.Canvas(bitmap));
        try (FileOutputStream output = new FileOutputStream(new File(dir, name))) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)); }
        catch (Exception e) { throw new AssertionError(e); }
        finally { bitmap.recycle(); }
    }
}
