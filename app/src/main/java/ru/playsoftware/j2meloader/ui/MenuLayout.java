/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.ui;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.*;
import androidx.preference.PreferenceManager;
import java.util.WeakHashMap;
import ru.playsoftware.j2meloader.BuildConfig;

/** Menu presentation only. Game activities keep their own orientation and viewport. */
public final class MenuLayout {
    public static final String ORIENTATION="pref_menu_orientation", MARGIN="pref_portrait_menu_margin";
    private static final WeakHashMap<Activity,Integer> defaults=new WeakHashMap<>();
    private static final WeakHashMap<View,int[]> padding=new WeakHashMap<>();
    private MenuLayout() { }
    public static int margin(Context c) {return Math.max(0,Math.min(480,PreferenceManager.getDefaultSharedPreferences(c).getInt(MARGIN,0)));}
    public static int inset(Context c) {
        if(c.getResources().getConfiguration().orientation!=Configuration.ORIENTATION_PORTRAIT)return 0;
        android.util.DisplayMetrics m=c.getResources().getDisplayMetrics();
        return Math.max(0,Math.min(Math.round(margin(c)*m.density),m.heightPixels-Math.round(200*m.density)));
    }
    public static int orientation(String value,int fallback) {
        return "portrait".equals(value)?ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT:
            "landscape".equals(value)?ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:fallback;
    }
    public static void apply(Activity a) {
        apply(a, a.findViewById(android.R.id.content));
    }
    public static void apply(Activity a, View content) {
        int fallback=defaults.computeIfAbsent(a,x->BuildConfig.HANDHELD_MODE?ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:a.getRequestedOrientation());
        int selected=orientation(PreferenceManager.getDefaultSharedPreferences(a).getString(ORIENTATION,"auto"),fallback);
        if(a.getRequestedOrientation()!=selected)a.setRequestedOrientation(selected);
        if(content!=null)attach(content);
    }
    public static void attach(View content) {
        if(!padding.containsKey(content)) {
            padding.put(content,new int[]{content.getPaddingLeft(),content.getPaddingTop(),content.getPaddingRight(),content.getPaddingBottom()});
            content.getViewTreeObserver().addOnGlobalLayoutListener(()->pad(content));
        }
        pad(content);
    }
    private static void pad(View content) {
        int[] original=padding.get(content);if(original==null)return;
        int bottom=inset(content.getContext());
        androidx.core.view.WindowInsetsCompat insets=androidx.core.view.ViewCompat.getRootWindowInsets(content);
        if(insets!=null&&insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()))
            bottom=Math.max(0,bottom-insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom);
        bottom+=original[3];
        if(content.getPaddingBottom()!=bottom)content.setPadding(original[0],original[1],original[2],bottom);
    }
    public static void fitMapper(Window w) {
        int inset=inset(w.getContext());WindowManager.LayoutParams p=w.getAttributes();
        int height=inset==0?WindowManager.LayoutParams.MATCH_PARENT:w.getContext().getResources().getDisplayMetrics().heightPixels-inset;
        if(p.height!=height||p.gravity!=(Gravity.TOP|Gravity.CENTER_HORIZONTAL)) {
            p.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;p.y=0;p.width=WindowManager.LayoutParams.MATCH_PARENT;p.height=height;w.setAttributes(p);
        }
    }
    public static void watchDialog(Dialog dialog) {
        Window window=dialog.getWindow();if(window==null)return;
        View decor=window.getDecorView();
        final int[] original={Integer.MIN_VALUE,0,0,0};
        decor.getViewTreeObserver().addOnGlobalLayoutListener(()->{
            WindowManager.LayoutParams p=window.getAttributes();
            if(original[0]==Integer.MIN_VALUE){original[0]=p.height;original[1]=p.gravity;original[2]=p.y;}
            original[3]=Math.max(original[3],decor.getHeight());
            int inset=inset(dialog.getContext());
            int height=original[0],gravity=original[1],y=original[2];
            if(inset>0){
                int max=Math.max(1,dialog.getContext().getResources().getDisplayMetrics().heightPixels-inset-Math.round(24*dialog.getContext().getResources().getDisplayMetrics().density));
                if(height>max||height==WindowManager.LayoutParams.MATCH_PARENT||original[3]>max)height=max;
                gravity=Gravity.CENTER;y=-inset/2;
            }
            if(p.height!=height||p.gravity!=gravity||p.y!=y){p.height=height;p.gravity=gravity;p.y=y;window.setAttributes(p);}
        });
    }
    private static boolean menu(Activity a) {
        String n=a.getClass().getName();
        return !(a instanceof GameAreaActivity) && !n.equals("javax.microedition.shell.MicroActivity")&&!n.endsWith("MophunGameActivity")&&!n.endsWith("WipiGameActivity")
            &&!n.endsWith("SymbianActivity")&&!n.endsWith("GameRestartActivity")&&!n.endsWith("CompatibilityTestActivity");
    }
    public static void install(Application app) {
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            public void onActivityCreated(Activity a,Bundle b){if(menu(a))apply(a);}
            public void onActivityResumed(Activity a){if(menu(a))apply(a);}
            public void onActivityStarted(Activity a){}
            public void onActivityPaused(Activity a){}
            public void onActivityStopped(Activity a){}
            public void onActivitySaveInstanceState(Activity a,Bundle b){}
            public void onActivityDestroyed(Activity a){defaults.remove(a);}
        });
    }
}
