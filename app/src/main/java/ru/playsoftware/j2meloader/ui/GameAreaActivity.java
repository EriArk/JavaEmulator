/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.graphics.*;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.IOException;

public final class GameAreaActivity extends Activity {
    private boolean portrait;
    private Preview preview;
    private LinearLayout panel;
    private boolean drawing;
    private TextView readout;
    private int[] saved;
    private final Rect area = new Rect();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        portrait = getIntent().getBooleanExtra("portrait", true);
        setRequestedOrientation(portrait ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT :
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        saved = state == null ? GameArea.read(this, portrait) : state.getIntArray("draft");
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        FrameLayout root = new FrameLayout(this);
        preview = new Preview();
        root.addView(preview);
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xcf202124);
        readout = new TextView(this);
        readout.setTextColor(Color.WHITE);
        panel.addView(readout);
        LinearLayout actions = new LinearLayout(this);
        panel.addView(actions);
        button(actions, "Draw", () -> {
            drawing = true;
            panel.setVisibility(View.INVISIBLE);
            Toast.makeText(this, "Drag between two corners of the visible screen", Toast.LENGTH_SHORT).show();
        });
        button(actions, "Pixels", this::editPixels);
        button(actions, "Reset", () -> {area.set(0, 0, preview.getWidth(), preview.getHeight()); refresh();});
        button(actions, "Save", () -> {
            try {
                GameArea.save(this, portrait, preview.getWidth(), preview.getHeight(), area);
                finish();
            } catch (IOException e) {Toast.makeText(this, "Could not save game area", Toast.LENGTH_LONG).show();}
        });
        root.addView(panel, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        setContentView(root);
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putIntArray("draft", new int[]{preview.getWidth(), preview.getHeight(),
                area.left, area.top, area.right, area.bottom});
    }

    private void button(LinearLayout row, String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setOnClickListener(v -> action.run());
        row.addView(b);
    }

    private void refresh() {
        readout.setText("X " + area.left + "   Y " + area.top + "     " + area.width() + " × " + area.height() + " px");
        preview.invalidate();
    }

    private void editPixels() {
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(fields);
        String[] labels = {"X from left", "Y from top", "Width", "Height"};
        int[] values = {area.left, area.top, area.width(), area.height()};
        EditText[] inputs = new EditText[4];
        for (int i = 0; i < inputs.length; i++) {
            LinearLayout row = new LinearLayout(this);
            TextView label = new TextView(this);
            label.setText(labels[i]);
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            EditText input = inputs[i] = new EditText(this);
            input.setInputType(InputType.TYPE_CLASS_NUMBER);
            input.setSingleLine();
            input.setContentDescription(labels[i]);
            input.setText(String.valueOf(values[i]));
            input.setSelectAllOnFocus(true);
            row.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
            fields.addView(row);
        }
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Game area in pixels")
                .setView(scroll).setPositiveButton("Preview", null).setNegativeButton("Cancel", null).create();
        dialog.getWindow().setGravity(Gravity.TOP);
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int[] limits = {preview.getWidth() - 1, preview.getHeight() - 1, 0, 0};
            for (int i = 0; i < inputs.length; i++) {
                if (i >= 2) limits[i] = (i == 2 ? preview.getWidth() : preview.getHeight()) - values[i - 2];
                int min = i < 2 ? 0 : 1;
                try {values[i] = Integer.parseInt(inputs[i].getText().toString());}
                catch (NumberFormatException e) {values[i] = -1;}
                if (values[i] < min || values[i] > limits[i]) {
                    inputs[i].requestFocus();
                    inputs[i].setError("Enter " + min + " to " + limits[i]);
                    return;
                }
            }
            area.set(values[0], values[1], values[0] + values[2], values[1] + values[3]);
            refresh();
            dialog.dismiss();
        });
    }

    private final class Preview extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Rect start = new Rect();
        int downX, downY, horizontal, vertical;
        Preview() {super(GameAreaActivity.this);setContentDescription("Drag the game area or its edges");}
        @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
            int[] previous = oldW > 0 && oldH > 0 ? new int[]{oldW, oldH, area.left, area.top, area.right, area.bottom} : saved;
            area.set(GameArea.bounds(previous, w, h));
            refresh();
        }
        @Override protected void onDraw(Canvas canvas) {
            canvas.drawColor(Color.BLACK);
            paint.setColor(0xff193747);
            canvas.drawRect(area, paint);
            paint.setColor(0xff8fe2ff);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4);
            canvas.drawRect(area.left+1,area.top+1,area.right-1,area.bottom-1,paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(14 * getResources().getDisplayMetrics().scaledDensity);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.save();
            canvas.clipRect(area);
            canvas.drawText("Drag edges to resize or drag inside to move", area.centerX(), area.centerY(), paint);
            canvas.restore();
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            int x = Math.round(e.getX()), y = Math.round(e.getY());
            if (drawing) {
                int action = e.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    start.set(area);
                    downX = x;
                    downY = y;
                } else if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
                    int x1 = clamp(Math.min(downX, x), 0, getWidth()-1);
                    int y1 = clamp(Math.min(downY, y), 0, getHeight()-1);
                    area.set(x1, y1, clamp(Math.max(downX, x), x1+1, getWidth()),
                            clamp(Math.max(downY, y), y1+1, getHeight()));
                }
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    if (action == MotionEvent.ACTION_CANCEL || x == downX || y == downY) area.set(start);
                    drawing = false;
                    panel.setVisibility(View.VISIBLE);
                }
                refresh();
                return true;
            }
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                int slop = Math.round(24 * getResources().getDisplayMetrics().density);
                if (x < area.left-slop || x > area.right+slop || y < area.top-slop || y > area.bottom+slop) return false;
                start.set(area); downX=x; downY=y;
                horizontal = edge(x, area.left, area.right, slop);
                vertical = edge(y, area.top, area.bottom, slop);
            } else if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                int dx=x-downX, dy=y-downY;
                area.set(start);
                if (horizontal==0 && vertical==0) area.offset(clamp(dx,-start.left,getWidth()-start.right),
                        clamp(dy,-start.top,getHeight()-start.bottom));
                else {
                    if (horizontal<0) area.left=clamp(start.left+dx,0,start.right-1);
                    if (horizontal>0) area.right=clamp(start.right+dx,start.left+1,getWidth());
                    if (vertical<0) area.top=clamp(start.top+dy,0,start.bottom-1);
                    if (vertical>0) area.bottom=clamp(start.bottom+dy,start.top+1,getHeight());
                }
                refresh();
            }
            return true;
        }
        int edge(int value, int lo, int hi, int slop) {
            int left=Math.abs(value-lo), right=Math.abs(value-hi);
            return Math.min(left,right)>slop ? 0 : left<right ? -1 : 1;
        }
        int clamp(int v,int lo,int hi) {return Math.max(lo,Math.min(hi,v));}
    }
}
