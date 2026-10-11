package io.github.eriark.m3gprobe;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.microedition.m3g.ProbeSuite;
import org.json.JSONArray;
import org.json.JSONObject;

public final class ProbeActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView waiting = new TextView(this); waiting.setText("Running M3G probe..."); setContentView(waiting);
        new Thread(() -> {
            try {
                ProbeSuite suite = new ProbeSuite();
                JSONObject report = suite.run();
                write("report.json", report.toString(2).getBytes(StandardCharsets.UTF_8));
                for (Map.Entry<String, Bitmap> frame : suite.frames.entrySet()) {
                    try (FileOutputStream out = new FileOutputStream(new File(getFilesDir(), frame.getKey() + ".png"))) {
                        frame.getValue().compress(Bitmap.CompressFormat.PNG, 100, out);
                    }
                }
                android.util.Log.i("M3GProbe", report.toString());
                runOnUiThread(() -> showReport(report, suite));
            } catch (Throwable error) {
                android.util.Log.e("M3GProbe", "Probe failed", error);
                runOnUiThread(() -> waiting.setText(error.toString()));
            }
        }, "m3g-probe").start();
    }
    private void write(String name, byte[] data) throws Exception {
        try (FileOutputStream out = new FileOutputStream(new File(getFilesDir(), name))) { out.write(data); }
    }
    private void showReport(JSONObject report, ProbeSuite suite) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout column = new LinearLayout(this); column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(24, 16, 24, 24); column.setBackgroundColor(0xff101519);
        scroll.addView(column);
        JSONArray results = report.optJSONArray("tests");
        int passed = 0;
        for (int i = 0; i < results.length(); i++) if (results.optJSONObject(i).optBoolean("passed")) passed++;
        label(column, "M3G candidate / " + passed + " of " + results.length() + " checks passed", 22);
        label(column, report.optString("device") + " | " + report.optString("abi") + " | Synthetic tests", 14);
        LinearLayout row = null;
        int index = 0;
        for (Map.Entry<String, Bitmap> frame : suite.frames.entrySet()) {
            if (index++ % 2 == 0) { row = new LinearLayout(this); column.addView(row); }
            LinearLayout cell = new LinearLayout(this); cell.setOrientation(LinearLayout.VERTICAL);
            row.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
            label(cell, frame.getKey(), 16);
            ImageView image = new ImageView(this); image.setImageBitmap(frame.getValue());
            image.setAdjustViewBounds(true); image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            cell.addView(image, new LinearLayout.LayoutParams(-1, 240));
        }
        for (int i = 0; i < results.length(); i++) {
            JSONObject result = results.optJSONObject(i);
            label(column, (result.optBoolean("passed") ? "PASS  " : "FAIL  ") + result.optString("name")
                    + (result.has("error") ? "\n" + result.optString("error") : ""), 14);
        }
        setContentView(scroll);
    }
    private void label(LinearLayout parent, String text, int size) {
        TextView label = new TextView(this); label.setText(text); label.setTextSize(size);
        label.setTextColor(Color.WHITE); label.setPadding(0, 6, 8, 6); parent.addView(label);
    }
}
