/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.applist;

import android.graphics.Bitmap;
import android.os.SystemClock;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import org.junit.*;
import ru.playsoftware.j2meloader.MainActivity;
import ru.playsoftware.j2meloader.R;
import static org.junit.Assert.*;

/** Opt-in captures of the installed catalog, preserving appearance and navigation. */
public class SoftConsoleCaptureTest {
    @Test public void installedLibraryLightAndDark()throws Exception{
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("softCapture")));
        android.app.Instrumentation inst=InstrumentationRegistry.getInstrumentation();
        android.content.Context context=inst.getTargetContext();
        android.content.SharedPreferences prefs=PreferenceManager.getDefaultSharedPreferences(context);
        String palette=prefs.getString("pref_palette",null);int mode=AppCompatDelegate.getDefaultNightMode();
        try(LibraryPreferencesSnapshot saved=new LibraryPreferencesSnapshot(context)){
            prefs.edit().putString("pref_palette","classic").commit();
            for(int theme:new int[]{AppCompatDelegate.MODE_NIGHT_NO,AppCompatDelegate.MODE_NIGHT_YES}){
                inst.runOnMainSync(()->AppCompatDelegate.setDefaultNightMode(theme));
                try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
                    long until=SystemClock.elapsedRealtime()+15000;boolean[] ready={false};
                    do{activity.onActivity(a->{RecyclerView list=a.findViewById(R.id.apps_recycler);ready[0]=list!=null&&list.getAdapter()!=null&&list.getAdapter().getItemCount()>0&&!list.hasPendingAdapterUpdates();});if(!ready[0])Thread.sleep(100);}while(!ready[0]&&SystemClock.elapsedRealtime()<until);
                    assertTrue("Installed catalog required",ready[0]);
                    String prefix=theme==AppCompatDelegate.MODE_NIGHT_NO?"light-":"dark-";
                    activity.onActivity(a->a.findViewById(R.id.view_mode_gallery).performClick());capture(prefix+"gallery.png");
                    activity.onActivity(a->a.findViewById(R.id.view_mode_grid).performClick());capture(prefix+"grid.png");
                    activity.onActivity(a->a.findViewById(R.id.view_mode_list).performClick());capture(prefix+"list.png");
                    activity.onActivity(a->{RecyclerView list=a.findViewById(R.id.apps_recycler);list.scrollToPosition(0);});Thread.sleep(500);
                    activity.onActivity(a->{RecyclerView list=a.findViewById(R.id.apps_recycler);assertNotNull(list.findViewHolderForAdapterPosition(0));list.findViewHolderForAdapterPosition(0).itemView.performLongClick();});
                    capture(prefix+"actions.png");
                }
            }
        }finally{
            android.content.SharedPreferences.Editor edit=prefs.edit();if(palette==null)edit.remove("pref_palette");else edit.putString("pref_palette",palette);edit.commit();
            inst.runOnMainSync(()->AppCompatDelegate.setDefaultNightMode(mode));
        }
    }
    private void capture(String name)throws Exception{
        android.app.Instrumentation inst=InstrumentationRegistry.getInstrumentation();inst.waitForIdleSync();Thread.sleep(900);
        Bitmap image=inst.getUiAutomation().takeScreenshot();assertNotNull(image);
        File folder=new File(inst.getTargetContext().getExternalFilesDir(null),"soft-console-captures");assertTrue(folder.isDirectory()||folder.mkdirs());
        try(FileOutputStream out=new FileOutputStream(new File(folder,name))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}finally{image.recycle();}
    }
}
