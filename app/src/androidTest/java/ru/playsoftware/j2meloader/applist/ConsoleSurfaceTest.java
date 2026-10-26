package ru.playsoftware.j2meloader.applist;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import ru.playsoftware.j2meloader.MainActivity;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.ui.AppPalette;
import ru.playsoftware.j2meloader.ui.ConsoleSurface;
import static org.junit.Assert.*;

public class ConsoleSurfaceTest {
    @Test public void allPalettesRenderAndKeysKeepDistinctPressFocusAndSelection() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.content.SharedPreferences appearance = PreferenceManager.getDefaultSharedPreferences(context);
        String oldPalette = appearance.getString("pref_palette", null);
        try (LibraryPreferencesSnapshot prefs = new LibraryPreferencesSnapshot(context)) {
            for (String palette : AppPalette.VALUES) {
                PreferenceManager.getDefaultSharedPreferences(context).edit().putString("pref_palette", palette).commit();
                for (int night : new int[]{Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES}) {
                    Context themed = AppPalette.preview(context, palette, night);
                    for (ConsoleSurface.Kind kind : ConsoleSurface.Kind.values()) {
                        ConsoleSurface surface = new ConsoleSurface(themed, kind, 0);
                        surface.setBounds(0, 0, 240, 100);
                        surface.setState(new int[]{android.R.attr.state_enabled});
                        Bitmap idle = render(surface);
                        if (kind != ConsoleSurface.Kind.ROW) assertEquals(255, Color.alpha(idle.getPixel(120, 50)));
                        if (surface.isStateful()) {
                            surface.setState(new int[]{android.R.attr.state_enabled, android.R.attr.state_focused});
                            Bitmap focused = render(surface);
                            assertFalse(kind + " focus must be visible", idle.sameAs(focused)); focused.recycle();
                        }
                        if (kind == ConsoleSurface.Kind.TAB) {
                            surface.setState(new int[]{android.R.attr.state_enabled, android.R.attr.state_selected});
                            Bitmap selected = render(surface);
                            assertFalse("Selected platform must be marked", idle.sameAs(selected)); selected.recycle();
                            surface.setState(new int[]{android.R.attr.state_enabled, android.R.attr.state_pressed});
                            Bitmap pressed = render(surface);
                            assertFalse("Press must be visible", idle.sameAs(pressed)); pressed.recycle();
                            assertEquals(240, surface.getBounds().width());
                            assertEquals(100, surface.getBounds().height());
                        }
                        idle.recycle();
                    }
                }
            }
        } finally {
            android.content.SharedPreferences.Editor edit = appearance.edit();
            if (oldPalette == null) edit.remove("pref_palette"); else edit.putString("pref_palette", oldPalette);
            edit.commit();
        }
    }

    private Bitmap render(ConsoleSurface surface) {
        Bitmap bitmap = Bitmap.createBitmap(240, 100, Bitmap.Config.ARGB_8888);
        surface.draw(new Canvas(bitmap)); return bitmap;
    }

    @Test public void shortScreenReparentsToolsAndRestoresWithoutLosingCommands() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (LibraryPreferencesSnapshot prefs = new LibraryPreferencesSnapshot(context);
             ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                View root = activity.findViewById(R.id.library_root);
                float d = activity.getResources().getDisplayMetrics().density;
                float font = activity.getResources().getConfiguration().fontScale;
                AppsListFragment fragment = (AppsListFragment) activity.getSupportFragmentManager().findFragmentById(R.id.container);
                try {
                    java.lang.reflect.Method update = AppsListFragment.class.getDeclaredMethod("updateResponsiveToolbar");
                    update.setAccessible(true);
                    for (int[] size : new int[][]{{480,320},{540,620},{480,320},{393,800}}) {
                        int w = Math.round(size[0]*d), h = Math.round(size[1]*d);
                        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
                        root.layout(0,0,w,h); update.invoke(fragment);
                        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
                        root.layout(0,0,w,h);
                        boolean compact = size[1] < 400 && font <= 1.2f;
                        View tools = activity.findViewById(R.id.library_tools);
                        assertSame(activity.findViewById(compact ? R.id.library_header : R.id.library_screen), tools.getParent());
                        for (int id : new int[]{R.id.library_menu,R.id.library_social,R.id.library_search_toggle,
                                R.id.view_mode_gallery,R.id.view_mode_list,R.id.view_mode_grid}) {
                            View control = activity.findViewById(id);
                            assertEquals(View.VISIBLE, control.getVisibility());
                            assertFalse("Custom focus must not get Android's rectangular highlight", control.getDefaultFocusHighlightEnabled());
                            assertTrue(control.getWidth() >= 48*d-1); assertTrue(control.getHeight() >= 48*d-1);
                        }
                        assertEquals(4, ((ViewGroup) activity.findViewById(R.id.library_platform_tabs)).getChildCount());
                        assertTrue(activity.findViewById(R.id.library_category).getWidth() >= 48*d-1);
                    }
                } catch (Exception e) { throw new AssertionError(e); }
                finally { root.requestLayout(); }
            });
        }
    }
}
