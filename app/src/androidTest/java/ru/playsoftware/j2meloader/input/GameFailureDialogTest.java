package ru.playsoftware.j2meloader.input;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import org.junit.Test;
import ru.playsoftware.j2meloader.MainActivity;
import ru.playsoftware.j2meloader.R;
import static org.junit.Assert.*;

public class GameFailureDialogTest {
    @Test public void retryIsManualAndDispatchedOnlyOnce() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(host -> {
                int[] retries = {0}, exits = {0};
                GameMenuDialog dialog = GameFailureDialog.show(host, "Fixture", "Load failed",
                        () -> retries[0]++, () -> exits[0]++);
                assertEquals(0, retries[0]);
                View button = text(dialog.getWindow().getDecorView(), host.getString(R.string.compatibility_test_retry));
                assertNotNull(button); button.performClick(); button.performClick(); dialog.onBackPressed();
                assertEquals(1, retries[0]); assertEquals(0, exits[0]); assertFalse(dialog.isShowing());
            });
        }
    }
    @Test public void backAndCancelExitOnlyOnceWithoutRetry() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(host -> {
                int[] exits = {0};
                GameMenuDialog dialog = GameFailureDialog.show(host, null, null, null, () -> exits[0]++);
                assertNull(text(dialog.getWindow().getDecorView(), host.getString(R.string.compatibility_test_retry)));
                dialog.onBackPressed(); dialog.cancel(); assertEquals(1, exits[0]);
            });
        }
    }
    @Test public void detailsAreBounded() {
        assertEquals("reason", GameFailureDialog.detail("  reason  "));
        assertEquals(1203, GameFailureDialog.detail(new String(new char[4000]).replace('\0', 'a')).length());
    }
    private static View text(View view, String label) {
        if (view instanceof TextView && label.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View found = text(((ViewGroup) view).getChildAt(i), label);
            if (found != null) return found;
        }
        return null;
    }
}
