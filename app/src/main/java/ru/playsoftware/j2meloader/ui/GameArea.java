/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.util.AtomicFile;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import java.io.*;
import java.util.Arrays;

/** A calibrated rectangle in full-window coordinates, shared across engine processes. */
public final class GameArea {
    private static AtomicFile file(Context c, boolean portrait) {
        return new AtomicFile(new File(c.getFilesDir(), portrait ? "game-area-portrait" : "game-area-landscape"));
    }

    static int[] read(Context c, boolean portrait) {
        try (DataInputStream in = new DataInputStream(file(c, portrait).openRead())) {
            int[] v = new int[6];
            for (int i = 0; i < v.length; i++) v[i] = in.readInt();
            if (v[0] > 0 && v[1] > 0 && v[2] >= 0 && v[3] >= 0 &&
                    v[4] > v[2] && v[4] <= v[0] && v[5] > v[3] && v[5] <= v[1]) return v;
        } catch (FileNotFoundException absent) {
            return null;
        } catch (IOException e) {
            Log.w("GameArea", "Could not read calibration", e);
        }
        return null;
    }

    static void save(Context c, boolean portrait, int width, int height, Rect r) throws IOException {
        AtomicFile file = file(c, portrait);
        FileOutputStream stream = file.startWrite();
        try {
            DataOutputStream out = new DataOutputStream(stream);
            for (int n : new int[]{width, height, r.left, r.top, r.right, r.bottom}) out.writeInt(n);
            file.finishWrite(stream);
        } catch (IOException e) {
            file.failWrite(stream);
            throw e;
        }
    }

    static Rect bounds(int[] v, int width, int height) {
        if (v == null || width == 0 || height == 0) return new Rect(0, 0, width, height);
        double sx = width / (double)v[0], sy = height / (double)v[1];
        int x = Math.min(width - 1, (int)Math.round(v[2] * sx));
        int y = Math.min(height - 1, (int)Math.round(v[3] * sy));
        return new Rect(x, y, Math.max(x + 1, (int)Math.round(v[4] * sx)),
                Math.max(y + 1, (int)Math.round(v[5] * sy)));
    }

    public static View wrap(View game) {
        FrameLayout frame = new FrameLayout(game.getContext()) {
            int[] portrait, landscape;
            @Override public void onWindowFocusChanged(boolean focused) {
                super.onWindowFocusChanged(focused);
                if (focused) reload();
            }
            private void reload() {
                int[] p = read(getContext(), true), l = read(getContext(), false);
                if (!Arrays.equals(p, portrait) || !Arrays.equals(l, landscape)) {
                    portrait = p;
                    landscape = l;
                    requestLayout();
                }
            }
            @Override protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                reload();
            }
            @Override protected void onMeasure(int w, int h) {
                int width = MeasureSpec.getSize(w), height = MeasureSpec.getSize(h);
                boolean vertical = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
                Rect r = bounds(vertical ? portrait : landscape, width, height);
                setPadding(r.left, r.top, width - r.right, height - r.bottom);
                super.onMeasure(w, h);
            }
        };
        frame.setBackgroundColor(Color.BLACK);
        frame.addView(game, new FrameLayout.LayoutParams(-1, -1));
        return frame;
    }
}
