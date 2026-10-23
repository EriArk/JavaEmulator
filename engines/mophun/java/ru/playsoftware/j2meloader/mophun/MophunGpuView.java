/* Copyright 2026. GPL-3.0-only. */
package ru.playsoftware.j2meloader.mophun;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.opengl.GLSurfaceView;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import javax.microedition.lcdui.graphics.EnhancementPipeline;
import javax.microedition.lcdui.graphics.PresentationFence;
import ru.playsoftware.j2meloader.BuildConfig;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.runtime.InterpolationLoadGuard;
import wolphun.Screen;
import static android.opengl.GLES20.*;

/** Optional presentation only. One latest state, GL-owned pixels, no queued guest frames. */
@android.annotation.SuppressLint("ViewConstructor") // Programmatic only: requires owner callbacks.
final class MophunGpuView extends GLSurfaceView implements GLSurfaceView.Renderer {
    private final Runnable changed;
    private final Runnable failure;
    private final InterpolationLoadGuard load = new InterpolationLoadGuard();
    private final FloatBuffer vertices = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder()).asFloatBuffer();
    private final long[] stamp = new long[2];
    private volatile State state, presented;
    private volatile boolean active, failed, animating;
    private volatile long renderedSerial;
    private EnhancementPipeline pipeline;
    private PresentationFence fence;
    private Screen source;
    private Bitmap bitmap;
    private int[] pixels;
    private int surfaceWidth, surfaceHeight;
    private boolean suspended;

    MophunGpuView(Context context, Runnable changed, Runnable failure) {
        super(context);
        this.changed = changed; this.failure = failure;
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 0, 0, 0);
        setRenderer(this);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        setFocusable(false); setClickable(false);
    }

    static boolean enabled(ProfileModel profile) {
        return BuildConfig.DEBUG && (profile.fsrUpscaling || profile.frameInterpolation);
    }

    void active(boolean value) {
        if (active == value) return;
        active = value; presented = null;
        if (value) {
            if (suspended) onResume();
            suspended = false; requestRender();
        } else {
            queueEvent(this::release);
            onPause(); suspended = true;
        }
    }

    // Called on the UI vsync, including while a menu is open. Repeated source frames do not upload.
    boolean update(Screen screen, RectF area, int width, int height, int rotation,
                   ProfileModel profile, boolean paused) {
        if (!active || failed) return false;
        State next = new State(screen, area, width, height, rotation, profile.fsrUpscaling,
                profile.frameInterpolation && !paused, profile.fsrSharpness, profile.screenFilter,
                InterpolationLoadGuard.refreshPeriod(getDisplay() == null ? 60 : getDisplay().getRefreshRate()));
        State before = state;
        if (next.same(before)) next = before;
        state = next;
        if (next != before || renderedSerial != screen.presentationSerial() || animating) requestRender();
        return presented == next;
    }

    @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        // Old texture names belonged to the destroyed context. EGL sync lifetime is display-owned.
        if (fence != null) fence.close();
        fence = null; pipeline = null; source = null; load.reset();
        if (bitmap != null) bitmap.recycle();
        bitmap = null; pixels = null; presented = null; animating = false; renderedSerial = 0;
    }

    @Override public void onSurfaceChanged(GL10 gl, int width, int height) {
        surfaceWidth = width; surfaceHeight = height; presented = null;
    }

    @Override public void onDrawFrame(GL10 gl) {
        State current = state;
        if (!active || failed || current == null || surfaceWidth <= 0 || surfaceHeight <= 0) return;
        try {
            draw(current);
        } catch (RuntimeException error) {
            failed = true; presented = null;
            android.util.Log.w("MophunGpu", "Restoring Canvas presentation", error);
            release(); post(failure);
        }
    }

    private void draw(State current) {
        long started = System.nanoTime();
        boolean allow = true;
        if (current.motion) {
            if (fence == null) fence = new PresentationFence();
            allow = load.allow(started, fence.behind(started, current.budget));
        } else {
            load.reset(); if (fence != null) { fence.close(); fence = null; }
        }
        if (pipeline == null) pipeline = new EnhancementPipeline();
        if (source != current.screen) {
            source = current.screen; stamp[0] = 0; renderedSerial = 0;
            if (source.pw <= 0 || source.ph <= 0 || (long)source.pw*source.ph > 1_048_576L)
                throw new IllegalArgumentException("Enhancement texture budget exceeded");
            if (bitmap != null) bitmap.recycle();
            bitmap = Bitmap.createBitmap(source.pw, source.ph, Bitmap.Config.ARGB_8888);
            pixels = new int[source.pw * source.ph]; pipeline.resetHistory();
        }
        // The guest lock is held only for the snapshot, never during GPU upload or processing.
        if (source.copyPresented(pixels, stamp, stamp[0]))
            bitmap.setPixels(pixels, 0, source.pw, 0, 0, source.pw, source.ph);
        if (stamp[0] == 0) return;
        pipeline.upload(bitmap, stamp[0], source.pw, source.ph, current.motion, stamp[1]);
        if (!allow) pipeline.skipInterpolation();
        int texture = pipeline.renderForPresentation(current.width, current.height,
                current.fsr, current.sharpness, System.nanoTime());
        glBindFramebuffer(GL_FRAMEBUFFER, 0); glViewport(0, 0, surfaceWidth, surfaceHeight);
        glDisable(GL_SCISSOR_TEST); glClearColor(16f/255, 20f/255, 23f/255, 1); glClear(GL_COLOR_BUFFER_BIT);
        int left = Math.max(0, (int)Math.ceil(current.area.left));
        int top = Math.max(0, (int)Math.ceil(current.area.top));
        int right = Math.min(surfaceWidth, (int)Math.floor(current.area.right));
        int bottom = Math.min(surfaceHeight, (int)Math.floor(current.area.bottom));
        glEnable(GL_SCISSOR_TEST);
        glScissor(left, surfaceHeight-bottom, Math.max(0,right-left), Math.max(0,bottom-top));
        current.vertices(vertices, surfaceWidth, surfaceHeight);
        pipeline.presentTexture(texture, vertices, current.filter && !current.fsr);
        glDisable(GL_SCISSOR_TEST);
        if (current.motion) {
            fence.submitted(System.nanoTime());
            long finished = System.nanoTime(); load.submitted(finished, finished-started, current.budget);
        }
        animating = pipeline.animating();
        long submittedSerial = stamp[0];
        // GLSurfaceView swaps after onDrawFrame returns, before consuming this GL-thread event.
        // Do not uncover the SurfaceView while it still has no submitted buffer.
        queueEvent(() -> {
            if (!active || failed || state != current) return;
            renderedSerial = submittedSerial;
            if (presented != current) { presented = current; post(changed); }
        });
    }

    private void release() {
        if (fence != null) fence.close(); fence = null; load.reset();
        if (pipeline != null) pipeline.close(); pipeline = null;
        if (bitmap != null) bitmap.recycle(); bitmap = null; pixels = null; source = null;
        presented = null; renderedSerial = 0; animating = false;
    }

    static final class State {
        final Screen screen;
        final RectF area;
        final int width, height, rotation, sharpness;
        final boolean fsr, motion, filter;
        final long budget;
        State(Screen screen, RectF area, int width, int height, int rotation,
              boolean fsr, boolean motion, int sharpness, boolean filter, long budget) {
            this.screen=screen; this.area=new RectF(area); this.width=width; this.height=height;
            this.rotation=rotation; this.fsr=fsr; this.motion=motion; this.sharpness=sharpness;
            this.filter=filter; this.budget=budget;
        }
        boolean same(State other) {
            return other != null && screen==other.screen && area.equals(other.area) && width==other.width
                    && height==other.height && rotation==other.rotation && sharpness==other.sharpness
                    && fsr==other.fsr && motion==other.motion && filter==other.filter && budget==other.budget;
        }
        void vertices(FloatBuffer buffer, int viewWidth, int viewHeight) {
            double angle=Math.toRadians(rotation);
            float cos=(float)Math.cos(angle), sin=(float)Math.sin(angle);
            buffer.clear();
            for (int i=0;i<4;i++) {
                int u=i/2, v=i%2;
                float x=-width/2 + u*width, y=-height/2 + v*height;
                float px=area.centerX()+x*cos-y*sin, py=area.centerY()+x*sin+y*cos;
                buffer.put(px*2/viewWidth-1).put(1-py*2/viewHeight).put(u).put(v);
            }
            buffer.rewind();
        }
    }
}
