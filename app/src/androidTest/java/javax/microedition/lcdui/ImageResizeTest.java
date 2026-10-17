package javax.microedition.lcdui;

import org.junit.Test;
import static org.junit.Assert.*;

public class ImageResizeTest {
    @Test public void allCachedGraphicsFollowExpandedBitmap() {
        Image image = Image.createImage(8, 8);
        Graphics first = image.getGraphics(), second = image.getGraphics();
        first.setColor(0x123456); first.fillRect(0, 0, 8, 8);
        image.setSize(24, 32);
        assertEquals(0xFF123456, image.getBitmap().getPixel(2, 2));
        first.setColor(0x00FF00); first.fillRect(20, 28, 4, 4);
        second.setColor(0xFF0000); second.fillRect(16, 24, 4, 4);
        assertEquals(0xFF00FF00, image.getBitmap().getPixel(23, 31));
        assertEquals(0xFFFF0000, image.getBitmap().getPixel(19, 27));
    }

    @Test public void shrinkingAndRegrowingUpdatesClipsWithoutLosingContext() {
        Image image = Image.createImage(24, 32);
        Graphics g = image.getSingleGraphics();
        image.setSize(8, 8);
        assertEquals(8, g.getClipWidth()); assertEquals(8, g.getClipHeight());
        image.setSize(24, 32);
        assertEquals(24, g.getClipWidth()); assertEquals(32, g.getClipHeight());
        g.setColor(0x00FF00); g.fillRect(0, 0, 24, 32);
        assertEquals(0xFF00FF00, image.getBitmap().getPixel(23, 31));
    }

    @Test public void resizingWithoutAGraphicsObjectPreservesPixels() {
        Image image = Image.createImage(8, 8);
        image.getBitmap().setPixel(7, 7, 0xFF123456);
        image.setSize(24, 32);
        assertEquals(0xFF123456, image.getBitmap().getPixel(7, 7));
    }
}
