/* SPDX-License-Identifier: GPL-3.0-only */
package javax.microedition.lcdui;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.util.function.Consumer;
import javax.microedition.lcdui.event.RunnableEvent;
import javax.microedition.lcdui.graphics.ScreenRotation;
import javax.microedition.shell.MicroActivity;

/** Reports the system IME's overlap with the guest display, in MIDP pixels. */
public final class KeyboardViewport {
    private static final class State {
        final boolean visible;
        final Rect bounds;
        State(boolean visible, Rect bounds) {
            this.visible = visible;
            this.bounds = bounds;
        }
    }

    private static final State HIDDEN = new State(false, new Rect());
    private final MicroActivity activity;
    private final View root;
    private final ViewTreeObserver.OnGlobalLayoutListener layoutListener = this::measure;
    private final int[] location = new int[2];
    private final ThreadLocal<State> notification = new ThreadLocal<>();
    private volatile State current = HIDDEN;
    private volatile Consumer<Boolean> listener;
    private boolean closed;
    private int savedAdjustment = -1;

    public KeyboardViewport(MicroActivity activity) {
        this.activity = activity;
        root = activity.binding.getRoot();
        root.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            // Sample after Android has applied the new insets and laid out the surface.
            view.post(this::measure);
            return view.onApplyWindowInsets(insets);
        });
    }

    public boolean isVisible() { return state().visible; }
    public Rect getBounds() { return new Rect(state().bounds); }
    private State state() {
        State event = notification.get();
        return event == null ? current : event;
    }

    void canvasMoved() {
        if (current.visible) root.post(this::measure);
    }

    public void setListener(Consumer<Boolean> next) { listener = next; }

    private void measure() {
        if (closed) return;
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(root);
        if (insets == null) return;
        boolean visible = insets.isVisible(WindowInsetsCompat.Type.ime());
        if (!visible && !current.visible) return;
        State next = HIDDEN;
        if (visible) {
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
            if (current.visible && current.bounds.equals(covered)) return;
            next = new State(true, covered);
        }
        boolean changed = current.visible != visible;
        current = next;
        Consumer<Boolean> recipient = listener;
        if (changed && recipient != null) {
            State transition = next;
            Display.getEventQueue().postEventQueued(RunnableEvent.getInstance(() -> {
                if (listener != recipient) return;
                notification.set(transition);
                try { recipient.accept(transition.visible); }
                finally { notification.remove(); }
            }));
        }
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
        });
    }

    public void close() {
        closed = true;
        listener = null;
        current = HIDDEN;
        root.setOnApplyWindowInsetsListener(null);
        root.getViewTreeObserver().removeOnGlobalLayoutListener(layoutListener);
    }
}
