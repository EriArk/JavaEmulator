package ru.playsoftware.j2meloader.input;

import android.graphics.*;
import android.net.Uri;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import ru.playsoftware.j2meloader.config.*;
import ru.playsoftware.j2meloader.util.*;
import static org.junit.Assert.*;

public class ScreenshotEffectTest {
    private ProfileModel profile() {
        ProfileModel p = new ProfileModel(); p.screenshotWithEffect = true;
        p.screenEffect = 2; p.screenEffectIntensity = 70; return p;
    }
    private Bitmap frame(int w, int h) {
        Bitmap b = Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888); b.eraseColor(Color.YELLOW); return b;
    }
    @Test public void cleanOffZeroAndInvalidModePreserveExactInput() {
        Bitmap b = frame(8,12); ProfileModel p = profile();
        p.screenshotWithEffect = false; assertSame(b,new ScreenshotEffect(p).render(b,8,12));
        p.screenshotWithEffect = true; p.screenEffect = 0; assertSame(b,new ScreenshotEffect(p).render(b,8,12));
        p.screenEffect = 99; assertSame(b,new ScreenshotEffect(p).render(b,8,12));
        p.screenEffect = 2; p.screenEffectIntensity = 0; assertSame(b,new ScreenshotEffect(p).render(b,8,12));
        assertEquals(Color.YELLOW,b.getPixel(3,3)); b.recycle();
    }
    @Test public void styledExportHasVisibleCellsAndDoesNotMutateOrRecycleInput() {
        Bitmap b = frame(8,12); Bitmap out = new ScreenshotEffect(profile()).render(b,8,12);
        assertNotSame(b,out); assertEquals(32,out.getWidth()); assertEquals(48,out.getHeight());
        assertNotEquals(out.getPixel(3,3),out.getPixel(1,1));
        assertEquals(Color.YELLOW,b.getPixel(0,0)); out.recycle(); assertFalse(b.isRecycled()); b.recycle();
    }
    @Test public void shutterSettingsAreImmutableAndSoftDiffersFromClassic() {
        ProfileModel p = profile(); ScreenshotEffect captured = new ScreenshotEffect(p);
        p.screenshotWithEffect = false; p.screenEffectIntensity = 0;
        Bitmap b = frame(8,12), classic = captured.render(b,8,12);
        p = profile(); p.screenEffect = 1; Bitmap soft = new ScreenshotEffect(p).render(b,8,12);
        assertNotSame(b,classic); assertFalse(classic.sameAs(soft));
        classic.recycle(); soft.recycle(); b.recycle();
    }
    @Test public void alreadyRotatedAndScaledFramesKeepAspectAndOrientation() {
        Bitmap b = frame(60,40); b.setPixel(0,0,Color.BLUE);
        Bitmap out = new ScreenshotEffect(profile()).render(b,12,8);
        assertEquals(60,out.getWidth()); assertEquals(40,out.getHeight());
        assertTrue(Color.blue(out.getPixel(0,0)) > Color.red(out.getPixel(0,0)));
        assertTrue(Color.red(out.getPixel(59,39)) > Color.blue(out.getPixel(59,39)));
        out.recycle(); b.recycle();
    }
    @Test public void largeGuestDimensionsCannotCreateUnboundedExport() {
        Bitmap b = frame(16,8); Bitmap out = new ScreenshotEffect(profile()).render(b,Integer.MAX_VALUE,Integer.MAX_VALUE);
        assertTrue(out.getWidth() <= 4096); assertTrue(out.getHeight() <= 4096);
        assertTrue((long)out.getWidth()*out.getHeight() <= 4_000_000);
        out.recycle(); b.recycle();
    }
    @Test public void galleryReceivesRenderedPngAndRetainsCallerFrame() throws Exception {
        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Bitmap b = frame(8,12); Uri uri = null;
        try {
            uri = Screenshots.save(context,b,new ScreenshotEffect(profile()),8,12);
            try(java.io.InputStream in = context.getContentResolver().openInputStream(uri)) {
                Bitmap out = BitmapFactory.decodeStream(in);
                assertNotNull(out); assertEquals(32,out.getWidth()); assertEquals(48,out.getHeight());
                assertNotEquals(Color.YELLOW,out.getPixel(0,0)); out.recycle();
            }
            assertFalse(b.isRecycled()); assertEquals(Color.YELLOW,b.getPixel(0,0));
        } finally { b.recycle(); if(uri != null) context.getContentResolver().delete(uri,null,null); }
    }
}
