package ru.playsoftware.j2meloader.applist;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.inspector.WindowInspector;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.HashMap;
import java.util.Map;
import ru.playsoftware.j2meloader.MainActivity;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.appsdb.AppDatabase;
import ru.playsoftware.j2meloader.config.Config;
import static org.junit.Assert.*;

public class GalleryPresentationTest {
    @Test public void formatsPreserveArtworkAndSurviveRecreation() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File root = Files.createTempDirectory(context.getCacheDir().toPath(), "gallery-layout-").toFile();
        File converted = new File(root,"converted"); assertTrue(converted.mkdir());
        // Keep synthetic art outside converted/, whose startup scan removes non-game files.
        File art = new File(root,"portrait.png");
        Bitmap bitmap = Bitmap.createBitmap(80,120,Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(0xff34ba9a);
        try (FileOutputStream out = new FileOutputStream(art)) { bitmap.compress(Bitmap.CompressFormat.PNG,100,out); }
        bitmap.recycle(); byte[] originalArt = Files.readAllBytes(art.toPath());
        for (int[] size : new int[][]{{160,90},{240,56}}) {
            Bitmap other = Bitmap.createBitmap(size[0],size[1],Bitmap.Config.ARGB_8888);
            other.eraseColor(0xffca9f34);
            try (FileOutputStream out = new FileOutputStream(new File(root,"art-"+size[0]+".png"))) {
                other.compress(Bitmap.CompressFormat.PNG,100,out);
            }
            other.recycle();
        }
        try (AppDatabase db = AppDatabase.open(context,root.getPath())) {
            for (int i=0;i<8;i++) {
                AppItem item = new AppItem("gallery-"+i,"Gallery fixture " + i,"Fixture","1");
                item.setCoverPath(new String[]{"../portrait.png","../art-160.png","../art-240.png",null}[i%4]);
                item.setPreparationState("indexed"); db.appItemDao().insertImported(item);
            }
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Map<String, Object> navigation = new HashMap<>();
        prefs.getAll().forEach((key, value) -> {
            if (key.startsWith("library")) navigation.put(key, value);
        });
        int oldFormat = prefs.getInt(LibraryLayout.COVER_PREFERENCE,0), oldMode = prefs.getInt("pref_library_view_mode",0);
        int oldSort = prefs.getInt("pref_app_sort",0);
        String originalRoot = Config.getEmulatorDir();
        Method init = Config.class.getDeclaredMethod("initDirs",String.class); init.setAccessible(true);
        try {
            init.invoke(null,root.getPath()); prefs.edit().putInt("pref_library_view_mode",0).putInt("pref_app_sort",0).commit();
            new LibraryNavigation(prefs).save(1, 0, 0, 0, "Gallery fixture", null);
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                scenario.onActivity(activity -> ((android.widget.EditText)activity.findViewById(R.id.library_search)).setText("Gallery fixture"));
                awaitLibrary(scenario);
                for (int format : new int[]{2,1,0}) {
                    scenario.onActivity(activity -> activity.findViewById(R.id.library_menu).performClick());
                    String[] formats = context.getResources().getStringArray(R.array.library_cover_formats);
                    click(context.getString(R.string.library_cover_format_value,
                            formats[LibraryLayout.format(prefs.getInt(LibraryLayout.COVER_PREFERENCE,0))]));
                    click(formats[format]);
                    assertEquals(format,prefs.getInt(LibraryLayout.COVER_PREFERENCE,-1));
                    scenario.recreate(); awaitLibrary(scenario);
                    scenario.onActivity(activity -> {
                        RecyclerView list = activity.findViewById(R.id.apps_recycler);
                        float d = activity.getResources().getDisplayMetrics().density;
                        LibraryLayout expected = LibraryLayout.calculate(0,format,
                                Math.round((list.getWidth()-list.getPaddingLeft()-list.getPaddingRight())/d),
                                Math.round((list.getHeight()-list.getPaddingTop()-list.getPaddingBottom())/d),
                                activity.getResources().getConfiguration().fontScale);
                        View card = list.findViewHolderForAdapterPosition(0).itemView;
                        View panel = card.findViewById(R.id.art_container);
                        assertEquals(Math.round(expected.artHeight*d),panel.getHeight(),1);
                        assertEquals(Math.round(expected.artWidth*d),panel.getWidth(),1);
                        assertEquals(ImageView.ScaleType.FIT_CENTER,((ImageView)card.findViewById(R.id.cover)).getScaleType());
                        assertEquals(80,((android.graphics.drawable.BitmapDrawable)((ImageView)card.findViewById(R.id.cover)).getDrawable()).getBitmap().getWidth());
                        assertTrue("One complete card must remain visible",card.getHeight() < list.getHeight());
                        assertTrue("No clipped title",card.findViewById(R.id.name).getBottom() <= card.getHeight());
                    });
                    for (int position=1;position<4;position++) {
                        final int at=position;
                        scenario.onActivity(activity -> ((RecyclerView)activity.findViewById(R.id.apps_recycler)).scrollToPosition(at));
                        InstrumentationRegistry.getInstrumentation().waitForIdleSync(); SystemClock.sleep(150);
                        scenario.onActivity(activity -> {
                            RecyclerView list=activity.findViewById(R.id.apps_recycler);
                            ImageView image=list.findViewHolderForAdapterPosition(at).itemView.findViewById(R.id.cover);
                            assertEquals(ImageView.ScaleType.FIT_CENTER,image.getScaleType());
                            if(at<3)assertEquals(at==1?160:240,((android.graphics.drawable.BitmapDrawable)image.getDrawable()).getBitmap().getWidth());
                            else assertNotNull(image.getDrawable());
                        });
                    }
                    scenario.onActivity(activity -> ((RecyclerView)activity.findViewById(R.id.apps_recycler)).scrollToPosition(0));
                }
            }
            assertArrayEquals(originalArt,Files.readAllBytes(art.toPath()));
        } finally {
            init.invoke(null,originalRoot);
            prefs.edit().putInt(LibraryLayout.COVER_PREFERENCE,oldFormat).putInt("pref_library_view_mode",oldMode).putInt("pref_app_sort",oldSort).commit();
            SharedPreferences.Editor restore = prefs.edit();
            for (String key : prefs.getAll().keySet()) if (key.startsWith("library")) restore.remove(key);
            navigation.forEach((key, value) -> {
                if (value instanceof String) restore.putString(key, (String)value);
                else if (value instanceof Integer) restore.putInt(key, (Integer)value);
                else if (value instanceof Boolean) restore.putBoolean(key, (Boolean)value);
                else if (value instanceof Long) restore.putLong(key, (Long)value);
                else if (value instanceof Float) restore.putFloat(key, (Float)value);
            });
            restore.commit();
        }
    }

    private void click(String text) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (View window : WindowInspector.getGlobalWindowViews()) {
                View view = find(window,text); if (view != null) { view.performClick(); return; }
            }
            fail("Missing action: " + text);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    private View find(View view,String text) {
        if (view instanceof TextView && text.contentEquals(((TextView)view).getText())) return view;
        if (view instanceof ViewGroup) for (int i=0;i<((ViewGroup)view).getChildCount();i++) {
            View child=find(((ViewGroup)view).getChildAt(i),text); if(child!=null)return child;
        }
        return null;
    }
    private void awaitLibrary(ActivityScenario<MainActivity> scenario) {
        AtomicBoolean ready = new AtomicBoolean(); long deadline = SystemClock.uptimeMillis()+8000;
        do {
            scenario.onActivity(activity -> {
                RecyclerView list = activity.findViewById(R.id.apps_recycler);
                ready.set(list != null && list.getAdapter().getItemCount()==8 && !list.hasPendingAdapterUpdates()
                        && list.findViewHolderForAdapterPosition(0)!=null && list.getHeight()>0);
            });
            SystemClock.sleep(100);
        } while (!ready.get() && SystemClock.uptimeMillis()<deadline);
        assertTrue("Gallery did not settle",ready.get());
    }
}
