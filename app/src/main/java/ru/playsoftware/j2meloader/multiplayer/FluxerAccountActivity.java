/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.lifecycle.ViewModelProvider;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.settings.CompactSettingsActivity;
import ru.playsoftware.j2meloader.ui.AppPalette;

public final class FluxerAccountActivity extends CompactSettingsActivity {
    private FluxerAccountModel model;
    private FluxerAuth.State state;
    private TextView heading, description, code, detail;
    private ProgressBar progress;
    private Button primary, secondary, remove;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable clock = new Runnable() {
        @Override public void run() { if (state != null) render(state); handler.postDelayed(this, 1000); }
    };

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved); screenTitle("Fluxer account");
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        FrameLayout frame = new FrameLayout(this);
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(20), dp(20), dp(20));
        int width = Math.min(getResources().getDisplayMetrics().widthPixels, dp(520));
        FrameLayout.LayoutParams column = new FrameLayout.LayoutParams(width, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        frame.addView(body, column); scroll.addView(frame); screenContent.addView(scroll);
        heading = text(body, 24, R.color.text_primary); heading.setTypeface(null, Typeface.BOLD);
        heading.setId(R.id.fluxer_heading); heading.setText("Fluxer");
        description = text(body, 16, R.color.text_secondary); description.setId(R.id.fluxer_status);
        description.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true); body.addView(progress, new LinearLayout.LayoutParams(-1, dp(8)));
        code = text(body, 24, R.color.accent); code.setId(R.id.fluxer_code);
        code.setGravity(Gravity.CENTER); code.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(code, 16, 26, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        code.setSingleLine(true); code.setMinHeight(dp(64));
        code.setBackgroundResource(R.drawable.bg_fluxer_action);
        detail = text(body, 14, R.color.text_secondary); detail.setId(R.id.fluxer_detail);
        primary = action(body, R.id.fluxer_primary, android.R.drawable.ic_menu_send, this::primary);
        secondary = action(body, R.id.fluxer_secondary, android.R.drawable.ic_menu_close_clear_cancel, this::secondary);
        remove = action(body, R.id.fluxer_remove, android.R.drawable.ic_menu_delete, () -> {
            new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Remove saved sign-in?")
                    .setMessage("This removes the sign-in from this device only. It does not revoke the session on Fluxer. Games and saves will not be removed.")
                    .setNegativeButton("Cancel", null).setPositiveButton("Remove", (dialog, which) -> model.forget()).show();
        });
        model = new ViewModelProvider(this).get(FluxerAccountModel.class);
        model.state.observe(this, this::render);
        description.setText("Checking saved sign-in...");
        primary.setVisibility(View.GONE); secondary.setVisibility(View.GONE); remove.setVisibility(View.GONE);
        code.setVisibility(View.GONE); detail.setVisibility(View.GONE);
    }
    private TextView text(LinearLayout parent, int size, int color) {
        TextView view = new TextView(this); view.setTextSize(size); view.setTextColor(AppPalette.color(this, color));
        view.setPadding(0, dp(4), 0, dp(12)); parent.addView(view, new LinearLayout.LayoutParams(-1, -2)); return view;
    }
    private Button action(LinearLayout parent, int id, int icon, Runnable run) {
        Button button = new Button(this); button.setId(id); button.setAllCaps(false); button.setTextSize(16);
        button.setTextColor(AppPalette.color(this, R.color.text_primary)); button.setBackgroundResource(R.drawable.bg_fluxer_action);
        android.graphics.drawable.Drawable drawable = androidx.core.content.ContextCompat.getDrawable(this, icon).mutate();
        drawable.setTint(AppPalette.color(this, R.color.accent)); drawable.setBounds(0,0,dp(22),dp(22));
        button.setCompoundDrawables(drawable, null, null, null); button.setCompoundDrawablePadding(dp(12));
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START); button.setPadding(dp(16),dp(10),dp(16),dp(10));
        button.setMinHeight(dp(52)); button.setOnClickListener(v -> run.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(8);
        parent.addView(button, params); return button;
    }
    private void render(FluxerAuth.State next) {
        boolean changed = state == null || state.phase != next.phase;
        state = next;
        boolean waiting = next.phase == FluxerAuth.Phase.WAITING;
        boolean busy = next.phase == FluxerAuth.Phase.STARTING || next.phase == FluxerAuth.Phase.VERIFYING
                || next.phase == FluxerAuth.Phase.SIGNING_OUT;
        heading.setText(next.name.isEmpty() ? "Fluxer" : next.name);
        if (!description.getText().toString().equals(next.message)) description.setText(next.message);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        code.setVisibility(waiting ? View.VISIBLE : View.GONE); code.setText(next.code);
        detail.setVisibility(View.VISIBLE);
        long seconds = Math.max(0, (next.expiresAt - System.currentTimeMillis() + 999) / 1000);
        if (waiting) detail.setText("Expires in " + (seconds / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds % 60));
        else if (!next.username.isEmpty()) detail.setText("@" + next.username);
        else if (next.phase == FluxerAuth.Phase.SIGNED_OUT) detail.setText("Sign in securely on the official Fluxer website. Playing offline does not require an account.");
        else detail.setVisibility(View.GONE);
        String mainAction = null, alternateAction = null;
        boolean canRemove = false;
        if (waiting) {
            mainAction = "Copy code & open Fluxer"; alternateAction = "Cancel sign-in";
        } else if (next.phase == FluxerAuth.Phase.SIGNED_OUT) mainAction = "Continue with Fluxer";
        else if (next.phase == FluxerAuth.Phase.STARTING) alternateAction = "Cancel sign-in";
        else if (next.phase == FluxerAuth.Phase.CONNECTED) {
            mainAction = next.remembered ? "Open chats" : "Retry saving session";
            alternateAction = "Sign out";
        } else if (next.phase == FluxerAuth.Phase.OFFLINE) {
            mainAction = "Retry connection"; alternateAction = "Sign out"; canRemove = true;
        } else if (next.phase == FluxerAuth.Phase.ERROR) canRemove = true;
        show(primary, mainAction); show(secondary, alternateAction); show(remove, canRemove ? "Remove from device" : null);
        boolean ready = System.currentTimeMillis() >= next.retryAt;
        primary.setEnabled(ready); secondary.setEnabled(ready || !next.hasSession);
        primary.setAlpha(primary.isEnabled() ? 1f : 0.5f); secondary.setAlpha(secondary.isEnabled() ? 1f : 0.5f);
        if (!ready) { detail.setVisibility(View.VISIBLE); detail.setText("Try again in " + Math.max(1, (next.retryAt - System.currentTimeMillis() + 999) / 1000) + "s"); }
        // Do not expose the approval code in Android's app-switcher snapshot.
        if (waiting) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if (changed) focusContentWhenReady();
    }
    private void show(Button button, String label) {
        if (label != null) button.setText(label);
        button.setVisibility(label == null ? View.GONE : View.VISIBLE);
    }
    private void primary() {
        if (state == null) return;
        if (state.phase == FluxerAuth.Phase.SIGNED_OUT) model.begin();
        else if (state.phase == FluxerAuth.Phase.WAITING) {
            ClipboardManager clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Fluxer sign-in code", state.code);
            android.os.PersistableBundle extras = new android.os.PersistableBundle();
            extras.putBoolean("android.content.extra.IS_SENSITIVE", true); clip.getDescription().setExtras(extras);
            clipboard.setPrimaryClip(clip);
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(FluxerAuth.LOGIN_URL))); }
            catch (android.content.ActivityNotFoundException e) {
                Toast.makeText(this, "No browser found. Open web.fluxer.app/login?handoff=1 in a browser and enter the code.", Toast.LENGTH_LONG).show();
            }
        } else if (state.phase == FluxerAuth.Phase.CONNECTED && state.remembered) {
            startActivity(new Intent(this, FluxerChatsActivity.class));
        } else model.retry();
    }
    private void secondary() {
        if (state == null) return;
        if (state.phase == FluxerAuth.Phase.WAITING || state.phase == FluxerAuth.Phase.STARTING) model.cancel();
        else model.signOut();
    }
    @Override protected void onStart() { super.onStart(); model.revalidate(); handler.post(clock); }
    @Override protected void onStop() { handler.removeCallbacks(clock); super.onStop(); }
}
