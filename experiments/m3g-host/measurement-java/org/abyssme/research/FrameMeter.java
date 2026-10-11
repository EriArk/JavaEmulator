package org.abyssme.research;

import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.json.JSONObject;
import javax.microedition.util.ContextHolder;

/** Opt-in, shared host observation; does not time display refresh as new game frames. */
public final class FrameMeter {
    private static final long[] intervals = new long[1024];
    private static final long[] paints = new long[1024];
    private static long previous, windowStart, wallStart;
    private static int frames, paintCount, intervalCount, overflow;

    public static synchronized void record(long paintNanos) {
        long now = System.nanoTime();
        if (windowStart == 0) {
            windowStart = now;
            wallStart = System.currentTimeMillis();
        }
        frames++;
        if (previous != 0) {
            if (intervalCount < intervals.length) intervals[intervalCount++] = now - previous;
            else overflow++;
        }
        previous = now;
        if (paintNanos >= 0) {
            if (paintCount < paints.length) paints[paintCount++] = paintNanos;
            else overflow++;
        }
        if (now - windowStart < 2_000_000_000L) return;
        try {
            JSONObject out = new JSONObject();
            out.put("pid", android.os.Process.myPid());
            out.put("startMs", wallStart);
            out.put("endMs", System.currentTimeMillis());
            out.put("spanNs", now - windowStart);
            out.put("updates", frames);
            out.put("paints", paintCount);
            out.put("overflow", overflow);
            out.put("intervalP50Ns", percentile(intervals, intervalCount, 0.5));
            out.put("intervalP95Ns", percentile(intervals, intervalCount, 0.95));
            out.put("intervalMaxNs", percentile(intervals, intervalCount, 1));
            out.put("paintP50Ns", percentile(paints, paintCount, 0.5));
            out.put("paintP95Ns", percentile(paints, paintCount, 0.95));
            File file = new File(ContextHolder.getAppContext().getCacheDir(), "m3g-cadence.jsonl");
            try (FileOutputStream stream = new FileOutputStream(file, file.length() < 524288)) {
                stream.write((out.toString() + "\n").getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            Log.e("M3gCadence", "Measurement failed", e);
        }
        windowStart = now;
        wallStart = System.currentTimeMillis();
        frames = paintCount = intervalCount = overflow = 0;
    }

    private static long percentile(long[] values, int count, double fraction) {
        if (count == 0) return -1;
        Arrays.sort(values, 0, count);
        return values[(int) Math.ceil(count * fraction) - 1];
    }
}
