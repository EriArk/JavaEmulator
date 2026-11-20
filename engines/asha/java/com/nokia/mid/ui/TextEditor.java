/* SPDX-License-Identifier: GPL-3.0-only */
package com.nokia.mid.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.TextField;
import javax.microedition.lcdui.ViewHandler;
import javax.microedition.util.ContextHolder;

public class TextEditor extends CanvasItem {
    public static final int DISABLE_SMILEY_MODE = 0x400000;
    private String content, initialInputMode;
    private int maxSize, constraints, selectionStart, caret;
    private Font font = Font.getDefaultFont();
    private int background = 0xffffffff, foreground = 0xff000000, highlight = 0xff6699cc;
    private boolean multiline, focused, touchEnabled = true;
    private TextEditorListener listener;
    private boolean scheduled, applying, selectionDirty, styleDirty = true;
    private volatile int scrollPosition;
    private Canvas mounted;
    private Field field;

    protected TextEditor(String text, int maxSize, int constraints, int width, int height) {
        if (maxSize <= 0) throw new IllegalArgumentException("Invalid maximum size");
        this.maxSize = maxSize;
        validateConstraints(constraints);
        this.constraints = constraints;
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Invalid editor size");
        setSize(width, height);
        setContent(text);
    }

    public static TextEditor createTextEditor(String text, int maxSize, int constraints, int width, int height) {
        return new TextEditor(text, maxSize, constraints, width, height);
    }

    public static TextEditor createTextEditor(int maxSize, int constraints, int width, int rows) {
        return createTextEditor("", maxSize, constraints, width, rows * Font.getDefaultFont().getHeight());
    }

    @Override
    synchronized void changed() {
        if (!scheduled && (parent != null || mounted != null)) {
            scheduled = true;
            ViewHandler.postEvent(this::apply);
        }
    }

    @Override
    public synchronized void setParent(Object value) {
        super.setParent(value);
        if (value == null) focused = false;
    }

    @Override
    public synchronized void setVisible(boolean value) {
        super.setVisible(value);
        if (!value) focused = false;
    }

    public synchronized void setFocus(boolean value) {
        if (parent == null) throw new IllegalStateException("Editor has no parent");
        focused = value;
        changed();
    }
    public synchronized boolean hasFocus() { return focused; }
    public synchronized void setTouchEnabled(boolean value) { touchEnabled = value; }
    public synchronized boolean isTouchEnabled() { return touchEnabled; }
    public synchronized String getContent() { return content; }
    public synchronized int size() { return content.codePointCount(0, content.length()); }
    public synchronized int getMaxSize() { return maxSize; }

    public synchronized int setMaxSize(int value) {
        if (value <= 0) throw new IllegalArgumentException("Invalid maximum size");
        if (size() > value) setContent(content.substring(0, index(value)));
        maxSize = value;
        return value;
    }

    private int index(int position) {
        if (position < 0 || position > size()) throw new StringIndexOutOfBoundsException(position);
        return content.offsetByCodePoints(0, position);
    }

    public synchronized void setContent(String text) {
        if (text == null) text = "";
        if (!validContent(text, constraints)) throw new IllegalArgumentException("Invalid editor content");
        int count = text.codePointCount(0, text.length());
        if (count > maxSize) throw new IllegalArgumentException("Text exceeds maximum size");
        content = text;
        setCaret(count);
    }

    public synchronized void insert(String text, int position) {
        java.util.Objects.requireNonNull(text);
        position = Math.max(0, Math.min(position, size()));
        int offset = index(position);
        setContent(content.substring(0, offset) + text + content.substring(offset));
        setCaret(position + text.codePointCount(0, text.length()));
    }

    public synchronized void delete(int offset, int length) {
        if (length < 0) throw new StringIndexOutOfBoundsException(length);
        setContent(content.substring(0, index(offset)) + content.substring(index(offset + length)));
        setCaret(offset);
    }

    public synchronized void setSelection(int start, int length) {
        if (length < 0) throw new StringIndexOutOfBoundsException(length);
        int first = index(start);
        int last = index(start + length);
        selectionStart = first;
        caret = last;
        selectionDirty = true;
        changed();
    }

    public synchronized String getSelection() {
        return content.substring(Math.min(selectionStart, caret), Math.max(selectionStart, caret));
    }
    public synchronized void setCaret(int position) { setSelection(position, 0); }
    public synchronized int getCaretPosition() { return content.codePointCount(0, caret); }

    public synchronized Font getFont() { return font; }
    public synchronized void setFont(Font value) { font = java.util.Objects.requireNonNull(value); restyle(); }
    public synchronized int getConstraints() { return constraints; }
    public synchronized void setConstraints(int value) {
        validateConstraints(value);
        constraints = value;
        if (!validContent(content, value)) setContent("");
        restyle();
    }

