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
                ViewConfiguration.get(host).getScaledTouchSlop(), keys, this::invalidate, () -> {},
                () -> TouchFeedback.perform(this, profile.vkFeedback));
        touch.deck.setTheme(host);
    }
    void layoutControls(boolean connected) {
        touch.deck.setEnabled(DeviceControls.showTouch(profile, connected));
        touch.layout(getWidth(), getHeight());
        invalidate();
    }
    void release() { touch.cancel(); }
    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) { touch.layout(w, h); }
    @Override public boolean onTouchEvent(MotionEvent event) { return !paused && touch.touch(event); }
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
                if (hasPreviewContent()) {
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
        float scale = integer ? PictureScale.integerFit(W, H, aw, ah)
                : profile.screenScaleType == 0 ? 1 : Math.min(aw / W, ah / H);
        int w = Math.round(profile.screenScaleType == 2 ? aw : W * scale);
        int h = Math.round(profile.screenScaleType == 2 ? ah : H * scale);
        boolean enhanced = gpu != null && MophunGpuView.enabled(profile) && gpu.update(this, area, w, h, rotation, profile, paused);
        if (!enhanced) canvas.drawColor(0xff101417);
        canvas.save();
        canvas.clipRect(area);
        canvas.translate(PictureScale.center(area.centerX(), quarter ? h : w, integer),
                PictureScale.center(area.centerY(), quarter ? w : h, integer));
        canvas.rotate(rotation);
        dest.set(-w / 2f, -h / 2f, w / 2f, h / 2f);
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
