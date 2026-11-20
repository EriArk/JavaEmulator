/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.wipi;

import android.app.Activity;
import android.content.Intent;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import com.parkjeongseop.wipi.WipiNative;
import io.github.eriark.abyssme.storage.StoragePaths;
import ru.playsoftware.j2meloader.storage.StorageActivity;
import ru.playsoftware.j2meloader.ui.GameArea;
import java.io.*;
import java.util.HashSet;

/** Debug-only host; picker, display and input belong to the AbyssME APK. */
public final class WipiGameActivity extends Activity {
    private boolean started, resumed;
    private Uri selected;
    private final HashSet<String> held = new HashSet<>();
    private Screen screen;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (StorageActivity.guard(this)) return;
        selected = state == null ? getIntent().getData() : state.getParcelable("game");
        if (selected != null) load();
        else if (state == null) startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), 1);
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putParcelable("game", selected);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (request!=1) return;
        if (result!=RESULT_OK || data==null || data.getData()==null) {finish();return;}
        selected=data.getData();
        load();
    }
    private void load() {
        new Thread(()->{
            try (InputStream input=getContentResolver().openInputStream(selected)) {
                WipiPackage game=WipiPackage.read(input);
                runOnUiThread(()->{
                    if (isDestroyed() || isFinishing() || StorageActivity.guard(this)) return;
                    try {
                        File saves = new File(StoragePaths.library(this), "wipi/data/" + game.id);
                        if (!saves.isDirectory() && !saves.mkdirs()) {
                            throw new IllegalStateException("Cannot open WIPI save directory");
                        }
                        WipiNative.initialize(this);
                        started=WipiNative.nativeStart(game.bytes,"game.zip",saves.getPath(),"");
                        if (!started) throw new IllegalStateException("Could not start "+game.carrier+" session");
                        WipiNative.nativeSetPaused(!resumed);
                        screen=new Screen();
                        setContentView(GameArea.wrap(screen));
                        setTitle(game.carrier+" game");
                    } catch (RuntimeException | LinkageError e) {fail(e.toString());}
                });
            } catch (Exception e) {runOnUiThread(()->fail(e.toString()));}
        },"WIPI import").start();
    }
    private void fail(String message) {
        if (isFinishing() || isDestroyed()) return;
        android.util.Log.e("AbyssWipi",message);
        new android.app.AlertDialog.Builder(this).setMessage(message)
                .setPositiveButton("Close",(d,w)->finish()).setOnCancelListener(d->finish()).show();
    }
    @Override public void onResume() {
        super.onResume();
        resumed=true;
        if (started) {WipiNative.nativeSetPaused(false);screen.invalidate();}
    }
    @Override public void onPause() {
        resumed=false;
        if(started) {
            for(String key:held) WipiNative.nativeKeyUp(key);
            held.clear();
            WipiNative.nativeSetPaused(true);
        }
        super.onPause();
    }
    @Override public void onDestroy() {
        if (started) WipiNative.nativeStop();
        super.onDestroy();
    }
    private static String key(int code) {
        if(code>=KeyEvent.KEYCODE_0 && code<=KeyEvent.KEYCODE_9) return Integer.toString(code-KeyEvent.KEYCODE_0);
        switch(code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:return "LEFT";
            case KeyEvent.KEYCODE_DPAD_RIGHT:return "RIGHT";
            case KeyEvent.KEYCODE_DPAD_UP:return "UP";
            case KeyEvent.KEYCODE_DPAD_DOWN:return "DOWN";
            case KeyEvent.KEYCODE_BUTTON_A:case KeyEvent.KEYCODE_DPAD_CENTER:return "OK";
            case KeyEvent.KEYCODE_SOFT_LEFT:return "SOFT_L";
            case KeyEvent.KEYCODE_SOFT_RIGHT:return "SOFT_R";
            case KeyEvent.KEYCODE_DEL:return "CLR";
            case KeyEvent.KEYCODE_STAR:return "*";
            case KeyEvent.KEYCODE_POUND:return "#";
            default:return null;
        }
    }
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        String k=key(e.getKeyCode());
        if(started && k!=null) {
            if(e.getAction()==KeyEvent.ACTION_DOWN && held.add(k)) WipiNative.nativeKeyDown(k);
            if(e.getAction()==KeyEvent.ACTION_UP && held.remove(k)) WipiNative.nativeKeyUp(k);
            return true;
        }
        return super.dispatchKeyEvent(e);
    }
    private final class Screen extends View {
        final Bitmap frame=Bitmap.createBitmap(240,320,Bitmap.Config.ARGB_8888);
        final int[] pixels=new int[240*320], errorKind=new int[1];
        final Rect dest=new Rect();
        boolean ended;
        Screen(){super(WipiGameActivity.this);}
        @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){
            float scale=Math.min(w/240f,h/320f);
            int dx=Math.round(240*scale),dy=Math.round(320*scale);
            dest.set(0,0,dx,dy);dest.offset((w-dx)/2,(h-dy)/2);
        }
        @Override protected void onDraw(Canvas canvas) {
            canvas.drawColor(Color.BLACK);
            if(!resumed || ended)return;
            if(WipiNative.nativeGetFrame(pixels))frame.setPixels(pixels,0,240,0,0,240,320);
            canvas.drawBitmap(frame,null,dest,null);
            String error=WipiNative.nativeGetError(errorKind);
            if(error!=null){ended=true;post(()->fail(error));return;}
            if(WipiNative.nativePollExit()){ended=true;post(WipiGameActivity.this::finish);return;}
            postInvalidateOnAnimation();
        }
    }
}