    private static void validateConstraints(int value) {
        if ((value & TextField.CONSTRAINT_MASK) > TextField.DECIMAL)
            throw new IllegalArgumentException("Unknown input constraint");
    }

    private static boolean validContent(String text, int constraints) {
        if (text.isEmpty()) return true;
        switch (constraints & TextField.CONSTRAINT_MASK) {
            case TextField.NUMERIC: return text.matches("-?[0-9]+");
            case TextField.DECIMAL: return text.matches("-?([0-9]+(\\.[0-9]*)?|\\.[0-9]+)");
            default: return true;
        }
    }
    public synchronized boolean isMultiline() { return multiline; }
    public synchronized void setMultiline(boolean value) { multiline = value; restyle(); }
    public synchronized void setInitialInputMode(String mode) { initialInputMode = mode; }
    public synchronized String getInitialInputMode() { return initialInputMode; }
    public synchronized int getForegroundColor() { return foreground; }
    public synchronized void setForegroundColor(int value) { foreground = value; changed(); }
    public synchronized int getBackgroundColor() { return background; }
    public synchronized void setBackgroundColor(int value) { background = value; changed(); }
    public synchronized void setHighlightBackgroundColor(int value) { highlight = value; changed(); }

    public int getVisibleContentPosition() { return scrollPosition; }
    public int getLineMarginHeight() { return 0; }
    public synchronized int getContentHeight() {
        if (!multiline) return font.getHeight();
        TextPaint paint = new TextPaint();
        paint.setTypeface(typeface());
        paint.setTextSize(textSize());
        return StaticLayout.Builder.obtain(content, 0, content.length(), paint, Math.max(1, width))
                .build().getLineCount() * font.getHeight();
    }

    private Typeface typeface() {
        Typeface face = font.getFace() == Font.FACE_MONOSPACE ? Typeface.MONOSPACE : Typeface.DEFAULT;
        return Typeface.create(face, font.getStyle() & Typeface.BOLD_ITALIC);
    }

    private float textSize() {
        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setTypeface(typeface());
        paint.setTextSize(100);
        return font.getHeight() * 100f / paint.getFontSpacing();
    }

    private void restyle() { styleDirty = true; changed(); }

    public synchronized void setTextEditorListener(TextEditorListener value) { listener = value; }

    private void notifyGuest(int actions) {
        TextEditorListener target = listener;
        if (target == null || parent == null) return;
        Canvas owner = parent;
        owner.postGuestCallback(() -> {
            synchronized (this) {
                if (parent != owner || listener != target) return;
            }
            target.inputAction(this, actions);
        });
    }

