/* SPDX-License-Identifier: GPL-3.0-only */
import com.skt.m.MathFP;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.midlet.MIDlet;

/** Self-contained SKT input fixture. SKVM supplies MathFP at runtime. */
public final class Slider extends MIDlet {
    protected void startApp() {
        Display.getDisplay(this).setCurrent(new Screen());
    }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}

    private static final class Screen extends Canvas {
        private int x = 100;

        protected void paint(Graphics graphics) {
            graphics.setColor(0x223044);
            graphics.fillRect(0, 0, getWidth(), getHeight());
            graphics.setColor(0xffffff);
            graphics.drawString("SKVM - press left or right", 8, 12, Graphics.TOP | Graphics.LEFT);
            graphics.setColor(0xff8a3d);
            graphics.fillRect(x, getHeight() - 40, 40, 14);
        }
        protected void keyPressed(int code) {
            int action = getGameAction(code);
            if (action == RIGHT || code == '6') x = (int) MathFP.min(x + 36, getWidth() - 40);
            if (action == LEFT || code == '4') x = (int) MathFP.max(x - 36, 0);
            repaint();
        }
    }
}
