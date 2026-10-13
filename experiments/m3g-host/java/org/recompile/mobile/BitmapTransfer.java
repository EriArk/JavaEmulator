/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.recompile.mobile;

import android.graphics.Bitmap;

public final class BitmapTransfer {
    public static boolean enabled=true;
    public static long nativeReads, fallbackReads;
    private static final boolean available;
    static {
        boolean loaded;
        try { System.loadLibrary("abyssm3graster"); loaded=true; }
        catch(UnsatisfiedLinkError missing) { loaded=false; }
        available=loaded;
    }
    private BitmapTransfer() { }

    public static void read(Bitmap bitmap,int[] pixels,int stride,int x,int y,int width,int height) {
        if(enabled && available && bitmap.getConfig()==Bitmap.Config.ARGB_8888
                && bitmap.getColorSpace()!=null && bitmap.getColorSpace().isSrgb()
                && readOpaque(bitmap,pixels,stride,x,y,width,height)) {
            nativeReads++;
        } else {
            bitmap.getPixels(pixels,y*stride+x,stride,x,y,width,height);
            fallbackReads++;
        }
    }
    private static native boolean readOpaque(Bitmap bitmap,int[] pixels,int stride,int x,int y,int width,int height);
}
