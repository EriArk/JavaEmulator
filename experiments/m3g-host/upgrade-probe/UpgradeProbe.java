/* SPDX-License-Identifier: Apache-2.0 */
import javax.microedition.midlet.MIDlet;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.rms.RecordStore;

/** Manual cross-APK RMS fixture, not a bundled game. */
public final class UpgradeProbe extends MIDlet {
    private int loaded;
    private int saved;
    private String error;

    public UpgradeProbe() {
        RecordStore store = null;
        try {
            store = RecordStore.openRecordStore("UpgradeCounter", true);
            if (store.getNumRecords() == 0) store.addRecord(new byte[] {0}, 0, 1);
            loaded = saved = store.getRecord(1)[0] & 255;
        } catch (Exception e) { error = e.toString(); }
        finally { close(store); }
    }

    public void startApp() { Display.getDisplay(this).setCurrent(new Screen()); }
    public void pauseApp() { }
    public void destroyApp(boolean unconditional) { }

    private void close(RecordStore store) {
        if (store != null) {
            try { store.closeRecordStore(); }
            catch (Exception e) { error = e.toString(); }
        }
    }

    private final class Screen extends Canvas {
        protected void paint(Graphics g) {
            g.setColor(0x101218); g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(0xffc04d);
            g.drawString("RMS upgrade probe", 8, 18, Graphics.TOP | Graphics.LEFT);
            g.setColor(0xffffff);
            g.drawString("Loaded: " + loaded, 8, 64, Graphics.TOP | Graphics.LEFT);
            g.drawString("Saved: " + saved, 8, 96, Graphics.TOP | Graphics.LEFT);
            g.drawString("5: increment + save", 8, 160, Graphics.TOP | Graphics.LEFT);
            g.drawString("0: exit", 8, 192, Graphics.TOP | Graphics.LEFT);
            if (error != null) g.drawString(error, 8, 224, Graphics.TOP | Graphics.LEFT);
        }
        protected void keyPressed(int key) {
            if (key == KEY_NUM0) { notifyDestroyed(); return; }
            if (key != KEY_NUM5) return;
            RecordStore store = null;
            try {
                store = RecordStore.openRecordStore("UpgradeCounter", false);
                int next = (saved + 1) & 255;
                store.setRecord(1, new byte[] {(byte) next}, 0, 1);
                saved = next;
            } catch (Exception e) { error = e.toString(); }
            finally { close(store); }
            repaint();
        }
    }
}
