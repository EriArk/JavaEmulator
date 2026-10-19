package ru.playsoftware.j2meloader.util;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.ui.LcdEffect;

/** Immutable settings captured at shutter time, independent of later profile edits. */
public final class ScreenshotEffect {
    private final int mode, intensity;
    private final boolean smoothing;

    public ScreenshotEffect(ProfileModel profile) {
        mode = profile.screenshotWithEffect ? LcdEffect.mode(profile.screenEffect) : 0;
        intensity = LcdEffect.intensity(profile.screenEffectIntensity);
        smoothing = profile.screenFilter;
    }

    /** Input is already oriented. Caller owns input and any distinct returned bitmap. */
    public Bitmap render(Bitmap frame, int guestWidth, int guestHeight) {
        if (mode == 0 || intensity == 0 || guestWidth <= 0 || guestHeight <= 0) return frame;
        // Give the LCD cell four output pixels when possible, bounded to 4 MP/4096 per side.
        double scale = Math.max(1, 4 / Math.min((double) frame.getWidth() / guestWidth,
                (double) frame.getHeight() / guestHeight));
        scale = Math.min(scale, Math.min(4096.0 / frame.getWidth(), 4096.0 / frame.getHeight()));
        scale = Math.min(scale, Math.sqrt(4_000_000.0 / ((double) frame.getWidth() * frame.getHeight())));
        int width = Math.max(1, (int) Math.floor(frame.getWidth() * scale));
        int height = Math.max(1, (int) Math.floor(frame.getHeight() * scale));
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(result);
            RectF bounds = new RectF(0, 0, width, height);
            Paint sampling = new Paint(); sampling.setFilterBitmap(smoothing);
            canvas.drawBitmap(frame, null, bounds, sampling);
            new LcdEffect().draw(canvas, bounds, guestWidth, guestHeight, 0, mode, intensity);
            return result;
        } catch (RuntimeException | Error error) {
            result.recycle();
            throw error;
        }
    }
}
