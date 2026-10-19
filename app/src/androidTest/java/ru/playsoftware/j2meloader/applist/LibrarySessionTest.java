package ru.playsoftware.j2meloader.applist;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import ru.playsoftware.j2meloader.BuildConfig;
import ru.playsoftware.j2meloader.MainActivity;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.appsdb.AppDatabase;
import ru.playsoftware.j2meloader.catalog.ExternalLibrary;
import ru.playsoftware.j2meloader.catalog.AdditionalGames;
import ru.playsoftware.j2meloader.config.Config;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeFalse;

public class LibrarySessionTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context context = instrumentation.getTargetContext();
    private AppItem game(String path, String title, long played, boolean favorite) {
        AppItem item = new AppItem(path, title, "Vendor", "1");
        item.setLastPlayedAt(played); item.setFavorite(favorite); return item;
    }
    private AppItem nativeGame(String path, String platform) {
        return new ExternalLibrary.Item(path, "Native", platform, "Phone") {
            public void launch(Activity activity, boolean settings) { }
            public void favorite() { setFavorite(!isFavorite()); }
            public void rename(String title) { setTitle(title); }
        };
    }
    @Test public void collectionPlatformAndSearchIntersectWithoutMutatingSource() throws Exception {
        File mpn = Files.createTempDirectory(context.getCacheDir().toPath(), "library-mpn-").toFile();
        assertTrue(new File(mpn, AdditionalGames.MANIFEST).createNewFile());
        AppItem mophun = new AppItem("mpn", "Mophun", "Vendor", "1") {
            @Override public String getPathExt() { return mpn.getPath(); }
        };
        mophun.setFavorite(true); mophun.setLastPlayedAt(30);
        AppItem java = game("java", "Java", 20, true);
        AppItem symbian = nativeGame("symbian:one", "Symbian");
        AppItem ngage = nativeGame("symbian:two", "N-Gage 2.0");
        List<AppItem> items = Arrays.asList(java, mophun, symbian, ngage);
        assertEquals(Arrays.asList(java), LibraryQuery.select(items, 2, 1, " vendor "));
        assertEquals(Arrays.asList(mophun), LibraryQuery.select(items, 1, 2, ""));
        assertEquals(Arrays.asList(symbian), LibraryQuery.select(items, 0, 3, ""));
        assertEquals(Arrays.asList(ngage), LibraryQuery.select(items, 0, 4, ""));
        assertTrue(LibraryQuery.select(items, 2, 3, "").isEmpty());
        assertSame(java, items.get(0));
    }
    @Test public void recentOrderAndLastPlayedIgnoreUnplayedGames() {
        AppItem a=game("a","A",2,false), b=game("b","B",30,false), c=game("c","C",0,true);
        List<AppItem> items=Arrays.asList(a,b,c);
        assertEquals(Arrays.asList(b,a),LibraryQuery.select(items,1,0,""));
        assertSame(b,LibraryQuery.lastPlayed(items));
        assertNull(LibraryQuery.lastPlayed(Collections.singletonList(c)));
    }
    @Test public void searchDoesNotDependOnDeviceLocale() {
        Locale old=Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            assertEquals(1,LibraryQuery.select(Collections.singletonList(game("id","INDIGO",0,false)),0,0,"indigo").size());
        } finally { Locale.setDefault(old); }
    }
    @Test public void rapidRequestsPublishOnlyFinalCollection() {
        AtomicBoolean loaded=new AtomicBoolean();
        instrumentation.runOnMainSync(() -> {
            AppsListAdapter adapter=new AppsListAdapter(new AppsListAdapter.Listener() {
                public void onAppClicked(AppItem i) { }
                public void onAppFocused(AppItem i) { }
                public void onAppActionsRequested(View v,AppItem i) { }
            });
            adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
                @Override public void onChanged() {
                    assertEquals(1,adapter.getItemCount());
                    assertEquals("last",adapter.getFirstItem().getPath()); loaded.set(true);
                }
            });
            for(int i=0;i<30;i++) {
                adapter.setItems(Arrays.asList(game("old","Old",0,false), game("last","Final",1,true)));
                adapter.setCategory(i%3); adapter.setSearchQuery("Old");
            }
            adapter.setCategory(2); adapter.setSearchQuery("Final"); adapter.setPlatform(1);
        });
        long end=SystemClock.uptimeMillis()+8000;
        while(!loaded.get()&&SystemClock.uptimeMillis()<end)SystemClock.sleep(25);
        assertTrue(loaded.get()); instrumentation.waitForIdleSync();
    }
    @Test public void selectionWaitsForLateNativeCatalog() throws Exception {
        AppsListAdapter[] adapter=new AppsListAdapter[1];
        java.util.concurrent.atomic.AtomicReference<AppItem> selection=new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.CountDownLatch first=new java.util.concurrent.CountDownLatch(1);
        AppItem nativeItem=nativeGame("symbian:late","N-Gage");
        instrumentation.runOnMainSync(() -> {
            adapter[0]=new AppsListAdapter(new AppsListAdapter.Listener() {
                public void onAppClicked(AppItem item) { }
                public void onAppFocused(AppItem item) { selection.set(item); first.countDown(); }
                public void onAppActionsRequested(View view,AppItem item) { }
            });
            adapter[0].restoreSelection(nativeItem.getPath());
            adapter[0].setItems(Collections.singletonList(game("java","Java",0,false)));
        });
        assertTrue(first.await(5,java.util.concurrent.TimeUnit.SECONDS));
        instrumentation.runOnMainSync(() -> adapter[0].setItems(Arrays.asList(game("java","Java",0,false),nativeItem)));
        long end=SystemClock.uptimeMillis()+5000;
        while(selection.get()!=nativeItem&&SystemClock.uptimeMillis()<end)SystemClock.sleep(25);
        assertSame(nativeItem,selection.get());
    }
    @Test public void filtersPositionAndCardActionsSurviveColdOpen() throws Exception {
        assumeFalse(BuildConfig.SYMBIAN_EXPERIMENT);
        File root=Files.createTempDirectory(context.getCacheDir().toPath(),"library-session-").toFile();
        try(AppDatabase db=AppDatabase.open(context,root.getPath())) {
            for(int i=0;i<40;i++) {
                AppItem item=game(String.format(Locale.ROOT,"game-%02d",i),
                        String.format(Locale.ROOT,"Game %02d",i),i+1,i%2==0);
                item.setPreparationState("indexed"); db.appItemDao().insertImported(item);
            }
        }
        String original=Config.getEmulatorDir();
        Method init=Config.class.getDeclaredMethod("initDirs",String.class); init.setAccessible(true);
        SharedPreferences preferences=PreferenceManager.getDefaultSharedPreferences(context);
        Map<String,?> before=new HashMap<>(preferences.getAll());
        try {
            init.invoke(null,root.getPath());
            preferences.edit().putInt("libraryCategory",0).putInt("libraryPlatform",0)
                    .putInt("pref_library_view_mode",1).remove("librarySelection").commit();
            try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
                awaitCount(scenario,40);
                java.util.concurrent.atomic.AtomicReference<android.content.Intent> launch=new java.util.concurrent.atomic.AtomicReference<>();
                Instrumentation.ActivityMonitor monitor=new Instrumentation.ActivityMonitor() {
                    @Override public Instrumentation.ActivityResult onStartActivity(android.content.Intent intent) {
                        if(intent.getComponent()==null||!intent.getComponent().getClassName().endsWith("CompatibilityTestActivity"))return null;
                        launch.set(intent);return new Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null);
                    }
                };
                instrumentation.addMonitor(monitor);
                try {
                    scenario.onActivity(a -> a.findViewById(R.id.library_menu).performClick());
                    click("Play last: Game 39");
                    assertNotNull(launch.get()); assertTrue(launch.get().getData().toString().endsWith("game-39"));
                } finally { instrumentation.removeMonitor(monitor); }
                scenario.onActivity(a -> a.findViewById(R.id.library_menu).performClick());
                click("Collection"); click("Platform: All platforms"); click("Java ME");
                awaitCount(scenario,40);
                scenario.onActivity(a -> a.findViewById(R.id.rail_favorites).performClick());
                awaitCount(scenario,20);
                scenario.onActivity(a -> {
                    RecyclerView list=a.findViewById(R.id.apps_recycler);
                    ((LinearLayoutManager)list.getLayoutManager()).scrollToPositionWithOffset(10,0);
                });
                instrumentation.waitForIdleSync(); SystemClock.sleep(500);
            }
            assertEquals("game-20",preferences.getString("libraryLocation.2.1",null));
            try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
                awaitCount(scenario,20); SystemClock.sleep(500);
                scenario.onActivity(a -> {
                    assertEquals("Favorites / Java ME",((TextView)a.findViewById(R.id.library_category)).getText().toString());
                    RecyclerView list=a.findViewById(R.id.apps_recycler);
                    assertEquals(10,((LinearLayoutManager)list.getLayoutManager()).findFirstVisibleItemPosition());
                    RecyclerView.ViewHolder row=list.findViewHolderForAdapterPosition(10);
                    assertNotNull(row); row.itemView.performLongClick();
                });
                click("Edit card"); click("Title");
                // Cancel must leave the game and its saved identity untouched.
                click("Cancel");
                scenario.onActivity(a -> {
                    RecyclerView list=a.findViewById(R.id.apps_recycler);
                    list.findViewHolderForAdapterPosition(10).itemView.performLongClick();
                });
                click("Remove favorite"); awaitCount(scenario,19);
                scenario.recreate(); awaitCount(scenario,19);
            }
        } finally {
            init.invoke(null,original);
            SharedPreferences.Editor edit=preferences.edit();
            for(String key:preferences.getAll().keySet()) if(key.startsWith("library")||key.equals("pref_library_view_mode")) edit.remove(key);
            for(Map.Entry<String,?> entry:before.entrySet()) {
                String key=entry.getKey(); Object value=entry.getValue();
                if(!key.startsWith("library")&&!key.equals("pref_library_view_mode"))continue;
                if(value instanceof Integer)edit.putInt(key,(Integer)value);
                else if(value instanceof String)edit.putString(key,(String)value);
            }
            edit.commit();
        }
    }
    private void awaitCount(ActivityScenario<MainActivity> scenario,int count) {
        AtomicBoolean ready=new AtomicBoolean(); long end=SystemClock.uptimeMillis()+10000;
        do {
            scenario.onActivity(a -> {RecyclerView list=a.findViewById(R.id.apps_recycler);
                ready.set(list!=null&&list.getAdapter()!=null&&list.getAdapter().getItemCount()==count&&!list.hasPendingAdapterUpdates());});
            if(!ready.get())SystemClock.sleep(50);
        }while(!ready.get()&&SystemClock.uptimeMillis()<end);
        assertTrue("Expected "+count+" games",ready.get());
    }
    private void click(String text) {
        long end=SystemClock.uptimeMillis()+10000;
        do {
            AccessibilityNodeInfo root=instrumentation.getUiAutomation().getRootInActiveWindow();
            if(root!=null)for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(text)) {
                if(node.getText()==null||!text.equalsIgnoreCase(node.getText().toString()))continue;
                while(!node.isClickable()&&node.getParent()!=null)node=node.getParent();
                assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK));
                instrumentation.waitForIdleSync();SystemClock.sleep(150);return;
            }
            SystemClock.sleep(50);
        }while(SystemClock.uptimeMillis()<end);
        fail("Missing "+text);
    }
    @Test public void captureInstalledLibraryWhenRequested() throws Exception {
        org.junit.Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("libraryCapture")));
        SharedPreferences preferences=PreferenceManager.getDefaultSharedPreferences(context);
        Map<String,?> before=new HashMap<>(preferences.getAll());
        preferences.edit().putInt("libraryCategory",0).putInt("libraryPlatform",0)
                .putInt("pref_library_view_mode",0).commit();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            AtomicBoolean ready=new AtomicBoolean(); long end=SystemClock.uptimeMillis()+15000;
            do {
                scenario.onActivity(a -> {RecyclerView list=a.findViewById(R.id.apps_recycler);
                    ready.set(list!=null&&list.getAdapter().getItemCount()>0&&!list.hasPendingAdapterUpdates());});
                if(!ready.get())SystemClock.sleep(100);
            }while(!ready.get()&&SystemClock.uptimeMillis()<end);
            assertTrue("Installed games required",ready.get()); SystemClock.sleep(1500);
            scenario.onActivity(a -> ((RecyclerView)a.findViewById(R.id.apps_recycler)).scrollToPosition(0));
            capture("gallery.png");
            scenario.onActivity(a -> a.findViewById(R.id.library_menu).performClick());
            capture("library-menu.png"); click("Collection"); capture("collection.png");
            click("Platform: All platforms"); capture("platforms.png"); click("All platforms");
            scenario.onActivity(a -> a.findViewById(R.id.view_mode_grid).performClick());
            capture("grid.png");
            scenario.onActivity(a -> a.findViewById(R.id.view_mode_list).performClick());
            capture("list.png");
            scenario.onActivity(a -> {
                RecyclerView list=a.findViewById(R.id.apps_recycler);
                ((LinearLayoutManager)list.getLayoutManager()).scrollToPositionWithOffset(0,0);
            });
            instrumentation.waitForIdleSync(); SystemClock.sleep(300);
            scenario.onActivity(a -> {
                RecyclerView list=a.findViewById(R.id.apps_recycler);
                list.findViewHolderForAdapterPosition(0).itemView.performLongClick();
            });
            capture("game-options.png"); click("Edit card"); capture("edit-card.png");
            click("Title"); capture("edit-title.png"); click("Cancel");
        } finally {
            SharedPreferences.Editor edit=preferences.edit();
            for(String key:preferences.getAll().keySet())if(key.startsWith("library")||key.equals("pref_library_view_mode"))edit.remove(key);
            for(Map.Entry<String,?> entry:before.entrySet()) {
                String key=entry.getKey(); Object value=entry.getValue();
                if(!key.startsWith("library")&&!key.equals("pref_library_view_mode"))continue;
                if(value instanceof Integer)edit.putInt(key,(Integer)value);
                else if(value instanceof String)edit.putString(key,(String)value);
            }
            edit.commit();
        }
    }
    private void capture(String name) throws Exception {
        instrumentation.waitForIdleSync(); SystemClock.sleep(800);
        android.graphics.Bitmap bitmap=instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File dir=new File(context.getExternalFilesDir(null),"library-session-captures"); dir.mkdirs();
        try(java.io.OutputStream out=new java.io.FileOutputStream(new File(dir,name))) {
            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));
        } finally { bitmap.recycle(); }
    }
}
