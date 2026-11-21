/* SPDX-License-Identifier: GPL-3.0-only */
package javax.microedition.lcdui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;

/** Buttons are sized in the parent's coordinates, including a rotated MIDP canvas. */
final class GuestKeypadView extends LinearLayout {
    interface Input { void key(int code, int action); }
    private static final int CLOSE = 0;
    private static final int[][] PHONE = {
            {'1','2','3',Canvas.KEY_UP}, {'4','5','6',Canvas.KEY_FIRE},
            {'7','8','9',Canvas.KEY_DOWN}, {'*','0','#',CLOSE}
    };
    private static final int[][] GAME = {
            {Canvas.KEY_SOFT_LEFT,Canvas.KEY_UP,Canvas.KEY_SOFT_RIGHT,CLOSE},
            {Canvas.KEY_LEFT,Canvas.KEY_FIRE,Canvas.KEY_RIGHT,'1'},
            {'*',Canvas.KEY_DOWN,'#','3'}
    };
    private static final String[] LETTERS = {"", "", "abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz"};
    private final Input input;
    private final Runnable dismiss;
    private int type, mode;

    GuestKeypadView(Context context, Input input, Runnable dismiss) {
        super(context);
        this.input = input;
        this.dismiss = dismiss;
        setOrientation(VERTICAL);
        setFocusable(false);
        setClickable(true);
        setBackgroundColor(0xff16232e);
    }

    void configure(int nextType, int nextMode) {
        if (type == nextType && mode == nextMode) return;
        releaseKeys();
        removeAllViews();
        type = nextType;
        mode = nextMode;
        for (int[] codes : type == 1 ? PHONE : GAME) {
            LinearLayout row = new LinearLayout(getContext());
            addView(row, new LayoutParams(-1, 0, 1));
            for (int code : codes) {
                Key key = new Key(code);
                LayoutParams cell = new LayoutParams(0, -1, 1);
                cell.setMargins(1, 1, 1, 1);
                row.addView(key, cell);
            }
        }
    }

    private String label(int code) {
        switch (code) {
            case CLOSE: return "×";
            case Canvas.KEY_UP: return "▲";
            case Canvas.KEY_DOWN: return "▼";
            case Canvas.KEY_LEFT: return "◀";
            case Canvas.KEY_RIGHT: return "▶";
            case Canvas.KEY_FIRE: return "OK";
            case Canvas.KEY_SOFT_LEFT: return "L";
            case Canvas.KEY_SOFT_RIGHT: return "R";
            default:
                String digit = Character.toString((char) code);
                if (type != 1 || mode == 1 || code < '2' || code > '9') return digit;
                String letters = LETTERS[code - '0'];
                return digit + " " + (mode >= 3 ? letters.toUpperCase(Locale.ROOT) : letters);
        }
    }

    void releaseKeys() {
        for (int i = 0; i < getChildCount(); i++) {
            LinearLayout row = (LinearLayout) getChildAt(i);
            for (int j = 0; j < row.getChildCount(); j++) ((Key) row.getChildAt(j)).release();
        }
    }

    private final class Key extends TextView implements Runnable {
        private final int code;
        private boolean held;
        Key(int code) {
            super(GuestKeypadView.this.getContext());
            this.code = code;
            setText(label(code));
            setContentDescription(code == CLOSE ? "Close keypad" : getText());
            setClickable(true);
            setFocusable(false);
            setGravity(Gravity.CENTER);
            setSingleLine();
            setTextColor(0xfff3f7fa);
            setHorizontallyScrolling(false);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(3);
            bg.setColor(new ColorStateList(new int[][]{{android.R.attr.state_pressed},{}},
                    new int[]{0xff337588,0xff304452}));
            setBackground(bg);
        }

        @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            super.onSizeChanged(width, height, oldWidth, oldHeight);
            float labelWidth = Math.max(1f, getPaint().measureText(getText().toString()));
            float fit = getTextSize() * Math.max(1, width - 4) / labelWidth;
            setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.max(1f, Math.min(height * 0.53f, fit)));
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (!held) {
                        held = true;
                        setPressed(true);
                        if (code != CLOSE) {
                            input.key(code, 0);
                            postDelayed(this, ViewConfiguration.getKeyRepeatTimeout());
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    if (held && code == CLOSE) performClick();
                    release();
                    break;
                case MotionEvent.ACTION_CANCEL: release(); break;
            }
            return true;
        }

        @Override public boolean performClick() {
            super.performClick();
            if (code == CLOSE) dismiss.run();
            else if (!held) { input.key(code, 0); input.key(code, 1); }
            return true;
        }

        void release() {
            if (!held) return;
            held = false;
            removeCallbacks(this);
            setPressed(false);
            if (code != CLOSE) input.key(code, 1);
        }
        @Override public void run() {
            if (!held) return;
            input.key(code, 2);
            postDelayed(this, ViewConfiguration.getKeyRepeatDelay());
        }
        @Override protected void onDetachedFromWindow() {
            release();
            super.onDetachedFromWindow();
        }
    }
}
