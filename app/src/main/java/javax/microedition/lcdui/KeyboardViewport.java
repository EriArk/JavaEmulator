/* SPDX-License-Identifier: GPL-3.0-only */
package javax.microedition.lcdui;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.util.function.BiConsumer;
import javax.microedition.lcdui.event.RunnableEvent;
import javax.microedition.lcdui.graphics.ScreenRotation;
import javax.microedition.shell.MicroActivity;

/** Reports the system IME's overlap with the guest display, in MIDP pixels. */
public final class KeyboardViewport {
    private static final class State {
        final int category;
        final Rect bounds;
        State(int category, Rect bounds) {
            this.category = category;
            this.bounds = bounds;
        }
    }

    private static final State HIDDEN = new State(0, new Rect());
    private final MicroActivity activity;
    private final View root;
    private final ViewTreeObserver.OnGlobalLayoutListener layoutListener = this::measure;
    private final int[] location = new int[2];
    private final ThreadLocal<State> notification = new ThreadLocal<>();
    private volatile State current = HIDDEN;
    private volatile BiConsumer<Boolean, Integer> listener;
    private final GuestKeypad keypad;
    private volatile boolean systemVisible;
    private boolean closed;
    private int savedAdjustment = -1;

    public KeyboardViewport(MicroActivity activity) {
        this.activity = activity;
        keypad = new GuestKeypad(activity, this);
        root = activity.binding.getRoot();
        root.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            // Sample after Android has applied the new insets and laid out the surface.
            view.post(this::measure);
            return view.onApplyWindowInsets(insets);
        });
    }

    public boolean isVisible() {
        State event = notification.get();
        return event != null ? event.category != 0 : current.category == 2 || keypad.requested();
    }

    public Rect getBounds() {
        State event = notification.get();
        if (event != null) return new Rect(event.bounds);
        State measured = current;
        return measured.category == 2 ? new Rect(measured.bounds) : keypad.requestedBounds();
    }

    void canvasMoved() {
        if (current.category != 0 || keypad.requested()) root.post(this::measure);
    }

    public GuestKeypad keypad() { return keypad; }
    boolean systemVisible() { return systemVisible; }
    boolean suppressesResize() { return savedAdjustment >= 0; }

    public void setListener(BiConsumer<Boolean, Integer> next) { listener = next; }

    private void measure() {
        if (closed) return;
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(root);
        if (insets == null) return;
        boolean visible = insets.isVisible(WindowInsetsCompat.Type.ime());
        systemVisible = visible;
        if (!visible) {
            keypad.refresh();
            return;
        }
        keypad.dismiss();
        View decor = root.getRootView();
        int bottom = decor.getHeight();
        int top = bottom - insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
        Rect covered = new Rect(0, top, decor.getWidth(), bottom);
        Displayable screen = activity.getCurrent();
        if (screen instanceof Canvas) {
            ((Canvas) screen).mapWindowOverlap(covered, location);
        } else if (screen != null) {
            View container = activity.binding.displayableContainer;
            container.getLocationInWindow(location);
            toGuest(covered, location[0], location[1], container.getWidth(), container.getHeight(),
                    screen.getWidth(), screen.getHeight(), 0);
        } else {
            covered.setEmpty();
        }
        if (current.category == 2 && current.bounds.equals(covered)) return;
        publish(new State(2, covered));
    }

    void customHidden() {
        if (!closed && !systemVisible && current != HIDDEN) publish(HIDDEN);
    }

    public boolean canOpenCustom() { return !systemVisible; }

    void customBounds(Rect bounds) {
        if (closed || systemVisible) return;
        publish(bounds.isEmpty() ? HIDDEN : new State(1, bounds));
    }

    private void publish(State next) {
        State previous = current;
        current = next;
        if (previous.category == next.category) return;
        if (previous.category != 0) notifyListener(HIDDEN, previous.category);
        if (next.category != 0) notifyListener(next, next.category);
    }

    private void notifyListener(State transition, int category) {
        BiConsumer<Boolean, Integer> recipient = listener;
        if (recipient == null) return;
        Display.getEventQueue().postEventQueued(RunnableEvent.getInstance(() -> {
            if (listener != recipient) return;
            notification.set(transition);
            try { recipient.accept(transition.category != 0, category); }
            finally { notification.remove(); }
        }));
    }

    static void toGuest(Rect overlap, int left, int top, int width, int height,
                        int guestWidth, int guestHeight, int rotation) {
        if (overlap.isEmpty() || width <= 0 || height <= 0 || guestWidth <= 0 || guestHeight <= 0
                || !overlap.intersect(left, top, left + width, top + height)) {
            overlap.setEmpty();
            return;
        }
        float x1 = (overlap.left - left) / (float) width, x2 = (overlap.right - left) / (float) width;
        float y1 = (overlap.top - top) / (float) height, y2 = (overlap.bottom - top) / (float) height;
        float u1 = ScreenRotation.sourceX(x1, y1, rotation), u2 = ScreenRotation.sourceX(x2, y2, rotation);
        float v1 = ScreenRotation.sourceY(x1, y1, rotation), v2 = ScreenRotation.sourceY(x2, y2, rotation);
        overlap.set((int) Math.floor(Math.min(u1, u2) * guestWidth),
                (int) Math.floor(Math.min(v1, v2) * guestHeight),
                (int) Math.ceil(Math.max(u1, u2) * guestWidth),
                (int) Math.ceil(Math.max(v1, v2) * guestHeight));
    }

    public void suppressSizeChanged(boolean suppress) {
        ViewHandler.postEvent(() -> {
            if (closed || suppress == (savedAdjustment >= 0)) return;
            Window window = activity.getWindow();
            int mode = window.getAttributes().softInputMode;
            int mask = WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST;
            int adjustment = savedAdjustment;
            if (suppress) {
                savedAdjustment = mode & mask;
                // Android retains the last adjustment if an unspecified mode (zero) is reapplied.
                // A guest requesting size events should allow the surface to resize again.
                if (savedAdjustment == WindowManager.LayoutParams.SOFT_INPUT_ADJUST_UNSPECIFIED)
                    savedAdjustment = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
                adjustment = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
            } else {
                savedAdjustment = -1;
            }
            window.setSoftInputMode((mode & ~mask) | adjustment);
            keypad.refresh();
        });
    }

    public void close() {
        closed = true;
        listener = null;
        keypad.dismiss();
        current = HIDDEN;
        root.setOnApplyWindowInsetsListener(null);
        root.getViewTreeObserver().removeOnGlobalLayoutListener(layoutListener);
    }
}
