// SPDX-License-Identifier: GPL-3.0-only
package ru.playsoftware.j2meloader.catalog;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.widget.EditText;
import android.widget.TextView;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.sqlite.db.SimpleSQLiteQuery;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import org.json.*;
import org.junit.Test;
import ru.playsoftware.j2meloader.MainActivity;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.applist.AppsListAdapter;
import ru.playsoftware.j2meloader.appsdb.AppDatabase;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.util.FileUtils;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

/** Run seed on the baseline Debug APK, install -r the candidate, then run verify. */
public class ProvidedUpgradeJourneyTest {
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final SharedPreferences preferences=PreferenceManager.getDefaultSharedPreferences(context);
    private final File journal=new File(context.getFilesDir(),"upgrade-fixture-state.json");

    @Test public void libraryAndPreferencesSurviveInPlaceUpdate() throws Exception {
        String phase=InstrumentationRegistry.getArguments().getString("upgradePhase"); assumeNotNull(phase);
        assertTrue(context.getPackageName().endsWith(".debug"));
        assertTrue(phase.equals("seed") || phase.equals("verify") || phase.equals("cleanup"));
        String previous=Config.getEmulatorDir();
        JSONObject record=null; File root=null; boolean retain=false;
        try {
            if (phase.equals("seed")) {
                assertFalse("Finish or clean up the earlier fixture first",journal.exists());
                root=new File(context.getExternalFilesDir(null),"upgrade-fixture-"+UUID.randomUUID()); assertTrue(root.mkdirs());
                record=new JSONObject().put("root",root.getPath()).put("originalPreferences",snapshotPreferences());
                save(record); // Allows explicit cleanup even if setup or the later install is interrupted.
                restorePreferences(new JSONObject());
                preferences.edit().putBoolean("pref_vibration_switch",false).putInt("pref_app_sort",2).commit();
                configure(root);
                new ProvidedLibraryJourneyTest().prepare(root);
                try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)) {
                    count(activity,2,null);
                    activity.onActivity(a -> {
                        a.findViewById(R.id.view_mode_list).performClick();
                        a.findViewById(R.id.library_search_toggle).performClick();
                        ((EditText)a.findViewById(R.id.library_search)).setText("Diamond");
                    });
                    count(activity,1,"Diamond Rush");
                    activity.onActivity(a -> a.findViewById(R.id.platform_mophun).performClick()); count(activity,1,"DeepAbyss [v1.4] [multiscreen]");
                    activity.onActivity(a -> {
                        a.findViewById(R.id.view_mode_grid).performClick();
                        a.findViewById(R.id.platform_java).performClick();
                    });
                    count(activity,1,"Diamond Rush"); capture("upgrade-baseline.png");
                }
                record.put("expectedPreferences",snapshotPreferences()).put("files",hashes(root)).put("catalog",catalog(root));
                save(record); retain=true;
            } else {
                assertTrue("Baseline fixture is required",journal.isFile());
                record=new JSONObject(new String(Files.readAllBytes(journal.toPath()),StandardCharsets.UTF_8));
                root=new File(record.getString("root"));
                assertEquals(context.getExternalFilesDir(null).getCanonicalFile(),root.getParentFile().getCanonicalFile());
                assertTrue(root.getName().startsWith("upgrade-fixture-"));
                if (!phase.equals("cleanup")) {
                    configure(root);
                    same(record.getJSONObject("expectedPreferences"),snapshotPreferences());
                    same(record.getJSONObject("files"),hashes(root)); same(record.getJSONObject("catalog"),catalog(root));
                    try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)) {
                        count(activity,1,"Diamond Rush");
                        activity.onActivity(a -> {
                            assertTrue(a.findViewById(R.id.platform_java).isSelected());
                            assertTrue(a.findViewById(R.id.view_mode_list).isSelected());
                            assertEquals("Diamond",((EditText)a.findViewById(R.id.library_search)).getText().toString());
                            a.findViewById(R.id.platform_mophun).performClick();
                        });
                        count(activity,1,"DeepAbyss [v1.4] [multiscreen]");
                        activity.onActivity(a -> {
                            assertTrue(a.findViewById(R.id.view_mode_grid).isSelected());
                            assertEquals("",((EditText)a.findViewById(R.id.library_search)).getText().toString());
                            a.findViewById(R.id.platform_java).performClick();
                        });
                        count(activity,1,"Diamond Rush"); capture("upgrade-candidate.png");
                    }
                    same(record.getJSONObject("files"),hashes(root)); same(record.getJSONObject("catalog"),catalog(root));
                    Files.write(new File(context.getExternalFilesDir(null),"upgrade-result.txt").toPath(),
                            "In-place Debug update retained three catalog entries, payload/config/save/shared-file hashes, favorites/history and remembered Java/Mophun navigation. Synthetic save sentinels; no signed-release or guest-progress acceptance.\n".getBytes(StandardCharsets.UTF_8));
                }
            }
        } finally {
            configure(new File(previous));
            if (!retain && record!=null) {
                restorePreferences(record.getJSONObject("originalPreferences"));
                if(root!=null) FileUtils.deleteDirectory(root);
                Files.deleteIfExists(journal.toPath());
            }
        }
    }
    private boolean owned(String key) {
        return key.startsWith("library") || key.equals("pref_library_view_mode") || key.equals("pref_vibration_switch") || key.equals("pref_app_sort");
    }
    private JSONObject snapshotPreferences() throws Exception {
        JSONObject result=new JSONObject();
        for(Map.Entry<String,?> e:preferences.getAll().entrySet()) if(owned(e.getKey()))
            result.put(e.getKey(),new JSONObject().put("type",e.getValue().getClass().getSimpleName()).put("value",e.getValue()));
        return result;
    }
    private void restorePreferences(JSONObject snapshot) throws Exception {
        SharedPreferences.Editor edit=preferences.edit();
        for(String key:preferences.getAll().keySet()) if(owned(key))edit.remove(key);
        for(Iterator<String> keys=snapshot.keys();keys.hasNext();) {
            String key=keys.next(); JSONObject item=snapshot.getJSONObject(key);
            switch(item.getString("type")) {
                case "Boolean":edit.putBoolean(key,item.getBoolean("value"));break;
                case "Integer":edit.putInt(key,item.getInt("value"));break;
                case "Long":edit.putLong(key,item.getLong("value"));break;
                case "String":edit.putString(key,item.getString("value"));break;
                default:throw new AssertionError("Unsupported fixture preference "+key);
            }
        }
        assertTrue(edit.commit());
    }
    private JSONObject hashes(File root) throws Exception {
        Map<String,String> result=new TreeMap<>();
        for(String dir:new String[]{"converted","configs","data","fs"})
            ProvidedBackupRoundTrip.hashes(new File(root,dir)).forEach((k,v) -> result.put(dir+"/"+k,v));
        return new JSONObject(result);
    }
    private JSONObject catalog(File root) throws Exception {
        JSONObject result=new JSONObject();
        try(AppDatabase db=AppDatabase.open(context,root.getPath())) {
            List<AppItem> games=db.appItemDao().getAllSingle(new SimpleSQLiteQuery("SELECT * FROM apps ORDER BY path")).blockingGet();
            assertEquals(3,games.size());
            for(AppItem item:games) result.put(item.getPath(),new JSONObject().put("title",item.getTitle())
                    .put("favorite",item.isFavorite()).put("plays",item.getPlayCount()).put("lastPlayed",item.getLastPlayedAt()));
        }
        return result;
    }
    private void same(JSONObject expected,JSONObject actual) throws Exception {
        Set<String> keys=new TreeSet<>(); expected.keys().forEachRemaining(keys::add);
        Set<String> actualKeys=new TreeSet<>(); actual.keys().forEachRemaining(actualKeys::add); assertEquals(keys,actualKeys);
        for(String key:keys) {
            Object a=expected.get(key),b=actual.get(key);
            if(a instanceof JSONObject) { assertTrue(b instanceof JSONObject); same((JSONObject)a,(JSONObject)b); }
            else assertEquals(key,String.valueOf(a),String.valueOf(b));
        }
    }
    private void configure(File root) throws Exception {
        Method init=Config.class.getDeclaredMethod("initDirs",String.class); init.setAccessible(true); init.invoke(null,root.getPath());
    }
    private void save(JSONObject value) throws Exception { Files.write(journal.toPath(),value.toString(2).getBytes(StandardCharsets.UTF_8)); }
    private void count(ActivityScenario<MainActivity> activity,int expected,String firstTitle) {
        long end=SystemClock.uptimeMillis()+20000;
        do {
            int[] count={-1}; activity.onActivity(a -> { RecyclerView list=a.findViewById(R.id.apps_recycler);
                if(list!=null && list.getAdapter() instanceof AppsListAdapter && !list.hasPendingAdapterUpdates()) {
                    AppsListAdapter adapter=(AppsListAdapter)list.getAdapter();
                    if(adapter.getItemCount()==expected && (firstTitle==null ||
                            firstTitle.equals(adapter.getItem(0).getTitle()))) {
                        RecyclerView.ViewHolder row=list.findViewHolderForAdapterPosition(0);
                        TextView title=row==null?null:row.itemView.findViewById(R.id.name);
                        if(firstTitle==null || title!=null && firstTitle.contentEquals(title.getText())) count[0]=expected;
                    }
                } });
            if(count[0]==expected)return; SystemClock.sleep(100);
        } while(SystemClock.uptimeMillis()<end);
        throw new AssertionError("Library did not settle to "+expected+" entries, first title "+firstTitle);
    }
    private void capture(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().waitForIdle(500,5000);
        android.graphics.Bitmap image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot(); assertNotNull(image);
        try(OutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),name))) {
            assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));
        } finally { image.recycle(); }
    }
}
