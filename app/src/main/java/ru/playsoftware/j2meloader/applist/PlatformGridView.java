/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.applist;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.ui.AppPalette;

/** Root library content. The header and its commands belong to the library fragment. */
public final class PlatformGridView extends LinearLayout {
    static final String STARTUP = "pref_platform_launcher";
    private static final int[] ART = {0, R.drawable.sticker_java, R.drawable.sticker_mophun,
            R.drawable.sticker_symbian, R.drawable.sticker_ngage, R.drawable.sticker_wipi};
    private final ScrollView scroll;
    private final GridLayout grid;
    private final EditText search;
    private final ListView results;
    private final TextView empty;
    private final ArrayAdapter<AppItem> matches;
    private List<AppItem> games = Collections.emptyList();
    private IntConsumer openPlatform;
    private Consumer<AppItem> play;

    public PlatformGridView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        scroll = new ScrollView(context);
        grid = new GridLayout(context);
        grid.setPadding(dp(6), dp(8), dp(6), dp(8));
        scroll.addView(grid);
        addView(scroll, new LayoutParams(-1, -1));
        for (int id = 1; id < LibraryQuery.PLATFORMS.length; id++) {
            if (LibraryNavigation.available(id)) grid.addView(sticker(id));
        }
        scroll.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
            if (r - l != or - ol || b - t != ob - ot) layoutStickers(r - l, b - t);
        });
        search = new EditText(context);
        search.setSingleLine();
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                | android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
                | android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN);
        search.setHint(R.string.search_all_games);
        search.setPadding(dp(14), 0, dp(12), 0);
        addView(search, new LayoutParams(-1, dp(48)));
        results = new ListView(context);
        matches = new ArrayAdapter<AppItem>(context, android.R.layout.simple_list_item_2, android.R.id.text1) {
            @Override public View getView(int pos, View recycled, ViewGroup parent) {
                View row = super.getView(pos, recycled, parent);
                AppItem item = getItem(pos);
                ((TextView) row.findViewById(android.R.id.text1)).setText(item.getTitle());
                ((TextView) row.findViewById(android.R.id.text2)).setText(LibraryQuery.PLATFORMS[LibraryQuery.platform(item)]);
                return row;
            }
        };
        results.setAdapter(matches);
        results.setOnItemClickListener((parent, row, pos, id) -> play.accept(matches.getItem(pos)));
        addView(results, new LayoutParams(-1, -2));
        empty = new TextView(context);
        empty.setText(R.string.platform_search_empty);
        empty.setGravity(Gravity.CENTER);
        addView(empty, new LayoutParams(-1, dp(64)));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refresh(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        setSearching(false);
    }

    void bind(IntConsumer open, Consumer<AppItem> launch) { openPlatform = open; play = launch; }

    void setGames(List<AppItem> items) { games = items; if (isSearching()) refresh(); }

    boolean isSearching() { return search.getVisibility() == VISIBLE; }

    void setSearching(boolean enabled) {
        scroll.setVisibility(enabled ? GONE : VISIBLE);
        search.setVisibility(enabled ? VISIBLE : GONE);
        if (enabled) {
            refresh();
            search.requestFocus();
            search.post(() -> { if (isSearching() && search.hasFocus()) keyboard().showSoftInput(search, InputMethodManager.SHOW_IMPLICIT); });
        } else {
            if (search.hasFocus()) keyboard().hideSoftInputFromWindow(search.getWindowToken(), 0);
            search.setText("");
            results.setVisibility(GONE);
            empty.setVisibility(GONE);
        }
    }

    String query() { return search.getText().toString(); }

    void restoreSearch(String query) {
        setSearching(true);
        search.setText(query);
        search.setSelection(search.length());
    }

    private InputMethodManager keyboard() {
        return (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    }

    private void refresh() {
        if (!isSearching()) return;
        matches.setNotifyOnChange(false);
        matches.clear();
        matches.addAll(LibraryQuery.select(games, 0, 0, search.getText().toString()));
        matches.notifyDataSetChanged();
        results.setVisibility(matches.isEmpty() ? GONE : VISIBLE);
        empty.setVisibility(matches.isEmpty() ? VISIBLE : GONE);
    }

    void focusPlatform(int id) {
        View target = grid.findViewWithTag(id);
        if (target != null) target.requestFocusFromTouch();
    }

    private void layoutStickers(int w, int h) {
        if (w <= 0 || h <= 0) return;
        int width = w - grid.getPaddingLeft() - grid.getPaddingRight();
        int count = grid.getChildCount();
        int cols = Math.min(count, Math.max(1, width / dp(146)));
        if (cols == 4 && (count == 5 || count == 9)) cols = 3;
        int rows = (count + cols - 1) / cols;
        int height = Math.max(dp(136), Math.min(dp(234), (h - dp(16)) / rows));
        grid.setColumnCount(GridLayout.UNDEFINED);
        for (int i = 0; i < count; i++) {
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(i / cols), GridLayout.spec(i % cols));
            lp.setMargins(dp(5), dp(5), dp(5), dp(5));
            lp.width = width / cols - dp(10);
            lp.height = height - dp(10);
            View tile = grid.getChildAt(i);
            tile.setLayoutParams(lp);
            int rowSize = Math.min(cols, count - (i / cols) * cols);
            tile.setTranslationX((cols - rowSize) * (width / cols) / 2f);
        }
        grid.setColumnCount(cols);
    }

    private View sticker(int id) {
        Context context = getContext();
        LinearLayout tile = new LinearLayout(context);
        tile.setTag(id);
        tile.setOrientation(VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(10), dp(8), dp(10), dp(8));
        tile.setDefaultFocusHighlightEnabled(false);
        tile.setFocusable(true);
        tile.setContentDescription(LibraryQuery.PLATFORMS[id]);
        tile.setOnClickListener(v -> openPlatform.accept(id));
        StickerImage image = new StickerImage(context);
        image.setPadding(dp(6), dp(6), dp(6), dp(6));
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int art = id < ART.length ? ART[id] : getResources().getIdentifier(
                id == 6 ? "sticker_dingoo" : id == 7 ? "sticker_gemei" : id == 8 ? "sticker_piece" : id == 9 ? "sticker_gp32" : id == 10 ? "sticker_supervision" : id == 11 ? "sticker_megaduck" : id == 12 ? "sticker_cybiko" : id == 13 ? "sticker_flash" : id == 14 ? "sticker_gamate" : id == 15 ? "sticker_gameking" : id == 16 ? "sticker_palm" : id == 17 ? "sticker_mre" : id == 18 ? "sticker_webos" : id == 19 ? "sticker_firefox" : id == 20 ? "sticker_kaios" : id == 21 ? "sticker_portfolio" : id == 22 ? "sticker_pocketpc" : id == 29 ? "sticker_epoch" : id == 28 ? "sticker_hartung" : id == 27 ? "sticker_pokitto" : id == 26 ? "sticker_gamebuino" : id == 25 ? "sticker_arduboy" : id == 24 ? "sticker_iphone" : "sticker_gizmondo", "drawable", context.getPackageName());
        image.setImageResource(art);
        image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        tile.addView(image, new LayoutParams(-1, 0, 1));
        TextView label = new TextView(context);
        label.setText(id == 8 ? "Aquaplus\nP/ECE" : id == 29 ? "Epoch Game\nPocket Computer" : LibraryQuery.PLATFORMS[id]);
        label.setTextSize(id == 29 ? 14 : 17);
        label.setTextColor(AppPalette.color(context, R.color.text_primary));
        label.setGravity(Gravity.CENTER);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setPadding(0, dp(5), 0, 0);
        label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        tile.addView(label, new LayoutParams(-1, -2));
        tile.setOnFocusChangeListener((v, focused) -> {
            image.highlight(focused);
            label.setTextColor(AppPalette.color(context, focused ? R.color.accent : R.color.text_primary));
        });
        return tile;
    }

    /** Draw the accent around the actual silhouette, including irregular handheld shapes. */
    private static final class StickerImage extends androidx.appcompat.widget.AppCompatImageView {
        private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final int[] offset = new int[2];
        private Bitmap mask;
        private boolean highlighted;

        StickerImage(Context context) {
            super(context);
            glow.setColor(AppPalette.color(context, R.color.accent));
        }

        void highlight(boolean focused) {
            highlighted = focused;
            if (focused && mask == null && getDrawable() instanceof BitmapDrawable) {
                Bitmap source = ((BitmapDrawable) getDrawable()).getBitmap();
                Paint blur = new Paint(Paint.ANTI_ALIAS_FLAG);
                float radius = Math.max(source.getWidth(), source.getHeight()) * 0.035f;
                blur.setMaskFilter(new BlurMaskFilter(radius, BlurMaskFilter.Blur.SOLID));
                mask = source.extractAlpha(blur, offset);
            }
            float scale = focused ? 1.12f : 1f;
            animate().scaleX(scale).scaleY(scale).setDuration(150).start();
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            if (highlighted && mask != null) {
                int save = canvas.save();
                canvas.translate(getPaddingLeft(), getPaddingTop());
                canvas.concat(getImageMatrix());
                canvas.drawBitmap(mask, offset[0], offset[1], glow);
                canvas.restoreToCount(save);
            }
            super.onDraw(canvas);
        }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
