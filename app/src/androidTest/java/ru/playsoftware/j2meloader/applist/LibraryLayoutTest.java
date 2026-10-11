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
