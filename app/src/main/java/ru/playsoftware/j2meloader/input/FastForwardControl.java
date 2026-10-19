// SPDX-License-Identifier: GPL-3.0-only
package ru.playsoftware.j2meloader.input;

import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.runtime.FastForward;

/** Shared live command and shortcut, separate from transactional game settings. */
public final class FastForwardControl {
    public static final int[] BUTTONS = {0, 106, 107, 102, 103, 104, 105, 96, 97, 99, 100};
    public static final String[] BUTTON_NAMES = {"None", "L3", "R3", "L1", "R1", "L2", "R2", "A", "B", "X", "Y"};
    private final ProfileModel profile;
    private final BooleanSupplier available;
    private final ImageButton button;
    private final FastForward state;
    private Runnable changed = () -> { };
    private boolean pendingStart;

    public FastForwardControl(FrameLayout root, ProfileModel profile, BooleanSupplier available,
                              IntConsumer speed, IntConsumer frames, boolean javaGame) {
        this.profile = profile; this.available = available;
        button = new ImageButton(root.getContext()) {
            @Override public void onWindowFocusChanged(boolean focused) {
                super.onWindowFocusChanged(focused);
                if (focused) post(FastForwardControl.this::startPending);
            }
        };
        button.setId(R.id.game_fast_forward_button);
        button.setImageResource(android.R.drawable.ic_media_ff);
        int size = Math.round(48 * root.getResources().getDisplayMetrics().density);
        button.setPadding(size / 4, size / 4, size / 4, size / 4);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size, Gravity.TOP | Gravity.START);
        params.setMarginStart(Math.round(104 * root.getResources().getDisplayMetrics().density));
        root.addView(button, params);
        state = new FastForward(percent -> {
            speed.accept(percent);
            frames.accept(frameLimit(javaGame ? profile.fpsLimit : profile.getNativeFpsLimit()));
            render(); changed.run();
        });
        button.setOnClickListener(v -> toggle());
        render();
    }
    private int frameLimit(int base) { return state == null ? base : state.frameLimit(base); }
    public void changed(Runnable callback) { changed = callback; }
    public boolean enabled() { return available.getAsBoolean(); }
    public int binding() {
        if (!enabled()) return 0;
        for (int key : BUTTONS) if (key == profile.fastForwardButton) return key;
        return 0;
    }
    public boolean active() { return state.active(); }
    public void configure() {
        state.configure(profile.getEmulationSpeed(), profile.fastForwardSpeed, profile.fastForwardToggle);
    }
    public void button(boolean down) {
        if (!enabled()) { stop(); return; }
        if (down && !active()) configure();
        state.button(down);
    }
    public void toggle() {
        if (!enabled()) return;
        if (!active()) configure();
        state.toggle();
    }
    /** Input cleanup during menu dismissal must not discard the queued resume command. */
    public void stop() { state.stop(); }
    public void cancel() { pendingStart = false; stop(); }
    private void startPending() {
        if (pendingStart && button.hasWindowFocus() && button.isAttachedToWindow()) {
            pendingStart = false; toggle();
        }
    }
    public void menuAction(GameMenuDialog menu) {
        if (!enabled()) return;
        menu.action("Fast-forward " + Math.max(profile.getEmulationSpeed(),
                ru.playsoftware.j2meloader.runtime.EmulationClock.normalize(profile.fastForwardSpeed)) + "%",
                android.R.drawable.ic_media_ff, () -> {
                    menu.dismiss();
                    // Dismiss listeners may clear input while the host still lacks focus.
                    pendingStart = true;
                    button.post(this::startPending);
                });
    }
    private void render() {
        boolean active = state != null && state.active();
        button.setSelected(active);
        button.setContentDescription(active ? "Stop fast-forward" : "Fast-forward");
        button.setTooltipText(button.getContentDescription());
        button.setAlpha(active ? 1f : .8f);
        button.setEnabled(enabled());
    }
}
