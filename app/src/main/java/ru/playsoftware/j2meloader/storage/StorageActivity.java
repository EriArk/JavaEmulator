/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.storage;

import android.app.*;
import android.os.*;
import android.content.*;
import android.widget.*;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import io.github.eriark.abyssme.storage.StoragePaths;
import java.io.*;
import org.json.JSONObject;
import ru.playsoftware.j2meloader.MainActivity;

/** Dedicated process: moving files never races a live game VM or Room connection. */
public final class StorageActivity extends ru.playsoftware.j2meloader.settings.CompactSettingsActivity {
    private LinearLayout content;
    private TextView progress;
    private Thread worker;
    @Override public void onCreate(Bundle state) {super.onCreate(state); showLocations();}
    public static boolean guard(Activity activity) {
        if(StoragePaths.available(activity)&&!StoragePaths.moving(activity))return false;
        activity.startActivity(new Intent(activity,StorageActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        activity.finish();return true;
    }
    private void page(String title) {
        ScrollView scroll=new ScrollView(this);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(16*getResources().getDisplayMetrics().density);content.setPadding(pad,pad,pad,pad);scroll.addView(content);screenContent.removeAllViews();screenContent.addView(scroll);screenTitle(title);
    }
    private TextView text(String s) {TextView v=new TextView(this);v.setText(s);v.setPadding(0,12,0,12);content.addView(v);return v;}
    private void button(String s,Runnable action) {Button b=new Button(this);b.setText(s);b.setOnClickListener(v->action.run());content.addView(b);}
    private void showLocations() {
        page("Library storage location");
        if(StoragePaths.moving(this)) {
            text("A library move needs recovery. No unverified originals are deleted. Reinsert any missing card, then retry or keep the original location.");
            button("Retry / finish move",()->work(()->{stopOtherProcesses();new LibraryMove(this).run(StoragePaths.read(StoragePaths.journal(this)),this::status);}));
            button("Keep original location",()->work(()->{new LibraryMove(this).cancelUncommitted();}));
            return;
        }
        if(!StoragePaths.available(this)) {
            text("The selected library storage is unavailable. Reinsert the SD card. AbyssME will not create an empty replacement library or reset your phones.");
            button("Retry",this::showLocations);return;
        }
        text("Current library: "+StoragePaths.library(this)+"\nVirtual phones: "+StoragePaths.phones(this));
        text("Moves installed games, saves, profiles and all virtual phones. Android app-specific storage is removed when you uninstall AbyssME. Keep a separate backup. Folders added for scanning remain in their original locations.");
        StorageManager sm=getSystemService(StorageManager.class);
        File[] dirs=getExternalFilesDirs(null);
        for(File dir:dirs) {
            if(dir==null||!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState(dir)))continue;
            StorageVolume volume=sm.getStorageVolume(dir);boolean removable=Environment.isExternalStorageRemovable(dir);
            String label=removable?"Removable SD card":"Internal storage";
            if(volume!=null)label+=" — "+volume.getDescription(this);
            label+="\n"+android.text.format.Formatter.formatFileSize(this,dir.getUsableSpace())+" free / "+android.text.format.Formatter.formatFileSize(this,dir.getTotalSpace())+" total";
            String current=StoragePaths.read(StoragePaths.pointer(this)).optString("volume",StoragePaths.primary(this).getPath());
            if(current.equals(dir.getPath()))text(label+" (current)");else button(label,()->estimate(dir));
        }
        text("Only Android-supported app-specific locations are shown. Insert a writable SD card to add a removable location.");
        button("Back to library",this::openLibrary);
    }
    private interface Job {void run() throws Exception;}
    private void estimate(File destination) {
        page("Preparing move");progress=text("Calculating required space…");
        worker=new Thread(()->{
            try {JSONObject plan=new LibraryMove(this).plan(destination);runOnUiThread(()->confirm(plan));}
            catch(Exception e){runOnUiThread(()->failure(e));}
            finally{worker=null;}
        },"Storage estimate");worker.start();
    }
    private void confirm(JSONObject plan) {
        page("Move existing library?");
        text("Estimated required space: "+android.text.format.Formatter.formatFileSize(this,plan.optLong("requiredBytes"))+" (includes filesystem allocation and 32 MiB reserve).\nDestination: "+plan.optString("volume")+"\n\nActive games and imports will close. Do not remove either storage device. Every copied file is verified before switching locations; only then are old copies deleted.");
        button("Move library",()->work(()->{
            new LibraryMove(this).beginQuiescent(plan,this::stopOtherProcesses,this::status);
        }));
        button("Cancel",this::showLocations);
    }
    private void stopOtherProcesses() throws IOException {
        ActivityManager manager=getSystemService(ActivityManager.class);
        for(int attempt=0;attempt<30;attempt++) {
            boolean found=false;
            java.util.List<ActivityManager.RunningAppProcessInfo> list=manager.getRunningAppProcesses();
            if(list!=null)for(ActivityManager.RunningAppProcessInfo p:list)if(p.uid==android.os.Process.myUid()&&p.pid!=android.os.Process.myPid()) {
                found=true;android.os.Process.killProcess(p.pid);
            }
            if(!found)return;SystemClock.sleep(100);
        }
        throw new IOException("Could not stop an active game. Close it and retry the move.");
    }
    private void work(Job job) {
        page("Moving library");progress=text("Preparing…");button("Cancel / stop after current operation",()->{if(worker!=null)worker.interrupt();});
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        worker=new Thread(()->{
            try{job.run();runOnUiThread(()->{page("Library ready");text("The selected library and virtual phones are ready. Settings and favorites are preserved.");button("Open library",this::openLibrary);});}
            catch(Exception e){runOnUiThread(()->failure(e));}
            finally{worker=null;runOnUiThread(()->getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));}
        },"Library move");worker.start();
    }
    private void status(String s){runOnUiThread(()->{if(progress!=null)progress.setText(s);});}
    private void failure(Exception e){page("Move paused");text(e.getMessage()==null?e.toString():e.getMessage());text("Original data is retained until its copy is verified. You can retry or recover the original location.");button("Recovery / storage locations",this::showLocations);}
    private void openLibrary(){if(StoragePaths.moving(this)||!StoragePaths.available(this)){showLocations();return;}startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));finishAndRemoveTask();}
    @Override public void onBackPressed(){if(worker!=null){worker.interrupt();return;}if(StoragePaths.moving(this)||!StoragePaths.available(this))finishAndRemoveTask();else openLibrary();}
    @Override protected void onDestroy(){if(worker!=null)worker.interrupt();super.onDestroy();}
}
