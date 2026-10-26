/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.applist;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.lang.reflect.*;
import org.junit.Test;
import ru.playsoftware.j2meloader.MainActivity;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.config.*;
import ru.playsoftware.j2meloader.input.GameMenuDialog;
import static org.junit.Assert.*;

public class GameActionsTest {
    @Test public void externalGameKeepsEngineOwnedActionsWithoutJavaSettings() throws Exception {
        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ru.playsoftware.j2meloader.catalog.ExternalLibrary.Item item =
                new ru.playsoftware.j2meloader.catalog.ExternalLibrary.Item("symbian:fixture", "Native fixture", "Symbian", "Fixture") {
                    public void launch(android.app.Activity activity, boolean settings) { fail("Navigation must not launch"); }
                    public void favorite() { }
                    public void rename(String title) { }
                    public boolean supportsUninstallReview() { return true; }
                };
        try (LibraryPreferencesSnapshot prefs = new LibraryPreferencesSnapshot(context);
             ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    AppsListFragment fragment = (AppsListFragment) activity.getSupportFragmentManager().findFragmentById(R.id.container);
                    Method show = AppsListFragment.class.getDeclaredMethod("showActions", View.class, AppItem.class);
                    show.setAccessible(true); show.invoke(fragment, activity.findViewById(R.id.library_menu), item);
                    Field field = AppsListFragment.class.getDeclaredField("contextMenu"); field.setAccessible(true);
                    GameMenuDialog menu = (GameMenuDialog) field.get(fragment);
                    View view = menu.getWindow().getDecorView();
                    try {
                        assertNotNull(find(view, "Play")); assertNull(find(view, "Display")); assertNull(find(view, "Controls"));
                        find(view, "More").performClick();
                        assertNotNull(find(view, "System files")); assertNotNull(find(view, "Export launch file"));
                        assertNotNull(find(view, "Uninstall from phone")); assertNull(find(view, "Settings"));
                        find(view, "Edit card").performClick();
                        assertNotNull(find(view, "Title")); assertNull(find(view, "Artwork"));
                        back(view); back(view); assertNotNull(find(view, "Play"));
                    } finally { menu.dismiss(); }
                } catch (Exception e) { throw new AssertionError(e); }
            });
        }
    }

    @Test public void gameActionsKeepInformationSettingsAndLegacyMoreInOnePanel() throws Exception {
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        File root=new File(context.getCacheDir(),"actions-"+java.util.UUID.randomUUID());
        File app=new File(root,"converted/fixture"), config=new File(root,"configs/fixture");
        assertTrue(app.mkdirs()); assertTrue(config.mkdirs());
        assertTrue(new File(app,Config.MIDLET_RES_FILE).createNewFile());
        ProfileModel saved=new ProfileModel(config); assertTrue(ProfilesManager.saveConfig(saved));
        AppItem item=new AppItem("actions-fixture","Fixture game","Fixture vendor","1") {
            @Override public String getPathExt(){return app.getAbsolutePath();}
        };
        try (LibraryPreferencesSnapshot prefs=new LibraryPreferencesSnapshot(context);
             ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity->{
                try {
                    AppsListFragment fragment=(AppsListFragment)activity.getSupportFragmentManager().findFragmentById(R.id.container);
                    Method show=AppsListFragment.class.getDeclaredMethod("showActions",View.class,AppItem.class); show.setAccessible(true);
                    show.invoke(fragment,activity.findViewById(R.id.library_menu),item);
                    Field field=AppsListFragment.class.getDeclaredField("contextMenu"); field.setAccessible(true);
                    GameMenuDialog menu=(GameMenuDialog)field.get(fragment);
                    View view=menu.getWindow().getDecorView();
                    try {
                        assertNotNull(find(view,"Play")); assertNotNull(find(view,"Display")); assertNotNull(find(view,"Controls"));
                        assertNull(find(view,"Delete"));
                        find(view,"Game information").performClick();
                        assertTrue(menu.isShowing()); back(view);
                        find(view,"Display").performClick(); assertNotNull(find(view,"Picture size")); back(view);
                        assertNotNull(find(view,"Game actions"));
                        find(view,"Controls").performClick(); assertNotNull(find(view,"Controller mapping")); back(view);
                        find(view,"More").performClick();
                        assertNotNull(find(view,"Settings")); assertNotNull(find(view,"Edit card"));
                        assertTrue("Reinstall retained", buttons(view).stream().anyMatch(s->s.toLowerCase().contains("reinstall")));
                        assertTrue("Delete retained", buttons(view).stream().anyMatch(s->s.toLowerCase().contains("delete")));
                        find(view,"Edit card").performClick(); assertNotNull(find(view,"Artwork")); back(view);
                        assertNotNull(find(view,"Settings"));
                    } finally { menu.dismiss(); }
                } catch(Exception e) {throw new AssertionError(e);}
            });
            assertEquals(saved.screenScaleType,ProfilesManager.loadConfig(config).screenScaleType);
        } finally { remove(root); }
    }
    private static void remove(File f){File[] children=f.listFiles();if(children!=null)for(File child:children)remove(child);assertTrue(f.delete());}
    private static void back(View v){View b=description(v,"Back");assertNotNull(b);b.performClick();}
    private static View description(View v,String s){if(s.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View r=description(((ViewGroup)v).getChildAt(i),s);if(r!=null)return r;}return null;}
    private static View find(View v,String s){if(v instanceof android.widget.TextView&&s.contentEquals(((android.widget.TextView)v).getText()))return v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View r=find(((ViewGroup)v).getChildAt(i),s);if(r!=null)return r;}return null;}
    private static java.util.List<String> buttons(View v){java.util.List<String> result=new java.util.ArrayList<>();
        if(v instanceof Button)result.add(((Button)v).getText().toString());
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)result.addAll(buttons(((ViewGroup)v).getChildAt(i)));return result;}
}