    private synchronized void apply() {
        scheduled = false;
        applying = true;
        try {
            if (mounted != parent && mounted != null) {
                field.releaseInput();
                mounted.detachGuestView(field);
                mounted = null;
                field = null;
            }
            if (parent == null) return;
            if (field == null) {
                field = new Field(ContextHolder.getActivity());
                mounted = parent;
                mounted.attachGuestView(field);
                styleDirty = true;
                selectionDirty = true;
            }
            if (styleDirty) {
                field.setSingleLine(!multiline);
                field.setInputType(inputType());
                field.setTransformationMethod((constraints & TextField.PASSWORD) != 0
                        ? PasswordTransformationMethod.getInstance() : null);
                field.setEnabled((constraints & TextField.UNEDITABLE) == 0);
                field.setTypeface(typeface());
                field.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize());
                styleDirty = false;
            }
            field.setLayoutParams(new FrameLayout.LayoutParams(width, height, Gravity.LEFT | Gravity.TOP));
            field.setTranslationX(x);
            field.setTranslationY(y);
            field.setTranslationZ(z);
            field.setTextColor(foreground);
            field.setBackgroundColor(background);
            field.setHighlightColor(highlight);
            if (!content.contentEquals(field.getText())) {
                // Hardware key filters (e.g. numeric) govern user input only.
                field.setText(content);
                selectionDirty = true;
            }
            if (selectionDirty) {
                field.setSelection(Math.min(selectionStart, field.length()), Math.min(caret, field.length()));
                selectionDirty = false;
            }
            if ((!focused || !visible) && field.hasFocus()) field.releaseInput();
            field.setVisibility(visible ? Field.VISIBLE : Field.GONE);
            if (focused && visible && !field.hasFocus()) field.openInput();
        } finally {
            applying = false;
        }
    }

    private int inputType() {
        int type;
        switch (constraints & TextField.CONSTRAINT_MASK) {
            case TextField.NUMERIC: return InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED;
            case TextField.DECIMAL: return InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED
                    | InputType.TYPE_NUMBER_FLAG_DECIMAL;
            case TextField.PHONENUMBER: return InputType.TYPE_CLASS_PHONE;
            case TextField.EMAILADDR: type = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS; break;
            case TextField.URL: type = InputType.TYPE_TEXT_VARIATION_URI; break;
            default: type = InputType.TYPE_TEXT_VARIATION_NORMAL;
        }
        type |= InputType.TYPE_CLASS_TEXT;
        if (multiline) type |= InputType.TYPE_TEXT_FLAG_MULTI_LINE;
        if ((constraints & (TextField.PASSWORD | TextField.SENSITIVE | TextField.NON_PREDICTIVE)) != 0)
            type |= InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        if ((constraints & TextField.PASSWORD) != 0)
            type = (type & ~InputType.TYPE_MASK_VARIATION) | InputType.TYPE_TEXT_VARIATION_PASSWORD;
        if ((constraints & TextField.INITIAL_CAPS_WORD) != 0) type |= InputType.TYPE_TEXT_FLAG_CAP_WORDS;
        if ((constraints & TextField.INITIAL_CAPS_SENTENCE) != 0) type |= InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
        return type;
    }

    private final class Field extends EditText implements TextWatcher {
        Field(Context context) {
            super(context);
            setPadding(0, 0, 0, 0);
            setGravity(Gravity.LEFT | Gravity.TOP);
            setIncludeFontPadding(false);
            setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            setFilters(new InputFilter[]{(src, start, end, dst, from, to) -> {
                synchronized (TextEditor.this) {
                    int room = maxSize - Character.codePointCount(dst, 0, from)
                            - Character.codePointCount(dst, to, dst.length());
                    if (Character.codePointCount(src, start, end) <= room) return null;
                    int last = Character.offsetByCodePoints(src, start, Math.max(0, room));
                    return src.subSequence(start, last);
                }
            }});
            addTextChangedListener(this);
            setOnEditorActionListener((view, id, event) -> {
                synchronized (TextEditor.this) {
                    if (id == EditorInfo.IME_ACTION_NEXT || id == EditorInfo.IME_ACTION_PREVIOUS) {
                        notifyGuest(id == EditorInfo.IME_ACTION_NEXT ? TextEditorListener.ACTION_TRAVERSE_NEXT
                                : TextEditorListener.ACTION_TRAVERSE_PREVIOUS);
                        return listener != null;
                    }
                    return false;
                }
            });
            setOnFocusChangeListener((v, hasFocus) -> {
                synchronized (TextEditor.this) {
                    if (!applying && isAttachedToWindow()) focused = hasFocus;
                }
            });
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            post(() -> {
                synchronized (TextEditor.this) {
                    if (field == this && focused && visible && isAttachedToWindow()) openInput();
                }
            });
        }

        void openInput() {
            if (isAttachedToWindow() && requestFocus())
                getContext().getSystemService(InputMethodManager.class).showSoftInput(this, 0);
        }

        void releaseInput() {
            if (!hasFocus()) return;
            getContext().getSystemService(InputMethodManager.class).hideSoftInputFromWindow(getWindowToken(), 0);
            if (mounted != null) mounted.restoreGameInputFocus();
            if (hasFocus()) clearFocus();
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            synchronized (TextEditor.this) {
                if (!touchEnabled || !focused) return false;
            }
            return super.onTouchEvent(event);
        }

        @Override protected void onScrollChanged(int x, int y, int oldX, int oldY) {
            super.onScrollChanged(x, y, oldX, oldY);
            scrollPosition = y;
        }

        @Override protected void onSelectionChanged(int start, int end) {
            super.onSelectionChanged(start, end);
            // The base constructor also calls this hook.
            if (TextEditor.this == null) return;
            synchronized (TextEditor.this) {
                if (field != this || applying || selectionDirty || start < 0 || end < 0
                        || !content.contentEquals(getText())) return;
                selectionStart = start;
                caret = end;
                notifyGuest(TextEditorListener.ACTION_CARET_MOVE);
            }
        }

        @Override public void afterTextChanged(Editable value) {
            synchronized (TextEditor.this) {
                if (applying || field != this || selectionDirty) return;
                content = value.toString();
                selectionStart = Math.max(0, getSelectionStart());
                caret = Math.max(0, getSelectionEnd());
                notifyGuest(TextEditorListener.ACTION_CONTENT_CHANGE | TextEditorListener.ACTION_CARET_MOVE);
            }
        }
        @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence text, int start, int before, int count) {}
    }
}
