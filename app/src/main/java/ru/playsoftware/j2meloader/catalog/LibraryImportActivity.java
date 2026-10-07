package ru.playsoftware.j2meloader.catalog;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import ru.playsoftware.j2meloader.R;

public class LibraryImportActivity extends AppCompatActivity {
	private static final int LIGHT = 0xFFE6EDF3, MUTED = 0xFF9DAAB3, AMBER = 0xFFFFB62E;
	private LibraryImportModel model;
	private TextView status, warning;
	private Button folder, select, start;
	private ProgressBar progress;
	private final Entries adapter = new Entries();
	private LibraryImportModel.State current;
	private final ActivityResultLauncher<android.net.Uri> picker = registerForActivityResult(
			new ActivityResultContracts.OpenDocumentTree(), uri -> {
				if (uri == null) return;
				try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
				catch (SecurityException ignored) { }
				model.scan(uri);
			});

	@Override protected void onCreate(Bundle state) {
		setTheme(R.style.SettingsTheme);
		super.onCreate(state);
		if (getSupportActionBar() != null) getSupportActionBar().hide();
		model = new ViewModelProvider(this).get(LibraryImportModel.class);
		LinearLayout root = new LinearLayout(this);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setPadding(dp(16), dp(12), dp(16), dp(12));
		root.setBackgroundColor(0xFF0C1013);
		TextView title = text(22, LIGHT); title.setText("Import from J2ME Loader");
		root.addView(title);
		status = text(14, MUTED); root.addView(status);
		warning = text(13, AMBER);
		warning.setText("Close J2ME Loader before importing. Original files stay unchanged.");
		warning.setPadding(0, dp(8), 0, dp(8)); root.addView(warning);
		progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
		progress.setProgressTintList(ColorStateList.valueOf(AMBER));
		root.addView(progress, new LinearLayout.LayoutParams(-1, dp(4)));
		RecyclerView list = new RecyclerView(this);
		list.setLayoutManager(new LinearLayoutManager(this)); list.setAdapter(adapter);
		root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
		LinearLayout actions = new LinearLayout(this);
		folder = button("Folder", () -> picker.launch(null));
		select = button("Select all", () -> {
			boolean all = true;
			for (LibraryImporter.Entry entry : adapter.entries) if (entry.problem == null && !entry.selected) all = false;
			for (LibraryImporter.Entry entry : adapter.entries) entry.selected = entry.problem == null && !all;
			adapter.notifyDataSetChanged(); updateSelection();
		});
		start = button("Import", () -> { if (current != null && current.busy) model.cancel(); else model.start(); });
		for (Button button : new Button[]{folder, select, start}) actions.addView(button, new LinearLayout.LayoutParams(0, dp(52), 1));
		root.addView(actions);
		setContentView(root);
		model.state.observe(this, this::render);
	}

	private void render(LibraryImportModel.State state) {
		current = state;
		status.setText(state.status);
		progress.setVisibility(state.busy ? View.VISIBLE : View.INVISIBLE);
		progress.setIndeterminate(state.total == 0);
		if (state.total > 0) { progress.setMax(state.total); progress.setProgress(state.completed); }
		folder.setEnabled(!state.busy);
		select.setEnabled(!state.busy && state.preview != null && !state.preview.entries.isEmpty());
		adapter.entries = state.preview == null ? new ArrayList<>() : state.preview.entries;
		adapter.results = state.results == null ? new ArrayList<>() : state.results;
		adapter.notifyDataSetChanged();
		warning.setText(state.preview != null && state.preview.sharedFiles
				? "Shared fs/ files need a separate transfer. Per-game saves are included. Existing data is never replaced."
				: "Close J2ME Loader before importing. Original files stay unchanged.");
		updateSelection();
	}

	private void updateSelection() {
		if (current != null && current.busy) { start.setText("Cancel"); start.setEnabled(true); return; }
		int count = 0;
		for (LibraryImporter.Entry entry : adapter.entries) if (entry.selected && entry.problem == null) count++;
		start.setText(count == 0 ? "Import" : "Import (" + count + ")"); start.setEnabled(count > 0);
	}

	@Override public void onBackPressed() {
		if (current != null && current.busy) { model.cancel(); status.setText("Stopping safely..."); }
		else super.onBackPressed();
	}

	private Button button(String label, Runnable action) {
		Button button = new Button(this); button.setText(label); button.setTextSize(14);
		button.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}},
				new int[]{0xFF59646B, AMBER})); button.setAllCaps(false);
		button.setBackgroundResource(R.drawable.bg_quick_setting_button);
		button.setOnClickListener(v -> action.run()); return button;
	}
	private TextView text(int size, int color) { TextView text = new TextView(this); text.setTextSize(size); text.setTextColor(color); return text; }
	private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

	private final class Entries extends RecyclerView.Adapter<Row> {
		List<LibraryImporter.Entry> entries = new ArrayList<>();
		List<String> results = new ArrayList<>();
		@Override public int getItemCount() { return results.isEmpty() ? entries.size() : results.size(); }
		@Override public Row onCreateViewHolder(ViewGroup parent, int viewType) {
			LinearLayout row = new LinearLayout(LibraryImportActivity.this); row.setGravity(Gravity.CENTER_VERTICAL);
			row.setMinimumHeight(dp(72)); row.setPadding(0, dp(8), 0, dp(8));
			row.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
			CheckBox check = new CheckBox(LibraryImportActivity.this); check.setButtonTintList(ColorStateList.valueOf(AMBER));
			row.addView(check, new LinearLayout.LayoutParams(dp(48), dp(48)));
			TextView label = text(15, LIGHT); row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
			return new Row(row, check, label);
		}
		@Override public void onBindViewHolder(Row row, int position) {
			row.check.setOnCheckedChangeListener(null);
			if (!results.isEmpty()) { row.check.setVisibility(View.GONE); row.label.setText(results.get(position)); row.itemView.setOnClickListener(null); return; }
			LibraryImporter.Entry entry = entries.get(position);
			row.check.setVisibility(View.VISIBLE); row.check.setChecked(entry.selected); row.check.setEnabled(entry.problem == null);
			row.check.setContentDescription(entry.title);
			row.label.setText(entry.title + "\n" + (entry.problem != null ? entry.problem
					: entry.vendor + " / " + entry.version + (entry.duplicateTitle ? "\nAlso installed: imports as a separate copy" : "")));
			row.check.setOnCheckedChangeListener((button, checked) -> { entry.selected = checked; updateSelection(); });
			row.itemView.setOnClickListener(v -> { if (row.check.isEnabled()) row.check.toggle(); });
		}
	}
	private static final class Row extends RecyclerView.ViewHolder {
		final CheckBox check; final TextView label;
		Row(View view, CheckBox check, TextView label) { super(view); this.check = check; this.label = label; }
	}
}
