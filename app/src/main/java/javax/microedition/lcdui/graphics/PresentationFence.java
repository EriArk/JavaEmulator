/* Copyright 2026. GPL-3.0-only. */
package javax.microedition.lcdui.graphics;

import android.opengl.EGL14;
import android.opengl.EGL15;
import android.opengl.EGLDisplay;
import android.opengl.EGLSync;
import android.opengl.GLES20;

/** One nonblocking completion probe; not a GPU timer. Owned by the rendering thread. */
public final class PresentationFence implements AutoCloseable {
    private static final long[] ATTRIBUTES = {EGL14.EGL_NONE};
    private final EGLDisplay display;
    private EGLSync fence;
    private long submitted;
    private boolean available;

    public PresentationFence() {
        display = EGL14.eglGetCurrentDisplay();
        String version = EGL14.EGL_NO_DISPLAY.equals(display) ? null
                : EGL14.eglQueryString(display, EGL14.EGL_VERSION);
        // No JNI extension shim or GLES3 context requirement; older EGL keeps CPU-only protection.
        available = version != null && version.startsWith("1.5");
    }

    public boolean available() { return available; }

    public boolean behind(long now, long budget) {
        if (!available || fence == null || now - submitted < budget) return false;
        try {
            int status = EGL15.eglClientWaitSync(display, fence, 0, 0);
            if (status == EGL15.EGL_TIMEOUT_EXPIRED) return true;
            if (status != EGL15.EGL_CONDITION_SATISFIED) { disable(); return false; }
            close();
        } catch (RuntimeException e) { disable(); }
        return false;
    }

    public void submitted(long now) {
        if (!available) return;
        close();
        try {
            fence = EGL15.eglCreateSync(display, EGL15.EGL_SYNC_FENCE, ATTRIBUTES, 0);
            if (fence == null || EGL15.EGL_NO_SYNC.equals(fence)) { fence = null; disable(); return; }
            submitted = now;
            GLES20.glFlush();
        } catch (RuntimeException e) { disable(); }
    }

    private void disable() {
        close(); available = false;
        EGL14.eglGetError();
        android.util.Log.w("PresentationFence", "GPU completion probe unavailable; using CPU submission pressure");
    }

    @Override public void close() {
        if (fence == null) return;
        try {
            if (!EGL15.eglDestroySync(display, fence)) EGL14.eglGetError();
        } catch (RuntimeException e) { EGL14.eglGetError(); }
        finally { fence = null; }
    }
}
