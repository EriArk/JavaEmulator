package javax.microedition.lcdui;

/** Only the surface/clip API consumed by the candidate renderer. */
public class Graphics {
    protected final Image image;
    protected int tx, ty, clipX, clipY, clipW, clipH;
    public Graphics(Image image) {
        this.image = image;
        clipW = image.getWidth(); clipH = image.getHeight();
    }
    public Image getCanvas() { return image; }
    public int getTranslateX() { return tx; }
    public int getTranslateY() { return ty; }
    public int getClipX() { return clipX - tx; }
    public int getClipY() { return clipY - ty; }
    public int getClipWidth() { return clipW; }
    public int getClipHeight() { return clipH; }
    public void translate(int x, int y) { tx += x; ty += y; }
    public void setClip(int x, int y, int w, int h) {
        clipX = x + tx; clipY = y + ty; clipW = w; clipH = h;
    }
}
