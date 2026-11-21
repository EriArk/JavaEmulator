/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.handheld;

import android.app.Activity;
import android.app.Presentation;
import android.content.Context;
import android.graphics.PixelFormat;
import android.hardware.display.*;
import android.media.Image;
import android.media.ImageReader;
import android.view.*;
import android.webkit.WebView;
import java.io.IOException;
import java.nio.*;

/** Presents an in-app web runtime through the shared handheld graphics and controls. */
public final class HandheldWebDisplay implements AutoCloseable {
    private final VirtualDisplay display;
    private final Presentation presentation;
    private final ImageReader reader;
    private final int width, height;
    public final WebView web;
    public HandheldWebDisplay(Activity host, String title, int width, int height) throws IOException {
        this.width = width;
        this.height = height;
        reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,3);
        DisplayManager manager=(DisplayManager)host.getSystemService(Context.DISPLAY_SERVICE);
        display=manager.createVirtualDisplay(title,width,height,160,reader.getSurface(),
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
        if (display == null) { reader.close(); throw new IOException("Cannot create game display"); }
        presentation=new Presentation(host,display.getDisplay());
        Window window=presentation.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.getDecorView().setSystemUiVisibility(5894);
        web=new WebView(presentation.getContext());
    }
    public void show() {
        presentation.setContentView(web);
        presentation.show();
    }
    @Override public void close() {
        presentation.dismiss();
        if (web.getParent() instanceof ViewGroup) ((ViewGroup) web.getParent()).removeView(web);
        web.destroy();
        display.release();
        reader.close();
    }
    public boolean frame(int[] pixels) {
        if (reader==null) return false;
        try (Image img=reader.acquireLatestImage()) {
            if (img==null) return false;
            Image.Plane plane=img.getPlanes()[0];
            IntBuffer src=plane.getBuffer().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
            for (int y=0;y<height;y++) {
                src.position(y*plane.getRowStride()/4);
                src.get(pixels,y*width,width);
            }
            for (int i=0;i<pixels.length;i++) {
                int c=pixels[i]; pixels[i]=0xff000000 | ((c&255)<<16) | (c&0xff00) | ((c>>16)&255);
            }
            return true;
        }
    }
}
