package ru.playsoftware.j2meloader.input;

import android.view.HapticFeedbackConstants;
import android.view.View;

/** UI feedback respects Android's touch-feedback setting, independently of game rumble. */
public final class TouchFeedback {
    private TouchFeedback() { }

    public static boolean perform(View view, boolean enabled) {
        return enabled && view != null && view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }
}
