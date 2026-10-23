package ru.playsoftware.j2meloader.input;

import android.graphics.*;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.io.File;
import java.nio.file.Files;
import ru.playsoftware.j2meloader.config.*;
import ru.playsoftware.j2meloader.ui.LcdEffect;
import static org.junit.Assert.*;

public class LcdEffectTest {
    @Test public void integerGridMatchesRotatedSourceCellsAtOddOffsets() {
        Bitmap source=Bitmap.createBitmap(7,9,Bitmap.Config.ARGB_8888);
        for(int y=0;y<9;y++) for(int x=0;x<7;x++) source.setPixel(x,y,(x+y)%2==0?Color.RED:Color.BLUE);
        try {
            for(int scale:new int[]{3,4,5}) for(int rotation:new int[]{0,90,180,270}) {
                boolean quarter=rotation%180!=0;
                int w=(quarter?9:7)*scale,h=(quarter?7:9)*scale;
                RectF area=new RectF(13,17,13+w,17+h);
                Bitmap base=Bitmap.createBitmap(80,80,Bitmap.Config.ARGB_8888);
                Bitmap mask=Bitmap.createBitmap(80,80,Bitmap.Config.ARGB_8888);
                base.eraseColor(Color.WHITE); mask.eraseColor(Color.WHITE);
                Canvas canvas=new Canvas(base); canvas.translate(area.centerX(),area.centerY()); canvas.rotate(rotation);
                Paint paint=new Paint(); paint.setFilterBitmap(false);
                canvas.drawBitmap(source,null,new RectF(-7*scale/2f,-9*scale/2f,7*scale/2f,9*scale/2f),paint);
                new LcdEffect().draw(new Canvas(mask),area,7,9,rotation,2,100);
                int plain=mask.getPixel(14,18);
                // Rotating a one-pixel gap can place it on either side of the shared texel edge.
                for(int y=18;y<17+h-1;y++) for(int x=14;x<13+w-1;x++) {
                    if(base.getPixel(x,y)!=base.getPixel(x-1,y))
                        assertTrue(mask.getPixel(x,y)!=plain || mask.getPixel(x-1,y)!=plain);
                    if(base.getPixel(x,y)!=base.getPixel(x,y-1))
                        assertTrue(mask.getPixel(x,y)!=plain || mask.getPixel(x,y-1)!=plain);
                }
                assertEquals(Color.WHITE,mask.getPixel(12,18));
                assertEquals(Color.WHITE,mask.getPixel(13+w,18));
                mask.recycle(); base.recycle();
            }
        } finally { source.recycle(); }
    }
    private Bitmap image(int mode, int strength, int width, int height, int rotation) {
        Bitmap image = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888);
        image.eraseColor(Color.rgb(240, 160, 80));
        new LcdEffect().draw(new Canvas(image), new RectF(16,16,144,144), width,height,rotation,mode,strength);
        return image;
    }
    @Test public void offZeroAndUnknownArePixelIdentical() {
        Bitmap baseline=image(0,70,16,16,0);
        for(int mode:new int[]{-1,3,999}) {
            Bitmap other=image(mode,70,16,16,0); assertTrue(baseline.sameAs(other)); other.recycle();
        }
        Bitmap zero=image(2,0,16,16,0); assertTrue(baseline.sameAs(zero));
        zero.recycle(); baseline.recycle();
    }
    @Test public void modesDifferAndLetterboxIsUntouched() {
        Bitmap soft=image(1,70,16,16,0), classic=image(2,70,16,16,0);
        assertFalse(soft.sameAs(classic));
        assertEquals(Color.rgb(240,160,80), classic.getPixel(4,80));
        assertNotEquals(classic.getPixel(79,79),classic.getPixel(83,83));
        soft.recycle(); classic.recycle();
    }
    @Test public void smallScalesRemoveGridInsteadOfAliasing() {
        assertEquals(0,LcdEffect.gridStrength(1),0);
        assertEquals(0,LcdEffect.gridStrength(2),0);
        assertEquals(.5f,LcdEffect.gridStrength(3),0);
        assertEquals(1,LcdEffect.gridStrength(4),0);
        Bitmap small=image(2,70,128,128,0);
        assertEquals(small.getPixel(60,60), small.getPixel(61,61)); small.recycle();
    }
    @Test public void rotationAndStretchRemainInsidePicture() {
        for(int rotation:new int[]{0,90,180,270}) {
            Bitmap picture=image(2,100,16,32,rotation);
            assertEquals(Color.rgb(240,160,80),picture.getPixel(145,80));
            assertNotEquals(Color.rgb(240,160,80),picture.getPixel(80,80)); picture.recycle();
        }
    }
    @Test public void fractionalGridUsesPixelBoundariesWithoutPeriodicDarkBands() {
        for (int width : new int[]{56,77,96}) {
            Bitmap b=Bitmap.createBitmap(width,95,Bitmap.Config.ARGB_8888); b.eraseColor(Color.WHITE);
            new LcdEffect().draw(new Canvas(b),new RectF(0,0,width,95),16,20,0,2,70);
            int interior=b.getPixel(1,1), line=b.getPixel(Math.round(width/16f)-1,1);
            int crossing=b.getPixel(Math.round(width/16f)-1,Math.round(95/20f)-1);
            assertNotEquals(interior,line);
            for(int y=0;y<95;y++) for(int x=0;x<width;x++) {
                boolean vertical=false, horizontal=false;
                for(int i=1;i<=16;i++) vertical |= x==Math.round(i*width/16f)-1;
                for(int i=1;i<=20;i++) horizontal |= y==Math.round(i*95/20f)-1;
                int expected=vertical && horizontal ? crossing : vertical || horizontal ? line : interior;
                // Horizontal/vertical rasterization can differ by one 8-bit rounding step.
                assertTrue("Uneven or misplaced gap at "+x+","+y,
                        Math.abs(Color.red(expected)-Color.red(b.getPixel(x,y)))<=1);
            }
            b.recycle();
        }
    }
    @Test public void gridFollowsNearestScaledCheckerboardEdges() {
        Bitmap source=Bitmap.createBitmap(16,20,Bitmap.Config.ARGB_8888);
        for(int y=0;y<20;y++) for(int x=0;x<16;x++) source.setPixel(x,y,(x+y)%2==0?Color.RED:Color.BLUE);
        for(int width:new int[]{56,77,96}) {
            Bitmap base=Bitmap.createBitmap(width,95,Bitmap.Config.ARGB_8888);
            Paint sampling=new Paint(); sampling.setFilterBitmap(false);
            new Canvas(base).drawBitmap(source,null,new RectF(0,0,width,95),sampling);
            Bitmap mask=Bitmap.createBitmap(width,95,Bitmap.Config.ARGB_8888); mask.eraseColor(Color.WHITE);
            new LcdEffect().draw(new Canvas(mask),new RectF(0,0,width,95),16,20,0,2,70);
            int plain=mask.getPixel(1,1);
            for(int x=1;x<width;x++) if(base.getPixel(x,1)!=base.getPixel(x-1,1))
                assertNotEquals("Grid missed source edge at "+width+":"+x+" colors="+Integer.toHexString(base.getPixel(x-1,1))+","+Integer.toHexString(base.getPixel(x,1)),plain,mask.getPixel(x-1,1));
            for(int y=1;y<95;y++) if(base.getPixel(1,y)!=base.getPixel(1,y-1))
                assertNotEquals("Grid missed source row at "+y,plain,mask.getPixel(1,y-1));
            mask.recycle(); base.recycle();
        }
        source.recycle();
    }
    @Test public void cachedRendererChangesModesAndReturnsToOriginal() {
        LcdEffect effect=new LcdEffect();
        Bitmap b=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(b); RectF rect=new RectF(0,0,64,64);
        b.eraseColor(Color.WHITE); effect.draw(c,rect,8,8,0,2,70);
        b.eraseColor(Color.WHITE); effect.draw(c,rect,8,8,0,0,70);
        assertEquals(Color.WHITE,b.getPixel(10,10));
        effect.draw(c,rect,8,8,0,1,70); assertNotEquals(Color.WHITE,b.getPixel(10,10)); b.recycle();
    }
    @Test public void legacyProfilesDefaultOffAndPreviewUndoDoesNotNeedRestart() throws Exception {
        File dir=Files.createTempDirectory(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir().toPath(),"lcd-profile-").toFile();
        File config=new File(dir,Config.MIDLET_CONFIG_FILE);
        try {
            Files.write(config.toPath(),"{\"Version\":4}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            ProfileModel p=ProfilesManager.loadConfig(dir); assertNotNull(p);
            assertEquals(0,p.screenEffect); assertEquals(70,p.screenEffectIntensity);
            assertFalse(p.screenshotWithEffect);
            ProfilesManager.beginPreview(p); p.screenEffect=2; p.screenEffectIntensity=45;
            p.screenshotWithEffect=true;
            assertFalse(ProfilesManager.previewNeedsRestart(p));
            ProfilesManager.undoPreview(p); assertEquals(0,p.screenEffect);
            assertFalse(p.screenshotWithEffect);
            ProfilesManager.beginPreview(p); p.screenEffect=1; p.screenEffectIntensity=35;
            p.screenshotWithEffect=true;
            assertTrue(ProfilesManager.keepPreview(p));
            ProfileModel loaded=ProfilesManager.loadConfig(dir);
            assertEquals(1,loaded.screenEffect); assertEquals(35,loaded.screenEffectIntensity);
            assertTrue(loaded.screenshotWithEffect);
            String json=new String(Files.readAllBytes(config.toPath()),java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(json.contains("ScreenEffectIntensity"));
        } finally { Files.deleteIfExists(config.toPath()); Files.deleteIfExists(dir.toPath()); }
    }
}
