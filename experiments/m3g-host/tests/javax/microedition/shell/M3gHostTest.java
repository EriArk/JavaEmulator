package javax.microedition.shell;

import android.content.Context;
import android.graphics.Bitmap;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.m3g.Background;
import javax.microedition.m3g.Graphics3D;
import javax.microedition.m3g.Image2D;
import javax.microedition.m3g.Loader;
import javax.microedition.m3g.Object3D;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Same tests against real AbyssME LCDUI and resources in both research APKs. */
@RunWith(AndroidJUnit4.class)
public class M3gHostTest {
    private Graphics3D renderer;
    @Before public void setup() { Graphics3D.initGraphics3D(); renderer = Graphics3D.getInstance(); }
    @After public void release() { renderer.releaseTarget(); }

    @Test public void preserves2dBeforeAndAfter3d() {
        Image image = Image.createImage(80, 100);
        Graphics graphics = image.getGraphics();
        graphics.setColor(0x123456); graphics.fillRect(0, 0, 80, 100);
        graphics.translate(10, 20); graphics.setClip(5, 5, 30, 40);
        renderer.bindTarget(graphics);
        assertSame(graphics, renderer.getTarget());
        Background background = new Background(); background.setColor(0xffcd2345);
        renderer.clear(background); renderer.releaseTarget();
        assertEquals(0xff123456, image.getBitmap().getPixel(5, 5));
        assertEquals(0xffcd2345, image.getBitmap().getPixel(20, 30));
        assertEquals(0xff123456, image.getBitmap().getPixel(46, 30));
        graphics.setColor(0xffffff); graphics.fillRect(6, 6, 4, 4);
        assertEquals(0xffffffff, image.getBitmap().getPixel(17, 27));
        renderer.bindTarget(graphics);
        background.setColorClearEnable(false); renderer.clear(background); renderer.releaseTarget();
        assertEquals(0xffffffff, image.getBitmap().getPixel(17, 27));
        assertEquals(10, graphics.getTranslateX()); assertEquals(5, graphics.getClipX());
        assertNull(renderer.getTarget());
    }

    @Test public void switchesTargetsAndResizesWithoutOldPixels() {
        for (int n = 0; n < 20; n++) {
            Image image = Image.createImage(40 + n, 70 - n);
            Graphics graphics = image.getGraphics();
            graphics.setColor(0x112200 + n); graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            renderer.bindTarget(graphics);
            Background background = new Background(); background.setColorClearEnable(false);
            renderer.clear(background); renderer.releaseTarget();
            assertEquals(0xff112200 + n, image.getBitmap().getPixel(0, 0));
            assertEquals(0xff112200 + n, image.getBitmap().getPixel(image.getWidth() - 1, image.getHeight() - 1));
        }
    }

    @Test public void resetDropsPreviousSessionState() {
        renderer.bindTarget(Image.createImage(30, 30).getGraphics()); renderer.releaseTarget();
        Graphics3D.initGraphics3D();
        renderer = Graphics3D.getInstance();
        assertNull(renderer.getTarget()); assertNull(renderer.getCamera(null));
        assertEquals(0, renderer.getLightCount());
    }

    @Test public void imageTargetDoesNotWritePreviousGraphics() {
        Image image = Image.createImage(32, 32);
        Graphics graphics = image.getGraphics();
        graphics.setColor(0x123456); graphics.fillRect(0, 0, 32, 32);
        renderer.bindTarget(graphics); renderer.releaseTarget();
        Image2D target = new Image2D(Image2D.RGBA, 16, 16);
        renderer.bindTarget(target);
        Background background = new Background(); background.setColor(0xffffffff);
        renderer.clear(background); renderer.releaseTarget();
        assertEquals(0xff123456, image.getBitmap().getPixel(0, 0));
    }

    @Test public void loaderUsesActiveMidletArchive() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File root = new File(context.getCacheDir(), "m3g-resource-test/converted/sample");
        assertTrue(root.isDirectory() || root.mkdirs());
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(new File(root, "res.jar")))) {
            zip.putNextEntry(new ZipEntry("nested/texture.png"));
            Bitmap bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(0xff24a35d);
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip));
            zip.closeEntry(); bitmap.recycle();
        }
        new AppClassLoader("", context.getCodeCacheDir().getPath(), getClass().getClassLoader(), root);
        Object3D[] loaded = Loader.load("/nested/texture.png");
        assertNotNull(loaded); assertEquals(1, loaded.length); assertTrue(loaded[0] instanceof Image2D);
        assertEquals(8, ((Image2D) loaded[0]).getWidth());
    }

    @Test public void graphicsBindingDoesNotOverwriteAnImageTarget() {
        Image2D texture = new Image2D(Image2D.RGB, 16, 16);
        Background background = new Background(); background.setColor(0xff24a35d);
        renderer.bindTarget(texture); renderer.clear(background); renderer.releaseTarget();
        Image screen = Image.createImage(16, 16);
        Graphics graphics = screen.getGraphics();
        graphics.setColor(0xcd2345); graphics.fillRect(0, 0, 16, 16);
        renderer.bindTarget(graphics); renderer.releaseTarget();
        background.setColor(0xff000000);
        background.setImage(texture); background.setCrop(0, 0, 16, 16);
        renderer.bindTarget(graphics); renderer.clear(background); renderer.releaseTarget();
        assertEquals(0xff24a35d, screen.getBitmap().getPixel(8, 8));
    }
}
