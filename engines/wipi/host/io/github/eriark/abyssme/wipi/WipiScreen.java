/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.wipi;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import com.parkjeongseop.wipi.WipiNative;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.input.*;
import ru.playsoftware.j2meloader.mophun.MophunGpuView;
import ru.playsoftware.j2meloader.ui.*;

/** Native-frame consumer shared by the Canvas and enhancement pipeline. */
final class WipiScreen extends View implements NativeFrame {
    private static final int W = 240, H = 320;
    private final ProfileModel profile;
    private final WipiGameActivity host;
    private final NativeTouchControls touch;
    private final Bitmap bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
    private final int[] pixels = new int[W * H], errorKind = new int[1];
    private final RectF dest = new RectF();
    private final Paint paint = new Paint();
    private final LcdEffect lcd = new LcdEffect();
    private volatile long serial;
    private long nanos;
    boolean paused = true;
    int fps;
    MophunGpuView gpu;

    WipiScreen(WipiGameActivity host, ProfileModel profile, ControllerInput.Sink keys) {
        super(host);
        this.host = host;
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
    private synchronized void poll() {
        if (WipiNative.nativeGetFrame(pixels)) {
            bitmap.setPixels(pixels, 0, W, 0, 0, W, H);
            nanos = System.nanoTime();
            serial++;
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
        String error = WipiNative.nativeGetError(errorKind);
        if (error != null) { paused = true; post(() -> host.fail(error)); return; }
        if (WipiNative.nativePollExit()) { paused = true; post(host::finish); return; }
        if (fps > 0 && fps < 60) postInvalidateDelayed(Math.max(1, 1000 / fps));
        else postInvalidateOnAnimation();
    }
}
