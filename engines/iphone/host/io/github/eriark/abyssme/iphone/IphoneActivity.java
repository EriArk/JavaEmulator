/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.iphone;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;
import org.libsdl.app.SDLActivity;
import org.libsdl.app.SDLSurface;
import io.github.eriark.abyssme.storage.StoragePaths;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.input.ControllerInput;
import ru.playsoftware.j2meloader.storage.StorageActivity;
import static javax.microedition.lcdui.Canvas.*;

public final class IphoneActivity extends SDLActivity {
    private File game;
    private SDLSurface surface;
    private Bitmap capture;
    private int[] completedFrame;
    private boolean copying, hasFrame;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean landscape, ready, closing;
    private int width = 320, height = 480;
    private float x, y;
    private boolean left, right, up, down, pressed, cursor;
    private long lastFrame;
    private static native void pauseGuest(boolean value);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (StorageActivity.guard(this) || mBrokenLibraries) return;
        try {
            String id = getIntent().getStringExtra("installed");
            IphoneLibrary library = new IphoneLibrary();
            File dir = library.installed(this,id);
            Properties meta = library.metadata(dir);
            game = new File(dir,"game.ipa");
            File data = new File(StoragePaths.library(this),"iphone/data/" + id);
            Files.createDirectories(data.toPath());
            nativeSetenv("ABYSSME_IPHONE_DATA", data.getAbsolutePath());
            landscape = meta.getProperty("orientation","").contains("Landscape");
            if (landscape) { width = 480; height = 320; }
            x = width / 2f; y = height / 2f;
            completedFrame = new int[width * height];
            capture = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            prepareSession(new File(Config.getConfigsDir(),"iphone-" + id));
            ready = true;
            sessionStarted(meta.getProperty("title"));
        } catch (IOException | RuntimeException e) { fail(e.toString()); }
    }
    @Override protected String[] getLibraries() { return new String[]{"SDL2","touchHLE"}; }
    @Override protected String[] getArguments() { return new String[]{game.getAbsolutePath()}; }
    @Override protected SDLSurface createSDLSurface(Context c) {
        surface = new SDLSurface(c);
        return surface;
    }
    @Override protected View guestSurface() { return mLayout; }
    @Override public void setOrientationBis(int w,int h,boolean resizable,String hint) {
        // SDL renders into the guest surface; the shared game area owns Android orientation.
    }
    @Override protected int width() { return width; }
    @Override protected int height() { return height; }
    @Override protected boolean directTouchFirst() { return true; }
    @Override protected boolean multiTouch() { return true; }
    @Override protected void guestTouch(int id, int action, float px, float py) {
        cursor = false;
        touch(id,action,px,py);
    }
    private void touch(int id,int action,float px,float py) {
        if (ready) SDLActivity.onNativeTouch(1,id,action,px / width,py / height,action == MotionEvent.ACTION_UP ? 0f : 1f);
    }
    @Override protected ControllerInput.Sink keys() {
        return new ControllerInput.Sink() {
            public void press(int key) { button(key,true); }
            public void release(int key) { button(key,false); }
            public void repeat(int key) {}
        };
    }
    private void button(int key,boolean active) {
        cursor = true;
        switch (key) {
            case KEY_UP: up = active; break;
            case KEY_DOWN: down = active; break;
            case KEY_LEFT: left = active; break;
            case KEY_RIGHT: right = active; break;
            case KEY_FIRE:
                if (pressed != active) {
                    pressed = active;
                    touch(64,active ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_UP,x,y);
                }
                break;
        }
    }
    private void moveCursor() {
        long now = SystemClock.uptimeMillis();
        float step = Math.min(50, now - lastFrame) * .22f;
        lastFrame = now;
        int dx = (right ? 1 : 0) - (left ? 1 : 0), dy = (down ? 1 : 0) - (up ? 1 : 0);
        if (dx == 0 && dy == 0) return;
        x = Math.max(0,Math.min(width-1,x + dx * step));
        y = Math.max(0,Math.min(height-1,y + dy * step));
        if (pressed) touch(64,MotionEvent.ACTION_MOVE,x,y);
    }
    @Override protected boolean frame(int[] pixels) {
        moveCursor();
        if (!copying && surface.getHolder().getSurface().isValid()) {
            copying = true;
            PixelCopy.request(surface, capture, status -> {
                copying = false;
                if (status == PixelCopy.SUCCESS && !closing) {
                    capture.getPixels(completedFrame, 0, width, 0, 0, width, height);
                    hasFrame = true;
                }
            }, ui);
        }
        if (!hasFrame) return false;
        System.arraycopy(completedFrame, 0, pixels, 0, pixels.length);
        if (cursor) {
            int cx = (int)x, cy = (int)y;
            for (int i=-6; i<=6; i++) {
                int color = Math.abs(i) < 4 ? 0xffffffff : 0xff202020;
                if (cx+i>=0 && cx+i<width) pixels[cy*width+cx+i] = color;
                if (cy+i>=0 && cy+i<height) pixels[(cy+i)*width+cx] = color;
            }
        }
        return true;
    }
    @Override protected void pauseCore(boolean paused) { if (ready && !closing) pauseGuest(paused); }
    private static native void setGuestSpeed(int percent);
    @Override protected void setSpeed(int percent) { setGuestSpeed(percent); }
    @Override protected boolean exited() { return false; }
    @Override protected void stopCore() { if (ready) pauseGuest(false); }
    @Override public void onDestroy() {
        closing = true;
        if (ready) pauseGuest(false);
        super.onDestroy();
        ready = false;

    }
}
