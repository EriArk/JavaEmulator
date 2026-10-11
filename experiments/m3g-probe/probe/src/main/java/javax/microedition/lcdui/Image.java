package javax.microedition.lcdui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.IOException;
import java.io.InputStream;

/** Bitmap-backed image surface for standalone renderer tests only. */
public final class Image {
    private final Bitmap bitmap;
    public Image(Bitmap bitmap) { this.bitmap = bitmap; }
    public Bitmap getBitmap() { return bitmap; }
    public boolean isMutable() { return bitmap.isMutable(); }
    public int getWidth() { return bitmap.getWidth(); }
    public int getHeight() { return bitmap.getHeight(); }
    public void getRGB(int[] pixels, int offset, int stride, int x, int y, int w, int h) {
        bitmap.getPixels(pixels, offset, stride, x, y, w, h);
    }
    public static Image createImage(InputStream input) throws IOException {
        Bitmap bitmap = BitmapFactory.decodeStream(input);
        if (bitmap == null) throw new IOException("Unsupported probe image");
        return new Image(bitmap);
    }
}
