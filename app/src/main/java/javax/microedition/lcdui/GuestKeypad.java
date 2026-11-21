/* SPDX-License-Identifier: GPL-3.0-only */
package javax.microedition.lcdui;

import android.graphics.Rect;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import javax.microedition.lcdui.event.RunnableEvent;
import javax.microedition.shell.MicroActivity;

/** Per-MIDlet custom keypad. Guest requests can precede attachment of the Android canvas. */
public final class GuestKeypad {
    private final MicroActivity activity;
    private final KeyboardViewport viewport;
    private Displayable requested, mounted;
    private CustomItem target, mountedItem;
    private int type = 1, mode;
    private GuestKeypadView panel;
    private Rect bounds = new Rect();

    GuestKeypad(MicroActivity activity, KeyboardViewport viewport) {
        this.activity = activity;
        this.viewport = viewport;
    }

    public static void validateType(int value) {
        if (value != 1 && value != 3) throw new IllegalArgumentException("Unknown keypad type");
    }

    private static void validateMode(int value) {
        if (value < 0 || value > 4) throw new IllegalArgumentException("Unknown keypad mode");
    }

    public synchronized void launch(int type, int mode) {
        validateType(type);
        validateMode(mode);
        Display display = Display.getDisplay(null);
        Displayable screen = display == null ? null : display.getCurrent();
        CustomItem item = screen instanceof Form ? ((Form) screen).focusedCustomItem : null;
        if (!(screen instanceof Canvas) && (item == null || !item.hasOwnerForm() || item.getOwnerForm() != screen))
            throw new IllegalStateException("No canvas or focused custom item");
        if (viewport.systemVisible()) throw new IllegalStateException("System keyboard is open");
        requested = screen;
        target = item;
        this.type = type;
        this.mode = mode;
        ViewHandler.postEvent(this::refresh);
    }

    public synchronized void setType(int value) {
        validateType(value);
        if (requested != null) { type = value; ViewHandler.postEvent(this::refresh); }
    }
    public synchronized void setMode(int value) {
        validateMode(value);
        if (requested != null) { mode = value; ViewHandler.postEvent(this::refresh); }
    }
    public synchronized int getType() { return type; }
    public synchronized int getMode() { return mode; }
    public synchronized boolean requested() { return requested != null; }

    private int panelHeight(int width, int height) {
        int rows = type == 1 ? 4 : 3;
        return Math.max(1, Math.min(height / 2, Math.round(Math.min(width / 5f, height / 9f) * rows)));
    }

    synchronized Rect requestedBounds() {
        if (requested == null) return new Rect();
        if (requested instanceof Canvas) {
            Canvas canvas = (Canvas) requested;
            if (canvas.width > 0 && canvas.height > 0)
                return new Rect(0, canvas.height - panelHeight(canvas.width, canvas.height), canvas.width, canvas.height);
        }
        return new Rect(bounds);
    }

    public synchronized void dismiss() {
        if (requested == null && mounted == null) return;
        requested = null;
        target = null;
        if (Looper.myLooper() == Looper.getMainLooper()) unmount();
        else ViewHandler.postEvent(this::unmount);
    }

    public synchronized void screenChanged(Displayable screen) {
        if (requested != screen && !(screen instanceof Alert)) dismiss();
    }

    synchronized void refresh() {
        if (mounted != requested || mountedItem != target) unmount();
        if (requested == null) { viewport.customHidden(); return; }
        if (activity.getCurrent() != requested || viewport.systemVisible()) return;
        if (target != null && ((Form) requested).focusedCustomItem != target) { dismiss(); return; }
        Canvas canvas = requested instanceof Canvas ? (Canvas) requested : null;
        FrameLayout container = activity.binding.displayableContainer;
        int width = canvas != null ? canvas.width : container.getWidth();
        int height = canvas != null ? canvas.height : container.getHeight();
        if (width < 1 || height < 1) return;
        if (panel == null) {
            mounted = requested;
            mountedItem = target;
            panel = new GuestKeypadView(activity, this::sendKey, this::dismiss);
            if (canvas != null) canvas.attachGuestView(panel);
        }
        if (canvas == null && panel.getParent() == null) container.addView(panel);
        panel.configure(type, mode);
        int panelHeight = panelHeight(width, height);
        if (panel.getLayoutParams() == null || panel.getLayoutParams().width != width
                || panel.getLayoutParams().height != panelHeight) {
            panel.setLayoutParams(new FrameLayout.LayoutParams(width, panelHeight, Gravity.LEFT | Gravity.BOTTOM));
        }
        int guestW = canvas != null ? width : requested.getWidth();
        int guestH = canvas != null ? height : ((Form) requested).fullHeight();
        if (guestW <= 0) guestW = width;
        if (guestH <= 0) guestH = height;
        int overlap = Math.round(panelHeight * (float) guestH / height);
        Rect next = new Rect(0, guestH - overlap, guestW, guestH);
        setInset(viewport.suppressesResize() ? 0 : overlap);
        if (!bounds.equals(next)) { bounds = next; viewport.customBounds(next); }
    }

    private void setInset(int size) {
        if (mounted instanceof Canvas) ((Canvas) mounted).setKeypadInset(size);
        else if (mounted instanceof Form) ((Form) mounted).setKeypadInset(size);
    }

    private synchronized void unmount() {
        if (panel == null) return;
        panel.releaseKeys();
        if (mounted instanceof Canvas) ((Canvas) mounted).detachGuestView(panel);
        else if (panel.getParent() instanceof ViewGroup) ((ViewGroup) panel.getParent()).removeView(panel);
        setInset(0);
        panel = null;
        mounted = null;
        mountedItem = null;
        bounds = new Rect();
        viewport.customBounds(bounds);
    }

    private void sendKey(int code, int action) {
        if (mounted instanceof Canvas) {
            Canvas canvas = (Canvas) mounted;
            if (action == 0) canvas.postKeyPressed(code);
            else if (action == 1) canvas.postKeyReleased(code);
            else canvas.postKeyRepeated(code);
        } else if (mountedItem != null) {
            CustomItem item = mountedItem;
            Display.getEventQueue().postEventQueued(RunnableEvent.getInstance(() -> {
                if (action == 0) item.keyPressed(code);
                else if (action == 1) item.keyReleased(code);
                else item.keyRepeated(code);
            }));
        }
    }
}
