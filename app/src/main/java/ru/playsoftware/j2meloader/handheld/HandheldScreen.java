/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.handheld;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.input.*;
import ru.playsoftware.j2meloader.mophun.MophunGpuView;
import ru.playsoftware.j2meloader.ui.*;

/** Native-frame consumer shared by the Canvas and enhancement pipeline. */
final class HandheldScreen extends View implements NativeFrame {
    private final int W, H;
    private final ProfileModel profile;
    private final HandheldActivity host;
    private final NativeTouchControls touch;
    private final Bitmap bitmap;
    private final int[] pixels;
    private final RectF dest = new RectF();
    private final Paint paint = new Paint();
    private final LcdEffect lcd = new LcdEffect();
    private volatile long serial;
    private long nanos;
    private final android.graphics.Matrix stageToView = new android.graphics.Matrix();
    private final float[] tapPoint = new float[2];
    private java.io.File preview;
    private long nextPreviewFrame = 240;
    boolean paused = true;
    int fps;
    MophunGpuView gpu;

    HandheldScreen(HandheldActivity host, ProfileModel profile, ControllerInput.Sink keys) {
        super(host);
        this.host = host;
        String engine = host.getIntent().getStringExtra("artworkEngine");
        String id = host.getIntent().getStringExtra("installed");
        if (engine != null && engine.matches("[a-z0-9]+") && id != null && id.matches("[a-f0-9]{64}")) {
            java.io.File dir = new java.io.File(io.github.eriark.abyssme.storage.StoragePaths.library(host), engine + "/packages/" + id);
            if (dir.isDirectory() && !engine.equals("cybiko") && !new java.io.File(dir, "icon.png").exists()
                    && !new java.io.File(dir, "cover.png").exists()
                    && !new java.io.File(dir, "preview.png").exists())
                preview = new java.io.File(dir, "preview.png");
        }
        W = host.width(); H = host.height();
        bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        pixels = new int[W * H];
        this.profile = profile;
        touch = new NativeTouchControls(profile, getResources().getDisplayMetrics().density,
                ViewConfiguration.get(host).getScaledTouchSlop(), keys, this::invalidate, this::stageTap,
                () -> TouchFeedback.perform(this, profile.vkFeedback));
        touch.deck.setTheme(host);
    }
    void layoutControls(boolean connected) {
        boolean automatic = profile.getTouchVisibility() == 0 && profile.controlMode == 0;
        touch.deck.setEnabled(!(host.directTouchFirst() && automatic) && DeviceControls.showTouch(profile, connected));
        touch.layout(getWidth(), getHeight());
        invalidate();
    }
    private int penPointer = -1;
    private void liftPen() {
        if (penPointer != -1) host.stylus(0, 0, false);
        penPointer = -1;
    }
    private final android.util.SparseArray<float[]> fingers = new android.util.SparseArray<>();
    private void releaseFingers() {
        for (int i=0; i<fingers.size(); i++) {
            float[] point = fingers.valueAt(i);
            host.guestTouch(fingers.keyAt(i), MotionEvent.ACTION_UP, point[0], point[1]);
        }
        fingers.clear();
    }
    void release() { liftPen(); releaseFingers(); touch.cancel(); }
    private void routeFingers(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_CANCEL) { releaseFingers(); return; }
        boolean move = action == MotionEvent.ACTION_MOVE;
        boolean down = action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN;
        boolean up = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP;
        if (!move && !down && !up) return;
        Matrix inverse = new Matrix();
        if (!stageToView.invert(inverse)) return;
        for (int i=0; i<event.getPointerCount(); i++) {
            if (!move && i != event.getActionIndex()) continue;
            int id = event.getPointerId(i);
            float x = event.getX(i), y = event.getY(i);
            if (down && touch.deck.containsKey(x,y)) continue;
            float[] point = fingers.get(id);
            if (point == null && !down) continue;
            if (point == null) point = new float[2];
            point[0] = x; point[1] = y; inverse.mapPoints(point);
            if (down && (point[0]<0 || point[1]<0 || point[0]>=W || point[1]>=H)) continue;
            point[0] = Math.max(0, Math.min(W-1, point[0]));
            point[1] = Math.max(0, Math.min(H-1, point[1]));
            host.guestTouch(id, down ? MotionEvent.ACTION_DOWN : up ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE, point[0],point[1]);
            if (up) fingers.remove(id); else fingers.put(id,point);
        }
    }
    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) { touch.layout(w, h); }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (paused) return false;
        if (host.multiTouch()) routeFingers(event);
        else if (host.usesStylus()) routePen(event);
        tapPoint[0]=event.getX(); tapPoint[1]=event.getY();
        return touch.touch(event);
    }
    private void routePen(MotionEvent event) {
        int action = event.getActionMasked(), index = event.getActionIndex();
        if (action == MotionEvent.ACTION_CANCEL) { liftPen(); return; }
        int id = event.getPointerId(index);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            if (id == penPointer) liftPen();
            return;
        }
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            if (penPointer != -1 || touch.deck.containsKey(event.getX(index), event.getY(index))) return;
        } else if (action == MotionEvent.ACTION_MOVE) {
            index = event.findPointerIndex(penPointer);
            if (index < 0) return;
            id = penPointer;
        } else return;
        Matrix inverse = new Matrix();
        if (!stageToView.invert(inverse)) return;
        float[] point = {event.getX(index), event.getY(index)};
        inverse.mapPoints(point);
        if (point[0] < 0 || point[1] < 0 || point[0] >= W || point[1] >= H) { liftPen(); return; }
        penPointer = id;
        host.stylus(point[0], point[1], true);
    }
    private void stageTap() {
        android.graphics.Matrix inverse=new android.graphics.Matrix();
        if (!stageToView.invert(inverse)) return;
        inverse.mapPoints(tapPoint);
        if (tapPoint[0]>=0 && tapPoint[1]>=0 && tapPoint[0]<W && tapPoint[1]<H)
            host.gameTap(tapPoint[0],tapPoint[1]);
    }
    public int frameWidth() { return W; }
    public int frameHeight() { return H; }
    public long presentationSerial() { return serial; }
    public synchronized boolean copyPresented(int[] out, long[] stamp, long previous) {
        if (serial == 0 || serial == previous) return false;
        System.arraycopy(pixels, 0, out, 0, pixels.length);
        stamp[0] = serial;
        stamp[1] = nanos;
        return true;
    }
    private boolean hasPreviewContent() {
        // A busy image can have a unique top-left pixel. Compare against the dominant
        // colour instead so a blank or nearly blank framebuffer is the only rejection.
        int candidate=0, votes=0;
        for (int i=0;i<pixels.length;i+=7) {
            if (votes==0) candidate=pixels[i];
            votes+=pixels[i]==candidate ? 1 : -1;
        }
        int different=0;
        for (int i=0;i<pixels.length;i+=7) if (pixels[i]!=candidate) different++;
        return different>pixels.length/140;
    }
    private synchronized void poll() {
        if (host.frame(pixels)) {
            bitmap.setPixels(pixels, 0, W, 0, 0, W, H);
            nanos = System.nanoTime();
            serial++;
            if (preview != null && serial >= nextPreviewFrame) {
                nextPreviewFrame = serial + 60;
                if (host.artworkReady() && hasPreviewContent()) {
                    java.io.File target = preview;
                    preview = null;
                    Bitmap copy = bitmap.copy(Bitmap.Config.ARGB_8888, false);
                    new Thread(() -> {
                        try {
                            if (target.getParentFile().isDirectory())
                                ru.playsoftware.j2meloader.applist.GameArtwork.writeBitmap(target, copy);
                        } catch (java.io.IOException e) { android.util.Log.d("HandheldScreen", "Preview unavailable", e); }
                        finally { copy.recycle(); }
                    }, "Game artwork").start();
                }
            }
        }
    }
    @Override protected void onDraw(Canvas canvas) {
        if (!paused) poll();
        RectF area = touch.gameArea();
        int rotation = Math.floorMod(profile.screenRotation, 360);
        boolean quarter = rotation % 180 != 0;
        float aw = quarter ? area.height() : area.width(), ah = quarter ? area.width() : area.height();
        boolean integer = profile.screenScaleType == PictureScale.INTEGER;
        int displayWidth = host.displayWidth();
        float scale = integer ? PictureScale.integerFit(displayWidth, H, aw, ah)
                : profile.screenScaleType == 0 ? 1 : Math.min(aw / displayWidth, ah / H);
        int w = Math.round(profile.screenScaleType == 2 ? aw : displayWidth * scale);
        int h = Math.round(profile.screenScaleType == 2 ? ah : H * scale);
        boolean enhanced = gpu != null && MophunGpuView.enabled(profile) && gpu.update(this, area, w, h, rotation, profile, paused);
        if (!enhanced) canvas.drawColor(0xff101417);
        canvas.save();
        canvas.clipRect(area);
        canvas.translate(PictureScale.center(area.centerX(), quarter ? h : w, integer),
                PictureScale.center(area.centerY(), quarter ? w : h, integer));
        canvas.rotate(rotation);
        dest.set(-w / 2f, -h / 2f, w / 2f, h / 2f);
        stageToView.setScale(w/(float)W,h/(float)H);
        stageToView.postTranslate(dest.left,dest.top);
        stageToView.postRotate(rotation);
        stageToView.postTranslate(PictureScale.center(area.centerX(), quarter ? h : w, integer),
                PictureScale.center(area.centerY(), quarter ? w : h, integer));
        paint.setFilterBitmap(profile.screenFilter);
        if (!enhanced) canvas.drawBitmap(bitmap, null, dest, paint);
        lcd.draw(canvas, dest, W, H, 0, profile.getRetroShader() == 0 ? profile.screenEffect : 0, profile.screenEffectIntensity);
        canvas.restore();
        touch.draw(canvas);
        if (paused) return;
        String error = host.error();
        if (error != null) { paused = true; post(() -> host.fail(error)); return; }
        if (host.exited()) { paused = true; post(host::finish); return; }
        if (fps > 0 && fps < 60) postInvalidateDelayed(Math.max(1, 1000 / fps));
        else postInvalidateOnAnimation();
    }
}
