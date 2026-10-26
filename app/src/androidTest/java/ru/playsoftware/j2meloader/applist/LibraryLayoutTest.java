package ru.playsoftware.j2meloader.applist;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import ru.playsoftware.j2meloader.R;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LibraryLayoutTest {
    @Test public void firstTapLaunchesButHoldAndCanceledTouchDoNot() throws Exception {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        AppsListAdapter[] adapter = new AppsListAdapter[1];
        java.util.concurrent.atomic.AtomicInteger clicks = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger actions = new java.util.concurrent.atomic.AtomicInteger();
        CountDownLatch loaded = new CountDownLatch(1);
        instrumentation.runOnMainSync(() -> {
            adapter[0] = new AppsListAdapter(new AppsListAdapter.Listener() {
                public void onAppClicked(AppItem item) { clicks.incrementAndGet(); }
                public void onAppFocused(AppItem item) { }
                public void onAppActionsRequested(View anchor, AppItem item) { actions.incrementAndGet(); }
            });
            adapter[0].registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
                @Override public void onChanged() { if (adapter[0].getItemCount() == 1) loaded.countDown(); }
            });
            adapter[0].setItems(Collections.singletonList(new AppItem("tap-fixture", "Fixture", "Vendor", "1")));
        });
        assertTrue(loaded.await(5, TimeUnit.SECONDS));
        try (LibraryPreferencesSnapshot prefs = new LibraryPreferencesSnapshot(instrumentation.getTargetContext());
             androidx.test.core.app.ActivityScenario<ru.playsoftware.j2meloader.MainActivity> scenario =
                     androidx.test.core.app.ActivityScenario.launch(ru.playsoftware.j2meloader.MainActivity.class)) {
            View[] card = new View[1];
            FrameLayout[] overlay = new FrameLayout[1];
            android.widget.Button[] sentinel = new android.widget.Button[1];
            scenario.onActivity(activity -> {
                overlay[0] = new FrameLayout(activity);
                ((android.view.ViewGroup) activity.findViewById(android.R.id.content)).addView(overlay[0],
                        new android.view.ViewGroup.LayoutParams(-1, -1));
                sentinel[0] = new android.widget.Button(activity); sentinel[0].setText("Focus fixture");
                sentinel[0].setFocusableInTouchMode(true);
            });
            for (int mode = 0; mode < 3; mode++) {
                final int selectedMode = mode;
                scenario.onActivity(activity -> {
                    clicks.set(0); actions.set(0); overlay[0].removeAllViews();
                    adapter[0].setDisplayMode(selectedMode);
                    AppsListAdapter.ViewHolder holder = adapter[0].onCreateViewHolder(overlay[0], 0);
                    adapter[0].onBindViewHolder(holder, 0); card[0] = holder.itemView;
                    overlay[0].addView(card[0], new FrameLayout.LayoutParams(350, 450));
                    overlay[0].addView(sentinel[0], new FrameLayout.LayoutParams(180, 96, android.view.Gravity.END));
                    sentinel[0].requestFocusFromTouch();
                });
                instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(100);
                scenario.onActivity(activity -> {
                    assertFalse(card[0].hasFocus());
                    touch(card[0], android.view.MotionEvent.ACTION_DOWN);
                    touch(card[0], android.view.MotionEvent.ACTION_UP);
                });
                instrumentation.waitForIdleSync();
                assertEquals("First tap in mode " + mode, 1, clicks.get());
                scenario.onActivity(activity -> {
                    sentinel[0].requestFocusFromTouch(); touch(card[0], android.view.MotionEvent.ACTION_DOWN);
                });
                android.os.SystemClock.sleep(android.view.ViewConfiguration.getLongPressTimeout() + 150);
                scenario.onActivity(activity -> touch(card[0], android.view.MotionEvent.ACTION_UP));
                instrumentation.waitForIdleSync();
                assertEquals(1, actions.get()); assertEquals("Hold must not launch", 1, clicks.get());
                scenario.onActivity(activity -> {
                    touch(card[0], android.view.MotionEvent.ACTION_DOWN);
                    touch(card[0], android.view.MotionEvent.ACTION_CANCEL);
                });
                instrumentation.waitForIdleSync(); assertEquals(1, clicks.get());
            }
        }
    }

    private static void touch(View card, int action) {
        long now = android.os.SystemClock.uptimeMillis();
        android.view.MotionEvent event = android.view.MotionEvent.obtain(now, now, action, 30, 30, 0);
        try { card.dispatchTouchEvent(event); } finally { event.recycle(); }
    }

    @Test public void artworkAndTitlesFitShortViewportsInEveryMode() throws Exception {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        AppsListAdapter[] adapter = new AppsListAdapter[1];
        CountDownLatch loaded = new CountDownLatch(1);
        instrumentation.runOnMainSync(() -> {
            adapter[0] = new AppsListAdapter(new AppsListAdapter.Listener() {
                public void onAppClicked(AppItem item) { }
                public void onAppFocused(AppItem item) { }
                public void onAppActionsRequested(View anchor, AppItem item) { }
            });
            adapter[0].registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
                @Override public void onChanged() { if (adapter[0].getItemCount() == 1) loaded.countDown(); }
            });
            adapter[0].setItems(Collections.singletonList(new AppItem("layout-test",
                    "A very long game title that needs two lines", "Example vendor", "1.0")));
        });
        assertTrue("Library filter did not finish", loaded.await(5, TimeUnit.SECONDS));
        instrumentation.runOnMainSync(() -> {
            Context context = new ContextThemeWrapper(instrumentation.getTargetContext(), R.style.AppTheme);
            float density = context.getResources().getDisplayMetrics().density;
            for (boolean compact : new boolean[]{false, true}) {
              adapter[0].setCompact(compact);
              for (int height : new int[]{136, 184, 224, 500}) {
                adapter[0].setAvailableHeight(height);
                for (int mode = 0; mode < 3; mode++) {
                    adapter[0].setDisplayMode(mode);
                    AppsListAdapter.ViewHolder holder = adapter[0].onCreateViewHolder(new FrameLayout(context), 0);
                    adapter[0].onBindViewHolder(holder, 0);
                    View card = holder.itemView;
                    card.measure(View.MeasureSpec.makeMeasureSpec(Math.round((mode == 1 ? 448 : 216) * density),
                            View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                    card.layout(0, 0, card.getMeasuredWidth(), card.getMeasuredHeight());
                    assertTrue("Card clipped in mode " + mode + " at " + height,
                            card.getHeight() + 8 * density <= height * density);
                    if (compact && mode == AppsListAdapter.MODE_LIST) {
                        assertTrue("Compact list row is too tall", card.getHeight() <= 64 * density);
                    }
                    assertEquals(mode == 0 ? View.VISIBLE : View.GONE, card.findViewById(R.id.cover).getVisibility());
                    assertEquals(mode == 0 ? View.GONE : View.VISIBLE, card.findViewById(R.id.icon).getVisibility());
                    View title = card.findViewById(R.id.name);
                    assertTrue(title.getHeight() >= 18 * density);
                }
              }
            }
        });
    }
}
