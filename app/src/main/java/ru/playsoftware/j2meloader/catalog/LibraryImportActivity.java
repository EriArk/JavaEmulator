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
	private Button backup, restore;
	private CheckBox shared;
	private boolean includeShared = true;
	private LinearLayout singleActions;
	private int singlePart;
	private final Entries adapter = new Entries();
	private LibraryImportModel.State current;
	private final ActivityResultLauncher<android.net.Uri> picker = registerForActivityResult(
			new ActivityResultContracts.OpenDocumentTree(), uri -> {
				if (uri == null) return;
				try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
				catch (SecurityException ignored) { }
				model.scan(uri);
			});
	private final ActivityResultLauncher<String[]> restorePicker = registerForActivityResult(
			new ActivityResultContracts.OpenDocument(), uri -> { if (uri != null) model.scanBackup(uri); });
	private final ActivityResultLauncher<String> backupPicker = registerForActivityResult(
			new ActivityResultContracts.CreateDocument("application/zip"), uri -> { if (uri != null) model.exportBackup(uri); });
	private final ActivityResultLauncher<android.net.Uri> singlePicker = registerForActivityResult(
			new ActivityResultContracts.OpenDocumentTree(), uri -> {
				if (uri == null) return;
				try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
				catch (SecurityException ignored) { }
				model.scanSingle(singlePart, uri);
			});

	@Override protected void onCreate(Bundle state) {
		setTheme(R.style.SettingsTheme);
		super.onCreate(state);
		if (ru.playsoftware.j2meloader.BuildConfig.HANDHELD_MODE)
			setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
		if (state != null) { singlePart = state.getInt("singlePart"); includeShared = state.getBoolean("includeShared", true); }
		if (getSupportActionBar() != null) getSupportActionBar().hide();
		model = new ViewModelProvider(this).get(LibraryImportModel.class);
		LinearLayout root = new LinearLayout(this);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setPadding(dp(16), dp(12), dp(16), dp(12));
		root.setBackgroundColor(0xFF0C1013);
		LinearLayout heading = new LinearLayout(this);
		android.widget.ImageButton back = new android.widget.ImageButton(this);
		back.setImageResource(androidx.appcompat.R.drawable.abc_ic_ab_back_material); back.setColorFilter(AMBER);
		back.setBackgroundColor(android.graphics.Color.TRANSPARENT); back.setContentDescription("Back");
		back.setOnClickListener(v -> onBackPressed()); heading.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
		TextView title = text(22, LIGHT); title.setText("Library transfer"); title.setGravity(Gravity.CENTER_VERTICAL);
		heading.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1)); root.addView(heading);
		LinearLayout transfers = new LinearLayout(this);
		backup = button("Back up", () -> backupPicker.launch("AbyssME-" + new java.text.SimpleDateFormat("yyyy-MM-dd-HHmm", java.util.Locale.ROOT).format(new java.util.Date()) + ".zip"));
		restore = button("Restore", () -> restorePicker.launch(new String[]{"application/zip", "application/octet-stream"}));
		folder = button("J2ME Loader", () -> new androidx.appcompat.app.AlertDialog.Builder(this)
				.setTitle("Import from J2ME Loader")
				.setItems(new String[]{"Whole library", "Single game"}, (dialog, which) -> {
					if (which == 0) picker.launch(null); else selectSinglePart(0);
				}).setNegativeButton("Close", null).show());
		for (Button button : new Button[]{backup, restore, folder}) transfers.addView(button, new LinearLayout.LayoutParams(0, dp(52), 1));
		root.addView(transfers);
		singleActions = new LinearLayout(this);
		singleActions.addView(button("Add saves", () -> selectSinglePart(1)), new LinearLayout.LayoutParams(0, dp(48), 1));
		singleActions.addView(button("Add settings", () -> selectSinglePart(2)), new LinearLayout.LayoutParams(0, dp(48), 1));
		root.addView(singleActions);
		status = text(14, MUTED); root.addView(status);
		warning = text(13, AMBER);
		warning.setText("Close J2ME Loader before importing. Original files stay unchanged.");
		warning.setPadding(0, dp(8), 0, dp(8)); root.addView(warning);
		shared = new CheckBox(this); shared.setText("Include shared game files (keep existing)");
		shared.setTextSize(13); shared.setTextColor(LIGHT); shared.setButtonTintList(ColorStateList.valueOf(AMBER));
		shared.setChecked(includeShared); shared.setOnCheckedChangeListener((view, checked) -> includeShared = checked);
		root.addView(shared);
		progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
		progress.setProgressTintList(ColorStateList.valueOf(AMBER));
		root.addView(progress, new LinearLayout.LayoutParams(-1, dp(4)));
		RecyclerView list = new RecyclerView(this);
		list.setLayoutManager(new LinearLayoutManager(this)); list.setAdapter(adapter);
		root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
		LinearLayout actions = new LinearLayout(this);
		select = button("Select all", () -> {
			boolean all = true;
			for (LibraryImporter.Entry entry : adapter.entries) if (entry.problem == null && !entry.selected) all = false;
			for (LibraryImporter.Entry entry : adapter.entries) entry.selected = entry.problem == null && !all;
			adapter.notifyDataSetChanged(); updateSelection();
		});
		start = button("Import", () -> {
			if (current != null && current.busy) { model.cancel(); return; }
			if (model.isSingleGame() && !adapter.entries.isEmpty() && !adapter.entries.get(0).hasSaves) {
				new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("No saves selected")
						.setMessage("The game will start without your previous progress.")
						.setPositiveButton("Add saves", (dialog, which) -> selectSinglePart(1))
						.setNeutralButton("Import without saves", (dialog, which) -> model.start(includeShared))
						.setNegativeButton("Cancel", null).show();
			} else model.start(includeShared);
		});
		for (Button button : new Button[]{select, start}) actions.addView(button, new LinearLayout.LayoutParams(0, dp(52), 1));
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
		backup.setEnabled(!state.busy); restore.setEnabled(!state.busy);
		shared.setVisibility(state.preview != null && state.preview.sharedFiles ? View.VISIBLE : View.GONE);
		singleActions.setVisibility(!state.busy && model.isSingleGame() && state.preview != null ? View.VISIBLE : View.GONE);
		select.setEnabled(!state.busy && state.preview != null && !state.preview.entries.isEmpty());
		adapter.entries = state.preview == null ? new ArrayList<>() : state.preview.entries;
		adapter.results = state.results == null ? new ArrayList<>() : state.results;
		adapter.notifyDataSetChanged();
		warning.setText(state.preview != null && state.preview.sharedFiles
				? "Per-game saves are included. Shared file conflicts keep the files already on this device."
				: "Exit games before transferring. Original files and existing saves stay unchanged.");
		updateSelection();
	}

	private void updateSelection() {
		if (current != null && current.busy) { start.setText("Cancel"); start.setEnabled(true); return; }
		int count = 0;
		for (LibraryImporter.Entry entry : adapter.entries) if (entry.selected && entry.problem == null) count++;
		start.setText(count == 0 ? "Import" : "Import (" + count + ")"); start.setEnabled(count > 0);
	}

	private void selectSinglePart(int part) {
		singlePart = part;
		new androidx.appcompat.app.AlertDialog.Builder(this)
				.setTitle(new String[]{"Game folder", "Saves folder", "Settings folder"}[part])
				.setMessage(new String[]{"J2ME Loader / converted / your game",
						"J2ME Loader / data / the same game",
						"J2ME Loader / configs / the same game"}[part])
				.setPositiveButton("Choose folder", (dialog, which) -> singlePicker.launch(null))
				.setNegativeButton("Cancel", null).show();
	}

	@Override protected void onSaveInstanceState(Bundle state) {
		state.putInt("singlePart", singlePart); state.putBoolean("includeShared", includeShared);
		super.onSaveInstanceState(state);
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
					: entry.vendor + " / " + entry.version + (entry.hasSaves ? "\nSaves included" : "\nNo saves found")
					+ (entry.duplicateTitle ? "\nAlso installed: imports as a separate copy" : "")));
			row.check.setOnCheckedChangeListener((button, checked) -> { entry.selected = checked; updateSelection(); });
			row.itemView.setOnClickListener(v -> { if (row.check.isEnabled()) row.check.toggle(); });
		}
	}
	private static final class Row extends RecyclerView.ViewHolder {
		final CheckBox check; final TextView label;
		Row(View view, CheckBox check, TextView label) { super(view); this.check = check; this.label = label; }
	}
}
